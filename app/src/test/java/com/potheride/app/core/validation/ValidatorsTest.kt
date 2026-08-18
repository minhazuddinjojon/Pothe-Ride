package com.potheride.app.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun everyCommonWayOfTypingABdNumberNormalisesToOneForm() {
        val canonical = "+8801712345678"
        for (input in listOf(
            "01712345678",
            "+8801712345678",
            "8801712345678",
            "017 123 45678",
            "017-1234-5678",
            "1712345678"
        )) {
            assertEquals("failed for '$input'", canonical, Validators.normalizeBdPhone(input))
        }
    }

    @Test
    fun everyLiveOperatorPrefixIsAccepted() {
        for (prefix in listOf("13", "14", "15", "16", "17", "18", "19")) {
            assertTrue("prefix $prefix should be valid", Validators.validatePhone("0${prefix}12345678").isValid)
        }
    }

    @Test
    fun aLandlineOrUnissuedPrefixIsRejected() {
        assertNull(Validators.normalizeBdPhone("0212345678"))
        assertNull(Validators.normalizeBdPhone("01012345678"))
    }

    @Test
    fun numbersOfTheWrongLengthAreRejected() {
        assertNull(Validators.normalizeBdPhone("0171234567"))
        assertNull(Validators.normalizeBdPhone("017123456789"))
        assertNull(Validators.normalizeBdPhone(""))
    }

    @Test
    fun invalidPhonesCarryMessagesInBothLanguages() {
        val result = Validators.validatePhone("123")
        assertTrue(result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue(invalid.messageEn.isNotBlank())
        assertTrue(invalid.messageBn.isNotBlank())
    }

    @Test
    fun namesMustBeReasonablyLong() {
        assertFalse(Validators.validateName("A").isValid)
        assertFalse(Validators.validateName("  ").isValid)
        assertTrue(Validators.validateName("Rafiqul Islam").isValid)
        assertTrue(Validators.validateName("রফিকুল ইসলাম").isValid)
        assertFalse(Validators.validateName("x".repeat(100)).isValid)
    }

    @Test
    fun otpMustBeExactlyFourDigits() {
        assertTrue(Validators.validateOtp("1234").isValid)
        assertFalse(Validators.validateOtp("123").isValid)
        assertFalse(Validators.validateOtp("12345").isValid)
        assertFalse(Validators.validateOtp("12a4").isValid)
    }

    @Test
    fun plateNumbersNeedLettersAndDigits() {
        assertTrue(Validators.validatePlate("DHAKA METRO GA 11-2233").isValid)
        assertTrue(Validators.validatePlate("DHA-15-2231").isValid)
        assertFalse(Validators.validatePlate("12345").isValid)
        assertFalse(Validators.validatePlate("ABC").isValid)
    }

    @Test
    fun seatsAreBoundedByVehicleCapacity() {
        assertTrue(Validators.validateSeats(2, 4).isValid)
        assertTrue(Validators.validateSeats(4, 4).isValid)
        assertFalse(Validators.validateSeats(5, 4).isValid)
        assertFalse(Validators.validateSeats(0, 4).isValid)
    }

    @Test
    fun detourMustBeSmallEnoughToStillBeASharedRide() {
        assertTrue(Validators.validateDetour(1.5).isValid)
        assertFalse(Validators.validateDetour(0.0).isValid)
        assertFalse(Validators.validateDetour(25.0).isValid)
    }

    @Test
    fun departureTimeMustBeInTheFutureButNotAbsurdlyFarOut() {
        val now = 1_700_000_000_000L
        assertTrue(Validators.validateDepartureTime(now + 60_000, now).isValid)
        assertFalse(Validators.validateDepartureTime(now - 60 * 60_000, now).isValid)
        assertFalse(Validators.validateDepartureTime(now + 30L * 24 * 3600_000, now).isValid)
    }

    @Test
    fun aFewMinutesOfClockSkewIsToleratedOnDepartureTime() {
        val now = 1_700_000_000_000L
        assertTrue(Validators.validateDepartureTime(now - 60_000, now).isValid)
    }
}
