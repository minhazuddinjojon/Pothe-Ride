# Wireframe Spec — transcribed from `Pothe_Ride_Wireframes.pptx`

Ten slides. Slide 1 is the cover (`WIREFRAMES / Pothe Ride`, dark ink background,
green accent). Slides 2–10 are sections 01–09. Each slide's PPTX content is stored
as four quadrant images; reassembled they form one 3840×2160 board.

## Visual language (observed across all boards)

| Token | Value observed | Maps to existing theme |
|---|---|---|
| Board background | warm off-white `#EFEDE7`-ish | `Paper` |
| Phone surface | pure white | `Snow` |
| Heading text | near-black, bold, tight tracking | `Ink` |
| Section eyebrow | small caps, letterspaced, **green** | `RouteGreen` |
| Body/meta text | **monospace**, grey, small | new: needs a mono type role |
| Primary CTA | **full-width blue pill**, white bold label | ⚠️ theme primary is `Ink`, wireframe is blue |
| Secondary CTA | white pill, thin dark outline | ok |
| Destructive CTA | white pill, **red** outline + red label (`SOS`, `Decline`) | `AlertRed` |
| Chips (selected) | black pill, white label (`Passenger`, `Cash`, `CNG`) | `Ink` |
| Chips (unselected) | white pill, thin outline | ok |
| Status badges | outlined pill: amber `Pending review`/`En route`, green `Approved`/`92% overlap` | `SignalAmber` / `RouteGreen` |
| Cards | white, generous radius (~14–20dp), hairline outline, soft shadow | `PotheShapes.medium/large` |
| Map canvas | warm grey-beige fill, pale grey street lines | new |
| Route polyline | **deep green**, thick | `RouteGreen` |
| Shared stretch | **light blue** overlay on the green | `InfoBlue` (lighter) |
| Live driver marker | **amber filled dot** | `SignalAmber` |
| Origin marker | green filled dot · Destination marker | red filled dot | `RouteGreen`/`AlertRed` |
| Phone chrome | status bar `9:41` left, 4 dots right, notch pill top | decorative only |

**Key deltas from current theme:** primary CTA colour (blue, not ink), a monospace
metadata role, and a map canvas palette. The "modern 3D" direction the user asked for
is layered on top: elevated cards with soft multi-layer shadows, depth on the map
sheet, and pressed-state scale on buttons — without departing from these tokens.

---

## 01 · ONBOARDING — "Sign in & home"
> Phone OTP login, then a mode switch between passenger and driver

**Screen A — Auth / OTP**
- Centred brand block: `Pothe Ride` (green, bold) + mono subtitle `route-based ride-sharing, dhaka`
- Label `MOBILE NUMBER` (mono, caps) → outlined field split `+880` | `1712-345678`
- Primary blue pill: **Get code**
- Label `ENTER 4-DIGIT CODE` → **four** separate square OTP boxes
- Secondary outlined pill: **Verify**

**Screen B — Home / mode switch**
- Top: full-width segmented control, black pill on `Passenger` side (Passenger | Driver)
- `Hi, Rahim` heading
- **Black** hero card: `Search a ride` + mono sub `pickup + drop-off, find a match`
- Label `RECENT TRIPS` → list of outlined cards, mono text `yesterday · ৳45 · cash`
- Bottom nav, 4 items: Home · Activity · Chat · Profile

> ⚠️ Current app's bottom nav is Home · Search · Activity · Profile. Wireframe has **Chat**.

## 02 · REGISTRATION & VERIFICATION — "Sign-up for passenger & driver"
**A — Passenger profile:** `Create your profile`; fields FULL NAME, EMAIL (OPTIONAL),
EMERGENCY CONTACT, PROFILE PHOTO (upload placeholder box); blue pill **Continue**.

**B — Driver documents:** `Driver registration`; FULL NAME; NATIONAL ID (NID) →
`Upload front & back` row with circular `+` button; DRIVING LICENCE → `Upload licence` + `+`;
VEHICLE → `CNG — DHK-1234`; blue pill **Submit for review**.

**C — Driver status:** `Verification status`; three outlined cards each with a status
badge above a mono caption: amber `Pending review`/NID document, green `Approved`/Driving
licence, amber `Pending review`/Vehicle photo. Mono note `Usually reviewed within 24 hours.`
Outlined pill **Re-upload a document**.

## 03 · IDENTITY — "Facial ID verification"
> Matched against the uploaded NID photo, with a liveness check

**A — Capture:** `Face verification` + mono sub. Large **near-black** camera panel with a
dashed **green oval** guide and a simple face glyph. Mono caption `Center your face in the
frame`. Blue pill **Capture**.

**B — Result:** `Verification result`; large empty preview card with mono
`98% confidence against NID photo`; smaller card `Face ID · Passed · just now`; blue pill **Continue**.

## 04 · PASSENGER — "Search & matches"
> Overlap-ranked matches against published driver routes

**A — Search:** `Where to?`; two outlined fields `Pickup — Mirpur-10`, `Drop-off — Tongi Station`;
label `SUGGESTED — DHAKA LANDMARKS` → four empty outlined suggestion rows; blue pill **Search rides**.

**B — Results:** `3 matches`; a row of three filter chips; result cards each carrying a
green outlined badge **`92% overlap`**, mono line `CNG · 2 seats left · leaves 9:50am`, and a
right-aligned mono link `View route →`.

## 05 · MAP — "Route preview, full-screen map"
> Green route, blue shared stretch, amber live driver position — Dhaka streets

Three layout variants of the same screen (pick one, build the others as the sheet's
collapsed/expanded states):
1. **Full-screen map + bottom sheet** — map fills the phone; sheet has a drag handle,
   green `92% overlap` badge, mono `2 seats`, blue pill **Request seat**.
2. **Map top, details bottom** — map in a rounded card in the top ~55%; below it
   `Mirpur-10 → Tongi Station`, a detail card, mono `CNG · 92% overlap · 2 seats`,
   blue pill **Request seat**.
3. **Draggable card** — map full-bleed, small collapsed sheet with handle, mono
   `expand ↑` hint, blue pill **Request seat**.

Map content: green polyline Mirpur-10 → Tongi Station, labelled endpoints, a **light blue
overlay** on the shared stretch, and an **amber dot** at the live driver position.

**Decision:** implement variant 1 as the primary with a draggable `BottomSheetScaffold`
that collapses to variant 3.

## 06 · PAYMENT — "Confirm booking & pay"
> Cash, bKash, Nagad, Rocket — digital methods settle pending until a gateway is connected

**A — Booking confirm:** `Confirm booking`; route summary card with mono
`Mirpur-10 → Tongi Station`; label `FARE BREAKDOWN` → itemised rows, with
`Overlap discount` shown in **green** on the left and `−৳7` in green on the right, a
dashed divider, then the total; label `PAY WITH` → four chips, `Cash` selected (black);
blue pill **Confirm booking**.

**B — Payment methods:** `Payment methods`; four outlined rows with right-aligned mono
status — green outlined `Default`, `Linked`, `Not linked`, `Not linked`;
outlined pill **+ Add payment method**.

**C — Earnings:** `Earnings`; a `TODAY` summary card plus two more cards;
blue pill **Withdraw earnings**.

## 07 · LIVE LOCATION — "Live tracking — passenger"
> Real fused GPS position along the published route, with call, message and SOS

Same three layout variants. Sheet contents:
- Amber outlined badge `Driver en route` (compact variant: `En route`)
- Mono line `Kamal Hossain · CNG · DHK-1234`
- Two side-by-side outlined pills: **Call** | **Message**
- Full-width **red outlined** pill: **SOS**
- Variant 2 headline: `Driver arriving · ETA 6 min`

## 08 · DRIVER — "Publish a route"
> Drivers publish a journey they are already making, not a custom trip for hire

Single screen `Publish a route`: outlined field `Origin — Mirpur-10`; outlined field
`Destination — Tongi Station`; label `VEHICLE` → three chips, `CNG` selected (black);
label `SEATS AVAILABLE` → outlined stepper row `−  3  +`; label `DEPARTURE` → outlined
field `9:50 AM, today`; blue pill **Publish route**.

## 09 · LIVE LOCATION — "Live tracking — driver"
> Driver's live GPS position, plus incoming seat requests along the route

Same three map variants. Additions over the passenger view:
- A **blue dot** on the route marking a waiting passenger's boarding point
- Sheet headline `2 seats left`
- Request card: passenger name `Farida Yasmin` + mono `boards near Kazipara · 1 seat`
- Two buttons: blue filled **Accept** | red outlined **Decline**
