package com.potheride.app.data.remote

import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PaymentStatus
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.RideState
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.DriverProfileEntity
import com.potheride.app.data.local.entities.NotificationEntity
import com.potheride.app.data.local.entities.NotificationKind
import com.potheride.app.data.local.entities.PaymentEntity
import com.potheride.app.data.local.entities.TripEntity
import com.potheride.app.data.local.entities.TripStatus
import com.potheride.app.data.local.entities.UserEntity
import com.potheride.app.data.local.entities.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Firestore mapping layer is pure, so it is fully testable with no emulator.
 *
 * Two kinds of test here. The round-trips prove nothing is silently dropped on the way
 * out and back. The malformed-document tests matter more: Firestore is schemaless, so
 * missing fields, wrong types and unknown enum values are all things a real client will
 * meet, and the mappers must degrade rather than throw on a background thread.
 */
class FirestoreMappersTest {

    // ------------------------------------------------------------------
    // Round trips
    // ------------------------------------------------------------------

    @Test
    fun `user survives a round trip`() {
        val user = UserEntity(
            id = "u1", phone = "+8801712345678", name = "Rahim Ahmed",
            language = "bn", otpVerified = true, photoTint = 42,
            blocked = false, createdAt = 1_700_000_000_000L
        )
        val back = FirestoreMappers.userFromMap("u1", FirestoreMappers.userToMap(user))
        assertEquals(user, back)
    }

    @Test
    fun `driver survives a round trip`() {
        val driver = DriverProfileEntity(
            id = "d1", userId = "u1", licenseNumber = "DL-998",
            verified = true, ratingSum = 47, ratingCount = 10, totalTrips = 12
        )
        val back = FirestoreMappers.driverFromMap("d1", FirestoreMappers.driverToMap(driver))
        assertEquals(driver, back)
    }

    @Test
    fun `vehicles nested on the driver document survive a round trip`() {
        val vehicles = listOf(
            VehicleEntity("v1", "d1", VehicleClass.CNG, "DHK-1234", "Bajaj", "green", 3),
            VehicleEntity("v2", "d1", VehicleClass.CAR, "DHK-9999", null, null, 4)
        )
        val driver = DriverProfileEntity("d1", "u1", "DL-998")
        val map = FirestoreMappers.driverToMap(driver, vehicles)
        assertEquals(vehicles, FirestoreMappers.vehiclesFromDriverMap("d1", map))
    }

    @Test
    fun `route survives a round trip and keeps its waypoints`() {
        val trip = TripEntity(
            id = "t1", driverId = "d1", vehicleId = "v1",
            startAddress = "Mirpur-10", startLat = 23.8067, startLng = 90.3686,
            endAddress = "Tongi Station", endLat = 23.8909, endLng = 90.4023,
            departureTime = 1_700_000_000_000L, totalSeats = 3, availableSeats = 2,
            detourKm = 1.5, travelledKm = 4.25,
            status = TripStatus.IN_PROGRESS, createdAt = 1_699_000_000_000L
        )
        val waypoints = listOf(
            LatLng(23.8067, 90.3686), LatLng(23.7960, 90.3742), LatLng(23.8909, 90.4023)
        )
        val map = FirestoreMappers.routeToMap(trip, waypoints)
        val back = FirestoreMappers.routeFromMap("t1", map)

        assertEquals(trip, back)
        assertEquals(waypoints, FirestoreMappers.routeWaypoints(map))
    }

    @Test
    fun `ride request survives a round trip`() {
        val booking = BookingEntity(
            id = "b1", tripId = "t1", passengerId = "u2",
            pickupAddress = "Kazipara", pickupLat = 23.7960, pickupLng = 90.3742,
            dropAddress = "Airport", dropLat = 23.8433, dropLng = 90.3978,
            seatsRequested = 2, routeOverlapRatio = 0.92f, sharedDistanceKm = 5.5,
            detourKm = 0.4, farePoisha = 4500, totalPoisha = 4300,
            pickupEtaMillis = 1_700_000_600_000L, dropoffEtaMillis = 1_700_001_800_000L,
            status = RideState.ACCEPTED, cancellationReason = null,
            requestedAt = 1_700_000_000_000L, acceptedAt = 1_700_000_100_000L,
            completedAt = null
        )
        val back = FirestoreMappers.rideRequestFromMap(
            "b1", FirestoreMappers.rideRequestToMap(booking, driverId = "d1")
        )
        assertEquals(booking, back)
    }

    @Test
    fun `the driver id is denormalised onto the ride request`() {
        val booking = BookingEntity(
            id = "b1", tripId = "t1", passengerId = "u2",
            pickupAddress = "a", pickupLat = 1.0, pickupLng = 2.0,
            dropAddress = "b", dropLat = 3.0, dropLng = 4.0
        )
        val map = FirestoreMappers.rideRequestToMap(booking, driverId = "d-xyz")
        // Without this a driver cannot query their own incoming requests in one read.
        assertEquals("d-xyz", map[FirestoreSchema.RideRequest.DRIVER_ID])
    }

    @Test
    fun `payment survives a round trip`() {
        val payment = PaymentEntity(
            id = "p1", bookingId = "b1", driverId = "d1",
            amountPoisha = 4300, platformFeePoisha = 430, driverEarningsPoisha = 3870,
            method = PaymentMethod.BKASH, status = PaymentStatus.PENDING,
            transactionRef = "TX-1", createdAt = 1_700_000_000_000L, paidAt = null
        )
        val back = FirestoreMappers.paymentFromMap("p1", FirestoreMappers.paymentToMap(payment))
        assertEquals(payment, back)
    }

    @Test
    fun `notification survives a round trip including both languages`() {
        val n = NotificationEntity(
            id = "n1", userId = "u1", kind = NotificationKind.REQUEST_ACCEPTED,
            titleEn = "Accepted", titleBn = "গৃহীত",
            bodyEn = "Your seat is confirmed", bodyBn = "আপনার আসন নিশ্চিত",
            bookingId = "b1", readAt = null, createdAt = 1_700_000_000_000L
        )
        val back = FirestoreMappers.notificationFromMap("n1", FirestoreMappers.notificationToMap(n))
        assertEquals(n, back)
    }

    // ------------------------------------------------------------------
    // Waypoint encoding
    // ------------------------------------------------------------------

    @Test
    fun `waypoints are stored as a flat number array`() {
        val flat = FirestoreMappers.waypointsToFlatList(
            listOf(LatLng(23.8, 90.3), LatLng(23.9, 90.4))
        )
        assertEquals(listOf(23.8, 90.3, 23.9, 90.4), flat)
    }

    @Test
    fun `a truncated waypoint array drops the dangling value instead of inventing a point`() {
        // A half-written array must not produce a point at longitude 0 — that would draw
        // the route off into the Atlantic.
        val points = FirestoreMappers.waypointsFromFlatList(listOf(23.8, 90.3, 23.9))
        assertEquals(listOf(LatLng(23.8, 90.3)), points)
    }

    @Test
    fun `waypoints read back when Firestore returns mixed Long and Double`() {
        // Firestore returns whole numbers as Long. A direct cast to Double throws.
        val points = FirestoreMappers.waypointsFromFlatList(listOf(23L, 90.3, 24.0, 91L))
        assertEquals(listOf(LatLng(23.0, 90.3), LatLng(24.0, 91.0)), points)
    }

    @Test
    fun `a missing waypoint field yields an empty route rather than throwing`() {
        assertEquals(emptyList<LatLng>(), FirestoreMappers.waypointsFromFlatList(null))
        assertEquals(emptyList<LatLng>(), FirestoreMappers.waypointsFromFlatList("not a list"))
    }

    // ------------------------------------------------------------------
    // Malformed documents
    // ------------------------------------------------------------------

    @Test
    fun `an empty document maps to a usable entity`() {
        val user = FirestoreMappers.userFromMap("u1", emptyMap())
        assertEquals("u1", user.id)
        assertEquals("", user.phone)
        assertEquals("en", user.language)
    }

    @Test
    fun `an unknown enum value falls back instead of throwing`() {
        // A newer build introduces a status this client has never heard of.
        val booking = FirestoreMappers.rideRequestFromMap(
            "b1", mapOf(FirestoreSchema.RideRequest.STATUS to "TELEPORTED")
        )
        assertEquals(RideState.REQUESTED, booking.status)

        val trip = FirestoreMappers.routeFromMap(
            "t1", mapOf(FirestoreSchema.Route.STATUS to "WHO_KNOWS")
        )
        assertEquals(TripStatus.PUBLISHED, trip.status)
    }

    @Test
    fun `whole-number decimals arriving as Long do not crash the reader`() {
        // Firestore stores 0.0 as a Long. This is the single most common Firestore
        // mapping bug and it only shows up on the first free ride.
        val booking = FirestoreMappers.rideRequestFromMap(
            "b1",
            mapOf(
                FirestoreSchema.RideRequest.PICKUP_LAT to 23L,
                FirestoreSchema.RideRequest.SHARED_DISTANCE_KM to 5L,
                FirestoreSchema.RideRequest.ROUTE_OVERLAP_RATIO to 1L,
                FirestoreSchema.RideRequest.FARE_POISHA to 4500.0
            )
        )
        assertEquals(23.0, booking.pickupLat, 0.0001)
        assertEquals(5.0, booking.sharedDistanceKm, 0.0001)
        assertEquals(1f, booking.routeOverlapRatio, 0.0001f)
        assertEquals(4500L, booking.farePoisha)
    }

    @Test
    fun `a wrongly typed field falls back rather than throwing`() {
        val user = FirestoreMappers.userFromMap(
            "u1",
            mapOf(
                FirestoreSchema.User.PHONE to 12345,        // number, not string
                FirestoreSchema.User.BLOCKED to "yes",       // string, not boolean
                FirestoreSchema.User.CREATED_AT to "recent"  // string, not number
            )
        )
        assertEquals("", user.phone)
        assertEquals(false, user.blocked)
        assertEquals(0L, user.createdAt)
    }

    @Test
    fun `nullable fields stay null through a round trip`() {
        val booking = BookingEntity(
            id = "b1", tripId = "t1", passengerId = "u2",
            pickupAddress = "a", pickupLat = 1.0, pickupLng = 2.0,
            dropAddress = "b", dropLat = 3.0, dropLng = 4.0,
            pickupEtaMillis = null, dropoffEtaMillis = null,
            cancellationReason = null, acceptedAt = null, completedAt = null
        )
        val back = FirestoreMappers.rideRequestFromMap(
            "b1", FirestoreMappers.rideRequestToMap(booking, "d1")
        )
        assertNull(back.pickupEtaMillis)
        assertNull(back.acceptedAt)
        assertNull(back.completedAt)
        assertNull(back.cancellationReason)
    }

    // ------------------------------------------------------------------
    // Live location
    // ------------------------------------------------------------------

    @Test
    fun `live location survives a round trip`() {
        val map = FirestoreMappers.liveLocationToMap(
            tripId = "t1", driverId = "d1",
            position = LatLng(23.7960, 90.3742), travelledKm = 3.2,
            updatedAt = 1_700_000_000_000L
        )
        val back = FirestoreMappers.liveLocationFromMap(map)
        assertNotNull(back)
        assertEquals(23.7960, back!!.position.lat, 0.00001)
        assertEquals(3.2, back.travelledKm, 0.00001)
        assertEquals(1_700_000_000_000L, back.updatedAt)
    }

    @Test
    fun `a live location without coordinates is null rather than a point at zero zero`() {
        // (0, 0) is in the Atlantic. Drawing a driver there is worse than drawing nothing.
        assertNull(FirestoreMappers.liveLocationFromMap(emptyMap()))
        assertNull(
            FirestoreMappers.liveLocationFromMap(
                mapOf(FirestoreSchema.LiveLocation.LAT to 23.8)  // lng missing
            )
        )
    }

    @Test
    fun `the route document does not carry a live position`() {
        // Two sources of truth for where the driver is would let the map disagree with
        // itself; the live position belongs only in liveLocations/{tripId}.
        val trip = TripEntity(
            id = "t1", driverId = "d1", vehicleId = "v1",
            startAddress = "a", startLat = 1.0, startLng = 2.0,
            endAddress = "b", endLat = 3.0, endLng = 4.0,
            departureTime = 0L, totalSeats = 3, availableSeats = 3,
            currentLat = 23.8, currentLng = 90.3
        )
        val map = FirestoreMappers.routeToMap(trip, emptyList())
        assertTrue(map.keys.none { it.contains("current", ignoreCase = true) })
        assertNull(FirestoreMappers.routeFromMap("t1", map).livePoint)
    }

    // ------------------------------------------------------------------
    // Storage paths
    // ------------------------------------------------------------------

    @Test
    fun `storage paths are namespaced per user so rules can scope access`() {
        assertEquals(
            "drivers/u1/documents/NID_FRONT.jpg",
            FirestoreSchema.Storage.driverDocument("u1", "NID_FRONT", "jpg")
        )
        assertEquals("users/u1/profile.png", FirestoreSchema.Storage.profilePhoto("u1", "png"))
        assertTrue(FirestoreSchema.Storage.faceCapture("u1", 42L, "jpg").startsWith("users/u1/"))
    }
}
