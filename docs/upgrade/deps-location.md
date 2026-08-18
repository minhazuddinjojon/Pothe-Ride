# Manifest / resource changes needed for Level 5 (real GPS)

No new Gradle dependency — `play-services-location` is already present. Applied directly
(this session owns `AndroidManifest.xml` and `strings.xml`).

## AndroidManifest.xml — applied

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<!-- Not requested: ACCESS_BACKGROUND_LOCATION. The service only needs to survive the
     app being minimised, which foregroundServiceType="location" already covers; asking
     for background access on top invites a Play Store review rejection for a
     permission the app does not actually need. -->

<service
    android:name=".location.LocationForegroundService"
    android:exported="false"
    android:foregroundServiceType="location" />
```

## strings.xml — applied

```xml
<string name="tracking_notification_channel">Live location</string>
<string name="tracking_notification_title">Pothe Ride</string>
<string name="tracking_notification_body_starting">Starting live tracking…</string>
<string name="tracking_notification_body_active">Sharing your location with your passenger</string>
```

## What is already done

- `location/LocationWritePolicy.kt`, `LocationPermissionState.kt`, `TrackingSession.kt` —
  pure decision logic, unit-tested.
- `location/LocationForegroundService.kt` — the Android glue: owns the notification,
  `START_REDELIVER_INTENT`, and recovery via `TrackingRecovery` after process death.
- `SimulatedDriveTracker` stays as the debug/emulator fallback (see its own file) — the
  production path is `LocationForegroundService` calling `RideDataSource.recordLocation`
  directly, not the simulator.

## Wiring still needed in the UI layer (not part of this location package)

`DriverLiveScreen` (or wherever "start ride" lives) should call:

```kotlin
LocationForegroundService.start(context, tripId = trip.id, driverId = driver.id)
```

on ride start, and `LocationForegroundService.stop(context)` on completion/cancellation.
Also request `ACCESS_FINE_LOCATION` (falling back to `ACCESS_COARSE_LOCATION`) before
calling `start`, and handle the `TrackingReadiness.NeedsLocationServices` case from
`TrackingGate.evaluate` with a `SettingsClient` resolution dialog rather than a raw denial.
