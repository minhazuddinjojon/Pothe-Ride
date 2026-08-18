# Gradle / manifest changes needed for the Level 4 map package

Everything in `app/src/main/java/com/potheride/app/map/**` compiles with zero new
dependencies **except** OSMDroid itself, which is unavoidable for tile rendering. Apply
the following to `app/build.gradle.kts` and `AndroidManifest.xml`.

## `app/build.gradle.kts`

```kotlin
dependencies {
    // ...existing dependencies...
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
```

### API keys — read from `local.properties`, never committed

```kotlin
import java.util.Properties

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    defaultConfig {
        // ...
        buildConfigField(
            "String", "ORS_API_KEY",
            "\"${localProperties.getProperty("ors.apiKey", "")}\""
        )
        buildConfigField(
            "String", "GRAPHHOPPER_API_KEY",
            "\"${localProperties.getProperty("graphhopper.apiKey", "")}\""
        )
    }
}
```

`MapApiKeys.kt` reads these two field names **by reflection**, specifically so this
package can compile before the above is wired up. If the field names above are ever
renamed, update `BuildConfigMapApiKeys.FIELD_ORS` / `FIELD_GRAPHHOPPER` to match — a
mismatch is not a compile error, it silently behaves as "no key configured".

Add to `local.properties` (git-ignored, so this is safe to write locally):

```properties
ors.apiKey=
graphhopper.apiKey=
```

Both free to obtain:
- OpenRouteService: https://openrouteservice.org/dev/#/signup — free tier, 2,000
  requests/day.
- GraphHopper: https://www.graphhopper.com/ — free tier, 500 requests/day. Used only as
  the second link in the chain, so its lower quota matters less.

Leaving both blank is a fully supported state — `RouteRepository` falls through to the
straight-line geometry, and the app still runs.

## `AndroidManifest.xml`

OSMDroid needs no manifest entry beyond what the app already declares:
`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` are
all already present (see `AndroidManifest.xml` as it stands). No change needed here.

## Application startup

Call `com.potheride.app.map.configureOsmdroid(applicationContext)` once, from
`PotheRideApp.onCreate()`, **before** any `OsmRouteMap` composable is first entered.
Missing this step does not crash — it produces a permanently blank tile grid with no
error, which is the single most common OSMDroid integration mistake. See the KDoc on
`configureOsmdroid` for why.

```kotlin
// PotheRideApp.kt
override fun onCreate() {
    super.onCreate()
    com.potheride.app.map.configureOsmdroid(this)
}
```

## What is already done, untouched by the above

- `HttpTransport` / `UrlHttpTransport` / `FakeHttpTransport` — no new dependency, uses
  `HttpURLConnection`.
- `Json` — hand-written, no serialization library, deliberately (see its KDoc: the
  Android-shipped `org.json` is stubbed out under Robolectric's `isReturnDefaultValues`,
  which would make every parse silently succeed-into-nothing under test).
- `PolylineCodec`, `RouteRepository`, `NominatimClient`, `PlaceSearchController` — pure
  Kotlin plus coroutines, both already in the project.
- `OsmRouteMap.kt` needs only the OSMDroid dependency above.

## Test coverage in this package

`RouteRepositoryTest`, `NominatimClientTest`, `PlaceSearchControllerTest`,
`PolylineCodecTest` — all pure-JVM, no network, no emulator. `OsmRouteMap` itself is not
unit-tested (it is a thin `AndroidView` wrapper around OSMDroid's own `MapView`); verify
it visually once the dependency above is wired in.
