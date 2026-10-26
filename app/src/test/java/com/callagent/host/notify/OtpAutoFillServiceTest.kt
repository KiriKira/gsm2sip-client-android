package com.callagent.host.notify

import android.text.InputType
import com.callagent.host.notify.OtpAutoFillService.Field
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpAutoFillServiceTest {
    private val number = InputType.TYPE_CLASS_NUMBER
    private val text = InputType.TYPE_CLASS_TEXT

    @Test
    fun fillsOnlyEmptyFieldsThatLookLikeCodeFields() {
        assertTrue(OtpAutoFillService.looksLikeCodeField(Field(true, true, text, -1, "请输入验证码")))
        assertTrue(OtpAutoFillService.looksLikeCodeField(Field(true, true, text, -1, "com.bank:id/otp_input")))
        assertTrue("a short numeric field", OtpAutoFillService.looksLikeCodeField(Field(true, true, number, 6, "")))
        assertFalse("a filled field is never overwritten", OtpAutoFillService.looksLikeCodeField(Field(true, false, number, 6, "验证码")))
        assertFalse("a search box is not a code field", OtpAutoFillService.looksLikeCodeField(Field(true, true, text, -1, "搜索")))
        assertFalse("a phone number field is too long", OtpAutoFillService.looksLikeCodeField(Field(true, true, number, 11, "手机号")))
        assertFalse(OtpAutoFillService.looksLikeCodeField(Field(false, true, number, 6, "验证码")))
        assertFalse("a postal code is not a one-time code", OtpAutoFillService.looksLikeCodeField(Field(true, true, number, 7, "Zip code")))
        assertFalse(OtpAutoFillService.looksLikeCodeField(Field(true, true, text, -1, "邀请码")))
    }
}
