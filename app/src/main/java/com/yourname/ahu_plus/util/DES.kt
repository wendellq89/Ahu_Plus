package com.yourname.ahu_plus.util

/**
 * 纯 Kotlin 移植 des.js 的 3DES 加密算法。
 *
 * 用于安徽大学 CAS 登录的密码加密。
 * 从 https://one.ahu.edu.cn/cas/comm/js/des.js 逐函数移植。
 *
 * 用法:
 *   val encrypted = DES.strEnc(username + password + lt, "1", "2", "3")
 */
object DES {

    /**
     * 三重 DES 加密，返回十六进制字符串。
     *
     * @param data      明文
     * @param firstKey  第一重密钥
     * @param secondKey 第二重密钥
     * @param thirdKey  第三重密钥
     * @return 加密后的十六进制字符串
     */
    fun strEnc(data: String, firstKey: String?, secondKey: String?, thirdKey: String?): String {
        val leng = data.length
        val encData = StringBuilder()

        val firstKeyBt: List<IntArray>?
        val firstLength: Int
        if (!firstKey.isNullOrEmpty()) {
            firstKeyBt = getKeyBytes(firstKey)
            firstLength = firstKeyBt.size
        } else {
            firstKeyBt = null
            firstLength = 0
        }

        val secondKeyBt: List<IntArray>?
        val secondLength: Int
        if (!secondKey.isNullOrEmpty()) {
            secondKeyBt = getKeyBytes(secondKey)
            secondLength = secondKeyBt.size
        } else {
            secondKeyBt = null
            secondLength = 0
        }

        val thirdKeyBt: List<IntArray>?
        val thirdLength: Int
        if (!thirdKey.isNullOrEmpty()) {
            thirdKeyBt = getKeyBytes(thirdKey)
            thirdLength = thirdKeyBt.size
        } else {
            thirdKeyBt = null
            thirdLength = 0
        }

        if (leng > 0) {
            if (leng < 4) {
                val bt = strToBt(data)
                val encByte = doEncrypt(bt, firstKeyBt, firstLength, secondKeyBt, secondLength, thirdKeyBt, thirdLength)
                encData.append(bt64ToHex(encByte))
            } else {
                val iterator = leng / 4
                val remainder = leng % 4
                for (i in 0 until iterator) {
                    val tempData = data.substring(i * 4, i * 4 + 4)
                    val tempByte = strToBt(tempData)
                    val encByte = doEncrypt(tempByte, firstKeyBt, firstLength, secondKeyBt, secondLength, thirdKeyBt, thirdLength)
                    encData.append(bt64ToHex(encByte))
                }
                if (remainder > 0) {
                    val remainderData = data.substring(iterator * 4, leng)
                    val tempByte = strToBt(remainderData)
                    val encByte = doEncrypt(tempByte, firstKeyBt, firstLength, secondKeyBt, secondLength, thirdKeyBt, thirdLength)
                    encData.append(bt64ToHex(encByte))
                }
            }
        }
        return encData.toString()
    }

    private fun doEncrypt(
        bt: IntArray,
        firstKeyBt: List<IntArray>?, firstLength: Int,
        secondKeyBt: List<IntArray>?, secondLength: Int,
        thirdKeyBt: List<IntArray>?, thirdLength: Int
    ): IntArray {
        var tempBt = bt
        for (x in 0 until firstLength) {
            tempBt = enc(tempBt, firstKeyBt!![x])
        }
        for (y in 0 until secondLength) {
            tempBt = enc(tempBt, secondKeyBt!![y])
        }
        for (z in 0 until thirdLength) {
            tempBt = enc(tempBt, thirdKeyBt!![z])
        }
        return tempBt
    }

    // ── 密钥处理 ──────────────────────────────────────

    /** 将密钥字符串转为 64 位块数组（保留 public 供测试使用） */
    fun getKeyBytes(key: String): List<IntArray> {
        val keyBytes = mutableListOf<IntArray>()
        val leng = key.length
        val iterator = leng / 4
        val remainder = leng % 4
        for (i in 0 until iterator) {
            keyBytes.add(strToBt(key.substring(i * 4, i * 4 + 4)))
        }
        if (remainder > 0) {
            keyBytes.add(strToBt(key.substring(iterator * 4, leng)))
        }
        return keyBytes
    }

    // ── 字符串 ↔ 位数组 ───────────────────────────────

    /** 将长度 ≤ 4 的字符串转为 64 位数组（每字符 16 位，高位在前） */
    private fun strToBt(str: String): IntArray {
        val bt = IntArray(64)
        val leng = str.length
        if (leng < 4) {
            for (i in 0 until leng) {
                val k = str[i].code
                for (j in 0..15) {
                    bt[16 * i + j] = (k shr (15 - j)) and 1
                }
            }
            for (p in leng until 4) {
                for (q in 0..15) {
                    bt[16 * p + q] = 0
                }
            }
        } else {
            for (i in 0..3) {
                val k = str[i].code
                for (j in 0..15) {
                    bt[16 * i + j] = (k shr (15 - j)) and 1
                }
            }
        }
        return bt
    }

    /** 64 位数组 → 16 字符十六进制字符串 */
    private fun bt64ToHex(byteData: IntArray): String {
        val hex = StringBuilder()
        for (i in 0..15) {
            val bt = StringBuilder()
            for (j in 0..3) {
                bt.append(byteData[i * 4 + j])
            }
            hex.append(bt4ToHex(bt.toString()))
        }
        return hex.toString()
    }

    /** 4 位二进制字符串 → 1 字符十六进制 */
    private fun bt4ToHex(binary: String): String {
        return when (binary) {
            "0000" -> "0"
            "0001" -> "1"
            "0010" -> "2"
            "0011" -> "3"
            "0100" -> "4"
            "0101" -> "5"
            "0110" -> "6"
            "0111" -> "7"
            "1000" -> "8"
            "1001" -> "9"
            "1010" -> "A"
            "1011" -> "B"
            "1100" -> "C"
            "1101" -> "D"
            "1110" -> "E"
            "1111" -> "F"
            else -> "0"
        }
    }

    /** 整数 0-15 → 4 位二进制字符串 */
    private fun getBoxBinary(i: Int): String {
        return when (i) {
            0 -> "0000"
            1 -> "0001"
            2 -> "0010"
            3 -> "0011"
            4 -> "0100"
            5 -> "0101"
            6 -> "0110"
            7 -> "0111"
            8 -> "1000"
            9 -> "1001"
            10 -> "1010"
            11 -> "1011"
            12 -> "1100"
            13 -> "1101"
            14 -> "1110"
            15 -> "1111"
            else -> "0000"
        }
    }

    // ── DES 核心 ──────────────────────────────────────

    /** 单次 DES 加密 */
    private fun enc(dataByte: IntArray, keyByte: IntArray): IntArray {
        val keys = generateKeys(keyByte)
        val ipByte = initPermute(dataByte)
        val ipLeft = IntArray(32)
        val ipRight = IntArray(32)
        val tempLeft = IntArray(32)

        for (k in 0..31) {
            ipLeft[k] = ipByte[k]
            ipRight[k] = ipByte[32 + k]
        }

        for (i in 0..15) {
            for (j in 0..31) {
                tempLeft[j] = ipLeft[j]
                ipLeft[j] = ipRight[j]
            }
            val key = IntArray(48)
            for (m in 0..47) {
                key[m] = keys[i][m]
            }
            val tempRight = xor(
                pPermute(sBoxPermute(xor(expandPermute(ipRight), key))),
                tempLeft
            )
            for (n in 0..31) {
                ipRight[n] = tempRight[n]
            }
        }

        val finalData = IntArray(64)
        for (i in 0..31) {
            finalData[i] = ipRight[i]
            finalData[32 + i] = ipLeft[i]
        }
        return finallyPermute(finalData)
    }

    /** 生成 16 轮子密钥 */
    private fun generateKeys(keyByte: IntArray): Array<IntArray> {
        val key = IntArray(56)
        val keys = Array(16) { IntArray(48) }
        val loop = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

        // PC-1 置换
        for (i in 0..6) {
            for (j in 0..7) {
                key[i * 8 + j] = keyByte[8 * (7 - j) + i]
            }
        }

        for (i in 0..15) {
            var tempLeft: Int
            var tempRight: Int
            for (j in 0 until loop[i]) {
                tempLeft = key[0]
                tempRight = key[28]
                for (k in 0..26) {
                    key[k] = key[k + 1]
                    key[28 + k] = key[29 + k]
                }
                key[27] = tempLeft
                key[55] = tempRight
            }

            // PC-2 置换 → 48 位子密钥
            val tempKey = IntArray(48)
            tempKey[0] = key[13]; tempKey[1] = key[16]; tempKey[2] = key[10]; tempKey[3] = key[23]
            tempKey[4] = key[0]; tempKey[5] = key[4]; tempKey[6] = key[2]; tempKey[7] = key[27]
            tempKey[8] = key[14]; tempKey[9] = key[5]; tempKey[10] = key[20]; tempKey[11] = key[9]
            tempKey[12] = key[22]; tempKey[13] = key[18]; tempKey[14] = key[11]; tempKey[15] = key[3]
            tempKey[16] = key[25]; tempKey[17] = key[7]; tempKey[18] = key[15]; tempKey[19] = key[6]
            tempKey[20] = key[26]; tempKey[21] = key[19]; tempKey[22] = key[12]; tempKey[23] = key[1]
            tempKey[24] = key[40]; tempKey[25] = key[51]; tempKey[26] = key[30]; tempKey[27] = key[36]
            tempKey[28] = key[46]; tempKey[29] = key[54]; tempKey[30] = key[29]; tempKey[31] = key[39]
            tempKey[32] = key[50]; tempKey[33] = key[44]; tempKey[34] = key[32]; tempKey[35] = key[47]
            tempKey[36] = key[43]; tempKey[37] = key[48]; tempKey[38] = key[38]; tempKey[39] = key[55]
            tempKey[40] = key[33]; tempKey[41] = key[52]; tempKey[42] = key[45]; tempKey[43] = key[41]
            tempKey[44] = key[49]; tempKey[45] = key[35]; tempKey[46] = key[28]; tempKey[47] = key[31]

            for (m in 0..47) {
                keys[i][m] = tempKey[m]
            }
        }
        return keys
    }

    /** 初始置换 IP */
    private fun initPermute(originalData: IntArray): IntArray {
        val ipByte = IntArray(64)
        var m = 1
        var n = 0
        for (i in 0..3) {
            for (j in 7 downTo 0) {
                ipByte[i * 8 + (7 - j)] = originalData[j * 8 + m]
                ipByte[i * 8 + (7 - j) + 32] = originalData[j * 8 + n]
            }
            m += 2
            n += 2
        }
        return ipByte
    }

    /** 扩展置换 E */
    private fun expandPermute(rightData: IntArray): IntArray {
        val epByte = IntArray(48)
        for (i in 0..7) {
            epByte[i * 6 + 0] = if (i == 0) rightData[31] else rightData[i * 4 - 1]
            epByte[i * 6 + 1] = rightData[i * 4 + 0]
            epByte[i * 6 + 2] = rightData[i * 4 + 1]
            epByte[i * 6 + 3] = rightData[i * 4 + 2]
            epByte[i * 6 + 4] = rightData[i * 4 + 3]
            epByte[i * 6 + 5] = if (i == 7) rightData[0] else rightData[i * 4 + 4]
        }
        return epByte
    }

    /** 异或 */
    private fun xor(byteOne: IntArray, byteTwo: IntArray): IntArray {
        val xorByte = IntArray(byteOne.size)
        for (i in byteOne.indices) {
            xorByte[i] = byteOne[i] xor byteTwo[i]
        }
        return xorByte
    }

    /** S 盒置换 */
    private fun sBoxPermute(expandByte: IntArray): IntArray {
        val sBoxByte = IntArray(32)

        val s1 = arrayOf(
            intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7),
            intArrayOf(0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8),
            intArrayOf(4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0),
            intArrayOf(15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13)
        )
        val s2 = arrayOf(
            intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10),
            intArrayOf(3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5),
            intArrayOf(0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15),
            intArrayOf(13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9)
        )
        val s3 = arrayOf(
            intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8),
            intArrayOf(13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1),
            intArrayOf(13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7),
            intArrayOf(1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12)
        )
        val s4 = arrayOf(
            intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15),
            intArrayOf(13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9),
            intArrayOf(10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4),
            intArrayOf(3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14)
        )
        val s5 = arrayOf(
            intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9),
            intArrayOf(14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6),
            intArrayOf(4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14),
            intArrayOf(11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3)
        )
        val s6 = arrayOf(
            intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11),
            intArrayOf(10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8),
            intArrayOf(9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6),
            intArrayOf(4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13)
        )
        val s7 = arrayOf(
            intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1),
            intArrayOf(13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6),
            intArrayOf(1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2),
            intArrayOf(6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12)
        )
        val s8 = arrayOf(
            intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7),
            intArrayOf(1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2),
            intArrayOf(7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8),
            intArrayOf(2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11)
        )

        for (m in 0..7) {
            val i = expandByte[m * 6 + 0] * 2 + expandByte[m * 6 + 5]
            val j = expandByte[m * 6 + 1] * 8 +
                    expandByte[m * 6 + 2] * 4 +
                    expandByte[m * 6 + 3] * 2 +
                    expandByte[m * 6 + 4]
            val binary = when (m) {
                0 -> getBoxBinary(s1[i][j])
                1 -> getBoxBinary(s2[i][j])
                2 -> getBoxBinary(s3[i][j])
                3 -> getBoxBinary(s4[i][j])
                4 -> getBoxBinary(s5[i][j])
                5 -> getBoxBinary(s6[i][j])
                6 -> getBoxBinary(s7[i][j])
                7 -> getBoxBinary(s8[i][j])
                else -> "0000"
            }
            sBoxByte[m * 4 + 0] = binary[0].digitToInt()
            sBoxByte[m * 4 + 1] = binary[1].digitToInt()
            sBoxByte[m * 4 + 2] = binary[2].digitToInt()
            sBoxByte[m * 4 + 3] = binary[3].digitToInt()
        }
        return sBoxByte
    }

    /** P 盒置换 */
    private fun pPermute(sBoxByte: IntArray): IntArray {
        val pBoxPermute = IntArray(32)
        pBoxPermute[0] = sBoxByte[15]; pBoxPermute[1] = sBoxByte[6]; pBoxPermute[2] = sBoxByte[19]
        pBoxPermute[3] = sBoxByte[20]; pBoxPermute[4] = sBoxByte[28]; pBoxPermute[5] = sBoxByte[11]
        pBoxPermute[6] = sBoxByte[27]; pBoxPermute[7] = sBoxByte[16]; pBoxPermute[8] = sBoxByte[0]
        pBoxPermute[9] = sBoxByte[14]; pBoxPermute[10] = sBoxByte[22]; pBoxPermute[11] = sBoxByte[25]
        pBoxPermute[12] = sBoxByte[4]; pBoxPermute[13] = sBoxByte[17]; pBoxPermute[14] = sBoxByte[30]
        pBoxPermute[15] = sBoxByte[9]; pBoxPermute[16] = sBoxByte[1]; pBoxPermute[17] = sBoxByte[7]
        pBoxPermute[18] = sBoxByte[23]; pBoxPermute[19] = sBoxByte[13]; pBoxPermute[20] = sBoxByte[31]
        pBoxPermute[21] = sBoxByte[26]; pBoxPermute[22] = sBoxByte[2]; pBoxPermute[23] = sBoxByte[8]
        pBoxPermute[24] = sBoxByte[18]; pBoxPermute[25] = sBoxByte[12]; pBoxPermute[26] = sBoxByte[29]
        pBoxPermute[27] = sBoxByte[5]; pBoxPermute[28] = sBoxByte[21]; pBoxPermute[29] = sBoxByte[10]
        pBoxPermute[30] = sBoxByte[3]; pBoxPermute[31] = sBoxByte[24]
        return pBoxPermute
    }

    /** 最终置换 FP (IP⁻¹) */
    private fun finallyPermute(endByte: IntArray): IntArray {
        val fpByte = IntArray(64)
        fpByte[0] = endByte[39]; fpByte[1] = endByte[7]; fpByte[2] = endByte[47]
        fpByte[3] = endByte[15]; fpByte[4] = endByte[55]; fpByte[5] = endByte[23]
        fpByte[6] = endByte[63]; fpByte[7] = endByte[31]; fpByte[8] = endByte[38]
        fpByte[9] = endByte[6]; fpByte[10] = endByte[46]; fpByte[11] = endByte[14]
        fpByte[12] = endByte[54]; fpByte[13] = endByte[22]; fpByte[14] = endByte[62]
        fpByte[15] = endByte[30]; fpByte[16] = endByte[37]; fpByte[17] = endByte[5]
        fpByte[18] = endByte[45]; fpByte[19] = endByte[13]; fpByte[20] = endByte[53]
        fpByte[21] = endByte[21]; fpByte[22] = endByte[61]; fpByte[23] = endByte[29]
        fpByte[24] = endByte[36]; fpByte[25] = endByte[4]; fpByte[26] = endByte[44]
        fpByte[27] = endByte[12]; fpByte[28] = endByte[52]; fpByte[29] = endByte[20]
        fpByte[30] = endByte[60]; fpByte[31] = endByte[28]; fpByte[32] = endByte[35]
        fpByte[33] = endByte[3]; fpByte[34] = endByte[43]; fpByte[35] = endByte[11]
        fpByte[36] = endByte[51]; fpByte[37] = endByte[19]; fpByte[38] = endByte[59]
        fpByte[39] = endByte[27]; fpByte[40] = endByte[34]; fpByte[41] = endByte[2]
        fpByte[42] = endByte[42]; fpByte[43] = endByte[10]; fpByte[44] = endByte[50]
        fpByte[45] = endByte[18]; fpByte[46] = endByte[58]; fpByte[47] = endByte[26]
        fpByte[48] = endByte[33]; fpByte[49] = endByte[1]; fpByte[50] = endByte[41]
        fpByte[51] = endByte[9]; fpByte[52] = endByte[49]; fpByte[53] = endByte[17]
        fpByte[54] = endByte[57]; fpByte[55] = endByte[25]; fpByte[56] = endByte[32]
        fpByte[57] = endByte[0]; fpByte[58] = endByte[40]; fpByte[59] = endByte[8]
        fpByte[60] = endByte[48]; fpByte[61] = endByte[16]; fpByte[62] = endByte[56]
        fpByte[63] = endByte[24]
        return fpByte
    }
}
