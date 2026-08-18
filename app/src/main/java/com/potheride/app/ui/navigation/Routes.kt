package com.potheride.app.ui.navigation

/** Every destination in the app, in one place. */
object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val SEARCH = "search"
    const val RESULTS = "results"
    const val ROUTE_PREVIEW = "route_preview"
    const val BOOKING_CONFIRM = "booking_confirm"
    const val RIDE_STATUS = "ride_status"
    const val CHAT = "chat"
    const val RATE = "rate"
    const val DRIVER_CREATE = "driver_create"
    const val DRIVER_LIVE = "driver_live"
    const val EARNINGS = "earnings"
    const val ACTIVITY = "activity"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val ADMIN = "admin"

    // Registration and verification — wireframe boards 02 and 03.
    const val CREATE_PROFILE = "create_profile"
    const val DRIVER_REGISTRATION = "driver_registration"
    const val VERIFICATION_STATUS = "verification_status"
    const val FACE_VERIFICATION = "face_verification"

    // Payment — wireframe board 06B.
    const val PAYMENT_METHODS = "payment_methods"

    /** The conversation list, reached from the bottom bar. */
    const val CHATS = "chats"

    /**
     * Destinations that show the bottom navigation bar.
     *
     * Search is deliberately absent: wireframe board 01B shows the four tabs as
     * Home · Activity · Chat · Profile, with search reached through the black hero card
     * on Home rather than a tab of its own. Leaving SEARCH here would render a bar with
     * five items on a screen the wireframe gives four.
     */
    val bottomBarRoutes = setOf(HOME, ACTIVITY, CHATS, PROFILE)
}
