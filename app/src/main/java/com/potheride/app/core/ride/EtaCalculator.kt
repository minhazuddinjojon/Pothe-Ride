package com.potheride.app.core.ride

import com.potheride.app.core.pricing.VehicleClass
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Travel-time estimates tuned for Dhaka traffic rather than open highway. Speeds
 * here are deliberately pessimistic: a 12 km trip across the city genuinely does
 * take the better part of an hour at 6pm, and an ETA that ignores that is worse
 * than no ETA at all because passengers plan around it.
 */
object EtaCalculator {

    /** Average speeds in km/h under free-flowing conditions. */
    private val freeFlowSpeed = mapOf(
        VehicleClass.BIKE to 28.0,
        VehicleClass.CNG to 22.0,
        VehicleClass.CAR to 25.0,
        VehicleClass.MICROBUS to 22.0
    )

    /**
     * Multiplier applied to free-flow travel time by hour of day. Dhaka has two
     * pronounced peaks: the morning office run and the long evening crawl.
     */
    fun congestionFactor(hourOfDay: Int): Double {
        val h = ((hourOfDay % 24) + 24) % 24
        return when (h) {
            in 0..5 -> 0.75
            in 6..7 -> 1.10
            in 8..10 -> 1.85
            in 11..15 -> 1.35
            in 16..20 -> 1.95
            in 21..22 -> 1.20
            else -> 0.90
        }
    }

    /** Estimated minutes to cover [distanceKm], never less than one minute. */
    fun travelMinutes(distanceKm: Double, vehicleClass: VehicleClass, hourOfDay: Int): Int {
        val speed = freeFlowSpeed[vehicleClass] ?: 25.0
        val hours = max(0.0, distanceKm) / speed
        val minutes = hours * 60 * congestionFactor(hourOfDay)
        return max(1, minutes.roundToInt())
    }

    /**
     * When the driver reaches the passenger's pickup point, in epoch millis:
     * departure time plus the time to drive the stretch of route before the pickup.
     */
    fun pickupEtaMillis(
        departureTimeMillis: Long,
        distanceToPickupKm: Double,
        vehicleClass: VehicleClass,
        hourOfDay: Int
    ): Long = departureTimeMillis +
        travelMinutes(distanceToPickupKm, vehicleClass, hourOfDay) * 60_000L

    /** When the passenger is dropped off, given how far they ride after boarding. */
    fun dropoffEtaMillis(
        pickupEtaMillis: Long,
        sharedDistanceKm: Double,
        vehicleClass: VehicleClass,
        hourOfDay: Int
    ): Long = pickupEtaMillis +
        travelMinutes(sharedDistanceKm, vehicleClass, hourOfDay) * 60_000L
}
