package com.potheride.app

import android.app.Application
import com.potheride.app.map.configureOsmdroid

/**
 * Application entry point. Database creation and demo seeding are deliberately left
 * to the ViewModel's coroutine scope rather than done here — Application.onCreate
 * runs on the main thread, and opening Room plus writing several trips there would
 * show up as a visible launch stall.
 */
class PotheRideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must happen before the first OsmRouteMap composable is entered, or tile
        // requests carry OSMDroid's default user agent and are silently blocked — see
        // configureOsmdroid's KDoc.
        configureOsmdroid(this)
    }
}
