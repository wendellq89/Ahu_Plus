# 排考预测数据采集 (exam_prediction)

存放 Ahu_Plus「排考预测」功能依赖的全部数据采集脚本与原始/中间产物。

## 工作流

```
  ┌─────────────────────────────────────────────────────────────┐
  │  本机 (Windows / macOS / Linux)                              │
  │                                                              │
  │  1. 智慧安大 WebView → 抓 idToken → 写入 .jwt_token         │
  │  2. python scan_exams.py [start_date] [end_date]             │
  │     ├─ cache_full_scan.json   原始扫描缓存 (供 --reuse 重导) │
  │     ├─ exam_predictions.json  标准化产物 ←── 待上传          │
  │     ├─ output_*.csv / output_*.md   人看报表                │
  │  3. git push exam_predictions/exam_predictions.json 到 Gitee │
  └─────────────────────────────────────────────────────────────┘
                                 │
                                 │  Android 客户端启动时
                                 ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  yao-enqi/ahu-plus-update (Gitee 公开仓库)                    │
  │   exam_predictions/exam_predictions.json                     │
  │                                                              │
  │  Ahu_Plus App → ExamDataRepository.fetch()                  │
  │   → 缓存到 DataStore → 与用户课表匹配 → 展示                │
  └─────────────────────────────────────────────────────────────┘
```

## 文件清单

| 文件 | 用途 | 是否上传 Gitee |
|------|------|----------------|
| `scan_exams.py` | **主入口**。扫 jwapp API、生成 `exam_predictions.json` + 报表 | — |
| `scan_exams_probe.py` | demo 数据演示匹配逻辑,日常不用 | — |
| `exam_predictions.json` | **标准化产物**,直接推送到 Gitee | ✅ 是 |
| `exam_predictions_probe.json` | probe 输出,日常不用 | ❌ |
| `cache_full_scan.json` | 原始 API 响应缓存,`--reuse` 重导用 | ❌ |
| `cache_scan.json` / `cache_probe_result.json` | 历史遗留缓存,可清理 | ❌ |
| `output_7.6-7.15.csv` / `.md` | 人看报表 (Excel / Notion 分享用) | ❌ |
| `.jwt_token` | 单行 JWT idToken (智慧安大 WebView 抓取) | ❌ **别提交!** |

## 快速开始

### 1) 准备 JWT token

智慧安大 WebView 加载 `https://jwapp.ahu.edu.cn/eams-room-occupy-app/index.html`,
CAS SSO 登录后 SPA 会用 ST 换 idToken,出现在 URL 中。手动复制后写入:

```bash
echo "eyJ0eXAiOiJKV1Qi..." > .jwt_token
```

Token 30 天左右过期,过期后重抓。

### 2) 跑扫描

```bash
# 默认 7.6 ~ 7.15
python scan_exams.py

# 自定义日期范围
python scan_exams.py 2026-07-06 2026-07-15

# 用缓存快速重导 (不调 API)
python scan_exams.py --reuse
```

输出:
- `exam_predictions.json` (Gitee 拉的数据源,顶层 meta + exams)
- `output_*.csv` / `output_*.md` (报表)
- `cache_full_scan.json` (原始响应,`--reuse` 时复用)

### 3) 推到 Gitee

`exam_predictions.json` 上传到仓库 `yao-enqi/ahu-plus-update` 的
`exam_predictions/exam_predictions.json`。Android 客户端 raw URL:

```
https://gitee.com/yao-enqi/ahu-plus-update/raw/master/exam_predictions/exam_predictions.json
```

推送流程示例:

```bash
cd /path/to/gitee-ahu-plus-update
cp /path/to/this/folder/exam_predictions.json exam_predictions/exam_predictions.json
git add exam_predictions/exam_predictions.json
git commit -m "update exam predictions $(date +%F)"
git push origin master
```

## 数据 schema (Gitee JSON)

```json
{
  "version": 1,
  "generated_at": "2026-06-23T18:00:00+08:00",
  "semester": "2025-2026-2",
  "date_range": ["2026-07-06", "2026-07-15"],
  "campuses": ["磬苑校区", "龙河校区", "金寨路校区"],
  "source": "jwapp.ahu.edu.cn 教室占用 API (activityType=Exam)",
  "count": 1247,
  "summary_by_date": [
    {"date": "2026-07-10", "count": 287, "campuses": ["磬苑校区"], "elapsed_sec": 12.3}
  ],
  "exams": [
    {
      "date": "2026-07-10", "start": "08:00", "end": "10:00",
      "course_name": "国民经济核算",
      "course_code": "ZH58202", "section": "001",
      "full_code": "202520262-ZH58202.001", "semester": "202520262",
      "college": "大数据与统计学院",
      "room_name": "博学北楼A101", "room_code": "A101",
      "campus": "磬苑校区", "building_id": 18,
      "teacher": "主监考：xxx；副监考：xxx",
      "activity_id": 12345
    }
  ]
}
```

## 客户端匹配逻辑

Android `ExamDataRepository` 拉取 JSON → 解析 → 用 `course_code` 与用户课表缓存
(`SessionManager.getScheduleJson()` 里的 `courseCode`) 做精确匹配。
匹配结果按 `(date, start)` 排序,按日期分组展示。

## 注意事项

- `.jwt_token` 不要提交到 git;`.gitignore` 已加 `exam_prediction/.jwt_token`
- API 限速:每页间隔不要太快,默认 30s 内 60 页足够
- 多课程合并占用:一条 Exam 可能含多门课,JSON 已展开为多条 `exams[]` 记录,
  共享 `room_name/date/start/end/campus/teacher/building_id`
- 期末考试周一般在 7.5 ~ 7.20,具体日期需要从教务处通知或扫描 6 月底数据时发现