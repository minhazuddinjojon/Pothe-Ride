package com.potheride.app.core.validation

/** Result of validating a single field. */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val messageEn: String, val messageBn: String) : ValidationResult

    val isValid: Boolean get() = this is Valid
}

/**
 * Input rules for the fields the MVP collects. Written as pure functions so the
 * same rules run in unit tests, in the form UI, and (later) server-side, instead of
 * being reimplemented slightly differently in each place.
 */
object Validators {

    /** Mobile operator prefixes currently issued in Bangladesh. */
    private val validOperatorPrefixes = setOf("13", "14", "15", "16", "17", "18", "19")

    /**
     * Normalises any of the ways a Bangladeshi number gets typed — `01712345678`,
     * `+8801712345678`, `8801712345678`, or with spaces and dashes — into the single
     * canonical `+8801XXXXXXXXX` form the database stores. Returns null if the number
     * cannot be a valid BD mobile number.
     */
    fun normalizeBdPhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val national = when {
            digits.length == 13 && digits.startsWith("880") -> digits.substring(3)
            digits.length == 11 && digits.startsWith("0") -> digits.substring(1)
            digits.length == 10 && digits.startsWith("1") -> digits
            else -> return null
        }
        if (national.length != 10 || !national.startsWith("1")) return null
        if (national.substring(0, 2) !in validOperatorPrefixes) return null
        return "+880$national"
    }

    fun validatePhone(raw: String): ValidationResult =
        if (normalizeBdPhone(raw) != null) ValidationResult.Valid
        else ValidationResult.Invalid(
            "Enter a valid Bangladeshi mobile number, e.g. 01712345678",
            "একটি সঠিক বাংলাদেশি মোবাইল নম্বর দিন, যেমন ০১৭১২৩৪৫৬৭৮"
        )

    fun validateName(raw: String): ValidationResult {
        val trimmed = raw.trim()
        return when {
            trimmed.length < 2 -> ValidationResult.Invalid(
                "Please enter your full name.", "আপনার পুরো নাম লিখুন।"
            )
            trimmed.length > 60 -> ValidationResult.Invalid(
                "That name is too long.", "নামটি অনেক বড় হয়ে গেছে।"
            )
            else -> ValidationResult.Valid
        }
    }

    const val OTP_LENGTH = 4

    fun validateOtp(raw: String): ValidationResult =
        if (raw.length == OTP_LENGTH && raw.all { it.isDigit() }) ValidationResult.Valid
        else ValidationResult.Invalid(
            "Enter the $OTP_LENGTH-digit code we sent you.",
            "আমরা যে $OTP_LENGTH সংখ্যার কোড পাঠিয়েছি সেটি লিখুন।"
        )

    /**
     * Bangladeshi plates look like `DHAKA METRO-GA-11-2233`, but drivers type them a
     * dozen different ways. The MVP accepts anything with at least one letter and at
     * least four digits rather than rejecting legitimate plates on a strict regex.
     */
    fun validatePlate(raw: String): ValidationResult {
        val trimmed = raw.trim()
        val letters = trimmed.count { it.isLetter() }
        val digits = trimmed.count { it.isDigit() }
        return when {
            trimmed.length < 4 -> ValidationResult.Invalid(
                "Enter the vehicle's plate number.", "গাড়ির নম্বর প্লেট লিখুন।"
            )
            letters < 1 || digits < 4 -> ValidationResult.Invalid(
                "That doesn't look like a plate number.", "এটি নম্বর প্লেটের মতো মনে হচ্ছে না।"
            )
            else -> ValidationResult.Valid
        }
    }

    fun validateSeats(seats: Int, capacity: Int): ValidationResult = when {
        seats < 1 -> ValidationResult.Invalid("Offer at least one seat.", "অন্তত একটি আসন দিন।")
        seats > capacity -> ValidationResult.Invalid(
            "This vehicle seats $capacity.", "এই গাড়িতে $capacity জন বসতে পারে।"
        )
        else -> ValidationResult.Valid
    }

    fun validateDetour(km: Double): ValidationResult = when {
        km < 0.1 -> ValidationResult.Invalid(
            "Allow at least 100 m of detour.", "অন্তত ১০০ মিটার ঘুরপথ রাখুন।"
        )
        km > 10.0 -> ValidationResult.Invalid(
            "A detour over 10 km isn't a shared ride.", "১০ কিমির বেশি ঘুরপথ শেয়ার্ড রাইড নয়।"
        )
        else -> ValidationResult.Valid
    }

    /** Trips must depart in the future, and no more than a week out. */
    fun validateDepartureTime(departureMillis: Long, nowMillis: Long): ValidationResult = when {
        departureMillis < nowMillis - 5 * 60_000L -> ValidationResult.Invalid(
            "Departure time is in the past.", "ছাড়ার সময় অতীতে পড়ে গেছে।"
        )
        departureMillis > nowMillis + 7 * 24 * 60 * 60_000L -> ValidationResult.Invalid(
            "Publish routes up to a week ahead.", "সর্বোচ্চ এক সপ্তাহ আগে রুট প্রকাশ করা যায়।"
        )
        else -> ValidationResult.Valid
    }
}
