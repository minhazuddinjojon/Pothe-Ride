package com.potheride.app.data.local

import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.data.repository.RideDataSource

/**
 * Dhaka landmarks used both as demo data and as the place-picker's suggestion list.
 * Real coordinates, so the matching engine produces believable results the first
 * time someone opens the app.
 */
object DhakaPlaces {
    val mirpur10 = LatLng(23.8067, 90.3686)
    val kazipara = LatLng(23.7960, 90.3742)
    val shewrapara = LatLng(23.7889, 90.3768)
    val agargaon = LatLng(23.7780, 90.3796)
    val farmgate = LatLng(23.7583, 90.3897)
    val karwanBazar = LatLng(23.7509, 90.3934)
    val shahbag = LatLng(23.7383, 90.3956)
    val motijheel = LatLng(23.7331, 90.4172)
    val uttara = LatLng(23.8759, 90.3795)
    val airport = LatLng(23.8433, 90.3978)
    val tongi = LatLng(23.8909, 90.4023)
    val gazipur = LatLng(23.9999, 90.4203)
    val banani = LatLng(23.7937, 90.4066)
    val gulshan = LatLng(23.7925, 90.4078)
    val badda = LatLng(23.7806, 90.4256)
    val rampura = LatLng(23.7614, 90.4212)

    /** Searchable place list backing the pickup/destination fields. */
    val all: List<Pair<String, LatLng>> = listOf(
        "Mirpur-10, Dhaka" to mirpur10,
        "Kazipara, Dhaka" to kazipara,
        "Shewrapara, Dhaka" to shewrapara,
        "Agargaon, Dhaka" to agargaon,
        "Farmgate, Dhaka" to farmgate,
        "Karwan Bazar, Dhaka" to karwanBazar,
        "Shahbag, Dhaka" to shahbag,
        "Motijheel C/A, Dhaka" to motijheel,
        "Uttara Sector 7, Dhaka" to uttara,
        "Hazrat Shahjalal Airport" to airport,
        "Tongi Station, Gazipur" to tongi,
        "Gazipur Chowrasta" to gazipur,
        "Banani, Dhaka" to banani,
        "Gulshan-1, Dhaka" to gulshan,
        "Badda, Dhaka" to badda,
        "Rampura, Dhaka" to rampura
    )

    fun search(query: String): List<Pair<String, LatLng>> {
        val q = query.trim()
        if (q.isBlank()) return all
        return all.filter { it.first.contains(q, ignoreCase = true) }
    }

    fun addressFor(point: LatLng): String =
        all.minByOrNull {
            com.potheride.app.core.geo.GeoUtils.distanceKm(it.second, point)
        }?.first ?: "Dropped pin"
}

/**
 * Puts enough believable data in the database that every screen has something real
 * to show on first launch — three published routes across Dhaka, verified drivers
 * with rating histories, and a pending request waiting in the driver's queue.
 *
 * Runs exactly once: it checks for an existing seed marker before doing anything,
 * so reopening the app does not pile up duplicate trips.
 */
object DemoSeeder {

    private const val SEED_MARKER_PHONE = "+8801711000001"

    suspend fun seedIfEmpty(repo: RideDataSource) {
        if (repo.findUserByPhoneExists(SEED_MARKER_PHONE)) return

        // ---- Driver 1: Mirpur -> Gazipur, the long northbound corridor ----
        val rafiq = repo.findOrCreateUser(SEED_MARKER_PHONE, "Rafiqul Islam", AppLanguage.ENGLISH)
        val rafiqDriver = repo.becomeDriver(rafiq.id, "DHK-DRV-2291")
        repo.setDriverVerified(rafiqDriver.id, true)
        val axio = repo.registerVehicle(
            rafiqDriver.id, VehicleClass.CAR, "DHA-15-2231", capacity = 4, model = "Toyota Axio"
        )
        val trip1 = repo.publishTrip(
            driverId = rafiqDriver.id,
            vehicleId = axio.id,
            startAddress = "Mirpur-10, Dhaka", start = DhakaPlaces.mirpur10,
            endAddress = "Gazipur Chowrasta", end = DhakaPlaces.gazipur,
            departureTime = System.currentTimeMillis() + 45 * 60_000L,
            seats = 3,
            detourKm = 1.5,
            waypoints = listOf(DhakaPlaces.uttara, DhakaPlaces.airport, DhakaPlaces.tongi)
        )

        // ---- Driver 2: Uttara -> Motijheel, the office run ----
        val shirin = repo.findOrCreateUser("+8801711000002", "Shirin Akter", AppLanguage.BANGLA)
        val shirinDriver = repo.becomeDriver(shirin.id, "DHK-DRV-4471")
        repo.setDriverVerified(shirinDriver.id, true)
        val cng = repo.registerVehicle(
            shirinDriver.id, VehicleClass.CNG, "DHA-11-9087", capacity = 3
        )
        repo.publishTrip(
            driverId = shirinDriver.id,
            vehicleId = cng.id,
            startAddress = "Uttara Sector 7, Dhaka", start = DhakaPlaces.uttara,
            endAddress = "Motijheel C/A, Dhaka", end = DhakaPlaces.motijheel,
            departureTime = System.currentTimeMillis() + 70 * 60_000L,
            seats = 2,
            detourKm = 1.0,
            waypoints = listOf(DhakaPlaces.banani, DhakaPlaces.karwanBazar, DhakaPlaces.shahbag)
        )

        // ---- Driver 3: Mirpur -> Motijheel on a bike, overlapping the first leg ----
        val jahangir = repo.findOrCreateUser("+8801711000004", "Jahangir Alam", AppLanguage.ENGLISH)
        val jahangirDriver = repo.becomeDriver(jahangir.id, "DHK-DRV-8812")
        val bike = repo.registerVehicle(
            jahangirDriver.id, VehicleClass.BIKE, "DHA-22-4410", capacity = 1, model = "Bajaj Pulsar"
        )
        repo.publishTrip(
            driverId = jahangirDriver.id,
            vehicleId = bike.id,
            startAddress = "Mirpur-10, Dhaka", start = DhakaPlaces.mirpur10,
            endAddress = "Motijheel C/A, Dhaka", end = DhakaPlaces.motijheel,
            departureTime = System.currentTimeMillis() + 30 * 60_000L,
            seats = 1,
            detourKm = 2.0,
            waypoints = listOf(
                DhakaPlaces.shewrapara, DhakaPlaces.agargaon,
                DhakaPlaces.farmgate, DhakaPlaces.shahbag
            )
        )

        // ---- A passenger with a pending request, so the driver queue isn't empty ----
        val kamal = repo.findOrCreateUser("+8801911000003", "Kamal Hossain", AppLanguage.ENGLISH)
        repo.savePlace(kamal.id, "Home", "Mirpur-10, Dhaka", DhakaPlaces.mirpur10)
        repo.savePlace(kamal.id, "Work", "Motijheel C/A, Dhaka", DhakaPlaces.motijheel)
        repo.addTrustedContact(kamal.id, "Ammu", "+8801711223344")

        // Build the request through the same matching path a real passenger uses, so
        // the seeded booking carries a genuine overlap ratio and fare rather than
        // hand-written numbers that the pricing rules would never produce.
        val matches = repo.searchMatches(
            pickup = DhakaPlaces.mirpur10,
            drop = DhakaPlaces.tongi,
            seatsNeeded = 1,
            earliestDeparture = System.currentTimeMillis(),
            latestDeparture = System.currentTimeMillis() + 4 * 60 * 60_000L
        )
        matches.firstOrNull { it.trip.id == trip1.id }?.let { match ->
            repo.requestSeat(
                match = match,
                passengerId = kamal.id,
                pickupAddress = "Mirpur-10, Dhaka",
                dropAddress = "Tongi Station, Gazipur",
                seats = 1
            )
        }
    }
}
