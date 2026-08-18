package com.potheride.app.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.potheride.app.data.local.entities.TripStatus
import kotlinx.coroutines.tasks.await

/** Why a seat claim was refused, so the caller can produce the right bilingual message. */
enum class SeatClaimFailure {
    ROUTE_GONE,
    ROUTE_ENDED,
    NOT_ENOUGH_SEATS
}

sealed interface SeatClaimResult {
    data class Claimed(val remainingSeats: Int) : SeatClaimResult
    data class Refused(val reason: SeatClaimFailure, val availableSeats: Int) : SeatClaimResult
}

/**
 * Seat counting for the Firestore backend.
 *
 * This is the one place where moving off Room introduces a genuinely new failure mode,
 * and it is worth being explicit about why.
 *
 * Room is a single-writer store on one device: a read-then-write of `availableSeats`
 * could not interleave with anyone else's, so the Room implementation's
 * `decrementSeats` is safe as a plain update. Firestore is shared across every device
 * using the app. Two drivers accepting the last seat on the same route within the same
 * second is not a hypothetical — it is the normal case at a busy pickup point — and a
 * naive read-then-write silently oversells the vehicle.
 *
 * A Firestore transaction is the fix. It re-reads the route inside the transaction and
 * retries automatically if the document changed underneath, so the seat count can never
 * go negative no matter how many clients race.
 */
object SeatAccounting {

    /**
     * Atomically claims [seats] on a route, or refuses with the reason.
     *
     * The availability checks live *inside* the transaction on purpose. Checking before
     * opening it would reintroduce exactly the race the transaction exists to close.
     */
    suspend fun claimSeats(
        firestore: FirebaseFirestore,
        tripId: String,
        seats: Int
    ): SeatClaimResult {
        val routeRef = firestore.collection(FirestoreSchema.ROUTES).document(tripId)

        return firestore.runTransaction { txn: Transaction ->
            val snapshot = txn.get(routeRef)
            if (!snapshot.exists()) {
                return@runTransaction SeatClaimResult.Refused(SeatClaimFailure.ROUTE_GONE, 0)
            }

            val data = snapshot.data ?: emptyMap<String, Any?>()
            val trip = FirestoreMappers.routeFromMap(tripId, data)

            if (trip.status != TripStatus.PUBLISHED && trip.status != TripStatus.IN_PROGRESS) {
                return@runTransaction SeatClaimResult.Refused(
                    SeatClaimFailure.ROUTE_ENDED, trip.availableSeats
                )
            }
            if (trip.availableSeats < seats) {
                return@runTransaction SeatClaimResult.Refused(
                    SeatClaimFailure.NOT_ENOUGH_SEATS, trip.availableSeats
                )
            }

            val remaining = trip.availableSeats - seats
            txn.update(routeRef, FirestoreSchema.Route.AVAILABLE_SEATS, remaining)
            SeatClaimResult.Claimed(remaining)
        }.await()
    }

    /**
     * Returns [seats] to a route when a booking is declined or cancelled.
     *
     * Clamped to the route's own total. Without the clamp, a duplicate release — a
     * retried cancel, or two clients cancelling the same booking — would inflate the
     * vehicle beyond the seats it physically has.
     */
    suspend fun releaseSeats(
        firestore: FirebaseFirestore,
        tripId: String,
        seats: Int
    ) {
        val routeRef = firestore.collection(FirestoreSchema.ROUTES).document(tripId)
        firestore.runTransaction { txn: Transaction ->
            val snapshot = txn.get(routeRef)
            if (!snapshot.exists()) return@runTransaction null

            val data = snapshot.data ?: emptyMap<String, Any?>()
            val trip = FirestoreMappers.routeFromMap(tripId, data)
            val restored = (trip.availableSeats + seats).coerceAtMost(trip.totalSeats)

            txn.update(routeRef, FirestoreSchema.Route.AVAILABLE_SEATS, restored)
            null
        }.await()
    }
}
