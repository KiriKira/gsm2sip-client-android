package com.callagent.host.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodeTest {
    @Test
    fun findsCommonChineseCodes() {
        assertEquals("482913", VerificationCode.extract("【京东】验证码482913，5分钟内有效，请勿泄露给他人。"))
        assertEquals("8848", VerificationCode.extract("您的验证码是 8848，如非本人操作请致电95588。"))
        assertEquals("620194", VerificationCode.extract("【支付宝】校验码：620194，您正在进行身份验证，10分钟内有效。"))
        assertEquals("3301", VerificationCode.extract("3301是您的登录验证码，请于5分钟内填写。"))
        assertEquals("772105", VerificationCode.extract("尾号1234的卡正在登录，动态码772105，请勿告诉他人。"))
    }

    @Test
    fun findsEnglishAndPrefixedCodes() {
        assertEquals("123456", VerificationCode.extract("G-123456 is your Google verification code."))
        assertEquals("904512", VerificationCode.extract("Your verification code is:904512"))
        assertEquals("5531", VerificationCode.extract("Your OTP is 5531. Do not share it."))
        assertEquals("A7K29X", VerificationCode.extract("Use code A7K29X to sign in."))
    }

    @Test
    fun ignoresMessagesWithoutCodes() {
        assertNull(VerificationCode.extract("今晚 7 点一起吃饭吗？"))
        assertNull(VerificationCode.extract("您本月话费 128.50 元，余额 2048 元。"))
        assertNull(VerificationCode.extract("验证码将于 2026-10-15 失效，回复TD退订"))
        assertNull(VerificationCode.extract("Your code word is banana"))
        assertNull(VerificationCode.extract(null))
    }
}
