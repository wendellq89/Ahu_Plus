#!/usr/bin/env python3
"""
排考数据采集 — 全量扫描 + 标准化 JSON 输出
==========================================

遍历安大所有校区(磬苑 / 龙河 / 金寨路 / 纯线上)所有教学楼 + 指定日期范围,
从 jwapp.ahu.edu.cn 教室占用 API 抓取所有 Exam 类型记录,并合并生成一份
标准化的 JSON,上传到 Gitee `yao-enqi/ahu-plus-update` 仓库的
`exam_predictions/exam_predictions.json`,供 Android 客户端拉取后匹配课表。

API: POST https://jwapp.ahu.edu.cn/eams-micro-server/api/v1/room/place/rooms
认证: JWT Token (从本目录 .jwt_token 读取;通过智慧安大 WebView 获取)

# ── 日常使用流程 ──
    # 1) 准备 token (30 天有效期,过期重新跑 WebView)
    python get_jwt_token.py                 # 单独脚本 (此处不实现,见 README.md)

    # 2) 执行扫描 (默认 7.6 ~ 7.15,会写 cache + 输出 exam_predictions.json)
    python scan_exams.py
    # 或指定日期范围:
    python scan_exams.py 2026-07-06 2026-07-15

    # 3) 把生成的 exam_predictions.json 推到 Gitee 仓库
    #    (Android 端从仓库 raw URL 拉取即可,无需登录)
    git -C ../gitee-ahu-plus-update add exam_predictions/exam_predictions.json
    git -C ../gitee-ahu-plus-update commit -m "update exam predictions $(date +%F)"
    git -C ../gitee-ahu-plus-update push

输出 schema:
{
  "version": 1,
  "generated_at": "2026-06-23T18:00:00+08:00",
  "semester": "2025-2026-2",
  "date_range": ["2026-07-06", "2026-07-15"],
  "campuses": ["磬苑校区", "龙河校区", "金寨路校区", "纯线上"],
  "source": "jwapp.ahu.edu.cn 教室占用 API (activityType=Exam)",
  "count": 1247,
  "exams": [
    {
      "date": "2026-07-10",
      "start": "08:00",
      "end": "10:00",
      "course_name": "国民经济核算",
      "course_code": "ZH58202",
      "section": "001",
      "full_code": "202520262-ZH58202.001",
      "semester": "202520262",
      "college": "大数据与统计学院",
      "room_name": "博学北楼A101",
      "room_code": "A101",
      "campus": "磬苑校区",
      "building_id": 18,
      "teacher": "主监考：xxx；副监考：xxx",
      "activity_id": 12345
    },
    ...
  ]
}

去重策略: (full_code, date, start, room_code) 四元组,多课程合并场次按 Exam
原始记录逐一展开。多课程占用同一教室(同一日期+时间)会在每条 course 上共享
(room_name, date, start, end, campus, building_id, teacher)。
"""

import csv
import json
import re
import sys
import time
import urllib3
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path

import requests

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ── 路径 / 配置 ─────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).resolve().parent
TOKEN_FILE = SCRIPT_DIR / ".jwt_token"
RAW_CACHE = SCRIPT_DIR / "cache_full_scan.json"
OUTPUT_JSON = SCRIPT_DIR / "exam_predictions.json"          # 待上传到 Gitee 的标准化产物
OUTPUT_CSV = SCRIPT_DIR / "output_7.6-7.15.csv"
OUTPUT_MD = SCRIPT_DIR / "output_7.6-7.15.md"

BASE_URL = "https://jwapp.ahu.edu.cn/eams-micro-server/api/v1/room/place/rooms"
GITEE_TARGET_PATH = "exam_predictions/exam_predictions.json"   # 仓库内的相对路径

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 "
    "NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) "
    "WindowsWechat(0x63090a13) UnifiedPCWindowsWechat(0xf2541a35) XWEB/19977 Flue"
)

# 默认日期范围（春季学期期末考试周）
DEFAULT_START_DATE = "2026-07-06"
DEFAULT_END_DATE = "2026-07-15"

# 当前学期标识（写入 JSON 头部供客户端交叉验证）
SEMESTER_LABEL = "2025-2026-2"

PAGE_SIZE = 200
MAX_PAGES = 60   # 安全上限 (1521 间教室 / 200 ≈ 8 页)

# ── 工具函数 ─────────────────────────────────────────────

def load_token() -> str:
    if not TOKEN_FILE.exists():
        sys.exit(
            f"❌ JWT token 不存在: {TOKEN_FILE}\n"
            f"   请先用智慧安大 WebView 抓取 idToken 写入此文件 (单行)。"
        )
    return TOKEN_FILE.read_text().strip()


def fetch_page(token: str, date: str, page: int) -> dict | None:
    """请求指定日期+页码的所有教室。"""
    headers = {
        "User-Agent": UA,
        "Content-Type": "application/json",
        "Authorization": token,
        "Accept": "application/json",
    }
    body = {
        "currentPage": page,
        "pageSize": PAGE_SIZE,
        "campusAssoc": None,
        "buildingIds": [],
        "roomTypeIds": [],
        "floors": [],
        "minSeat": "",
        "maxSeat": "",
        "date": date,
    }
    try:
        resp = requests.post(BASE_URL, headers=headers, json=body,
                             timeout=30, verify=False)
    except requests.RequestException as e:
        print(f"  ⚠️  HTTP error page {page}: {e}", file=sys.stderr)
        return None

    if resp.status_code != 200:
        print(f"  ⚠️  HTTP {resp.status_code} page {page}", file=sys.stderr)
        return None

    try:
        return resp.json()
    except json.JSONDecodeError:
        print(f"  ⚠️  Non-JSON response page {page}", file=sys.stderr)
        return None


# ── 解析逻辑 ─────────────────────────────────────────────

COURSE_PATTERN = re.compile(
    r"""([^,()]+?)\((\d{9}-[A-Z0-9]+\.\d{3}),?\s*(.*?)\)(?=,|$)"""
)
COURSE_CODE_PATTERN = re.compile(r"(\d{9})-([A-Z0-9]+)\.(\d{3})")


def parse_exam_rooms(rooms: list) -> list:
    """从教室列表提取所有 Exam 类型占用,展开为单条考试记录。

    单条 Exam 若包含多门课程(如「课程:A(…),B(…)」),会展开为多条记录,
    共享同一 (date, start, end, room, campus, building, teacher)。
    """
    exams = []
    for room in rooms:
        occs = room.get("roomOccupationInfoVms") or []
        for occ in occs:
            if occ.get("activityType") != "Exam":
                continue
            activity_name = occ.get("activityName", "") or ""
            content = activity_name[3:] if activity_name.startswith("课程：") else activity_name

            shared = {
                "date": occ.get("date", ""),
                "start": occ.get("startTimeString", ""),
                "end": occ.get("endTimeString", ""),
                "room_name": room.get("nameZh", ""),
                "room_code": room.get("code", "") or "",
                "campus": room.get("campusNameZh", "") or "",
                "building_id": room.get("buildingId"),
                "teacher": (occ.get("teacherName", "") or "").strip(),
                "activity_id": occ.get("activityId"),
            }

            for m in COURSE_PATTERN.finditer(content):
                course_name = m.group(1).strip()
                full_code = m.group(2).strip()
                college = m.group(3).strip().rstrip(",")

                code_match = COURSE_CODE_PATTERN.match(full_code)
                if not code_match:
                    continue

                exams.append({
                    **shared,
                    "course_name": course_name,
                    "full_code": full_code,
                    "semester": code_match.group(1),
                    "course_code": code_match.group(2),
                    "section": code_match.group(3),
                    "college": college,
                })
    return exams


def scan_date(token: str, date: str) -> list:
    """扫描某一天的全部教室,提取去重后的 Exam 记录。"""
    all_exams = []
    seen_keys = set()

    for page in range(1, MAX_PAGES + 1):
        data = fetch_page(token, date, page)
        if data is None:
            break
        result = data.get("result")
        if result != 0:
            print(f"  ⚠️  API result={result} msg={data.get('message')}",
                  file=sys.stderr)
            break

        page_data = data.get("data", {})
        rooms = page_data.get("data") or []
        if not rooms:
            break

        exams = parse_exam_rooms(rooms)
        for e in exams:
            key = (e["full_code"], e["date"], e["start"], e["room_code"])
            if key not in seen_keys:
                seen_keys.add(key)
                all_exams.append(e)

        page_info = page_data.get("_page_", {})
        total_pages = page_info.get("totalPages", 1)
        if page >= total_pages:
            break

    return all_exams


# ── 主流程 ────────────────────────────────────────────────

def build_output_json(all_exams: list, date_range: list,
                      summary_by_date: list) -> dict:
    """组装待上传到 Gitee 的标准化 JSON。"""
    campuses = sorted({e["campus"] for e in all_exams if e["campus"]})
    # 排序: 按 (date, start, campus, room_name) 保证客户端拉到的结果稳定
    sorted_exams = sorted(
        all_exams,
        key=lambda e: (e["date"], e["start"], e["campus"], e["room_name"], e["full_code"]),
    )
    return {
        "version": 1,
        "generated_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "semester": SEMESTER_LABEL,
        "date_range": date_range,
        "campuses": campuses,
        "source": "jwapp.ahu.edu.cn 教室占用 API (activityType=Exam)",
        "count": len(sorted_exams),
        "summary_by_date": [
            {
                "date": d,
                "count": c,
                "campuses": cp,
                "elapsed_sec": round(el, 1),
            }
            for (d, c, cp, el) in summary_by_date
        ],
        "exams": sorted_exams,
    }


def export_legacy_outputs(all_exams: list, summary_by_date: list) -> None:
    """导出 CSV + Markdown 给人看的报表(非 Gitee 拉取的数据源)。"""
    # ── 写 CSV ──
    fieldnames = [
        "date", "weekday", "start", "end",
        "campus", "building_id", "room_name", "room_code",
        "course_name", "course_code", "section", "full_code", "semester", "college",
        "teacher", "activity_id",
    ]
    with open(OUTPUT_CSV, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for e in all_exams:
            row = {k: e.get(k, "") for k in fieldnames}
            row["weekday"] = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"][
                datetime.strptime(e["date"], "%Y-%m-%d").weekday()
            ]
            writer.writerow(row)
    print(f"[OK] CSV: {OUTPUT_CSV.name} ({len(all_exams)} records)")

    # ── 写 Markdown ──
    lines = []
    lines.append(f"# 期末考试安排 (output_7.6-7.15)")
    lines.append("")
    lines.append(f"- **生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"- **数据源**: jwapp.ahu.edu.cn 教室占用 API (`activityType=Exam`)")
    lines.append(
        f"- **校区覆盖**: {' / '.join(sorted({e['campus'] for e in all_exams if e['campus']})) or '-'}"
    )
    lines.append(f"- **总场次**: {len(all_exams)} (按 course_code|date|start|room_code 去重)")
    lines.append("")

    lines.append("## 每日汇总")
    lines.append("")
    lines.append("| 日期 | 星期 | 场次 | 覆盖校区 | 耗时 |")
    lines.append("|------|------|-----:|---------|-----:|")
    for date, cnt, cps, elapsed in summary_by_date:
        wd = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"][
            datetime.strptime(date, "%Y-%m-%d").weekday()
        ]
        lines.append(f"| {date} | {wd} | {cnt} | {' / '.join(cps) or '-'} | {elapsed:.1f}s |")
    lines.append("")

    lines.append("## 明细 (按日期 → 校区 → 教学楼)")
    lines.append("")
    by_date_campus = defaultdict(lambda: defaultdict(list))
    for e in all_exams:
        by_date_campus[e["date"]][e["campus"]].append(e)

    for date in sorted(by_date_campus.keys()):
        wd = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"][
            datetime.strptime(date, "%Y-%m-%d").weekday()
        ]
        total_day = sum(len(v) for v in by_date_campus[date].values())
        lines.append(f"### {date} ({wd}) — 共 {total_day} 场")
        lines.append("")
        for campus in sorted(by_date_campus[date].keys()):
            exams = by_date_campus[date][campus]
            lines.append(f"#### {campus} ({len(exams)} 场)")
            lines.append("")
            by_building = defaultdict(list)
            for e in exams:
                m = re.match(r"^(.+?[A-Za-z]?)(\d{3}|附)", e["room_name"])
                building = m.group(1) if m else e["room_name"][:4]
                by_building[building].append(e)

            for building in sorted(by_building.keys()):
                lines.append(f"- **{building}** ({len(by_building[building])} 场)")
                sorted_exams = sorted(
                    by_building[building],
                    key=lambda x: (x["room_name"], x["start"]),
                )
                room_time_courses = defaultdict(list)
                for ex in sorted_exams:
                    key = (ex["room_name"], ex["start"])
                    room_time_courses[key].append(ex)

                for (room, start), courses in sorted(room_time_courses.items()):
                    time_str = courses[0]["start"] + "-" + courses[0]["end"]
                    course_strs = [
                        f"`{c['course_code']}.{c['section']}` {c['course_name']} ({c['college']})"
                        for c in courses
                    ]
                    if len(course_strs) == 1:
                        lines.append(f"  - `{time_str}` {room} — {course_strs[0]}")
                    else:
                        lines.append(f"  - `{time_str}` {room} — 多课程合并:")
                        for cs in course_strs:
                            lines.append(f"    - {cs}")
            lines.append("")

    OUTPUT_MD.write_text("\n".join(lines), encoding="utf-8")
    print(f"[OK] Markdown: {OUTPUT_MD.name}")


def main():
    # ── --reuse: 直接用缓存导出 (供二次重命名/导出报表使用) ──
    if "--reuse" in sys.argv and RAW_CACHE.exists():
        cached = json.loads(RAW_CACHE.read_text(encoding="utf-8"))
        all_exams = cached["exams"]
        date_range = cached["date_range"]
        summary = [
            (d["date"], d["count"], d["campuses"], d["elapsed_sec"])
            for d in cached.get("summary_by_date", [])
        ]
        print(f"[CACHE] 复用 {RAW_CACHE.name}: {len(all_exams)} 条")

        payload = build_output_json(all_exams, date_range, summary)
        OUTPUT_JSON.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print(f"[OK] Gitee JSON: {OUTPUT_JSON.name} ({len(all_exams)} records)")
        export_legacy_outputs(all_exams, summary)
        return

    # ── 正常路径: 重新扫描 ──
    # CLI 参数: scan_exams.py [start_date] [end_date]
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    start_str = args[0] if len(args) >= 1 else DEFAULT_START_DATE
    end_str = args[1] if len(args) >= 2 else DEFAULT_END_DATE

    token = load_token()
    start = datetime.strptime(start_str, "%Y-%m-%d")
    end = datetime.strptime(end_str, "%Y-%m-%d")
    dates = [(start + timedelta(days=i)).strftime("%Y-%m-%d")
             for i in range((end - start).days + 1)]

    print(f"日期范围: {start_str} ~ {end_str} ({len(dates)} 天)")
    print(f"目标校区: 全部 (磬苑 + 龙河 + 金寨路 + 纯线上)")
    print(f"分页大小: {PAGE_SIZE} 间/页")
    print()
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    all_exams: list = []
    summary_by_date: list = []

    for date in dates:
        t0 = time.time()
        exams = scan_date(token, date)
        elapsed = time.time() - t0
        cnt_exam_rooms = len({e["room_code"] for e in exams})
        cnt_campuses = sorted({e["campus"] for e in exams})
        print(f"{date}: {len(exams):4d} 场考试 ({cnt_exam_rooms} 间教室)  "
              f"校区={cnt_campuses}  耗时={elapsed:.1f}s")
        summary_by_date.append((date, len(exams), cnt_campuses, elapsed))
        all_exams.extend(exams)

    # 排序 + 写原始缓存
    all_exams.sort(key=lambda e: (e["date"], e["start"], e["campus"], e["room_name"], e["full_code"]))

    RAW_CACHE.write_text(
        json.dumps({
            "scanned_at": datetime.now().isoformat(),
            "date_range": [start_str, end_str],
            "summary_by_date": [
                {"date": d, "count": c, "campuses": cp, "elapsed_sec": round(el, 1)}
                for (d, c, cp, el) in summary_by_date
            ],
            "total": len(all_exams),
            "exams": all_exams,
        }, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    # 写待上传的标准化 Gitee JSON
    payload = build_output_json(all_exams, [start_str, end_str], summary_by_date)
    OUTPUT_JSON.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\n[OK] Gitee JSON: {OUTPUT_JSON.name}")
    print(f"     上传目标: Gitee 仓库 {GITEE_TARGET_PATH}")

    # 写人看的报表
    export_legacy_outputs(all_exams, summary_by_date)

    # 统计
    print()
    print("=" * 60)
    print("统计")
    print("=" * 60)
    campus_counts = defaultdict(int)
    for e in all_exams:
        campus_counts[e["campus"]] += 1
    for campus, cnt in sorted(campus_counts.items()):
        print(f"  {campus}: {cnt} 场")
    unique_courses = {(e["course_code"], e["section"]) for e in all_exams}
    print(f"\n唯一课程 (course_code+section): {len(unique_courses)}")
    print(f"\n下一步: 把 {OUTPUT_JSON.name} 推到 Gitee (详见 README.md)")


if __name__ == "__main__":
    main()