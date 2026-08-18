# Implementation Roadmap — Levels 3 onward

Written so the next session can pick up cold. Each level is: change → test → green build
→ tick `00_PROGRESS.md`. Do not start a level before the previous one is green.

## Level 3 — Repository seam, then Firebase

The PDF forbids rewriting the architecture, and `RideRepository` is currently a concrete
class the ViewModel constructs directly. Firebase cannot be added without a seam, so:

1. Extract `interface RideDataSource` from the public surface of `RideRepository`
   (mechanical — the class already returns `RepoResult` and `Flow`, which is the right
   shape for both backends). Rename the existing class `RoomRideDataSource`.
2. Add `FirebaseRideDataSource` implementing the same interface.
3. Add a `DataSourceProvider` that picks one, defaulting to Room when
   `google-services.json` is absent — so a fresh clone still builds and runs. **This
   matters:** the repo has no Firebase project attached, and a hard dependency on
   `google-services.json` turns "clone and build" into "clone, create a Firebase project,
   then build".
4. Keep Room as the offline cache rather than deleting it. Phase 5 of the PDF requires
   offline handling and network recovery; a Firestore-only app cannot provide either.

**Firestore collections** (from the PDF): `users`, `drivers`, `routes`, `rideRequests`,
`payments`, `notifications`, `liveLocations`.

Map from existing entities: `UserEntity`→`users`, `DriverProfileEntity`+`VehicleEntity`
→`drivers`, `TripEntity`+`RouteWaypointEntity`→`routes`, `BookingEntity`→`rideRequests`,
`PaymentEntity`→`payments`, `NotificationEntity`→`notifications`, and a new
`liveLocations/{tripId}` document written by the driver's tracking service.

Dependencies to add: `firebase-bom`, `firebase-auth-ktx`, `firebase-firestore-ktx`,
`firebase-storage-ktx`, `firebase-messaging-ktx`, and the `google-services` plugin
applied conditionally.

**Tests:** the interface makes the ViewModel testable with a fake for the first time.
Write `FakeRideDataSource` and cover the booking flow end to end.

## Level 4 — Map (OSMDroid + ORS + Nominatim)

- `implementation("org.osmdroid:osmdroid-android:6.1.18")`
- Wrap `MapView` in an `AndroidView`; keep the existing `RouteMapView` as the preview /
  no-network fallback, and switch on tile availability.
- **Set a real `userAgentValue`** — OSM's tile policy blocks the default, and this is the
  single most common reason an OSMDroid integration silently shows a blank grid.
- Routing: OpenRouteService `/v2/directions/driving-car` (free key, needs a key field in
  `local.properties`, never committed). Fall back to GraphHopper, then to a straight
  polyline so the screen never renders empty.
- Search + reverse geocoding: Nominatim. **Rate limit is 1 req/s and it is enforced** —
  debounce the search field by 400 ms and cache results, or the app gets IP-banned.
- Draw order on the overlay: route (green) → shared stretch (blue) → markers →
  live position (amber). Colours come from `LocalMapColors`.

## Level 5 — Real GPS

- Delete `SimulatedDriveTracker` from the production path; keep it behind a debug flag.
- `LocationForegroundService` with `foregroundServiceType="location"`, a persistent
  notification, and `FusedLocationProviderClient` at a 5 s interval.
- Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and
  `ACCESS_BACKGROUND_LOCATION` (API 29+).
- Write to `liveLocations/{tripId}`; passenger attaches a Firestore snapshot listener.
- Handle: permission denied, permission "only this time", GPS switched off
  (`SettingsClient` resolution), airplane mode, and process death.

## Level 6 — Driver verification

New entity `DriverDocumentEntity(id, driverId, kind, storagePath, status, reviewedAt,
rejectionReason)`, `kind ∈ {NID_FRONT, NID_BACK, LICENCE, REGISTRATION, PROFILE_PHOTO}`,
`status ∈ {PENDING, APPROVED, REJECTED}`. This is a **Room schema change → migration
required** (schema v1 is committed; add v2 and a real `Migration`, do not
`fallbackToDestructiveMigration`).

Gate: `DriverProfileEntity.verified` becomes derived — a driver may not publish a route
until every required document is `APPROVED`. Enforce in the repository, not only the UI.

Screens: `DriverRegistrationScreen`, `VerificationStatusScreen` (boards 02B, 02C).

## Level 7 — Face verification

`com.google.mlkit:face-detection` + CameraX. Flow per board 03: capture → assert exactly
one face with open eyes (liveness proxy) → upload to Storage → record the attempt.
The PDF explicitly scopes this to detection, not recognition; the "98% confidence against
NID photo" on the board is **not** achievable with ML Kit face detection alone.
Model it as `FaceMatchResult(passed, confidence?, provider)` so a real matcher can be
dropped in later, and do not display a fabricated confidence figure in the meantime.

## Level 8 — 0–100 match score

`RouteMatcher` already returns the geometry. Add a `MatchScorer` that combines:
pickup proximity, destination proximity, route overlap, departure-time delta.
Keep it a pure function in `core/matching` so it is unit-testable, and make the weights
named constants. Sort results descending; surface the score as the `92% overlap` badge.

## Levels 9–11 — Screens

Work board by board from `01_WIREFRAME_SPEC.md`, using the Level 2 components. Order:
Home (board 01B) → Search/Results (04) → Route preview (05) → Booking confirm (06A) →
Live tracking passenger (07) → Publish route (08) → Live tracking driver (09) →
Payment methods (06B) → Earnings (06C).

Note the bottom-nav change: the wireframe's four tabs are **Home · Activity · Chat ·
Profile**, not the current Home · Search · Activity · Profile.

## Level 12 — Security

`firestore.rules` + `storage.rules` in a top-level `firebase/` directory. Role claim on
the user document (`passenger` / `driver` / `admin`). Rules must enforce: a passenger
reads only their own `rideRequests`; a driver writes `liveLocations` only for a trip they
own; only an admin transitions a document's `status` to `APPROVED`; storage paths are
namespaced per uid and documents are not world-readable.

## Level 13 — Tests

Robolectric is already wired (Level 2), so screen tests are JVM tests. Cover the flows
the PDF names: authentication, booking, ride matching, tracking, notifications, driver
verification.

## Level 14 — Delivery

`./gradlew clean build`, clear every warning (including the three deprecated icons),
generate the architecture and Firebase schema diagrams, write setup + deployment docs,
produce the signed release APK per `docs/RELEASE.md`, and package the ZIP.
