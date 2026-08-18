package com.potheride.app.data.remote

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.matching.MatchOutcome
import com.potheride.app.core.matching.MatchScorer
import com.potheride.app.core.matching.RouteMatcher
import com.potheride.app.core.pricing.FareCalculator
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PaymentStatus
import com.potheride.app.core.pricing.PricingRules
import com.potheride.app.core.pricing.Taka
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.Actor
import com.potheride.app.core.ride.EtaCalculator
import com.potheride.app.core.ride.RideState
import com.potheride.app.core.ride.RideStateMachine
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
import com.potheride.app.data.model.BookingDetail
import com.potheride.app.data.model.DriverSummary
import com.potheride.app.data.model.EarningsSummary
import com.potheride.app.data.model.MatchedRide
import com.potheride.app.data.model.PlatformStats
import com.potheride.app.data.repository.PendingNotification
import com.potheride.app.data.repository.RepoResult
import com.potheride.app.data.repository.RideDataSource
import com.potheride.app.data.repository.RideNotifications
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

/**
 * The Firestore-backed implementation of [RideDataSource].
 *
 * Every business rule is delegated to `core/` — [RouteMatcher], [FareCalculator],
 * [RideStateMachine], [MatchScorer], [EtaCalculator] — exactly as the Room
 * implementation does. That is not a stylistic preference: if the two backends ever
 * compute a fare or authorise a transition differently, the same ride costs different
 * amounts depending on which store answered, and no test at this layer would catch it.
 * This class is plumbing only.
 *
 * Where it *does* differ from Room is where Firestore genuinely differs:
 *  - seat counts move through [SeatAccounting]'s transactions, because Firestore is
 *    shared across devices and a read-then-write can interleave;
 *  - reads the UI watches are snapshot listeners via [asFlow];
 *  - there are no joins, so some ids are denormalised at write time.
 */
class FirebaseRideDataSource(
    private val firestore: FirebaseFirestore
) : RideDataSource {

    private fun newId(): String = UUID.randomUUID().toString()

    private val users get() = firestore.collection(FirestoreSchema.USERS)
    private val drivers get() = firestore.collection(FirestoreSchema.DRIVERS)
    private val routes get() = firestore.collection(FirestoreSchema.ROUTES)
    private val rideRequests get() = firestore.collection(FirestoreSchema.RIDE_REQUESTS)
    private val payments get() = firestore.collection(FirestoreSchema.PAYMENTS)
    private val notifications get() = firestore.collection(FirestoreSchema.NOTIFICATIONS)
    private val liveLocations get() = firestore.collection(FirestoreSchema.LIVE_LOCATIONS)
    private val safetyEvents get() = firestore.collection(FirestoreSchema.SAFETY_EVENTS)

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    override suspend fun findOrCreateUser(
        phone: String,
        name: String,
        language: AppLanguage
    ): UserEntity {
        val existing = users.whereEqualTo(FirestoreSchema.User.PHONE, phone).limit(1).get().await()
            .documents.firstOrNull()

        if (existing != null) {
            val current = FirestoreMappers.userFromMap(existing.id, existing.data.orEmpty())
            // Someone re-verifying an existing number may have changed their name.
            if (name.isNotBlank() && name != current.name) {
                val updated = current.copy(name = name, otpVerified = true)
                users.document(updated.id).set(FirestoreMappers.userToMap(updated)).await()
                return updated
            }
            return current
        }

        val user = UserEntity(
            id = newId(),
            phone = phone,
            name = name,
            language = language.code,
            otpVerified = true,
            photoTint = phone.hashCode()
        )
        users.document(user.id).set(FirestoreMappers.userToMap(user)).await()
        return user
    }

    override suspend fun findUser(id: String): UserEntity? =
        users.document(id).get().await().let { snap ->
            snap.data?.let { FirestoreMappers.userFromMap(snap.id, it) }
        }

    override suspend fun findUserByPhoneExists(phone: String): Boolean =
        !users.whereEqualTo(FirestoreSchema.User.PHONE, phone).limit(1).get().await().isEmpty

    override fun observeUser(id: String): Flow<UserEntity?> =
        users.document(id).asFlow().map { it.mapDocument(FirestoreMappers::userFromMap) }

    override fun observeAllUsers(): Flow<List<UserEntity>> =
        users.asFlow().map { it.mapDocuments(FirestoreMappers::userFromMap) }

    override suspend fun setLanguage(userId: String, language: AppLanguage) {
        users.document(userId).update(FirestoreSchema.User.LANGUAGE, language.code).await()
    }

    override suspend fun setUserBlocked(userId: String, blocked: Boolean) {
        users.document(userId).update(FirestoreSchema.User.BLOCKED, blocked).await()
    }

    // ------------------------------------------------------------------
    // Driver profile and vehicles
    // ------------------------------------------------------------------

    override suspend fun findDriverForUser(userId: String): DriverProfileEntity? =
        drivers.whereEqualTo(FirestoreSchema.Driver.USER_ID, userId).limit(1).get().await()
            .documents.firstOrNull()
            ?.let { FirestoreMappers.driverFromMap(it.id, it.data.orEmpty()) }

    override fun observeDriver(driverId: String): Flow<DriverProfileEntity?> =
        drivers.document(driverId).asFlow().map { it.mapDocument(FirestoreMappers::driverFromMap) }

    override fun observeAllDrivers(): Flow<List<DriverProfileEntity>> =
        drivers.asFlow().map { it.mapDocuments(FirestoreMappers::driverFromMap) }

    override suspend fun becomeDriver(userId: String, licenseNumber: String): DriverProfileEntity {
        findDriverForUser(userId)?.let { return it }

        val profile = DriverProfileEntity(id = newId(), userId = userId, licenseNumber = licenseNumber)
        drivers.document(profile.id).set(FirestoreMappers.driverToMap(profile)).await()
        // The role claim is what the security rules read to decide who may publish a
        // route; a driver profile without it is invisible to the rules.
        users.document(userId).update(FirestoreSchema.User.ROLE, FirestoreMappers.ROLE_DRIVER).await()
        return profile
    }

    override suspend fun setDriverVerified(driverId: String, verified: Boolean) {
        drivers.document(driverId).update(FirestoreSchema.Driver.VERIFIED, verified).await()
    }

    override fun vehiclesFor(driverId: String): Flow<List<VehicleEntity>> =
        drivers.document(driverId).asFlow().map { snap ->
            snap?.data?.let { FirestoreMappers.vehiclesFromDriverMap(driverId, it) } ?: emptyList()
        }

    private suspend fun vehicleListFor(driverId: String): List<VehicleEntity> =
        drivers.document(driverId).get().await().data
            ?.let { FirestoreMappers.vehiclesFromDriverMap(driverId, it) }
            ?: emptyList()

    private suspend fun findVehicle(driverId: String, vehicleId: String): VehicleEntity? =
        vehicleListFor(driverId).firstOrNull { it.id == vehicleId }

    /**
     * Registers a vehicle, reusing the row when the driver re-enters a plate they already
     * own — otherwise publishing three trips in the same CNG leaves three duplicates.
     */
    override suspend fun registerVehicle(
        driverId: String,
        type: VehicleClass,
        plate: String,
        capacity: Int,
        model: String?
    ): VehicleEntity {
        val normalisedPlate = plate.trim().uppercase()
        val existing = vehicleListFor(driverId)
        val match = existing.firstOrNull { it.plateNumber.equals(normalisedPlate, ignoreCase = true) }

        val vehicle = match?.copy(type = type, capacity = capacity, model = model)
            ?: VehicleEntity(
                id = newId(), driverId = driverId, type = type,
                plateNumber = normalisedPlate, model = model, capacity = capacity
            )

        val updated = existing.filterNot { it.id == vehicle.id } + vehicle
        drivers.document(driverId)
            .update(FirestoreSchema.Driver.VEHICLES, updated.map(FirestoreMappers::vehicleToMap))
            .await()
        return vehicle
    }

    // ------------------------------------------------------------------
    // Publishing a route
    // ------------------------------------------------------------------

    override suspend fun publishTrip(
        driverId: String,
        vehicleId: String,
        startAddress: String,
        start: LatLng,
        endAddress: String,
        end: LatLng,
        departureTime: Long,
        seats: Int,
        detourKm: Double,
        waypoints: List<LatLng>
    ): TripEntity {
        // Anchor the stored polyline to the real endpoints whatever the caller passed.
        val path = buildList {
            add(start)
            waypoints.forEach { if (it != start && it != end) add(it) }
            add(end)
        }

        val trip = TripEntity(
            id = newId(),
            driverId = driverId,
            vehicleId = vehicleId,
            startAddress = startAddress,
            startLat = start.lat, startLng = start.lng,
            endAddress = endAddress,
            endLat = end.lat, endLng = end.lng,
            departureTime = departureTime,
            totalSeats = seats,
            availableSeats = seats,
            detourKm = detourKm,
            currentLat = start.lat, currentLng = start.lng
        )

        routes.document(trip.id).set(FirestoreMappers.routeToMap(trip, path)).await()
        // A route starts parked at its own origin so the map has something to draw
        // before the first GPS fix arrives.
        liveLocations.document(trip.id).set(
            FirestoreMappers.liveLocationToMap(trip.id, driverId, start, 0.0, System.currentTimeMillis())
        ).await()
        return trip
    }

    override suspend fun findTrip(tripId: String): TripEntity? {
        val snap = routes.document(tripId).get().await()
        val data = snap.data ?: return null
        return withLiveLocation(FirestoreMappers.routeFromMap(snap.id, data))
    }

    /**
     * Composes a route with its live position.
     *
     * The two are stored apart so a 5-second GPS write does not rewrite the whole
     * polyline, but [TripEntity] carries both — so every read that hands a trip to the
     * UI has to put them back together.
     */
    private suspend fun withLiveLocation(trip: TripEntity): TripEntity {
        val live = liveLocations.document(trip.id).get().await().data
            ?.let { FirestoreMappers.liveLocationFromMap(it) }
            ?: return trip
        return trip.copy(
            currentLat = live.position.lat,
            currentLng = live.position.lng,
            travelledKm = live.travelledKm,
            lastLocationAt = live.updatedAt
        )
    }

    override fun observeTrip(tripId: String): Flow<TripEntity?> =
        routes.document(tripId).asFlow().map { it.mapDocument(FirestoreMappers::routeFromMap) }

    override fun tripsByDriver(driverId: String): Flow<List<TripEntity>> =
        routes.whereEqualTo(FirestoreSchema.Route.DRIVER_ID, driverId)
            .asFlow().map { it.mapDocuments(FirestoreMappers::routeFromMap) }

    override fun activeTripForDriver(driverId: String): Flow<TripEntity?> =
        routes.whereEqualTo(FirestoreSchema.Route.DRIVER_ID, driverId)
            .whereIn(FirestoreSchema.Route.STATUS, ACTIVE_TRIP_STATUSES)
            .asFlow()
            .map { snap ->
                snap.mapDocuments(FirestoreMappers::routeFromMap)
                    .minByOrNull { it.departureTime }
            }

    override suspend fun routeFor(tripId: String): List<LatLng> =
        routes.document(tripId).get().await().data
            ?.let { FirestoreMappers.routeWaypoints(it) }
            ?: emptyList()

    override suspend fun setTripStatus(tripId: String, status: TripStatus) {
        routes.document(tripId).update(FirestoreSchema.Route.STATUS, status.name).await()
    }

    /**
     * Records a GPS fix. Progress is stored as distance along the published polyline
     * rather than a raw coordinate, because that is what the ETA and the map's progress
     * indicator need, and it stays correct when the reported position wobbles off road.
     */
    override suspend fun recordLocation(tripId: String, position: LatLng) {
        val route = routeFor(tripId)
        val travelled = if (route.size < 2) 0.0 else {
            val cumulative = GeoUtils.cumulativeKm(route)
            RouteMatcher.anchorOf(route, cumulative, position).distanceAlongRouteKm
        }
        val driverId = routes.document(tripId).get().await().data
            ?.let { it[FirestoreSchema.Route.DRIVER_ID] as? String } ?: ""

        liveLocations.document(tripId).set(
            FirestoreMappers.liveLocationToMap(
                tripId, driverId, position, travelled, System.currentTimeMillis()
            )
        ).await()
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    override suspend fun searchMatches(
        pickup: LatLng,
        drop: LatLng,
        seatsNeeded: Int,
        earliestDeparture: Long,
        latestDeparture: Long,
        excludeDriverId: String?,
        rules: PricingRules
    ): List<MatchedRide> {
        // Firestore is asked only for what it can index cheaply — status and a departure
        // window. Seat count and the geometry are filtered here, because a range filter
        // on a second field would force a composite index for every combination, and the
        // matcher has to run client-side regardless.
        val candidates = routes
            .whereIn(FirestoreSchema.Route.STATUS, ACTIVE_TRIP_STATUSES)
            .whereGreaterThanOrEqualTo(FirestoreSchema.Route.DEPARTURE_TIME, earliestDeparture)
            .whereLessThanOrEqualTo(FirestoreSchema.Route.DEPARTURE_TIME, latestDeparture)
            .get().await()

        val now = System.currentTimeMillis()
        val results = mutableListOf<MatchedRide>()

        for (doc in candidates.documents) {
            val data = doc.data ?: continue
            val trip = FirestoreMappers.routeFromMap(doc.id, data)

            if (trip.availableSeats < seatsNeeded) continue
            if (excludeDriverId != null && trip.driverId == excludeDriverId) continue

            val route = FirestoreMappers.routeWaypoints(data)
            val outcome = RouteMatcher.match(route, pickup, drop, trip.detourKm)
            if (outcome !is MatchOutcome.Matched) continue
            val match = outcome.result

            val driver = drivers.document(trip.driverId).get().await()
                .let { snap -> snap.data?.let { FirestoreMappers.driverFromMap(snap.id, it) } }
                ?: continue
            val vehicle = findVehicle(trip.driverId, trip.vehicleId) ?: continue
            val user = findUser(driver.userId) ?: continue
            if (user.blocked) continue
            if (seatsNeeded > vehicle.capacity) continue

            val hour = hourOf(trip.departureTime)
            val minutes = EtaCalculator.travelMinutes(match.sharedDistanceKm, vehicle.type, hour)
            val fare = FareCalculator.calculate(
                vehicleClass = vehicle.type,
                distanceKm = match.sharedDistanceKm,
                durationMinutes = minutes,
                seats = seatsNeeded,
                routeOverlapRatio = match.overlapRatio,
                departureHour = hour,
                rules = rules
            )
            val pickupEta = EtaCalculator.pickupEtaMillis(
                trip.departureTime, match.pickup.distanceAlongRouteKm, vehicle.type, hour
            )

            results.add(
                MatchedRide(
                    trip = trip,
                    driver = summarise(driver, user, vehicle),
                    route = route,
                    match = match,
                    fare = fare,
                    pickup = pickup,
                    drop = drop,
                    pickupEtaMillis = pickupEta,
                    dropoffEtaMillis = EtaCalculator.dropoffEtaMillis(
                        pickupEta, match.sharedDistanceKm, vehicle.type, hour
                    ),
                    score = MatchScorer.score(match, trip.departureTime, now)
                )
            )
        }

        return results.sortedWith(
            compareByDescending<MatchedRide> { it.score.value }.thenBy { it.trip.departureTime }
        )
    }

    // ------------------------------------------------------------------
    // Bookings
    // ------------------------------------------------------------------

    override suspend fun requestSeat(
        match: MatchedRide,
        passengerId: String,
        pickupAddress: String,
        dropAddress: String,
        seats: Int
    ): RepoResult<BookingEntity> {
        if (match.trip.driverId == findDriverForUser(passengerId)?.id) {
            return RepoResult.Failed("You can't book your own route.", "নিজের রুটে আসন নেওয়া যাবে না।")
        }

        // Seats are claimed up front and atomically. Creating the booking first and
        // decrementing after would let two passengers both be told "confirmed" for the
        // same last seat.
        val claim = SeatAccounting.claimSeats(firestore, match.trip.id, seats)
        if (claim is SeatClaimResult.Refused) {
            return when (claim.reason) {
                SeatClaimFailure.ROUTE_GONE ->
                    RepoResult.Failed("This route is no longer available.", "এই রুটটি আর নেই।")
                SeatClaimFailure.ROUTE_ENDED ->
                    RepoResult.Failed("This route has already ended.", "এই রুটটি শেষ হয়ে গেছে।")
                SeatClaimFailure.NOT_ENOUGH_SEATS -> RepoResult.Failed(
                    "Only ${claim.availableSeats} seat(s) left on this route.",
                    "এই রুটে আর ${claim.availableSeats}টি আসন বাকি আছে।"
                )
            }
        }

        val booking = BookingEntity(
            id = newId(),
            tripId = match.trip.id,
            passengerId = passengerId,
            pickupAddress = pickupAddress,
            pickupLat = match.pickup.lat, pickupLng = match.pickup.lng,
            dropAddress = dropAddress,
            dropLat = match.drop.lat, dropLng = match.drop.lng,
            seatsRequested = seats,
            routeOverlapRatio = match.match.overlapRatio,
            sharedDistanceKm = match.match.sharedDistanceKm,
            detourKm = match.match.detourKm,
            farePoisha = match.fare.perSeatFare.poisha,
            totalPoisha = match.fare.totalFare.poisha,
            pickupEtaMillis = match.pickupEtaMillis,
            dropoffEtaMillis = match.dropoffEtaMillis
        )

        runCatching {
            rideRequests.document(booking.id)
                .set(FirestoreMappers.rideRequestToMap(booking, match.trip.driverId)).await()
        }.onFailure { error ->
            // The seats are already claimed. Give them back rather than stranding them
            // on a booking that was never written.
            SeatAccounting.releaseSeats(firestore, match.trip.id, seats)
            throw error
        }

        emit(
            RideNotifications.seatRequested(
                driverUserId = match.driver.userId,
                passengerName = findUser(passengerId)?.name,
                seats = seats,
                pickupAddress = pickupAddress,
                dropAddress = dropAddress,
                bookingId = booking.id
            )
        )
        return RepoResult.Ok(booking)
    }

    override fun requestsForTrip(tripId: String): Flow<List<BookingEntity>> =
        rideRequests.whereEqualTo(FirestoreSchema.RideRequest.TRIP_ID, tripId)
            .asFlow().map { it.mapDocuments(FirestoreMappers::rideRequestFromMap) }

    override fun bookingsForPassenger(userId: String): Flow<List<BookingEntity>> =
        rideRequests.whereEqualTo(FirestoreSchema.RideRequest.PASSENGER_ID, userId)
            .orderBy(FirestoreSchema.RideRequest.REQUESTED_AT, Query.Direction.DESCENDING)
            .asFlow().map { it.mapDocuments(FirestoreMappers::rideRequestFromMap) }

    override fun bookingsForDriver(driverId: String): Flow<List<BookingEntity>> =
        rideRequests.whereEqualTo(FirestoreSchema.RideRequest.DRIVER_ID, driverId)
            .orderBy(FirestoreSchema.RideRequest.REQUESTED_AT, Query.Direction.DESCENDING)
            .asFlow().map { it.mapDocuments(FirestoreMappers::rideRequestFromMap) }

    override fun activeBookingForPassenger(userId: String): Flow<BookingEntity?> =
        rideRequests.whereEqualTo(FirestoreSchema.RideRequest.PASSENGER_ID, userId)
            .whereIn(FirestoreSchema.RideRequest.STATUS, LIVE_BOOKING_STATUSES)
            .asFlow()
            .map { snap ->
                snap.mapDocuments(FirestoreMappers::rideRequestFromMap)
                    .maxByOrNull { it.requestedAt }
            }

    override fun observeBooking(id: String): Flow<BookingEntity?> =
        rideRequests.document(id).asFlow()
            .map { it.mapDocument(FirestoreMappers::rideRequestFromMap) }

    override suspend fun findBooking(id: String): BookingEntity? =
        rideRequests.document(id).get().await().let { snap ->
            snap.data?.let { FirestoreMappers.rideRequestFromMap(snap.id, it) }
        }

    /**
     * The only way a booking's status changes. Validates against the state machine
     * first, then applies every side effect: seat accounting, driver trip counts, and
     * the notifications to both parties.
     */
    override suspend fun transition(
        bookingId: String,
        to: RideState,
        actor: Actor,
        reason: String?
    ): RepoResult<BookingEntity> {
        val booking = findBooking(bookingId)
            ?: return RepoResult.Failed("That ride no longer exists.", "রাইডটি আর নেই।")

        RideStateMachine.validate(booking.status, to, actor)?.let { error ->
            return RepoResult.Failed(error.message, error.message)
        }

        val now = System.currentTimeMillis()
        rideRequests.document(bookingId).update(
            mapOf(
                FirestoreSchema.RideRequest.STATUS to to.name,
                FirestoreSchema.RideRequest.ACCEPTED_AT to
                    (if (to == RideState.ACCEPTED) now else booking.acceptedAt),
                FirestoreSchema.RideRequest.COMPLETED_AT to
                    (if (to == RideState.COMPLETED) now else booking.completedAt),
                FirestoreSchema.RideRequest.CANCELLATION_REASON to
                    (reason ?: booking.cancellationReason)
            )
        ).await()

        // Seats were already claimed when the request was made, so ACCEPTED consumes
        // nothing further; only a release is possible from here.
        if (RideStateMachine.releasesSeats(booking.status, to)) {
            SeatAccounting.releaseSeats(firestore, booking.tripId, booking.seatsRequested)
        }

        val trip = findTrip(booking.tripId)
        val driver = trip?.let { t ->
            drivers.document(t.driverId).get().await().let { snap ->
                snap.data?.let { FirestoreMappers.driverFromMap(snap.id, it) }
            }
        }
        val passenger = findUser(booking.passengerId)

        if (to == RideState.COMPLETED && driver != null) {
            drivers.document(driver.id)
                .update(FirestoreSchema.Driver.TOTAL_TRIPS, driver.totalTrips + 1).await()
        }
        if (to == RideState.PICKED_UP && trip != null && trip.status == TripStatus.PUBLISHED) {
            setTripStatus(trip.id, TripStatus.IN_PROGRESS)
        }

        RideNotifications
            .forTransition(to, booking, driver?.userId, passenger?.name, reason)
            .forEach { emit(it) }

        return RepoResult.Ok(booking.copy(status = to))
    }

    override suspend fun detailFor(booking: BookingEntity): BookingDetail {
        val trip = findTrip(booking.tripId)
        val driver = trip?.let { t ->
            drivers.document(t.driverId).get().await().let { snap ->
                snap.data?.let { FirestoreMappers.driverFromMap(snap.id, it) }
            }
        }
        val user = driver?.let { findUser(it.userId) }
        val vehicle = trip?.let { findVehicle(it.driverId, it.vehicleId) }
        val passenger = findUser(booking.passengerId)

        return BookingDetail(
            booking = booking,
            trip = trip,
            driver = if (driver != null && user != null && vehicle != null) {
                summarise(driver, user, vehicle)
            } else null,
            route = if (trip != null) routeFor(trip.id) else emptyList(),
            passengerName = passenger?.name
        )
    }

    // ------------------------------------------------------------------
    // Payments
    // ------------------------------------------------------------------

    /**
     * Settles a ride. Cash completes immediately because the money changed hands in the
     * car; every gateway-backed method is recorded PENDING and left for a real
     * integration, so the app never claims a transaction succeeded with no gateway call.
     */
    override suspend fun payForBooking(
        bookingId: String,
        method: PaymentMethod
    ): RepoResult<PaymentEntity> {
        val booking = findBooking(bookingId)
            ?: return RepoResult.Failed("That ride no longer exists.", "রাইডটি আর নেই।")
        if (booking.status != RideState.COMPLETED) {
            return RepoResult.Failed(
                "Payment opens once the ride is complete.",
                "রাইড শেষ হলে পেমেন্ট করা যাবে।"
            )
        }
        val trip = findTrip(booking.tripId)
            ?: return RepoResult.Failed("Route not found.", "রুট পাওয়া যায়নি।")

        val total = Taka.ofPoisha(booking.totalPoisha)
        val fee = (total * PricingRules.DEFAULT.platformCommission).roundedToTaka()
        val settledNow = !method.requiresGateway

        val existing = payments.whereEqualTo(FirestoreSchema.Payment.BOOKING_ID, bookingId)
            .limit(1).get().await().documents.firstOrNull()

        val payment = PaymentEntity(
            id = existing?.id ?: newId(),
            bookingId = bookingId,
            driverId = trip.driverId,
            amountPoisha = total.poisha,
            platformFeePoisha = fee.poisha,
            driverEarningsPoisha = (total - fee).poisha,
            method = method,
            status = if (settledNow) PaymentStatus.COMPLETED else PaymentStatus.PENDING,
            transactionRef = if (settledNow) "CASH-${bookingId.take(8).uppercase()}" else null,
            paidAt = if (settledNow) System.currentTimeMillis() else null
        )
        payments.document(payment.id).set(FirestoreMappers.paymentToMap(payment)).await()
        return RepoResult.Ok(payment)
    }

    override suspend fun confirmGatewayPayment(
        bookingId: String,
        reference: String
    ): RepoResult<Unit> {
        val doc = payments.whereEqualTo(FirestoreSchema.Payment.BOOKING_ID, bookingId)
            .limit(1).get().await().documents.firstOrNull()
            ?: return RepoResult.Failed("No payment to confirm.", "নিশ্চিত করার মতো পেমেন্ট নেই।")

        payments.document(doc.id).update(
            mapOf(
                FirestoreSchema.Payment.STATUS to PaymentStatus.COMPLETED.name,
                FirestoreSchema.Payment.TRANSACTION_REF to reference,
                FirestoreSchema.Payment.PAID_AT to System.currentTimeMillis()
            )
        ).await()
        return RepoResult.Ok(Unit)
    }

    override fun observePayment(bookingId: String): Flow<PaymentEntity?> =
        payments.whereEqualTo(FirestoreSchema.Payment.BOOKING_ID, bookingId).limit(1)
            .asFlow().map { it.mapDocuments(FirestoreMappers::paymentFromMap).firstOrNull() }

    override fun observeRecentPayments(): Flow<List<PaymentEntity>> =
        payments.orderBy(FirestoreSchema.Payment.CREATED_AT, Query.Direction.DESCENDING).limit(50)
            .asFlow().map { it.mapDocuments(FirestoreMappers::paymentFromMap) }

    override suspend fun earningsFor(driverId: String): EarningsSummary {
        val all = payments.whereEqualTo(FirestoreSchema.Payment.DRIVER_ID, driverId).get().await()
            .let { it.mapDocuments(FirestoreMappers::paymentFromMap) }

        val completed = all.filter { it.status == PaymentStatus.COMPLETED }
        fun earnedSince(from: Long): Taka = Taka.ofPoisha(
            completed.filter { (it.paidAt ?: it.createdAt) >= from }
                .sumOf { it.driverEarningsPoisha }
        )

        val driver = drivers.document(driverId).get().await().let { snap ->
            snap.data?.let { FirestoreMappers.driverFromMap(snap.id, it) }
        }

        return EarningsSummary(
            today = earnedSince(startOfToday()),
            thisWeek = earnedSince(startOfWeek()),
            thisMonth = earnedSince(startOfMonth()),
            pending = Taka.ofPoisha(
                all.filter { it.status == PaymentStatus.PENDING }.sumOf { it.driverEarningsPoisha }
            ),
            completedTrips = completed.size,
            rating = driver?.rating,
            ratingCount = driver?.ratingCount ?: 0
        )
    }

    // ------------------------------------------------------------------
    // Ratings
    // ------------------------------------------------------------------

    private fun ratingsOf(bookingId: String) =
        rideRequests.document(bookingId).collection(FirestoreSchema.RATINGS)

    override suspend fun submitRating(
        bookingId: String,
        raterId: String,
        rateeId: String,
        stars: Int,
        comment: String?
    ): RepoResult<Unit> {
        if (stars !in 1..5) {
            return RepoResult.Failed("Pick between 1 and 5 stars.", "১ থেকে ৫ তারকা বেছে নিন।")
        }
        val already = ratingsOf(bookingId)
            .whereEqualTo(FirestoreSchema.Rating.RATER_ID, raterId).limit(1).get().await()
        if (!already.isEmpty) {
            return RepoResult.Failed("You already rated this ride.", "আপনি এই রাইডে রেটিং দিয়েছেন।")
        }

        val rating = RatingEntity(
            id = newId(), bookingId = bookingId, raterId = raterId,
            rateeId = rateeId, stars = stars, comment = comment?.takeIf { it.isNotBlank() }
        )
        ratingsOf(bookingId).document(rating.id).set(FirestoreMappers.ratingToMap(rating)).await()

        // Keep the driver's running average in sync when the person rated is a driver.
        findDriverForUser(rateeId)?.let { driver ->
            drivers.document(driver.id).update(
                mapOf(
                    FirestoreSchema.Driver.RATING_SUM to driver.ratingSum + stars,
                    FirestoreSchema.Driver.RATING_COUNT to driver.ratingCount + 1
                )
            ).await()
        }
        return RepoResult.Ok(Unit)
    }

    override fun ratingsFor(userId: String): Flow<List<RatingEntity>> =
        firestore.collectionGroup(FirestoreSchema.RATINGS)
            .whereEqualTo(FirestoreSchema.Rating.RATEE_ID, userId)
            .asFlow()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val bookingId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                    FirestoreMappers.ratingFromMap(doc.id, bookingId, data)
                }
            }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    private fun messagesOf(bookingId: String) =
        rideRequests.document(bookingId).collection(FirestoreSchema.MESSAGES)

    override fun messagesFor(bookingId: String): Flow<List<MessageEntity>> =
        messagesOf(bookingId).orderBy(FirestoreSchema.Message.SENT_AT)
            .asFlow()
            .map { snap -> snap.mapDocuments { id, data -> FirestoreMappers.messageFromMap(id, bookingId, data) } }

    override fun unreadMessages(bookingId: String, readerId: String): Flow<Int> =
        messagesOf(bookingId).asFlow().map { snap ->
            snap.mapDocuments { id, data -> FirestoreMappers.messageFromMap(id, bookingId, data) }
                .count { it.senderId != readerId && it.readAt == null }
        }

    override suspend fun markMessagesRead(bookingId: String, readerId: String) {
        val now = System.currentTimeMillis()
        val unread = messagesOf(bookingId).get().await().documents.filter { doc ->
            val data = doc.data ?: return@filter false
            data[FirestoreSchema.Message.SENDER_ID] != readerId &&
                data[FirestoreSchema.Message.READ_AT] == null
        }
        if (unread.isEmpty()) return

        // One batch rather than a write per message: a long unread thread would
        // otherwise fire dozens of round trips when the screen opens.
        val batch = firestore.batch()
        unread.forEach { batch.update(it.reference, FirestoreSchema.Message.READ_AT, now) }
        batch.commit().await()
    }

    override suspend fun sendMessage(
        bookingId: String,
        senderId: String,
        content: String
    ): RepoResult<Unit> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return RepoResult.Failed("Type a message first.", "আগে একটি বার্তা লিখুন।")
        }
        val message = MessageEntity(
            id = newId(), bookingId = bookingId, senderId = senderId, content = trimmed
        )
        messagesOf(bookingId).document(message.id)
            .set(FirestoreMappers.messageToMap(message)).await()
        return RepoResult.Ok(Unit)
    }

    // ------------------------------------------------------------------
    // Saved places and trusted contacts
    // ------------------------------------------------------------------

    private fun placesOf(userId: String) =
        users.document(userId).collection(FirestoreSchema.SAVED_PLACES)

    private fun contactsOf(userId: String) =
        users.document(userId).collection(FirestoreSchema.TRUSTED_CONTACTS)

    override fun savedPlacesFor(userId: String): Flow<List<SavedPlaceEntity>> =
        placesOf(userId).asFlow()
            .map { snap -> snap.mapDocuments { id, data -> FirestoreMappers.savedPlaceFromMap(id, userId, data) } }

    override suspend fun savePlace(userId: String, label: String, address: String, point: LatLng) {
        val place = SavedPlaceEntity(
            id = newId(), userId = userId, label = label,
            address = address, lat = point.lat, lng = point.lng
        )
        placesOf(userId).document(place.id).set(FirestoreMappers.savedPlaceToMap(place)).await()
    }

    /**
     * The interface identifies a place by its id alone, but the document lives under its
     * owner. A collection-group query finds it wherever it is, which keeps the interface
     * identical for both backends rather than leaking Firestore's shape into it.
     */
    override suspend fun deletePlace(id: String) {
        firestore.collectionGroup(FirestoreSchema.SAVED_PLACES)
            .whereEqualTo(FieldPath.documentId(), id).limit(1).get().await()
            .documents.firstOrNull()?.reference?.delete()?.await()
    }

    override fun trustedContactsFor(userId: String): Flow<List<TrustedContactEntity>> =
        contactsOf(userId).asFlow()
            .map { snap -> snap.mapDocuments { id, data -> FirestoreMappers.trustedContactFromMap(id, userId, data) } }

    override suspend fun addTrustedContact(userId: String, name: String, phone: String) {
        val contact = TrustedContactEntity(id = newId(), userId = userId, name = name, phone = phone)
        contactsOf(userId).document(contact.id)
            .set(FirestoreMappers.trustedContactToMap(contact)).await()
    }

    override suspend fun deleteTrustedContact(id: String) {
        firestore.collectionGroup(FirestoreSchema.TRUSTED_CONTACTS)
            .whereEqualTo(FieldPath.documentId(), id).limit(1).get().await()
            .documents.firstOrNull()?.reference?.delete()?.await()
    }

    override suspend fun trustedContactList(userId: String): List<TrustedContactEntity> =
        contactsOf(userId).get().await()
            .let { snap -> snap.mapDocuments { id, data -> FirestoreMappers.trustedContactFromMap(id, userId, data) } }

    // ------------------------------------------------------------------
    // Safety
    // ------------------------------------------------------------------

    override suspend fun raiseSos(userId: String, bookingId: String?, position: LatLng?) {
        val event = SafetyEventEntity(
            id = newId(), raisedByUserId = userId, bookingId = bookingId,
            kind = SafetyEventKind.SOS, lat = position?.lat, lng = position?.lng
        )
        safetyEvents.document(event.id).set(FirestoreMappers.safetyEventToMap(event)).await()

        // Everyone the user nominated hears about it, not just the platform.
        trustedContactList(userId).forEach { contact ->
            emit(
                PendingNotification(
                    userId = userId,
                    kind = NotificationKind.SAFETY,
                    titleEn = "SOS sent",
                    titleBn = "এসওএস পাঠানো হয়েছে",
                    bodyEn = "Your alert was shared with ${contact.name}.",
                    bodyBn = "আপনার সতর্কবার্তা ${contact.name}-কে জানানো হয়েছে।",
                    bookingId = bookingId
                )
            )
        }
    }

    override suspend fun reportUser(
        reporterId: String,
        againstUserId: String,
        bookingId: String?,
        details: String
    ) {
        val event = SafetyEventEntity(
            id = newId(), raisedByUserId = reporterId, againstUserId = againstUserId,
            bookingId = bookingId, kind = SafetyEventKind.REPORT, details = details
        )
        safetyEvents.document(event.id).set(FirestoreMappers.safetyEventToMap(event)).await()
    }

    override suspend fun recordTripShared(userId: String, bookingId: String?, contactCount: Int) {
        val event = SafetyEventEntity(
            id = newId(), raisedByUserId = userId, bookingId = bookingId,
            kind = SafetyEventKind.TRIP_SHARED,
            details = "Shared with $contactCount contact(s)"
        )
        safetyEvents.document(event.id).set(FirestoreMappers.safetyEventToMap(event)).await()
    }

    override fun safetyEvents(): Flow<List<SafetyEventEntity>> =
        safetyEvents.orderBy(FirestoreSchema.SafetyEvent.CREATED_AT, Query.Direction.DESCENDING)
            .asFlow().map { it.mapDocuments(FirestoreMappers::safetyEventFromMap) }

    override fun openSafetyCount(): Flow<Int> =
        safetyEvents.whereEqualTo(FirestoreSchema.SafetyEvent.RESOLVED, false)
            .asFlow().map { it.size() }

    override suspend fun resolveSafetyEvent(id: String) {
        safetyEvents.document(id).update(FirestoreSchema.SafetyEvent.RESOLVED, true).await()
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    override fun notificationsFor(userId: String): Flow<List<NotificationEntity>> =
        notifications.whereEqualTo(FirestoreSchema.Notification.USER_ID, userId)
            .orderBy(FirestoreSchema.Notification.CREATED_AT, Query.Direction.DESCENDING)
            .asFlow().map { it.mapDocuments(FirestoreMappers::notificationFromMap) }

    override fun unreadNotificationCount(userId: String): Flow<Int> =
        notifications.whereEqualTo(FirestoreSchema.Notification.USER_ID, userId)
            .whereEqualTo(FirestoreSchema.Notification.READ_AT, null)
            .asFlow().map { it.size() }

    override suspend fun markNotificationsRead(userId: String) {
        val now = System.currentTimeMillis()
        val unread = notifications
            .whereEqualTo(FirestoreSchema.Notification.USER_ID, userId)
            .whereEqualTo(FirestoreSchema.Notification.READ_AT, null)
            .get().await()
        if (unread.isEmpty) return

        val batch = firestore.batch()
        unread.documents.forEach {
            batch.update(it.reference, FirestoreSchema.Notification.READ_AT, now)
        }
        batch.commit().await()
    }

    /** Persists one [PendingNotification] built by the shared [RideNotifications]. */
    private suspend fun emit(n: PendingNotification) {
        val entity = NotificationEntity(
            id = newId(), userId = n.userId, kind = n.kind,
            titleEn = n.titleEn, titleBn = n.titleBn,
            bodyEn = n.bodyEn, bodyBn = n.bodyBn, bookingId = n.bookingId
        )
        notifications.document(entity.id)
            .set(FirestoreMappers.notificationToMap(entity)).await()
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    override suspend fun platformStats(
        users: List<UserEntity>,
        drivers: List<DriverProfileEntity>,
        revenuePoisha: Long,
        openSafety: Int
    ): PlatformStats {
        val allRoutes = routes.get().await()
        val allRequests = rideRequests.get().await()
            .let { it.mapDocuments(FirestoreMappers::rideRequestFromMap) }

        return PlatformStats(
            totalUsers = users.size,
            totalDrivers = drivers.size,
            verifiedDrivers = drivers.count { it.verified },
            totalTrips = allRoutes.size(),
            totalBookings = allRequests.size,
            completedRides = allRequests.count {
                it.status == RideState.COMPLETED || it.status == RideState.PAID
            },
            platformRevenue = Taka.ofPoisha(revenuePoisha),
            openSafetyEvents = openSafety
        )
    }

    override fun platformRevenue(): Flow<Long> =
        payments.whereEqualTo(FirestoreSchema.Payment.STATUS, PaymentStatus.COMPLETED.name)
            .asFlow()
            .map { snap ->
                snap.mapDocuments(FirestoreMappers::paymentFromMap).sumOf { it.platformFeePoisha }
            }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun summarise(
        driver: DriverProfileEntity,
        user: UserEntity,
        vehicle: VehicleEntity
    ) = DriverSummary(
        driverId = driver.id,
        userId = driver.userId,
        name = user.name,
        phone = user.phone,
        verified = driver.verified,
        rating = driver.rating,
        ratingCount = driver.ratingCount,
        totalTrips = driver.totalTrips,
        vehicleType = vehicle.type,
        vehiclePlate = vehicle.plateNumber,
        vehicleModel = vehicle.model,
        vehicleCapacity = vehicle.capacity
    )

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        /** Routes that can still take a passenger. */
        val ACTIVE_TRIP_STATUSES = listOf(TripStatus.PUBLISHED.name, TripStatus.IN_PROGRESS.name)

        /** Bookings that are still in play, for "my current ride". */
        val LIVE_BOOKING_STATUSES = listOf(
            RideState.REQUESTED.name,
            RideState.ACCEPTED.name,
            RideState.DRIVER_ARRIVING.name,
            RideState.PICKED_UP.name,
            RideState.COMPLETED.name
        )
    }
}
