package com.yourname.ahu_plus.data.model

import com.google.gson.annotations.SerializedName

// ── 网费余额 ─────────────────────────────────────────────

/** 网费余额查询响应 (feeitemid=431, level=0, 无需额外参数) */
data class InternetBalanceResponse(
    val msg: String = "",
    val code: Int = 0,
    val map: InternetBalanceMap? = null
)

data class InternetBalanceMap(
    val showData: Map<String, String>? = null,
    val data: InternetBalanceData? = null
)

data class InternetBalanceData(
    @SerializedName("state_time") val stateTime: String = "",
    @SerializedName("state_memo") val stateMemo: String = "",
    val balance: String = "",           // 储值余额,字符串形式的数字
    @SerializedName("use_time") val useTime: String = "",
    @SerializedName("tsmAbstract") val tsmAbstract: String = "",
    @SerializedName("use_money") val useMoney: String = "",
    @SerializedName("use_flow") val useFlow: String = "",   // MB
    val account: String = "",
    @SerializedName("user_state") val userState: String = "",
    @SerializedName("start_date") val startDate: String = ""
) {
    /** 储值余额(元),解析失败返回 0 */
    val balanceYuan: Double get() = balance.toDoubleOrNull() ?: 0.0
    /** 本期已使用费用(元) */
    val usedMoneyYuan: Double get() = useMoney.toDoubleOrNull() ?: 0.0
    /** 本期已使用流量(MB) */
    val usedFlowMb: Double get() = useFlow.toDoubleOrNull() ?: 0.0
    /** 本期已使用时长(分钟) */
    val usedTimeMinutes: Long get() = useTime.toLongOrNull() ?: 0L
    /** 用户状态: "1"=正常 */
    val isNormal: Boolean get() = userState == "1"
}

// ── 网费账单 ─────────────────────────────────────────────

/** 网费充值账单响应 */
data class InternetBillResponse(
    val msg: String = "",
    val code: Int = 0,
    val accountList: List<InternetBillRecord> = emptyList(),
    val count: Int = 0
)

data class InternetBillRecord(
    @SerializedName("SUCCESSDATE") val successDate: String = "",
    @SerializedName("TYPENAME") val typeName: String = "",
    @SerializedName("TRANAMT") val tranAmt: Int = 0,       // 充值金额(元)
    @SerializedName("ABSTRACTS") val abstracts: String = "",
    @SerializedName("ITEMNAME") val itemName: String = "",
    @SerializedName("TURNOVERID") val turnoverId: Int = 0
)
