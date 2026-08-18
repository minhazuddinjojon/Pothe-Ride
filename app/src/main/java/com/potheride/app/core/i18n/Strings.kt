package com.potheride.app.core.i18n

import com.potheride.app.core.format.AppLanguage

/**
 * Every user-visible string in the app.
 *
 * Modelled as an interface with one implementation per language rather than as
 * Android string resources, for two reasons. First, a missing Bengali translation
 * becomes a compile error instead of a screen that silently falls back to English.
 * Second, switching language mid-session updates the UI immediately, where the
 * resource-based approach needs an activity recreation that loses the user's place.
 *
 * `values-bn/strings.xml` is still present for the launcher label and anything the
 * system renders outside the app.
 */
interface Strings {
    val appName: String
    val tagline: String

    // Auth
    val phoneLabel: String
    val phoneHint: String
    val nameLabel: String
    val nameHint: String
    val sendOtp: String
    val otpLabel: String
    val otpHelper: String
    val verifyContinue: String
    val changeNumber: String
    val demoOtpNotice: String
    val resendCode: String

    // Navigation and shell
    val navHome: String
    val navSearch: String
    val navActivity: String
    val navProfile: String

    /** Board 01B: the bottom bar's fourth tab is Chat, not Search. */
    val navChat: String

    /** Board 01B hero card. */
    val searchRide: String
    val searchRideHint: String
    val publishRouteHint: String
    val recentTrips: String

    /** Chat tab empty state. */
    val noMessagesTitle: String
    val noMessagesBody: String
    val back: String
    val cancel: String
    val confirm: String
    val save: String
    val close: String
    val retry: String
    val done: String

    // Home
    val greeting: String
    val passengerMode: String
    val driverMode: String
    val findRideTitle: String
    val findRideBody: String
    val shareRouteTitle: String
    val shareRouteBody: String
    val searchRides: String
    val publishRoute: String
    val myDashboard: String
    val earningsDashboard: String
    val savedPlaces: String
    val addSavedPlace: String
    val home: String
    val work: String
    val notifications: String
    val noNotifications: String

    // Driver: create trip
    val createTripTitle: String
    val startLocation: String
    val destination: String
    val vehicleType: String
    val plateNumber: String
    val seatsAvailable: String
    val acceptableDetour: String
    val departureTime: String
    val departingIn: String
    val routePreviewHint: String
    val publishing: String
    val routePublished: String

    // Driver: live
    val liveRouteTitle: String
    val incomingRequests: String
    val noRequestsYet: String
    val accept: String
    val decline: String
    val startTrip: String
    val simulateDriving: String
    val stopSimulation: String
    val usingRealGps: String
    val usingSimulatedGps: String
    val locationPermissionNeeded: String
    val grantPermission: String
    val progressAlongRoute: String
    val seatsLeft: String
    val onYourRoute: String

    // Passenger: search
    val searchTitle: String
    val pickupPoint: String
    val dropOffPoint: String
    val seatsNeeded: String
    val leavingWithin: String
    val searchMatchingRides: String
    val searching: String
    val matchesTitle: String
    val noMatchesTitle: String
    val noMatchesBody: String
    val perSeat: String
    val viewRoute: String
    val routeOverlap: String
    val extraDetour: String
    val fareEstimate: String
    val pickupEta: String
    val dropoffEta: String
    val verifiedDriver: String
    val newDriver: String
    val tripsCompleted: String

    // Booking
    val routePreviewTitle: String
    val requestSeat: String
    val confirmRequest: String
    val payWith: String
    val requestSent: String
    val requestSentBody: String
    val trackRide: String
    val fareBreakdown: String
    val baseFare: String
    val distanceFare: String
    val timeFare: String
    val nightSurcharge: String
    val sharedDiscount: String
    val total: String
    val gatewayPendingNotice: String

    // Ride status
    val rideStatusTitle: String
    val statusRequested: String
    val statusAccepted: String
    val statusDriverArriving: String
    val statusPickedUp: String
    val statusCompleted: String
    val statusPaid: String
    val statusDeclined: String
    val statusCancelled: String
    val chat: String
    val call: String
    val sos: String
    val shareTrip: String
    val cancelRide: String
    val advanceRide: String
    val payNow: String
    val rateTrip: String
    val noActiveRide: String
    val noActiveRideBody: String

    // Chat
    val chatTitle: String
    val messageHint: String
    val send: String
    val chatLockedNotice: String
    val maskedCallNotice: String

    // Rating
    val rateTitle: String
    val rateBody: String
    val feedbackHint: String
    val submitRating: String
    val ratingThanks: String

    // Dashboards
    val today: String
    val thisWeek: String
    val thisMonth: String
    val pendingBalance: String
    val completedTrips: String
    val yourRating: String
    val withdraw: String
    val withdrawNotice: String
    val rideHistory: String
    val noRideHistory: String
    val paymentMethods: String
    val receipt: String
    val activeRide: String

    // Profile and safety
    val profileTitle: String
    val language: String
    val trustedContacts: String
    val addTrustedContact: String
    val noTrustedContacts: String
    val safetyToolkit: String
    val reportUser: String
    val blockUser: String
    val logOut: String
    val driverProfile: String
    val becomeDriver: String
    val licenseNumber: String
    val unverified: String
    val verified: String
    val sosConfirmTitle: String
    val sosConfirmBody: String
    val sosRaised: String
    val tripShared: String
    val addContactFirst: String

    // Admin
    val adminTitle: String
    val adminUsers: String
    val adminDrivers: String
    val adminTrips: String
    val adminBookings: String
    val adminRevenue: String
    val adminSafety: String
    val adminVerifyDriver: String
    val adminUnverify: String
    val adminResolve: String
    val adminNoIssues: String

    // Generic
    val loading: String
    val somethingWentWrong: String
    val emptyHere: String
    val minutesShort: String
    val seat: String
    val seats: String
}

object EnglishStrings : Strings {
    override val appName = "Pothe Ride"
    override val tagline = "Already on the road? Share the ride."

    override val phoneLabel = "Phone number"
    override val phoneHint = "01712345678"
    override val nameLabel = "Your name"
    override val nameHint = "Full name"
    override val sendOtp = "Send code"
    override val otpLabel = "Verification code"
    override val otpHelper = "We sent a 4-digit code to your phone"
    override val verifyContinue = "Verify and continue"
    override val changeNumber = "Change number"
    override val demoOtpNotice = "Demo build: any 4 digits will verify. Real SMS arrives once an OTP provider is connected."
    override val resendCode = "Resend code"

    override val navHome = "Home"
    override val navSearch = "Search"
    override val navActivity = "Activity"
    override val navProfile = "Profile"
    override val navChat = "Chat"
    override val searchRide = "Search a ride"
    override val searchRideHint = "pickup + drop-off, find a match"
    override val publishRouteHint = "share a journey you are already making"
    override val recentTrips = "Recent trips"
    override val noMessagesTitle = "No conversations yet"
    override val noMessagesBody = "Once a driver accepts your request you can message them here."
    override val back = "Back"
    override val cancel = "Cancel"
    override val confirm = "Confirm"
    override val save = "Save"
    override val close = "Close"
    override val retry = "Try again"
    override val done = "Done"

    override val greeting = "Hi"
    override val passengerMode = "Passenger"
    override val driverMode = "Driver"
    override val findRideTitle = "Find a ride"
    override val findRideBody = "Search drivers already heading your way."
    override val shareRouteTitle = "Share your route"
    override val shareRouteBody = "Publish where you're already driving and pick up passengers along the way."
    override val searchRides = "Search rides"
    override val publishRoute = "Publish route"
    override val myDashboard = "My dashboard"
    override val earningsDashboard = "Earnings"
    override val savedPlaces = "Saved places"
    override val addSavedPlace = "Add a place"
    override val home = "Home"
    override val work = "Work"
    override val notifications = "Notifications"
    override val noNotifications = "Nothing new right now."

    override val createTripTitle = "Share your route"
    override val startLocation = "Start location"
    override val destination = "Destination"
    override val vehicleType = "Vehicle type"
    override val plateNumber = "Plate number"
    override val seatsAvailable = "Seats available"
    override val acceptableDetour = "Acceptable detour"
    override val departureTime = "Leaving in"
    override val departingIn = "Departing in"
    override val routePreviewHint = "Passengers whose pickup and drop-off sit near this line can request a seat."
    override val publishing = "Publishing…"
    override val routePublished = "Route published"

    override val liveRouteTitle = "Live route"
    override val incomingRequests = "Seat requests"
    override val noRequestsYet = "No requests yet. Passengers matching your route will appear here."
    override val accept = "Accept"
    override val decline = "Decline"
    override val startTrip = "Start trip"
    override val simulateDriving = "Simulate driving"
    override val stopSimulation = "Stop simulation"
    override val usingRealGps = "Live GPS"
    override val usingSimulatedGps = "Simulated drive"
    override val locationPermissionNeeded = "Location access lets passengers see where you are."
    override val grantPermission = "Allow location"
    override val progressAlongRoute = "Progress along route"
    override val seatsLeft = "seats left"
    override val onYourRoute = "on your route"

    override val searchTitle = "Find a ride"
    override val pickupPoint = "Pickup point"
    override val dropOffPoint = "Drop-off point"
    override val seatsNeeded = "Seats needed"
    override val leavingWithin = "Leaving within"
    override val searchMatchingRides = "Search matching rides"
    override val searching = "Matching routes…"
    override val matchesTitle = "Route matches"
    override val noMatchesTitle = "No matching routes"
    override val noMatchesBody = "Nobody is driving your way in this window. Try a wider time range or fewer seats."
    override val perSeat = "per seat"
    override val viewRoute = "View route"
    override val routeOverlap = "Route overlap"
    override val extraDetour = "Extra detour"
    override val fareEstimate = "Fare estimate"
    override val pickupEta = "Pickup at"
    override val dropoffEta = "Arrive by"
    override val verifiedDriver = "Verified"
    override val newDriver = "New driver"
    override val tripsCompleted = "trips"

    override val routePreviewTitle = "Route preview"
    override val requestSeat = "Request seat"
    override val confirmRequest = "Confirm request"
    override val payWith = "Pay with"
    override val requestSent = "Request sent"
    override val requestSentBody = "Waiting for the driver to accept. We'll notify you either way."
    override val trackRide = "Track ride"
    override val fareBreakdown = "Fare breakdown"
    override val baseFare = "Base fare"
    override val distanceFare = "Distance"
    override val timeFare = "Time"
    override val nightSurcharge = "Night surcharge"
    override val sharedDiscount = "Shared-route discount"
    override val total = "Total"
    override val gatewayPendingNotice = "Mobile wallet and card payments are recorded as pending in this build — no gateway is connected yet."

    override val rideStatusTitle = "Ride status"
    override val statusRequested = "Requested"
    override val statusAccepted = "Accepted"
    override val statusDriverArriving = "Driver arriving"
    override val statusPickedUp = "Picked up"
    override val statusCompleted = "Completed"
    override val statusPaid = "Paid"
    override val statusDeclined = "Declined"
    override val statusCancelled = "Cancelled"
    override val chat = "Chat"
    override val call = "Call"
    override val sos = "SOS"
    override val shareTrip = "Share trip"
    override val cancelRide = "Cancel ride"
    override val advanceRide = "Next step"
    override val payNow = "Pay now"
    override val rateTrip = "Rate this trip"
    override val noActiveRide = "No active ride"
    override val noActiveRideBody = "Search for a route to get going."

    override val chatTitle = "Messages"
    override val messageHint = "Write a message"
    override val send = "Send"
    override val chatLockedNotice = "Chat opens once the driver accepts your request."
    override val maskedCallNotice = "Calls are placed through a masked number, so neither side sees the other's real phone number."

    override val rateTitle = "Rate your trip"
    override val rateBody = "How did it go?"
    override val feedbackHint = "Add a note (optional)"
    override val submitRating = "Submit rating"
    override val ratingThanks = "Thanks for the feedback."

    override val today = "Today"
    override val thisWeek = "This week"
    override val thisMonth = "This month"
    override val pendingBalance = "Pending"
    override val completedTrips = "Completed trips"
    override val yourRating = "Your rating"
    override val withdraw = "Withdraw"
    override val withdrawNotice = "Withdrawals need a payout provider. Connect one before launch."
    override val rideHistory = "Ride history"
    override val noRideHistory = "No rides yet."
    override val paymentMethods = "Payment methods"
    override val receipt = "Receipt"
    override val activeRide = "Active ride"

    override val profileTitle = "Profile"
    override val language = "Language"
    override val trustedContacts = "Trusted contacts"
    override val addTrustedContact = "Add contact"
    override val noTrustedContacts = "Add someone who should know where you are."
    override val safetyToolkit = "Safety"
    override val reportUser = "Report"
    override val blockUser = "Block"
    override val logOut = "Log out"
    override val driverProfile = "Driver profile"
    override val becomeDriver = "Drive with Pothe Ride"
    override val licenseNumber = "Driving licence number"
    override val unverified = "Pending verification"
    override val verified = "Verified"
    override val sosConfirmTitle = "Send emergency alert?"
    override val sosConfirmBody = "Your trusted contacts and our safety team get your live location."
    override val sosRaised = "Emergency alert sent."
    override val tripShared = "Trip shared with your contacts."
    override val addContactFirst = "Add a trusted contact first."

    override val adminTitle = "Admin"
    override val adminUsers = "Users"
    override val adminDrivers = "Drivers"
    override val adminTrips = "Routes"
    override val adminBookings = "Bookings"
    override val adminRevenue = "Platform revenue"
    override val adminSafety = "Safety reports"
    override val adminVerifyDriver = "Verify"
    override val adminUnverify = "Unverify"
    override val adminResolve = "Resolve"
    override val adminNoIssues = "No open reports."

    override val loading = "Loading…"
    override val somethingWentWrong = "Something went wrong."
    override val emptyHere = "Nothing here yet."
    override val minutesShort = "min"
    override val seat = "seat"
    override val seats = "seats"
}

object BanglaStrings : Strings {
    override val appName = "পথে রাইড"
    override val tagline = "এমনিতেই রাস্তায়? রাইডটাও শেয়ার করুন।"

    override val phoneLabel = "ফোন নম্বর"
    override val phoneHint = "০১৭১২৩৪৫৬৭৮"
    override val nameLabel = "আপনার নাম"
    override val nameHint = "পুরো নাম"
    override val sendOtp = "কোড পাঠান"
    override val otpLabel = "যাচাই কোড"
    override val otpHelper = "আপনার ফোনে ৪ সংখ্যার কোড পাঠানো হয়েছে"
    override val verifyContinue = "যাচাই করে এগিয়ে যান"
    override val changeNumber = "নম্বর পরিবর্তন"
    override val demoOtpNotice = "ডেমো সংস্করণ: যেকোনো ৪ সংখ্যা দিয়ে যাচাই হবে। ওটিপি সেবা যুক্ত হলে আসল এসএমএস আসবে।"
    override val resendCode = "আবার কোড পাঠান"

    override val navHome = "হোম"
    override val navSearch = "খুঁজুন"
    override val navActivity = "কার্যক্রম"
    override val navProfile = "প্রোফাইল"
    override val navChat = "চ্যাট"
    override val searchRide = "রাইড খুঁজুন"
    override val searchRideHint = "পিকআপ ও গন্তব্য দিন, মিল খুঁজুন"
    override val publishRouteHint = "আপনি যে যাত্রা করছেন তা শেয়ার করুন"
    override val recentTrips = "সাম্প্রতিক যাত্রা"
    override val noMessagesTitle = "এখনও কোনও কথোপকথন নেই"
    override val noMessagesBody = "চালক আপনার অনুরোধ গ্রহণ করলে এখানে বার্তা পাঠাতে পারবেন।"
    override val back = "পেছনে"
    override val cancel = "বাতিল"
    override val confirm = "নিশ্চিত করুন"
    override val save = "সংরক্ষণ"
    override val close = "বন্ধ"
    override val retry = "আবার চেষ্টা করুন"
    override val done = "সম্পন্ন"

    override val greeting = "হ্যালো"
    override val passengerMode = "যাত্রী"
    override val driverMode = "চালক"
    override val findRideTitle = "রাইড খুঁজুন"
    override val findRideBody = "যারা আপনার পথেই যাচ্ছেন তাদের খুঁজুন।"
    override val shareRouteTitle = "আপনার রুট শেয়ার করুন"
    override val shareRouteBody = "আপনি যে পথে যাচ্ছেন তা প্রকাশ করুন, পথেই যাত্রী তুলুন।"
    override val searchRides = "রাইড খুঁজুন"
    override val publishRoute = "রুট প্রকাশ করুন"
    override val myDashboard = "আমার ড্যাশবোর্ড"
    override val earningsDashboard = "আয়"
    override val savedPlaces = "সংরক্ষিত জায়গা"
    override val addSavedPlace = "জায়গা যোগ করুন"
    override val home = "বাসা"
    override val work = "অফিস"
    override val notifications = "বিজ্ঞপ্তি"
    override val noNotifications = "নতুন কিছু নেই।"

    override val createTripTitle = "আপনার রুট শেয়ার করুন"
    override val startLocation = "যাত্রা শুরু"
    override val destination = "গন্তব্য"
    override val vehicleType = "গাড়ির ধরন"
    override val plateNumber = "নম্বর প্লেট"
    override val seatsAvailable = "খালি আসন"
    override val acceptableDetour = "গ্রহণযোগ্য ঘুরপথ"
    override val departureTime = "ছাড়বেন"
    override val departingIn = "ছাড়বে"
    override val routePreviewHint = "যাদের ওঠা-নামার জায়গা এই লাইনের কাছে, তারাই আসনের অনুরোধ করতে পারবেন।"
    override val publishing = "প্রকাশ করা হচ্ছে…"
    override val routePublished = "রুট প্রকাশিত হয়েছে"

    override val liveRouteTitle = "সরাসরি রুট"
    override val incomingRequests = "আসনের অনুরোধ"
    override val noRequestsYet = "এখনো কোনো অনুরোধ নেই। আপনার রুটের সাথে মিলে গেলে যাত্রীরা এখানে দেখাবে।"
    override val accept = "গ্রহণ করুন"
    override val decline = "প্রত্যাখ্যান"
    override val startTrip = "যাত্রা শুরু"
    override val simulateDriving = "চালানোর অনুকরণ"
    override val stopSimulation = "অনুকরণ বন্ধ"
    override val usingRealGps = "সরাসরি জিপিএস"
    override val usingSimulatedGps = "অনুকরণ করা যাত্রা"
    override val locationPermissionNeeded = "লোকেশন চালু থাকলে যাত্রীরা আপনাকে দেখতে পাবেন।"
    override val grantPermission = "লোকেশন অনুমতি দিন"
    override val progressAlongRoute = "রুটে অগ্রগতি"
    override val seatsLeft = "আসন বাকি"
    override val onYourRoute = "আপনার রুটে"

    override val searchTitle = "রাইড খুঁজুন"
    override val pickupPoint = "ওঠার জায়গা"
    override val dropOffPoint = "নামার জায়গা"
    override val seatsNeeded = "কয়টি আসন"
    override val leavingWithin = "কত সময়ের মধ্যে"
    override val searchMatchingRides = "মিলে যাওয়া রাইড খুঁজুন"
    override val searching = "রুট মেলানো হচ্ছে…"
    override val matchesTitle = "মিলে যাওয়া রুট"
    override val noMatchesTitle = "কোনো রুট মেলেনি"
    override val noMatchesBody = "এই সময়ে কেউ আপনার পথে যাচ্ছেন না। সময়ের পরিধি বাড়িয়ে বা কম আসন নিয়ে দেখুন।"
    override val perSeat = "প্রতি আসন"
    override val viewRoute = "রুট দেখুন"
    override val routeOverlap = "রুট মিল"
    override val extraDetour = "বাড়তি ঘুরপথ"
    override val fareEstimate = "আনুমানিক ভাড়া"
    override val pickupEta = "ওঠার সময়"
    override val dropoffEta = "পৌঁছাবে"
    override val verifiedDriver = "যাচাইকৃত"
    override val newDriver = "নতুন চালক"
    override val tripsCompleted = "যাত্রা"

    override val routePreviewTitle = "রুট প্রিভিউ"
    override val requestSeat = "আসনের অনুরোধ"
    override val confirmRequest = "অনুরোধ নিশ্চিত করুন"
    override val payWith = "পেমেন্ট মাধ্যম"
    override val requestSent = "অনুরোধ পাঠানো হয়েছে"
    override val requestSentBody = "চালকের সম্মতির অপেক্ষায়। যেকোনো সিদ্ধান্ত হলেই জানানো হবে।"
    override val trackRide = "রাইড দেখুন"
    override val fareBreakdown = "ভাড়ার বিবরণ"
    override val baseFare = "মূল ভাড়া"
    override val distanceFare = "দূরত্ব"
    override val timeFare = "সময়"
    override val nightSurcharge = "রাতের চার্জ"
    override val sharedDiscount = "শেয়ার্ড রুট ছাড়"
    override val total = "মোট"
    override val gatewayPendingNotice = "এই সংস্করণে মোবাইল ওয়ালেট ও কার্ড পেমেন্ট অপেক্ষমাণ হিসেবে রাখা হয় — কোনো গেটওয়ে যুক্ত নেই।"

    override val rideStatusTitle = "রাইড স্ট্যাটাস"
    override val statusRequested = "অনুরোধ করা হয়েছে"
    override val statusAccepted = "গৃহীত"
    override val statusDriverArriving = "চালক আসছেন"
    override val statusPickedUp = "যাত্রী উঠেছেন"
    override val statusCompleted = "সম্পন্ন"
    override val statusPaid = "পরিশোধিত"
    override val statusDeclined = "প্রত্যাখ্যাত"
    override val statusCancelled = "বাতিল"
    override val chat = "চ্যাট"
    override val call = "কল"
    override val sos = "জরুরি"
    override val shareTrip = "যাত্রা শেয়ার"
    override val cancelRide = "রাইড বাতিল"
    override val advanceRide = "পরবর্তী ধাপ"
    override val payNow = "পেমেন্ট করুন"
    override val rateTrip = "রেটিং দিন"
    override val noActiveRide = "কোনো চলমান রাইড নেই"
    override val noActiveRideBody = "শুরু করতে একটি রুট খুঁজুন।"

    override val chatTitle = "বার্তা"
    override val messageHint = "বার্তা লিখুন"
    override val send = "পাঠান"
    override val chatLockedNotice = "চালক অনুরোধ গ্রহণ করলে চ্যাট চালু হবে।"
    override val maskedCallNotice = "কল মাস্কড নম্বরে হয়, তাই কেউ কারও আসল নম্বর দেখতে পায় না।"

    override val rateTitle = "যাত্রার রেটিং দিন"
    override val rateBody = "যাত্রা কেমন হলো?"
    override val feedbackHint = "মন্তব্য লিখুন (ঐচ্ছিক)"
    override val submitRating = "রেটিং জমা দিন"
    override val ratingThanks = "মতামতের জন্য ধন্যবাদ।"

    override val today = "আজ"
    override val thisWeek = "এই সপ্তাহ"
    override val thisMonth = "এই মাস"
    override val pendingBalance = "অপেক্ষমাণ"
    override val completedTrips = "সম্পন্ন যাত্রা"
    override val yourRating = "আপনার রেটিং"
    override val withdraw = "উত্তোলন"
    override val withdrawNotice = "উত্তোলনের জন্য পেআউট সেবা লাগবে। চালুর আগে যুক্ত করুন।"
    override val rideHistory = "যাত্রার ইতিহাস"
    override val noRideHistory = "এখনো কোনো যাত্রা নেই।"
    override val paymentMethods = "পেমেন্ট মাধ্যম"
    override val receipt = "রসিদ"
    override val activeRide = "চলমান রাইড"

    override val profileTitle = "প্রোফাইল"
    override val language = "ভাষা"
    override val trustedContacts = "বিশ্বস্ত পরিচিতজন"
    override val addTrustedContact = "পরিচিতজন যোগ করুন"
    override val noTrustedContacts = "আপনি কোথায় আছেন তা জানা উচিত এমন কাউকে যোগ করুন।"
    override val safetyToolkit = "নিরাপত্তা"
    override val reportUser = "রিপোর্ট"
    override val blockUser = "ব্লক"
    override val logOut = "লগ আউট"
    override val driverProfile = "চালক প্রোফাইল"
    override val becomeDriver = "পথে রাইডে চালান"
    override val licenseNumber = "ড্রাইভিং লাইসেন্স নম্বর"
    override val unverified = "যাচাইয়ের অপেক্ষায়"
    override val verified = "যাচাইকৃত"
    override val sosConfirmTitle = "জরুরি সতর্কতা পাঠাবেন?"
    override val sosConfirmBody = "আপনার বিশ্বস্ত পরিচিতজন ও নিরাপত্তা টিম আপনার সরাসরি অবস্থান পাবে।"
    override val sosRaised = "জরুরি সতর্কতা পাঠানো হয়েছে।"
    override val tripShared = "যাত্রা আপনার পরিচিতজনদের সাথে শেয়ার হয়েছে।"
    override val addContactFirst = "আগে একজন বিশ্বস্ত পরিচিতজন যোগ করুন।"

    override val adminTitle = "অ্যাডমিন"
    override val adminUsers = "ব্যবহারকারী"
    override val adminDrivers = "চালক"
    override val adminTrips = "রুট"
    override val adminBookings = "বুকিং"
    override val adminRevenue = "প্ল্যাটফর্ম আয়"
    override val adminSafety = "নিরাপত্তা রিপোর্ট"
    override val adminVerifyDriver = "যাচাই করুন"
    override val adminUnverify = "যাচাই বাতিল"
    override val adminResolve = "নিষ্পত্তি"
    override val adminNoIssues = "কোনো অমীমাংসিত রিপোর্ট নেই।"

    override val loading = "লোড হচ্ছে…"
    override val somethingWentWrong = "কিছু একটা সমস্যা হয়েছে।"
    override val emptyHere = "এখানে এখনো কিছু নেই।"
    override val minutesShort = "মিনিট"
    override val seat = "আসন"
    override val seats = "আসন"
}

fun stringsFor(language: AppLanguage): Strings =
    if (language == AppLanguage.BANGLA) BanglaStrings else EnglishStrings
