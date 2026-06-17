package com.yourname.ahu_plus.data.model

/**
 * 校区信息 —— 空教室查询的静态参考数据。
 *
 * 教学楼 ID 来自 JW 系统，通过 `tools/jw_empty_classroom_test.py` 全量验证。
 * campusId: 磬苑=1 / 龙河=2 / 金寨路=3（已确认）。
 */
data class CampusInfo(
    val id: String,
    val nameZh: String,
    val buildings: List<BuildingInfo>
)

data class BuildingInfo(
    val id: String,
    val nameZh: String
)

object CampusBuildingData {
    val campuses: List<CampusInfo> = listOf(
        CampusInfo(
            id = "1",
            nameZh = "磬苑校区",
            buildings = listOf(
                BuildingInfo("18", "博学北楼"),
                BuildingInfo("17", "博学南楼"),
                BuildingInfo("27", "材料科学大楼"),
                BuildingInfo("15", "笃行北楼"),
                BuildingInfo("19", "笃行南楼"),
                BuildingInfo("9", "理工楼"),
                BuildingInfo("13", "人文楼"),
                BuildingInfo("20", "社科楼"),
                BuildingInfo("73", "现代实验技术中心"),
                BuildingInfo("6", "行知楼"),
                BuildingInfo("8", "艺术楼")
            )
        ),
        CampusInfo(
            id = "2",
            nameZh = "龙河校区",
            buildings = listOf(
                BuildingInfo("53", "互联大楼"),
                BuildingInfo("22", "理西楼"),
                BuildingInfo("12", "生化楼"),
                BuildingInfo("16", "实验中心楼"),
                BuildingInfo("23", "文东楼"),
                BuildingInfo("7", "文西楼"),
                BuildingInfo("29", "逸夫图书馆"),
                BuildingInfo("21", "主教楼")
            )
        ),
        CampusInfo(
            id = "3",
            nameZh = "金寨路校区",
            buildings = listOf(
                BuildingInfo("32", "第二教学楼"),
                BuildingInfo("31", "第一教学楼"),
                BuildingInfo("30", "金寨路校区图书馆")
            )
        )
    )

    /** 按校区 ID 获取教学楼列表。 */
    fun getBuildings(campusId: String): List<BuildingInfo> =
        campuses.find { it.id == campusId }?.buildings ?: emptyList()

    /** 按教学楼 ID 查找名称。 */
    fun getBuildingName(buildingId: String): String =
        campuses.flatMap { it.buildings }.find { it.id == buildingId }?.nameZh ?: buildingId
}
