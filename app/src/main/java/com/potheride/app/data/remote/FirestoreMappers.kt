package com.potheride.app.data.remote

import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PaymentStatus
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.RideState
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.DriverProfileEntity
import com.potheride.app.data.local.entities.MessageEntity
import com.potheride.app.data.local.entities.NotificationEntity
import com.potheride.app.data.local.entities.NotificationKind
import com.potheride.app.data.local.entities.PaymentEntity
import com.potheride.app.data.local.entities.RatingEntity
import com.potheride.app.data.local.entities.SafetyEventEntity
import com.potheride.app.data.local.entities.SafetyEventKind
import com.potheride.app.data.local.entities.SavedPlaceEntity
import com.potheride.app.data.local.entities.TripEntity
import com.potheride.app.data.local.entities.TripStatus
import com.potheride.app.data.local.entities.TrustedContactEntity
import com.potheride.app.data.local.entities.UserEntity
import com.potheride.app.data.local.entities.VehicleEntity

/**
 * Conversion between the app's entities and Firestore documents.
 *
 * The entities are reused as the domain model rather than replaced with a parallel set
 * of Firestore DTOs. They are already plain data classes with no Room annotations on
 * their *fields* — only on the class — so a second model would be pure duplication, and
 * two models that must stay in step are two models that eventually do not.
 *
 * Everything here is a pure function, which is the point: the whole mapping layer is
 * unit-testable with no emulator and no network.
 *
 * ### Reading defensively
 * Every read tolerates a missing or wrongly-typed field. Firestore is schemaless, so a
 * document written by an older build, a partially-completed write, or a hand-edit in the
 * console can be missing anything. A mapper that assumes its fields exist turns that into
 * a crash on a background thread; these fall back to a sane default instead.
 *
 * Numbers are read through [num] because Firestore normalises integers to `Long` and
 * decimals to `Double`, and which one comes back depends on the value that was written —
 * `0.0` can arrive as `Long`. Casting directly is a `ClassCastException` waiting for the
 * first whole-number fare.
 */
object FirestoreMappers {

    // ------------------------------------------------------------------
    // Primitive readers
    // ------------------------------------------------------------------

    private fun Map<String, Any?>.str(key: String, fallback: String = ""): String =
        this[key] as? String ?: fallback

    private fun Map<String, Any?>.strOrNull(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.num(key: String): Number? = this[key] as? Number

    private fun Map<String, Any?>.long(key: String, fallback: Long = 0L): Long =
        num(key)?.toLong() ?: fallback

    private fun Map<String, Any?>.longOrNull(key: String): Long? = num(key)?.toLong()

    private fun Map<String, Any?>.int(key: String, fallback: Int = 0): Int =
        num(key)?.toInt() ?: fallback

    private fun Map<String, Any?>.dbl(key: String, fallback: Double = 0.0): Double =
        num(key)?.toDouble() ?: fallback

    private fun Map<String, Any?>.dblOrNull(key: String): Double? = num(key)?.toDouble()

    private fun Map<String, Any?>.float(key: String, fallback: Float = 0f): Float =
        num(key)?.toFloat() ?: fallback

    private fun Map<String, Any?>.bool(key: String, fallback: Boolean = false): Boolean =
        this[key] as? Boolean ?: fallback

    /**
     * Reads an enum by name, falling back when the stored value is not a member.
     *
     * This is what keeps an old client from crashing on a status a newer build
     * introduced: it degrades to a known state rather than throwing.
     */
    private inline fun <reified T : Enum<T>> Map<String, Any?>.enum(key: String, fallback: T): T {
        val raw = this[key] as? String ?: return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    fun userToMap(user: UserEntity, role: String = ROLE_PASSENGER): Map<String, Any?> = mapOf(
        FirestoreSchema.User.PHONE to user.phone,
        FirestoreSchema.User.NAME to user.name,
        FirestoreSchema.User.LANGUAGE to user.language,
        FirestoreSchema.User.OTP_VERIFIED to user.otpVerified,
        FirestoreSchema.User.PHOTO_TINT to user.photoTint,
        FirestoreSchema.User.BLOCKED to user.blocked,
        FirestoreSchema.User.CREATED_AT to user.createdAt,
        FirestoreSchema.User.ROLE to role
    )

    fun userFromMap(id: String, data: Map<String, Any?>): UserEntity = UserEntity(
        id = id,
        phone = data.str(FirestoreSchema.User.PHONE),
        name = data.str(FirestoreSchema.User.NAME),
        language = data.str(FirestoreSchema.User.LANGUAGE, "en"),
        otpVerified = data.bool(FirestoreSchema.User.OTP_VERIFIED),
        photoTint = data.int(FirestoreSchema.User.PHOTO_TINT),
        blocked = data.bool(FirestoreSchema.User.BLOCKED),
        createdAt = data.long(FirestoreSchema.User.CREATED_AT)
    )

    const val ROLE_PASSENGER = "passenger"
    const val ROLE_DRIVER = "driver"
    const val ROLE_ADMIN = "admin"

    // ------------------------------------------------------------------
    // Drivers and vehicles
    // ------------------------------------------------------------------

    fun driverToMap(
        driver: DriverProfileEntity,
        vehicles: List<VehicleEntity> = emptyList()
    ): Map<String, Any?> = mapOf(
        FirestoreSchema.Driver.USER_ID to driver.userId,
        FirestoreSchema.Driver.LICENSE_NUMBER to driver.licenseNumber,
        FirestoreSchema.Driver.VERIFIED to driver.verified,
        FirestoreSchema.Driver.RATING_SUM to driver.ratingSum,
        FirestoreSchema.Driver.RATING_COUNT to driver.ratingCount,
        FirestoreSchema.Driver.TOTAL_TRIPS to driver.totalTrips,
        FirestoreSchema.Driver.VEHICLES to vehicles.map(::vehicleToMap)
    )

    fun driverFromMap(id: String, data: Map<String, Any?>): DriverProfileEntity =
        DriverProfileEntity(
            id = id,
            userId = data.str(FirestoreSchema.Driver.USER_ID),
            licenseNumber = data.str(FirestoreSchema.Driver.LICENSE_NUMBER),
            verified = data.bool(FirestoreSchema.Driver.VERIFIED),
            ratingSum = data.int(FirestoreSchema.Driver.RATING_SUM),
            ratingCount = data.int(FirestoreSchema.Driver.RATING_COUNT),
            totalTrips = data.int(FirestoreSchema.Driver.TOTAL_TRIPS)
        )

    fun vehicleToMap(vehicle: VehicleEntity): Map<String, Any?> = mapOf(
        FirestoreSchema.Vehicle.ID to vehicle.id,
        FirestoreSchema.Vehicle.TYPE to vehicle.type.name,
        FirestoreSchema.Vehicle.PLATE_NUMBER to vehicle.plateNumber,
        FirestoreSchema.Vehicle.MODEL to vehicle.model,
        FirestoreSchema.Vehicle.COLOUR to vehicle.colour,
        FirestoreSchema.Vehicle.CAPACITY to vehicle.capacity
    )

    fun vehicleFromMap(driverId: String, data: Map<String, Any?>): VehicleEntity = VehicleEntity(
        id = data.str(FirestoreSchema.Vehicle.ID),
        driverId = driverId,
        type = data.enum(FirestoreSchema.Vehicle.TYPE, VehicleClass.CNG),
        plateNumber = data.str(FirestoreSchema.Vehicle.PLATE_NUMBER),
        model = data.strOrNull(FirestoreSchema.Vehicle.MODEL),
        colour = data.strOrNull(FirestoreSchema.Vehicle.COLOUR),
        capacity = data.int(FirestoreSchema.Vehicle.CAPACITY, 1)
    )

    @Suppress("UNCHECKED_CAST")
    fun vehiclesFromDriverMap(driverId: String, data: Map<String, Any?>): List<VehicleEntity> =
        (data[FirestoreSchema.Driver.VEHICLES] as? List<*>)
            ?.mapNotNull { it as? Map<String, Any?> }
            ?.map { vehicleFromMap(driverId, it) }
            ?: emptyList()

    // ------------------------------------------------------------------
    // Routes (published trips)
    // ------------------------------------------------------------------

    /**
     * Waypoints are stored as a flat `[lat, lng, lat, lng, …]` array of doubles rather
     * than a list of `{lat, lng}` maps.
     *
     * A Dhaka route can carry a few hundred points. Firestore counts every field of every
     * nested map toward the 20,000-index-entries-per-document limit, and a list of maps
     * burns two named fields per point; a flat number array is one field total. It also
     * roughly halves the document size, which matters when passengers are re-reading
     * routes on a mobile connection.
     */
    fun waypointsToFlatList(points: List<LatLng>): List<Double> =
        points.flatMap { listOf(it.lat, it.lng) }

    fun waypointsFromFlatList(raw: Any?): List<LatLng> {
        val numbers = (raw as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: return emptyList()
        // An odd trailing value means a truncated write; drop it rather than fabricate a
        // point at longitude 0, which would draw a line into the Gulf of Guinea.
        return numbers.chunked(2).filter { it.size == 2 }.map { LatLng(it[0], it[1]) }
    }

    fun routeToMap(trip: TripEntity, waypoints: List<LatLng>): Map<String, Any?> = mapOf(
        FirestoreSchema.Route.DRIVER_ID to trip.driverId,
        FirestoreSchema.Route.VEHICLE_ID to trip.vehicleId,
        FirestoreSchema.Route.START_ADDRESS to trip.startAddress,
        FirestoreSchema.Route.START_LAT to trip.startLat,
        FirestoreSchema.Route.START_LNG to trip.startLng,
        FirestoreSchema.Route.END_ADDRESS to trip.endAddress,
        FirestoreSchema.Route.END_LAT to trip.endLat,
        FirestoreSchema.Route.END_LNG to trip.endLng,
        FirestoreSchema.Route.DEPARTURE_TIME to trip.departureTime,
        FirestoreSchema.Route.TOTAL_SEATS to trip.totalSeats,
        FirestoreSchema.Route.AVAILABLE_SEATS to trip.availableSeats,
        FirestoreSchema.Route.DETOUR_KM to trip.detourKm,
        FirestoreSchema.Route.TRAVELLED_KM to trip.travelledKm,
        FirestoreSchema.Route.STATUS to trip.status.name,
        FirestoreSchema.Route.CREATED_AT to trip.createdAt,
        FirestoreSchema.Route.WAYPOINTS to waypointsToFlatList(waypoints)
    )

    /**
     * Rebuilds a trip.
     *
     * The live position is deliberately *not* read here — it lives in `liveLocations`,
     * and a caller that wants it composes the two. Leaving a stale coordinate on the
     * route document would give the map two sources of truth for where the driver is.
     */
    fun routeFromMap(id: String, data: Map<String, Any?>): TripEntity = TripEntity(
        id = id,
        driverId = data.str(FirestoreSchema.Route.DRIVER_ID),
        vehicleId = data.str(FirestoreSchema.Route.VEHICLE_ID),
        startAddress = data.str(FirestoreSchema.Route.START_ADDRESS),
        startLat = data.dbl(FirestoreSchema.Route.START_LAT),
        startLng = data.dbl(FirestoreSchema.Route.START_LNG),
        endAddress = data.str(FirestoreSchema.Route.END_ADDRESS),
        endLat = data.dbl(FirestoreSchema.Route.END_LAT),
        endLng = data.dbl(FirestoreSchema.Route.END_LNG),
        departureTime = data.long(FirestoreSchema.Route.DEPARTURE_TIME),
        totalSeats = data.int(FirestoreSchema.Route.TOTAL_SEATS),
        availableSeats = data.int(FirestoreSchema.Route.AVAILABLE_SEATS),
        detourKm = data.dbl(FirestoreSchema.Route.DETOUR_KM, 1.5),
        travelledKm = data.dbl(FirestoreSchema.Route.TRAVELLED_KM),
        status = data.enum(FirestoreSchema.Route.STATUS, TripStatus.PUBLISHED),
        createdAt = data.long(FirestoreSchema.Route.CREATED_AT)
    )

    fun routeWaypoints(data: Map<String, Any?>): List<LatLng> =
        waypointsFromFlatList(data[FirestoreSchema.Route.WAYPOINTS])

    // ------------------------------------------------------------------
    // Live location
    // ------------------------------------------------------------------

    fun liveLocationToMap(
        tripId: String,
        driverId: String,
        position: LatLng,
        travelledKm: Double,
        updatedAt: Long
    ): Map<String, Any?> = mapOf(
        FirestoreSchema.LiveLocation.TRIP_ID to tripId,
        FirestoreSchema.LiveLocation.DRIVER_ID to driverId,
        FirestoreSchema.LiveLocation.LAT to position.lat,
        FirestoreSchema.LiveLocation.LNG to position.lng,
        FirestoreSchema.LiveLocation.TRAVELLED_KM to travelledKm,
        FirestoreSchema.LiveLocation.UPDATED_AT to updatedAt
    )

    /** Null when the document has no usable coordinate, rather than a point at (0, 0). */
    fun liveLocationFromMap(data: Map<String, Any?>): LiveLocation? {
        val lat = data.dblOrNull(FirestoreSchema.LiveLocation.LAT) ?: return null
        val lng = data.dblOrNull(FirestoreSchema.LiveLocation.LNG) ?: return null
        return LiveLocation(
            position = LatLng(lat, lng),
            travelledKm = data.dbl(FirestoreSchema.LiveLocation.TRAVELLED_KM),
            updatedAt = data.long(FirestoreSchema.LiveLocation.UPDATED_AT)
        )
    }

    // ------------------------------------------------------------------
    // Ride requests (bookings)
    // ------------------------------------------------------------------

    /**
     * @param driverId denormalised onto the request so a driver can query "requests for
     * me" in one indexed read. Firestore has no joins, and without it the driver would
     * have to read every route they own and then fan out a query per route.
     */
    fun rideRequestToMap(booking: BookingEntity, driverId: String): Map<String, Any?> = mapOf(
        FirestoreSchema.RideRequest.TRIP_ID to booking.tripId,
        FirestoreSchema.RideRequest.PASSENGER_ID to booking.passengerId,
        FirestoreSchema.RideRequest.DRIVER_ID to driverId,
        FirestoreSchema.RideRequest.PICKUP_ADDRESS to booking.pickupAddress,
        FirestoreSchema.RideRequest.PICKUP_LAT to booking.pickupLat,
        FirestoreSchema.RideRequest.PICKUP_LNG to booking.pickupLng,
        FirestoreSchema.RideRequest.DROP_ADDRESS to booking.dropAddress,
        FirestoreSchema.RideRequest.DROP_LAT to booking.dropLat,
        FirestoreSchema.RideRequest.DROP_LNG to booking.dropLng,
        FirestoreSchema.RideRequest.SEATS_REQUESTED to booking.seatsRequested,
        FirestoreSchema.RideRequest.ROUTE_OVERLAP_RATIO to booking.routeOverlapRatio.toDouble(),
        FirestoreSchema.RideRequest.SHARED_DISTANCE_KM to booking.sharedDistanceKm,
        FirestoreSchema.RideRequest.DETOUR_KM to booking.detourKm,
        FirestoreSchema.RideRequest.FARE_POISHA to booking.farePoisha,
        FirestoreSchema.RideRequest.TOTAL_POISHA to booking.totalPoisha,
        FirestoreSchema.RideRequest.PICKUP_ETA_MILLIS to booking.pickupEtaMillis,
        FirestoreSchema.RideRequest.DROPOFF_ETA_MILLIS to booking.dropoffEtaMillis,
        FirestoreSchema.RideRequest.STATUS to booking.status.name,
        FirestoreSchema.RideRequest.CANCELLATION_REASON to booking.cancellationReason,
        FirestoreSchema.RideRequest.REQUESTED_AT to booking.requestedAt,
        FirestoreSchema.RideRequest.ACCEPTED_AT to booking.acceptedAt,
        FirestoreSchema.RideRequest.COMPLETED_AT to booking.completedAt
    )

    fun rideRequestFromMap(id: String, data: Map<String, Any?>): BookingEntity = BookingEntity(
        id = id,
        tripId = data.str(FirestoreSchema.RideRequest.TRIP_ID),
        passengerId = data.str(FirestoreSchema.RideRequest.PASSENGER_ID),
        pickupAddress = data.str(FirestoreSchema.RideRequest.PICKUP_ADDRESS),
        pickupLat = data.dbl(FirestoreSchema.RideRequest.PICKUP_LAT),
        pickupLng = data.dbl(FirestoreSchema.RideRequest.PICKUP_LNG),
        dropAddress = data.str(FirestoreSchema.RideRequest.DROP_ADDRESS),
        dropLat = data.dbl(FirestoreSchema.RideRequest.DROP_LAT),
        dropLng = data.dbl(FirestoreSchema.RideRequest.DROP_LNG),
        seatsRequested = data.int(FirestoreSchema.RideRequest.SEATS_REQUESTED, 1),
        routeOverlapRatio = data.float(FirestoreSchema.RideRequest.ROUTE_OVERLAP_RATIO),
        sharedDistanceKm = data.dbl(FirestoreSchema.RideRequest.SHARED_DISTANCE_KM),
        detourKm = data.dbl(FirestoreSchema.RideRequest.DETOUR_KM),
        farePoisha = data.long(FirestoreSchema.RideRequest.FARE_POISHA),
        totalPoisha = data.long(FirestoreSchema.RideRequest.TOTAL_POISHA),
        pickupEtaMillis = data.longOrNull(FirestoreSchema.RideRequest.PICKUP_ETA_MILLIS),
        dropoffEtaMillis = data.longOrNull(FirestoreSchema.RideRequest.DROPOFF_ETA_MILLIS),
        status = data.enum(FirestoreSchema.RideRequest.STATUS, RideState.REQUESTED),
        cancellationReason = data.strOrNull(FirestoreSchema.RideRequest.CANCELLATION_REASON),
        requestedAt = data.long(FirestoreSchema.RideRequest.REQUESTED_AT),
        acceptedAt = data.longOrNull(FirestoreSchema.RideRequest.ACCEPTED_AT),
        completedAt = data.longOrNull(FirestoreSchema.RideRequest.COMPLETED_AT)
    )

    // ------------------------------------------------------------------
    // Payments
    // ------------------------------------------------------------------

    fun paymentToMap(payment: PaymentEntity): Map<String, Any?> = mapOf(
        FirestoreSchema.Payment.BOOKING_ID to payment.bookingId,
        FirestoreSchema.Payment.DRIVER_ID to payment.driverId,
        FirestoreSchema.Payment.AMOUNT_POISHA to payment.amountPoisha,
        FirestoreSchema.Payment.PLATFORM_FEE_POISHA to payment.platformFeePoisha,
        FirestoreSchema.Payment.DRIVER_EARNINGS_POISHA to payment.driverEarningsPoisha,
        FirestoreSchema.Payment.METHOD to payment.method.name,
        FirestoreSchema.Payment.STATUS to payment.status.name,
        FirestoreSchema.Payment.TRANSACTION_REF to payment.transactionRef,
        FirestoreSchema.Payment.CREATED_AT to payment.createdAt,
        FirestoreSchema.Payment.PAID_AT to payment.paidAt
    )

    fun paymentFromMap(id: String, data: Map<String, Any?>): PaymentEntity = PaymentEntity(
        id = id,
        bookingId = data.str(FirestoreSchema.Payment.BOOKING_ID),
        driverId = data.str(FirestoreSchema.Payment.DRIVER_ID),
        amountPoisha = data.long(FirestoreSchema.Payment.AMOUNT_POISHA),
        platformFeePoisha = data.long(FirestoreSchema.Payment.PLATFORM_FEE_POISHA),
        driverEarningsPoisha = data.long(FirestoreSchema.Payment.DRIVER_EARNINGS_POISHA),
        method = data.enum(FirestoreSchema.Payment.METHOD, PaymentMethod.CASH),
        status = data.enum(FirestoreSchema.Payment.STATUS, PaymentStatus.PENDING),
        transactionRef = data.strOrNull(FirestoreSchema.Payment.TRANSACTION_REF),
        createdAt = data.long(FirestoreSchema.Payment.CREATED_AT),
        paidAt = data.longOrNull(FirestoreSchema.Payment.PAID_AT)
    )

    // ------------------------------------------------------------------
    // Notifications, messages, ratings
    // ------------------------------------------------------------------

    fun notificationToMap(n: NotificationEntity): Map<String, Any?> = mapOf(
        FirestoreSchema.Notification.USER_ID to n.userId,
        FirestoreSchema.Notification.KIND to n.kind.name,
        FirestoreSchema.Notification.TITLE_EN to n.titleEn,
        FirestoreSchema.Notification.TITLE_BN to n.titleBn,
        FirestoreSchema.Notification.BODY_EN to n.bodyEn,
        FirestoreSchema.Notification.BODY_BN to n.bodyBn,
        FirestoreSchema.Notification.BOOKING_ID to n.bookingId,
        FirestoreSchema.Notification.READ_AT to n.readAt,
        FirestoreSchema.Notification.CREATED_AT to n.createdAt
    )

    fun notificationFromMap(id: String, data: Map<String, Any?>): NotificationEntity =
        NotificationEntity(
            id = id,
            userId = data.str(FirestoreSchema.Notification.USER_ID),
            kind = data.enum(FirestoreSchema.Notification.KIND, NotificationKind.SYSTEM),
            titleEn = data.str(FirestoreSchema.Notification.TITLE_EN),
            titleBn = data.str(FirestoreSchema.Notification.TITLE_BN),
            bodyEn = data.str(FirestoreSchema.Notification.BODY_EN),
            bodyBn = data.str(FirestoreSchema.Notification.BODY_BN),
            bookingId = data.strOrNull(FirestoreSchema.Notification.BOOKING_ID),
            readAt = data.longOrNull(FirestoreSchema.Notification.READ_AT),
            createdAt = data.long(FirestoreSchema.Notification.CREATED_AT)
        )

    fun messageToMap(m: MessageEntity): Map<String, Any?> = mapOf(
        FirestoreSchema.Message.SENDER_ID to m.senderId,
        FirestoreSchema.Message.CONTENT to m.content,
        FirestoreSchema.Message.READ_AT to m.readAt,
        FirestoreSchema.Message.SENT_AT to m.sentAt
    )

    fun messageFromMap(id: String, bookingId: String, data: Map<String, Any?>): MessageEntity =
        MessageEntity(
            id = id,
            bookingId = bookingId,
            senderId = data.str(FirestoreSchema.Message.SENDER_ID),
            content = data.str(FirestoreSchema.Message.CONTENT),
            readAt = data.longOrNull(FirestoreSchema.Message.READ_AT),
            sentAt = data.long(FirestoreSchema.Message.SENT_AT)
        )

    fun ratingToMap(r: RatingEntity): Map<String, Any?> = mapOf(
        FirestoreSchema.Rating.RATER_ID to r.raterId,
        FirestoreSchema.Rating.RATEE_ID to r.rateeId,
        FirestoreSchema.Rating.STARS to r.stars,
        FirestoreSchema.Rating.COMMENT to r.comment,
        FirestoreSchema.Rating.CREATED_AT to r.createdAt
    )

    fun ratingFromMap(id: String, bookingId: String, data: Map<String, Any?>): RatingEntity =
        RatingEntity(
            id = id,
            bookingId = bookingId,
            raterId = data.str(FirestoreSchema.Rating.RATER_ID),
            rateeId = data.str(FirestoreSchema.Rating.RATEE_ID),
            stars = data.int(FirestoreSchema.Rating.STARS),
            comment = data.strOrNull(FirestoreSchema.Rating.COMMENT),
            createdAt = data.long(FirestoreSchema.Rating.CREATED_AT)
        )

    // ------------------------------------------------------------------
    // Saved places, trusted contacts, safety
    // ------------------------------------------------------------------

    fun savedPlaceToMap(p: SavedPlaceEntity): Map<String, Any?> = mapOf(
        FirestoreSchema.Place.LABEL to p.label,
        FirestoreSchema.Place.ADDRESS to p.address,
        FirestoreSchema.Place.LAT to p.lat,
        FirestoreSchema.Place.LNG to p.lng
    )

    fun savedPlaceFromMap(id: String, userId: String, data: Map<String, Any?>): SavedPlaceEntity =
        SavedPlaceEntity(
            id = id,
            userId = userId,
            label = data.str(FirestoreSchema.Place.LABEL),
            address = data.str(FirestoreSchema.Place.ADDRESS),
            lat = data.dbl(FirestoreSchema.Place.LAT),
            lng = data.dbl(FirestoreSchema.Place.LNG)
        )

    fun trustedContactToMap(c: TrustedContactEntity): Map<String, Any?> = mapOf(
        FirestoreSchema.Contact.NAME to c.name,
        FirestoreSchema.Contact.PHONE to c.phone
    )

    fun trustedContactFromMap(
        id: String,
        userId: String,
        data: Map<String, Any?>
    ): TrustedContactEntity = TrustedContactEntity(
        id = id,
        userId = userId,
        name = data.str(FirestoreSchema.Contact.NAME),
        phone = data.str(FirestoreSchema.Contact.PHONE)
    )

    fun safetyEventToMap(e: SafetyEventEntity): Map<String, Any?> = mapOf(
        FirestoreSchema.SafetyEvent.RAISED_BY_USER_ID to e.raisedByUserId,
        FirestoreSchema.SafetyEvent.AGAINST_USER_ID to e.againstUserId,
        FirestoreSchema.SafetyEvent.BOOKING_ID to e.bookingId,
        FirestoreSchema.SafetyEvent.KIND to e.kind.name,
        FirestoreSchema.SafetyEvent.DETAILS to e.details,
        FirestoreSchema.SafetyEvent.LAT to e.lat,
        FirestoreSchema.SafetyEvent.LNG to e.lng,
        FirestoreSchema.SafetyEvent.RESOLVED to e.resolved,
        FirestoreSchema.SafetyEvent.CREATED_AT to e.createdAt
    )

    fun safetyEventFromMap(id: String, data: Map<String, Any?>): SafetyEventEntity =
        SafetyEventEntity(
            id = id,
            raisedByUserId = data.str(FirestoreSchema.SafetyEvent.RAISED_BY_USER_ID),
            againstUserId = data.strOrNull(FirestoreSchema.SafetyEvent.AGAINST_USER_ID),
            bookingId = data.strOrNull(FirestoreSchema.SafetyEvent.BOOKING_ID),
            kind = data.enum(FirestoreSchema.SafetyEvent.KIND, SafetyEventKind.REPORT),
            details = data.strOrNull(FirestoreSchema.SafetyEvent.DETAILS),
            lat = data.dblOrNull(FirestoreSchema.SafetyEvent.LAT),
            lng = data.dblOrNull(FirestoreSchema.SafetyEvent.LNG),
            resolved = data.bool(FirestoreSchema.SafetyEvent.RESOLVED),
            createdAt = data.long(FirestoreSchema.SafetyEvent.CREATED_AT)
        )
}

/** A driver's last reported position, read from `liveLocations/{tripId}`. */
data class LiveLocation(
    val position: LatLng,
    val travelledKm: Double,
    val updatedAt: Long
)
