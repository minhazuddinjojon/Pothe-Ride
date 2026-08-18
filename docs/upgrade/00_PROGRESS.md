# Pothe Ride Upgrade — Progress Ledger

**Working dir:** `D:\sisko\PotheRide`
**Started:** 2026-08-18
**Sources of truth:** `Pothe_Ride_Wireframes.pptx` (9 sections), `Claude Code Master Prompt ... .pdf` (15 phases)

Resume rule: read this file, find the first level not marked ✅, continue there.

## Build commands

```bash
cd D:/sisko/PotheRide && export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest --console=plain
```

Android SDK: `C:/Users/User/AppData/Local/Android/Sdk` (written to `local.properties`).
JDK: Android Studio JBR (no system `java` on PATH).

## Levels

Each level = implement + test + green build before moving on.

| # | Level | Phase(s) in PDF | Status |
|---|-------|-----------------|--------|
| 0 | Baseline: build + existing tests green | — | ✅ |
| 1 | Audit + wireframe gap analysis docs | 1, 2 | ✅ |
| 2 | Design system rebuild (3D/modern, wireframe-accurate) | 12 | ✅ |
| 3a | Repository interface seam + booking-flow contract tests | 3 | ✅ |
| 3b | Firebase implementation behind the seam | 3 | ⬜ |
| 4 | OSMDroid map + ORS routing + Nominatim search | 4 | ⬜ |
| 5 | Real fused-GPS live tracking + foreground service | 5 | ⬜ |
| 6 | Driver verification & document upload + approval workflow | 6 | ⬜ |
| 7 | Face verification (ML Kit) | 7 | ⬜ |
| 8 | Route matching engine 0–100 score | 8 | ✅ |
| 9 | Passenger flow screens to wireframe | 9 | ⬜ |
| 10 | Driver flow screens to wireframe | 10 | ⬜ |
| 11 | Payment module (cash/bKash/Nagad/Rocket) + earnings | 11 | ⬜ |
| 12 | Security rules + role-based access | 13 | ⬜ |
| 13 | Test suite expansion | 14 | ⬜ |
| 14 | Docs, diagrams, release build, ZIP | 15 | ⬜ |

Legend: ⬜ not started · 🔄 in progress · ✅ done & verified

## Log

- **2026-08-18 · Level 0 ✅** — Extracted project to `D:\sisko\PotheRide`. Extracted 10
  wireframe pages from the PPTX (each slide is a 2×2 image mosaic; reassembled to
  `<scratchpad>/sheet_01..10.png`). Fixed `local.properties` (forward slashes — backslash
  escapes make Gradle fail with `IOException: Invalid file path`).
  `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL, all pre-existing tests pass.
- **2026-08-18 · Level 1 ✅** — Wrote `01_WIREFRAME_SPEC.md` (full transcription of all
  9 boards + the visual token table) and `02_AUDIT.md` (architecture, feature, missing-
  feature, tech-debt and per-screen gap tables). Key decision recorded there: keep the
  existing architecture, add a repository interface seam before Firebase rather than
  rewriting the data layer.
- **2026-08-18 · Level 2 ✅** — Design system. Verified: **116 tests, 0 failures, 0 skipped**
  (was 107; the 9 new UI tests were confirmed to actually execute, not skip).
  - `theme/Color.kt` — added `ActionBlue` (the wireframes' CTA colour, distinct from the
    near-black `primary` used by selection chips), map canvas/street colours, and
    `SharedStretchBlue`.
  - `theme/Depth.kt` — new. A four-step depth scale (`FLAT/RESTING/FLOATING/LIFTED`)
    rendered as paired contact+ambient shadows, plus `Modifier.pressDepth` for the
    press-dip. This is the "modern 3D" treatment, encoding hierarchy rather than decoration.
  - `theme/Type.kt` — added `MetaMonoStyle` / `MetaSansStyle` / `EyebrowStyle`. The boards
    set all metadata in monospace; Bangla falls back to sans since the platform mono face
    has no Bengali coverage.
  - `theme/Theme.kt` — added `LocalMapColors`, `LocalCtaColor`, `metaTextStyle()`, all
    dark-mode aware.
  - `components/WireframeComponents.kt` — new: `Eyebrow`, `MetaText`, `CtaButton`,
    `DestructiveButton`, `StatusBadge`, `OtpBoxes`, `Stepper`, `UploadRow`,
    `DashedDivider`, `DepthCard`.
  - Added **Robolectric** so Compose UI tests run on the JVM — without it the UI can only
    be tested on a booted emulator, which means in practice it never is. Wrote
    `WireframeComponentsTest` (9 render/interaction tests).
  - `screens/AuthScreen.kt` rebuilt to board 01A: eyebrow labels, blue `Get code` CTA,
    four-box OTP entry backed by one hidden `BasicTextField`, outlined `Verify`. The code
    block is present-but-inert before a code is sent so the layout does not jump.
  - Wrote `03_ROADMAP.md` — the step-by-step plan for Levels 3–14, written to be picked
    up cold, including the traps found while reading the code (Room migration needed for
    driver documents, OSMDroid user-agent, Nominatim rate limit, Firebase optionality).

- **2026-08-18 · Level 3a ✅** — The seam Firebase needs. Verified: **128 tests, 0 failures,
  0 skipped**.
  - `data/repository/RideDataSource.kt` — new interface, all 61 public members of the old
    `RideRepository`, generated from the source rather than hand-transcribed. Default
    argument values live here only; Kotlin forbids an override from restating them.
  - `RideRepository` → renamed `RoomRideDataSource`, now `: RideDataSource` with `override`
    on all 61 members. **No behaviour changed** — this is purely a seam.
  - `PotheRideViewModel` and `DemoSeeder` now depend on the interface, not the class.
    The whole Firebase migration is now: write a second implementation, change one line.
  - `data/repository/BookingFlowTest.kt` — new, 12 tests against in-memory Room through
    the *interface*, so they can be re-pointed at the Firestore implementation unchanged.
    Covers what `core/` tests cannot: seat accounting on accept/decline, illegal
    transitions being refused, actor permissions, the fare/fee/earnings split summing to
    the total, full trips dropping out of matching, and live-location recording.

- **2026-08-18 · Level 8 ✅** — Match scoring (taken out of order: it is pure Kotlin, has
  no dependency on Firebase or the map, and the results screen needs it). Verified:
  **142 tests, 0 failures, 0 skipped**.
  - `core/matching/MatchScorer.kt` — new. Weighted 0–100 score over the four factors the
    PDF names: overlap 0.40, departure time 0.25, pickup proximity 0.20, destination
    proximity 0.15. Weights are named constants and a test asserts they sum to 1.0.
    Proximity falls linearly to zero at 1.5 km; departure peaks at a 5-minute wait and
    reaches zero at 45 minutes, with already-departed rides scored 0 rather than negative.
  - `MatchScore` keeps the per-factor breakdown and exposes `weakestFactor`, so the UI can
    say *why* a ride ranked where it did instead of showing a bare number.
  - `RoomRideDataSource.searchMatches` now ranks by score instead of raw overlap. **This
    is a real behaviour fix:** the old ordering put a perfect-overlap ride leaving in three
    hours above a good-fit ride leaving in ten minutes. A test pins the new behaviour.
  - `MatchedRide.score` added with a default, so existing construction sites are unaffected.
  - `core/matching/MatchScorerTest.kt` — 14 tests: bounds, each factor in isolation, the
    ranking trade-offs, weight integrity, and the diagnostic breakdown.

- **2026-08-18 · Level 3b 🔄** — Firebase project created and SDK wired in. Verified:
  `assembleDebug` succeeds, **142 tests, 0 failures**.
  - Firebase project **Pothe Ride** — id `pothe-ride-9f8a3`, number `647753040727`,
    Spark (free) plan. Storage bucket `pothe-ride-9f8a3.firebasestorage.app`. A Realtime
    Database also exists in `asia-southeast1` (created during setup; the app does not
    use RTDB — Firestore is the intended store).
  - `app/google-services.json` installed.
  - Root `build.gradle.kts`: `com.google.gms.google-services` 4.4.2, `apply false`.
  - `app/build.gradle.kts`: plugin applied **only when the config file exists**, so
    `git clone && ./gradlew build` still works for anyone without their own Firebase
    project. Firebase BOM 33.1.2 + auth/firestore/storage/messaging.
    `BuildConfig.HAS_FIREBASE` records the decision for runtime.
  - `data/repository/FirebaseAvailability.kt` — runtime gate; falls back to the local
    Room store if `FirebaseApp` fails to initialise rather than throwing deep in a coroutine.
  - **`applicationIdSuffix = ".debug"` removed** — only `com.potheride.app` is registered
    in the Firebase project, so debug builds reporting `com.potheride.app.debug` would
    have every Firebase call rejected. Reversion instructions are in the build file.

- **2026-08-18 · Firebase console setup ✅ (via CLI)** — the console UI kept hanging on
  "Add app"; `firebase-tools` did the lot in about a minute. Note `firebase login` alone
  hangs in a non-interactive shell — **use `firebase login --no-localhost`**, which prints
  a URL and then takes the code via `firebase login <code>`.
  - Both Android apps registered:
    - `com.potheride.app` → `1:647753040727:android:42e6026ea177cec9827412`
    - `com.potheride.app.debug` → `1:647753040727:android:4f29dc3e9d7c6295827412`
  - SHA-1 `dd4ab71ffec898aaefcf4956f668b500f2099418` verified on **both** apps.
  - Firestore created: Native mode, **asia-south1 (Mumbai)** — closest region to Dhaka.
    Permanent. Default rules are **closed**, so all reads fail until Level 12 writes rules.
  - `google-services.json` regenerated with both packages; `applicationIdSuffix = ".debug"`
    restored. `assembleDebug` + 142 tests green.

- **2026-08-18 · Level 3b — mapping layer ✅** — Verified: **163 tests, 0 failures, 0 skipped**.
  - `data/remote/FirestoreSchema.kt` — collection names, field-key constants, Storage paths.
    Field keys are constants because a typo on the write path and the same typo on the read
    path cancel out, and no test catches it until the data is already wrong.
  - `data/remote/FirestoreMappers.kt` — pure entity ↔ document conversion, so the whole
    mapping layer is unit-testable with no emulator.
  - `FirestoreMappersTest` — 21 tests. Round-trips plus the failure modes that actually
    bite: whole-number decimals arriving as `Long` (Firestore normalises `0.0` to `Long` —
    a direct cast throws on the first free ride), unknown enum values from a newer client,
    wrongly-typed and missing fields, and truncated waypoint arrays.
  - Modelling decisions: vehicles nested on the driver doc and waypoints inline on the
    route doc (both are never read separately, and Firestore bills per document read);
    `liveLocations` kept as its own top-level collection so a 5-second GPS write does not
    push the whole route polyline over the wire; `driverId` denormalised onto each ride
    request so a driver can query incoming requests in one indexed read.
  - Waypoints stored as a flat `[lat, lng, …]` number array, not a list of maps — one
    indexed field instead of two per point, against Firestore's 20k index-entry cap.

- **2026-08-18 · Level 3b — Firestore plumbing + shared copy ✅** — Verified:
  **172 tests, 0 failures, 0 skipped**; `assembleDebug` green.
  - `data/remote/FirestoreExtensions.kt` — `DocumentReference.asFlow()` /
    `Query.asFlow()` via `callbackFlow`, giving Firestore the same `Flow` shape the Room
    backend already hands the UI (which is what lets both sit behind one interface).
    `awaitClose` removes the listener on cancellation — a snapshot listener that outlives
    its subscriber keeps billing reads for a screen nobody is looking at. `mapDocuments`
    skips unreadable documents rather than failing the whole list.
  - `data/remote/SeatAccounting.kt` — **the one genuinely new failure mode from going
    multi-device.** Room is single-writer, so its read-then-write of `availableSeats`
    could not interleave; Firestore is shared across every device, and two passengers
    claiming the last seat in the same second is the normal case at a busy pickup point.
    Claims run in a Firestore transaction with the availability checks *inside* it.
    Releases are clamped to `totalSeats` so a retried cancel cannot inflate the vehicle.
  - `data/repository/RideNotifications.kt` — all bilingual notification copy as pure
    functions, **now used by the Room backend too** (~3 KB of duplicated copy deleted from
    `RoomRideDataSource`). This is what stops the two backends drifting: a passenger told
    "Seat confirmed" in Bangla on one device and something subtly different on another is
    a bug nobody catches in review.
  - `RideNotificationsTest` — 9 tests, mostly about *who* gets told what. Notifying the
    wrong party is silent: nothing crashes, the ride works, and the person at the roadside
    simply never hears anything.
  - Added `kotlinx-coroutines-play-services` for `Task<T>.await()`.

#### Still outstanding for Level 3b
  1. **Enable the Phone provider** in Firebase Authentication. Console-only — there is no
     CLI command for it. Auth → Sign-in method → Phone → Enable. SHA-1 is already in place.
  2. Write `FirebaseRideDataSource` implementing `RideDataSource` on top of the mappers,
     `FirestoreExtensions`, `SeatAccounting` and `RideNotifications` — all four are done
     and tested, so this is now assembly rather than design. ~61 members; reuse `core/`
     (RouteMatcher, FareCalculator, RideStateMachine, MatchScorer) exactly as
     `RoomRideDataSource` does — the business rules must not fork.
  3. Add a `DataSourceProvider` selecting Room vs Firebase via `FirebaseAvailability`.
  4. Re-point `BookingFlowTest` at the Firebase implementation (it is written against the
     interface, so this is a constructor swap) and run it against the Firestore emulator
     (`firebase emulators:start --only firestore`).

- **2026-08-18 · Levels 4, 5, 6, 7, 9(partial), 12, 14(partial) — implemented, NOT
  build-verified.** Four background subagents (map, location, verification, security/docs)
  were launched in parallel and all four hit the account's session limit partway through
  and stopped. Their partial output was reviewed file-by-file and is high quality — kept
  as-is. The remaining ~60% of each package was completed directly in this session,
  including the Gradle/manifest/AppDatabase wiring the subagents were deliberately barred
  from touching (to avoid file-conflicts between parallel agents).
  **Per explicit user instruction, no `./gradlew` command was run after this point** — the
  user will build in Android Studio and report back. A static read-through caught and
  fixed several real compile errors (missing imports, a reference to a
  `Formatters.relativeTime` that does not exist, a `RideState.hasCounterpart` that does not
  exist) but **this has not been proven to compile**. Treat the next build as the real
  verification step, not a formality.

  - **Level 4 (map) ✅ implemented.** `map/RouteRepository.kt` (ORS → GraphHopper →
    straight-line, none of it can fail), `map/NominatimClient.kt` (1 req/s rate limiter +
    LRU cache — Nominatim IP-bans abusers), `map/PlaceSearchController.kt` (400ms debounce),
    `map/OsmRouteMap.kt` (Compose/OSMDroid wrapper, colours from `LocalMapColors`). Wired:
    OSMDroid dependency, ORS/GraphHopper API key `buildConfigField`s (read reflectively by
    `MapApiKeys` so the package compiles before the build file catches up),
    `configureOsmdroid()` called from `PotheRideApp.onCreate` — **skipping this produces a
    silently blank tile grid**, it does not error. Tests: `RouteRepositoryTest`,
    `NominatimClientTest`, `PlaceSearchControllerTest`, `PolylineCodecTest`.
    Not done: wiring `OsmRouteMap` into the actual route-preview/tracking screens (they
    still use the old `RouteMapView` canvas fallback).

  - **Level 5 (real GPS) ✅ implemented.** `location/LocationForegroundService.kt` — new,
    written directly (the subagent had built every *pure* piece —
    `LocationWritePolicy`, `LocationPermissionState`, `TrackingSession` — but not the
    Android service itself). `START_REDELIVER_INTENT`, recovers a killed session from
    `SharedPreferences` via `TrackingRecovery`, throttles writes through the existing
    policy. Wired: `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_LOCATION` permissions, the
    `<service>` manifest entry, notification strings. `SimulatedDriveTracker`'s entry
    point (`startSimulatedTracking`) is now gated on `BuildConfig.DEBUG`.
    Not done: no screen calls `LocationForegroundService.start()` yet — `DriverLiveScreen`
    still drives the simulator. That wiring is a few lines once verified building.

  - **Level 6 (driver verification) ✅ implemented.** `core/verification/VerificationRules.kt`
    (subagent) + `data/local/entities/DriverDocuments.kt`, `data/local/Migrations.kt`
    (`MIGRATION_1_2`, replacing `fallbackToDestructiveMigration()` — **this is now a real
    migration; the v2 schema JSON is generated by the *next* build, not hand-written, and
    must be committed once it appears**), `DriverRegistrationScreen`,
    `VerificationStatusScreen` (boards 02B/02C). The publish gate is enforced in
    `PotheRideViewModel.publishTrip` itself, not only by disabling a button. Tests:
    `VerificationRulesTest` (12 cases). **Scope limit, documented in
    `docs/upgrade/deps-verification.md`**: documents are Room-local only — Storage upload
    was not wired, so this does not yet work on the Firebase backend. `AdminScreen` has no
    UI for `reviewDriverDocument` yet (the ViewModel function exists).

  - **Level 7 (face verification) ✅ implemented.** `core/verification/FaceVerification.kt`
    (subagent — the honesty-critical piece: no fabricated confidence score, see its header)
    + `verification/FaceDetectorAdapter.kt`, `verification/CameraCapture.kt` (CameraX +
    ML Kit, written directly), `FaceVerificationScreen.kt` (board 03). Wired: CameraX +
    ML Kit dependencies, `CAMERA` permission. Tests: `FaceChecksTest` (12 cases, including
    that the liveness-only provider genuinely never returns a confidence figure).

  - **Level 9 (passenger screens) 🔄 partial.** `HomeScreen` rebuilt to board 01B: mode
    switch moved to the top, new `HeroCard` component, bottom nav changed from
    Home·Search·Activity·Profile to **Home·Activity·Chat·Profile** per the wireframe
    (`Routes.kt`, `PotheNavHost.kt`). New `ChatsScreen` for the Chat tab (no board actually
    draws what it opens onto — every wireframe chat is reached from inside a ride; this
    lists ride threads still worth talking about and opens into the existing per-ride
    `ChatScreen`). Boards 04/05/06A/07 (search results, route preview map, booking confirm,
    live tracking) are **not done** this pass.

  - **Level 12 (security) ✅ implemented.** `firebase/firestore.rules` (subagent — 422
    lines, careful role-based rules with the critical `role`-field self-write block) +
    `firebase/storage.rules` (written directly, matching `FirestoreSchema.Storage`'s three
    paths). `firebase.json` updated to serve both. `docs/upgrade/04_SECURITY.md` written —
    documents what the rules do **not** cover: ride-transition legality isn't re-validated
    by rules (relies on `FirebaseRideDataSource` calling `RideStateMachine`), no rate
    limiting/App Check, a driver's licence number is visible to any signed-in user (a gap
    the rules file itself flags in a comment). **Not deployed** — `firebase deploy` was not
    run.

  - **Level 14 (docs) 🔄 partial.** `docs/ARCHITECTURE.md` (mermaid layer diagram),
    `docs/FIREBASE_SCHEMA.md` (every collection/field, derived from `FirestoreSchema.kt`),
    `docs/SETUP.md` written. `./gradlew clean build` with zero warnings, the signed release
    APK, and the final ZIP package are **not done** — they require a successful build first.

### Next session starts here

1. **Build it.** `./gradlew testDebugUnitTest` in Android Studio (or `assembleDebug` first
   if you want to catch compile errors before running the suite) — this has not been
   verified and real errors are likely. Report back with whatever the compiler says.
2. Once green: wire `OsmRouteMap` into the actual preview/tracking screens, wire
   `LocationForegroundService.start()`/`.stop()` into `DriverLiveScreen`'s ride-start button,
   commit the generated v2 Room schema JSON.
3. Boards 04/05/06A/06B/07 (Level 9–11 remainder) are the largest piece of UI work left.
4. Then Level 13 (expand the test suite — screen tests for the new screens) and Level 14's
   remaining delivery steps.

Gotcha for whoever resumes: **never run two Gradle invocations at once** on this project —
the second fails with a `classes.jar ... used by another process` lock on Windows.
