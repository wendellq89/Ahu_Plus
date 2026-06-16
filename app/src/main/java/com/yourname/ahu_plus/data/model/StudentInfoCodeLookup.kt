package com.yourname.ahu_plus.data.model

/**
 * 学生一张表「码值」字段 → 中文对照表。
 *
 * 表中仅覆盖学校常见码值，匹配失败时回退原值，避免阻塞显示。
 * 民族码遵循 GB/T 3304；政治面貌码遵循 GB/T 4762；婚姻状况码遵循 GB/T 2261.2。
 */
object StudentInfoCodeLookup {

    private val genderCodes: Map<String, String> = mapOf(
        "1" to "男",
        "2" to "女",
        "0" to "未说明",
        "9" to "未说明"
    )

    private val politicalStatusCodes: Map<String, String> = mapOf(
        "01" to "中共党员",
        "02" to "中共预备党员",
        "03" to "共青团员",
        "04" to "民革会员",
        "05" to "民盟盟员",
        "06" to "民建会员",
        "07" to "民进会员",
        "08" to "农工党党员",
        "09" to "致公党党员",
        "10" to "九三学社社员",
        "11" to "台盟盟员",
        "12" to "无党派人士",
        "13" to "群众"
    )

    private val idTypeCodes: Map<String, String> = mapOf(
        "1" to "居民身份证",
        "2" to "军官证",
        "3" to "武警警官证",
        "4" to "士兵证",
        "5" to "军队离退休干部证",
        "6" to "残疾人证",
        "7" to "残疾军人证（1-8级）",
        "8" to "外国护照",
        "9" to "港澳台居民身份证明",
        "10" to "外交护照",
        "11" to "公务护照",
        "12" to "普通护照",
        "13" to "台湾同胞来往大陆通行证",
        "14" to "港澳同胞回乡证",
        "15" to "海员证",
        "16" to "铁路职工工作证",
        "99" to "其他"
    )

    private val maritalStatusCodes: Map<String, String> = mapOf(
        "10" to "未婚",
        "20" to "已婚",
        "21" to "初婚",
        "22" to "再婚",
        "23" to "复婚",
        "30" to "丧偶",
        "40" to "离婚",
        "90" to "未说明"
    )

    private val educationCodes: Map<String, String> = mapOf(
        "10" to "研究生",
        "11" to "博士研究生",
        "12" to "硕士研究生",
        "20" to "大学本科",
        "30" to "大学专科",
        "40" to "中等职业教育",
        "41" to "中等专业学校",
        "42" to "职业高中",
        "43" to "技工学校",
        "60" to "普通高级中学",
        "70" to "初级中学",
        "80" to "小学",
        "90" to "其他"
    )

    private val degreeCodes: Map<String, String> = mapOf(
        "1" to "名誉博士",
        "2" to "博士",
        "3" to "硕士",
        "4" to "学士"
    )

    private val healthCodes: Map<String, String> = mapOf(
        "1" to "健康",
        "2" to "一般",
        "3" to "较弱",
        "4" to "有生理缺陷",
        "5" to "有残疾",
        "6" to "有精神病史"
    )

    /**
     * 安徽大学校区代码(待与学校核实/补充)
     *
     * 学校常见校区:磬苑校区(主校区/研究生院)、龙河校区(老校区)、蜀山校区(联合培养)等。
     * 下方为暂定映射 —— 实际使用前请打开「我的信息」查看「校区」字段实际值,
     * 在此文件中调整 key/value。命中失败时回退原始码,不会显示错。
     */
    private val campusCodes: Map<String, String> = mapOf(
        "1" to "磬苑校区",
        "2" to "龙河校区",
        "3" to "蜀山校区",
        "4" to "金寨路校区",
        "5" to "磬苑校区"
    )

    /**
     * 安徽大学宿舍楼栋代码。
     *
     * 磬苑校区宿舍常见命名:榴园、桂园、桔园、桃园、松园、梅园、竹园、蕙园、兰园、杏园、枣园、枫园、槐园、李园 等。
     * 龙河校区: 对应数字编号。
     * 码值来源: getList 返回的 JZWH_CPCODE / JZWH 成对字段，此处作为本地回退。
     */
    private val buildingCodes: Map<String, String> = mapOf(
        "56" to "榴园",
        "51" to "桂园",
        "52" to "桔园",
        "53" to "桃园",
        "54" to "松园",
        "55" to "梅园",
        // 磬苑校区其他楼栋（待补充）
        "57" to "竹园",
        "58" to "蕙园",
        "59" to "兰园",
        "60" to "杏园",
        "61" to "枣园",
        "62" to "枫园",
        "63" to "槐园",
        "64" to "李园",
        // 龙河校区
        "201" to "龙河 1 号楼",
        "202" to "龙河 2 号楼",
        "203" to "龙河 3 号楼",
    )

    private val nationCodes: Map<String, String> = mapOf(
        "01" to "汉族", "02" to "蒙古族", "03" to "回族", "04" to "藏族",
        "05" to "维吾尔族", "06" to "苗族", "07" to "彝族", "08" to "壮族",
        "09" to "布依族", "10" to "朝鲜族", "11" to "满族", "12" to "侗族",
        "13" to "瑶族", "14" to "白族", "15" to "土家族", "16" to "哈尼族",
        "17" to "哈萨克族", "18" to "傣族", "19" to "黎族", "20" to "傈僳族",
        "21" to "佤族", "22" to "畲族", "23" to "高山族", "24" to "拉祜族",
        "25" to "水族", "26" to "东乡族", "27" to "纳西族", "28" to "景颇族",
        "29" to "柯尔克孜族", "30" to "土族", "31" to "达斡尔族", "32" to "仫佬族",
        "33" to "羌族", "34" to "布朗族", "35" to "撒拉族", "36" to "毛南族",
        "37" to "仡佬族", "38" to "锡伯族", "39" to "阿昌族", "40" to "普米族",
        "41" to "塔吉克族", "42" to "怒族", "43" to "乌孜别克族", "44" to "俄罗斯族",
        "45" to "鄂温克族", "46" to "德昂族", "47" to "保安族", "48" to "裕固族",
        "49" to "京族", "50" to "塔塔尔族", "51" to "独龙族", "52" to "鄂伦春族",
        "53" to "赫哲族", "54" to "门巴族", "55" to "珞巴族", "56" to "基诺族",
        "97" to "其他", "98" to "外国血统中国籍人士"
    )

    // ── 教育相关码表 ────────────────────────────────────

    /** 是否在校 (getPageGrid: SFZX) */
    private val inSchoolCodes: Map<String, String> = mapOf(
        "1" to "在校",
        "0" to "毕业"
    )

    /** 是否在籍 (getPageGrid: SFZJ) */
    private val registeredCodes: Map<String, String> = mapOf(
        "1" to "在籍",
        "0" to "毕业"
    )

    /** 培养层次码 (getPageGrid: PYCCM, 表 ICDC_DM.XS_PYCC) —— 本地回退 */
    private val educationLevelCodes: Map<String, String> = mapOf(
        "1" to "博士研究生",
        "2" to "硕士研究生",
        "3" to "本科",
        "4" to "专科",
        "5" to "专升本"
    )

    // ── fetchUserInfo() 英文字段 → 中文标签映射 ──────────

    /**
     * /ep/userHome/getUserInfo 返回的 JSON key → 中文展示标签。
     * 未在此映射中的 key 按原样显示（首字母大写等回退）。
     */
    val userInfoFieldLabelMap: Map<String, String> = mapOf(
        "USER_NAME" to "姓名",
        "UNIT_NAME" to "培养单位",
        "CODENAME" to "性别",
        "SEX" to "性别",
        "SEX_CODE" to "性别",
        "XB" to "性别",
        "NATION" to "民族",
        "NATION_CODE" to "民族",
        "MZ" to "民族",
        "ID_NUMBER" to "身份证号",
        "SFZH" to "身份证号",
        "ID_TYPE" to "身份证件类型",
        "MOBILE" to "手机号",
        "PHONE" to "手机号",
        "SJHM" to "手机号",
        "POLITICAL_STATUS" to "政治面貌",
        "ZZMM" to "政治面貌",
        "MARITAL_STATUS" to "婚姻状况",
        "HYZK" to "婚姻状况",
        "EDUCATION" to "学历",
        "XL" to "学历",
        "DEGREE" to "学位",
        "XW" to "学位",
        "HEALTH" to "健康状况",
        "JKZK" to "健康状况",
        "NATIVE_PLACE" to "籍贯",
        "JG" to "籍贯",
        "BIRTH_DATE" to "出生日期",
        "CSRQ" to "出生日期",
        "STUDENT_ID" to "学号",
        "XH" to "学号",
        "GRADE" to "年级",
        "NJ" to "年级",
        "CLASS_NAME" to "班级",
        "BJ" to "班级",
        "MAJOR_NAME" to "专业",
        "ZY" to "专业",
        "DEPARTMENT" to "院系",
        "YX" to "院系",
        "CAMPUS" to "校区",
        "XQ" to "校区",
        "DORM_BUILDING" to "楼栋",
        "SSFJH" to "宿舍房间",
        "EMAIL" to "电子邮箱",
        "ADDRESS" to "通讯地址",
        "POSTAL_CODE" to "邮政编码",
        "BANK_CARD" to "银行卡号",
        "BIRTHDAY" to "出生日期",
        "ONE_CARD" to "校园卡号",
    )

    /**
     * fetchUserInfo 中应跳过的 JSON key。
     * 这些是冗余内部字段，其信息已由其他 key 提供解析后的值。
     */
    val skipUserInfoKeys: Set<String> = setOf(
        "USER_SEX",   // CODENAME 已提供解析后的性别
        "USER_UID",   // 内部 ID
        "UNIT_UID",   // UNIT_NAME 已提供解析后的单位名
    )

    private val fieldsToHide: Set<String> = setOf(
        "数据来源",
        "数据来源名称",
        "DATASOURCE",
        "DATA_SOURCE",
        "审批结果",
        "审批状态"
    )

    /**
     * 「码值字段」标签 → (匹配方式, 清洗后标签, 取值用字典) 的元数据。
     *
     * matchMode = "endsWith" : 标签以 suffix 结尾才匹配(去掉 suffix 得到展示标签)
     * matchMode = "exact"    : 标签等于 exactLabel 时匹配(展示标签即 displayLabel)
     */
    private data class CodeSpec(
        val matchMode: String,
        val key: String,
        val displayLabel: String,
        val dictionary: Map<String, String>
    )

    private val codeFieldSpecs: List<CodeSpec> = listOf(
        CodeSpec("endsWith", "性别码", "性别", genderCodes),
        CodeSpec("exact", "性别", "性别", genderCodes),
        CodeSpec("endsWith", "民族码", "民族", nationCodes),
        CodeSpec("exact", "民族", "民族", nationCodes),
        CodeSpec("endsWith", "政治面貌码", "政治面貌", politicalStatusCodes),
        CodeSpec("exact", "政治面貌", "政治面貌", politicalStatusCodes),
        CodeSpec("endsWith", "身份证件类型码", "身份证件类型", idTypeCodes),
        CodeSpec("endsWith", "证件类型码", "证件类型", idTypeCodes),
        CodeSpec("exact", "身份证件类型", "身份证件类型", idTypeCodes),
        CodeSpec("exact", "证件类型", "证件类型", idTypeCodes),
        CodeSpec("endsWith", "婚姻状况码", "婚姻状况", maritalStatusCodes),
        CodeSpec("exact", "婚姻状况", "婚姻状况", maritalStatusCodes),
        CodeSpec("endsWith", "学历码", "学历", educationCodes),
        CodeSpec("exact", "学历", "学历", educationCodes),
        CodeSpec("endsWith", "健康状况码", "健康状况", healthCodes),
        CodeSpec("exact", "健康状况", "健康状况", healthCodes),
        CodeSpec("endsWith", "学位码", "学位", degreeCodes),
        CodeSpec("exact", "学位", "学位", degreeCodes),
        // 教育相关码表
        CodeSpec("endsWith", "培养层次码", "培养层次", educationLevelCodes),
        CodeSpec("exact", "培养层次", "培养层次", educationLevelCodes),
        CodeSpec("endsWith", "是否在校码", "是否在校", inSchoolCodes),
        CodeSpec("exact", "是否在校", "是否在校", inSchoolCodes),
        CodeSpec("endsWith", "是否在籍码", "是否在籍", registeredCodes),
        CodeSpec("exact", "是否在籍", "是否在籍", registeredCodes),
        // 安大校内的具体业务码表
        CodeSpec("exact", "校区", "校区", campusCodes),
        CodeSpec("exact", "楼栋", "楼栋", buildingCodes),
        CodeSpec("exact", "所在楼栋", "楼栋", buildingCodes),
        CodeSpec("exact", "楼栋号", "楼栋", buildingCodes),
        CodeSpec("endsWith", "楼栋码", "楼栋", buildingCodes),
    )

    fun isHiddenField(label: String): Boolean {
        return fieldsToHide.any { label.equals(it, ignoreCase = true) }
    }

    /**
     * 将 fetchUserInfo 返回的英文 key 转为中文展示标签。
     * 未在 [userInfoFieldLabelMap] 中的 key 返回 null（调用方自行回退）。
     */
    fun resolveUserInfoLabel(key: String): String? {
        return userInfoFieldLabelMap[key]
    }

    /**
     * 解析一条「码值字段」为 (展示标签, 展示值)。
     * 找不到对应码表时返回 null,调用方按普通字段原样显示。
     */
    fun decode(label: String, rawValue: String): Pair<String, String>? {
        val trimmed = label.trim()
        val spec = codeFieldSpecs.firstOrNull {
            when (it.matchMode) {
                "exact" -> trimmed == it.key
                else -> trimmed == it.key || trimmed.endsWith(it.key)
            }
        } ?: return null
        val cleanedLabel = when (spec.matchMode) {
            "exact" -> spec.displayLabel
            else -> if (trimmed == spec.key) spec.displayLabel
                else trimmed.removeSuffix(spec.key).trim().ifBlank { spec.displayLabel }
        }
        val key = rawValue.trim()
        val decoded = spec.dictionary[key]
            ?: spec.dictionary[key.trimStart('0').ifBlank { "0" }]
            ?: rawValue
        return cleanedLabel to decoded
    }
}
