package com.potheride.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real Room migrations, starting at the first schema change since v1 shipped.
 *
 * `AppDatabase` used `fallbackToDestructiveMigration()` through v1 with an explicit note
 * that the first real change must replace it — this is that change. Deleting a driver's
 * verification history (or their whole database) on an app update is not an acceptable
 * cost for adding one table, so this file is now wired into the builder in
 * `AppDatabase.build` instead of the destructive fallback.
 */

/** v1 → v2: adds `driver_documents` for driver verification uploads (Level 6). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `driver_documents` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `driverId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `storagePath` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `rejectionReason` TEXT,
                `uploadedAt` INTEGER NOT NULL,
                `reviewedAt` INTEGER,
                FOREIGN KEY(`driverId`) REFERENCES `driver_profiles`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_driver_documents_driverId` ON `driver_documents` (`driverId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_driver_documents_kind` ON `driver_documents` (`kind`)")
    }
}

/** Every migration the database applies, in order. Add new ones here, never remove old ones. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
