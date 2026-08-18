package com.potheride.app.core.pricing

/**
 * Payment rails the app supports. bKash, Nagad and Rocket are the three mobile
 * financial services that actually matter in Bangladesh; cash remains the default
 * because it is still how most shared rides settle.
 *
 * [requiresGateway] marks the methods that will need a real payment-gateway
 * integration before launch — the MVP records them as pending and settles them
 * manually, and the UI says so rather than pretending a transaction occurred.
 */
enum class PaymentMethod(
    val displayEn: String,
    val displayBn: String,
    val requiresGateway: Boolean
) {
    CASH("Cash", "নগদ টাকা", false),
    BKASH("bKash", "বিকাশ", true),
    NAGAD("Nagad", "নগদ", true),
    ROCKET("Rocket", "রকেট", true),
    CARD("Card", "কার্ড", true);

    companion object {
        /** Methods offered in the MVP build. */
        val enabledForMvp = listOf(CASH, BKASH, NAGAD, ROCKET)

        fun fromNameOrNull(value: String?): PaymentMethod? =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED;

    val isSettled: Boolean get() = this == COMPLETED || this == REFUNDED
}
