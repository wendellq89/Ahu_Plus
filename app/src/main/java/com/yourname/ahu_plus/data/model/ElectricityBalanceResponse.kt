package com.yourname.ahu_plus.data.model

/**
 * 电费余额查询响应 (ycard.ahu.edu.cn/charge/feeitem/getThirdData)
 *
 * 适用于空调 (feeitemid=408) 和照明 (feeitemid=428),与浴室共用同一端点但参数和响应结构不同。
 *
 * Gson 反序列化后调用 [toUiData] 将 showData["信息"] 中的电量文本解析为数值。
 */
data class ElectricityBalanceResponse(
    val msg: String = "",
    val code: Int = 0,
    val map: ElectricityBalanceMap? = null
)

data class ElectricityBalanceMap(
    val showData: Map<String, String>? = null,
    val data: ElectricityBalanceData? = null
) {
    /** 解析为 UI 就绪的数据对象 */
    fun toUiData(): ElectricityUiData {
        val infoText = showData?.get("信息")
        val kwh = parseKwhFromInfo(infoText)
        return ElectricityUiData(
            buildingName = data?.buildingName ?: "",
            floorName = data?.floorName ?: "",
            roomName = data?.roomName ?: "",
            remainingKwh = kwh,
            infoText = infoText ?: ""
        )
    }
}

/** Gson 反序列化的原始字段 */
data class ElectricityBalanceData(
    val buildingName: String = "",
    val floorName: String = "",
    val roomName: String = "",
    val building: String = "",
    val floor: String = "",
    val room: String = "",
    val area: String = "",
    val areaName: String = "",
    val extdata: String = "",
    val aid: String = "",
    val account: String = ""
)

/** UI 层使用的电费数据(已解析) */
data class ElectricityUiData(
    val buildingName: String,
    val floorName: String,
    val roomName: String,
    /** 剩余电量(度),解析失败为 null */
    val remainingKwh: Double?,
    /** showData["信息"] 原文,解析失败时用于展示 */
    val infoText: String
)

/**
 * 从电费 showData["信息"] 文本中解析剩余电量。
 *
 * 示例输入:
 *   "房间当前剩余电量:7.25 度"
 *   "房间当前剩余电量: 12.57 度"
 *
 * @return 解析出的度数,失败返回 null
 */
fun parseKwhFromInfo(infoText: String?): Double? {
    if (infoText.isNullOrBlank()) return null
    val match = Regex("""(\d+\.?\d*)\s*度""").find(infoText)
    return match?.groupValues?.get(1)?.toDoubleOrNull()
}

// ── 电费日用明细 (getRechargeRecord) ──────────────────────

/** 电费日用明细响应 */
data class ElectricityBillResponse(
    val msg: String = "",
    val code: Int = 0,
    val data: List<ElectricityDailyRecord> = emptyList()
)

data class ElectricityDailyRecord(
    @com.google.gson.annotations.SerializedName("日期") val date: String = "",
    @com.google.gson.annotations.SerializedName("度数") val degreeText: String = ""
) {
    /** 解析度数(度),如 "5.37度" -> 5.37, 失败返回 null */
    val kwh: Double?
        get() {
            val match = Regex("""(\d+\.?\d*)""").find(degreeText)
            return match?.groupValues?.get(1)?.toDoubleOrNull()
        }
}
