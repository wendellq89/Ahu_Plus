#!/usr/bin/env python3
"""
排考预测匹配验证脚本（demo 模式）
==================================

用 jwapp 真实 API 或内置 demo 数据,演示「课程代码精确匹配」逻辑:
  1. 调用 scan_exams.py 同款抓取逻辑 (无 token 时回退 demo)
  2. 把结果标准化为 exam_predictions.json 同 schema
  3. 与用户课表做匹配,展示 predicted exams

供排错/手测使用。**不是**日常数据采集入口——日常请跑 `scan_exams.py`。

CLI:
  python scan_exams_probe.py                       # demo 数据
  python scan_exams_probe.py [start_date] [end_date]   # 真实 API (需要 .jwt_token)
"""

import json
import os
import re
import sys
import urllib3
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path

import requests

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

SCRIPT_DIR = Path(__file__).resolve().parent
TOKEN_FILE = SCRIPT_DIR / ".jwt_token"
OUTPUT_JSON = SCRIPT_DIR / "exam_predictions_probe.json"

JWT_TOKEN = os.environ.get("JWAPP_TOKEN", "")
BASE_URL = "https://jwapp.ahu.edu.cn/eams-micro-server/api/v1/room/place/rooms"

UA = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"
)

# 磬苑校区教学楼码表（与 CampusBuildingData.kt 保持一致）
QINGYUAN_BUILDINGS = {
    "18": "博学北楼", "17": "博学南楼", "27": "材料科学大楼",
    "15": "笃行北楼", "19": "笃行南楼", "9": "理工楼",
    "13": "人文楼", "20": "社科楼", "73": "现代实验技术中心",
    "6": "行知楼", "8": "艺术楼",
}

DEFAULT_START_DATE = "2026-07-05"
DEFAULT_END_DATE = "2026-07-11"

# 模拟课表 (实际使用时应该改成从 app 课表缓存读取)
DEMO_COURSES = {
    "ZH58202": "国民经济核算",
    "ZH37258": "单片机原理及应用",
    "ZH36136": "计算机组成与体系结构（一）",
    "ZH33324": "有机化学I",
    "ZH32122": "电磁场与电磁波",
    "ZH57029": "量子力学",
    "ZH46293": "生产运营管理",
    "ZH34007": "微生物学",
    "ZJ32090": "复变函数",
}

COURSE_PATTERN = re.compile(
    r"""([^,()]+?)\((\d{9}-[A-Z0-9]+\.\d{3}),?\s*(.*?)\)(?=,|$)"""
)
COURSE_CODE_PATTERN = re.compile(r"(\d{9})-([A-Z0-9]+)\.(\d{3})")


def get_headers(token: str) -> dict:
    return {
        "X-Id-Token": token, "userToken": token, "Authorization": token,
        "User-Agent": UA,
        "Accept": "application/json",
        "content-type": "application/json;charset=UTF-8",
        "Referer": f"https://jwapp.ahu.edu.cn/eams-room-occupy-app/index.html?idToken={token}",
    }


def fetch_rooms(token: str, building_id: int, date: str) -> list:
    headers = get_headers(token)
    body = {
        "currentPage": 1, "pageSize": 200,
        "campusAssoc": 1, "buildingIds": [building_id],
        "roomTypeIds": [], "floors": [],
        "minSeat": "", "maxSeat": "", "date": date,
    }
    all_rooms = []
    page = 1
    while True:
        body["currentPage"] = page
        try:
            resp = requests.post(BASE_URL, headers=headers, json=body,
                                 timeout=30, verify=False)
            data = resp.json()
        except Exception as e:
            print(f"    HTTP error page {page}: {e}")
            break
        if data.get("result") != 0:
            print(f"    API error: {data.get('message')}")
            break
        page_data = data.get("data", {})
        rooms = page_data.get("data", [])
        if not rooms:
            break
        all_rooms.extend(rooms)
        page_info = page_data.get("_page_", {})
        total_pages = page_info.get("totalPages", 1)
        if page >= total_pages:
            break
        page += 1
    return all_rooms


def parse_exam_courses(activity_name: str) -> list:
    """从 activityName 解析课程信息。"""
    results = []
    if not activity_name:
        return results
    content = activity_name[3:] if activity_name.startswith("课程：") else activity_name
    for m in COURSE_PATTERN.finditer(content):
        course_name = m.group(1).strip()
        full_code = m.group(2).strip()
        college = m.group(3).strip().rstrip(",")
        code_match = COURSE_CODE_PATTERN.match(full_code)
        if code_match:
            results.append({
                "course_name": course_name,
                "full_code": full_code,
                "semester": code_match.group(1),
                "course_code": code_match.group(2),
                "section": code_match.group(3),
                "college": college,
            })
    return results


def extract_exams(rooms: list, building_name: str) -> list:
    exams = []
    for room in rooms:
        occs = room.get("roomOccupationInfoVms")
        if not occs:
            continue
        for occ in occs:
            if occ.get("activityType") != "Exam":
                continue
            courses = parse_exam_courses(occ.get("activityName", ""))
            for c in courses:
                exams.append({
                    **c,
                    "date": occ["date"],
                    "start": occ["startTimeString"],
                    "end": occ["endTimeString"],
                    "room_name": room["nameZh"],
                    "room_code": room.get("code", "") or "",
                    "campus": room.get("campusNameZh", "") or "",
                    "building_id": room.get("buildingId"),
                    "teacher": occ.get("teacherName", "") or "",
                    "activity_id": occ.get("activityId"),
                })
    return exams


def match_courses(exams: list, user_courses: dict) -> tuple:
    """课程代码精确匹配。"""
    matched, unmatched = [], []
    seen = set()
    for exam in exams:
        ecode = exam["course_code"]
        key = (exam["full_code"], exam["date"], exam["start"])
        if ecode in user_courses:
            if key not in seen:
                seen.add(key)
                matched.append({**exam, "matched_course": user_courses[ecode], "match_type": "code"})
        else:
            unmatched.append(exam)
    return matched, unmatched


def load_demo_data() -> list:
    """从用户真实 API 响应里抽样的 demo 数据。"""
    demo = [
        ("国民经济核算", "202520262-ZH58202.001", "大数据与统计学院",
         "2026-07-10", "08:00", "10:00", "博学北楼A101", "博学北楼", "主监考：；副监考："),
        ("微生物学", "202520262-ZH34007.001", "生命科学与医学工程学院",
         "2026-07-10", "10:20", "12:20", "博学北楼A101", "博学北楼", "主监考：程园园；副监考：戎芳"),
        ("单片机原理及应用", "202520262-ZH37258.002", "电气工程与自动化学院",
         "2026-07-10", "08:00", "10:00", "博学北楼A104", "博学北楼", "主监考：刘碧；副监考：余攀"),
        ("电磁场与电磁波", "202520262-ZH32122.002", "物理学院",
         "2026-07-10", "10:20", "12:20", "博学北楼A104", "博学北楼", "主监考：陈学刚；副监考：李思祺"),
        ("计算机组成与体系结构（一）", "202520262-ZH36136.005", "计算机科学与技术学院",
         "2026-07-10", "13:40", "15:40", "博学北楼A104", "博学北楼", "主监考：刘峰；副监考：张少杰"),
        ("有机化学I", "202520262-ZH33324.001", "化学化工学院",
         "2026-07-10", "19:00", "21:00", "博学北楼A104", "博学北楼", "主监考：康熙；副监考：刘久逸"),
        ("单片机原理及应用", "202520262-ZH37258.001", "电气工程与自动化学院",
         "2026-07-10", "08:00", "10:00", "博学北楼A105", "博学北楼", "主监考：朱文杰；副监考：谭琨"),
        ("量子力学", "202520262-ZH57029.002", "材料科学与工程学院",
         "2026-07-10", "16:00", "18:00", "博学北楼A105", "博学北楼", "主监考：彭伟；副监考：郭正"),
        ("生产运营管理", "202520262-ZH46293.001", "商学院",
         "2026-07-10", "10:20", "12:20", "博学北楼A201", "博学北楼", "主监考：马文彬；副监考：徐海琴"),
        ("单片机原理及应用", "202520262-ZH37258.003", "电气工程与自动化学院",
         "2026-07-10", "08:00", "10:00", "博学北楼A103", "博学北楼", "主监考：那日沙；副监考：刁凯凯"),
        ("量子力学", "202520262-ZH57029.001", "材料科学与工程学院",
         "2026-07-10", "16:00", "18:00", "博学北楼A103", "博学北楼", "主监考：李广；副监考：王磊"),
        ("计算机组成与体系结构（一）", "202520262-ZH36136.004", "计算机科学与技术学院",
         "2026-07-10", "13:40", "15:40", "博学北楼A204", "博学北楼", "主监考：陈洁；副监考：汪万森"),
        ("有机化学I", "202520262-ZH33324.004", "化学化工学院",
         "2026-07-10", "19:00", "21:00", "博学北楼A204", "博学北楼", "主监考：许献云；副监考：张开富"),
        ("计算机组成与体系结构（一）", "202520262-ZH36136.009", "计算机科学与技术学院",
         "2026-07-10", "13:40", "15:40", "博学北楼A304", "博学北楼", "主监考：孙辉；副监考：范存航"),
        ("复变函数", "202520262-ZJ32090.001", "数学科学学院",
         "2026-07-10", "10:20", "12:20", "博学北楼B113", "博学北楼", ""),
    ]
    exams = []
    for cn, fc, cl, d, s, e, r, b, t in demo:
        cm = COURSE_CODE_PATTERN.match(fc)
        if cm:
            exams.append({
                "course_name": cn, "full_code": fc,
                "semester": cm.group(1), "course_code": cm.group(2), "section": cm.group(3),
                "college": cl, "date": d, "start": s, "end": e,
                "room_name": r, "room_code": "", "campus": "磬苑校区",
                "building_id": 18, "teacher": t, "activity_id": None,
            })
    return exams


def main():
    token = JWT_TOKEN.strip() or (
        TOKEN_FILE.read_text().strip() if TOKEN_FILE.exists() else ""
    )

    if not token:
        print("⚠️  未找到 token (环境变量 JWAPP_TOKEN 或 .jwt_token),用 demo 数据演示匹配。")
        exams = load_demo_data()
    else:
        start_date = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_START_DATE
        end_date = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_END_DATE
        start = datetime.strptime(start_date, "%Y-%m-%d")
        end = datetime.strptime(end_date, "%Y-%m-%d")
        dates = [(start + timedelta(days=i)).strftime("%Y-%m-%d")
                 for i in range((end - start).days + 1)]

        print(f"日期范围: {start_date} ~ {end_date} ({len(dates)} 天)")
        print(f"教学楼: {len(QINGYUAN_BUILDINGS)} (磬苑校区)")
        all_exams = []
        idx = 0
        total = len(dates) * len(QINGYUAN_BUILDINGS)
        for bld_id, bld_name in sorted(QINGYUAN_BUILDINGS.items()):
            bld_cnt = 0
            for date in dates:
                idx += 1
                rooms = fetch_rooms(token, int(bld_id), date)
                ex = extract_exams(rooms, bld_name)
                all_exams.extend(ex)
                if ex:
                    bld_cnt += len(ex)
                if idx % 10 == 0:
                    print(f"  Progress: {idx}/{total} queries, {len(all_exams)} exams so far...")
            if bld_cnt > 0:
                print(f"  {bld_name}({bld_id}): {bld_cnt} exams")

        # 去重
        seen = set()
        exams = []
        for e in all_exams:
            k = (e["full_code"], e["date"], e["start"], e["room_code"])
            if k not in seen:
                seen.add(k)
                exams.append(e)
        print(f"\nTotal: {len(exams)} unique exam records\n")

    matched, unmatched = match_courses(exams, DEMO_COURSES)
    print(f"匹配成功: {len(matched)} 条 / 未匹配: {len(unmatched)} 条")

    # 写探针版 JSON (供人工核对匹配逻辑)
    OUTPUT_JSON.write_text(
        json.dumps({
            "version": 1,
            "generated_at": datetime.now().astimezone().isoformat(timespec="seconds"),
            "source": "scan_exams_probe (demo or live)",
            "user_courses": DEMO_COURSES,
            "total_exams": len(exams),
            "matched_count": len(matched),
            "unmatched_count": len(unmatched),
            "matched": matched,
            "unmatched": unmatched,
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"[OK] Probe JSON: {OUTPUT_JSON.name}")


if __name__ == "__main__":
    main()