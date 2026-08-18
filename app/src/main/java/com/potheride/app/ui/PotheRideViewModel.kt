package com.potheride.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.Actor
import com.potheride.app.core.ride.RideState
import com.potheride.app.core.ride.RideStateMachine
import com.potheride.app.core.validation.ValidationResult
import com.potheride.app.core.validation.Validators
import com.potheride.app.data.local.AppDatabase
import com.potheride.app.data.local.DemoSeeder
import com.potheride.app.data.local.DhakaPlaces
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.DriverProfileEntity
import com.potheride.app.data.local.entities.MessageEntity
import com.potheride.app.data.local.entities.NotificationEntity
import com.potheride.app.data.local.entities.SafetyEventEntity
import com.potheride.app.data.local.entities.SavedPlaceEntity
import com.potheride.app.data.local.entities.TripEntity
import com.potheride.app.data.local.entities.TripStatus
import com.potheride.app.data.local.entities.TrustedContactEntity
import com.potheride.app.data.local.entities.UserEntity
import com.potheride.app.data.model.BookingDetail
import com.potheride.app.data.model.EarningsSummary
import com.potheride.app.data.model.MatchedRide
import com.potheride.app.data.model.PlatformStats
import com.potheride.app.data.repository.RepoResult
import com.potheride.app.data.repository.DataSourceProvider
import com.potheride.app.data.repository.RideDataSource
import com.potheride.app.location.LocationProvider
import com.potheride.app.location.SimulatedDriveTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class AppMode { PASSENGER, DRIVER }

/** Whether the map is being fed by the device's GPS or the demo drive simulator. */
enum class TrackingSource { NONE, REAL_GPS, SIMULATED }

/**
 * A one-shot message for the UI to surface in a snackbar. Carried as a counter-stamped
 * value so the same text shown twice in a row still triggers a second snackbar.
 */
data class Toast(val message: String, val id: Long = System.nanoTime())

data class UiState(
    val booting: Boolean = true,
    val currentUser: UserEntity? = null,
    val driverProfile: DriverProfileEntity? = null,
    val driverDocuments: List<com.potheride.app.data.local.entities.DriverDocumentEntity> = emptyList(),
    val mode: AppMode = AppMode.PASSENGER,
    val language: AppLanguage = AppLanguage.ENGLISH,

    // Driver
    val activeTrip: TripEntity? = null,
    val activeRoute: List<LatLng> = emptyList(),
    val incomingRequests: List<BookingEntity> = emptyList(),
    val trackingSource: TrackingSource = TrackingSource.NONE,
    val driverPosition: LatLng? = null,
    val travelledKm: Double = 0.0,
    val earnings: EarningsSummary? = null,

    // Passenger
    val searchResults: List<MatchedRide> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val selectedMatch: MatchedRide? = null,
    val activeBooking: BookingDetail? = null,
    val bookingHistory: List<BookingDetail> = emptyList(),
    val savedPlaces: List<SavedPlaceEntity> = emptyList(),
    val preferredPayment: PaymentMethod = PaymentMethod.CASH,

    // Shared
    val messages: List<MessageEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val unreadNotifications: Int = 0,
    val trustedContacts: List<TrustedContactEntity> = emptyList(),

    // Admin
    val platformStats: PlatformStats? = null,
    val safetyEvents: List<SafetyEventEntity> = emptyList(),
    val allUsers: List<UserEntity> = emptyList(),
    val allDrivers: List<DriverProfileEntity> = emptyList(),
    val platformRevenuePoisha: Long = 0L,

    val toast: Toast? = null
) {
    val isLoggedIn: Boolean get() = currentUser != null
}

/**
 * Holds all screen state and owns every coroutine the UI starts.
 *
 * Collection jobs are tracked and cancelled before being replaced. That matters here
 * because a driver can publish several routes in one session, and a naive
 * `launch { flow.collect {} }` per publish leaves the earlier collectors alive,
 * fighting over the same state field.
 */
class PotheRideViewModel(app: Application) : AndroidViewModel(app) {

    // Firebase when it is configured, the local Room store otherwise — see
    // DataSourceProvider. The ViewModel deliberately cannot tell which it got.
    private val repo: RideDataSource = DataSourceProvider.get(app)
    private val locationProvider = LocationProvider(app)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var requestsJob: Job? = null
    private var tripJob: Job? = null
    private var bookingJob: Job? = null
    private var historyJob: Job? = null
    private var placesJob: Job? = null
    private var messagesJob: Job? = null
    private var notificationsJob: Job? = null
    private var contactsJob: Job? = null
    private var trackingJob: Job? = null
    private var adminJob: Job? = null

    init {
        viewModelScope.launch {
            DemoSeeder.seedIfEmpty(repo)
            update { it.copy(booting = false) }
        }
    }

    private inline fun update(block: (UiState) -> UiState) {
        _uiState.value = block(_uiState.value)
    }

    private fun toast(message: String) = update { it.copy(toast = Toast(message)) }

    fun consumeToast() = update { it.copy(toast = null) }

    /** Picks the message matching the active language from a failed repository call. */
    private fun message(failure: RepoResult.Failed): String =
        if (_uiState.value.language == AppLanguage.BANGLA) failure.messageBn else failure.messageEn

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    fun setLanguage(language: AppLanguage) {
        update { it.copy(language = language) }
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch { repo.setLanguage(user.id, language) }
    }

    fun toggleLanguage() = setLanguage(
        if (_uiState.value.language == AppLanguage.ENGLISH) AppLanguage.BANGLA else AppLanguage.ENGLISH
    )

    fun setMode(mode: AppMode) = update { it.copy(mode = mode) }

    fun validatePhone(raw: String): ValidationResult = Validators.validatePhone(raw)
    fun validateName(raw: String): ValidationResult = Validators.validateName(raw)
    fun validateOtp(raw: String): ValidationResult = Validators.validateOtp(raw)

    /**
     * Signs a user in.
     *
     * The OTP is *not* verified against anything: this build has no SMS provider, and
     * every screen that mentions the code says so. Wiring a real provider means
     * replacing this method's body and nothing else.
     */
    fun verifyAndSignIn(rawPhone: String, name: String) {
        val phone = Validators.normalizeBdPhone(rawPhone) ?: return
        viewModelScope.launch {
            val user = repo.findOrCreateUser(phone, name.trim(), _uiState.value.language)
            val driver = repo.findDriverForUser(user.id)
            update {
                it.copy(
                    currentUser = user,
                    driverProfile = driver,
                    language = AppLanguage.fromCode(user.language)
                )
            }
            observeForUser(user.id)
            driver?.let { observeForDriver(it.id); observeDriverDocuments(it.id) }
        }
    }

    fun signOut() {
        listOf(
            requestsJob, tripJob, bookingJob, historyJob, placesJob,
            messagesJob, notificationsJob, contactsJob, trackingJob, adminJob
        ).forEach { it?.cancel() }
        _uiState.value = UiState(booting = false, language = _uiState.value.language)
    }

    private fun observeForUser(userId: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repo.bookingsForPassenger(userId).collect { bookings ->
                val details = bookings.map { repo.detailFor(it) }
                update { state ->
                    state.copy(
                        bookingHistory = details,
                        activeBooking = details.firstOrNull { !it.booking.status.isTerminal }
                            ?: state.activeBooking?.let { current ->
                                details.firstOrNull { it.booking.id == current.booking.id }
                            }
                    )
                }
            }
        }
        placesJob?.cancel()
        placesJob = viewModelScope.launch {
            repo.savedPlacesFor(userId).collect { places -> update { it.copy(savedPlaces = places) } }
        }
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            repo.notificationsFor(userId).collect { list ->
                update { it.copy(notifications = list, unreadNotifications = list.count { n -> n.readAt == null }) }
            }
        }
        contactsJob?.cancel()
        contactsJob = viewModelScope.launch {
            repo.trustedContactsFor(userId).collect { list -> update { it.copy(trustedContacts = list) } }
        }
    }

    private fun observeForDriver(driverId: String) {
        tripJob?.cancel()
        tripJob = viewModelScope.launch {
            repo.activeTripForDriver(driverId).collect { trip ->
                update { it.copy(activeTrip = trip) }
                if (trip != null) {
                    update { it.copy(activeRoute = repo.routeFor(trip.id), driverPosition = trip.livePoint) }
                    watchRequests(trip.id)
                } else {
                    requestsJob?.cancel()
                    update { it.copy(incomingRequests = emptyList(), activeRoute = emptyList()) }
                }
            }
        }
        refreshEarnings()
    }

    private fun watchRequests(tripId: String) {
        requestsJob?.cancel()
        requestsJob = viewModelScope.launch {
            repo.requestsForTrip(tripId).collect { list -> update { it.copy(incomingRequests = list) } }
        }
    }

    // ------------------------------------------------------------------
    // Driver
    // ------------------------------------------------------------------

    fun becomeDriver(licenseNumber: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            val profile = repo.becomeDriver(user.id, licenseNumber.trim().ifBlank { "PENDING" })
            update { it.copy(driverProfile = profile) }
            observeForDriver(profile.id)
            observeDriverDocuments(profile.id)
        }
    }

    private var documentsJob: Job? = null

    /**
     * Verification documents stay on the local Room store even when the ride data itself
     * is on Firebase, because the Storage upload that would carry the files to
     * `FirestoreSchema.Storage.driverDocument` is not wired up yet — see
     * `docs/upgrade/deps-verification.md`. The gate in [VerificationRules] is backend-
     * agnostic on purpose so swapping this for a Firestore-backed store later changes
     * only where the rows come from, never the rule that reads them.
     */
    private fun observeDriverDocuments(driverId: String) {
        documentsJob?.cancel()
        documentsJob = viewModelScope.launch {
            AppDatabase.getInstance(getApplication()).driverDocumentDao()
                .forDriver(driverId)
                .collect { docs -> update { it.copy(driverDocuments = docs) } }
        }
    }

    /**
     * Records an uploaded document. [localPath] is wherever the picked file currently
     * lives (a content URI's cached copy); see the note on [observeDriverDocuments] about
     * this becoming a real Storage upload once that plumbing exists.
     */
    fun uploadDriverDocument(kind: com.potheride.app.core.verification.DriverDocumentKind, localPath: String) {
        val driverId = _uiState.value.driverProfile?.id ?: return
        viewModelScope.launch {
            AppDatabase.getInstance(getApplication()).driverDocumentDao().upsert(
                com.potheride.app.data.local.entities.DriverDocumentEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    driverId = driverId,
                    kind = kind,
                    storagePath = localPath
                )
            )
        }
    }

    /** The gate a screen checks before letting a driver publish — see `VerificationRules`. */
    fun verificationState(): com.potheride.app.core.verification.VerificationState =
        com.potheride.app.core.verification.VerificationRules.state(
            _uiState.value.driverDocuments.map { it.toDomain() }
        )

    fun publishBlock(): com.potheride.app.core.verification.PublishBlock? =
        com.potheride.app.core.verification.VerificationRules.publishBlock(
            _uiState.value.driverDocuments.map { it.toDomain() }
        )

    fun validatePlate(raw: String): ValidationResult = Validators.validatePlate(raw)

    /**
     * Publishes a route, creating the driver profile first if this is the user's
     * first trip. Both happen in one coroutine so the profile is guaranteed to exist
     * before the trip is written — calling `becomeDriver` from the screen and then
     * immediately calling this would race, and the trip would be dropped.
     */
    fun publishTrip(
        licenseNumber: String = "",
        startAddress: String,
        start: LatLng,
        endAddress: String,
        end: LatLng,
        minutesFromNow: Int,
        seats: Int,
        detourKm: Double,
        vehicleType: VehicleClass,
        plate: String,
        onPublished: () -> Unit
    ) {
        val user = _uiState.value.currentUser ?: return
        // Enforced here, not only by graying out the button: the UI is the one layer an
        // attacker does not have to go through, and this is where an unverified driver
        // would otherwise end up carrying a passenger.
        if (!com.potheride.app.core.verification.VerificationRules.canPublishRoute(
                _uiState.value.driverDocuments.map { it.toDomain() }
            )
        ) {
            toast("Complete driver verification before publishing a route.")
            return
        }
        viewModelScope.launch {
            val driver = _uiState.value.driverProfile
                ?: repo.becomeDriver(user.id, licenseNumber.trim().ifBlank { "PENDING" })
                    .also { profile ->
                        update { it.copy(driverProfile = profile) }
                        observeForDriver(profile.id)
                    }
            val vehicle = repo.registerVehicle(
                driverId = driver.id,
                type = vehicleType,
                plate = plate,
                capacity = maxOf(seats, vehicleType.defaultSeats)
            )
            // Intermediate waypoints are interpolated along the straight line for now.
            // With a Directions API wired up, this is the one call that changes: feed
            // the returned polyline in here and every downstream calculation — matching,
            // fares, ETAs, the map — starts working off real road geometry.
            val waypoints = (1..3).map { i -> GeoUtils.interpolate(start, end, i / 4.0) }

            val trip = repo.publishTrip(
                driverId = driver.id,
                vehicleId = vehicle.id,
                startAddress = startAddress,
                start = start,
                endAddress = endAddress,
                end = end,
                departureTime = System.currentTimeMillis() + minutesFromNow * 60_000L,
                seats = seats,
                detourKm = detourKm,
                waypoints = waypoints
            )
            update {
                it.copy(
                    activeTrip = trip,
                    activeRoute = repo.routeFor(trip.id),
                    driverPosition = trip.startPoint,
                    travelledKm = 0.0
                )
            }
            watchRequests(trip.id)
            onPublished()
        }
    }

    fun respondToRequest(bookingId: String, accept: Boolean) {
        viewModelScope.launch {
            val result = repo.transition(
                bookingId,
                if (accept) RideState.ACCEPTED else RideState.DECLINED,
                Actor.DRIVER
            )
            if (result is RepoResult.Failed) toast(message(result))
        }
    }

    /** Driver-side advance along the happy path (arriving -> picked up -> completed). */
    fun driverAdvance(bookingId: String, from: RideState) {
        val next = RideStateMachine.nextHappyPathState(from) ?: return
        if (next == RideState.PAID) return // the passenger settles up, not the driver
        viewModelScope.launch {
            val result = repo.transition(bookingId, next, Actor.DRIVER)
            if (result is RepoResult.Failed) toast(message(result))
        }
    }

    fun finishTrip() {
        val trip = _uiState.value.activeTrip ?: return
        viewModelScope.launch {
            repo.setTripStatus(trip.id, TripStatus.COMPLETED)
            stopTracking()
            refreshEarnings()
        }
    }

    fun refreshEarnings() {
        val driver = _uiState.value.driverProfile ?: return
        viewModelScope.launch {
            update { it.copy(earnings = repo.earningsFor(driver.id)) }
        }
    }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    /** Streams real device GPS into the active trip. */
    fun startRealTracking() {
        val trip = _uiState.value.activeTrip ?: return
        if (!locationProvider.hasPermission()) return
        trackingJob?.cancel()
        update { it.copy(trackingSource = TrackingSource.REAL_GPS) }
        trackingJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { position ->
                repo.recordLocation(trip.id, position)
                update { it.copy(driverPosition = position) }
            }
        }
    }

    /**
     * Drives the published route in simulation. Explicit and clearly labelled in the
     * UI — a stock emulator emits no GPS fix, and without this there is no way to see
     * live tracking work at all.
     *
     * Gated on [BuildConfig.DEBUG] so this is unreachable from a release build. Real
     * tracking is [com.potheride.app.location.LocationForegroundService]; this stays a
     * demo aid for development and screenshots, not a path a production driver can ever
     * take even if a screen wired the button up by mistake.
     */
    fun startSimulatedTracking() {
        if (!com.potheride.app.BuildConfig.DEBUG) return
        val trip = _uiState.value.activeTrip ?: return
        val route = _uiState.value.activeRoute
        if (route.size < 2) return
        trackingJob?.cancel()
        update { it.copy(trackingSource = TrackingSource.SIMULATED) }
        trackingJob = viewModelScope.launch {
            // Through the interface, not the DAO: reaching into Room directly here
            // would work only on the local backend and silently misprice the simulated
            // drive on Firebase.
            val vehicle = repo.vehiclesFor(trip.driverId).first()
                .firstOrNull { it.id == trip.vehicleId }
            val tracker = SimulatedDriveTracker(
                route = route,
                vehicleClass = vehicle?.type ?: VehicleClass.CAR,
                hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            )
            tracker.drive(_uiState.value.travelledKm).collect { (position, travelled) ->
                repo.recordLocation(trip.id, position)
                update { it.copy(driverPosition = position, travelledKm = travelled) }
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        update { it.copy(trackingSource = TrackingSource.NONE) }
    }

    // ------------------------------------------------------------------
    // Passenger
    // ------------------------------------------------------------------

    fun placeSuggestions(query: String): List<Pair<String, LatLng>> = DhakaPlaces.search(query)

    fun searchMatches(
        pickup: LatLng,
        drop: LatLng,
        seats: Int,
        withinMinutes: Int
    ) {
        viewModelScope.launch {
            update { it.copy(searching = true, searched = false) }
            val now = System.currentTimeMillis()
            val results = repo.searchMatches(
                pickup = pickup,
                drop = drop,
                seatsNeeded = seats,
                earliestDeparture = now - 15 * 60_000L,
                latestDeparture = now + withinMinutes * 60_000L,
                excludeDriverId = _uiState.value.driverProfile?.id
            )
            update { it.copy(searchResults = results, searching = false, searched = true) }
        }
    }

    fun selectMatch(match: MatchedRide) = update { it.copy(selectedMatch = match) }

    fun requestSeat(
        pickupAddress: String,
        dropAddress: String,
        seats: Int,
        method: PaymentMethod,
        onBooked: () -> Unit
    ) {
        val user = _uiState.value.currentUser ?: return
        val match = _uiState.value.selectedMatch ?: return
        viewModelScope.launch {
            when (val result = repo.requestSeat(match, user.id, pickupAddress, dropAddress, seats)) {
                is RepoResult.Ok -> {
                    update { it.copy(activeBooking = repo.detailFor(result.value), preferredPayment = method) }
                    onBooked()
                }
                is RepoResult.Failed -> toast(message(result))
            }
        }
    }

    fun cancelRide(bookingId: String, reason: String? = null) {
        viewModelScope.launch {
            val result = repo.transition(bookingId, RideState.CANCELLED, Actor.PASSENGER, reason)
            if (result is RepoResult.Failed) toast(message(result))
        }
    }

    fun pay(bookingId: String, method: PaymentMethod) {
        viewModelScope.launch {
            when (val result = repo.payForBooking(bookingId, method)) {
                is RepoResult.Ok -> if (method.requiresGateway) {
                    toast(
                        if (_uiState.value.language == AppLanguage.BANGLA)
                            "${method.displayBn} পেমেন্ট অপেক্ষমাণ হিসেবে রাখা হয়েছে।"
                        else "${method.displayEn} payment recorded as pending."
                    )
                }
                is RepoResult.Failed -> toast(message(result))
            }
        }
    }

    fun submitRating(bookingId: String, rateeId: String, stars: Int, comment: String?, onDone: () -> Unit) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            when (val result = repo.submitRating(bookingId, user.id, rateeId, stars, comment)) {
                is RepoResult.Ok -> onDone()
                is RepoResult.Failed -> toast(message(result))
            }
        }
    }

    fun savePlace(label: String, address: String, point: LatLng) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch { repo.savePlace(user.id, label, address, point) }
    }

    fun deletePlace(id: String) {
        viewModelScope.launch { repo.deletePlace(id) }
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    fun openChat(bookingId: String) {
        val user = _uiState.value.currentUser ?: return
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repo.markMessagesRead(bookingId, user.id)
            repo.messagesFor(bookingId).collect { list -> update { it.copy(messages = list) } }
        }
    }

    fun closeChat() {
        messagesJob?.cancel()
        update { it.copy(messages = emptyList()) }
    }

    fun sendMessage(bookingId: String, content: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            val result = repo.sendMessage(bookingId, user.id, content)
            if (result is RepoResult.Failed) toast(message(result))
        }
    }

    // ------------------------------------------------------------------
    // Safety
    // ------------------------------------------------------------------

    fun raiseSos(bookingId: String?) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            repo.raiseSos(user.id, bookingId, _uiState.value.driverPosition)
        }
    }

    fun shareTrip(bookingId: String?, onShared: (Int) -> Unit) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            val contacts = repo.trustedContactList(user.id)
            repo.recordTripShared(user.id, bookingId, contacts.size)
            onShared(contacts.size)
        }
    }

    fun addTrustedContact(name: String, phone: String) {
        val user = _uiState.value.currentUser ?: return
        val normalised = Validators.normalizeBdPhone(phone) ?: phone
        viewModelScope.launch { repo.addTrustedContact(user.id, name.trim(), normalised) }
    }

    fun deleteTrustedContact(id: String) {
        viewModelScope.launch { repo.deleteTrustedContact(id) }
    }

    fun reportUser(againstUserId: String, bookingId: String?, details: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch { repo.reportUser(user.id, againstUserId, bookingId, details) }
    }

    fun markNotificationsRead() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch { repo.markNotificationsRead(user.id) }
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    fun openAdmin() {
        adminJob?.cancel()
        adminJob = viewModelScope.launch {
            repo.safetyEvents().collect { events -> update { it.copy(safetyEvents = events) } }
        }
        viewModelScope.launch {
            repo.observeAllUsers().collect { users ->
                update { it.copy(allUsers = users) }
                refreshPlatformStats()
            }
        }
        viewModelScope.launch {
            repo.observeAllDrivers().collect { drivers ->
                update { it.copy(allDrivers = drivers) }
                refreshPlatformStats()
            }
        }
        viewModelScope.launch {
            repo.platformRevenue().collect { revenue ->
                update { it.copy(platformRevenuePoisha = revenue) }
                refreshPlatformStats()
            }
        }
    }

    private fun refreshPlatformStats() {
        viewModelScope.launch {
            val state = _uiState.value
            update {
                it.copy(
                    platformStats = repo.platformStats(
                        users = state.allUsers,
                        drivers = state.allDrivers,
                        revenuePoisha = state.platformRevenuePoisha,
                        openSafety = state.safetyEvents.count { e -> !e.resolved }
                    )
                )
            }
        }
    }

    fun setDriverVerified(driverId: String, verified: Boolean) {
        viewModelScope.launch { repo.setDriverVerified(driverId, verified) }
    }

    /**
     * Admin action: approves or rejects one uploaded document. This is what actually
     * unlocks [publishTrip] — `driverProfile.verified` is a display cache, never the
     * source the gate reads from; see [VerificationRules.canPublishRoute].
     */
    fun reviewDriverDocument(
        documentId: String,
        approve: Boolean,
        rejectionReason: String? = null
    ) {
        viewModelScope.launch {
            AppDatabase.getInstance(getApplication()).driverDocumentDao().review(
                id = documentId,
                status = (if (approve) com.potheride.app.core.verification.DriverDocumentStatus.APPROVED
                          else com.potheride.app.core.verification.DriverDocumentStatus.REJECTED).name,
                reason = if (approve) null else rejectionReason,
                reviewedAt = System.currentTimeMillis()
            )
        }
    }

    fun resolveSafetyEvent(id: String) {
        viewModelScope.launch { repo.resolveSafetyEvent(id) }
    }

    fun setUserBlocked(userId: String, blocked: Boolean) {
        viewModelScope.launch { repo.setUserBlocked(userId, blocked) }
    }
}
