package com.potheride.app.data.remote

/**
 * Collection names and document field keys for the Firestore backend.
 *
 * The collection set is the one named in the project brief. The mapping from the
 * existing Room entities is not one-to-one, and deliberately so:
 *
 *  - `drivers` holds the driver profile with its vehicles nested, because a vehicle is
 *    never read without its driver and Firestore charges per document read.
 *  - `routes` holds a published trip with its waypoint polyline inline, for the same
 *    reason — a route without its geometry is useless to every caller.
 *  - `liveLocations` is a separate top-level collection keyed by trip id rather than a
 *    field on the route document. A driver's position is written every few seconds while
 *    passengers hold a snapshot listener on it; keeping it in the route document would
 *    push the entire route, waypoints included, over the wire on every GPS fix.
 *
 * Field names are declared as constants rather than written inline. A typo in a string
 * literal on the write path and the matching typo on the read path cancel out in a way
 * no test catches until the data is already wrong in production.
 */
object FirestoreSchema {

    // ---- collections ----
    const val USERS = "users"
    const val DRIVERS = "drivers"
    const val ROUTES = "routes"
    const val RIDE_REQUESTS = "rideRequests"
    const val PAYMENTS = "payments"
    const val NOTIFICATIONS = "notifications"
    const val LIVE_LOCATIONS = "liveLocations"

    // ---- subcollections ----
    /** Chat messages, nested under the ride request they belong to. */
    const val MESSAGES = "messages"

    /** Ratings, nested under the ride request that produced them. */
    const val RATINGS = "ratings"

    /** A user's saved places and trusted contacts, nested under their own document. */
    const val SAVED_PLACES = "savedPlaces"
    const val TRUSTED_CONTACTS = "trustedContacts"

    /** Top-level, because an admin reads these across all users. */
    const val SAFETY_EVENTS = "safetyEvents"

    /** Driver verification documents — NID, licence, registration, face capture. */
    const val DRIVER_DOCUMENTS = "documents"

    object User {
        const val PHONE = "phone"
        const val NAME = "name"
        const val LANGUAGE = "language"
        const val OTP_VERIFIED = "otpVerified"
        const val PHOTO_TINT = "photoTint"
        const val BLOCKED = "blocked"
        const val CREATED_AT = "createdAt"
        /** `passenger` | `driver` | `admin` — read by the security rules. */
        const val ROLE = "role"
    }

    object Driver {
        const val USER_ID = "userId"
        const val LICENSE_NUMBER = "licenseNumber"
        const val VERIFIED = "verified"
        const val RATING_SUM = "ratingSum"
        const val RATING_COUNT = "ratingCount"
        const val TOTAL_TRIPS = "totalTrips"
        const val VEHICLES = "vehicles"
    }

    object Vehicle {
        const val ID = "id"
        const val TYPE = "type"
        const val PLATE_NUMBER = "plateNumber"
        const val MODEL = "model"
        const val COLOUR = "colour"
        const val CAPACITY = "capacity"
    }

    object Route {
        const val DRIVER_ID = "driverId"
        const val VEHICLE_ID = "vehicleId"
        const val START_ADDRESS = "startAddress"
        const val START_LAT = "startLat"
        const val START_LNG = "startLng"
        const val END_ADDRESS = "endAddress"
        const val END_LAT = "endLat"
        const val END_LNG = "endLng"
        const val DEPARTURE_TIME = "departureTime"
        const val TOTAL_SEATS = "totalSeats"
        const val AVAILABLE_SEATS = "availableSeats"
        const val DETOUR_KM = "detourKm"
        const val TRAVELLED_KM = "travelledKm"
        const val STATUS = "status"
        const val CREATED_AT = "createdAt"
        /** Flat lat/lng pairs — see `FirestoreMappers` for why not a list of maps. */
        const val WAYPOINTS = "waypoints"
    }

    object RideRequest {
        const val TRIP_ID = "tripId"
        const val PASSENGER_ID = "passengerId"
        const val DRIVER_ID = "driverId"
        const val PICKUP_ADDRESS = "pickupAddress"
        const val PICKUP_LAT = "pickupLat"
        const val PICKUP_LNG = "pickupLng"
        const val DROP_ADDRESS = "dropAddress"
        const val DROP_LAT = "dropLat"
        const val DROP_LNG = "dropLng"
        const val SEATS_REQUESTED = "seatsRequested"
        const val ROUTE_OVERLAP_RATIO = "routeOverlapRatio"
        const val SHARED_DISTANCE_KM = "sharedDistanceKm"
        const val DETOUR_KM = "detourKm"
        const val FARE_POISHA = "farePoisha"
        const val TOTAL_POISHA = "totalPoisha"
        const val PICKUP_ETA_MILLIS = "pickupEtaMillis"
        const val DROPOFF_ETA_MILLIS = "dropoffEtaMillis"
        const val STATUS = "status"
        const val CANCELLATION_REASON = "cancellationReason"
        const val REQUESTED_AT = "requestedAt"
        const val ACCEPTED_AT = "acceptedAt"
        const val COMPLETED_AT = "completedAt"
    }

    object LiveLocation {
        const val TRIP_ID = "tripId"
        const val DRIVER_ID = "driverId"
        const val LAT = "lat"
        const val LNG = "lng"
        const val TRAVELLED_KM = "travelledKm"
        const val UPDATED_AT = "updatedAt"
    }

    object Payment {
        const val BOOKING_ID = "bookingId"
        const val DRIVER_ID = "driverId"
        const val AMOUNT_POISHA = "amountPoisha"
        const val PLATFORM_FEE_POISHA = "platformFeePoisha"
        const val DRIVER_EARNINGS_POISHA = "driverEarningsPoisha"
        const val METHOD = "method"
        const val STATUS = "status"
        const val TRANSACTION_REF = "transactionRef"
        const val CREATED_AT = "createdAt"
        const val PAID_AT = "paidAt"
    }

    object Notification {
        const val USER_ID = "userId"
        const val KIND = "kind"
        const val TITLE_EN = "titleEn"
        const val TITLE_BN = "titleBn"
        const val BODY_EN = "bodyEn"
        const val BODY_BN = "bodyBn"
        const val BOOKING_ID = "bookingId"
        const val READ_AT = "readAt"
        const val CREATED_AT = "createdAt"
    }

    object Message {
        const val SENDER_ID = "senderId"
        const val CONTENT = "content"
        const val READ_AT = "readAt"
        const val SENT_AT = "sentAt"
    }

    object Rating {
        const val RATER_ID = "raterId"
        const val RATEE_ID = "rateeId"
        const val STARS = "stars"
        const val COMMENT = "comment"
        const val CREATED_AT = "createdAt"
    }

    object Place {
        const val LABEL = "label"
        const val ADDRESS = "address"
        const val LAT = "lat"
        const val LNG = "lng"
    }

    object Contact {
        const val NAME = "name"
        const val PHONE = "phone"
    }

    object SafetyEvent {
        const val RAISED_BY_USER_ID = "raisedByUserId"
        const val AGAINST_USER_ID = "againstUserId"
        const val BOOKING_ID = "bookingId"
        const val KIND = "kind"
        const val DETAILS = "details"
        const val LAT = "lat"
        const val LNG = "lng"
        const val RESOLVED = "resolved"
        const val CREATED_AT = "createdAt"
    }

    /** Storage paths for uploaded files. Namespaced per uid so the rules can scope access. */
    object Storage {
        fun driverDocument(uid: String, kind: String, extension: String): String =
            "drivers/$uid/documents/$kind.$extension"

        fun profilePhoto(uid: String, extension: String): String =
            "users/$uid/profile.$extension"

        fun faceCapture(uid: String, timestamp: Long, extension: String): String =
            "users/$uid/face/$timestamp.$extension"
    }
}
