# Navigation distance and offline routing implementation plan

Prepared by Codex on 2026-09-16 for the user's reported Phoenix Mall / repeated-distance defect. Status: **planned; source inspected, implementation and device validation not performed**. Source baseline: `e76b87f9e3b20d593b5793b73187e89260124972` on main. Actual hackathon T+ remains unverified.

Authority: [AGENTS](../../AGENTS.md), [frozen PRD](../README.md), [frozen guidance](../guidance.md), [implementation state](../implementation-state.md), [master schedule](../implementation-plan.md), and [Rishav's owner plan](implementation-plan.md). This is a defect-remediation plan for the existing optional navigation feature; it does not amend the frozen product contract or replace mandatory indoor walking/search acceptance.

## 1. Objective and intended behavior

Every displayed navigation distance must be traceable to an accepted device location, a resolved destination, and a valid pedestrian route. Missing evidence must produce an explicit unavailable state, never a fabricated coordinate, route, street name, or distance.

For “navigate to Phoenix Mall,” resolve the actual place and confirm its locality/entrance when ambiguous. If the app cannot resolve it offline, report that limitation. If a real destination is known but its route is unavailable, report route unavailability. Do not label a nearby default coordinate “Phoenix Mall.” This plan does not guess which Phoenix Mall the user means or assert its actual distance.

Offline navigation means positioning plus a locally available destination index and connected pedestrian graph. GPS coordinates alone do not supply place names, roads, entrances, or routes. The current offline asset contains campus POIs; arbitrary city destinations require additional verified data or a configured online provider.

## 2. Confirmed source findings

Paths below are relative to `android/app/src/main/java/dev/navisense/` unless stated otherwise. Function names are stable references; inspect line numbers again before implementation.

| ID | File / function | Observed behavior | Consequence |
|---|---|---|---|
| NAV-01 | `navigation/maps/GoogleRoutesService.kt`, `geocodeDestination` | Failed lookup returns success at `12.8442, 80.1549` | Unrelated queries become the same destination coordinate |
| NAV-02 | Same file, `computeWalkingRoute` | Failed providers call `createMockWalkingRoute` | Fictional turns and fixed 35/45/40 m steps; fixed 120 m total enter live navigation |
| NAV-03 | Same file, `resolveStreetName` | Missing street becomes “Vandalur Road” | Invented street identity is presented as known |
| NAV-04 | `app/MainActivity.kt`, `lastKnownLocation` and `onDestinationReceived` | Initial origin is `12.8406, 80.1534`; null/error location uses it | Navigation can start without an actual current fix |
| NAV-05 | Same file, `fetchAndStartWalkingRoute` | Geocoding failure additionally substitutes origin plus `0.0005` degrees on each axis | Another invented destination remains even after service failure is corrected |
| NAV-06 | Same file, `startMapNavigationToPoi` | Every route starts at `12.8407, 80.1534`, even after starting GPS; permission request does not return before planning | Campus distance is calculated from the main gate rather than the phone |
| NAV-07 | `navigation/maps/PedestrianNavigationEngine.kt`, `onLocationUpdated` / `updateStatus` | Haversine distance to final endpoint becomes `totalRemainingDistanceMeters` | UI “meters left” means straight-line distance, not remaining walking distance |
| NAV-08 | `map/MapRoutingEngine.kt`, `findNearestNode` | No maximum snap distance | A location far outside mapped paths can attach to an unrelated graph node |
| NAV-09 | Same file, `reconstructRoute` | Total sums graph edges; inserted origin connector is not included; final POI connector is not included | Geometry, total and true destination approach can disagree |
| NAV-10 | Same file, turn construction | `points` begins with origin, but edge indexing begins at first graph node | Turn geometry appears shifted by one point; reproduce with a non-collinear fixture before fixing |
| NAV-11 | `map/MapNavigationCoordinator.kt` | Uses direct distance to current waypoint plus later maneuver lengths | Curved segments can be undercounted; this differs from full route progress |
| NAV-12 | `app/MainActivity.kt`, initialization | Constructs `GoogleRoutesService(context = this)` without an API key | Google API branches are skipped in this construction; native geocoder/OSRM/fallbacks remain |
| NAV-13 | `map/LocationTracker.kt` | Contains default coordinates and a mock switch; callback shape has no fix timestamp | Audit real call sites and freshness propagation; defaults alone are not proof of emitted fake GPS |
| NAV-14 | `map/MapNavigationCoordinator.kt`, speech | Uses wall clock and fixed `sessionGeneration = 1L` | Integrate current session/monotonic clock before reliable cancellation claims |

The current asset `android/app/src/main/assets/maps/vit_chennai_map.json` contains 11 POIs and 2,571 nodes. No Phoenix Mall entry exists in its POI list. Its declared bounding box is latitude 12.830–12.855 and longitude 80.142–80.165, but actual node extrema are latitude 12.7788907–12.8793314 and longitude 80.0799749–80.2002835. Resolve this metadata discrepancy before treating the bounds as authoritative coverage.

No literal 750 m or 220 m navigation fallback was established in the inspected Kotlin source. Fixed coordinates, derived distances and the two different routing paths are confirmed defects; the exact device branch producing the reported numbers is still unverified. Asset edge lengths containing 220.6 m are not by themselves evidence of hardcoding a user's destination distance.

## 3. Terminology

| Term | Meaning and implementation relevance |
|---|---|
| GNSS / GPS fix | A measured geographic position. GPS is one satellite positioning system; Android can combine multiple position sources |
| Fused location | Android/Google location result that can combine available positioning sources; do not call every fix satellite-only GPS |
| WGS84 coordinate | Latitude/longitude in degrees; use named fields to avoid swapping them |
| Horizontal accuracy | Estimated uncertainty radius in meters; it is not a guarantee that the phone is on a particular walkway |
| Monotonic fix age | Elapsed time since the fix using the device's elapsed-realtime clock, unaffected by wall-clock changes |
| Geocoding | Resolving text such as “Phoenix Mall” to place coordinates; success must identify the actual resolved place |
| Reverse geocoding | Resolving coordinates to a name/address; missing names do not justify invented road labels |
| POI / place identity | A point of interest with stable ID, display name, locality, coordinate and provenance |
| Pedestrian graph | Nodes and traversable edges representing mapped walking connections, including access restrictions |
| Edge weight | Routing cost; for this local plan, non-negative walkable distance in meters unless another metric is explicitly introduced |
| A* | Graph search using known cost plus a lower-bound heuristic; optimality requires appropriate edge costs and heuristic |
| Dijkstra | Graph search without a heuristic; use as a small-fixture reference for A* correctness |
| Snapping | Associating a fix or destination with a nearby traversable edge/node, subject to distance and connectivity limits |
| Connector | Verified path between a real origin/entrance and the routing graph; proximity alone does not prove walkability |
| Polyline | Ordered points describing route geometry; not just a direct origin-to-destination line |
| Along-route distance | Length following remaining route geometry, including bends and legitimate connectors |
| Geodesic distance | Shortest surface distance between coordinates, approximated here by Haversine; not walking distance |
| Map matching | Selecting a plausible point on the route for the current fix using distance, continuity and uncertainty |
| Cross-track error | Distance from the fix to its matched route segment, useful for off-route detection |
| Chainage | Accumulated distance from route start to a point along the route |
| Hysteresis | Different entry/recovery conditions or sustained evidence to prevent jitter-driven state oscillation |
| Session generation | Existing global identifier invalidating asynchronous results after Stop, pause or mode change |
| Provenance | Where a place/route/fix came from and which version/hash supplied it |
| Fail closed | Withhold unsupported navigation instructions and numeric claims when evidence fails |

Android documents fix timestamps/accuracy and the distinction between cached last location and current-location requests. These are API facts, not prescribed thresholds for NaviSense. See [Location reference](https://developer.android.com/reference/android/location/Location) and [current versus last location](https://developer.android.com/develop/sensors-and-location/location/retrieve-current).

## 4. Scope and ownership

Rishav owns navigation, MainActivity integration, lifecycle, shared contracts, permission flow, UI and voice integration. Record the `map/` package as part of this existing navigation repair in the entry review; do not silently transfer it to another member. Samik reviews preservation of camera/fusion behavior; Rohan supplies physical USB/STOP regression evidence. Their acknowledgement remains pending. Subham's memory service is unrelated to geographic routing and must not be repurposed as a geocoder. Spandan's model work is unchanged.

Any implementation outside an owner's package must be handed to Rishav as an exact shared-file change request. This document supplies the requested integration changes; it is not a receiver ACK.

Work order: eliminate fabricated successes first, then validate location/destination, correct geometry/progress, and prove offline behavior. Defer nationwide downloads, background navigation, SLAM, new cloud services, model changes and automatic side-clearance guidance. Optional map coverage expansion is a separate follow-up after the existing coverage is truthful.

## 5. Proposed contracts and state flow

Reuse existing coordinator and route models. Add only the fields/types required by the repair; names below are proposed, not existing APIs.

| Contract | Required information |
|---|---|
| `LocationFix` | Coordinate, horizontal accuracy, elapsed-realtime fix timestamp, provider, mock/test marker |
| `ResolvedDestination` | Place ID, confirmed name/locality, coordinate, source, optional verified entrance and map version |
| `RouteRequest` | Request ID, session generation, accepted origin fix, confirmed destination, walking mode |
| `RouteResult` | Success with validated route, or typed failure; never a success containing a synthetic route |
| `RouteProvenance` | Online provider or offline graph identity/version/hash, calculation time, routing profile |
| `RouteProgress` | Route ID, matched segment/fraction, along-route distance remaining, distance to maneuver, fix quality, off-route state |

Typed failures should distinguish `LOCATION_PERMISSION_REQUIRED`, `LOCATION_DISABLED`, `LOCATION_UNAVAILABLE`, `LOCATION_STALE`, `LOCATION_INACCURATE`, `DESTINATION_NOT_FOUND`, `DESTINATION_AMBIGUOUS`, `OUTSIDE_OFFLINE_COVERAGE`, `NO_WALKABLE_CONNECTION`, `NETWORK_UNAVAILABLE`, `PROVIDER_CONFIGURATION_ERROR`, `INVALID_ROUTE`, and `CANCELLED`. Map them to concise user language without exposing keys or raw provider responses.

State sequence: Idle → Acquiring location → Resolving destination → Awaiting selection if needed → Calculating route → Ready → Navigating → Near destination → Explicit arrival confirmation. Failure returns a recoverable unavailable state. Stop is valid at every stage. Tie preparation substates to the existing session authority; do not create a second session counter.

Route acceptance invariant: matching request/session + accepted origin + confirmed destination + approved walking source + finite valid geometry + connected route. Recheck at callback delivery; starting a newer request invalidates older results even when both are in the same mode.

## 6. Detailed execution steps

### Step 0 — Establish reproducible entry evidence

Owner: Rishav; peer review: Samik, with Rohan for sensor interactions. Independent source inspection/test design may proceed while acknowledgement is pending; do not assert READY or a named review occurred without a receipt.

1. Inspect worktree and synchronize main and communication using AGENTS instructions. Preserve concurrent changes and read new team messages.
2. Record source revision, both frozen hashes, phone/app build identity, available device slot, actual T+ if known, and latest receiver messages.
3. Trace both voice intents and both navigation buttons to determine which route path receives “Phoenix Mall.” Capture the recognized text and resolved result, not just the displayed number.
4. Reproduce connected, offline, location-disabled and permission-denied cases while stationary. Record exact original failures before changing code.
5. Record entry decision and allowed scope in the central review register. Missing phone evidence permits code/fixture preparation only; it does not establish physical readiness.

Deliverable: diagnostic record linking query → origin source/age → destination source → router → reported distances. Exit: exact reported-number branch reproduced, or explicitly retained as unverified while confirmed source defects proceed.

### Step 1 — Remove fabricated runtime results

Files: `GoogleRoutesService.kt`, `MainActivity.kt`; tests: existing `GoogleRoutesParsingTest.kt` and `NavigationSafetyPreemptionTest.kt`.

1. Return destination failure when both geocoding options fail; delete the fixed Chennai destination fallback.
2. Remove MainActivity's origin-offset destination fallback and stop processing on lookup failure.
3. Remove `createMockWalkingRoute` from production source. Move a named fixture builder to test sources and update existing tests to use it explicitly.
4. Return route failure when no validated provider succeeds. Never derive turns from a fabricated three-segment route.
5. Replace invented fallback street names with unknown-name state. Use neutral wording only when a real route segment exists.
6. Replace numeric coordinate defaults with absent-location state. Map-center coordinates may remain strictly for map viewport presentation, never route origin.
7. Add regression cases for unknown query, provider timeout, invalid response and missing location. Assert no navigation session starts and no distance/turn is announced.

Exit: no production call can produce a successful route using a fixture or default coordinate. A route-unavailable message is the correct result until later prerequisites are satisfied.

### Step 2 — Acquire and qualify actual location

Files: MainActivity and `map/LocationTracker.kt`, with a small shared navigation location adapter if needed.

1. Use one accepted-fix policy for both navigation paths. Avoid maintaining inconsistent independent defaults.
2. On permission denial or disabled location, stop preparation and show a recoverable message. Return immediately after requesting permission; resume only from a granted/current request callback.
3. Validate cached fixes before use; request a current high-accuracy fix when cache is absent or unsuitable. Apply a bounded wait and cancellation.
4. Preserve accuracy and monotonic timestamp through callbacks. Reject non-finite/out-of-range coordinates, invalid accuracy, future/out-of-order timestamps and stale fixes.
5. Proposed starting configuration: fix age <=10 s, horizontal accuracy <=20 m for route preparation, and acquisition timeout 20 s. These are engineering proposals requiring device review, not PRD safety thresholds. Coarse fixes must not authorize tight turn cues.
6. While navigating, evaluate age even if callbacks stop. Suppress precise navigation when stale/inaccurate; display unavailable rather than a frozen number presented as current.
7. Mock fixes belong only to explicit test execution. Do not silently mix simulated position with a real walking demonstration.
8. Unregister requests and invalidate results on Stop/pause/destroy. Reacquire on explicit restart.

Exit: neither route path starts from the campus gate unless an accepted real fix actually locates the user there.

### Step 3 — Resolve destination identity and coverage

Files: GoogleRoutesService, campus POI matching in MainActivity, existing map models/loader.

1. Normalize query casing/spacing and explicit aliases while preserving meaningful names. Return candidates rather than selecting the first vague substring match.
2. Search local POIs first when applicable. Return not-found if the offline index has no entry; never silently substitute the nearest known POI.
3. For online lookup, retain provider place identity, locality and coordinate. Use regional bias to improve relevance without forcing every result into the campus region.
4. If multiple plausible malls resolve, present accessible choices and require selection. Do not guess the user's Phoenix Mall branch.
5. Use verified entrances where available; label an approximate building point accordingly and do not invent a walkable last segment through a wall.
6. Validate coverage for origin, target and their connected walkable graph. A bounding-box inclusion alone is insufficient.
7. Reconcile declared asset bounds with actual nodes and export provenance. Do not simply enlarge a rectangle and call the enclosed area routable.

Exit: Phoenix Mall either resolves to a confirmed real place or is honestly unavailable. The current 11-POI campus package does not acquire a new mall by changing its display label.

### Step 4 — Make router selection explicit

1. Use existing offline A* only for qualified local graph coverage and connectivity.
2. For destinations requiring online routing, use a configured, verified pedestrian provider. The existing Google implementation already requests `travelMode: WALK`; validate credential/configuration availability without printing secrets.
3. Do not assume `/walking` in an OSRM URL establishes pedestrian routing. OSRM documents that the routing profile is determined during data preparation. Verify the actual deployment/profile or disable that fallback pending verification; do not claim the current public endpoint is definitely a walking service.
4. Separate transport failure, denied configuration, unsupported coverage and empty routes. Close HTTP responses, bound timeouts, propagate coroutine cancellation, and cancel underlying requests where supported.
5. Validate geometry, coordinates, step order, finite non-negative distances, endpoint association and route mode. Missing fields must not turn into plausible zeros or invented endpoint geometry.
6. Preserve route source in diagnostics and display an understandable offline/online state. No route provider may bypass session cancellation checks.

Exit: each route has identifiable provenance and supported pedestrian semantics. See [Google route requests](https://developers.google.com/maps/documentation/routes/compute_route_directions) and [OSRM profile semantics](https://project-osrm.org/docs/v5.24.0/api/).

### Step 5 — Repair the offline graph and route geometry

Files: `MapRoutingEngine.kt`, `MapModels.kt`, map asset; inspect the existing asset-generation process before editing generated data.

1. Validate graph nodes, edge references, non-negative finite weights, POI node IDs and duplicate IDs at load time. Reject invalid packages explicitly.
2. Record data source, extraction date, walking access/filter rules, map version and checksum. Preserve applicable map attribution.
3. Add bounded snapping. Proposed initial maximum is 30 m, subject to entrance/accuracy review. A larger radius must not conceal missing walkways; existing POIs report snap offsets up to 79.5 m and need explicit review.
4. Prefer projection onto a traversable edge where geometry supports it. If retaining node snapping initially, document its limitation and reject unsupported origin/entrance connectors.
5. Include only verified connectors in geometry and length. Do not add straight lines across buildings merely to reconcile a total.
6. Reconstruct graph points and edges with a consistent index convention; keep the origin connector separate from graph-edge indexing. Test a bent three-edge path to catch shifted turns.
7. Sum actual route edges/connectors once. Compare A* against Dijkstra on small fixtures. Ensure Haversine remains a lower-bound heuristic for accepted weights, including rounding tolerance; use zero heuristic when that invariant cannot be established.
8. Return no-route for disconnected components. Same snapped node means graph proximity, not automatically confirmed physical arrival.

Exit: geometry, maneuver positions and total length describe the same connected walking path.

### Step 6 — Compute honest remaining distance

Files: both navigation engines/coordinators and their status models. Reuse a pure distance/progress helper rather than rewriting the entire navigation subsystem.

1. Keep initial route length, remaining route length, distance to next maneuver and direct destination distance as separate fields.
2. Precompute segment lengths and cumulative lengths from validated route geometry. Maintain correspondence between maneuver locations and route segment indices.
3. Project accepted fixes onto plausible segments near previous progress. Score perpendicular distance and continuity; do not jump across a hairpin to a later nearby segment.
4. For matched segment index `i` and fractional progress `t` in [0,1], compute `remaining = (1-t) * segmentLength[i] + sum(segmentLength[j], j>i)`.
5. Calculate distance to maneuver as its chainage minus current chainage, with explicit handling of already-passed maneuvers. Curves must retain their full along-route length.
6. Keep provider totals and geometry-derived totals distinguishable. Record substantial discrepancies and reject implausible geometry instead of silently switching displayed definitions.
7. Handle stationary jitter with uncertainty-aware hysteresis. Genuine backward movement may increase distance; do not force a monotonically decreasing counter that conceals movement away from the goal.
8. On sustained off-route evidence, show off-route status and attempt a real reroute only when supported. If rerouting fails offline, withhold turn guidance; never draw a shortcut.
9. Display units consistently: for example rounded meters below 1 km and one decimal kilometer above it. Unavailable remains text, never zero. Precise rounding does not imply GPS precision.

Exit: “meters left” follows the walking route. If direct distance is ever exposed, label it “straight-line distance,” not route remaining.

### Step 7 — Preserve lifecycle, speech priority and explicit arrival

1. Attach route jobs, location callbacks and all guidance to current coordinator generation/request ID.
2. Test Stop during location acquisition, geocoding, routing, destination selection and active navigation. Late completions cannot restart or announce.
3. Replace fixed generation and wall-clock speech fields in MapNavigationCoordinator with injected current session and monotonic clock.
4. Pause navigation guidance when location is unusable. Keep existing app readiness/fatal-error rules and independent obstacle policy intact; location is not obstacle-clearance evidence.
5. Route guidance uses the single speech arbiter. Sensor/vision STOP must retain priority. Do not change risk thresholds as part of distance repair.
6. Proximity yields “Near destination,” not unconditional “You have arrived.” Preserve an accessible explicit arrival action and prevent navigation completion from silently activating a different mode.
7. Make error/retry/selection/arrival controls work with speech recognition disabled and TalkBack enabled.

Exit: accurate routes cannot bypass Stop, hazard priority, foreground policy or explicit arrival.

### Step 8 — Validate, package and hand off

1. Run focused deterministic regressions, then the existing Android unit suite and debug assembly once the focused checks pass.
2. Inspect every failure and XML report; record executed test count rather than repeating the historical 123-test claim.
3. Record APK SHA-256, source commit, map hash/version and runtime configuration. Install only in an agreed device slot; do not uninstall a conflicting package or erase app data without the applicable authorization.
4. Execute the matrix below with actual operators/equipment. Device installation is not route correctness evidence.
5. Recheck frozen hashes, update central state, commit intended source/tests/docs on main, push, then append the structured communication relay and return to main. Preserve concurrent history and never force-push.
6. Obtain named receiver verdict. Keep navigation-specific qualification separate from AC-01 through AC-16; this plan does not mark baseline gates passed.

## 7. Test matrix and pass conditions

All rows are planned / NOT RUN for this remediation.

| ID | Level / setup | Expected result |
|---|---|---|
| N01 | Unit: lookup returns no results | Destination unavailable; no success coordinate or route |
| N02 | Unit: providers timeout/deny/malformed JSON | Typed failure; no mock route or numeric zero success |
| N03 | Unit: two distinct confirmed destinations | Destination IDs/endpoints remain distinct; no shared fallback |
| N04 | Unit: no fix, stale/future fix, invalid accuracy | Preparation cannot enter navigating |
| N05 | Unit: denied permission then grant | No route before grant; only current request resumes |
| N06 | Unit: same destination, two different valid origins | Appropriate start attachment and route length change on a known fixture |
| N07 | Unit: out-of-coverage and disconnected graph | Explicit failure; no unlimited nearest-node snap |
| N08 | Unit: valid start/entrance connectors | Geometry and total count each connector exactly once |
| N09 | Unit: L-shaped 100 m + 100 m fixture | Start remaining is about 200 m, not the approximately 141 m diagonal; midpoint expectations hand-checked |
| N10 | Unit: hairpin, parallel paths and crossing geometry | Matching preserves plausible progress; no large shortcut jump |
| N11 | Unit: stationary jitter then backward movement | Jitter does not falsely finish; real reverse movement can increase remaining distance |
| N12 | Unit: invalid weights/references/empty geometry | Invalid route/package rejected rather than producing directions |
| N13 | Unit: Stop/new request before asynchronous completion | Zero stale starts, route updates or speech |
| N14 | Unit: sensor STOP while turn cue is ready | STOP wins through existing arbiter; navigation does not suppress it |
| N15 | Unit: near endpoint with uncertain position | Near-destination state; no automatic arrival claim |
| N16 | Device: Phoenix Mall, internet on | Confirm real resolved identity; route from accepted phone fix or truthful failure; retain provider evidence |
| N17 | Device: Phoenix Mall, internet off, existing asset | Unsupported offline destination, no fabricated 750 m/220 m value |
| N18 | Device: known campus POI, internet off, usable fix | Real local graph route with zero required network requests |
| N19 | Device: repeat campus request at two measured start locations | Origin evidence changes; independent route calculation agrees within recorded GPS/geometry uncertainty |
| N20 | Device: location disabled, approximate permission, indoor poor fix | Clear recoverable state, no assumed main-gate origin |
| N21 | Device: stale cached fix after app restart | New fix acquired or unavailable; previous coordinate not treated as current |
| N22 | Device: provider failure after valid geocoding | Route unavailable; destination identity retained without fabricated turns |
| N23 | Device: supervised curved route and deliberate deviation | Along-route distance follows geometry; off-route and reroute failure honest |
| N24 | Device: Stop/background/restart during each preparation stage | No late speech/session restart; explicit restart required |
| N25 | Device: TalkBack and voice recognition disabled | Destination choice, retry, Stop and arrival usable |
| N26 | Device: camera + ESP32 while navigating | Existing warning priorities retained; no map computation blocks sensor processing |

For pure geometry fixtures, specify expected values independently; target <=1 m tolerance for hand-defined short paths after coordinate conversion. For field comparison, record fix accuracy, entrance choice and reference-route geometry; do not require equality with a different provider's alternative route. Review numerical tolerances before execution. All cancellation/fabrication assertions require zero violations.

Proposed commands from `android/` after tests exist:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
# With qualification phone connected and reserved:
.\gradlew.bat :app:connectedDebugAndroidTest
Get-FileHash -LiteralPath app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
```

Capture commands, counts, failures and environment in the central record. Do not rerun expensive camera/model qualification solely for a documentation change.

## 8. Diagnostics required to explain any repeated number

For a bounded debug session capture: request ID/generation; query; resolved place ID/source; origin coordinate/age/accuracy/provider; route source and graph hash; snapped endpoints and offsets; total length; current matched segment/fraction; direct versus remaining distance; fix acceptance/rejection reason; final failure reason. Redact credentials. Keep precise personal location traces out of ordinary Git commits; commit minimal synthetic reproductions and aggregate findings.

UI should state “Waiting for location,” “Choose destination,” “Destination unavailable offline,” “Route unavailable,” or actual route status. Developer diagnostics belong in logs/debug views rather than the primary walking screen. When stale, mark distance unavailable immediately under the accepted policy; do not silently keep displaying an old measurement.

## 9. Dependencies, estimates and review checkpoints

These are effort estimates from remediation start, not the hackathon T+ clock or promised completion times. Work remains with existing owners; this plan does not initiate parallel agents.

| Checkpoint | Steps | Rough effort | Dependency / review exit |
|---|---|---|---|
| C0: evidence and review | 0 | 0.5–1 h | Source findings and device reproduction status recorded; entry scope acknowledged |
| C1: honest failure behavior | 1 | 1–2 h | No fabricated success regressions pass |
| C2: real origins and destinations | 2–4 | 3–5 h | Current fixes, identity, coverage and provider contract verified |
| C3: graph and distance correctness | 5–6 | 3–5 h | Geometry fixtures, snapping and progress tests pass |
| C4: integrated qualification | 7–8 | 2–4 h | Lifecycle tests, device matrix and receiver review |

Total estimated engineering/qualification effort: approximately 9.5–17 hours, excluding map survey/data repair, credential provisioning, unavailable hardware or new regional map extraction. Stop at an honest unavailable state for unsupported areas rather than compressing validation to fit the event deadline.

Open dependencies: actual Phoenix Mall identity; reproduction of the original numeric display; device availability; pedestrian-provider configuration/profile evidence; map-generation provenance and validated entrances; named peer ACK. None blocks preparing negative-path fixtures or removing fabricated success logic after the permitted entry scope is recorded.

## 10. Definition of done and next handoff

The repair is ready for receiver review only when production contains no coordinate/mock fallbacks; both navigation paths use accepted actual fixes; resolved identity is correct or explicitly unavailable; offline coverage/connectivity is enforced; remaining distance follows validated geometry; cancellation/hazard priority/explicit arrival pass; and build/map/test evidence is attached.

Physical navigation is verified only after the relevant device rows actually run. Project acceptance remains separate. The immediate next action is Rishav's entry review followed by Step 1 regression tests and removal of fabricated success paths. Samik reviews the navigation-to-fusion boundary; Rohan reviews physical USB interaction. No receiver acknowledgement is recorded by this document.

Frozen baseline verified during planning:

- `docs/README.md`: `54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA`.
- `docs/guidance.md`: `A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E`.

## Communication branch — mandatory

All progress, blocker, handoff, review and decision messages belong in communication's append-only `team-chat.md`. Only chat/decision-log changes go there; source/tests/plans/status stay on main under the mandatory synchronization/push protocol in AGENTS.md. Preserve concurrent entries, include actual timestamps and source/evidence references, obtain receiver responses, and never treat chat as frozen-contract authorization or physical test evidence.
