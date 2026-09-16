# VIT Chennai campus navigation: exact implementation plan

Prepared by Codex on 2026-09-16. **Status: analysis and implementation plan; no runtime fix or device verification performed.** Expanded on the user's request to cover all VIT Chennai locations. The AB1 report (approximately 178 m spoken, 728100 m left and a 223 m next turn on screen) is the first regression case, not the scope boundary. The filename is retained to preserve existing references.

## 1. Outcome required

Apply the repair to every supported VIT Chennai destination and every accepted campus origin, including voice and destination-menu flows. Build and verify a complete location inventory before claiming campus-wide coverage. The existing 11-POI list is an initial inventory, not an exhaustive campus directory. Routes terminate at verified walking entrances; room/floor destinations are resolved to a building entrance with an explicit indoor-navigation limitation unless separately mapped and localized.

The app must resolve the intended AB1 destination, acquire an acceptable actual location, validate a real walking route, and calculate remaining distance along that route. UI and speech must use the same validated route/session. If the location or route is unreliable, display a clear unavailable state instead of a plausible-looking number.

Do not hardcode 178 m, divide 728100 by an arbitrary factor, clamp the distance to a campus-sized maximum, or use the first-step distance as the total. The correct value must be established from recorded inputs. Standing inside a building does not imply the GPS fix is precise or that the map contains a route from the room to its entrance.

## 2. Verified baseline and analysis

Implementation source inspected at main commit `b49e6c33e4065ab8061809e1a1f9840783bd0bef`. The current worktree is detached at `9a03c13`, from communication history, and lacks Android source. Read-only inspection used `git show main:<path>`. Prepare a separate implementation branch/worktree from the implementation revision before execution. Preserve untracked datasets, models, runs and local database files.

Production Kotlin paths in this document are relative to `android/app/src/main/java/dev/navisense/`. Test paths are relative to `android/app/src/test/java/dev/navisense/`.

| Evidence | Finding | Interpretation |
|---|---|---|
| User screenshot | AB1 destination, 728100 m total remaining, 223 m next-turn distance | Confirmed display defect for the reported local use; 728100 m = 728.1 km |
| User report | About 178 m spoken, physically inside AB1 | Reproduce with exact route/request and spoken phrase; not independently measured |
| `PedestrianNavigationEngine.startRoute` | Speaks first step's `distanceMeters` | The 178 m could be a step length, not total distance; not proven without a trace |
| `PedestrianNavigationEngine.onLocationUpdated` | Computes remaining from overview geometry or concatenated step geometry | Displayed total comes from a different computation than the opening speech |
| `MainActivity.onNavigationStatusUpdated` | Converts remaining float to int and appends `m left` | No visible multiplication by 1000 in this UI path; formatting alone is not an established root cause |
| `GoogleRoutesService` | Does not validate overview length against provider distance or resolved endpoints | An inconsistent route can reach the progress calculator |
| `GoogleRoutesParsingTest.testPolylineDecoding` | Checks decoded count and only the first coordinate | Corruption in later points is not covered by this test |
| `MainActivity.onDestinationReceived` | Uses cached `lastLocation` or `lastKnownLocation`; latter stores only coordinate | Age/accuracy are not retained at route preparation |
| `PedestrianNavigationEngine.onLocationUpdated` | Accepts accuracy parameter but does not gate progress/arrival with it | An inaccurate indoor fix can drive guidance |
| Both navigation progress callers | Pass maneuver/step index as preferred polyline segment index | These indices refer to different structures; continuity hint can be incorrect |
| `SessionCoordinator.onOffRouteDetected` | Callback contains only a comment | “Recalculating path” is spoken without an actual reroute here |

Earlier fixed-coordinate/mock-route fallbacks have already been removed from the inspected service. Preserve that work; do not repeat the old diagnosis as the current root cause.

### Root-cause decision tree

1. If the raw provider geometry already describes an unrelated/huge route, inspect origin, destination resolution and provider response.
2. If raw geometry is valid but the decoded points differ from an independent decoder, fix decoding/precision/coordinate order.
3. If points are correct but a known position yields an inflated remaining length, fix progress matching and segment summation.
4. If the computed status is correct but the screen differs, inspect stale session updates and UI formatting.
5. If only live fixes are implausible, fix acquisition/freshness/accuracy handling; do not alter correct route geometry.

These possibilities are hypotheses until the same input reproduces the error. No specific decoder, unit or geocoder defect has yet been proven to cause 728100.

## 3. Terminology and units

| Term | Exact meaning |
|---|---|
| Coordinate | Named latitude/longitude fields in degrees; validate ranges and finiteness |
| Fix age | Current elapsed-realtime minus location elapsed-realtime, in milliseconds |
| Accuracy radius | Android's horizontal uncertainty estimate in meters; not proof of a specific room/walkway |
| Provider total | Distance returned by the routing provider for its route, converted once to meters |
| Polyline precision | Encoding scale, typically 1e5 or 1e6; select from the provider contract, never by trial until output looks plausible |
| Geometry length | Sum of distances between successive decoded route points |
| Chainage | Cumulative distance from route start to a position on its geometry |
| Segment index | Index of an adjacent pair of polyline points |
| Maneuver index | Index of a navigation instruction; one maneuver may span many geometry segments |
| Cross-track distance | Distance from the observed fix to a candidate route segment |
| Remaining distance | Route length minus matched chainage, not distance as the crow flies |
| Route identity | Provider/map version, request ID and route ID tying calculations to one accepted route |
| Session generation | Existing coordinator identity that invalidates late work after Stop/pause/mode changes |

Keep calculations in Double meters. Round only at the display/speech boundary. Unavailable distance is an explicit state, not zero, NaN rendered as an integer, or a cached number without a stale label.

## 4. Exact implementation sequence

### Step 01 — Prepare the correct working source

Owner: Rishav for navigation integration; Samik reviews obstacle-assistance regressions.

1. Inspect `git status --short`, `git worktree list`, and the implementation branch revision.
2. Create an unused `codex/gps-ab1-distance-repair` branch/worktree from the verified implementation revision. If that name already exists, inspect it and continue the existing work instead of resetting it.
3. Read current navigation code/tests and preserve any later teammate fixes. Do not switch this communication-based worktree over untracked model/data directories.
4. Record current APK identity and source/model/map hashes. Reserve the connected phone before installing/debugging.

Deliverable: implementation worktree and baseline inventory. No source changes to camera models or ultrasonic policy are required for this defect.

### Step 02 — Capture one complete failing request

Files: `app/MainActivity.kt`, `navigation/maps/GoogleRoutesService.kt`, `navigation/maps/PedestrianNavigationEngine.kt`; use existing logging with a bounded debug-only trace.

1. Allocate a request ID before location lookup. Carry it through geocoding, routing, progress and rendering.
2. Capture destination text, matched POI/place identity and locality, resolved coordinates, route mode and provider.
3. Capture origin latitude/longitude, provider, fix age, accuracy, elapsed-realtime timestamp and mock/test status.
4. Capture response status, provider total, first-step distance, all step lengths, encoded overview geometry and encoding precision. Store exact personal-location/raw response artifacts locally, not in routine Git commits; redact credentials.
5. After decoding, record point count, first/last point, bounding extent, geometry length, largest segment and its index.
6. At the first incorrect display, record current fix, matched segment/fraction, cross-track distance, computed remaining distance, current maneuver index, request/session/route IDs and displayed value.
7. Reproduce the same AB1 query while stationary. Record exact spoken text; “head forward 178 m” and “178 m total” are different assertions.

Exit: one trace connects input to the 728100 output, or reproduction remains explicitly pending. A synthetic case must not be labelled the captured user failure.

### Step 03 — Turn the captured defect into a failing test

Existing tests: `GoogleRoutesParsingTest.kt`, `PedestrianNavigationEngineTest.kt`, `navigation/maps/NavigationDistanceRemediationTest.kt`.

1. Preserve a redacted route response/coordinate fixture under test resources where appropriate. Keep actual user location private unless explicitly authorized for sharing; a geometry-preserving synthetic translation may be used if labelled as derived.
2. Decode using an independent reference implementation. Compare all points and segment lengths, not just the first point.
3. Run the same origin/current fix through `PedestrianProgressCalculator` and `PedestrianNavigationEngine` with a fake clock.
4. Assert the invariant that the route/status should satisfy, such as rejection of inconsistent geometry, rather than asserting a guessed 178 m output.
5. Ensure the new test fails on baseline and record failure text before fixing.

Exit: a regression fails for the actual cause. If the exact trace is unavailable, add independently specified bad-geometry tests while keeping exact reproduction open.

### Step 04 — Make decoding strict and independently tested

File: `navigation/maps/GoogleRoutesService.kt`, function `decodePolyline`; extract `PolylineDecoder.kt` only if it helps test parsing without Android dependencies.

1. Make encoding precision explicit per response/provider. For the current OSRM request, explicitly request the supported geometry encoding and decode that same encoding.
2. Check incomplete coordinate pairs, unterminated groups, illegal characters, excessive shifts and accumulator overflow. Return a typed parsing failure.
3. Preserve signed-delta decoding and named latitude/longitude order. Do not infer a factor of ten from the final distance.
4. Validate finite/ranged decoded coordinates. Remove consecutive identical points only as a documented geometry normalization, preserving instruction mapping.
5. Expand tests to all coordinates of the standard fixture, negative deltas, both hemispheres, repeated points, truncated strings and long malformed groups.
6. Add a fixture with deliberately wrong precision. Assert rejection by route validation rather than selecting whichever interpretation looks shorter.

Exit: deterministic decoder with explicit format and no unchecked malformed input entering navigation. A passing decoder does not establish that the provider selected the right destination.

### Step 05 — Validate complete routes before starting navigation

Add proposed `navigation/maps/RouteValidator.kt`; use existing route models with minimal metadata additions in `navigation/maps/models/NavigationModels.kt`.

1. Validate HTTP success/provider status and mandatory route fields before model construction. Do not default missing distances to zero or missing step locations to arbitrary origin/destination coordinates.
2. Require usable connected geometry, valid endpoints, non-negative finite distances and coherent ordered steps.
3. Compare requested/resolved endpoints to routed endpoints using documented provider snapping/entrance tolerances. A large unexplained offset is failure, not a new destination.
4. Calculate geometric length and compare it with provider total. For the local initial implementation, use a configurable review threshold such as `abs(geometryLength - providerTotal) > max(25 m, 0.15 * providerTotal)` to reject inconsistent input. This is a proposed diagnostic policy to validate against actual encoding/simplification, not a universal routing law.
5. Cross-check summed step distances and overview geometry. Missing optional step geometry may be handled only through a validated overview/maneuver mapping; do not synthesize false route legs.
6. Return typed failures: malformed geometry, inconsistent length, invalid endpoint, unknown destination, no route or unavailable provider.
7. Call the validator before `coordinator.startOutdoorWalking`. Invalid data must never enter an active numeric countdown.

Important: a long route is not automatically invalid. Reject inconsistency with this request and its geometry, not any distance over a hardcoded campus maximum. A legitimate distant destination can have a long route.

### Step 06 — Preserve and qualify location evidence

Files: `app/MainActivity.kt`, `map/LocationTracker.kt`, coordinator location entry point. Add a small `NavigationLocationFix` model/policy only where needed.

1. Replace coordinate-only cached origin with coordinate, accuracy, elapsed-realtime timestamp and provider. Retain source session/request identity on callbacks.
2. Validate cached last location. If absent/stale/inaccurate, request a fresh fix with a bounded timeout instead of reusing it indefinitely.
3. Proposed preparation defaults: age <=10 seconds, accuracy <=20 m, acquisition wait <=20 seconds. Keep these configurable and measure on the qualification phone; they do not justify a 4–6 m actionable cue at 20 m uncertainty.
4. Introduce a stricter maneuver/arrival quality policy. If uncertainty cannot distinguish the relevant waypoint/paths, suppress precise turns/arrival and explain degraded location.
5. Permission requests return immediately. After grant, resume only the still-current request; denial and location-disabled states have accessible recovery actions.
6. Evaluate stale location on a timer even when no new fixes arrive. Do not count receipt of an old fix as new measurement freshness.
7. If GPS is inadequate inside AB1, report location unavailable/imprecise. Do not claim room-level guidance; preserve independent obstacle assistance according to its own lifecycle policy.

Exit: valid route progress uses accepted current evidence. No assumed entrance/origin or automatic “already there” decision from a noisy indoor fix.

### Step 07 — Resolve AB1 through the right destination path

Files: MainActivity voice routing, local POI lookup, GoogleRoutesService destination result.

1. Trace both `VoiceCommand.NavigateToPoi` and `VoiceCommand.NavigateTo` handlers against the actual parsed input. Button and voice routes must resolve the same canonical campus destination identity.
2. Match explicit AB1 aliases to the verified campus POI; do not send a known campus alias through unrestricted first-result geocoding.
3. Review the POI coordinate and walkable entrance against campus map evidence. Inside-building position and entrance position are distinct concepts.
4. For other place names, retain locality/identity and present a choice when ambiguous. Never merely label the first remote result with the user's requested name.
5. Route offline only where a connected pedestrian graph covers origin and destination. Unknown/out-of-coverage queries fail clearly without a default campus target.
6. Verify online provider pedestrian profile. OSRM profile behavior depends on prepared server data; the string `/walking` alone is not evidence of a pedestrian deployment.

Exit: the destination can be identified and independently checked before investigating distance arithmetic.

### Step 08 — Repair route progress and index semantics

Files: `navigation/maps/PedestrianProgressCalculator.kt`, `PedestrianNavigationEngine.kt`, `map/MapNavigationCoordinator.kt`.

1. Precompute validated segment lengths `L[i]` and cumulative chainage `C[i]`. Do not rebuild all route geometry on every GPS update unnecessarily.
2. Return a structured match: segment index, fraction t, chainage, cross-track distance and validity. The calculator must be able to say no valid match instead of always returning a number.
3. Track previous matched segment explicitly. Replace `preferredStartIndex = currentStepIndex/currentManeuverIndex` with actual segment continuity state.
4. Initially search near previous progress. Wider reacquisition is allowed only when supported by good location evidence; avoid jumping across parallel paths/hairpins.
5. Compute `chainage = C[i] + t * L[i]` and `remaining = totalGeometryLength - chainage`. Enforce numeric bounds for valid on-route progress, not by hiding invalid input.
6. Determine distance to the next maneuver from its mapped chainage. Repeated coordinates/loops need ordered mapping, not global nearest-point lookup.
7. Do not automatically add direct distance from an off-route fix to the route as if that connector were walkable. Mark off-route and request a legitimate reroute or explicit retry.
8. Use uncertainty-aware jitter control; real reverse movement may increase distance. Do not force countdown to decrease regardless of movement.
9. On advancing a step, calculate status for the new step before publishing it; avoid a new instruction paired with the previous step's distance.

Exit: curved routes, backtracking, loops and waypoint changes produce coherent remaining and next-turn values.

### Step 09 — Make UI and speech consume one navigation snapshot

Files: `NavigationModels.kt`, `app/MainActivity.kt`, `app/SessionCoordinator.kt`.

1. Publish an immutable snapshot containing route/request/session IDs, destination identity, location quality, total remaining, next-maneuver distance/instruction and navigation state.
2. Use that same snapshot for screen and spoken guidance; explicitly distinguish first-step distance from route total.
3. Render validated meters below 1 km and kilometers above it. Formatting improves readability but must not disguise an invalid 728.1 km route.
4. For invalid/stale state, replace the live numeric countdown with “Distance unavailable” and a clear reason/retry action. A retained previous route may be displayed only as explicitly stale.
5. Check generation/request/route identity again inside queued UI callbacks so an older navigation request cannot overwrite a new one.
6. Replace “We are walking on [road]” with less certain wording when evidence only establishes the planned route, not the user's matched position on that road.
7. Preserve the visible Stop control and TalkBack access.

### Step 10 — Correct cancellation, off-route and arrival behavior

1. Cancel pending location/geocoding/routing and invalidate their callbacks on Stop/pause/new destination. Propagate coroutine cancellation rather than converting it into an ordinary provider failure.
2. Retain one active route job and release location subscriptions on lifecycle transitions; do not restart automatically after backgrounding.
3. Implement a real reroute callback with the existing provider/coverage validation, or remove the false “Recalculating” claim and present retry. Do not add a service solely for this.
4. Keep old directions suppressed while off-route/unmatched; failed rerouting must not silently resume them as current.
5. Require valid quality/progress evidence to say “Near destination”; use explicit arrival confirmation for completion rather than radius alone.
6. Keep camera/ESP32 warnings independent and higher priority. A route calculation must not block sensor processing or delay STOP speech.

## 5. Minimal proposed contracts

These are implementation shapes, not files already present:

```kotlin
data class NavigationLocationFix(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val elapsedRealtimeMs: Long,
    val provider: String
)

data class RouteMatch(
    val segmentIndex: Int,
    val fraction: Double,
    val chainageMeters: Double,
    val crossTrackMeters: Double
)

// Match failure / unavailable state must be explicit; never encode it as zero.
// Route preparation and output also carry the existing session generation
// plus a request identifier to distinguish concurrent requests within a session.
```

Use the existing coordinator rather than introducing a parallel navigation manager. Prefer pure validator/progress functions with injectable clock/provider for deterministic tests.

## 6. Test cases with exact expected behavior

| ID | Fixture/action | Assertion |
|---|---|---|
| G01 | Actual captured AB1 failing response | Reproduces baseline defect; repaired code corrects the proven cause or rejects inconsistent data |
| G02 | Provider total 178 m; geometry hundreds of km | Route rejected before start; no numeric navigation output |
| G03 | Valid long-distance provider route with matching geometry | Not rejected merely for length; correct kilometer formatting |
| G04 | Standard polyline test | Every coordinate matches independently specified reference, not only first |
| G05 | Truncated/overflowing/illegal encoding | Typed failure; no crash or partial successful route |
| G06 | Wrong precision or swapped axes | Validation detects inconsistent endpoints/length; no guess-based reinterpretation |
| G07 | L-shaped 100 m + 100 m path | At start approximately 200 m remains, not the 141 m diagonal |
| G08 | Same path, halfway along first leg | Approximately 150 m remains; about 50 m to bend |
| G09 | 30 geometry segments but two maneuvers | Step index never used as segment identity; progress stays on expected segments |
| G10 | Hairpin/parallel route and noisy fixes | No unjustified jump to much later route segment |
| G11 | Stationary jitter then genuine backtrack | Stable nearby progress; remaining may increase on true backtracking |
| G12 | Missing/stale/future/imprecise fix | No precise turn/arrival; explicit quality/unavailable state |
| G13 | Cached fix before fresh fix arrives | Stale position cannot start route or refresh itself by receipt time |
| G14 | AB1 spoken alias and AB1 menu selection | Same canonical destination identity; routing source/coverage explicit |
| G15 | New request B while A completes | No output from A overwrites B |
| G16 | Stop during location/lookup/routing/UI dispatch | No route start, late speech or countdown after cancellation |
| G17 | Off-route with no network/local coverage | Honest route unavailable/retry; no fictional reroute announcement |
| G18 | Position near endpoint but inaccurate | No automatic arrived state |
| G19 | USB close hazard while route calculation runs | Existing STOP processing/preemption remains intact |
| G20 | Device reproduction inside AB1 | Accepted fix/route yields defensible result or unavailable; retain actual spoken/UI evidence |

For hand-defined geometry tests, target <=1 m numerical tolerance over short paths. Build geographic fixtures from independently specified meter offsets and verify conversion; do not compute expected values by calling the same production helper. Device tolerance must account for real GPS uncertainty and route choice; no guessed 178 m acceptance criterion.

## 7. Commands and verification order

After implementation files/tests exist, from the implementation worktree's `android` directory:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.navisense.GoogleRoutesParsingTest' --tests 'dev.navisense.PedestrianNavigationEngineTest' --tests 'dev.navisense.navigation.maps.NavigationDistanceRemediationTest'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
Get-FileHash -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Algorithm SHA256
```

Add new validator/location test classes to focused execution as they are created. Inspect XML test results and record actual counts/failures. Run device instrumentation only against a reserved connected phone and the exact candidate APK; preserve app data and existing signing constraints. Installation success is not navigation validation.

Physical checks in order: stationary online AB1 request; location disabled; denied/approximate permission; indoor low-quality fix; outdoor accepted fix; offline supported campus route; offline unsupported destination; Stop during loading; supervised short curved walk; deliberate route deviation; camera/ESP32 regression. Never require the user to walk along an unvalidated route just to reproduce a software calculation.

## 8. Delivery boundaries and estimates

| Change | Included steps | Exit evidence | Effort estimate |
|---|---|---|---|
| Diagnostic/reproduction slice | 01–03 | Captured trace and baseline failing test, or explicit reproduction blocker | 1–2 h after device available |
| Input correctness slice | 04–07 | Strict parser, route validation, accepted fix and destination identity tests | 3–5 h |
| Progress/UI slice | 08–09 | Geometry/continuity tests and unified snapshot | 2–4 h |
| Lifecycle/device slice | 10 and physical matrix | Cancellation/priority tests, device evidence and remaining limitations | 2–3 h |

Estimates are provisional, not promises. Decoder/provider faults, missing raw responses or unavailable phone access may change them. Each slice should be reviewable independently; commit only intended files after appropriate checks. Do not replace existing main history or push communication history into source.

## 9. Completion checklist

- The exact cause of the AB1 screenshot is demonstrated by recorded input and a regression, not assumed from the magnitude alone.
- Invalid routes/fixes fail clearly before misleading distance/turn output.
- Both route paths use real accepted position and correct destination identity.
- Along-route total and next-turn distance use the same valid snapshot and correct index mapping.
- Stop, unavailable location, offline failure and off-route behavior remain honest and cancellable.
- Device retest records source/APK/map identity, actual display/speech and failures.
- Model accuracy and YOLO–ESP32 naming work remain separate, covered by `chair-table-fusion-and-navigation-plan.md`.

Immediate first implementation task: establish the source worktree and instrument one AB1 request while preparing the campus inventory below. If raw route data cannot yet be captured, implement the deterministic inconsistent-geometry guard/tests independently while retaining the exact-cause investigation as open. All common fixes in Steps 01–10 must apply to campus-wide route construction and progress, not special-case AB1.

## 10. Campus-wide implementation extension

### C01 — Establish the complete Chennai-only location inventory

Owner: navigation integration owner; campus operator supplies field verification. Run before adding guessed coordinates.

1. Extract the existing map's POIs, entrance/node associations and aliases into an audit table. Current asset at the inspected revision has 11 POIs, 2,571 nodes and 5,452 directed edge records.
2. Cross-reference the current official Chennai campus directory/map and on-campus signage. Exclude Vellore, AP and Bhopal information. Official name evidence does not establish coordinates or a usable entrance.
3. Add every identified destination with a stable ID, canonical name, aliases, category, source URL/document/date, discovery status and responsible verifier.
4. Separate building, service, entrance and room identities. A library inside a building may share its entrance; it should not be assigned an invented independent outdoor location. One building can have multiple verified entrances.
5. Check all categories: academic buildings, administration/offices, libraries, hostels and their permitted entrances, dining/mess/canteens, auditoriums/event venues, sports facilities, medical/welfare services, banks/ATMs, shops/student services, parking/transit points and gates. These are discovery categories, not claims that every candidate is mapped or unrestricted.
6. Maintain explicit inventory states: discovered, name verified, position verified, entrance verified, graph connected, device tested, restricted, temporarily unavailable. No guessed item enters the selectable routable list.
7. Reconcile the inventory with a campus operator before calling it complete. Record missing names/data and excluded/restricted places with reasons; do not invent a final campus location count.

Current asset checklist—all entries need coordinate/entrance verification rather than automatic acceptance:

| Existing ID | Existing label | Required review |
|---|---|---|
| `poi_main_gate` | Main Entrance Gate | Pedestrian entrance and crossing/access rules |
| `poi_academic_block_1` | Academic Block 1 (AB1) | Screenshot regression, canonical entrance and indoor limitation |
| `poi_academic_block_2` | Academic Block 2 (AB2) | Actual entrance; current POI-to-node offset 38.0 m |
| `poi_academic_block_3` | Academic Block 3 (AB3) | Current offset 79.5 m; verify missing path rather than increasing snap radius |
| `poi_library` | Central Library | Building/service identity and actual access entrance |
| `poi_food_court` | Food Court / Ambrosia Canteen | Verify these names refer to the same routable place; current offset 45.6 m |
| `poi_hostel_delta` | Delta Hostel Block | Verify name, permitted entrance and access restrictions |
| `poi_hostel_gamma` | Gamma Hostel Block | Verify name, permitted entrance and access restrictions |
| `poi_admin_block` | Admin Block | Verify canonical identity and public entrance |
| `poi_sports_complex` | Sports Complex & Ground | Determine whether multiple distinct destinations/entrances are needed |
| `poi_kelambakkam_road` | Vandalur-Kelambakkam Bus Stop | Outside-campus boundary, pedestrian connection and crossing evidence |

Known inventory gaps: official Chennai pages identify [Academic Block Four](https://chennai.vit.ac.in/about/infrastructure/academics-block-4/) and [Academic Block Five](https://chennai.vit.ac.in/academic-block-5/), absent from the current 11 POIs. Their coordinates and entrances are not established in this plan. The [official infrastructure page](https://chennai.vit.ac.in/about/infrastructure/) contains inconsistent numeric wording while listing AB1–AB5, so use individual facility records and field verification rather than trusting a single summary count. Use [campus amenities](https://chennai.vit.ac.in/campus-amenities/) to seed service discovery, not to invent positions.

### C02 — Define location data and one destination resolver

Files: extend `map/MapModels.kt`, map loader in `MapRoutingEngine.kt`, and MainActivity's destination selection/voice dispatch. Proposed test: `CampusDestinationResolverTest.kt`.

1. Add aliases, parent building ID where applicable, verification status and entrance IDs to the current POI data with a versioned schema. Keep existing IDs stable.
2. Store verified entrance coordinate, associated walking graph node/edge, permitted approach, source and verification date. Access metadata may be unknown; unknown must not be presented as verified unrestricted access.
3. Implement a pure resolver over this index: normalized exact ID/name/alias first, then candidate matching. Use token-aware matching so AB1 does not match AB10 and “hostel” does not choose the first hostel.
4. Ambiguous matches require accessible selection. Unknown campus destinations return not-found with alternatives; never substitute the nearest named place.
5. Route known campus voice/menu destinations through this same resolver. No unrestricted online geocoder for canonical campus IDs.
6. A room/service query can resolve its verified parent entrance and explain “Guidance to [building] entrance; indoor directions unavailable.” Do not silently strip a room number and announce arrival at the room.
7. Keep out-of-campus queries in an explicit online/unsupported branch. Phoenix Mall must not be mapped to a campus default to satisfy the request.

Exit: every selectable place has a stable verified identity; input method cannot change its coordinates or route semantics.

### C03 — Survey entrances and walking connections

Files/data: existing `vit_chennai_map.json`; proposed audited source manifest and an export/validation script under a dedicated `scripts/maps/` directory. Do not scatter hand-edited coordinates through Kotlin.

1. Compare graph coverage with the inventoried locations. Current declared bounds and actual node extrema disagree; derive region metadata from audited source data and separately represent the campus boundary. Graph bounds alone do not prove coverage of every point inside them.
2. For each destination verify an actual pedestrian entrance, not building centroid. Record distinct entrances if one is inaccessible from another side.
3. Identify walkways, ramps, stairs, gates, barriers, roads/crossings, direction restrictions and known closures. Do not infer accessibility from a generic road label or a straight line between nodes.
4. Add missing connections only from map/survey evidence. A 79.5 m node offset is a data-review issue; blindly extending the snapping radius can route through a building or wall.
5. Use appropriate access filtering. Do not market a stairs-unknown path as step-free or universally accessible. If a requested access profile cannot be supported, report that rather than relaxing it silently.
6. Validate each edge's endpoints, geometry length, cost and direction. Preserve deliberate one-way/access constraints; do not automatically mirror all edges.
7. Export deterministic, versioned map data with checksums, provenance and attribution. Keep unavailable destinations in the audit inventory but out of routable choices until connected.

Exit: every supported entrance has a defensible connected approach. Map completeness and physical walkability are independently recorded.

### C04 — Apply one route correctness pipeline everywhere

1. Use accepted current location from Step 06 for every campus origin. Never assume main gate, last requested building or the user's spoken building name is their measured location.
2. Snap only to a plausible connected walking edge within the accepted policy and location uncertainty. If the fix cannot distinguish parallel paths, mark location ambiguous.
3. Resolve the target's selected entrance and compute graph route. Check access constraints before running path search.
4. Validate endpoints, geometry and totals with the common validator; compare local A* cost with an independent Dijkstra reference on test graphs.
5. Include only evidence-backed connectors; do not include an unverified indoor-to-outdoor straight line in the claimed walking distance.
6. Both campus and online navigation feed the shared progress/snapshot contract from Steps 08–09. Retain provider differences behind adapters; do not create one countdown implementation per destination.
7. An unavailable path remains unavailable, including same-building/nearby scenarios. Same snapped node is not proof of arrival inside the building.
8. Stop/new destination invalidates progress and callbacks identically for every destination. GPS failure must not silently switch routing mode.

Exit: one common fix resolves the class of problem across campus; no AB1-only special case.

### C05 — Generate automated tests for the entire inventory

Extend `MapRoutingEngineTest.kt` and `NavigationDistanceRemediationTest.kt`; proposed new `CampusMapIntegrityTest.kt` and `CampusDestinationResolverTest.kt`.

1. Parameterize ID/name/alias tests over every inventoried supported destination. Test whitespace/case and explicit AB variants; assert ambiguous/general names never pick arbitrarily.
2. Validate every supported POI/entrance reference, graph node, edge and geometry. Count missing/invalid entries and fail the build on invalid selectable data.
3. For N supported entrances, test all N*(N-1) directed origin/destination pairs on the graph. With the current 11 single-entrance POIs, this is 110 directed non-self pairs; update the denominator as the inventory grows. Maintain explicit expected-unreachable/restricted pairs rather than requiring illegal connections.
4. For valid pairs, assert route starts/ends at intended entrances, uses permitted connected edges, has finite non-negative length and agrees with an independent reference cost within documented numerical tolerance.
5. Generate mid-edge/intersection origin tests as well as entrance-to-entrance tests; a user need not start at a named POI. Exercise both sides of snapping and coverage boundaries.
6. Replay progress over each representative route geometry; assert coherent total/next-turn values at start, bends, midpoint and destination vicinity.
7. Include short, curved, looped, disconnected and long routes. Apply malformed-geometry and 728100-m inconsistency fixtures to common validation rather than only the AB1 ID.
8. Test stale/imprecise location, offline mode, new request, Stop and non-campus queries across representative location categories. Unit tests must not require live internet.

Exit: report exact inventory size, entrance count, pair counts, valid routes, expected restrictions and failures. “110 tests passed” alone is not campus-wide physical qualification.

### C06 — Verify every supported destination physically

1. Maintain a per-destination record with canonical name, selected entrance, verification operator/date, map/APK hash, accepted-fix quality, route trace and observed barriers/access rules.
2. Verify arrival vicinity at every supported entrance, and that the label actually matches the destination. No automatic room-level arrival claims.
3. For each destination, approach from at least two distinct, valid campus origins where the topology permits. A route from a second origin must be computed from that origin, not cached from the first. If topology permits only one approach, record the limitation explicitly.
4. Choose field routes that collectively cover every claimed supported walking edge and turn/connector at least once. Share field traces across destination tests when valid; do not claim all-pairs physical walking from all-pairs unit tests.
5. Repeat a representative route from each category with internet disabled. Verify no hidden network dependency in the local campus path.
6. Test representative indoor starting points, including AB1. If location is insufficient, correct output is unavailable/ask to obtain a usable fix; do not force numeric guidance to demonstrate coverage.
7. Verify restrictions/closures and trace uncertainty with an operator before a supervised walking trial. Review GPS changes around large buildings and entrances separately from arithmetic correctness.
8. Keep the screenshot regression mandatory. A campus inventory expansion does not close the original defect without its reproduced cause and device retest.

Exit: all supported destinations have physical identity/entrance evidence and tested approaches; unverified places remain visibly unavailable. Full edge coverage claims require an edge coverage report.

### C07 — Package coverage honestly and keep it maintainable

1. Display supported campus destinations from the verified inventory rather than a hardcoded name list in the UI/voice parser.
2. Bundle a versioned offline destination index and graph in the existing app. No new server is required for offline campus routing.
3. Run schema, destination, all-pairs and progress checks whenever map data changes. Retain a previous qualified package for rollback.
4. Mark known closed/restricted destinations explicitly and regenerate routes; do not present outdated access as verified current indefinitely. Record review dates and recheck after reported campus changes.
5. Publish coverage counts: discovered locations, verified names, verified entrances, connected destinations, automated-tested routes and physically checked destinations. All must retain clear denominators.
6. Completion means the campus inventory has been reconciled and every claimed supported destination meets the same validation; it does not mean GPS can navigate to every floor, classroom or lab interior.

## 11. Revised schedule and first actions

Shared GPS/route repair retains the Steps 01–10 estimates. Campus inventory, entrance verification and coverage expansion add work proportional to missing locations and paths; do not claim they fit the AB1-only estimate. Start with a half-day inventory/survey assessment to measure the actual gap, then estimate data edits and field coverage from that result. Physical survey requires an available campus operator; it cannot be replaced with fabricated coordinates.

Execute in this order:

1. Prepare the implementation worktree; record source/APK/map identity.
2. Capture AB1's failing route and create the regression test.
3. In parallel as independent work packages, inventory all Chennai destinations and implement shared input/geometry validation.
4. Verify destination aliases, entrances and graph gaps; produce the versioned campus index.
5. Correct common progress, UI, GPS quality and cancellation across both routing paths.
6. Run the full inventory/pair/mid-edge test matrix.
7. Survey and device-check every supported destination and claimed path; keep unresolved items unavailable.
8. Review results and package the verified campus map/APK. Report precisely what remains unsupported.

No runtime code, map asset, location coordinates or deployment was changed while expanding this plan.

## References

- [Android Location fields](https://developer.android.com/reference/android/location/Location): accuracy and elapsed-realtime timestamps.
- [Android current versus last location](https://developer.android.com/develop/sensors-and-location/location/retrieve-current): cached position and current acquisition.
- [OSRM API](https://project-osrm.org/docs/v5.24.0/api/): geometry options, coordinate order and server-prepared routing profile.
- [Google Routes request contract](https://developers.google.com/maps/documentation/routes/compute_route_directions): walking request and response field selection.

## Communication branch — mandatory

All progress, blocker, handoff, review and decision messages belong in communication's append-only `team-chat.md`. Only chat/decision-log changes go there; source/tests/plans/status stay on main under the mandatory synchronization/push protocol in AGENTS.md. Preserve concurrent entries, include actual timestamps and source/evidence references, obtain receiver responses, and never treat chat as frozen-contract authorization or physical test evidence.
