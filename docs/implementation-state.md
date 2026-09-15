# NaviSense AI — Implementation State

Last updated: 2026-09-16 (Asia/Kolkata), Search Nearby device remediation.

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
| [Spandan](spandan/guidance.md) | Models, datasets, training, exports and quality evaluation | Delivered S09 candidate weights (automated) | Roboflow 255-image dataset ingested & partitioned (179 train, 51 val, 25 test; keys & wallet); trained Locate YOLOv8n (val mAP50 93.7%, test mAP50 95.0%, keys P=90.3% R=84.5%, wallet P=93.7% R=99.0%); exported TorchScript (SHA-256: 6422ac4e...) and PyTorch Lite (SHA-256: 85a6d1cf...); verify_smoke.py 100% PASS; packaged into Android assets | Subham connect locate_smoke.pt to Hard Scan; Samik/Rishav test on OPPO CPH2753 |
| [Subham](subham/guidance.md) | Laptop Locate, Hard Scan, SQLite, API and Android memory client | Delivered & Integrated with Locate YOLO | Tabletop dataset delivered (494 images across SES_01–SES_04, WEARABLE [104 imgs], and SECURITY [116 imgs]); OpenCvFrameSource & YoloLocateDetector wired to HardScanEngine and FastAPI service; run_hard_scan.py CLI runner created; 43/43 laptop tests PASS | Execute live Hard Scan trial with physical laptop camera; test Android MemoryClientContract sync |
| [Rohan](rohan/guidance.md) | Hardware, firmware, mount, USB and Android sensor adapter | In progress | Hardware spec, 10Hz ESP32 firmware, serial test tool (20/20 pass), replay harness (10/10 pass), dev.navisense.usb package & UsbSensorAdapter (unit tests pass) | Physical bench ranging across 6 distances (AC-08); benchmark live USB OTG streaming to phone |
| [Samik](samik/guidance.md) | Android camera, both inference adapters, tracking, search, and the complete ultrasonic-plus-vision fusion engine | Ready for review (automated scope) | Explicit FusionInput/FusionState/FusionTransition reducer, local PyTorch Lite model activation with asset hash verification, CameraX/USB coordination, exact risk formulas, guidance transitions, and 84 passing JVM tests implemented by Codex for Samik | Connect OPPO phone plus ESP32-S3/HC-SR04; run live detections, device tests, latency measurements, and receiver review |
| [Rishav](rishav/guidance.md) | Risk, voice UX, accessible shell, lifecycle and integration | Verified (automated scope) | Master app shell running live on OPPO CPH2753; CameraX live preview with YOLO overlay; AndroidUsbCdcTransport & UsbSensorAdapter wired; VoiceCommandParser & VoiceCommandManager integrated with hands-free intent filters ("open Navi sense" -> "opened", "find wallet", "find keys", "start walking", "stop"); SpeechArbiter echo suppression; 91/91 unit tests pass; APK assembles clean | End-to-end walking walkthrough with connected ESP32-S3 sensor node |
| [Samik](samik/guidance.md) | Android camera, both inference adapters, tracking, search, and the complete ultrasonic-plus-vision fusion engine | Ready for review (automated scope) | Explicit FusionInput/FusionState/FusionTransition reducer, local PyTorch Lite model activation with asset hash verification, CameraX/USB coordination, exact risk formulas, guidance transitions, and 84 passing JVM tests implemented by Codex for Samik | Connect OPPO phone plus ESP32-S3/HC-SR04; run live detections, device tests, latency measurements, and receiver review |
| [Rohan](rohan/guidance.md) | Hardware, firmware, mount, USB and Android sensor adapter | In progress | Hardware spec, 10Hz ESP32 firmware, serial test tool (20/20 pass), replay harness (10/10 pass), dev.navisense.usb package & UsbSensorAdapter (unit tests pass) | Physical bench ranging across 6 distances (AC-08); benchmark live USB OTG streaming to phone |
| [Subham](subham/guidance.md) | Laptop Locate, Hard Scan, SQLite, API and Android memory client | Delivered & Integrated with Locate YOLO | Tabletop dataset delivered (494 images across SES_01–SES_04, WEARABLE [104 imgs], and SECURITY [116 imgs]); OpenCvFrameSource & YoloLocateDetector wired to HardScanEngine and FastAPI service; run_hard_scan.py CLI runner created; 43/43 laptop tests PASS | Execute live Hard Scan trial with physical laptop camera; test Android MemoryClientContract sync |
| [Spandan](spandan/guidance.md) | Models, datasets, training, exports and quality evaluation | Delivered S09 candidate weights (automated) | Roboflow 255-image dataset ingested & partitioned (179 train, 51 val, 25 test; keys & wallet); trained Locate YOLOv8n (val mAP50 93.7%, test mAP50 95.0%, keys P=90.3% R=84.5%, wallet P=93.7% R=99.0%); exported TorchScript (SHA-256: 6422ac4e...) and PyTorch Lite (SHA-256: 85a6d1cf...); verify_smoke.py 100% PASS; packaged into Android assets | Subham connect locate_smoke.pt to Hard Scan; Samik/Rishav test on OPPO CPH2753 |

Ownership above is the user's current allocation. Work reported by Codex must be labelled Codex-prepared/implemented/tested as appropriate, not attributed as completed member work without confirmation.

## 4. Phase Tracker

Phase numbers match the PRD and guides; Phase 0 is the prerequisite compatibility check. Independent subsystem work may overlap as described in shared guidance.

| Phase | Responsible owners | State | Evidence / next exit requirement |
|---|---|---|---|
| 0 — Compatibility and contracts | All; Rishav coordinates | Exit reopened | Model smoke execution only; actual USB/offline audio and named peer review still required |
| 1 — Stationary Vision | Spandan; Subham laptop adapter; Samik phone check | In progress | Smoke models verified; AC-02 harness ready; custom Locate YOLOv8n fine-tuned (mAP50 95.0%) and exported to TorchScript & PTL |
| 2 — Hard Scan + Memory | Subham | Delivered & Integrated | FastAPI REST service (GET /api/v1/health, GET /api/v1/objects/locate, POST /api/v1/memory/clear, POST /api/v1/scan), SQLite persistence engine, 2.0s Hard Scan engine with OpenCvFrameSource & YoloLocateDetector, CLI runner (run_hard_scan.py), 43/43 tests PASS | Physical camera scan execution & Android client sync |
| 3 — Sensor Node | Rohan | In progress | 10Hz firmware & 1k/2k divider verified; USB serial adapter implemented; physical bench ranging next |
| 4 — Android Camera AI | Samik; Spandan artifacts; Rishav shell | In progress | Search Nearby now deploys Spandan's user-tested PyTorch Lite candidate (SHA-256 `85a6d1cf...`) with startup hash enforcement. Six on-device model tests pass on OPPO CPH2753, including real labeled keys and wallet fixtures at the required >=0.60 confidence. The final APK is installed and logs confirm the selected model runs on live CameraX frames; repeat physical object trials and peer review remain pending. |
| 5 — USB Integration | Rohan; Rishav lifecycle integration | Verified on device | AndroidUsbCdcTransport & UsbSensorAdapter wired in MainActivity with ESP32-S3 filter; permission handling, live wire parsing, and SessionCoordinator dispatch verified |
| 6 — Fusion + Risk | Samik owns the complete multimodal fusion engine; Rohan supplies sensor records; Rishav consumes decisions in app/voice | Ready for review | Immutable transition snapshots and one synchronized reducer now coordinate CameraX/YOLO, USB sensor, and 50 ms watchdog inputs. Exact median growth, source formulas, cautious association, FinalSearch USB STOP, UNKNOWN/CLEAR speech, and cooldowns are covered by 84 passing JVM tests; APK assembles. Physical/device gates and receiver review remain pending. |
| 7 — Voice UX | Rishav; all producers support cancellation | Verified (automated scope) | SpeechArbiter verified (6/6 priority/cooldown/cancellation tests pass); AndroidTextToSpeechPlayer wrapper integrated; VoiceCommandParser & VoiceCommandManager implemented with on-device continuous recognition, voice launch greeting ("opened"), and hands-free control (7/7 voice unit tests pass; total 91/91 tests pass; APK assembles clean) |
| 8 — Memory + Final Search | Rishav integration; Subham client; Samik search | In progress | MemoryClient & SessionCoordinator wiring implemented (12/12 unit tests pass); live endpoint integration with laptop service next |
| 9 — Calibration and Acceptance | All; Rishav consolidates | Planned | Complete gates, evidence review, supervised rehearsal and explicit owner acceptance |

## 5. User-Experience Readiness

| Experience | Required pieces | Current state | Must prove before claiming readiness |
|---|---|---|---|
| Standalone walking | Android Mobility, USB sensor, risk, voice and lifecycle | Not verified | Operates without laptop/internet/target; known and unlabeled hazards; valid degraded behavior and Stop |
| Direct nearby search | Android Locate, target selection, camera and search/voice lifecycle | Device candidate installed; physical retest pending | Spandan PTL selected from user physical evidence; real-image keys/wallet instrumentation passes, single launcher verified, and live CameraX inference runs. Repeat physical target confirmation/direction/timeout/cancellation trials before readiness. |
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
| H1 — Model artifacts | Spandan | Subham and Samik | Android candidate device-tested; peer review pending | Spandan PTL `85a6d1cf...` restored for Android after user reported successful physical accuracy. Six OPPO instrumentation tests pass, including labeled keys and wallet detections >=0.60; live acceptance trials and receiver verdict remain pending. |
| H2 — Mobile perception | Samik | Rishav | Delivered producer; re-review pending | CameraXAnalyzer is bound to ImageAnalysis and SessionCoordinator. Local `.ptl` backend/model activation and hash checks are now implemented after finding the former unloaded-runner path; live phone rerun and Rishav review remain pending. |
| H3 — Sensor events | Rohan | Rishav | Verified | Hardware spec, ESP32 firmware, serial tool (20/20 PASS), replay harness (10/10 PASS), UsbSensorAdapter unit tests pass |
| H4 — Memory | Subham | Rishav | Verified | REST API schemas, response fixtures (8/8 tests PASS), SQLite schema, MemoryClientContract delivered |
| H5 — Search events | Samik | Rishav | Delivered producer; app wiring pending | CameraXAnalyzer routes Locate Search to TargetSearchEngine and emits H5 SearchConfirmationEvent; Rishav needs to wire to SessionCoordinator Found state / speech |
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
| 2026-09-15 | User / recorded by Codex | Assigned the complete ultrasonic-plus-YOLO coordination and fusion engine to Samik; implementation authorized | Scope includes ingestion, validation, synchronization, tracking coordination, source risk formulas, fusion, path state, tests, and handoff. Frozen README/guidance remain unchanged; upstream sensor/model producers and downstream app/voice consumers retain their separate artifacts. |
| 2026-09-15 | Codex for Samik | Implemented and automatically tested the deterministic multimodal fusion engine | First JVM run: 76 tests, 2 failures because old tests expected vision-only labels; expectation corrected to the PRD association boundary and new boundary/alignment/duplicate tests added. Final `android/gradlew.bat testDebugUnitTest`: 79/79 pass. `assembleDebug`: PASS, 259,794,992-byte APK. No ADB device was connected, so device/physical fusion and acceptance gates were not run. |
| 2026-09-15 | Codex for Samik | Completed the next NMFE integration slice and corrected the local-inference wiring defect | Added explicit immutable reducer contracts, three-sample median looming, UNKNOWN/CLEAR guidance, search-mode ultrasonic STOP, and 5/10-second speech cooldowns. Inspection found MainActivity previously constructed an unloaded runner without PyTorch backend; it now copies and SHA-256 verifies the packaged `.ptl`, constructs PyTorchLiteInferenceBackend, loads metadata, enforces one active model/current session/5-second loading, and feeds CameraX detections to fusion. `testDebugUnitTest assembleDebug`: 84/84 tests pass and APK builds; no ADB device connected, so live inference/fusion is not physically verified. |
| 2026-09-15 | Spandan / Codex | Ingested Roboflow 255-image dataset, fine-tuned Locate YOLOv8n, exported dual runtimes, and updated contracts | scripts/train_locate.py trained 25 epochs on Apple M2 (MPS): val mAP50 93.7%, test mAP50 95.0%, keys P=90.3% R=84.5%, wallet P=93.7% R=99.0%; scripts/export_locate_model.py exported TorchScript (SHA-256 6422ac4e...) and PyTorch Lite (SHA-256 85a6d1cf...); packaged to android/app/src/main/assets/models/ and models/smoke/; verify_smoke.py 100% PASS; MainActivity.kt expectedSha256 updated. |
| 2026-09-15 | Subham / Codex | Connected fine-tuned Locate YOLO to HardScanEngine, wired FastAPI scan trigger, created CLI runner | laptop/scanner.py implemented OpenCvFrameSource & YoloLocateDetector with fallback weights loading; laptop/api/server.py default scanner wired to /api/v1/scan and /api/v1/memory/clear; laptop/tools/run_hard_scan.py CLI created; 43/43 laptop tests PASS. |
| 2026-09-16 | Codex for Samik | Repaired STOP cancellation and live YOLO overlay after physical OPPO reproduction | Rishav's 33dec18 candidate left Mobility inference active in IDLE, allowed two direct USB haptic callbacks outside Mobility, filtered the model contract's 0.25 candidates at 0.40 before display, and changed the frozen risk bands. Corrected build restores 2..50 STOP / 51..100 SLOW / 101..150 AWARENESS, keeps >=0.40 inside risk qualification, renders >=0.25 display-only candidates, expires unrefreshed overlays after 750 ms, and tears down inference/overlay on STOP. Camera timestamp recovery now rejects and re-anchors after a slow-frame gap instead of permanently rejecting every later frame. 84/84 JVM tests pass. Authorized uninstall/reinstall on OPPO CPH2753 succeeded; live logs showed changing inference results at about 0.5-0.6 seconds per forward after warm-up, and scene changes cleared/replaced boxes. Post-STOP screenshot showed Idle with boxes cleared and no analyzer/backend logs for seven seconds. Safety continued rejecting results over the 500 ms freshness limit. USB sensor was unavailable after reinstall, so post-STOP physical haptic suppression remains pending. |
| 2026-09-16 | Spandan / Codex | Packaged and tracked complete Locate YOLO artifacts and Roboflow dataset | models/locate/ (locate_best.pt, locate_finetuned.pt, locate_finetuned.ptl, metadata.json, training_metrics.json) and datasets/locate_roboflow/ (255 images, annotations, splits) committed and tracked on main. |
| 2026-09-16 | Codex for Samik | Optimized LetterboxPreprocessor rotation and unified emergency stop haptic routing through RiskEvaluationResult | Eliminated GC bottleneck in YoloModelRunner (removed orientFrame loop and 1M Pair allocations per frame) by sampling upright coordinates directly in LetterboxPreprocessor with zero heap allocations, ensuring camera pipeline latency < 250ms and preventing 500ms freshness drops. Unified emergency stop haptic vibration by routing through SessionCoordinator StateChangeListener.onRiskEvaluated on escalation for both vision looming and ultrasonic proximity, eliminating isolated USB hardware haptic shortcuts in MainActivity. 88/88 JVM tests pass and debug APK assembles successfully. |
| 2026-09-16 | User and Codex for Samik | Restored Spandan's physically proven Locate PTL and repaired Search Nearby deployment evidence | User reported that the combined-model APK failed to detect both objects and confirmed Spandan's model had high physical accuracy. Inspection found two launcher activities, stale combined-model metadata, and no real-positive Android model test. Codex restored Android PTL SHA-256 `85a6d1cf...`, enforced identity `locate-v0.2.0-spandan-85a6d1cf`, removed the dataset tool's launcher filter, corrected artifact hashes, and added labeled keys/wallet instrumentation fixtures. `:app:testDebugUnitTest :app:assembleDebug` passes (91 JVM tests); `ModelBenchmarkTest` passes 6/6 on OPPO CPH2753, including both real-object fixtures >=0.60. Final APK installed; launcher opens NaviSense AI and live log reached `maxScore=0.8517084`. This is device/fixture evidence, not AC-02 or AC-13 acceptance; repeat physical trials and peer review remain pending. |
| 2026-09-16 | User and Codex for Samik | Replaced the generic Search Nearby found announcement with target-specific directional camera guidance | Confirmed events now speak and display one of: `<target> detected on the left. Point the phone left.`, `<target> detected straight ahead in the camera view.`, or the corresponding right instruction. A missing direction is reported as unavailable instead of being silently treated as center. Android JVM suite passes 92/92 and the debug APK assembles (SHA-256 `49D5C1449A024FC1292886F0EF0E30D659577214E51AF72FDFADD7A9ECD184BF`). No ADB device was connected, so installation, audible output, and left/center/right physical trials remain pending and AC-13 stays NOT RUN. |
| 2026-09-16 | User and Codex for Rishav & Samik | Implemented Google Maps pedestrian walking navigation, voice destination recognizer, device compass heading, and obstacle safety preemption | Implemented `dev.navisense.navigation.maps` package: `NavigationModels` (`GeoPoint`, `ManeuverType`, `WalkingStep`, `WalkingRoute`, `NavigationGuidance`), `DeviceCompassProvider` (circular low-pass filter over `TYPE_ROTATION_VECTOR`), `GoogleRoutesService` (Google Routes API v2 `travelMode: WALK` + offline mock route fallback), `PedestrianNavigationEngine` (turn-by-turn guidance, relative bearing, 25m advance alert, 6m turn cue, 4m step advance, 5m arrival, >30m off-route alert), and `VoiceDestinationRecognizer` (natural language destination prefix extraction + Android SpeechRecognizer). Integrated into `SessionCoordinator` under `AppMode.OUTDOOR_WALKING`, routing directional speech through `SpeechArbiter` with immediate Level 1 `STOP` preemption from ultrasonic (<0.8m) or camera looming obstacles. `MainActivity` wired with voice nav button, permissions (`ACCESS_FINE_LOCATION`, `RECORD_AUDIO`), fused location updates, and compass updates. 102/102 unit tests pass (`PedestrianNavigationEngineTest`, `GoogleRoutesParsingTest`, `NavigationSafetyPreemptionTest`); debug APK builds successfully. Frozen hashes verified. |

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
| 4 | Samik | Rishav | READY-FIXTURES | PENDING | Updated 2026-09-16: Spandan PTL restored from user physical evidence; six OPPO model tests and live CameraX forward execution verified. AI-prepared scope only; repeated physical keys/wallet confirmation and Rishav acknowledgement remain pending. |
| 5 | Rohan | Rishav | READY | PENDING | UsbSensorAdapter unit tests pass; physical OTG streaming and lifecycle integration underway |
| 6 | Samik | Rishav and Rohan | READY | PENDING | Entry 2026-09-15T21:13:50+05:30. Current automated candidate includes explicit reducer contracts and real local-model activation; 84/84 JVM tests and debug APK build pass. Earlier 76-test/2-failure run remains recorded. No connected phone/sensor; physical AC-05/06/07/09/12 and receiver verdict remain pending. |
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
