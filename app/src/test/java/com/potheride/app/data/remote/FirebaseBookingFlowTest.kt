package com.potheride.app.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PricingRules
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.Actor
import com.potheride.app.core.ride.RideState
import com.potheride.app.data.local.DhakaPlaces
import com.potheride.app.data.local.entities.TripStatus
import com.potheride.app.data.repository.RepoResult
import com.potheride.app.data.repository.RideDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.Socket

/**
 * The same booking-flow contract as `BookingFlowTest`, run against Firestore.
 *
 * This is the payoff for extracting `RideDataSource` at Level 3a: the assertions below
 * are the Room ones, unchanged in meaning, pointed at a different implementation. If the
 * two backends ever disagree about seat accounting, transition legality or the fare
 * split, one of these two suites goes red.
 *
 * Runs against the **emulator**, never the real project — see [emulatorRunning]. A test
 * suite that writes to production Firestore is a test suite that eventually deletes
 * someone's data.
 *
 * Uses `runBlocking`, not `runTest`. Every call here is real network I/O against the
 * emulator, and `runTest`'s virtual clock does not advance for work happening on
 * Firestore's own executors — a snapshot listener collected with `first()` simply never
 * delivers, and the test times out after a minute looking like a deadlock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FirebaseBookingFlowTest {

    private lateinit var repo: RideDataSource

    companion object {
        private const val EMULATOR_HOST = "127.0.0.1"
        private const val EMULATOR_PORT = 8080

        /**
         * Whether the Firestore emulator is listening.
         *
         * When it is not, every test here is skipped rather than failed. A developer who
         * has not started the emulator should see a green build with skips, not a wall of
         * red that trains them to ignore failures — but CI should start it.
         */
        private fun emulatorRunning(): Boolean = runCatching {
            Socket(EMULATOR_HOST, EMULATOR_PORT).close(); true
        }.getOrDefault(false)

        /** Makes each test's FirebaseApp name unique. */
        val appCounter = java.util.concurrent.atomic.AtomicInteger(0)
    }

    @Before
    fun setUp() {
        assumeTrue(
            "Firestore emulator not running on $EMULATOR_HOST:$EMULATOR_PORT — " +
                "start it with: firebase emulators:start --only firestore",
            emulatorRunning()
        )

        // A uniquely named FirebaseApp per test.
        //
        // `useEmulator()` may only be called before a FirebaseFirestore instance is
        // used, and the default instance survives between tests in the same sandbox —
        // so the second test would otherwise fail with "already initialized". A fresh
        // named app per test gives each one a fresh Firestore to point at the emulator.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = "test-" + appCounter.incrementAndGet()
        val app = FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setProjectId("pothe-ride-test")
                .setApplicationId("1:1:android:1")
                .setApiKey("test-api-key")
                .build(),
            appName
        )

        val firestore = FirebaseFirestore.getInstance(app).apply {
            // Guard against ever pointing at the real project.
            useEmulator(EMULATOR_HOST, EMULATOR_PORT)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
        }
        repo = FirebaseRideDataSource(firestore)
    }

    // ------------------------------------------------------------------
    // Fixtures — deliberately unique per run, since the emulator keeps state
    // for the lifetime of the process.
    // ------------------------------------------------------------------

    private var seq = 0
    private fun uniquePhone(): String = "+88017${(1000000..9999999).random()}${seq++}"

    private suspend fun publishRoute(seats: Int = 3) = run {
        val user = repo.findOrCreateUser(uniquePhone(), "Kamal Hossain", AppLanguage.ENGLISH)
        val driver = repo.becomeDriver(user.id, "DL-99887")
        val vehicle = repo.registerVehicle(driver.id, VehicleClass.CNG, "DHK-${seq++}", 3)
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
            waypoints = listOf(
                DhakaPlaces.mirpur10, DhakaPlaces.kazipara, DhakaPlaces.airport, DhakaPlaces.tongi
            )
        )
        Triple(driver, trip, user)
    }

    private suspend fun passenger() =
        repo.findOrCreateUser(uniquePhone(), "Farida Yasmin", AppLanguage.ENGLISH)

    private suspend fun matchesFor(tripId: String) = repo.searchMatches(
        pickup = DhakaPlaces.kazipara,
        drop = DhakaPlaces.airport,
        seatsNeeded = 1,
        earliestDeparture = System.currentTimeMillis() - 60 * 60_000L,
        latestDeparture = System.currentTimeMillis() + 6 * 60 * 60_000L,
        excludeDriverId = null,
        rules = PricingRules.DEFAULT
    ).filter { it.trip.id == tripId }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `a published route is written and read back intact`() = runBlocking {
        val (_, trip, _) = publishRoute()
        val stored = repo.findTrip(trip.id)
        assertNotNull(stored)
        assertEquals("Mirpur-10", stored!!.startAddress)
        assertEquals(3, stored.availableSeats)
        // The polyline survives the flat-array encoding.
        assertEquals(4, repo.routeFor(trip.id).size)
    }

    @Test
    fun `a published route starts parked at its own origin`() = runBlocking {
        val (_, trip, _) = publishRoute()
        val live = repo.findTrip(trip.id)!!.livePoint
        assertNotNull("no live position seeded", live)
        assertEquals(DhakaPlaces.mirpur10.lat, live!!.lat, 0.0001)
    }

    @Test
    fun `a passenger travelling along the route finds the published trip`() = runBlocking {
        val (_, trip, _) = publishRoute()
        val matches = matchesFor(trip.id)
        assertEquals(1, matches.size)
        assertTrue(matches.single().overlapPercent in 1..99)
    }

    @Test
    fun `requesting a seat creates a booking and claims the seats atomically`() = runBlocking {
        val (_, trip, _) = publishRoute(seats = 3)
        val rider = passenger()
        val match = matchesFor(trip.id).single()

        val booking = (repo.requestSeat(match, rider.id, "Kazipara", "Airport", 2)
            as RepoResult.Ok).value

        assertEquals(RideState.REQUESTED, booking.status)
        // Seats are taken at request time, not at acceptance — otherwise two passengers
        // can both be told "confirmed" for the same last seat.
        assertEquals(1, repo.findTrip(trip.id)!!.availableSeats)
    }

    @Test
    fun `the last seat cannot be sold twice`() = runBlocking {
        val (_, trip, _) = publishRoute(seats = 1)
        val first = passenger()
        val second = passenger()
        val match = matchesFor(trip.id).single()

        val a = repo.requestSeat(match, first.id, "Kazipara", "Airport", 1)
        val b = repo.requestSeat(match, second.id, "Kazipara", "Airport", 1)

        assertTrue("the first claim should succeed", a is RepoResult.Ok)
        assertTrue("the second claim oversold the vehicle", b is RepoResult.Failed)
        assertEquals(0, repo.findTrip(trip.id)!!.availableSeats)
    }

    @Test
    fun `cancelling returns the seats to the route`() = runBlocking {
        val (_, trip, _) = publishRoute(seats = 3)
        val rider = passenger()
        val booking = (repo.requestSeat(
            matchesFor(trip.id).single(), rider.id, "Kazipara", "Airport", 2
        ) as RepoResult.Ok).value

        repo.transition(booking.id, RideState.ACCEPTED, Actor.DRIVER, null)
        assertEquals(1, repo.findTrip(trip.id)!!.availableSeats)

        repo.transition(booking.id, RideState.CANCELLED, Actor.PASSENGER, "changed my mind")
        assertEquals(3, repo.findTrip(trip.id)!!.availableSeats)
    }

    @Test
    fun `a completed ride does not hand the seat back`() = runBlocking {
        val (_, trip, _) = publishRoute(seats = 3)
        val rider = passenger()
        val booking = (repo.requestSeat(
            matchesFor(trip.id).single(), rider.id, "Kazipara", "Airport", 2
        ) as RepoResult.Ok).value

        listOf(
            RideState.ACCEPTED, RideState.DRIVER_ARRIVING,
            RideState.PICKED_UP, RideState.COMPLETED
        ).forEach { repo.transition(booking.id, it, Actor.DRIVER, null) }

        assertEquals(1, repo.findTrip(trip.id)!!.availableSeats)
    }

    @Test
    fun `an illegal transition is refused rather than applied`() = runBlocking {
        val (_, trip, _) = publishRoute()
        val rider = passenger()
        val booking = (repo.requestSeat(
            matchesFor(trip.id).single(), rider.id, "Kazipara", "Airport", 1
        ) as RepoResult.Ok).value

        val result = repo.transition(booking.id, RideState.COMPLETED, Actor.DRIVER, null)
        assertTrue("the skip was allowed", result is RepoResult.Failed)
        assertEquals(RideState.REQUESTED, repo.findBooking(booking.id)!!.status)
    }

    @Test
    fun `a passenger cannot accept their own request`() = runBlocking {
        val (_, trip, _) = publishRoute()
        val rider = passenger()
        val booking = (repo.requestSeat(
            matchesFor(trip.id).single(), rider.id, "Kazipara", "Airport", 1
        ) as RepoResult.Ok).value

        val result = repo.transition(booking.id, RideState.ACCEPTED, Actor.PASSENGER, null)
        assertTrue(result is RepoResult.Failed)
    }

    @Test
    fun `paying a completed ride splits the fare exactly`() = runBlocking {
        val (driver, trip, _) = publishRoute()
        val rider = passenger()
        val booking = (repo.requestSeat(
            matchesFor(trip.id).single(), rider.id, "Kazipara", "Airport", 1
        ) as RepoResult.Ok).value

        listOf(
            RideState.ACCEPTED, RideState.DRIVER_ARRIVING,
            RideState.PICKED_UP, RideState.COMPLETED
        ).forEach { repo.transition(booking.id, it, Actor.DRIVER, null) }

        val payment = (repo.payForBooking(booking.id, PaymentMethod.CASH)
            as RepoResult.Ok).value

        assertEquals(
            "the fee and the driver's cut must account for the whole fare",
            payment.amountPoisha,
            payment.platformFeePoisha + payment.driverEarningsPoisha
        )
        assertTrue(repo.earningsFor(driver.id).completedTrips >= 1)
    }

    @Test
    fun `the driver sees the request on their route`() = runBlocking {
        val (_, trip, _) = publishRoute()
        val rider = passenger()
        repo.requestSeat(matchesFor(trip.id).single(), rider.id, "Kazipara", "Airport", 1)

        val requests = repo.requestsForTrip(trip.id).first()
        assertEquals(1, requests.size)
        assertEquals(rider.id, requests.single().passengerId)
    }

    @Test
    fun `a recorded location advances the live position and progress`() = runBlocking {
        val (_, trip, _) = publishRoute()
        repo.recordLocation(trip.id, DhakaPlaces.kazipara)

        val moved = repo.findTrip(trip.id)!!
        assertEquals(DhakaPlaces.kazipara.lat, moved.livePoint!!.lat, 0.0001)
        assertTrue("travelled distance did not advance", moved.travelledKm > 0.0)
    }

    @Test
    fun `a cancelled route stops being offered`() = runBlocking {
        val (_, trip, _) = publishRoute()
        assertEquals(1, matchesFor(trip.id).size)

        repo.setTripStatus(trip.id, TripStatus.CANCELLED)
        assertTrue(matchesFor(trip.id).isEmpty())
    }

    @Test
    fun `requesting a seat notifies the driver`() = runBlocking {
        val (_, trip, driverUser) = publishRoute()
        val rider = passenger()
        repo.requestSeat(matchesFor(trip.id).single(), rider.id, "Kazipara", "Airport", 1)

        val sent = repo.notificationsFor(driverUser.id).first()
        assertTrue("the driver was never told", sent.isNotEmpty())
        assertTrue(sent.any { it.bodyEn.contains("Kazipara") })
    }
}
