package com.yourname.ahu_plus.data.model

/**
 * 浴室余额查询响应 (ycard.ahu.edu.cn/charge/feeitem/getThirdData)
 */
data class BathroomBalanceResponse(
    val msg: String = "",
    val code: Int = 0,
    val map: BathroomBalanceMap? = null
)

data class BathroomBalanceMap(
    val showData: Map<String, String>? = null,
    val data: BathroomBalanceData? = null
)

data class BathroomBalanceData(
    val projectId: Int = 0,
    val projectName: String = "",
    val accountId: Int = 0,
    val telPhone: String = "",
    val identifier: String? = null,
    val sex: String = "",
    val name: String? = null,
    val statusId: Int = 0,
    /** 现金余额，单位：0.1分（1/1000 元），除以 1000 得元 */
    val accountMoney: Int = 0,
    /** 赠送余额，单位：0.1分（1/1000 元），除以 1000 得元 */
    val accountGivenMoney: Int = 0,
    val alias: String? = null,
    val tags: String? = null,
    val isCard: Int = 0,
    val cardStatusId: Int = -1,
    val isUseCode: Int = 1,
    val cardPhysicalId: String? = null,
    val tsmAbstract: String = ""
) {
    /** 现金余额（元） */
    val cashYuan: Double get() = accountMoney / 1000.0

    /** 赠送余额（元） */
    val bonusYuan: Double get() = accountGivenMoney / 1000.0
}
