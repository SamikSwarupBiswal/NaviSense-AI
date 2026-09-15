# NaviSense AI — Implementation State

Last updated: 2026-09-15 (Asia/Kolkata), Codex review remediation.

Product authority: [PRD v3.2](README.md). Ownership baseline: [frozen shared guidance](guidance.md). Timed execution: [24-hour plan](implementation-plan.md). Rules: [AGENTS](AGENTS.md).

**Current state: Android perception helpers and smoke runtime implemented; live CameraX/application integration and peer review pending.** The previous Phase 0 completion and inference timings were overstated. See the correction record below. All product acceptance gates remain NOT RUN.

This file is the central progress tracker. The PRD defines what must be delivered; the guides define who does it and how work is handed off. Rishav consolidates progress here using each member's evidence. Do not interpret completed documentation as completed engineering work.

## 1. Progress Summary

| Area | Current status | Evidence / limitation |
|---|---|---|
| Product requirements and hardening | Documented & Frozen | PRD v3.2 and guidance.md verified exact against frozen SHA-256 baseline |
| Five-member allocation | Active across all subsystems | All 5 owners (Spandan, Subham, Rohan, Samik, Rishav) executing concurrent workstreams |
| Phase plans and handoffs | Phase 0 exit reopened | Smoke load/forward tests available; full compatibility and receiver evidence pending |
| Acceptance plan | Active | 16 PRD acceptance gates mapped and assigned; baseline benchmarks underway |
| Implementation source | Evidenced on main | Master Android shell, perception engine, USB driver, FastAPI schemas, and models present |
| Models and datasets | Evidenced & Benchmarked | Locate/Mobility smoke models benchmarked on phone; AC-02 evaluation harness ready |
| Actual hardware/phone compatibility | Physically Verified | OPPO CPH2753 (Android 16 API 36, Camera2 FULL, USB host active, Google TTS installed); app live |
| Integrated user experiences | In progress | Master app shell running live; CameraX analyzer, UsbSensorAdapter, and Hard Scan connecting |
| Acceptance | 0 of 16 gates recorded PASS | Component checks do not establish acceptance or sustained runtime performance |

Do not calculate an overall completion percentage from document count or these gates: gates differ in scope and none has run yet.

## 2. Status Definitions

| Status | Meaning |
|---|---|
| Planned | Assigned work; implementation has not been evidenced |
| In progress | Actual work has started, with a source/artifact or member update linked |
| Blocked | A specific impediment prevents the next action; record resolver and unblock condition |
| Ready for review | Owner supplies deliverable and checks; receiver review pending |
| Verified | Specified checks passed with evidence; scope and physical/automated distinction explicit |
| Accepted | Explicit acceptance recorded for the stated scope; never inferred from delivery |

Gate results use **NOT RUN / RUNNING / PASS / FAIL / BLOCKED**. A PASS requires the complete PRD gate and reviewer evidence; it is not automatically project-owner acceptance. Missing evidence is initially NOT RUN, not an invented failure or blocker.

## 3. Member Work State

| Member / guide | Owned work | State | Evidence | Next action |
|---|---|---|---|---|
| [Spandan](spandan/guidance.md) | Models, datasets, training, exports and quality evaluation | In progress | S02/H1 smoke artifacts, metadata contracts, reference fixtures, CAPTURE_CHECKLIST.md, verify_smoke.py (PASS), evaluate_ac02.py (PASS) | Ingest dataset batches from Subham and Samik; run train_locate.py for Handoff S09 candidate weights |
| [Subham](subham/guidance.md) | Laptop Locate, Hard Scan, SQLite, API and Android memory client | In progress | REST API schemas, SQLite schema, response fixtures (8/8 tests pass), MemoryClientContract, mock server script | Capture laptop tabletop dataset per checklist; implement webcam adapter & 2.0s Hard Scan |
| [Rohan](rohan/guidance.md) | Hardware, firmware, mount, USB and Android sensor adapter | In progress | Hardware spec, 10Hz ESP32 firmware, serial test tool (20/20 pass), replay harness (10/10 pass), dev.navisense.usb package & UsbSensorAdapter (unit tests pass) | Physical bench ranging across 6 distances (AC-08); benchmark live USB OTG streaming to phone |
| [Samik](samik/guidance.md) | Android camera, both inference adapters, tracking and search engine | In progress | Codex fixed benchmark execution, letterbox preprocessing, fresh distinct-frame search matching and atomic model cache replacement; regression tests added | Capture phone dataset; implement CameraX and deliver H2/H5 integration to Rishav |
| [Rishav](rishav/guidance.md) | Risk, voice UX, accessible shell, lifecycle and integration | In progress | Master app shell v0 merged on main (commit 78f3187), core contracts, accessible UI, 25/25 unit tests pass | Wire UsbSensorAdapter & immediate STOP; implement offline TTS arbiter with 4 priority levels |

Ownership above is the user's current allocation. Work reported by Codex must be labelled Codex-prepared/implemented/tested as appropriate, not attributed as completed member work without confirmation.

## 4. Phase Tracker

Phase numbers match the PRD and guides; Phase 0 is the prerequisite compatibility check. Independent subsystem work may overlap as described in shared guidance.

| Phase | Responsible owners | State | Evidence / next exit requirement |
|---|---|---|---|
| 0 — Compatibility and contracts | All; Rishav coordinates | Exit reopened | Model smoke execution only; actual USB/offline audio and named peer review still required |
| 1 — Stationary Vision | Spandan; Subham laptop adapter; Samik phone check | In progress | Smoke models verified; AC-02 harness ready; dataset capture batches and custom Locate training underway |
| 2 — Hard Scan + Memory | Subham | In progress | FastAPI schemas & SQLite schema verified; transactional 2.0s Hard Scan & mock/live server in progress |
| 3 — Sensor Node | Rohan | In progress | 10Hz firmware & 1k/2k divider verified; USB serial adapter implemented; physical bench ranging next |
| 4 — Android Camera AI | Samik; Spandan artifacts; Rishav shell | In progress | Tested helpers and model backend; concrete CameraX analyzer and application event wiring still pending |
| 5 — USB Integration | Rohan; Rishav lifecycle integration | In progress | UsbSensorAdapter unit tests pass; Android USB host verified; live cable streaming and lifecycle hookup next |
| 6 — Fusion + Risk | Rishav; Samik/Rohan inputs | In progress | RiskEngine contracts defined; sensor <= 50cm emergency STOP wiring & staleness watchdog next |
| 7 — Voice UX | Rishav; all producers support cancellation | In progress | SpeechArbiter contracts defined; offline Android TextToSpeech wrapper with <= 250ms STOP cancel next |
| 8 — Memory + Final Search | Rishav integration; Subham client; Samik search | Planned | Real refresh/selection/arrival/search flow, plus independent nearby search |
| 9 — Calibration and Acceptance | All; Rishav consolidates | Planned | Complete gates, evidence review, supervised rehearsal and explicit owner acceptance |

## 5. User-Experience Readiness

| Experience | Required pieces | Current state | Must prove before claiming readiness |
|---|---|---|---|
| Standalone walking | Android Mobility, USB sensor, risk, voice and lifecycle | Not verified | Operates without laptop/internet/target; known and unlabeled hazards; valid degraded behavior and Stop |
| Direct nearby search | Android Locate, target selection, camera and search/voice lifecycle | Not verified | Local search without laptop; target confirmation, direction, timeout and cancellation |
| Memory-assisted finding | Laptop scan/memory/API, client, explicit arrival, Android Locate | Not verified | Correct last-seen query, stale/ambiguous handling, target transfer and final search |

No automatic route planning, side-escape guidance, universal obstacle detection or safety certification is claimed. Optional features remain outside baseline readiness.

## 6. Acceptance Register

Exact criteria remain in PRD Section 29. Lead/reviewer assignments match shared guidance. Evidence links, configuration/hashes, raw measurements and review must be recorded before changing NOT RUN to PASS.

| Gate | Lead | Reviewer | Result | Evidence / review |
|---|---|---|---|---|
| AC-01 — Standalone offline run | Rishav | Subham | NOT RUN | Pending |
| AC-02 — Locate quality on both devices | Spandan | Rishav | NOT RUN | Pending |
| AC-03 — Hard Scan | Subham | Samik | NOT RUN | Pending |
| AC-04 — Memory/API cases | Subham | Spandan | NOT RUN | Pending |
| AC-05 — Mobile performance | Samik | Rohan | NOT RUN | Pending |
| AC-06 — Physical obstacle to STOP/audio | Rohan | Subham | NOT RUN | Pending |
| AC-07 — Supervised obstacle encounters | Rishav | Subham | NOT RUN | Pending |
| AC-08 — Sensor measurements | Rohan | Samik | NOT RUN | Pending |
| AC-09 — USB delivery/reconnects | Rohan | Samik | NOT RUN | Pending |
| AC-10 — Risk/transport replay | Rishav | Subham | NOT RUN | Pending |
| AC-11 — Speech/cancellation timing | Rishav | Samik | NOT RUN | Pending |
| AC-12 — Clear/degraded/failure cases | Samik | Spandan | NOT RUN | Pending |
| AC-13 — Android nearby/final search | Samik | Subham | NOT RUN | Pending |
| AC-14 — Full memory/search integration | Subham | Rohan | NOT RUN | Pending |
| AC-15 — Lifecycle/late callbacks | Rishav | Spandan | NOT RUN | Pending |
| AC-16 — Accessible baseline flows | Rishav | Spandan | NOT RUN | Pending |

Project-owner acceptance: **not recorded**. Documentation link/schema checks do not satisfy any of these product gates.

## 7. Handoff Register

| Handoff | Producer | Receiver | State | Missing deliverable / evidence |
|---|---|---|---|---|
| H1 — Model artifacts | Spandan | Subham and Samik | Partial smoke evidence | Corrected Android model load/forward checks; decoding reference comparison, laptop receipt and peer review pending |
| H2 — Mobile perception | Samik | Rishav | Helpers tested; integration pending | No live camera producer or receiver event wiring; prior integration claim withdrawn |
| H3 — Sensor events | Rohan | Rishav | Verified | Hardware spec, ESP32 firmware, serial tool (20/20 PASS), replay harness (10/10 PASS), UsbSensorAdapter unit tests pass |
| H4 — Memory | Subham | Rishav | Verified | REST API schemas, response fixtures (8/8 tests PASS), SQLite schema, MemoryClientContract delivered |
| H5 — Search events | Samik | Rishav | Helpers tested; integration pending | Search regressions pass; live Locate/Found/speech wiring and receiver re-review pending |
| H6 — Build integration | Rishav | All | Verified | Master app shell v0 merged on main (commit 78f3187); clean build & live execution on OPPO CPH2753 |

## 8. Decisions and Blockers

No active implementation blocker has been confirmed. The following prerequisites are resolved:

| Open item | Resolver | Required next evidence |
|---|---|---|
| Actual phone and Android version | Rishav, Samik, Rohan | RESOLVED: OPPO CPH2753, MediaTek MT6835 (arm64-v8a), Android 16 (API 36), Camera2 Level FULL, rear camera orientation 90°, USB host active, Google TTS installed |
| Board, GPIOs, power, USB interface and mount | Rohan | RESOLVED: Recorded in docs/rohan/hardware-spec.md: ESP32-S3 DevKit, GPIO 4 (TRIG), GPIO 5 (ECHO via 1k/2k divider), 5V phone OTG (< 80mA), chest rig |
| Demo Locate classes/aliases | Spandan with Subham/Samik | RESOLVED: Object 1 = "keys" (aliases: key, keychain, car keys), Object 2 = "wallet" (aliases: billfold, purse, cardholder) recorded in models/metadata/demo_classes.json |
| Model/runtime/export compatibility | Spandan, Samik | Corrected load/forward execution tested on smoke artifacts; reference detection comparison and full pipeline qualification pending |
| App package paths and event/session contracts | Rishav with producers | RESOLVED: dev.navisense.contracts.* and session architecture merged in commit 78f3187 |
| Laptop camera zones and permitted memory network | Subham | In progress: Laptop tabletop capture per CAPTURE_CHECKLIST.md; local HTTP subnet 192.168.43.0/24 |

When a real blocker appears, record: affected phase/gate, observed failure, evidence, resolver, unblock condition and useful independent work. Do not mark the entire project blocked when another subsystem can progress.

## 9. Update Procedure

1. Update this tracker when actual work, a handoff, a test result or an accepted decision changes state. This file does not monitor progress automatically.
2. Record who performed the work, source revision/paths, artifact hashes, exact commands, test setup and evidence location. If work exists elsewhere, link/import evidence before claiming it here.
3. Separate automated fixtures, physical-device measurements, supervised runs, receiver review and owner acceptance. Record sample counts and failures; keep earlier failed results when retesting.
4. Update relevant member/phase/handoff rows and the gate register together. Keep owners aligned with shared guidance; record confirmed Phase 0 decisions here; the frozen guidance decision table remains the initial baseline.
5. Preserve unchanged PRD thresholds. Changed code/model/configuration invalidates affected prior evidence until the required retests run; record that explicitly rather than retaining a misleading PASS.
6. Use the following entry in the activity log; avoid secrets and continuous raw recordings in status documents.

```text
Date/time and author:
Member / phase / handoff / gate:
Previous state → new state:
Work performed by:
Source revision and artifact paths/hashes:
Commands and test environment:
Result, sample counts, timing and evidence links:
Limitations / outstanding failures:
Receiver or reviewer and review result:
Next action / blocker resolver:
```

## 10. Activity Log

| Date | Recorded by | Change | Evidence / interpretation |
|---|---|---|---|
| 2026-09-14 | Codex | Requirements, hardening and user-assigned five-member guidance present | PRD and six guidance documents inspected; documentation exists, engineering acceptance not established |
| 2026-09-14 | Codex | Implementation-state tracker initialized | Member work/phases/handoffs planned; all 16 product gates NOT RUN; no progress invented |
| 2026-09-14 | Samik | Implemented Phase 0 perception contracts, transforms, quality gating, tracking, and target search | Created dev.navisense Android module; authored H2 & H5 contracts; 13/13 unit tests passed in 49ms |
| 2026-09-14 | Rohan | Hardware spec, ESP32 firmware, serial tool, replay test suite & Android USB module implemented | docs/rohan/hardware-spec.md, esp32/navisense_sensor/, scripts/test_sensor_serial.py (20/20 PASS), scripts/verify_sensor_fusion_replay.py (10/10 PASS), android/app/src/main/java/dev/navisense/usb/ |
| 2026-09-15 | Subham | Delivered S04/H4 laptop API schemas, response fixtures (8/8 passed), SQLite schema, and MemoryClientContract | laptop/api/schemas.py, laptop/tests/fixtures/responses.json, dev.navisense.networking.MemoryClientContract |
| 2026-09-15 | Samik | Physical qualification phone verified and app-debug.apk successfully launched | OPPO CPH2753 (MT6835, Android 16 API 36, Camera2 FULL, 90° rear sensor, USB host active, Google TTS installed); app UI running live |
| 2026-09-15 | Spandan | Delivered S02/H1 smoke models, metadata contracts, reference fixture, capture checklist, and evaluation scripts | models/smoke/, models/metadata/, models/fixtures/, datasets/CAPTURE_CHECKLIST.md, android/app/src/main/assets/models/, scripts/verify_smoke.py (100% PASS), scripts/evaluate_ac02.py |
| 2026-09-15 | Samik | Implemented PyTorchLiteInferenceBackend and benchmarked S02/H1 models on OPPO CPH2753 | dev.navisense.ModelBenchmarkTest: Locate load 31ms, inf 2.8ms; Mobility load 317ms, inf 40ms; switching 80ms (0 leaks, 0 OOM). 3/3 tests PASS |
| 2026-09-15 | Rishav | Integrated S01 / H6 Master Application Shell on main | Commit 78f3187: MainActivity, SessionCoordinator, core contracts, UI layout, 25/25 unit tests pass |
| 2026-09-15 | Samik | Phase 0 Exit Review & Phase 1–4 Task Assignments Dispatched | Formally exited Phase 0 as VERIFIED; kicked off concurrent Phases 1–4 for all 5 subsystems |

## 11. Schedule and Governance State

| Item | Current state |
|---|---|
| Event duration | User specified 24 hours |
| Actual start/date/timezone | Unverified: chat timestamps and T+ values conflict; do not infer current elapsed time |
| Master/individual implementation plans | Fully aligned and active |
| Shared breaks | Planned T+06:00–06:30, T+12:00–13:00, T+18:00–18:30 |
| Staged handoffs | S01–S04 delivered & verified; S05–S08 in progress |
| Candidate freeze | Planned T+15:30; pre-stage datasets and evaluation harnesses |
| Final evidence review | Planned T+23:30–23:45; no product acceptance recorded |
| Documentation freeze | PRD file serves as README; main guidance also frozen under AGENTS.md |
| Communication branch/log | Active and synchronized; append-only protocol strictly enforced |
| Source implementation | Active on main across all subsystems; 25/25 Android unit tests passing |

## 12. Mandatory Phase Review Register

Entry status: PENDING / READY / READY-FIXTURES / BLOCKED.
Exit status: PENDING / VERIFIED / RETURNED.
READY-FIXTURES must name allowed independent work and cannot authorize real-device claims.
A VERIFIED phase exit is not whole-product acceptance.

| Phase | Lead | Peer reviewer | Entry | Exit | Actual time / permitted scope / evidence |
|---|---|---|---|---|---|
| 0 | Rishav | Subham | READY-FIXTURES | RETURNED | Earlier exit claim withdrawn; named peer review and physical compatibility evidence outstanding |
| 1 | Spandan | Samik and Subham | READY | PENDING | Smoke exports verified; AC-02 harness ready; datasets capture and custom Locate training underway |
| 2 | Subham | Rishav | READY | PENDING | API schemas, SQLite spec and response fixtures verified; webcam adapter & 2.0s Hard Scan underway |
| 3 | Rohan | Rishav | READY | PENDING | Firmware, electrical spec, serial tests and UsbSensorAdapter verified; physical bench ranging underway |
| 4 | Samik | Rishav | READY-FIXTURES | PENDING | User authorized review fixes to owned helpers/tests; AI prepared scope only, Rishav acknowledgement pending; no live integration exit |
| 5 | Rohan | Rishav | READY | PENDING | UsbSensorAdapter unit tests pass; physical OTG streaming and lifecycle integration underway |
| 6 | Rishav | Samik and Rohan | PENDING | PENDING | RiskEngine contracts defined; sensor <= 50cm emergency STOP wiring next |
| 7 | Rishav | Samik | PENDING | PENDING | SpeechArbiter contracts defined; offline Android TextToSpeech wrapper next |
| 8 | Rishav | Rohan | PENDING | PENDING | Real refresh/selection/arrival/search flow |
| 9 | Rishav | Assigned gate reviewers and project owner | PENDING | PENDING | Complete gates, evidence review, supervised rehearsal and explicit owner acceptance |

For entry record: phase/objective, owner, source state, frozen hash check, prerequisites and receiver receipts, tests to execute, permitted scope, open blocker/resolver, reviewer and actual decision time.
For exit record: owned changes/hashes, commands and results, fixture/device distinction, receiver verdict, unresolved defects, downstream readiness, frozen hash recheck, reviewer and actual decision time. Never overwrite failed evidence with an unexplained PASS.

### Documentation Update Record

2026-09-14 — Codex prepared the 24-hour master and five individual execution plans; added communication-branch rules and mandatory entry/exit governance. This is documentation work only. User assignments and existing NOT RUN product gates are retained.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.

## Authorized README rename and freeze record — 2026-09-14

The user explicitly requested renaming the canonical PRD to README.md everywhere and a hard rule prohibiting README changes. Codex renamed it to docs/README.md, updated Markdown references (including the frozen guidance link), and strengthened AGENTS.md. README contents are byte-identical. This is documentation work only; implementation, physical checks, receiver reviews and product acceptance remain unchanged.

| Frozen artifact | Before SHA-256 | After SHA-256 | Authorized scope |
|---|---|---|---|
| docs/README.md | 8512F5804A61D3F93D695B55D718127734C251415CF8B5379473A482B98052D2 | 8512F5804A61D3F93D695B55D718127734C251415CF8B5379473A482B98052D2 | Filename only; no content changes |
| docs/guidance.md | A4F38B5973FD30DEB31E0314F1E74BEBB6842CBEA7AED15C4E969E071ED8BAD6 | 6DCF13CD6C5A6DDC22ABA076DBF79CB830F81DCE48F103DDB6FEC0A6BA352EEA | Canonical README link only |

The hashes above and AGENTS.md define the current frozen baseline. Future changes require explicit user authorization for the named frozen file; ordinary implementation and progress updates must preserve both files.

## Documentation folder organization — 2026-09-14

At the user's request, Codex grouped each member's documents in docs/subham, docs/samik, docs/rishav, docs/spandan and docs/rohan. Each folder contains guidance.md and implementation-plan.md. Shared README/PRD, guidance, implementation plan and implementation state remain directly in docs; AGENTS.md remains at the repository root. All relative links were updated.

The requested relocation required only five individual-guide link target changes in each frozen document. Product requirements, ownership, phase timing, acceptance criteria and freeze rules are unchanged. This authorization applies only to this folder reorganization, not future contract edits.

| Frozen file | Previous SHA-256 | Current SHA-256 |
|---|---|---|
| docs/README.md | 8512F5804A61D3F93D695B55D718127734C251415CF8B5379473A482B98052D2 | 54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA |
| docs/guidance.md | 6DCF13CD6C5A6DDC22ABA076DBF79CB830F81DCE48F103DDB6FEC0A6BA352EEA | A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E |

Documentation organization is complete; implementation and acceptance statuses are unchanged. AGENTS.md records the current frozen checksums.

## Review remediation — 2026-09-15

Final retest after benchmark wording/cache synchronization changes: 44 JVM tests and 4 device tests pass. Locate pipeline mean 48.8 ms (six forward calls including warm-up); Mobility single sample 185 ms (one forward call). Loads 28 ms and 300 ms. Earlier corrected run below is retained for comparison. Both frozen hashes verified unchanged.

Actor: Codex, on Samik's explicit request to resolve review findings. Base source c71e3cf. Scope: owned inference/search helpers, regression tests and correction of status claims. No named peer acknowledgement is impersonated.

Earlier 2.8 ms Locate / 40 ms Mobility claims measured rejection of uniform imagery and are withdrawn wherever quoted above as historical activity. Earlier zero-leak claims were not measured. The corrected benchmark requires USABLE results and counts completed forward calls. The first corrected device run exposed two failures: PyTorch required a direct buffer. After fixing allocation, 44 JVM tests and 4 OPPO CPH2753 instrumentation tests passed using `android/gradlew.bat testDebugUnitTest connectedDebugAndroidTest`. Recorded synthetic pipeline timing (quality + preprocessing + forward + decode): Locate 57.8 ms mean of five samples after warm-up; Mobility 189 ms single sample. Loads: 28 ms / 295 ms. These samples are not AC-05, model accuracy or live camera measurements.

Letterboxing now preserves aspect ratio with 114 padding and shares the inverse transform's geometry; portrait RGB and landscape padding fixtures pass. Search requires ordered, fresh, distinct capture/frame IDs, Locate mode/model identity, geometry reset and one-to-one matching per frame. Cache replacement is atomic on each asset load, including existing non-empty files; a device test replaces stale bytes and verifies the packaged bytes are restored. Native model resources are destroyed on close.

H2/H5 remain awaiting concrete CameraX producer and Rishav integration/review. The status defect is corrected; live CameraX is not claimed implemented. Next owner actions: Samik implements owned camera producer; Rishav wires readiness/watchdog/session/risk/search outputs and reviews receipts. All 16 acceptance gates remain NOT RUN. Frozen hashes match AGENTS.md before edits; verify again at commit. Original failed benchmark attempt retained here; later retests do not erase it.
