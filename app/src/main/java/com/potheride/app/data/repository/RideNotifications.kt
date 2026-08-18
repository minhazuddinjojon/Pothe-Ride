package com.potheride.app.data.repository

import com.potheride.app.core.ride.RideState
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.NotificationKind

/** One notification to be delivered to one user. Backend-agnostic. */
data class PendingNotification(
    val userId: String,
    val kind: NotificationKind,
    val titleEn: String,
    val titleBn: String,
    val bodyEn: String,
    val bodyBn: String,
    val bookingId: String? = null
)

/**
 * The bilingual copy for every notification the app raises, as pure functions.
 *
 * Extracted so the Room and Firestore backends cannot drift apart. This text is the
 * product's voice — a passenger being told "Seat confirmed" in Bangla on one device and
 * something subtly different on another is the kind of inconsistency nobody notices in
 * review and everybody notices in use. Keeping it in one place also makes it testable
 * without a database of any kind.
 */
object RideNotifications {

    fun seatRequested(
        driverUserId: String,
        passengerName: String?,
        seats: Int,
        pickupAddress: String,
        dropAddress: String,
        bookingId: String
    ) = PendingNotification(
        userId = driverUserId,
        kind = NotificationKind.RIDE_REQUEST,
        titleEn = "New seat request",
        titleBn = "নতুন আসনের অনুরোধ",
        bodyEn = "${passengerName ?: "A passenger"} wants $seats seat(s) from $pickupAddress to $dropAddress.",
        bodyBn = "${passengerName ?: "একজন যাত্রী"} $pickupAddress থেকে $dropAddress পর্যন্ত $seats টি আসন চান।",
        bookingId = bookingId
    )

    /**
     * Everything that must be sent when a booking changes state.
     *
     * Returns a list because some transitions notify both parties — a completed ride
     * asks each of them to rate the other — and some notify nobody.
     */
    fun forTransition(
        to: RideState,
        booking: BookingEntity,
        driverUserId: String?,
        passengerName: String?,
        reason: String?
    ): List<PendingNotification> {
        val passengerId = booking.passengerId
        return when (to) {
            RideState.ACCEPTED -> listOf(
                PendingNotification(
                    passengerId, NotificationKind.REQUEST_ACCEPTED,
                    "Seat confirmed", "আসন নিশ্চিত",
                    "Your driver accepted the request. You can now chat and call.",
                    "চালক আপনার অনুরোধ গ্রহণ করেছেন। এখন চ্যাট ও কল করতে পারবেন।",
                    booking.id
                )
            )

            RideState.DECLINED -> listOf(
                PendingNotification(
                    passengerId, NotificationKind.REQUEST_DECLINED,
                    "Request declined", "অনুরোধ প্রত্যাখ্যাত",
                    "The driver couldn't take this request. Try another matching route.",
                    "চালক এই অনুরোধটি নিতে পারেননি। অন্য মিলে যাওয়া রুট দেখুন।",
                    booking.id
                )
            )

            RideState.DRIVER_ARRIVING -> listOf(
                PendingNotification(
                    passengerId, NotificationKind.DRIVER_ARRIVING,
                    "Your driver is on the way", "চালক আসছেন",
                    "Head to ${booking.pickupAddress} for pickup.",
                    "${booking.pickupAddress}-এ যাওয়ার জন্য প্রস্তুত হোন।",
                    booking.id
                )
            )

            RideState.COMPLETED -> buildList {
                add(
                    PendingNotification(
                        passengerId, NotificationKind.RIDE_COMPLETED,
                        "Ride complete", "রাইড সম্পন্ন",
                        "Please settle the fare and rate your driver.",
                        "ভাড়া পরিশোধ করে চালককে রেটিং দিন।",
                        booking.id
                    )
                )
                driverUserId?.let {
                    add(
                        PendingNotification(
                            it, NotificationKind.RIDE_COMPLETED,
                            "Ride complete", "রাইড সম্পন্ন",
                            "Rate ${passengerName ?: "your passenger"} when you get a moment.",
                            "সুযোগ পেলে ${passengerName ?: "যাত্রীকে"} রেটিং দিন।",
                            booking.id
                        )
                    )
                }
            }

            RideState.CANCELLED -> buildList {
                val detail = reason?.let { " ($it)" } ?: ""
                add(
                    PendingNotification(
                        passengerId, NotificationKind.CANCELLATION,
                        "Ride cancelled", "রাইড বাতিল",
                        "This ride was cancelled$detail.", "রাইডটি বাতিল হয়েছে$detail।",
                        booking.id
                    )
                )
                driverUserId?.let {
                    add(
                        PendingNotification(
                            it, NotificationKind.CANCELLATION,
                            "Ride cancelled", "রাইড বাতিল",
                            "A booking on your route was cancelled$detail.",
                            "আপনার রুটের একটি বুকিং বাতিল হয়েছে$detail।",
                            booking.id
                        )
                    )
                }
            }

            // REQUESTED, PICKED_UP and PAID are visible in the UI the moment they
            // happen, so a push on top would be noise.
            else -> emptyList()
        }
    }
}
