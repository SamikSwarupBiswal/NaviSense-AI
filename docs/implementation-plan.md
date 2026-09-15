# NaviSense AI — 24-Hour Implementation Plan

Schedule version: 1. Governance: [AGENTS](AGENTS.md); mandatory phase entry/exit reviews apply. Prepared for the user's **24-hour hackathon**.
Product authority: [PRD v3.2](README.md).
Owners and interfaces: [guidance](guidance.md). Actual progress: [implementation state](implementation-state.md).

**This is a planned execution schedule, not completed work or a guarantee that every acceptance gate will pass in 24 hours.** A start date/time was not supplied. T+00:00 means the actual hackathon start; all times below are elapsed wall-clock hours/minutes, not dates or person-hours. Record the real start with timezone before execution. Calendar deadline = start + 24 hours. Never silently treat today's date or file creation time as the start.

## 1. Scope, Capacity and Starting Assumptions

- Preserve the assigned owners: Spandan models/datasets; Subham laptop memory/API and Android client; Rohan hardware/USB; Samik Android camera/inference/search; Rishav risk/voice/app lifecycle/integration.
- Target exactly the three baseline experiences: standalone walking, direct nearby search, and memory-assisted finding with explicit arrival. Both Android model modes remain required.
- This estimate assumes **two selected demo Locate classes**, an available development laptop, training compute, one actual Android phone, ESP32-S3/HC-SR04 assembly parts, and usable development tools. Choose the classes at kickoff based on actual data feasibility; keys/wallet are examples, not assumed qualified classes.
- Check Android SDK/JDK/build tools, Python dependencies, firmware tooling, offline TTS and training capability during the first hour. A long install/download or missing component changes the feasibility forecast. Record it; do not pretend prerequisites are complete.
- Reserve shared breaks at T+06:00–06:30, T+12:00–13:00 and T+18:00–18:30. Do not schedule mandatory reviews in those windows. This leaves **22 hours/member maximum**, including coding, meetings, testing and documentation, not 22 coding hours plus reviews. Automated training may continue during a break without requiring a member to monitor continuously.
- Additional rest or reduced availability is valid; Rishav must reallocate time and record impact. Do not plan simultaneous tasks for a resting/unavailable member.
- The schedule is aggressive because no implementation or qualified artifacts are evidenced yet. Custom Locate training, phone export compatibility and late integration are the main timing risks. Start their checks immediately.
- Defer optional OCR, scene extras, speech recognition, routing and escape directions. A failed mandatory feature remains failed/unqualified; it is not renamed optional to meet the clock.

## 2. How an AI Should Execute This Plan

1. Read the PRD, shared guidance, implementation state, and the assigned [individual plan](#individual-execution-plans). Read applicable repository instructions and inspect existing source before editing. Continue existing work; do not recreate working modules.
2. Confirm the actual T+00:00 timestamp and your owner. If not known, work against relative deadlines and record the missing anchor. Never claim a calendar appointment or actual completion time from a target time.
3. Inspect dependency handoffs and their evidence. A producer saying “done” is not receiver verification. Use clearly labelled fixtures where explicitly allowed; keep the corresponding physical gate NOT RUN.
4. Edit only owned source. Rishav integrates shared Android build/manifest and shared-contract changes after receiving the exact requested patch. Coordinate producer/receiver API changes before using them.
5. Follow each numbered step: objective → prerequisites → actions → verification → handoff. Create meaningful tests for state/failure contracts, not tests that merely duplicate code.
6. Execute locally runnable checks and record exact commands/results. Hardware assembly, mount changes, supervised walking and audible verification require actual equipment and an available operator. If those cannot be performed, ask for the specific physical action or record the blocker; never substitute a fabricated sensor result.
7. Deliver source revision, artifact hashes, run instructions, fixture/physical results, limitations and a specific next action. Update implementation-state.md and your handoff record with actual work, actor and evidence.
8. Move forward early if dependencies are genuinely ready; the schedule does not require idle waiting. Review/device reservations must still be coordinated. This document creates no background automation or automatic notifications.

### Individual Execution Plans

| Member | Detailed numbered plan |
|---|---|
| Spandan | [Models and datasets](spandan/implementation-plan.md) |
| Subham | [Laptop memory/API and client](subham/implementation-plan.md) |
| Rohan | [Hardware, firmware and USB](rohan/implementation-plan.md) |
| Samik | [Android camera, inference and search](samik/implementation-plan.md) |
| Rishav | [Risk, voice, lifecycle and integration](rishav/implementation-plan.md) |

## 3. Source Layout and Execution Commands

Use one Android application. Proposed package root: `dev.navisense`; confirm it at S01 or record the actual existing package. Folder boundaries below describe ownership, not separate Gradle modules.

| Owner | Concrete intended source roots |
|---|---|
| Spandan | `models/`, `datasets/` manifests, `scripts/` training/export/evaluation |
| Subham | `laptop/app/`, `laptop/vision/`, `laptop/memory/`, `laptop/api/`, `laptop/config/`, `laptop/tests/`; Android `networking/` |
| Rohan | `esp32/navisense_sensor/`; Android `usb/` |
| Samik | Android `camera/`, `inference/`, `search/` |
| Rishav | Android `app/`, `contracts/`, `navigation/`, `voice/`, MainActivity, build files and manifest |

Android packages live under `android/app/src/main/java/dev/navisense/`; corresponding unit/instrumentation tests live under `android/app/src/test/` and `android/app/src/androidTest/`. Adapt paths once at S01 if an existing project uses another valid layout, then update all affected owners.

These Windows commands are intended after the owners create the required files; they are **not claims of existing runnable source**:

```powershell
# Repository root: create environment only if none exists.
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r laptop\requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest laptop\tests

# Android directory, after wrapper/project creation:
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Subham supplies the real local API start command bound to the verified permitted interface. Spandan supplies exact training/export commands once the runtime/model are selected. Rohan supplies firmware build/upload commands after the actual board/FQBN or equivalent target is identified. Do not invent GPIOs, board identifiers, model output layouts, network IPs or secrets to fill a command placeholder.

## 4. Phase Objectives and Ordered Directions

Phase windows overlap deliberately; they are team workstreams, not a sequence of ten exclusive blocks. Review/acceptance reservations below take precedence inside those windows.

### Phase 0 — T+00:00–02:00

**Objective:** Confirm runtime, hardware and interfaces before parallel code diverges.
**Owner:** All; Rishav coordinates.

**Directions:** Read PRD and owner guides; inspect repository/tools; record devices, two demo classes, runtime and package map; build shell and agree event types; execute S01–S03 smoke reviews.
**Exit evidence:** Actual smoke results or named blockers; fixtures clearly labelled; no quality pass inferred.

### Phase 1 — T+00:30–13:30

**Objective:** Produce actual Locate artifacts and a pretrained Mobility artifact.
**Owner:** Spandan; Subham/Samik adapters.

**Directions:** Deliver smoke exports first; collect separate laptop/phone views; annotate and split by capture session; train Locate; export and evaluate candidates; deliver S09 then S15.
**Exit evidence:** Artifact hashes, real training outputs and held-out inputs; both receivers confirm decoding; quality remains AC-02.

### Phase 2 — T+01:15–10:00

**Objective:** Implement trustworthy laptop scan, memory and query/client behavior.
**Owner:** Subham.

**Directions:** Build detector adapter; configure zones; implement scan validation and atomic writes; handle clear/profile/clock races; implement API and validated Android client; deliver S04/S07/S11.
**Exit evidence:** Recorded scan/query/transaction fixtures and live client operation; preserve negative/error distinctions.

### Phase 3 — T+00:30–04:00

**Objective:** Make the actual sensor assembly emit the exact protocol.
**Owner:** Rohan.

**Directions:** Identify board/pins/supply; verify electrical limits/mount; implement acquisition/invalid records/sequence/uptime; bench-check readings; deliver S03/S05.
**Exit evidence:** Verified hardware record and real serial records; no fabricated distances.

### Phase 4 — T+01:15–11:00

**Objective:** Run camera and both model modes locally with correct evidence.
**Owner:** Samik.

**Directions:** Integrate CameraX/runtime; decode and normalize boxes; enforce frame identity/quality; maintain short tracks; implement search confirmation/timeouts; deliver S06/S10/S12.
**Exit evidence:** Fresh perception/search events on phone, runtime hashes and failure tests.

### Phase 5 — T+04:00–11:30

**Objective:** Deliver usable Android sensor events with health and recovery.
**Owner:** Rohan.

**Directions:** Implement permissions/serial adapter and bounded parser; sequence/uptime/session freshness; preserve critical close events; expose watchdog health evaluation; deliver S08/S13.
**Exit evidence:** Real phone detach/recovery plus deterministic malformed/backlog tests.

### Phase 6 — T+03:00–13:00

**Objective:** Turn independent sensor/vision evidence into correct risk/path states.
**Owner:** Rishav with Samik/Rohan.

**Directions:** Implement pure source severity rules and timer logic with fake clock; add real inputs as handed off; ensure close STOP bypasses inference; verify tentative/stale evidence cannot clear path.
**Exit evidence:** Risk fixture suite and real input smoke; no severity averaging.

### Phase 7 — T+00:30 foundations; 09:00–14:30 integration

**Objective:** Deliver accessible, cancellable and prioritized Android guidance.
**Owner:** Rishav; all producers.

**Directions:** Implement shell and Stop early; shared TTS arbiter/watchdogs/session invalidation; accessible selection/arrival/retry UI; foreground/audio/camera failure handling; review S14.
**Exit evidence:** Standalone candidate with real speech and lifecycle behavior; manual controls complete.

### Phase 8 — T+10:00–15:30

**Objective:** Connect memory-assisted finding without breaking independent modes.
**Owner:** Subham, Samik, Rishav; Rohan supports.

**Directions:** Attach validated client and search engine; refresh target before Guide; confirm stale/ambiguous target and arrival; retain USB STOP in stationary search; demonstrate all three flows; review S16/S17.
**Exit evidence:** Feature-frozen reproducible candidate and no unexplained placeholder paths.

### Phase 9 — T+16:00–23:45

**Objective:** Execute complete PRD gates, retain failures and review delivered scope.
**Owner:** Gate leads/reviewers from guidance.

**Directions:** Run reserved test slots; record hardware/model/configuration identity; fix only reproducible defects; retest affected gates; package evidence and review S18.
**Exit evidence:** Actual gate register plus explicit owner decision; not automatic acceptance at T+24.


No phase closes merely because its time window ends. If a dependency is absent, update the forecast and continue only independent/fixture work.

## 5. Exact Handoff and Receiver-Review Schedule

S01–S18 are scheduled instances of the existing H1–H6 contracts; they do not introduce new protocols. Delivery time means the artifact is available at review start. Reserve 10 minutes before delivery for packaging/checks; if a preceding mandatory meeting occupies that interval, package before that meeting. In particular, S02 smoke artifacts must be packaged before S01 starts at T+01:00. Receiver review is separate from producer delivery and is included in the recipient's workload.

For split reviews, the producer is available in sequence; the two receivers do not require the same producer simultaneously. Combined reviews are one joint session on the same build/device.

| Instance / contract | Deliver by T+ | Receiver review T+ | Producer | Receiver | Dependency |
|---|---|---|---|---|---|
| S01 / H6 | 01:00 | 01:00–01:15 | Rishav | Spandan, Subham, Samik, Rohan | Kickoff decisions |
| S02 / H1 | 01:15 | 01:15–01:30 Samik; 01:30–01:45 Subham | Spandan | Samik, Subham | S01 |
| S03 / H3 | 01:45 | 01:45–02:00 | Rohan | Rishav | S01 |
| S04 / H4 | 03:00 | 03:00–03:15 | Subham | Rishav | S01 |
| S05 / H3 | 04:00 | 04:00–04:15 | Rohan | Rishav | S03 |
| S06 / H2 | 05:00 | 05:00–05:15 | Samik | Rishav | S01, S02 |
| S07 / H4 | 05:30 | 05:30–05:45 | Subham | Rishav | S02, S04 |
| S08 / H3 | 07:00 | 07:00–07:20 | Rohan | Rishav | S05 |
| S09 / H1 | 08:00 | 08:00–08:20 Samik; 08:20–08:40 Subham | Spandan | Samik, Subham | S02, actual training and export completion |
| S10 / H2 | 09:00 | 09:00–09:20 | Samik | Rishav | S06, S09 for real Locate integration |
| S11 / H4 | 10:00 | 10:00–10:20 | Subham | Rishav | S07, S01 |
| S12 / H5 | 11:00 | 11:00–11:20 | Samik | Rishav | S09, S10 |
| S13 / H3 | 11:30 | 11:30–11:45 | Rohan | Rishav | S08 |
| S14 / H6 | 13:00 | 13:00–13:30 | Rishav | Samik, Rohan | S08, S10, S13 |
| S15 / H1 | 13:30 | 13:30–13:45 Samik; 13:45–14:00 Subham | Spandan | Samik, Subham | S09, completed correction/evaluation |
| S16 / H4/H5/H6 | 14:30 | 14:30–15:00 | Subham, Samik, Rishav | Rohan | S11, S12, S14, S15 |
| S17 / H6 | 15:30 | 15:30–16:00 | Rishav | Spandan, Subham, Samik, Rohan | S16 plus resolved integration blockers |
| S18 / H6 | 23:30 | 23:30–23:45 | Rishav | Spandan, Subham, Samik, Rohan | All gate results recorded; retests complete or failures explicitly retained |

### What Must Be in Each Delivery

| Instance | Deliverable | Receiver's explicit check |
|---|---|---|
| S01 | App skeleton/interface v0, package ownership, session context, build instructions | Confirm shared types and a buildable shell; agree fixture inputs |
| S02 | Both smoke artifacts, hashes, labels, decoding metadata and reference inputs | Load on phone/laptop respectively; smoke only, no quality claim |
| S03 | Actual board/pins/power/mount record and phone USB smoke | Observe real valid/invalid serial data and offline TTS readiness with Rishav |
| S04 | Exact found/ambiguous/stale/error REST fixtures and target contract | Validate response states and UI input shapes; no live service claim |
| S05 | Firmware candidate, protocol replay set, preliminary bench measurements | Check valid/no-echo/sequence records; no ESP32 risk logic |
| S06 | Live camera plus smoke-model detections, timestamps and geometry identity | Observe real frames; reject duplicate/stale/wrong-session events |
| S07 | Hard Scan/memory service candidate and transactional fault tests | Run scan fixtures, clear-during-scan and API queries; model quality still separate |
| S08 | Real Android USB adapter with health/recovery events | Detach/stale/recover on phone; preserve immediate close events |
| S09 | First trained Locate candidate, mobile export, data split and quality report | Run shared examples on both devices; record failures instead of accepting by deadline |
| S10 | Tracked vision, quality checks and approach history candidate | Run geometry, stale-frame, track-churn and quality fixtures through risk inputs |
| S11 | Validated Android memory client and live laptop API | Query on real phone, cancel requests and reject malformed/stale selections |
| S12 | Local target confirmation/multiple/timeout/search events | Nearby search with laptop off; current-phone directions and Stop cancellation |
| S13 | Sensor adapter candidate with full parser/freshness replay results | Run loss during STOP, backlog/reset and three-record recovery cases |
| S14 | Standalone risk/voice/lifecycle integrated candidate | Joint live-camera/USB/TTS check; neither producer speaks directly |
| S15 | Final planned model candidate, hashes, export metadata and held-out evaluation inputs | Load/compare exact artifacts and freeze model identity for gate runs |
| S16 | Three-flow integrated demonstration candidate and run instructions | Observe standalone, nearby, memory/arrival/search behavior; log integration defects |
| S17 | Feature-frozen build, model/firmware/config hashes, prepared test fixtures and recording setup | Reproduce build/load, verify test configuration and proceed only with executable gates |
| S18 | Final artifact/evidence bundle, actual pass/fail register and demo limitations | Review exact delivered scope; project owner acceptance only if all applicable gates passed |

At review end, record VERIFIED FOR HANDOFF or RETURNED WITH DEFECTS, the actual time, evidence and next action. This receiver status does not mark a PRD gate PASS unless the entire gate actually ran.

## 6. Shared Phone and Hardware Reservations

Assume one qualification phone/assembly so the plan does not depend on unconfirmed extra devices.

- Every device-based handoff above reserves the phone for that joint review. Subham's laptop-only portions of S02/S09/S15 do not need it. All final test slots below reserve the required device(s).
- Between reviews and before T+15:30, Samik has default phone access for camera/runtime development, except these explicit blocks:
  - T+02:00–02:30: Samik captures phone examples with Spandan; Subham supplies laptop examples separately.
  - T+03:15–04:15, T+06:30–07:20, T+09:20–09:50 and T+11:20–11:45: Rohan owns phone/USB testing (including relevant reviews).
  - T+09:50–10:20: Subham owns real client checks (including S11).
  - T+11:45–12:00 and T+14:00–14:30: Rishav owns app/lifecycle validation.
- T+13:00–13:30 and T+14:30–16:00 are joint integration/review, not parallel independent phone sessions.
- During another owner's device slot, use desktop fixtures, recorded inputs, code review, labelling or tests that do not seize the phone. No second app install/firmware upload during a reserved measurement.
- The laptop stationary camera and training process share compute only if measured capacity allows it. Reserve the webcam/compute for AC-02/AC-03; pause training before final model freeze and qualify the same deployment load intended for the demo.

## 7. Final Gate Execution and Review Reservations

Prepare fixtures, labelled scenes, premarked bench distances and recording tools before T+16:00. These short slots are for executing prepared tests, not authoring test infrastructure. Baseline assumes two demo classes; additional classes add mandatory trials and require rescheduling.

| T+ slot | Gate(s) | Lead | Contributors | Reviewer | Resource / required preparation |
|---|---|---|---|---|---|
| 16:00–16:30 | AC-02 | Spandan | Subham, Samik | Rishav | Phone + laptop — Held-out quality on the exact exported models; annotations ready beforehand |
| 16:30–17:00 | AC-03, AC-04 | Subham | Spandan, Rishav | Samik (AC-03), Spandan (AC-04) | Laptop; phone for client validation — 20 prepared Hard Scans plus prebuilt API/transaction/client fixture suite |
| 17:00–17:20 | AC-08 | Rohan | Rishav | Samik | Sensor bench — 120 readings over six distances with premarked positions; retain invalid counts |
| 17:20–17:40 | AC-10 | Rishav | Rohan, Samik | Subham | Replay harness — Execute already-authored deterministic transport/risk cases and inspect results |
| 17:40–18:00 | AC-11 | Rishav | Rohan | Samik | Phone/audio — Repeated speech, escalation and ten measured Stop cancellations |
| 18:30–19:00 | AC-01, AC-05 | Rishav (AC-01), Samik (AC-05) | Samik, Rohan, Spandan | Subham (AC-01), Rohan (AC-05) | Phone + sensor — One compatible 10-minute offline Mobility run; retain separate assertions for both gates |
| 19:00–19:30 | AC-06 | Rohan | Rishav, Samik | Subham | Phone + sensor + external recording — 20 bench entries at 30/40 cm; physical entry, packet, decision and audible timestamps |
| 19:30–20:00 | AC-09 | Rohan | Rishav | Samik | Phone + sensor — 10-minute stable stream then five reconnect cycles, denominators kept separate |
| 20:00–20:30 | AC-12 | Samik | Rishav, Rohan | Spandan | Phone + sensor/audio — Five-minute clear scene plus dark/covered/stall/sensor/TTS failure cases |
| 20:30–21:00 | AC-13 | Samik | Spandan, Rishav | Subham | Phone + object scenes — Prepared two-class present/absent search trials; all PRD counts still required |
| 21:00–21:30 | AC-14 | Subham | Rishav, Samik, Spandan | Rohan | Phone + sensor + laptop — Five full flows and five switching/Stop cycles with pre-staged conditions |
| 21:30–22:15 | AC-07 | Rishav | Rohan, Samik, Spandan | Subham | Supervised aisle + mounted phone — 20 controlled encounters; stationary critical tests already completed |
| 22:15–22:45 | AC-15, AC-16 | Rishav | Subham, Samik, Rohan | Spandan | Phone + laptop + sensor — Prepared lifecycle fault suite and TalkBack flows; record each gate separately |

Each slot includes recording and reviewer inspection. If the full PRD samples cannot be completed in its slot, record the incomplete gate and consume contingency time; do not reduce its denominator. Bundled slots share compatible setup, but keep separate results. AC-15 must include its own full failure matrix; TalkBack success is not a substitute.

T+18:00–18:30 remains a break. T+22:45–23:30 is reserved for focused fixes/retests and evidence cleanup. This is only 45 minutes, not enough for retraining or an architectural rewrite. T+23:30–23:45 is S18 review. T+23:45–24:00 is final launch/demo preparation and truthful scope presentation.

## 8. Critical Dependencies and Delay Rules

The likely critical path is: smoke compatibility → actual Locate training/export → both runtime checks → search integration → frozen candidate → quality/search/full-flow gates. A separate critical branch is actual USB compatibility → valid sensor events → independent risk/TTS → physical event-to-audio acceptance.

- **By T+02:00:** unresolved phone/model/USB smoke issues get a named blocker, evidence and revised ETA. Continue independent code/fixture work. Missing hardware cannot be “passed” with a replay.
- **By T+08:00:** Spandan must provide a real candidate or measured training progress and ETA. A running training job is not an artifact. Subham/Samik can continue adapters on smoke artifacts; final Locate gates stay open.
- **By T+11:30:** perception, memory client and USB health interfaces must be reviewable for integration. Unreviewed producers threaten S14/S16; Rishav must record the impacted milestones rather than assume their completion.
- **By T+13:30:** freeze the planned model candidate for integration. If Locate remains unusable, explicitly forecast an incomplete object-finding experience; do not replace it with unsupported Mobility labels or laptop inference on behalf of Android.
- **At T+15:30:** stop adding features. Fix only reproducible defects; retain the precise tested build/configuration hashes.
- If a delivery will be late, notify the receiver as soon as known, ideally 30 minutes before its target. Record actual ETA and cause. Receiver uses the reserved slot for reproducible blocker inspection or independent work, not fictional acceptance.
- Dependency start = max(planned start, actual prerequisite review completion, next available resource slot). Rishav moves dependent reviews and each member's affected steps together. Do not retain impossible original downstream deadlines or overlap phone/person reservations.
- A code/model/firmware/configuration change after candidate freeze must identify affected gates and trigger their required retests. If retests do not fit before T+24:00, mark the result incomplete/unqualified rather than accepted.
- Baseline acceptance requires all 16 gates. A working subset may be demonstrated explicitly as a **partial prototype**, with failed/untested capabilities stated. No scope relabelling, synthetic evidence or shortened acceptance tests.

## 9. Common Handoff Record

```text
Schedule instance / H-contract / source owner:
Planned delivery and review window (T+):
Actual delivery/review time and event start timezone:
Source revision and changed paths:
Artifact paths and SHA-256 hashes:
Exact setup/run/test commands:
Evidence type: fixture | actual device | supervised run
Results and failures; full gate sample count if claiming a gate:
Receiver: verified for handoff | returned with defects
Unresolved issue, resolver and new ETA:
Affected downstream steps and new device reservation:
Implementation-state rows updated:
```

## 10. Completion Boundary

The plan itself can be documented now. Implementation status remains planned and all product gates remain NOT RUN until actual work and evidence exist. Rishav updates the central tracker; every owner supplies their own evidence and receiver reviews. The actual event start/calendar dates remain unrecorded until supplied by the team.


## 11. Mandatory Phase Entry and Exit Reviews

These reviews are part of execution, not optional paperwork. Read AGENTS.md for exact required fields. Entry must occur before dependent work; preparatory fixture-only scope must be explicitly bounded. Exit occurs only after the phase deliverables/checks and receiver review, not simply because the target clock time arrived.

| Phase | Entry objective | Exit review checkpoint | Responsible lead / peer reviewer |
|---|---|---|---|
| 0 | At kickoff, verify frozen hashes, ownership, tools, devices and permitted smoke scope | By T+02:00 after S01–S03 receipts, or record unresolved prerequisites | Rishav / Subham |
| 1 | Before data/training work, agree labels/splits/runtime and actual compute; smoke preparation may start earlier | S15 receiver reviews through T+14:00; AC-02 remains separate | Spandan / Samik and Subham |
| 2 | Before scan persistence, confirm schema/zones/fixtures and transaction ownership | S11 through T+10:20, including scan/service/client checks | Subham / Rishav |
| 3 | Before assembly/acquisition, verify actual electrical setup and operator | S05 through T+04:15; full calibration gate remains separate | Rohan / Rishav |
| 4 | Before camera/runtime integration, verify S01/S02 interfaces/artifacts | S12 through T+11:20, with actual search/perception evidence | Samik / Rishav |
| 5 | Before live USB integration, verify S05 protocol and phone connection | S13 through T+11:45 | Rohan / Rishav |
| 6 | Before risk implementation, verify PRD cases, clock abstraction and input schemas | S14 through T+13:30 | Rishav / Samik and Rohan |
| 7 | Before shell/speech work, verify session and accessible-control scope; final integration requires actual producers | S16 through T+15:00 with speech/lifecycle checks | Rishav / Samik |
| 8 | Before full flow, verify S11/S12/S14 and reviewed model candidates | S17 through T+16:00 | Rishav / Rohan |
| 9 | At S17, verify frozen candidate, full test fixtures and operators/resources | S18 through T+23:45; project-owner acceptance only if justified | Rishav / assigned gate reviewers and project owner |

Peer review is included in the named delivery/review block. Phase 0's final readiness record uses collected receipts; Subham can inspect the record remotely without taking the phone from S03. If entry evidence is missing, mark BLOCKED or READY-FIXTURES for the explicitly independent subset; this never closes a physical phase or acceptance gate.

Record entry/exit actual times and evidence in implementation-state.md and the communication log. The main guidance and PRD/README remain unchanged. Phase-9 exit cannot be marked successful if required gates failed or were not run.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
