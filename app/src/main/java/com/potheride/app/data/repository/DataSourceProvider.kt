package com.potheride.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.potheride.app.data.local.AppDatabase
import com.potheride.app.data.remote.FirebaseRideDataSource

/** Which backend is answering. Surfaced so the UI can say so rather than guess. */
enum class Backend { FIREBASE, LOCAL }

/**
 * Chooses the data source for this install.
 *
 * Firebase when it is configured and initialised, the local Room store otherwise. The
 * fallback is not a convenience — it is what keeps the app runnable for a contributor
 * who has cloned the repo without a Firebase project of their own, and what stops a
 * misconfigured build from being a blank screen instead of a working single-device demo.
 *
 * The choice is made once and cached. Re-deciding per call could hand two different
 * stores to two halves of the same screen.
 */
object DataSourceProvider {

    private const val TAG = "DataSourceProvider"

    @Volatile
    private var instance: RideDataSource? = null

    @Volatile
    var activeBackend: Backend = Backend.LOCAL
        private set

    fun get(context: Context): RideDataSource =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    private fun create(context: Context): RideDataSource {
        if (FirebaseAvailability.isAvailable(context)) {
            // Constructing FirebaseFirestore can still throw if the project is reachable
            // but misconfigured, so the fallback has to wrap this too — an exception here
            // would otherwise crash the app on its first frame.
            runCatching { FirebaseRideDataSource(FirebaseFirestore.getInstance()) }
                .onSuccess {
                    activeBackend = Backend.FIREBASE
                    Log.i(TAG, "Using the Firebase backend.")
                    return it
                }
                .onFailure { Log.w(TAG, "Firestore unavailable; falling back to the local store.", it) }
        }
        activeBackend = Backend.LOCAL
        Log.i(TAG, "Using the local Room store.")
        return RoomRideDataSource(AppDatabase.getInstance(context))
    }

    /** Test seam: drop the cached choice. */
    fun reset() {
        synchronized(this) {
            instance = null
            activeBackend = Backend.LOCAL
            FirebaseAvailability.reset()
        }
    }
}
