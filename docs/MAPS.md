# Switching to Google Maps

The app currently draws routes on a custom Canvas
(`ui/components/RouteMapView.kt`) rather than using the Maps SDK.

## Why it works this way today

The route view is not a mock. It takes real `LatLng` values, projects them through
`core/geo/Bounds.toUnitSquare()`, and draws them to scale with north up. The
published path, the passenger's boarding and alighting points, the shared stretch,
and the driver's live position are all real data.

The reason for it is practical: the Maps SDK needs an API key tied to a Google Cloud
billing account. Requiring that before the project will even run makes the app
impossible to open, review, or hand to a teammate without setup. The trade is that
the map has no satellite imagery, no street labels, and no road geometry — the line
between two waypoints is straight, because there is no Directions API telling us
otherwise.

## What to change

**1. Add the dependencies** in `app/build.gradle.kts`:

```kotlin
implementation("com.google.android.gms:play-services-maps:19.0.0")
implementation("com.google.maps.android:maps-compose:4.4.1")
```

**2. Add the key.** Put `MAPS_API_KEY=your_key` in `local.properties` (git-ignored),
read it in `build.gradle.kts` via `manifestPlaceholders`, and uncomment the
`meta-data` block already present in `AndroidManifest.xml`.

**3. Replace the Canvas body** in `RouteMapView`. The signature stays identical, so
no calling screen changes:

```kotlin
GoogleMap(cameraPositionState = cameraPositionState) {
    Polyline(points = route.map { LatLng(it.lat, it.lng) }, color = RouteGreen)
    pickup?.let { Marker(state = MarkerState(LatLng(it.lat, it.lng))) }
    drop?.let { Marker(state = MarkerState(LatLng(it.lat, it.lng))) }
    driverPosition?.let { Marker(state = MarkerState(LatLng(it.lat, it.lng))) }
}
```

## The more valuable change: real road geometry

Bigger than the visual upgrade. Today `PotheRideViewModel.publishTrip` interpolates
three waypoints along the straight line between origin and destination. Every
downstream calculation — matching tolerance, route overlap, fare, ETA — is computed
against that straight line.

Call the Directions API when a driver publishes, decode the returned polyline, and
pass it as `waypoints`. Nothing else in the codebase needs to change, and the
matcher immediately starts working on real roads. That single call is the difference
between a demo and a product.

## Place search

`DhakaPlaces` in `data/local/DemoSeeder.kt` is a fixed table of 16 Dhaka landmarks
backing the pickup and destination fields. Replace `DhakaPlaces.search()` with the
Places Autocomplete API. Keep the contract identical: a selected name must always
resolve to a coordinate, because the matcher works on geometry and would fail
silently on a free-text address.
