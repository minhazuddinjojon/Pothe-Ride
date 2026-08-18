# Pothe Ride

Route-based ride-sharing for Bangladesh. Drivers publish a journey they are already
making; passengers may request a seat only if their pickup **and** drop-off sit on
that route, in the direction the driver is travelling.

## Running it

1. Open the `PotheRide/` folder in Android Studio (Hedgehog or newer).
2. Let Gradle sync. The wrapper is committed, so no separate Gradle install is needed.
3. Run on an emulator or device, **minSdk 26 (Android 8.0)**.

No API keys, no backend, no signing config required to run. On first launch the app
seeds three published routes across Dhaka, verified drivers with rating histories,
and one pending seat request, so every screen has real data immediately.

```bash
./gradlew test          # 107 unit tests covering the matching and pricing engine
./gradlew assembleDebug
```

### Try the core flow in two minutes

1. Sign in with any Bangladeshi number (`01712345678`) and any 4-digit code.
2. Stay in **Passenger** mode, tap **Search rides**.
3. Pickup `Mirpur-10`, drop-off `Tongi Station`, search.
4. You get real matches with genuine overlap percentages and fares. Open one, look
   at the fare breakdown, request the seat.
5. Switch to **Driver** mode → **Publish route** → publish anything.
6. On the live screen, tap **Simulate driving** to watch the vehicle move along the
   route, and accept the pending request.

## How it is put together

```
core/          Pure Kotlin. No Android imports at all.
  geo/         Haversine, point-to-segment projection, polyline maths
  matching/    RouteMatcher — the product's core algorithm
  pricing/     FareCalculator, Taka (money as whole poisha)
  ride/        RideStateMachine, EtaCalculator
  validation/  Bangladeshi phone/plate/seat rules
  i18n/        Every user-visible string, English and Bangla
data/          Room entities, DAOs, and RideRepository
location/      Fused GPS, plus an explicit drive simulator for the emulator
ui/            Compose theme, components, screens, ViewModel, navigation
```

Two decisions shape everything else:

**`core/` has no Android dependency.** It compiles and runs on a plain JVM, so the
matching engine, the pricing rules and the ride lifecycle are covered by fast unit
tests, and the same code can move to a server without modification.

**`RideRepository` is the only thing the UI knows about.** Swapping its body from
Room to HTTP is the entire backend migration — no screen or ViewModel changes.

## What is real and what is not

| Area | Status |
|---|---|
| Route matching | **Real.** Direction-aware, ordered along the route, detour-bounded. 19 unit tests. |
| Fare calculation | **Real.** Per-vehicle rates, distance and time, overlap discount, night surcharge, minimum fare, platform commission. Money held as integer poisha. |
| Ride lifecycle | **Real.** Explicit state machine with per-actor permissions and seat accounting. |
| Database | **Real** SQLite via Room, 13 tables, foreign keys enforced. |
| Bengali / English | **Real**, switches instantly. A missing translation is a compile error. |
| Map | Coordinate-accurate custom canvas, drawn to scale. Not Google Maps — see `docs/MAPS.md`. |
| Road geometry | Straight lines between waypoints. Needs a Directions API — `docs/MAPS.md`. |
| Live tracking | Real fused GPS, plus a clearly-labelled simulator because emulators emit no fix. |
| Phone OTP | **Simulated.** Any 4 digits pass. The screen says so. |
| Payments | Cash settles locally. bKash / Nagad / Rocket / card are recorded **pending** — no gateway is connected. |
| Masked calling | UI affordance only; no telephony provider. |
| Multi-device | **No.** Everything is on one phone — see below. |

## The two things standing between this and a launch

**1. There is no backend.** All data is local, so a driver on one phone and a
passenger on another cannot see each other. The app works, but it works alone. That
is fine for development and demos; it is not a shippable ride-sharing service.
`docs/BACKEND.md` sets out the migration, which is smaller than it sounds because
all the rules already live in `core/`.

**2. This is Android only.** It is Kotlin, Compose, Room and Play Services. It will
not ship to the App Store, and there is no web build. The original brief asked for
one shared codebase across Android and iOS plus responsive web; this delivers the
Android half. `docs/RELEASE.md` compares Compose Multiplatform (which reuses `core/`
and its tests intact) against a Flutter or React Native rewrite.

Both are real work, not configuration. Better to know now.

## Design

Near-black primary actions on white surfaces, with colour reserved for meaning:
green for the route, amber for live position and pending states, red for alerts.
That high-contrast structure is the convention transport apps converge on because it
stays legible on a phone held at arm's length in daylight.

The identity is Pothe Ride's own — paddy green, CNG yellow, its own typography and
its own route-thread motif. No other company's marks, fonts, icons or assets are
used anywhere in the project.

## Docs

- `docs/MAPS.md` — switching to Google Maps and real road geometry
- `docs/BACKEND.md` — the server migration, and what is stubbed
- `docs/RELEASE.md` — Play Store signing and submission; the honest iOS position
- `docs/db_schema.mmd` — ER diagram
