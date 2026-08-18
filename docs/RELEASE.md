# Shipping Pothe Ride

## Android — Play Store

**1. Generate an upload key.** Once, and keep it safe: losing it means you cannot
update your own app.

```bash
keytool -genkey -v -keystore pothe-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias pothe
```

**2. Point the build at it.** Copy `keystore.properties.example` to
`keystore.properties` and fill it in. That file is git-ignored, and
`app/build.gradle.kts` reads it only if it exists — so a fresh clone still builds
without it.

**3. Build the bundle.**

```bash
./gradlew clean test          # runs the core unit tests first
./gradlew bundleRelease       # -> app/build/outputs/bundle/release/app-release.aab
```

R8 and resource shrinking are on for release. If a release build crashes where debug
did not, the cause is nearly always a missing `-keep` rule — check
`app/proguard-rules.pro`.

**4. Play Console.** Create the app, upload the AAB to internal testing first. You
will need:

- A privacy policy URL. Non-negotiable, because the app requests location.
- A Data Safety declaration: location, phone number, name. Declare that location is
  collected and used for app functionality, and whether it is shared.
- Feature graphic (1024×500), icon (512×512), at least two phone screenshots.
- A Prominent Disclosure for location, shown before the permission prompt.

**Note on the location permission.** The app requests foreground location only. If
you later add tracking while the driver's screen is off, that becomes
`ACCESS_BACKGROUND_LOCATION`, which triggers a separate and much slower Play review
requiring a demo video. Design around it if you can.

**Ride-sharing apps get extra scrutiny.** Expect questions about driver vetting,
insurance, and local operating permissions. Have answers ready before you submit.

## iOS — App Store

**This project cannot ship to the App Store.** It is a native Android app: Kotlin,
Jetpack Compose, Room, Play Services. None of that runs on iOS.

Your brief asked for one shared codebase across Android and iOS. This build does not
provide that, and no amount of configuration will make it. The honest options:

**Option A — Compose Multiplatform.** Keeps the most work. `core/` is already pure
Kotlin and moves over untouched; the tests come with it. Room becomes SQLDelight,
Compose UI is largely portable, and the platform-specific parts are location and
maps. Realistically a few weeks, and you keep one codebase.

**Option B — Flutter or React Native rewrite.** The UI is rewritten from scratch.
The value you carry over is the design, the schema, and the algorithms in `core/` —
which you would port to Dart or TypeScript, ideally with the unit tests, since those
encode the actual rules.

**Option C — Ship Android first.** Bangladesh's smartphone market is overwhelmingly
Android. Launching on one platform, learning from real users, and building iOS once
the product is proven is a defensible plan rather than a compromise.

Option A is the best fit given what already exists. But it is a real project, not a
build setting, and it should be scheduled as one.

## The responsive web app

Also not in this build. The same reasoning applies: `core/` compiles to JavaScript
through Kotlin/JS, so the matching and pricing rules can be shared with a web
frontend. The UI would be new work.
