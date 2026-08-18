# Level 12 — Security

`firebase/firestore.rules` and `firebase/storage.rules` replace the open placeholder
(`allow read, write: if true`) that shipped with the emulator scaffolding at Level 3b.

## Role model

`users/{uid}.role` is one of `passenger`, `driver`, `admin` — written by
`FirestoreMappers.ROLE_*` at account creation (`passenger` by default) and when
`becomeDriver` runs (`driver`). There are no custom Firebase Auth claims: every role
check in the rules is a document read of the caller's own `users/{uid}` document via
`roleOf(uid())`.

**The single most important rule in the file**: a user may update their own `users/{uid}`
document, but never the `role` field on it — see `unchanged('role')` in the `users` match
block. Without that one line, any signed-in passenger could write `role: "admin"` onto
their own document and grant themselves full platform access. This is checked with
`request.resource.data.diff(resource.data).affectedKeys()` rather than value comparison,
because that also catches the field being *dropped* — a write that removes `role`
entirely would otherwise pass a naive `==` check and leave a document with no role,
which every other rule then has to handle as a special case.

## Per-collection summary

| Collection | Read | Write |
|---|---|---|
| `users/{uid}` | owner, or admin | owner (not `role`), or admin |
| `drivers/{id}` | signed in (drivers are public — a passenger must see who they're booking) | the driver via their `userId`, or admin (`verified` only admin) |
| `routes/{id}` | signed in | the owning driver; `availableSeats` is expected to move via the seat-accounting transaction, not a direct rules-level write |
| `rideRequests/{id}` | the passenger, or the driver named on `driverId` | passenger creates; driver and passenger may each update only the fields their role is allowed to touch (status transitions are *not* re-validated by rules — see "what these rules do not protect" below) |
| `liveLocations/{tripId}` | the passenger who booked that trip, or the driver | only the owning driver |
| `payments/{id}` | the passenger or driver on the linked booking | nobody, directly — payments are written by app logic on behalf of a completed ride; a direct client write of `status: COMPLETED` is exactly the kind of self-service refund/payout fraud these rules exist to block |
| `notifications/{id}` | the addressed user | not writable by clients — app logic only |
| `safetyEvents/{id}` | admin, or the user who raised it | signed-in users may create (raising an SOS must work even mid-incident), nobody may edit or delete except admin resolving |

Full detail, including every helper function, is in the rules files themselves — they are
commented at the point of each decision rather than summarised a second time here, so the
two cannot drift apart.

## Storage

`drivers/{uid}/documents/*` and `users/{uid}/face/*` (identity documents, face captures)
are readable **only by the owning uid** — never world-readable, never readable by another
passenger or driver. `users/{uid}/profile.*` is the one exception: profile photos are
shown on booking cards to the other party in a ride, so they are readable by any signed-in
user.

Every write path enforces a max size (4–8 MB) and an image content type, to stop a
compromised or buggy client from filling the bucket with arbitrary files.

## What these rules deliberately do NOT protect

Being explicit about this matters more than the rules themselves — a false sense of
coverage is worse than a known gap.

1. **Ride state transition legality is not re-validated in Firestore rules.**
   `RideStateMachine.validate` (in `core/ride`) is the authority on which transitions are
   legal, and it runs in `FirebaseRideDataSource` before any write. But Firestore rules
   evaluate a raw document write, not an app-level state machine call — a client could in
   principle write `status: "COMPLETED"` directly onto a `REQUESTED` booking if the rules
   permitted the field at all. The rules restrict *who* may touch `status` (the involved
   driver or passenger only) but do not encode the full transition graph. Encoding it
   would require duplicating `RideStateMachine` in the rules language, which is worse for
   maintenance than the two ever agreeing to drift. **Mitigation for production**: move
   transitions behind a Cloud Function that re-runs `RideStateMachine.validate`
   server-side, and lock the client out of writing `status` directly at all.

2. **Seat accounting's atomicity is a Firestore transaction (`SeatAccounting.kt`), not a
   rule.** The rules allow the owning driver to update `availableSeats`, but do not
   enforce that it only moves via the transaction. A malicious client with the driver's
   own credentials could still write an arbitrary seat count to their own route. This is a
   lower-severity gap than #1 — the worst case is a driver oversells or undersells their
   own vehicle, not a cross-account exploit — but it is a gap.

3. **No rate limiting.** Firestore rules have no concept of "too many writes per second."
   A compromised driver account could, in principle, spam `liveLocations` writes far
   faster than the app's own `LocationWritePolicy` throttle intends. App Check (Firebase's
   anti-abuse product) is the standard mitigation and is not configured here.

4. **Payment status is trusted from whatever wrote it**, because nothing writes it from
   the client at all in this build — `payForBooking` runs entirely server-side (inside
   `FirebaseRideDataSource`, called from the ViewModel, never directly from a rules-gated
   client write). This is fine for cash and the "PENDING" gateway placeholder, but the
   moment a real payment gateway is integrated, its webhook confirmation must be the only
   thing allowed to mark a payment `COMPLETED` — never the client, even indirectly.

5. **Admin role revocation is not audited.** An admin can demote another admin (or
   themselves) with no log of who did it or when. Firestore audit logging (a Cloud
   project-level feature, not a rules-file concern) is the fix, and is not configured here.

6. **A driver's licence number is visible to every signed-in account, not just admins.**
   The `drivers/{driverId}` document is readable by any signed-in user because a passenger
   choosing a ride needs to see the driver's rating, trip count and vehicle — but
   `licenseNumber` lives on that same document, so it comes along for the ride. Splitting
   licence number into a separate, admin-only-readable document (or a Storage-backed
   record alongside the licence photo, which is already locked down) is the fix, and was
   not done in this pass. Referenced directly in a comment in `firestore.rules`.

7. **These rules have not been tested against the Firestore emulator's rules test
   harness** (`@firebase/rules-unit-testing`). They are correct by careful reading against
   `FirestoreSchema.kt`, but "correct by reading" and "verified by test" are different
   claims — Level 13 should add a rules test suite before this goes anywhere near
   production data.
