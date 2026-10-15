package com.callagent.host.notify

/**
 * Finds the one-time code in a verification SMS.
 *
 * Keyword-proximity heuristic in the spirit of SmsCodeHelper
 * (github.com/tianma8023/SmsCode): the message must mention a code keyword,
 * candidates are standalone 4–8 character tokens, and the candidate nearest
 * to a keyword wins. Numbers that read as money, durations, dates or phone
 * numbers are skipped, so "5分钟内有效" or "客服95588" are never copied.
 */
object VerificationCode {
    private val keyword = Regex(
        "验证码|校验码|检验码|确认码|激活码|动态码|安全码|认证码|识别码|授权码|登录码|短信码|取件码|提货码|" +
            "动态密码|交易码|随机码|一次性密码|验证代码|" +
            "verification code|verify code|security code|confirmation code|login code|sign-in code|" +
            "one[- ]time (?:pass)?code|one[- ]time password|passcode|\\botp\\b|\\bpin\\b|\\bcode\\b",
        RegexOption.IGNORE_CASE
    )

    /** A run of letters/digits not glued to other letters or digits (CJK text counts as a separator). */
    private val token = Regex("(?<![A-Za-z0-9])[A-Za-z0-9]{4,8}(?![A-Za-z0-9])")

    private val unitAfter = Regex("^\\s*(?:元|块|分钟|分|秒|小时|天|年|月|日|号|次|个|条|%|mins?|minutes?|hours?|secs?|seconds?|days?)", RegexOption.IGNORE_CASE)
    private val moneyBefore = Regex("(?:[¥￥$€£]|RMB|CNY|USD|HKD)\\s*$", RegexOption.IGNORE_CASE)
    private val phoneBefore = Regex("(?:电话|热线|客服|致电|拨打|回复|退订|回T|尾号|账号|卡号|tel|call|reply)\\s*[:：]?\\s*$", RegexOption.IGNORE_CASE)

    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val keywords = keyword.findAll(text).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        val candidates = token.findAll(text).mapNotNull { match ->
            val value = match.value
            val digits = value.count(Char::isDigit)
            if (digits == 0) return@mapNotNull null
            // Mixed codes need at least half digits so ordinary words never match.
            if (digits != value.length && (digits * 2 < value.length || value.any(Char::isLowerCase))) return@mapNotNull null
            val before = text.substring(0, match.range.first)
            val after = text.substring(match.range.last + 1)
            if (unitAfter.containsMatchIn(after) || moneyBefore.containsMatchIn(before) || phoneBefore.containsMatchIn(before)) {
                return@mapNotNull null
            }
            // Part of a decimal, time or date such as 12.50, 10:30 or 2026-10-15 ("G-123456" still counts).
            val glued = ".:-/"
            if (before.lastOrNull()?.let { it in glued } == true && before.dropLast(1).lastOrNull()?.isDigit() == true ||
                after.firstOrNull()?.let { it in glued } == true && after.getOrNull(1)?.isDigit() == true
            ) return@mapNotNull null
            if (keywords.any { it.first <= match.range.first && match.range.last <= it.last }) return@mapNotNull null
            val distance = keywords.minOf { range ->
                when {
                    match.range.first > range.last -> match.range.first - range.last
                    // A code just before its keyword ("123456是您的验证码") is as likely; weigh it slightly less.
                    else -> (range.first - match.range.last) + 2
                }
            }
            Candidate(value, distance, digits == value.length)
        }.toList()
        if (candidates.isEmpty()) return null
        return candidates.minWithOrNull(compareBy<Candidate>({ !it.numeric }, { it.distance }))?.value
    }

    private data class Candidate(val value: String, val distance: Int, val numeric: Boolean)
}
