# Phase 1 — Project Audit

Baseline verified 2026-08-18: `./gradlew testDebugUnitTest` → **BUILD SUCCESSFUL**,
all existing unit tests pass. Three deprecation warnings (auto-mirrored icons).

## Current architecture

```
com.potheride.app
├── core/            pure Kotlin, no Android deps, unit-tested
│   ├── format/      Formatters (৳ money, distance, time), AppLanguage
│   ├── geo/         LatLng, GeoUtils (haversine, projectionOnSegment, cumulativeKm)
│   ├── i18n/        Strings interface + English/Bangla tables (29 KB)
│   ├── matching/    RouteMatcher — anchor projection, direction check, overlap ratio
│   ├── pricing/     FareCalculator, PaymentMethod, PaymentStatus, VehicleClass
│   ├── ride/        RideState + RideStateMachine, EtaCalculator
│   └── validation/  Validators
├── data/
│   ├── local/       Room: AppDatabase (v1, schema committed), 12 entities, Daos, DemoSeeder
│   ├── model/       MatchedRide, BookingDetail, EarningsSummary, PlatformStats
│   └── repository/  RideRepository (37 KB) — the single data facade, returns RepoResult
├── location/        LocationProvider (fused), SimulatedDriveTracker
└── ui/
    ├── PotheRideViewModel (24 KB, single AndroidViewModel, one UiState)
    ├── navigation/  Routes, PotheNavHost (16 destinations, 4-tab bottom bar)
    ├── components/  Common.kt, RideComponents.kt, RouteMapView.kt (custom Canvas map)
    ├── screens/     16 screens
    └── theme/       Color, Theme (light+dark M3), Type
```

**Verdict:** the architecture is sound and worth preserving. `core/` is genuinely
testable and does the hard geometry correctly (projection onto legs, distance-along-route
ordering, round-trip detour). The single-ViewModel/single-UiState shape is coarse but
consistent. **Do not rewrite. Extend.**

## Feature report — what already exists

| Area | State |
|---|---|
| Auth | Local phone + OTP **simulated**, no backend |
| Passenger search → results → preview → confirm → status → rate | Complete, local |
| Driver publish → live → earnings | Complete, local |
| Route matching | Real geometry, boolean match + overlap ratio |
| Fare calculation | Real, with overlap discount, platform fee split |
| Ride state machine | Real, actor-gated transitions |
| Chat, notifications, ratings, saved places, trusted contacts, SOS | Local only |
| Admin screen | Exists |
| i18n | English + Bangla, full table, runtime switch |
| Map | **Custom Canvas** (`RouteMapView`), coordinate-accurate, no tiles |
| Location | Fused provider present, but ride tracking uses `SimulatedDriveTracker` |
| Dark mode | Present |

## Missing feature report (vs. the PDF's 15 phases)

| # | Missing | Effort |
|---|---|---|
| 1 | **All of Firebase** — Auth/Firestore/Storage/FCM. Everything is Room-local. | L |
| 2 | **Real map tiles** — no OSMDroid, no ORS routing, no Nominatim search | L |
| 3 | **Real GPS ride tracking** — simulator drives the marker; no foreground service | M |
| 4 | **Driver verification** — no document upload, no NID/licence/registration, no approval workflow (only a `verified` boolean) | M |
| 5 | **Face verification** — absent entirely; no ML Kit dependency | M |
| 6 | **0–100 match score** — matcher returns overlap ratio only; no departure-time or proximity weighting | S |
| 7 | **Security rules** — no Firestore/Storage rules, no role model (`Passenger/Driver/Admin`) | M |
| 8 | **Payments** — model exists; no method management screen, no payout flow | S |
| 9 | **UI to wireframe** — see gap table below | L |
| 10 | Instrumented/UI tests — only unit tests exist | M |

## Technical debt

1. `RideRepository` is 37 KB / `PotheRideViewModel` 24 KB — both are doing too much.
   Splitting is desirable but is *not* a prerequisite; do it opportunistically when a
   phase already touches the file.
2. `UiState` is one 30-field object; every screen recomposes on any change.
3. Three deprecated `Icons.Default` usages (ArrowBack, Send, Chat) → warnings.
4. `SimulatedDriveTracker` is wired into the production path, not a debug flavour.
5. No dependency injection — `AppDatabase.getInstance` and `RideRepository` are
   constructed inside the ViewModel, so the Firebase swap needs an interface seam first.
6. Room schema is v1 with no migration path exercised.

## Wireframe gap analysis

| Wireframe screen | Current screen | Difference | Required change |
|---|---|---|---|
| 01 Auth / OTP | `AuthScreen` | Wireframe: centred brand lockup, **4 separate OTP boxes**, blue `Get code` + outlined `Verify`. Current: generic form. | Rebuild layout; add `OtpBoxes` component; blue primary |
| 01 Home / mode switch | `HomeScreen` | Wireframe: segmented Passenger/Driver at very top, **black hero card**, `RECENT TRIPS` mono list, bottom nav **Home·Activity·Chat·Profile**. Current: nav is Home·Search·Activity·Profile, no black hero. | Reorder; add hero card; swap Search tab → Chat |
| 02 Passenger profile | (inside `ProfileScreen`) | No standalone create-profile step; no emergency contact / photo upload at signup | New `CreateProfileScreen` |
| 02 Driver documents | — | **Missing entirely** | New `DriverRegistrationScreen` + upload rows |
| 02 Driver status | — | **Missing entirely** | New `VerificationStatusScreen` + status badges |
| 03 Face capture / result | — | **Missing entirely** | New `FaceVerificationScreen` (CameraX + ML Kit) |
| 04 Search | `PassengerSearchScreen` | Wireframe adds `SUGGESTED — DHAKA LANDMARKS` list; blue CTA | Add suggestions section |
| 04 Results | `PassengerResultsScreen` | Wireframe: filter chip row, green **`92% overlap`** badge, `View route →` link | Add chips + badge + link affordance |
| 05 Route preview | `RoutePreviewScreen` + `RouteMapView` | Wireframe: **full-bleed tiles** with draggable bottom sheet, blue shared stretch, amber live dot | OSMDroid map + `BottomSheetScaffold` |
| 06 Booking confirm | `BookingConfirmScreen` | Close. Needs green `Overlap discount −৳7` row, dashed divider, `PAY WITH` chips | Restyle |
| 06 Payment methods | — | **Missing entirely** | New `PaymentMethodsScreen` |
| 06 Earnings | `DriverEarningsScreen` | Needs `TODAY` card + `Withdraw earnings` CTA | Restyle + payout stub |
| 07 Live tracking (passenger) | `RideStatusScreen` | Wireframe is map-first with sheet: `Driver en route` badge, Call/Message, **red SOS** | Rebuild as map + sheet |
| 08 Publish route | `DriverCreateTripScreen` | Wireframe: vehicle chips, **`− 3 +` stepper**, departure field | Add `Stepper`; restyle |
| 09 Live tracking (driver) | `DriverLiveScreen` | Wireframe: map-first, `2 seats left`, request card with blue Accept / red Decline | Rebuild as map + sheet |

## Ordering decision

Firebase (Phase 3) is the deepest change and every later phase depends on where the
data lives. But the repository has no interface seam yet. Therefore:

1. **Level 2 — design system first.** It is independent, unblocks every screen, and is
   the change the user is most likely to see. (Also delivers Phase 12.)
2. **Level 3 — extract a repository interface, then add the Firebase implementation
   behind it**, keeping Room as the offline cache rather than deleting it. This satisfies
   Phase 3 without the "rewrite everything" the PDF forbids.
3. Levels 4+ follow the table in `00_PROGRESS.md`.
