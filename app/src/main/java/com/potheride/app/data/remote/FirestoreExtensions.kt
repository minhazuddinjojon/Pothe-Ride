package com.potheride.app.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@PublishedApi
internal const val TAG = "Firestore"

/**
 * Firestore snapshot listeners as [Flow]s.
 *
 * The Room backend hands the UI a `Flow` per query and the UI re-renders when it emits.
 * These adapters give Firestore the same shape, which is what lets both backends sit
 * behind one interface without the screens knowing which is in use.
 *
 * `callbackFlow` is the right builder here specifically because it registers the
 * listener on collection and removes it in [awaitClose] on cancellation — a snapshot
 * listener that outlives its subscriber is a live network subscription that keeps
 * billing reads for a screen nobody is looking at.
 */

/** Emits every time the document changes. Emits `null` when it does not exist. */
fun DocumentReference.asFlow(): Flow<DocumentSnapshot?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            // Closing the flow rather than swallowing the error means a permission
            // failure surfaces to the caller instead of looking like an empty result.
            Log.w(TAG, "Listener failed for ${this@asFlow.path}", error)
            close(error)
            return@addSnapshotListener
        }
        trySend(snapshot?.takeIf { it.exists() })
    }
    awaitClose { registration.remove() }
}

/** Emits the full result set every time any document in it changes. */
fun Query.asFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            Log.w(TAG, "Query listener failed", error)
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/**
 * Maps each document in a query snapshot, skipping any that fail to convert.
 *
 * Skipping rather than throwing is deliberate: one malformed document in a collection
 * must not blank the whole list. The passenger with 19 good bookings and one bad one
 * should see 19, not an error screen.
 */
inline fun <T> QuerySnapshot.mapDocuments(transform: (String, Map<String, Any?>) -> T): List<T> =
    documents.mapNotNull { doc ->
        val data = doc.data ?: return@mapNotNull null
        try {
            transform(doc.id, data)
        } catch (e: Exception) {
            Log.w(TAG, "Skipping unreadable document ${doc.id}", e)
            null
        }
    }

/** Maps a single document, or `null` when it is absent or unreadable. */
inline fun <T> DocumentSnapshot?.mapDocument(transform: (String, Map<String, Any?>) -> T): T? {
    val data = this?.data ?: return null
    return try {
        transform(id, data)
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable document $id", e)
        null
    }
}
