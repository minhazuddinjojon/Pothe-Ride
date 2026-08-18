# From single-device app to a real service

## The constraint you need to know about first

Every user, trip, booking and payment lives in SQLite on the phone. The app is
fully functional, but it is functional *alone*: a driver publishing a route on one
phone and a passenger searching on another **will never see each other**. There is
no network call anywhere in the codebase.

That is fine for development, for a demo, and for a course submission. It is not
something you can put on the Play Store as a ride-sharing service, because the core
promise of the product — matching two strangers — cannot happen.

A backend is not an enhancement here. It is the remaining half of the product.

## Why the migration is small

Every rule that decides *what happens* is already isolated:

- `core/` — matching, pricing, ETA, the ride state machine, validation. Pure Kotlin,
  no Android, no database. **This code moves to the server unchanged.** It is already
  covered by 107 unit tests, which move with it.
- `data/repository/RideRepository.kt` — the only type the UI knows about. Every
  screen calls this and nothing else.

So the migration is: keep `core/` on both sides, and swap the body of
`RideRepository` from DAO calls to HTTP calls. No screen changes. No ViewModel
changes.

## Suggested shape

```
POST /auth/request-otp     { phone }
POST /auth/verify          { phone, code }  -> { token, user }
POST /trips                 publish a route
GET  /trips/search?...      runs RouteMatcher server-side
POST /bookings              request a seat
POST /bookings/{id}/transition  { to, actor }   <- RideStateMachine validates
POST /payments/{id}/webhook     bKash / Nagad callback
WS   /trips/{id}/location       live driver position
```

Server: Kotlin + Ktor or Spring Boot reuses `core/` directly. Postgres with PostGIS
turns the candidate-trip query into a real spatial index instead of the current
`LIMIT 100` scan, which is the one part of the matching pipeline that will not scale.

## Things that are stubbed and must be real before launch

| Area | Today | Needed |
|---|---|---|
| OTP | Any 4 digits pass. `verifyAndSignIn` never checks the code. | An SMS provider, and server-side verification. |
| Payments | Cash settles locally. bKash/Nagad/Rocket/card are written as `PENDING` and never confirmed. | Merchant accounts and webhook handlers calling `confirmGatewayPayment`. |
| Masked calling | The button explains the concept; no call is placed. | A telephony provider issuing proxy numbers. |
| Push notifications | Rows in a local table, shown in-app. | FCM, so a driver hears about a request with the app closed. |
| Admin console | Reachable from any profile. | A server-side role claim. |
| Driver verification | An admin toggles a boolean. | Licence and NID document upload and review. |
| Live location | Written to the local trips table. | WebSocket fan-out to the passengers on that trip. |

## Order to do it in

1. Auth with real OTP — everything else needs a user identity that survives reinstall.
2. Trips and search — the moment two devices can see each other, you have a product.
3. Bookings and the state machine — reuse `RideStateMachine` verbatim on the server.
4. Live location over WebSocket.
5. Payments last. It is the most regulated piece and the least useful before the
   first four work.
