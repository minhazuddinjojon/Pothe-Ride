package com.potheride.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.potheride.app.BuildConfig

/**
 * Decides whether this build can talk to Firebase.
 *
 * Two conditions, and both matter:
 *  - [BuildConfig.HAS_FIREBASE] records whether `google-services.json` was present when
 *    the APK was built. Without it the google-services plugin never ran, so no project
 *    identifiers were baked in.
 *  - [FirebaseApp.initializeApp] is still checked at runtime, because the file being
 *    present at build time does not guarantee the SDK initialised — a malformed config,
 *    or a package name that is not registered in the Firebase project, both fail here.
 *
 * The second check is the one that earns its keep. A config file listing only
 * `com.potheride.app` while the running build reports `com.potheride.app.debug` produces
 * exactly this failure, and it is far better to fall back to the local store than to let
 * every call throw somewhere deep in a coroutine.
 */
object FirebaseAvailability {

    private const val TAG = "FirebaseAvailability"

    @Volatile
    private var cached: Boolean? = null

    /** True when Firebase initialised and may be used as the backing store. */
    fun isAvailable(context: Context): Boolean =
        cached ?: synchronized(this) {
            cached ?: resolve(context).also { cached = it }
        }

    private fun resolve(context: Context): Boolean {
        if (!BuildConfig.HAS_FIREBASE) {
            Log.i(TAG, "Built without google-services.json — using the local store.")
            return false
        }
        return try {
            val app = FirebaseApp.initializeApp(context.applicationContext)
            if (app == null) {
                Log.w(TAG, "FirebaseApp did not initialise — using the local store.")
                false
            } else {
                true
            }
        } catch (e: IllegalStateException) {
            // Thrown when the config is present but unusable for this build variant.
            Log.w(TAG, "Firebase unavailable, falling back to the local store.", e)
            false
        }
    }

    /** Test seam: forget the cached decision. */
    fun reset() {
        synchronized(this) { cached = null }
    }
}
