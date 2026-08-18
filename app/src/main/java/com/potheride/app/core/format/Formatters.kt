package com.potheride.app.core.format

import com.potheride.app.core.pricing.Taka

/** The two languages the app ships in. */
enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    BANGLA("bn");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}

/**
 * Number and money formatting that respects the selected language. Bangla users
 * expect Bengali digits (১২৩) and taka amounts grouped the South Asian way —
 * ১২,৩৪,৫৬৭ rather than 1,234,567 — so this cannot be delegated to a plain
 * thousands separator.
 */
object Formatters {

    private val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    /** Converts every ASCII digit in [input] to its Bengali equivalent. */
    fun toBengaliDigits(input: String): String = buildString(input.length) {
        for (c in input) {
            if (c in '0'..'9') append(bengaliDigits[c - '0']) else append(c)
        }
    }

    fun localizeDigits(input: String, language: AppLanguage): String =
        if (language == AppLanguage.BANGLA) toBengaliDigits(input) else input

    /**
     * Groups an integer using the South Asian lakh/crore convention: the last three
     * digits stay together, then every two digits before that.
     */
    fun groupSouthAsian(value: Long): String {
        val negative = value < 0
        val digits = kotlin.math.abs(value).toString()
        if (digits.length <= 3) return if (negative) "-$digits" else digits

        val lastThree = digits.substring(digits.length - 3)
        val rest = digits.substring(0, digits.length - 3)
        val grouped = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (grouped.isNotEmpty()) grouped.insert(0, ",")
            grouped.insert(0, rest.substring(start, i))
            i = start
        }
        val result = "$grouped,$lastThree"
        return if (negative) "-$result" else result
    }

    /** e.g. `৳1,240` in English, `৳১,২৪০` in Bangla. */
    fun money(amount: Taka, language: AppLanguage = AppLanguage.ENGLISH): String =
        "৳" + localizeDigits(groupSouthAsian(amount.whole), language)

    fun distance(km: Double, language: AppLanguage = AppLanguage.ENGLISH): String {
        val text = if (km < 1.0) {
            "${Math.round(km * 1000)} m"
        } else {
            String.format("%.1f km", km)
        }
        return localizeDigits(text, language)
    }

    /** Compact duration: `45 min` under an hour, `1h 20m` above it. */
    fun duration(minutes: Int, language: AppLanguage = AppLanguage.ENGLISH): String {
        val safe = maxOf(0, minutes)
        val text = if (safe < 60) "$safe min" else "${safe / 60}h ${safe % 60}m"
        return localizeDigits(text, language)
    }

    fun percent(ratio: Float, language: AppLanguage = AppLanguage.ENGLISH): String =
        localizeDigits("${Math.round(ratio * 100f)}%", language)

    /** Star ratings always show one decimal place, so 5 reads as `5.0`. */
    fun rating(value: Float, language: AppLanguage = AppLanguage.ENGLISH): String =
        localizeDigits(String.format("%.1f", value), language)

    /**
     * Masks the middle of a phone number for display next to a driver or passenger
     * you have not yet been matched with: `+8801712345678` becomes `+88017****5678`.
     */
    fun maskPhone(phone: String): String {
        val trimmed = phone.trim()
        if (trimmed.length <= 9) return trimmed
        val head = trimmed.take(6)
        val tail = trimmed.takeLast(4)
        return "$head****$tail"
    }
}
