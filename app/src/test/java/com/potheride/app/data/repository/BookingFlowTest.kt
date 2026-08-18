package com.potheride.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PricingRules
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.Actor
import com.potheride.app.core.ride.RideState
import com.potheride.app.data.local.AppDatabase
import com.potheride.app.data.local.DhakaPlaces
import com.potheride.app.data.local.entities.TripStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end coverage of the booking flow against a real (in-memory) database.
 *
 * The existing suite tests `core/` — the geometry, the fare arithmetic, the state
 * machine — in isolation, which leaves the part that actually goes wrong untested: the
 * *orchestration*. Seat accounting, the ordering of a state transition against the
 * payment record, and whether a declined request gives its seats back are all decided
 * in [RoomRideDataSource], not in `core/`.
 *
 * Written against [RideDataSource] rather than the concrete class, so the same tests
 * can be pointed at the Firestore implementation when it lands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BookingFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RideDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomRideDataSource(db)
    }

    @After
    fun tearDown() = db.close()

    /** A driver with a vehicle and a published Mirpur-10 → Tongi route with [seats] seats. */
    private suspend fun publishRoute(seats: Int = 3) = run {
        val user = repo.findOrCreateUser("+8801712000001", "Kamal Hossain", AppLanguage.ENGLISH)
        val driver = repo.becomeDriver(user.id, "DL-99887")
        val vehicle = repo.registerVehicle(driver.id, VehicleClass.CNG, "DHK-1234", 3)
        val trip = repo.publishTrip(
            driverId = driver.id,
            vehicleId = vehicle.id,
            startAddress = "Mirpur-10",
            start = DhakaPlaces.mirpur10,
            endAddress = "Tongi Station",
            end = DhakaPlaces.tongi,
            departureTime = System.currentTimeMillis() + 30 * 60_000L,
            seats = seats,
            detourKm = 1.5,
            waypoints = listOf(DhakaPlaces.mirpur10, DhakaPlaces.kazipara, DhakaPlaces.airport, DhakaPlaces.tongi)
        )
        Triple(driver, trip, user)
    }

    private suspend fun passenger() =
        repo.findOrCreateUser("+8801812000002", "Farida Yasmin", AppLanguage.ENGLISH)

    private suspend fun findMatch(seatsNeeded: Int = 1) = repo.searchMatches(
        pickup = DhakaPlaces.kazipara,
        drop = DhakaPlaces.airport,
        seatsNeeded = seatsNeeded,
        earliestDeparture = System.currentTimeMillis() - 60 * 60_000L,
        latestDeparture = System.currentTimeMillis() + 6 * 60 * 60_000L,
        excludeDriverId = null,
        rules = PricingRules.DEFAULT
    )

    @Test
    fun `a passenger travelling along the route finds the published trip`() = runTest {
        publishRoute()
        val matches = findMatch()
        assertEquals(1, matches.size)
        // Kazipara → Airport is a genuine subset of Mirpur-10 → Tongi, so the overlap
        // must be a real fraction rather than the whole route or nothing.
        val overlap = matches.single().overlapPercent
        assertTrue("overlap was $overlap%", overlap in 1..99)
    }

    @Test
    fun `a passenger travelling against the route is not matched`() = runTest {
        publishRoute()
        val backwards = repo.searchMatches(
            pickup = DhakaPlaces.airport,
            drop = DhakaPlaces.kazipara,
            seatsNeeded = 1,
            earliestDeparture = System.currentTimeMillis() - 60 * 60_000L,
            latestDeparture = System.currentTimeMillis() + 6 * 60 * 60_000L,
            excludeDriverId = null,
            rules = PricingRules.DEFAULT
        )
        assertTrue("a wrong-way rider was matched", backwards.isEmpty())
    }

    @Test
    fun `requesting a seat creates a booking in REQUESTED`() = runTest {
        publishRoute()
        val rider = passenger()
        val result = repo.requestSeat(findMatch().single(), rider.id, "Kazipara", "Airport", 1)
        val booking = (result as RepoResult.Ok).value
        assertEquals(RideState.REQUESTED, booking.status)
        assertEquals(rider.id, booking.passengerId)
    }

    @Test
    fun `accepting a request holds the seats and completing the ride does not release them`() = runTest {
        val (_, trip, _) = publishRoute(seats = 3)
        val rider = passenger()
        val booking = (repo.requestSeat(findMatch().single(), rider.id, "Kazipara", "Airport", 2)
                as RepoResult.Ok).value

        repo.transition(booking.id, RideState.ACCEPTED, Actor.DRIVER, null)
        assertEquals(1, repo.findTrip(trip.id)!!.availableSeats)

        repo.transition(booking.id, RideState.DRIVER_ARRIVING, Actor.DRIVER, null)
        repo.transition(booking.id, RideState.PICKED_UP, Actor.DRIVER, null)
        repo.transition(booking.id, RideState.COMPLETED, Actor.DRIVER, null)

        // A finished ride must not hand the seat back — the passenger consumed it.
        assertEquals(1, repo.findTrip(trip.id)!!.availableSeats)
    }

    @Test
    fun `declining a request returns the seats to the trip`() = runTest {
        val (_, trip, _) = publishRoute(seats = 3)
        val rider = passenger()
        val booking = (repo.requestSeat(findMatch().single(), rider.id, "Kazipara", "Airport", 2)
                as RepoResult.Ok).value

        repo.transition(booking.id, RideState.ACCEPTED, Actor.DRIVER, null)
        assertEquals(1, repo.findTrip(trip.id)!!.availableSeats)

        repo.transition(booking.id, RideState.CANCELLED, Actor.PASSENGER, "changed my mind")
        assertEquals(3, repo.findTrip(trip.id)!!.availableSeats)
    }

    @Test
    fun `a trip with no seats left stops matching`() = runTest {
        val (_, trip, _) = publishRoute(seats = 1)
        val rider = passenger()
        val booking = (repo.requestSeat(findMatch().single(), rider.id, "Kazipara", "Airport", 1)
                as RepoResult.Ok).value
        repo.transition(booking.id, RideState.ACCEPTED, Actor.DRIVER, null)

        assertEquals(0, repo.findTrip(trip.id)!!.availableSeats)
        assertTrue("a full trip was still offered", findMatch().isEmpty())
    }

    @Test
    fun `an illegal transition is refused rather than applied`() = runTest {
        publishRoute()
        val rider = passenger()
        val booking = (repo.requestSeat(findMatch().single(), rider.id, "Kazipara", "Airport", 1)
                as RepoResult.Ok).value

        // REQUESTED -> COMPLETED skips acceptance and pickup entirely.
        val result = repo.transition(booking.id, RideState.COMPLETED, Actor.DRIVER, null)
        assertTrue("the skip was allowed", result is RepoResult.Failed)
        assertEquals(RideState.REQUESTED, repo.findBooking(booking.id)!!.status)
    }

    @Test
    fun `a passenger cannot accept their own request`() = runTest {
        publishRoute()
        val rider = passenger()
        val booking = (repo.requestSeat(findMatch().single(), rider.id, "Kazipara", "Airport", 1)
                as RepoResult.Ok).value

        val result = repo.transition(booking.id, RideState.ACCEPTED, Actor.PASSENGER, null)
        assertTrue("a passenger accepted their own ride", result is RepoResult.Failed)
    }

    @Test
    fun `paying a completed ride records the payment and splits the driver's earnings`() = runTest {
        val (driver, _, _) = publishRoute()
        val rider = passenger()
        val booking = (repo.requestSeat(findMatch().single(), rider.id, "Kazipara", "Airport", 1)
                as RepoResult.Ok).value

        repo.transition(booking.id, RideState.ACCEPTED, Actor.DRIVER, null)
        repo.transition(booking.id, RideState.DRIVER_ARRIVING, Actor.DRIVER, null)
        repo.transition(booking.id, RideState.PICKED_UP, Actor.DRIVER, null)
        repo.transition(booking.id, RideState.COMPLETED, Actor.DRIVER, null)

        val payment = (repo.payForBooking(booking.id, PaymentMethod.CASH) as RepoResult.Ok).value
        assertEquals(PaymentMethod.CASH, payment.method)
        assertEquals(
            "the fee and the driver's cut must account for the whole fare",
            payment.amountPoisha,
            payment.platformFeePoisha + payment.driverEarningsPoisha
        )

        val earnings = repo.earningsFor(driver.id)
        assertNotNull(earnings)
    }

    @Test
    fun `the driver sees the request on their trip`() = runTest {
        val (_, trip, _) = publishRoute()
        val rider = passenger()
        repo.requestSeat(findMatch().single(), rider.id, "Kazipara", "Airport", 1)

        val requests = repo.requestsForTrip(trip.id).first()
        assertEquals(1, requests.size)
        assertEquals(rider.id, requests.single().passengerId)
    }

    @Test
    fun `a recorded location moves the trip's live position`() = runTest {
        val (_, trip, _) = publishRoute()
        // A freshly published trip starts parked at its own origin rather than at a
        // null position, so the map has something to draw before the first GPS fix.
        assertEquals(DhakaPlaces.mirpur10.lat, repo.findTrip(trip.id)!!.livePoint!!.lat, 0.00001)

        repo.recordLocation(trip.id, DhakaPlaces.kazipara)

        val moved = repo.findTrip(trip.id)!!
        assertNotNull("the live position was not stored", moved.livePoint)
        assertEquals(DhakaPlaces.kazipara.lat, moved.livePoint!!.lat, 0.00001)
        // Progress is stored as distance along the polyline, not just a raw coordinate.
        assertTrue("travelled distance did not advance", moved.travelledKm > 0.0)
    }

    @Test
    fun `a cancelled trip stops being offered`() = runTest {
        val (_, trip, _) = publishRoute()
        assertEquals(1, findMatch().size)

        repo.setTripStatus(trip.id, TripStatus.CANCELLED)
        assertTrue("a cancelled trip was still offered", findMatch().isEmpty())
    }
}
