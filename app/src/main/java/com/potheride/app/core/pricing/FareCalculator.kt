package com.potheride.app.core.pricing

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Vehicle classes available in the app. Kept in the pure core (rather than in the
 * Room entities) so pricing can be unit-tested without Android on the classpath;
 * the database layer stores the enum name as TEXT.
 */
enum class VehicleClass(val displayEn: String, val displayBn: String, val defaultSeats: Int) {
    BIKE("Bike", "বাইক", 1),
    CNG("CNG", "সিএনজি", 3),
    CAR("Car", "কার", 4),
    MICROBUS("Microbus", "মাইক্রোবাস", 8);

    companion object {
        fun fromNameOrNull(value: String?): VehicleClass? =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * Money in Bangladeshi taka, held as whole poisha to avoid the rounding drift you
 * get from accumulating Doubles across base fare, per-km charges, discounts and
 * commission. Everything the passenger and driver see comes out of this type.
 */
@JvmInline
value class Taka private constructor(val poisha: Long) : Comparable<Taka> {

    val whole: Long get() = poisha / 100
    val toDouble: Double get() = poisha / 100.0

    operator fun plus(other: Taka) = Taka(poisha + other.poisha)
    operator fun minus(other: Taka) = Taka(poisha - other.poisha)
    operator fun times(factor: Int) = Taka(poisha * factor)
    operator fun times(factor: Double) = Taka((poisha * factor).roundToLong())

    /** Rounds to the nearest whole taka — nobody in Dhaka settles up in poisha. */
    fun roundedToTaka(): Taka = Taka(Math.round(poisha / 100.0) * 100)

    override fun compareTo(other: Taka): Int = poisha.compareTo(other.poisha)
    override fun toString(): String = "৳$whole"

    companion object {
        val ZERO = Taka(0)
        fun ofTaka(amount: Double) = Taka((amount * 100).roundToLong())
        fun ofTaka(amount: Long) = Taka(amount * 100)
        fun ofPoisha(amount: Long) = Taka(amount)
    }
}

/**
 * Per-vehicle pricing inputs. Rates are editable by an admin in a real deployment —
 * [PricingRules.DEFAULT] is only the seed table.
 */
data class VehicleRate(
    val baseFare: Taka,
    val perKm: Taka,
    val perMinute: Taka,
    val minimumFare: Taka
)

data class PricingRules(
    val rates: Map<VehicleClass, VehicleRate>,
    /** Share of the fare the platform keeps. Drivers see net earnings after this. */
    val platformCommission: Double = 0.12,
    /** Extra multiplier applied between [nightStartHour] and [nightEndHour]. */
    val nightSurcharge: Double = 0.15,
    val nightStartHour: Int = 23,
    val nightEndHour: Int = 5,
    /** Deepest discount a shared ride can reach, at 100% route overlap. */
    val maxSharedDiscount: Double = 0.50,
    /** Discount floor, applied even to a barely-overlapping match. */
    val minSharedDiscount: Double = 0.30
) {
    companion object {
        val DEFAULT = PricingRules(
            rates = mapOf(
                VehicleClass.BIKE to VehicleRate(
                    baseFare = Taka.ofTaka(20L), perKm = Taka.ofTaka(9L),
                    perMinute = Taka.ofTaka(0.5), minimumFare = Taka.ofTaka(30L)
                ),
                VehicleClass.CNG to VehicleRate(
                    baseFare = Taka.ofTaka(40L), perKm = Taka.ofTaka(14L),
                    perMinute = Taka.ofTaka(1.0), minimumFare = Taka.ofTaka(60L)
                ),
                VehicleClass.CAR to VehicleRate(
                    baseFare = Taka.ofTaka(60L), perKm = Taka.ofTaka(19L),
                    perMinute = Taka.ofTaka(1.5), minimumFare = Taka.ofTaka(90L)
                ),
                VehicleClass.MICROBUS to VehicleRate(
                    baseFare = Taka.ofTaka(100L), perKm = Taka.ofTaka(24L),
                    perMinute = Taka.ofTaka(2.0), minimumFare = Taka.ofTaka(150L)
                )
            )
        )
    }
}

/**
 * Every line item behind a quoted fare. The app shows this breakdown to both sides
 * so a passenger can see why a number is what it is, and a driver can see the
 * commission that was deducted — an opaque single figure is the fastest way to lose
 * trust on both ends of a marketplace.
 */
data class FareBreakdown(
    val vehicleClass: VehicleClass,
    val distanceKm: Double,
    val durationMinutes: Int,
    val baseFare: Taka,
    val distanceFare: Taka,
    val timeFare: Taka,
    val nightSurcharge: Taka,
    val grossFare: Taka,
    val sharedDiscountRate: Double,
    val sharedDiscount: Taka,
    val perSeatFare: Taka,
    val seats: Int,
    val totalFare: Taka,
    val platformFee: Taka,
    val driverEarnings: Taka
) {
    val sharedDiscountPercent: Int get() = Math.round(sharedDiscountRate * 100).toInt()
}

object FareCalculator {

    /**
     * @param distanceKm distance the passenger actually rides *along the driver's
     *   route* — not the straight line between pickup and drop-off, which would
     *   under-charge on any route that bends.
     * @param routeOverlapRatio 0..1 from the matcher. A higher overlap means the
     *   driver was going that way anyway, so the passenger pays less.
     * @param departureHour local hour (0-23) used for the night surcharge.
     */
    fun calculate(
        vehicleClass: VehicleClass,
        distanceKm: Double,
        durationMinutes: Int,
        seats: Int,
        routeOverlapRatio: Float,
        departureHour: Int,
        rules: PricingRules = PricingRules.DEFAULT
    ): FareBreakdown {
        require(seats >= 1) { "seats must be at least 1" }
        val safeDistance = max(0.0, distanceKm)
        val safeDuration = max(0, durationMinutes)
        val rate = rules.rates[vehicleClass] ?: rules.rates.getValue(VehicleClass.CAR)

        val distanceFare = rate.perKm * safeDistance
        val timeFare = rate.perMinute * safeDuration
        val subtotal = rate.baseFare + distanceFare + timeFare

        val isNight = isNightHour(departureHour, rules)
        val nightExtra = if (isNight) subtotal * rules.nightSurcharge else Taka.ZERO
        val gross = subtotal + nightExtra

        // Discount scales linearly with overlap between the floor and the ceiling.
        val overlap = routeOverlapRatio.toDouble().coerceIn(0.0, 1.0)
        val discountRate = rules.minSharedDiscount +
            (rules.maxSharedDiscount - rules.minSharedDiscount) * overlap
        val discount = gross * discountRate

        val discounted = (gross - discount).roundedToTaka()
        val perSeat = maxOf(discounted, rate.minimumFare).roundedToTaka()
        val total = perSeat * seats
        val platformFee = (total * rules.platformCommission).roundedToTaka()

        return FareBreakdown(
            vehicleClass = vehicleClass,
            distanceKm = safeDistance,
            durationMinutes = safeDuration,
            baseFare = rate.baseFare,
            distanceFare = distanceFare,
            timeFare = timeFare,
            nightSurcharge = nightExtra,
            grossFare = gross,
            sharedDiscountRate = discountRate,
            sharedDiscount = discount,
            perSeatFare = perSeat,
            seats = seats,
            totalFare = total,
            platformFee = platformFee,
            driverEarnings = total - platformFee
        )
    }

    /** Night window wraps past midnight, so this is not a simple range check. */
    fun isNightHour(hour: Int, rules: PricingRules = PricingRules.DEFAULT): Boolean {
        val h = ((hour % 24) + 24) % 24
        return if (rules.nightStartHour <= rules.nightEndHour) {
            h in rules.nightStartHour until rules.nightEndHour
        } else {
            h >= rules.nightStartHour || h < rules.nightEndHour
        }
    }
}
