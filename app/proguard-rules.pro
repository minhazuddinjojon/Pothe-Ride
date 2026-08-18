# R8 runs in full mode on release builds. These keeps are the minimum needed.

# Room generates implementations reflectively at runtime.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Enum names are persisted as TEXT in SQLite and read back with valueOf-style
# lookups. Obfuscating them would silently corrupt every existing row on upgrade.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}
-keep class com.potheride.app.core.pricing.VehicleClass { *; }
-keep class com.potheride.app.core.pricing.PaymentMethod { *; }
-keep class com.potheride.app.core.pricing.PaymentStatus { *; }
-keep class com.potheride.app.core.ride.RideState { *; }
-keep class com.potheride.app.data.local.entities.** { *; }

# Kotlin coroutines internals.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Play Services location.
-dontwarn com.google.android.gms.**

# Keep line numbers so Play Console crash reports stay readable, but hide the
# original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
