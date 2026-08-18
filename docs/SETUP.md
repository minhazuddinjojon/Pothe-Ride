# Setup

## Prerequisites

- **JDK**: use Android Studio's bundled JBR — there is commonly no system `java` on
  Windows dev machines this project has been built on. Point `JAVA_HOME` at it:
  ```
  JAVA_HOME=C:/Program Files/Android/Android Studio/jbr
  ```
- **Android SDK**: set in `local.properties` at the repo root:
  ```properties
  sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
  ```
  **Use forward slashes even on Windows.** Backslash escapes in this file make Gradle
  fail with `java.io.IOException: Invalid file path` before any real build error shows up
  — this is the single most common first-build failure on this project.
- **Node.js + the Firebase CLI**, only if you'll touch Firebase locally:
  ```bash
  npm install -g firebase-tools
  ```

## First build (no Firebase — the local Room backend)

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew testDebugUnitTest
```

No `google-services.json`, no Firebase project, and no API keys are required to reach a
green build. `DataSourceProvider` selects Room automatically when Firebase isn't
configured — see `docs/ARCHITECTURE.md`.

**Do not run two Gradle invocations at once.** On Windows the second one reliably fails
with `classes.jar ... being used by another process` — a file lock, not a real error.
Wait for the first to finish.

## Adding Firebase (optional)

1. Create a Firebase project (console.firebase.google.com), or use the CLI:
   ```bash
   firebase login --no-localhost   # plain `firebase login` hangs in a non-interactive shell
   ```
2. Register **two** Android apps in the project — this is the step that's easy to miss:
   - `com.potheride.app` (release)
   - `com.potheride.app.debug` (debug — the build adds `applicationIdSuffix = ".debug"`)

   Skipping the second one means every debug build's Firebase calls are rejected as
   coming from an unregistered package.
3. Add your debug signing certificate's SHA-1 to **both** apps (phone/OTP auth needs it):
   ```bash
   ./gradlew signingReport
   ```
4. Enable: Authentication → **Phone** provider; Firestore Database (**Native mode** —
   this project uses `asia-south1`, closest region to Dhaka, and **the region is
   permanent once chosen**); Cloud Storage; Cloud Messaging.
5. Download `google-services.json` into `app/`.
6. Rebuild — the `com.google.gms.google-services` Gradle plugin applies automatically
   once the file is present (see `app/build.gradle.kts`; it's a no-op without the file).

Routing/search API keys (optional, both free-tier — see `docs/upgrade/deps-map.md`):
```properties
# local.properties
ors.apiKey=
graphhopper.apiKey=
```
Both blank is fully supported: the map falls back to a straight-line route with no key
at all.

## Running the Firestore emulator (for the Firebase-backed test suite)

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"   # firebase-tools needs `java` resolvable on PATH
firebase emulators:start --only firestore,storage --project pothe-ride-9f8a3
```

With the emulator running, `FirebaseBookingFlowTest` (in `app/src/test`) runs the same
booking-flow contract that `BookingFlowTest` runs against Room, but against real
Firestore. Without the emulator running, those tests are **skipped**, not failed — check
the test report's skip count, not just its pass count, or a dead emulator silently looks
like a clean run.

## Running tests

```bash
./gradlew testDebugUnitTest
```

Everything — `core/`, the Firestore mappers, the map/location/verification pure logic,
and Compose UI via Robolectric — runs on the JVM. No emulator, no device, except the
Firestore-emulator-gated tests noted above.

## Deploying security rules

```bash
firebase deploy --only firestore:rules,storage:rules --project pothe-ride-9f8a3
```

Read `docs/upgrade/04_SECURITY.md` first — it documents what the rules deliberately do
not cover.
