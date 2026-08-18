package com.potheride.app.data.local

import androidx.room.TypeConverter
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PaymentStatus
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.RideState
import com.potheride.app.core.verification.DriverDocumentKind
import com.potheride.app.core.verification.DriverDocumentStatus
import com.potheride.app.data.local.entities.NotificationKind
import com.potheride.app.data.local.entities.SafetyEventKind
import com.potheride.app.data.local.entities.TripStatus

/**
 * Enums are stored as their `name` in TEXT columns. Every reader falls back to a
 * safe default instead of throwing: an unknown value in the database (from a
 * downgrade, or a hand-edited row) should degrade the row, not crash the app on
 * the query that touches it.
 */
class Converters {

    @TypeConverter fun vehicleClassToString(value: VehicleClass): String = value.name
    @TypeConverter fun stringToVehicleClass(value: String): VehicleClass =
        VehicleClass.fromNameOrNull(value) ?: VehicleClass.CAR

    @TypeConverter fun tripStatusToString(value: TripStatus): String = value.name
    @TypeConverter fun stringToTripStatus(value: String): TripStatus =
        TripStatus.values().firstOrNull { it.name == value } ?: TripStatus.CANCELLED

    @TypeConverter fun rideStateToString(value: RideState): String = value.name
    @TypeConverter fun stringToRideState(value: String): RideState =
        RideState.values().firstOrNull { it.name == value } ?: RideState.CANCELLED

    @TypeConverter fun paymentMethodToString(value: PaymentMethod): String = value.name
    @TypeConverter fun stringToPaymentMethod(value: String): PaymentMethod =
        PaymentMethod.fromNameOrNull(value) ?: PaymentMethod.CASH

    @TypeConverter fun paymentStatusToString(value: PaymentStatus): String = value.name
    @TypeConverter fun stringToPaymentStatus(value: String): PaymentStatus =
        PaymentStatus.values().firstOrNull { it.name == value } ?: PaymentStatus.PENDING

    @TypeConverter fun notificationKindToString(value: NotificationKind): String = value.name
    @TypeConverter fun stringToNotificationKind(value: String): NotificationKind =
        NotificationKind.values().firstOrNull { it.name == value } ?: NotificationKind.SYSTEM

    @TypeConverter fun safetyKindToString(value: SafetyEventKind): String = value.name
    @TypeConverter fun stringToSafetyKind(value: String): SafetyEventKind =
        SafetyEventKind.values().firstOrNull { it.name == value } ?: SafetyEventKind.REPORT

    @TypeConverter fun documentKindToString(value: DriverDocumentKind): String = value.name
    @TypeConverter fun stringToDocumentKind(value: String): DriverDocumentKind =
        DriverDocumentKind.fromNameOrNull(value) ?: DriverDocumentKind.PROFILE_PHOTO

    @TypeConverter fun documentStatusToString(value: DriverDocumentStatus): String = value.name
    @TypeConverter fun stringToDocumentStatus(value: String): DriverDocumentStatus =
        DriverDocumentStatus.fromNameOrNull(value) ?: DriverDocumentStatus.PENDING
}
