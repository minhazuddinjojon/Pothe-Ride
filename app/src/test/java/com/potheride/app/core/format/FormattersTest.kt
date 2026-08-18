package com.potheride.app.core.format

import com.potheride.app.core.pricing.Taka
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun asciiDigitsBecomeBengaliDigits() {
        assertEquals("১২৩৪৫৬৭৮৯০", Formatters.toBengaliDigits("1234567890"))
    }

    @Test
    fun nonDigitsSurviveConversionUntouched() {
        assertEquals("৳১,২৪০ / আসন", Formatters.toBengaliDigits("৳1,240 / আসন"))
    }

    @Test
    fun englishKeepsAsciiDigits() {
        assertEquals("1240", Formatters.localizeDigits("1240", AppLanguage.ENGLISH))
    }

    @Test
    fun groupingUsesTheLakhCroreConvention() {
        // 1234567 is twelve lakh thirty-four thousand — grouped 12,34,567 in Bangla
        // and Bangladeshi English, not 1,234,567.
        assertEquals("12,34,567", Formatters.groupSouthAsian(1234567))
        assertEquals("1,240", Formatters.groupSouthAsian(1240))
        assertEquals("999", Formatters.groupSouthAsian(999))
        assertEquals("1,00,000", Formatters.groupSouthAsian(100000))
    }

    @Test
    fun groupingHandlesZeroAndNegatives() {
        assertEquals("0", Formatters.groupSouthAsian(0))
        assertEquals("-1,240", Formatters.groupSouthAsian(-1240))
    }

    @Test
    fun moneyCarriesTheTakaSign() {
        assertEquals("৳1,240", Formatters.money(Taka.ofTaka(1240L), AppLanguage.ENGLISH))
        assertEquals("৳১,২৪০", Formatters.money(Taka.ofTaka(1240L), AppLanguage.BANGLA))
    }

    @Test
    fun shortDistancesAreShownInMetres() {
        assertEquals("400 m", Formatters.distance(0.4))
        assertTrue(Formatters.distance(1.5).endsWith("km"))
    }

    @Test
    fun durationsOverAnHourSwitchToHoursAndMinutes() {
        assertEquals("45 min", Formatters.duration(45))
        assertEquals("1h 20m", Formatters.duration(80))
        assertEquals("0 min", Formatters.duration(-5))
    }

    @Test
    fun percentagesRoundToWholeNumbers() {
        assertEquals("82%", Formatters.percent(0.8234f))
        assertEquals("৮২%", Formatters.percent(0.8234f, AppLanguage.BANGLA))
    }

    @Test
    fun ratingsAlwaysShowOneDecimal() {
        assertEquals("5.0", Formatters.rating(5.0f))
        assertEquals("4.8", Formatters.rating(4.75f))
    }

    @Test
    fun phoneNumbersAreMaskedInTheMiddle() {
        assertEquals("+88017****5678", Formatters.maskPhone("+8801712345678"))
    }

    @Test
    fun shortPhoneStringsAreLeftAloneRatherThanMangled() {
        assertEquals("12345", Formatters.maskPhone("12345"))
    }

    @Test
    fun languageCodesResolveAndFallBackSafely() {
        assertEquals(AppLanguage.BANGLA, AppLanguage.fromCode("bn"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("klingon"))
    }
}
