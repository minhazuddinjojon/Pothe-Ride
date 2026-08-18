package com.potheride.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.potheride.app.data.local.dao.BookingDao
import com.potheride.app.data.local.dao.DriverDao
import com.potheride.app.data.local.dao.DriverDocumentDao
import com.potheride.app.data.local.dao.JoinDao
import com.potheride.app.data.local.dao.MessageDao
import com.potheride.app.data.local.dao.NotificationDao
import com.potheride.app.data.local.dao.PaymentDao
import com.potheride.app.data.local.dao.RatingDao
import com.potheride.app.data.local.dao.SafetyDao
import com.potheride.app.data.local.dao.SavedPlaceDao
import com.potheride.app.data.local.dao.TripDao
import com.potheride.app.data.local.dao.TrustedContactDao
import com.potheride.app.data.local.dao.UserDao
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.DriverDocumentEntity
import com.potheride.app.data.local.entities.DriverProfileEntity
import com.potheride.app.data.local.entities.MessageEntity
import com.potheride.app.data.local.entities.NotificationEntity
import com.potheride.app.data.local.entities.PaymentEntity
import com.potheride.app.data.local.entities.RatingEntity
import com.potheride.app.data.local.entities.RouteWaypointEntity
import com.potheride.app.data.local.entities.SafetyEventEntity
import com.potheride.app.data.local.entities.SavedPlaceEntity
import com.potheride.app.data.local.entities.TripEntity
import com.potheride.app.data.local.entities.TrustedContactEntity
import com.potheride.app.data.local.entities.UserEntity
import com.potheride.app.data.local.entities.VehicleEntity

/**
 * On-device SQLite store, via Room.
 *
 * In this build the device *is* the backend, which is what lets the app run in
 * Android Studio with no server. That also means a driver on one phone and a
 * passenger on another cannot see each other — the app is fully functional but
 * single-device. See docs/BACKEND.md; the swap point is RideDataSource, which is
 * the only type the UI layer knows about.
 */
@Database(
    entities = [
        UserEntity::class,
        DriverProfileEntity::class,
        VehicleEntity::class,
        TripEntity::class,
        RouteWaypointEntity::class,
        BookingEntity::class,
        PaymentEntity::class,
        RatingEntity::class,
        MessageEntity::class,
        SavedPlaceEntity::class,
        TrustedContactEntity::class,
        NotificationEntity::class,
        SafetyEventEntity::class,
        DriverDocumentEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun driverDao(): DriverDao
    abstract fun driverDocumentDao(): DriverDocumentDao
    abstract fun tripDao(): TripDao
    abstract fun bookingDao(): BookingDao
    abstract fun paymentDao(): PaymentDao
    abstract fun ratingDao(): RatingDao
    abstract fun messageDao(): MessageDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun notificationDao(): NotificationDao
    abstract fun safetyDao(): SafetyDao
    abstract fun joinDao(): JoinDao

    companion object {
        private const val DB_NAME = "pothe_ride.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Room does not enable this by default, and without it every
                        // ForeignKey declaration in the schema is decorative.
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .addMigrations(*ALL_MIGRATIONS)
                .build()

        /** Test hook: lets instrumented tests point at a fresh in-memory database. */
        fun overrideForTesting(database: AppDatabase?) {
            instance = database
        }
    }
}
