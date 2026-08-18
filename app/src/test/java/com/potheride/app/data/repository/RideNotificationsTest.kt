package com.potheride.app.data.repository

import com.potheride.app.core.ride.RideState
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notification copy is shared by both backends, so it is worth pinning.
 *
 * These assertions are mostly about *who* gets told what. Notifying the wrong party, or
 * failing to notify one of two, is a silent product bug: nothing crashes, the ride still
 * works, and the person waiting at the roadside simply never hears anything.
 */
class RideNotificationsTest {

    private val booking = BookingEntity(
        id = "b1", tripId = "t1", passengerId = "passenger-1",
        pickupAddress = "Kazipara", pickupLat = 23.79, pickupLng = 90.37,
        dropAddress = "Airport", dropLat = 23.84, dropLng = 90.39,
        seatsRequested = 2
    )

    @Test
    fun `a seat request goes to the driver, not the passenger`() {
        val n = RideNotifications.seatRequested(
            driverUserId = "driver-user-1", passengerName = "Farida Yasmin",
            seats = 2, pickupAddress = "Kazipara", dropAddress = "Airport",
            bookingId = "b1"
        )
        assertEquals("driver-user-1", n.userId)
        assertEquals(NotificationKind.RIDE_REQUEST, n.kind)
        assertTrue(n.bodyEn.contains("Farida Yasmin"))
        assertTrue(n.bodyEn.contains("Kazipara"))
        assertEquals("b1", n.bookingId)
    }

    @Test
    fun `an anonymous passenger still produces readable copy in both languages`() {
        val n = RideNotifications.seatRequested(
            driverUserId = "d", passengerName = null, seats = 1,
            pickupAddress = "Mirpur-10", dropAddress = "Tongi", bookingId = "b1"
        )
        assertTrue(n.bodyEn.contains("A passenger"))
        assertTrue(n.bodyBn.isNotBlank())
        // No "null" leaking into user-visible text in either language.
        assertTrue(!n.bodyEn.contains("null"))
        assertTrue(!n.bodyBn.contains("null"))
    }

    @Test
    fun `acceptance notifies only the passenger`() {
        val sent = RideNotifications.forTransition(
            RideState.ACCEPTED, booking, driverUserId = "driver-user-1",
            passengerName = "Farida", reason = null
        )
        assertEquals(1, sent.size)
        assertEquals("passenger-1", sent.single().userId)
        assertEquals(NotificationKind.REQUEST_ACCEPTED, sent.single().kind)
    }

    @Test
    fun `completion notifies both parties so each can rate the other`() {
        val sent = RideNotifications.forTransition(
            RideState.COMPLETED, booking, driverUserId = "driver-user-1",
            passengerName = "Farida", reason = null
        )
        assertEquals(2, sent.size)
        assertEquals(setOf("passenger-1", "driver-user-1"), sent.map { it.userId }.toSet())
    }

    @Test
    fun `cancellation notifies both parties and carries the reason`() {
        val sent = RideNotifications.forTransition(
            RideState.CANCELLED, booking, driverUserId = "driver-user-1",
            passengerName = "Farida", reason = "traffic"
        )
        assertEquals(2, sent.size)
        assertTrue(sent.all { it.bodyEn.contains("traffic") })
    }

    @Test
    fun `cancellation without a reason does not render an empty bracket`() {
        val sent = RideNotifications.forTransition(
            RideState.CANCELLED, booking, driverUserId = "d", passengerName = "F", reason = null
        )
        assertTrue(sent.all { !it.bodyEn.contains("()") })
    }

    @Test
    fun `an unknown driver does not produce a notification addressed to nobody`() {
        // A route whose driver record has gone missing must not yield a notification
        // with a blank userId — it would be written to Firestore and never delivered.
        val sent = RideNotifications.forTransition(
            RideState.COMPLETED, booking, driverUserId = null,
            passengerName = "Farida", reason = null
        )
        assertEquals(1, sent.size)
        assertEquals("passenger-1", sent.single().userId)
        assertTrue(sent.all { it.userId.isNotBlank() })
    }

    @Test
    fun `states the UI already shows do not raise a notification`() {
        // A push for something the user is currently looking at is noise.
        listOf(RideState.REQUESTED, RideState.PICKED_UP, RideState.PAID).forEach { state ->
            val sent = RideNotifications.forTransition(state, booking, "d", "F", null)
            assertTrue("$state should be silent", sent.isEmpty())
        }
    }

    @Test
    fun `every notification carries both languages and links its booking`() {
        val all = RideState.values().flatMap {
            RideNotifications.forTransition(it, booking, "driver-user-1", "Farida", "reason")
        } + RideNotifications.seatRequested("d", "F", 1, "a", "b", "b1")

        assertTrue(all.isNotEmpty())
        all.forEach {
            assertTrue("missing English title", it.titleEn.isNotBlank())
            assertTrue("missing Bangla title", it.titleBn.isNotBlank())
            assertTrue("missing English body", it.bodyEn.isNotBlank())
            assertTrue("missing Bangla body", it.bodyBn.isNotBlank())
            assertEquals("b1", it.bookingId)
        }
    }
}
