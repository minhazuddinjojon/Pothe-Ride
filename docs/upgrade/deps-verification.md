# Gradle / manifest / schema changes for Levels 6–7 (driver + face verification)

All applied directly this session (this session owns `build.gradle.kts`,
`AndroidManifest.xml`, and `AppDatabase.kt`).

## `app/build.gradle.kts` — applied

```kotlin
val cameraxVersion = "1.3.4"
implementation("androidx.camera:camera-core:$cameraxVersion")
implementation("androidx.camera:camera-camera2:$cameraxVersion")
implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
implementation("androidx.camera:camera-view:$cameraxVersion")
implementation("com.google.mlkit:face-detection:16.1.7")
```

## `AndroidManifest.xml` — applied

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.front" android:required="false" />
```

`required="false"` on both features: a device with no camera at all can still complete
the rest of registration and simply cannot reach the face-verification step — that is a
softer failure than being unable to install the app.

## `AppDatabase.kt` — applied

- `DriverDocumentEntity` added to the `entities` list.
- `driverDocumentDao()` abstract accessor added.
- **Version bumped 1 → 2**, and `fallbackToDestructiveMigration()` replaced with
  `.addMigrations(*ALL_MIGRATIONS)` from the new `data/local/Migrations.kt`
  (`MIGRATION_1_2` creates the `driver_documents` table). This is a real migration, not a
  destructive one — an app update must not delete anyone's ride history to add one table.

### Generating the committed v2 schema JSON

Room's `room.schemaLocation` (already configured in `build.gradle.kts`) writes
`app/schemas/com.potheride.app.data.local.AppDatabase/2.json` automatically the first time
the project is built with `version = 2`. That file is **not** hand-written here — running
`./gradlew compileDebugKotlin` (or any build) generates it via KSP. **Commit it** once it
appears; Room's `MigrationTestHelper` needs the old schema JSON on the test classpath to
verify a migration, and a missing schema file is the most common reason
"the migration test can't find schema version 1" shows up later.

## Runtime plumbing note — driver documents are local-only for now

`PotheRideViewModel.uploadDriverDocument` / `driverDocuments` / `reviewDriverDocument`
read and write `AppDatabase.driverDocumentDao()` directly, **not** through
`RideDataSource`. This is a deliberate, documented scope limit: uploading the actual file
to Firebase Storage (`FirestoreSchema.Storage.driverDocument(...)`) is real work — content
picking, upload progress, retry-on-failure — that was out of scope for this pass. The
approval **rules** (`VerificationRules`) are fully backend-agnostic pure Kotlin, so wiring
Storage later only changes where the `DriverDocument` rows come from, never the gate that
reads them or `PotheRideViewModel.publishTrip`'s enforcement of it.

## What is already done, complete and tested

- `core/verification/VerificationRules.kt`, `FaceVerification.kt` — pure, no Android
  dependency. `VerificationRulesTest`, `FaceChecksTest` — 22 tests total.
- `data/local/entities/DriverDocuments.kt`, `Converters.kt` extended, `Migrations.kt`.
- `verification/FaceDetectorAdapter.kt` — the only file importing ML Kit's `Face` type.
- `verification/CameraCapture.kt` — CameraX preview + live analysis + still capture.
- `ui/screens/DriverRegistrationScreen.kt`, `VerificationStatusScreen.kt`,
  `FaceVerificationScreen.kt` — boards 02B, 02C, 03.
- `PotheRideViewModel.publishTrip` now refuses to publish unless
  `VerificationRules.canPublishRoute(...)` is true — enforced below the UI, not only by
  disabling a button.
- Admin approval: `PotheRideViewModel.reviewDriverDocument(documentId, approve, reason)`.
  `AdminScreen` does not yet have a UI for this — it needs a small addition to list
  pending documents and call it; the ViewModel side is done.

## Not done / explicitly out of scope this pass

- Firebase Storage upload for documents and face captures (see the runtime-plumbing note
  above).
- A real `FaceMatchProvider` that compares against the NID photo — `LivenessOnlyFaceMatchProvider`
  is what ships; see `FaceVerification.kt`'s file header for why a confidence figure is not
  fabricated in its place.
- `AdminScreen` UI for reviewing documents (ViewModel function exists; no screen change).
- Instrumented Room migration test (`MigrationTestHelper`) — the project has no
  `androidTest` migration test infrastructure yet; this needs a device/emulator to run
  and was out of reach for a pass that could not run a build.
