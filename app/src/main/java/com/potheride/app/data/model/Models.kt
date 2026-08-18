package com.potheride.app.data.model

import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.matching.RouteMatchResult
import com.potheride.app.core.pricing.FareBreakdown
import com.potheride.app.core.pricing.Taka
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.TripEntity

/** Everything the passenger sees about a driver before deciding to book. */
data class DriverSummary(
    val driverId: String,
    val userId: String,
    val name: String,
    val phone: String,
    val verified: Boolean,
    val rating: Float?,
    val ratingCount: Int,
    val totalTrips: Int,
    val vehicleType: VehicleClass,
    val vehiclePlate: String,
    val vehicleModel: String?,
    val vehicleCapacity: Int
) {
    /** Initials for the avatar placeholder — no photo upload in the MVP. */
    val initials: String
        get() = name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }
}

/**
 * A published trip that matched a passenger's search, together with why it matched
 * and what it would cost. Produced by the repository so the UI never has to run the
 * matcher or the fare rules itself.
 */
data class MatchedRide(
    val trip: TripEntity,
    val driver: DriverSummary,
    val route: List<LatLng>,
    val match: RouteMatchResult,
    val fare: FareBreakdown,
    val pickup: LatLng,
    val drop: LatLng,
    val pickupEtaMillis: Long,
    val dropoffEtaMillis: Long,
    /**
     * The 0–100 ranking score. Defaulted so the many places that construct a
     * [MatchedRide] in tests need not care about ranking.
     */
    val score: com.potheride.app.core.matching.MatchScore = com.potheride.app.core.matching.MatchScore(
        value = 0,
        overlapFactor = 0.0,
        departureFactor = 0.0,
        pickupProximityFactor = 0.0,
        destinationProximityFactor = 0.0
    )
) {
    val perSeatFare: Taka get() = fare.perSeatFare
    val overlapPercent: Int get() = match.overlapPercent

    /** The stretch of the driver's path this passenger would actually ride. */
    val sharedPath: List<LatLng>
        get() = com.potheride.app.core.matching.RouteMatcher.sharedPath(route, match)
}

/** A booking joined with the trip and driver it belongs to, for history and status. */
data class BookingDetail(
    val booking: BookingEntity,
    val trip: TripEntity?,
    val driver: DriverSummary?,
    val route: List<LatLng>,
    val passengerName: String? = null
) {
    val fare: Taka get() = com.potheride.app.core.pricing.Taka.ofPoisha(booking.farePoisha)
    val total: Taka get() = com.potheride.app.core.pricing.Taka.ofPoisha(booking.totalPoisha)
}

/** Aggregates behind the driver's earnings screen. */
data class EarningsSummary(
    val today: Taka,
    val thisWeek: Taka,
    val thisMonth: Taka,
    val pending: Taka,
    val completedTrips: Int,
    val rating: Float?,
    val ratingCount: Int
)

/** Aggregates behind the admin dashboard. */
data class PlatformStats(
    val totalUsers: Int,
    val totalDrivers: Int,
    val verifiedDrivers: Int,
    val totalTrips: Int,
    val totalBookings: Int,
    val completedRides: Int,
    val platformRevenue: Taka,
    val openSafetyEvents: Int
)
