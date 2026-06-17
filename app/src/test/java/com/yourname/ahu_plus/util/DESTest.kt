package com.yourname.ahu_plus.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DESTest {

    @Test
    fun `strEnc with known input matches Node js output`() {
        // Pre-computed with: node tools/ahu_encrypt.js test pass 'LT-test123456'
        val expected = "D8D35E5019288C410303D47B562E51A9662495321AE3E266086D08F8A4E17C8650B87714D462E933C2C76468C2F1190F"
        val result = DES.strEnc("test" + "pass" + "LT-test123456", "1", "2", "3")
        assertEquals(expected, result)
    }

    @Test
    fun `strEnc with short input less than 4 chars`() {
        // "a" + "b" + "X" = "abX" (< 4 chars)
        val result = DES.strEnc("abX", "1", "2", "3")
        // Should produce exactly 16 hex chars (one 64-bit block)
        assertEquals(16, result.length)
    }

    @Test
    fun `strEnc with exactly 4 chars`() {
        val result = DES.strEnc("abcd", "1", "2", "3")
        assertEquals(16, result.length)
    }

    @Test
    fun `strEnc produces deterministic output`() {
        val input = "hello"
        val r1 = DES.strEnc(input, "1", "2", "3")
        val r2 = DES.strEnc(input, "1", "2", "3")
        assertEquals(r1, r2)
    }

    @Test
    fun `strEnc with empty data returns empty string`() {
        val result = DES.strEnc("", "1", "2", "3")
        assertEquals("", result)
    }

    @Test
    fun `getKeyBytes splits key into 64-bit blocks`() {
        // Single char '1' → 1 block (key length 1 < 4)
        val keyBytes = DES.getKeyBytes("1")
        assertEquals(1, keyBytes.size)
        assertEquals(64, keyBytes[0].size)
    }

    @Test
    fun `verify exact match with CAS shaped fixture`() {
        // Pre-computed with: node tools/ahu_encrypt.js TEST_USER TEST_PASSWORD 'LT-REDACTED-TOKEN'
        val username = "TEST_USER"
        val password = "TEST_PASSWORD"
        val lt = "LT-REDACTED-TOKEN"

        val plainText = username + password + lt
        val result = DES.strEnc(plainText, "1", "2", "3")

        assertEquals("18EE3D7A7D0C154461127E6F4EE7C4902BDE3E69A1114EB6", result.substring(0, 48))
    }
}
