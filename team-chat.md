# NaviSense AI — Team Communication Log

This file is the append-only record of all team chats, progress updates, handoffs, reviews, blockers, and architectural decisions on the `communication` branch, governed by [docs/AGENTS.md](docs/AGENTS.md).

## Communication branch — mandatory

All work/progress/handoff/review/decision messages belong in communication's append-only team-chat.md. Only chat/decision-log changes go there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized.

---

### Entry Log

```text
Entry ID: KICKOFF-2026-09-14-001 / 2026-09-14T20:45:00+05:30 / T+00:00
Author and type: Rishav | REVIEW
Phase / step / S-instance / H-contract: Phase 0 / Entry Review
Message and requested action:
Kickoff Phase 0 compatibility check. Verified frozen file hashes for docs/README.md and docs/guidance.md under AGENTS.md. Team ownership confirmed: Spandan (Models), Subham (Laptop Memory/API), Rohan (Hardware/Firmware/USB), Samik (Android Camera/AI/Search), Rishav (Risk/Voice/Integration).
Entry status: READY for Phase 0 setup and smoke reviews.
Source revision and evidence reference: main branch root
Recipient(s): Spandan, Subham, Rohan, Samik
For response: referenced entry ID and ACK
```

```text
Entry ID: RISHAV-2026-09-14-002 / 2026-09-14T21:45:00+05:30 / T+01:00
Author and type: Rishav | HANDOFF
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / S01 / H6
Message and requested action:
Delivering S01: Android app shell v0 foundation, package roots under dev.navisense, session_generation authority, and shared producer contracts interface. All producers inspect and build against these types.
Source revision and evidence reference: android/app/src/main/java/dev/navisense/
Recipient(s): Spandan, Subham, Samik, Rohan
For response: referenced entry ID and ACK
```

```text
Entry ID: SAMIK-2026-09-14-003 / 2026-09-14T22:15:00+05:30 / T+01:30
Author and type: Samik | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / S01
Message and requested action:
ACK RISHAV-2026-09-14-002. Confirmed dev.navisense package structure and session_generation contract. Setting up Android camera, inference, tracking, and search modules.
Source revision and evidence reference: commit on main
Recipient(s): Rishav
For response: referenced entry ID and ACK
```

```text
Entry ID: SAMIK-2026-09-14-004 / 2026-09-14T22:30:00+05:30 / T+01:45
Author and type: Samik | HANDOFF
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / H2, H5
Message and requested action:
Delivered mobile perception and target search contracts and foundational pipeline:
1. dev.navisense.contracts.PerceptionContracts (Handoff H2): NormalizedRect with IoU, DetectedObject, FrameQualityStatus, MobilePerceptionEvent with monotonic timestamps and sessionGeneration.
2. dev.navisense.contracts.SearchContracts (Handoff H5): TargetDirection (LEFT/CENTER/RIGHT), SearchStatus, SearchEvent.
3. dev.navisense.camera.CoordinateTransformer: letterbox undoing and upright 0/90/180/270 deg rotation.
4. dev.navisense.camera.FrameQualityChecker: PRD §17.2 dark/covered (mean < 10) and featureless (stdDev < 5) rejection.
5. dev.navisense.tracking.VisualTracker: 1-to-1 greedy IoU matching (>= 0.30), 500 ms expiry, and 0.5s area growth tracking (>= 25%).
6. dev.navisense.search.TargetSearchEngine: PRD §20 3-of-5 confirmation within 1.0s, direction mapping, 15s timeout, and session generation invalidation.
7. Verification: 13/13 automated unit tests PASSED via .\gradlew.bat testDebugUnitTest.
Requested action: Rishav review contracts H2 & H5; Spandan prepare S02 smoke model exports for on-phone loading.
Source revision and evidence reference: commit c3a3a49 on main; PerceptionUnitTests.kt
Recipient(s): Rishav, Spandan
For response: referenced entry ID and VERIFIED / RETURNED
```

```text
Entry ID: SAMIK-2026-09-15-005 / 2026-09-15T09:30:00+05:30 / T+02:00
Author and type: Samik | CHAT
Phase / step / S-instance / H-contract: Phase 0 / S02 prerequisite
Message and requested action:
GitHub repository initialized at https://github.com/SamikSwarupBiswal/NaviSense-AI with main and communication branches. Standing by for Spandan's S02 smoke models (Locate + Mobility YOLO in TFLite format) to execute on-phone load and latency check.
Source revision and evidence reference: https://github.com/SamikSwarupBiswal/NaviSense-AI
Recipient(s): Team (Spandan, Subham, Rohan, Rishav)
```

```text
Entry ID: SAMIK-2026-09-15-006 / 2026-09-15T10:02:00+05:30 / T+02:15
Author and type: Samik | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Team setup
Message and requested action:
Invited all four teammates with write access to https://github.com/SamikSwarupBiswal/NaviSense-AI:
- rishav-bits (Rishav) -> Invitation sent
- JeansGit77 (Rohan) -> Invitation sent
- spandanjit-ai (Spandan) -> Invitation sent
- shubhusden (Subham) -> Invitation sent
Please accept repository invitations to collaborate on main and communication branches.
Source revision and evidence reference: https://github.com/SamikSwarupBiswal/NaviSense-AI/invitations
Recipient(s): rishav-bits, JeansGit77, spandanjit-ai, shubhusden
```

```text
Entry ID: SUBHAM-2026-09-15-007 / 2026-09-15T10:30:00+05:30 / T+02:40
Author and type: Subham | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / S01 Receipt, Setup Verification & Frozen Hash Check
Message and requested action:
1. ACK to SAMIK-2026-09-15-006: Subham (shubhusden) has accepted repository invitation and verified write access to https://github.com/SamikSwarupBiswal/NaviSense-AI.
2. ACK to KICKOFF-2026-09-14-001 (Rishav): Subsystem ownership verified: Laptop webcam Hard Scan pipeline, SQLite persistence, authenticated FastAPI local service, and Android memory networking client (dev.navisense.networking).
3. ACK to RISHAV-2026-09-14-002 (S01 / H6):
   - Inspected android/app/src/main/java/dev/navisense/contracts/Contracts.kt and SessionAuthority.
   - Validated SessionAuthority generation increments on mode switches/STOP and monotonic clock (MonotonicClock) contracts.
   - Confirmed package root dev.navisense.networking for Android memory client implementation.
4. Frozen file SHA-256 baseline verification under AGENTS.md §2:
   - docs/README.md: 54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA (MATCH - frozen contract intact).
   - docs/guidance.md: A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E (MATCH - frozen guidance intact).
5. Local Environment Verification:
   - Python 3.14 virtual environment verified (.venv\Scripts\python.exe).
   - Pytest suite verified: 8/8 unit tests passed on schemas and fixtures (test_api_schemas.py).
   - Laptop webcam interface available; OpenCV/capture test harness ready.
6. Phase 0 Entry Status: READY-FIXTURES (Laptop REST API schemas established, PRD v1 fixtures validated, Android MemoryClient contract drafted).
Source revision and evidence reference: laptop/api/schemas.py, laptop/tests/test_api_schemas.py, android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt
Recipient(s): Rishav, Samik, Spandan, Rohan
For response: referenced entry IDs KICKOFF-2026-09-14-001, RISHAV-2026-09-14-002, SAMIK-2026-09-15-006 and ACK
```

```text
Entry ID: SUBHAM-2026-09-15-008 / 2026-09-15T10:35:00+05:30 / T+02:45
Author and type: Subham | HANDOFF
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / S04 / H4
Message and requested action:
Delivering Handoff S04 / H4 (Laptop Locate REST API schemas, response fixtures, SQLite schema specification, and Android MemoryClientContract interface):
1. Laptop REST API Specification (laptop/api/schemas.py - PRD §23):
   - GET /api/v1/health -> HealthResponse(service_status: "ok"|"degraded"|"error", camera_connected: bool, memory_records_count: int, active_profile_id: str)
   - GET /api/v1/objects/locate?query={name} -> LocateResponse(status, query_name, canonical_name, target_class, candidates, scan_id, observation_time_iso, age_seconds, camera_profile_id)
   - Strictly typed status enum: found, ambiguous, stale, historical_only, not_found, unsupported.
   - Candidate model with normalized bounding box [x1, y1, x2, y2] in [0.0, 1.0], confidence in [0.0, 1.0], coarse zone_id (zone_left, zone_center, zone_right), and optional human-readable zone_name.
2. PRD v1 Response Fixtures (laptop/tests/fixtures/responses.json):
   - Exact fixture payloads matching all 6 query outcomes and 2 health statuses.
   - Test validation: 8/8 unit tests passed (pytest laptop/tests/test_api_schemas.py) covering box coordinate normalization, stale age threshold (>60.0s), ambiguous multi-candidate isolation, profile invalidation, and malformed coordinate rejection.
3. Android Memory Client Contract (dev.navisense.networking.MemoryClientContract):
   - Sealed class LocateResult (Found, Ambiguous, Stale, HistoricalOnly, NotFound, Unsupported, NetworkError).
   - Enforces PRD §23/24 constraints: 2000 ms total timeout (MAX_TIMEOUT_MS = 2000L), 64 KiB payload cap (MAX_RESPONSE_BYTES = 65536L), 60.0s stale threshold (STALE_THRESHOLD_SECONDS = 60.0).
   - Accepts sessionGeneration: Long to ensure late network callbacks are discarded immediately upon navigation mode change or User STOP without mutating active app target or triggering speech.
   - Distinguishes network failure from not_found: network errors return typed LocateResult.NetworkError with HTTP status code and error description.
4. Authoritative SQLite Memory Schema (PRD §11/§12):
   - Tables: camera_profiles, scan_snapshots, observed_objects.
   - One-transaction atomic commit per 2.0s Hard Scan (10 frames, >= 0.60 confidence in >= 6 frames). Rollback on cancellation, profile change, or Clear history.
5. Proposed Camera Zone Profile (tabletop_cam_v1):
   - Non-overlapping horizontal zones: zone_left [0.0, 0.33], zone_center [0.33, 0.67], zone_right [0.67, 1.0].
   - Truthful "last seen" reporting without route invention.
Requested action: Rishav review S04 / H4 contracts and fixtures; confirm Android app integration hooks for MemoryClientContract.
Source revision and evidence reference: laptop/api/schemas.py, laptop/tests/fixtures/responses.json, android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt
Recipient(s): Rishav, Spandan, Samik
For response: referenced entry ID SUBHAM-2026-09-15-008 and VERIFIED / RETURNED
```

```text
Entry ID: SUBHAM-2026-09-15-009 / 2026-09-15T10:40:00+05:30 / T+02:50
Author and type: Subham | CHAT
Phase / step / S-instance / H-contract: Phase 0 / Inter-subsystem Information & Prerequisite Requests
Message and requested action:
Organized requirements and information needed from teammates for Phase 0 and Phase 1 transitions:
1. To Spandan (Models & Datasets Lead):
   - [Information Needed]: Baseline classes and alias mapping sign-off. Subham established "keys" and "wallet" as initial demo classes with aliases ("key", "keys", "house keys", "car keys", "wallet", "billfold", "purse") in models/locate/metadata.json. Spandan please confirm if any additional classes or aliases are planned for S02 / H1.
   - [Artifact Needed]: Smoke Locate model artifact for Laptop (S02 / H1, scheduled T+01:15–01:45). Please provide the laptop-compatible model export (PyTorch / ONNX / TFLite) and exact input tensor requirements (RGB 640x640, normalized [0, 1]) so Subham can integrate the stationary webcam detector adapter.
   - [Prerequisite Check]: Confirm detection confidence threshold (>= 0.60 per PRD §10) and coordinate format (normalized xyxy [0, 1]).
2. To Rishav (System Integration, Voice UX & Shell Lead):
   - [Contract Confirmation]: Verify MemoryClientContract interface in dev.navisense.networking matches your coordinator's expectations for triggering locateObject(queryName, sessionGeneration) during Voice UX object finding queries.
   - [Auth & Security]: PRD specifies session token / bearer token authentication for FastAPI. Confirm how the pre-shared bearer token will be injected/stored securely on Android (e.g. BuildConfig field / runtime secret) without committing raw tokens to git.
   - [Stale Callback Handling]: Confirm SessionAuthority.isValid(sessionGeneration) will be checked upon LocateResult arrival before any target selection or UI/speech transition occurs.
   - [Network Configuration]: Confirm target IP/subnet configuration (e.g. Wi-Fi hotspot subnet 192.168.43.0/24, laptop static IP 192.168.43.100:8000) for Android-to-laptop connectivity.
3. To Rohan (Hardware, Firmware & Sensor Lead):
   - [Information Needed]: Confirm network isolation and transport independence. Confirm sensor node communicates purely via USB CDC/OTG with the phone and does not touch the Wi-Fi/HTTP local subnet.
   - [Error Semantics]: Align error taxonomy between hardware ultrasonic sensor failures (e.g. USB disconnected, sensor unreadable) and laptop memory service errors (HTTP timeout, connection refused) so Rishav's voice arbiter handles both gracefully without ambiguous voice prompts.
4. To Samik (Android Perception & Search Lead):
   - [Handoff Alignment]: Review MemoryClientContract candidate format (MemoryCandidate with BoundingBox, confidence, zoneId). Confirm how TargetSearchEngine will consume target_class from LocateResult.Found when user arrives at the designated zone to trigger final search (PRD §20).
Source revision and evidence reference: models/locate/metadata.json, android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt, laptop/config/settings.py
Recipient(s): Spandan, Rishav, Rohan, Samik
For response: reply with relevant entry reference and confirmations / details.
```

```text
Entry ID: SAMIK-2026-09-15-007 / 2026-09-15T10:50:00+05:30 / T+02:20
Author and type: Samik | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Team setup
Message and requested action:
Invited collaborator jeans0177 (Rohan) with write access to https://github.com/SamikSwarupBiswal/NaviSense-AI.
Status update on repository collaborators:
- rishav-bits (Rishav) -> Accepted & Active
- spandanjit-ai (Spandan) -> Accepted & Active
- shubhusden (Subham) -> Accepted & Active
- jeans0177 / JeansGit77 (Rohan) -> Invitation Sent
Recipient(s): jeans0177, Team
```

```text
Entry ID: SAMIK-2026-09-15-008 / 2026-09-15T10:55:00+05:30 / T+02:55
Author and type: Samik | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / H5 Integration Alignment
Message and requested action:
1. Response to Subham (SUBHAM-2026-09-15-009, Item 4):
   - Reviewed MemoryClientContract candidate format. Confirming TargetSearchEngine (dev.navisense.search.TargetSearchEngine) is decoupled from laptop spatial coordinates: when Rishav's coordinator triggers arrival at the zone, it invokes TargetSearchEngine.startSearch(targetClass, sessionGeneration, startMonotonicMs) using the canonical target_class from LocateResult.Found.
   - Confirmation is evaluated strictly from live upright phone camera frames using on-device Locate YOLO (3-of-5 frames within 1.0s, confidence >= 0.60, IoU >= 0.30) and emits left/center/right image direction (TargetDirection) relative to phone view per PRD §20.
2. Standing by for Spandan's S02 model exports to benchmark phone inference and load time.
Source revision and evidence reference: dev.navisense.search.TargetSearchEngine
Recipient(s): Subham, Rishav, Spandan
For response: referenced entry ID SUBHAM-2026-09-15-009 and ACK
```

```text
<<<<<<< HEAD
Entry ID: RISHAV-2026-09-15-010 / 2026-09-15T11:10:00+05:30 / T+03:20
Author and type: Rishav | REVIEW
Phase / step / S-instance / H-contract: Phase 0 / S04 Receipt & Review (Subham H4) and H2/H5 Review (Samik)
Message and requested action:
1. Review of SUBHAM-2026-09-15-008 (S04 / H4 - Laptop REST API Schemas, SQLite Schema, PRD Fixtures, Android MemoryClientContract):
   - Status: VERIFIED FOR HANDOFF.
   - Checked laptop/api/schemas.py, laptop/tests/fixtures/responses.json, and android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt.
   - All 6 Locate response statuses (Found, Ambiguous, Stale, HistoricalOnly, NotFound, Unsupported) and NetworkError are cleanly typed and map directly into SessionCoordinator state transitions.
   - Enforced constraints verified: 2000 ms timeout cap, 64 KiB payload limit, 60.0s staleness threshold.
2. Technical Confirmations in response to SUBHAM-2026-09-15-009 (Item 2 for Rishav):
   - [Contract Confirmation]: Confirmed. MemoryClientContract.locateObject(queryName, sessionGeneration) is integrated into dev.navisense.networking and invoked by SessionCoordinator during Voice UX object location queries.
   - [Auth & Security]: Pre-shared bearer token will be injected via local.properties (navisense.api.key=...) mapped to BuildConfig.NAVISENSE_API_KEY at compile time, supplemented with an in-app debug configuration screen for live IP/token override, ensuring raw secrets are NEVER committed to git.
   - [Stale Callback Handling]: Confirmed. When locateObject completes, SessionCoordinator verifies sessionAuthority.isValid(sessionGeneration) before processing candidates, mutating mode, or queueing speech. Any callback from a stale generation (due to mode switch, timeout, or User Stop) is immediately and silently dropped.
   - [Network Configuration]: Confirmed. Android app manifest enables cleartext traffic (android:usesCleartextTraffic="true"). Default target endpoint is configured to http://192.168.43.100:8000/api/v1 for portable Wi-Fi hotspot operation, with editable base URL in debug settings.
3. Review of SAMIK-2026-09-14-004 & SAMIK-2026-09-15-008 (H2 & H5 - Mobile Perception & Target Search Contracts):
   - Status: VERIFIED FOR HANDOFF.
   - Unified PerceptionContracts.kt (NormalizedRect, DetectedObject, FrameQualityStatus, MobilePerceptionEvent) and SearchContracts.kt (TargetDirection, SearchStatus, SearchEvent) with Rishav's master app shell.
   - Reconciled bounding box math: WalkingCorridor now uses NormalizedRect directly, confirming central corridor bounds [0.30, 0.70] width, [0.30, 1.00] depth, and >= 20% intersection area ratio.
   - Confirmed TargetSearchEngine correctly evaluates 3-of-5 frames within 1.0s, handles 15.0s timeout, and drops evaluations on sessionGeneration mismatch.
Source revision and evidence reference: android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt, android/app/src/main/java/dev/navisense/contracts/PerceptionContracts.kt, android/app/src/main/java/dev/navisense/contracts/SearchContracts.kt, android/app/src/main/java/dev/navisense/navigation/WalkingCorridor.kt
Recipient(s): Subham, Samik, Spandan, Rohan
For response: referenced entry IDs SUBHAM-2026-09-15-008, SAMIK-2026-09-14-004, SAMIK-2026-09-15-008 and ACK
```

```text
Entry ID: RISHAV-2026-09-15-011 / 2026-09-15T11:15:00+05:30 / T+03:25
Author and type: Rishav | HANDOFF
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / S01 / H6
Message and requested action:
Delivering S01 / H6 Comprehensive Update — Master Android Application Shell, Session Authority, Core Contracts, Build Pipeline & Test Harness:
1. Master Build Configuration & Manifest (android/app/build.gradle.kts, AndroidManifest.xml):
   - SDK: compileSdk = 34, minSdk = 26, targetSdk = 34.
   - Features: ViewBinding enabled, Java 17 toolchain, debug suffix .debug.
   - Core dependencies wired: AndroidX Core, AppCompat, Material, ConstraintLayout, Lifecycle KTX 2.7.0, Coroutines 1.7.3, CameraX 1.3.1 (Samik), OkHttp 4.12.0 (Subham), JUnit 4.13.2.
   - Manifest permissions: CAMERA, WAKE_LOCK, INTERNET, FOREGROUND_SERVICE. Portrait locked; cleartext traffic enabled for local development.
2. Accessible UI Shell (dev.navisense.app.MainActivity):
   - Accessible baseline (PRD §13.6, AC-16): Minimum 48dp touch targets, high-contrast color scheme (Green/Red/Amber status badges), TalkBack accessibility announcements on every state change.
   - Screen keep-awake flag (FLAG_KEEP_SCREEN_ON) active during navigation.
   - Immediate User Stop button: Always prominent, zero confirmation dialogs, immediately silences all audio and resets coordinator to IDLE within <= 250 ms.
3. Global Session Authority & Coordinator (dev.navisense.app.SessionCoordinator, dev.navisense.contracts.SessionGeneration):
   - Monotonic 64-bit SessionGeneration authority: increments on mode start, mode transition, and User Stop.
   - Thread-safe token generation and validation (isValid(generation)).
   - Synchronously invalidates all pending asynchronous callbacks (vision inference, HTTP memory requests, search timeouts, speech queue).
   - Supported modes: IDLE, MOBILITY, FINAL_SEARCH, FOUND, PAUSED, ERROR.
4. Core Subsystem Contracts & Interfaces (dev.navisense.contracts, navigation, voice):
   - AppMode, PathStatus (CLEAR_OBSERVED, BLOCKED, UNKNOWN), RiskLevel (SAFE, CAUTION, STOP).
   - SensorEvent, SensorWireRecord, SensorHealth (PRD §14, AC-08/AC-09): Enforces ultrasonic distance <= 50 cm as critical close STOP (isCriticalClose).
   - IClock, SystemMonotonicClock: Deterministic clock interface allowing fake clock injection for replay tests (AC-10).
   - IRiskEngine: Pure risk evaluation contract accepting sensor events, camera perception events, and watchdog ticks (>= 20 Hz / 50 ms).
   - WalkingCorridor: Geometric obstacle intersection calculator ([0.30, 0.70] x [0.30, 1.00], >= 20% area overlap).
   - ISpeechArbiter, SpeechRequest: Prioritized speech arbiter interface with 4 strict priority tiers (Level 1 EMERGENCY_STOP, Level 2 NAVIGATION_CRITICAL, Level 3 SEARCH_GUIDANCE, Level 4 STATUS_INFO), preemption, and <= 250 ms audio cancellation.
5. Verification Evidence:
   - 25/25 automated unit tests PASSED via ./gradlew.bat testDebugUnitTest (0 failures, 0 errors):
     - dev.navisense.EventContractsTest: 4/4 passed
     - dev.navisense.IClockTest: 1/1 passed
     - dev.navisense.PerceptionUnitTests: 13/13 passed
     - dev.navisense.SessionGenerationTest: 3/3 passed
     - dev.navisense.WalkingCorridorTest: 4/4 passed
   - Debug APK successfully built via ./gradlew.bat :app:assembleDebug:
     - File: android/app/build/outputs/apk/debug/app-debug.apk (7,286,242 bytes)
     - SHA-256 Hash: C7899DCD66EA8F7D1EE090D6ED75A9F23E5E92FA0096D957FE2CFB743CAFE3F8
   - Frozen File SHA-256 Hashes Re-verified (AGENTS.md §2):
     - docs/README.md: 54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA (MATCH)
     - docs/guidance.md: A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E (MATCH)
Requested action: All teammates inspect shared contracts, package boundaries, and build files. Spandan, Rohan, Subham, Samik ACK receipt.
Source revision and evidence reference: commit 91ecc1f on work/rishav-phase0; android/app/build/outputs/apk/debug/app-debug.apk
Recipient(s): Spandan, Subham, Rohan, Samik
For response: referenced entry ID RISHAV-2026-09-15-011 and ACK
```

```text
Entry ID: RISHAV-2026-09-15-012 / 2026-09-15T11:20:00+05:30 / T+03:30
Author and type: Rishav | CHAT
Phase / step / S-instance / H-contract: Phase 0 / Inter-subsystem Handoffs & Prerequisite Requests (Team Dispatch)
Message and requested action:
Detailed and organized breakdown of handoffs, deliverables, and technical information needed from each teammate for Phase 0 completion and Phase 1 transition:

1. To Spandan (Models & Datasets Lead):
   - [Deliverable Needed - S02 / H1 (Scheduled T+01:15–01:45)]:
     * Smoke Locate model artifact (TFLite for mobile, ONNX/PyTorch for laptop).
     * Smoke Mobility YOLO model artifact (TFLite for mobile).
     * Provide exact SHA-256 hashes and place Android exports in android/app/src/main/assets/models/.
   - [Tensor Signatures & Normalization]:
     * Confirm exact input tensor shape: e.g. [1, 384, 384, 3] or [1, 640, 640, 3].
     * Confirm data type: Float32 (normalized [0.0, 1.0] or [-1.0, 1.0]) vs Uint8 quantized.
     * Confirm channel ordering: RGB vs BGR.
     * Confirm output tensor format: [ymin, xmin, ymax, xmax] vs [xmin, ymin, xmax, ymax], confidence score range, class index layout.
   - [Demo Classes & Aliases]:
     * Formally sign off on initial two classes ("keys" and "wallet") and dictionary aliases per models/locate/metadata.json.
   - [Latency Budget]:
     * Confirm model target on-phone inference latency <= 150 ms to guarantee 5-10 Hz perception loop without thermal throttling.

2. To Rohan (Hardware, Firmware & Sensor Lead):
   - [Deliverable Needed - S03 / H3 (Scheduled T+01:45–02:00)]:
     * Hardware assembly and pinout record: ESP32-S3 board pin mapping for HC-SR04/RCWL-1601 (TRIG/ECHO pins), 5V power supply stability, and 5V->3.3V ECHO resistor voltage divider (e.g. 1k/2k ohm) to protect ESP32 GPIO.
     * Physical mounting specification: Chest/belt height (~1.0m to 1.2m), straight-ahead perpendicular orientation.
   - [USB Framing & Protocol Specification]:
     * Serial configuration: 115200 baud, 8 data bits, no parity, 1 stop bit (8N1).
     * Packet framing: Newline-delimited ASCII or framed JSON emitted at 20 Hz (50 ms interval): e.g. {"seq": N, "dist_cm": X.X, "status": "OK", "uptime_ms": M}\n or CSV format SEQ,UPTIME_MS,DIST_CM,STATUS\n.
     * Status flag taxonomy: OK, NO_ECHO (distance > 400 cm), BLIND_ZONE (distance < 2 cm), SENSOR_FAULT.
     * USB IDs: Provide Vendor ID (VID) and Product ID (PID) or CDC ACM driver profile so Android device_filter.xml can auto-attach.
   - [Phone Hardware & Offline TTS Joint Verification]:
     * Physical Android qualification device is connected via USB: Device ID 6545Q8A6X89TW8ZX.
     * Current status: unauthorized. Action needed: Please accept "Always allow from this computer" USB debugging prompt on the phone screen.
     * Once authorized, we will run joint verification of Android TextToSpeech offline English voice engine and measure silence latency upon Stop (<= 250 ms target per AC-11).

3. To Samik (Android Perception & Search Lead):
   - [Handoff S06 / H2 Alignment & S10 Preparation]:
     * Camera resolution & frame rate: Confirm CameraX ImageAnalysis resolution (640x480 or 640x640) targeting 15-30 FPS.
     * Coordinate transformation: Confirm CoordinateTransformer maps sensor coordinates to upright portrait orientation (compensating for 90° clockwise rear camera sensor) with normalized [0.0, 1.0] bounds.
     * Walking Corridor bounds: Confirm camera corridor alignment with Rishav's WalkingCorridor ([0.30, 0.70] horizontal width, [0.30, 1.00] ground depth).
     * Analysis Pause Hook: Confirm CameraX analyzer immediately pauses or drops frames when SessionCoordinator.userStop() is called or mode returns to AppMode.IDLE.

4. To Subham (Laptop Memory & API Lead):
   - [Handoff S07 / H4 Preparation]:
     * Mock server script: Provide a standalone lightweight script (e.g. python -m laptop.api.mock_server) or command to spin up FastAPI with PRD v1 fixtures so Rishav can test dev.navisense.networking.MemoryClientContract without requiring laptop webcam capture.
     * Response latency SLA: Confirm laptop locate query response latency <= 1000 ms to stay comfortably below the 2000 ms client timeout limit.

Source revision and evidence reference: android/app/src/main/java/dev/navisense/, android/app/build/outputs/apk/debug/app-debug.apk, docs/AGENTS.md
Recipient(s): Spandan, Rohan, Samik, Subham
For response: reply with referenced entry ID and required specifications / artifacts.
```

```text
Entry ID: SAMIK-2026-09-15-009 / 2026-09-15T11:25:00+05:30 / T+03:35
Author and type: Samik | RULE & HANDOFF
Phase / step / S-instance / H-contract: Governance / Mandatory Collaborator Git Workflow & Phase 0 Alignment
Message and requested action:
1. MANDATORY COLLABORATOR GIT & RELAY WORKFLOW (HARD RULE):
   - Added Section 1.1 to docs/AGENTS.md and root AGENTS.md (commit e7e8129 on main).
   - This rule is strictly binding for all project collaborators (Samik, Rishav, Subham, Spandan, Rohan):
     a. BEFORE EXECUTING ANY WORK:
        - Pull latest changes from BOTH remote branches:
          git pull origin main
          git pull origin communication
     b. AFTER EXECUTING ANY WORK:
        - Push work commits directly to main:
          git push origin main
        - Immediately relay detailed record of work in team-chat.md on the communication branch and push:
          git checkout communication
          git pull origin communication
          (append entry with timestamp, author, type, deliverables, SHA hashes)
          git add team-chat.md
          git commit -m "Relay ..."
          git push origin communication
          git checkout main
     c. NEVER force-push (git push --force) to any branch.
     d. Maintain frozen file SHA-256 integrity (docs/README.md and docs/guidance.md) at all times.

2. RESPONSE & ALIGNMENT TO RISHAV (RISHAV-2026-09-15-012, Item 3):
   - [Camera Resolution & FPS]: Confirming CameraX ImageAnalysis target resolution 640x480 (or 640x640 depending on pipeline), targeting 15-30 FPS.
   - [Coordinate Transformation]: Confirmed. dev.navisense.perception.CoordinateTransformer correctly maps raw 90° clockwise sensor coordinates to upright portrait orientation with normalized [0.0, 1.0] bounds.
   - [Walking Corridor Bounds]: Confirmed alignment with Rishav's WalkingCorridor ([0.30, 0.70] horizontal width, [0.30, 1.00] ground depth, >= 20% area overlap).
   - [Analysis Pause Hook]: Confirmed. Camera analyzer checks SessionCoordinator.sessionAuthority.isValid(sessionGeneration) and current AppMode before evaluation; immediately pauses or drops frame processing on mode reset or User Stop.

Source revision and evidence reference: docs/AGENTS.md (commit e7e8129), AGENTS.md, android/app/src/main/java/dev/navisense/perception/
Recipient(s): Team (Rishav, Subham, Spandan, Rohan)
For response: All collaborators ACK receipt and adherence to the mandatory workflow rule.
```

```text
<<<<<<< HEAD
Entry ID: ROHAN-2026-09-15-001 / 2026-09-15T11:30:00+05:30 / T+03:40
Author and type: Rohan | PROGRESS & SETUP ACK
Phase / step / S-instance / H-contract: Phase 0 / Collaborator Setup & Workflow ACK
Message and requested action:
1. Collaborator Access Verified:
   - Accepted GitHub repository invitation for JeansGit77 / jeans0177 to https://github.com/SamikSwarupBiswal/NaviSense-AI (API status 204 verified).
   - Write permissions established on main and communication branches.
2. Frozen File Hash Verification (AGENTS.md §2):
   - docs/README.md: 54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA (MATCH)
   - docs/guidance.md: A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E (MATCH)
3. Workflow & Guidance Acknowledgements:
   - ACK SAMIK-2026-09-15-009: Formally adhering to Section 1.1 Mandatory Collaborator Git & Relay Workflow (pull from both branches before work, push code to main, relay entries to communication, zero force-pushing).
   - ACK KICKOFF-2026-09-14-001 (Rishav) & RISHAV-2026-09-14-002 (S01 / H6): Subsystem ownership verified: HC-SR04 ultrasonic sensor + ESP32-S3 assembly, phone mount alignment, electrical/power verification, 10 Hz acquisition loop firmware, Android USB serial adapter (dev.navisense.usb), and leading acceptance gates AC-08, AC-06, AC-09.
Source revision and evidence reference: commit 8e91a84 on main
Recipient(s): Team (Rishav, Samik, Subham, Spandan)
For response: referenced entry ID and ACK
```

```text
Entry ID: ROHAN-2026-09-15-002 / 2026-09-15T11:35:00+05:30 / T+03:45
Author and type: Rohan | HANDOFF
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / S03 / H3
Message and requested action:
Delivering complete Phase 0 S03 / H3 package (Hardware Spec, 10 Hz ESP32 Firmware, Serial Test Harness, Transport Replay Suite, and Android USB Module) committed to main (commit 8e91a84):
1. Hardware & Electrical Specification (docs/rohan/hardware-spec.md):
   - Microcontroller: ESP32-S3 DevKit (WROOM-1 / DevKitC-1).
   - Sensor: HC-SR04 ultrasonic transducer (4-pin: VCC, GND, TRIG, ECHO).
   - Voltage Protection: Passive 1.0 kΩ (R1) / 2.0 kΩ (R2) ±1% resistor divider on ECHO line stepping down 5.0 V pulse to 3.33 V input for ESP32-S3 GPIO 5 (within 3.3 V CMOS bounds; 1.67 mA draw). Direct 3.3 V trigger pulses emitted from GPIO 4 (10 µs pulse).
   - Power Budget & USB OTG: Android phone USB-C OTG supplies standard 5.0 V @ 500 mA (up to 1.5 A). ESP32-S3 with Wi-Fi/BT disabled draws ~60 mA; HC-SR04 draws ~15 mA active. Total assembly current < 80 mA (well below 500 mA OTG limit, zero brownout).
   - Mechanical Alignment: Chest / lanyard rig, 100–120 cm height from ground, transducer plane facing directly forward perpendicular to walking vector, sharing aligned forward axis with phone camera (±5° pitch/yaw deviation).
2. ESP32-S3 10 Hz Acquisition Firmware (esp32/navisense_sensor/navisense_sensor.ino):
   - Strictly implements PRD Section 14.1 wire format: V=1,SEQ=<seq>,UP_MS=<uptime_ms>,DIST_CM=<dist>,VALID=<valid>\n.
   - Non-blocking 10 Hz loop enforcing 100 ms spacing without accumulating drift; bounded 25 ms echo timeout (~430 cm maximum travel).
   - Zero backlog policy: measurements are never buffered; only fresh unbuffered records emitted.
   - Integer rounding formula: (duration_us * 343 + 10000) / 20000. Valid range 2..400 cm sets VALID=1; timeouts or out-of-range emit DIST_CM=-1, VALID=0.
   - Serial: Native USB CDC at 115200 baud, 8N1.
3. Host Serial Verification Tool (scripts/test_sensor_serial.py):
   - 20/20 test cases passing: validates 128-byte line bound, CRLF/LF trimming, oversize discard through next \n, schema compliance, invalid range rejection (e.g. 401 cm, 1 cm), duplicate SEQ rejection, 32-bit unsigned wrap, backwards jump session resets.
   - 5.0s mock 10 Hz stream (50 records) verified: zero packet loss, critical close obstacle flags (<= 50 cm) correctly tagged.
4. Transport & Freshness Replay Suite (scripts/verify_sensor_fusion_replay.py):
   - 10/10 test cases passing: verifies 3-valid-record recovery (span >= 150 ms, gap <= 300 ms), 300 ms staleness watchdog expiry, immediate STOP candidate detection (<= 50 cm) during recovery, newer invalid record clearing distance, delay drift detection (> 200 ms), and backwards sequence reset.
5. Android USB Serial Package (dev.navisense.usb):
   - SensorRecord.kt: Immutable data class with monotonic receipt timestamp (receiptMonotonicMs) and isImmediateStopCandidate (<= 50 cm).
   - SensorHealth.kt: States DISCONNECTED, CONNECTING, RECOVERING, HEALTHY, STALE, DEGRADED_INVALID and SensorState snapshot.
   - SensorParser.kt: Bounded parser (128-byte limit, oversize discard, US-ASCII decoding, regex schema verification).
   - SensorStateManager.kt: Manages connection lifecycle, 3-record recovery, delay drift tracking, and evaluateHealth() periodic watchdog API for Rishav's Risk Engine.
   - Unit Tests: SensorParserTest.kt (4 unit tests) & SensorStateManagerTest.kt (4 unit tests).
Requested action: Rishav review H3 contracts and Android USB package; confirm integration into Android service lifecycle.
Source revision and evidence reference: commit 8e91a84 on main; docs/rohan/hardware-spec.md, esp32/navisense_sensor/, scripts/, android/app/src/main/java/dev/navisense/usb/
Recipient(s): Rishav, Samik, Subham, Spandan
For response: referenced entry ID ROHAN-2026-09-15-002 and VERIFIED / RETURNED
```

```text
Entry ID: ROHAN-2026-09-15-003 / 2026-09-15T11:40:00+05:30 / T+03:50
Author and type: Rohan | PROGRESS & REVIEW
Phase / step / S-instance / H-contract: Phase 0 / Inter-subsystem Alignment & Inquiries
Message and requested action:
Inter-subsystem technical alignment and responses to teammate inquiries:
1. Response to Rishav (RISHAV-2026-09-15-012, Item 2):
   - [Hardware Pinout & Divider]: Documented in full in docs/rohan/hardware-spec.md (ESP32-S3 GPIO 4 TRIG, GPIO 5 ECHO with 1k/2k divider to 3.33 V, 5V OTG supply).
   - [USB Framing & Protocol]: Confirming wire format is PRD §14.1 standard: V=1,SEQ=%lu,UP_MS=%lu,DIST_CM=%ld,VALID=%d\n at 10 Hz (100 ms interval) over CDC ACM 115200 8N1. (Note: PRD §14.1 specifies 10 Hz acquisition loop to balance ultrasonic travel/ring-down and zero backlog; parser handles up to 50 Hz if needed). Status flag mapped directly to PRD: VALID=1 (2..400 cm) and VALID=0 with DIST_CM=-1 for timeouts (> 400 cm) or out-of-range (< 2 cm).
   - [USB CDC Identification]: ESP32-S3 USB CDC uses Espressif Vendor ID 0x303A, Product ID 0x1001 (ESP32-S3 USB CDC ACM). Android device_filter.xml should specify <usb-device vendor-id="12346" product-id="4097" /> (decimal for 0x303A / 0x1001) or match USB class 0x02 (CDC) / subclass 0x02 (ACM).
   - [USB Debugging & Phone Screen Prompt]: Phone screen prompt acknowledged. Please authorize the USB debugging prompt on the qualification device (ID: 6545Q8A6X89TW8ZX) so ADB and offline TTS silence verification can proceed.
2. Response to Subham (SUBHAM-2026-09-15-009, Item 3):
   - [Network Isolation Confirmed]: Confirmed 100% transport independence and network isolation. The sensor node is hardwired purely over USB CDC / OTG to the Android phone. Wi-Fi and Bluetooth stacks are completely disabled in ESP32-S3 firmware; zero packets touch the Wi-Fi hotspot / HTTP subnet (192.168.43.0/24).
   - [Error Taxonomy Alignment]: Aligned sensor health states with Rishav's voice arbiter:
     * DISCONNECTED / CONNECTING: Physical USB cable detached or permission pending. Risk engine flags "sensor unavailable" — walking proceeds under cautious vision-only or issues sensor prompt.
     * HEALTHY: Fresh valid distance (<= 300 ms). Proximity obstacles <= 50 cm trigger immediate emergency STOP bypassing vision.
     * STALE: Watchdog age > 300 ms (cable stall or packet dropout). Distance cleared immediately.
     * DEGRADED_INVALID: Sensor emitted VALID=0 (timeout/no-echo) or delay drift > 200 ms. Distance cleared immediately.
     * These errors are strictly decoupled from laptop memory errors (HTTP timeout, connection refused, query stale > 60s), ensuring Rishav's Voice UX produces distinct, unambiguous prompts.
3. Response to Samik (SAMIK-2026-09-15-009):
   - Workflow rule acknowledged and verified.
   - Co-axial mount alignment confirmed (±5° pitch/yaw forward axis).
Source revision and evidence reference: docs/rohan/hardware-spec.md, esp32/navisense_sensor/navisense_sensor.ino, android/app/src/main/java/dev/navisense/usb/
Recipient(s): Rishav, Subham, Samik
For response: referenced entry ID and ACK
```

```text
Entry ID: ROHAN-2026-09-15-004 / 2026-09-15T11:45:00+05:30 / T+03:55
Author and type: Rohan | CHAT
Phase / step / S-instance / H-contract: Phase 0 / Prerequisite & Information Requests (Rohan -> Teammates)
Message and requested action:
Detailed and organized breakdown of handoffs and technical prerequisites needed from each teammate for Phase 0 completion and Phase 1 transitions:
1. To Rishav (Integration, Voice UX & Shell Lead):
   - [Android Lifecycle Hookup]: Requesting integration of dev.navisense.usb.SensorStateManager into the main Android service lifecycle:
     * Register USB BroadcastReceiver for ACTION_USB_DEVICE_ATTACHED and ACTION_USB_DEVICE_DETACHED invoking stateManager.onConnected() and stateManager.onDisconnected().
     * Include ESP32-S3 VID 0x303A / PID 0x1001 in res/xml/device_filter.xml.
   - [Watchdog Loop Hookup]: Confirm Rishav's 50 ms loop will call stateManager.evaluateHealth() to update SensorHealth and stale state even when no incoming bytes arrive over USB.
   - [Immediate Proximity STOP Priority]: Confirm Risk Engine prioritizes state.immediateStopCandidate (valid <= 50 cm) to issue STOP within <= 100 ms and audible audio onset <= 500 ms (AC-06), bypassing vision inference and 3-packet recovery.
2. To Samik (Android Perception & Camera Lead):
   - [Rig Mechanical Dimensions & Clearance]: Please share physical CAD / 3D-print or rig dimensions for the chest phone clamp. Ensure the phone clamp does not block the phone's USB-C port or put mechanical strain on the right-angle USB-C OTG cable connected to the ESP32-S3.
   - [AC-06 Co-Benchmarking]: Confirm coordination for AC-06 Physical Obstacle to Audible STOP benchmark (T+19:00–19:30): Samik will ensure full on-device vision inference (Locate + Mobility YOLO) is running concurrently to validate zero missed STOPs under live inference contention.
3. To Spandan (Models & Datasets Lead):
   - [Ultrasonic vs Camera FOV Coverage Limits]:
     * HC-SR04 has a narrow ~15° conical beam facing straight forward, effective from 2 cm to 400 cm.
     * Mobile camera FOV is ~68°–75° diagonal.
     * Test scene design must recognize this envelope: obstacles in the direct walking corridor (<= 200 cm ahead) are caught immediately by ultrasonic, but peripheral obstacles (> 10° off-axis) rely entirely on Spandan's Mobility YOLO model.
   - [Acoustic Material Disclosures]: Note physical ultrasonic limitations for test datasets and AC-12/final demo: angled surfaces (> 45°), sound-absorbing soft fabrics, and drop-offs/downward stairs are acoustically transparent or non-reflective to HC-SR04.
Source revision and evidence reference: docs/rohan/guidance.md, docs/rohan/implementation-plan.md
Recipient(s): Rishav, Samik, Spandan
For response: reply with relevant entry reference and confirmations / specifications.
```

```text
Entry ID: ROHAN-2026-09-15-005 / 2026-09-15T11:50:00+05:30 / T+04:00
Author and type: Rohan | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Subsystem Commitments & Acceptance Gate Roadmap
Message and requested action:
Formally logging downstream milestone commitments, freezes, and acceptance gate ownership for Rohan (Hardware, Firmware, Mount, USB):
1. Milestone Deliverables:
   - Handoff S13 (T+11:30): Deliver stable Android USB adapter handling bounded line parsing (128-byte limit, CRLF/LF), monotonic receipt timestamps (<= 300 ms validity), sequence loss diagnostics, and 3-valid-packet recovery logic.
   - S17 Freeze (T+15:30): Physical sensor mount, wiring, and firmware configuration frozen.
   - Bench Pre-Marking: Pre-mark 6 bench distances (30, 50, 75, 100, 150, 200 cm) and set up dual timestamp recording (physical optical entry beam + external high-speed audio mic).
2. Acceptance Gates Led:
   - AC-08 (T+17:00–17:20) — Sensor Bench Calibration: 120 physical readings across 6 pre-marked distances (requires >= 90% validity per distance, median absolute error <= 5 cm).
   - AC-06 (T+19:00–19:30) — Physical Obstacle to Audible STOP Latency: 20 bench presentations (10 at 30 cm, 10 at 40 cm) under live inference load. Proves risk decision <= 100 ms, audible onset <= 500 ms, and physical entry-to-audio <= 750 ms with zero missed STOPs.
   - AC-09 (T+19:30–20:00) — USB Transport Stability & Recovery: 10-minute continuous stream without crash or drift, followed by 5 physical cable disconnect/reconnect cycles (recovery <= 2.0 s).
3. Peer Reviews & Collaboration:
   - Reviews: AC-05 (Samik mobile inference latency) and AC-14 (Subham full flow integration).
   - Contributes to: AC-01 (Mobility run), AC-07 (Supervised walking trials), AC-10 (Replay cases), and AC-12 (Sensor disconnect/degradation).
4. Final Demo & S18 Role (T+23:30–24:00):
   - Manage physical hardware and mount during live demo.
   - Demonstrate immediate proximity STOP bypassing vision and sensor disconnect resilience without app crash.
   - Provide comprehensive documentation of acoustic limitations (angled walls, soft fabrics, drop-offs/steps) for final disclosure.
Source revision and evidence reference: docs/rohan/guidance.md, docs/rohan/implementation-plan.md, docs/AGENTS.md
Recipient(s): Team (Rishav, Samik, Subham, Spandan)
For response: Team review and alignment.
```

```text
Entry ID: SUBHAM-2026-09-15-010 / 2026-09-15T11:05:00+05:30 / T+03:00
Author and type: Subham | HANDOFF
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / S04 / H4 Handoff Package & Phase 0 Exit Review
Message and requested action:
Subham Phase 0 Comprehensive Handoff Package & Inter-Subsystem Prerequisite Summary:

1. Delivered Phase 0 Handoff S04 / H4 Artifacts & Source Code:
   - FastAPI REST API Schemas (laptop/api/schemas.py): Strictly typed LocateStatus (FOUND, AMBIGUOUS, STALE, HISTORICAL_ONLY, NOT_FOUND, UNSUPPORTED), HealthResponse, LocateResponse, and Candidate with normalized bounding box validation [x1, y1, x2, y2] in [0.0, 1.0] and confidence [0.0, 1.0].
   - Response Fixture Suite (laptop/tests/fixtures/responses.json): Complete response fixtures covering all 6 query status outcomes and health statuses per PRD §23.
   - Schemas & Boundary Test Suite (laptop/tests/test_api_schemas.py): 8/8 automated unit tests passed verifying box coordinate bounds, invalid coordinate rejection, age calculation, stale threshold (>60.0s), and ambiguous candidate isolation.
   - Android Memory Client Contract (android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt): Sealed class LocateResult (Found, Ambiguous, Stale, HistoricalOnly, NotFound, Unsupported, NetworkError), MemoryCandidate, and MemoryClientContract interface specifying MAX_TIMEOUT_MS = 2000L, MAX_RESPONSE_BYTES = 65536L (64 KiB cap), STALE_THRESHOLD_SECONDS = 60.0s, bearer token auth, and sessionGeneration cancellation support.
   - Authoritative SQLite Schema (laptop/config/settings.py / PRD §11-12): SQLite persistence model for camera_profiles, scan_snapshots, and observed_objects supporting single-transaction atomic commits per 2.0s Hard Scan (10 frames, >=0.60 confidence in >=6 frames), transaction rollback on cancellation/Clear history, and camera profile invalidation.
   - Comprehensive Phase 0 Handoffs & Requests Document: docs/subham/phase0-handoffs-and-requests.md.

2. Phase 0 Information & Prerequisite Requests Matrix:
   - To Spandan (Models Lead):
     * [INFO NEEDED]: Sign-off on baseline classes (keys, wallet) and canonical alias mapping (key, keys, house keys, car keys, wallet, billfold, purse) in models/locate/metadata.json.
     * [ARTIFACT NEEDED]: Smoke Locate model export (TFLite/PyTorch/ONNX) for Laptop (S02 / H1) with RGB 640x640 input specification, >=0.60 confidence threshold, and normalized xyxy coordinates.
   - To Rishav (System Integration & Voice UX Lead):
     * [CONTRACT CONFIRMATION]: Confirm MemoryClientContract interface matches Voice UX coordinator requirements for triggering locateObject(queryName, sessionGeneration).
     * [AUTH & NETWORK]: Confirm bearer token storage/injection mechanism on Android and hotspot IP configuration (laptop endpoint e.g., http://192.168.43.100:8000).
     * [STALE CALLBACK HANDLING]: Confirm SessionAuthority.isValid(sessionGeneration) check on LocateResult return to discard late network callbacks on mode switch or User STOP.
   - To Rohan (Hardware Lead):
     * [NETWORK ISOLATION]: Confirm sensor node operates exclusively over USB CDC/OTG with the phone and does not interfere with the laptop local HTTP subnet.
     * [ERROR TAXONOMY]: Confirm distinction between sensor transport disconnects and laptop memory HTTP timeout/unreachable errors.
   - To Samik (Android Perception & Search Lead):
     * [TARGET SEARCH ENGINE ALIGNMENT]: ACK received (SAMIK-2026-09-15-008). Confirmed TargetSearchEngine.startSearch(targetClass, sessionGeneration, startMonotonicMs) consumes target_class from LocateResult.Found upon arrival.

3. Phase 0 Exit Review & Phase 1 Entry Status:
   - Phase 0 Exit Review: VERIFIED-FIXTURES (Handoff S04/H4 contracts delivered and tested).
   - Phase 1 Entry Status: READY-FIXTURES (Standing by for Spandan S02 smoke model export to integrate laptop webcam detector adapter).

Source revision and evidence reference: laptop/api/schemas.py, laptop/tests/fixtures/responses.json, laptop/tests/test_api_schemas.py, android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt, docs/subham/phase0-handoffs-and-requests.md
Recipient(s): Spandan, Rishav, Rohan, Samik
For response: referenced entry IDs SUBHAM-2026-09-15-008, SUBHAM-2026-09-15-009, SAMIK-2026-09-15-008 and ACK / VERIFIED
```

```text
Entry ID: SUBHAM-2026-09-15-011 / 2026-09-15T11:30:00+05:30 / T+03:40
Author and type: Subham | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Review ACK & Subsystem Alignment
Message and requested action:
1. ACK to RISHAV-2026-09-15-010: Received VERIFIED FOR HANDOFF for S04 / H4 (Laptop Locate REST API Schemas, SQLite Schema, PRD Fixtures, MemoryClientContract). Confirmed alignment on SessionCoordinator integration, bearer token security via BuildConfig, and stale callback invalidation via SessionAuthority.
2. ACK to RISHAV-2026-09-15-011: Inspected master Android application shell v0, build configuration, permissions, and accessibility baseline. Confirmed OkHttp 4.12.0 dependency is available for Android MemoryClient implementation.
3. ACK to RISHAV-2026-09-15-012 (Item 4 for Subham):
   - [Mock Server Script]: Will provide python -m laptop.api.mock_server to launch local FastAPI service returning PRD v1 fixtures for offline/fixture testing without webcam.
   - [Response Latency SLA]: Confirmed laptop locate query response latency <= 1000 ms to stay comfortably within the 2000 ms client timeout limit.
4. ACK to SAMIK-2026-09-15-009: Confirmed full adherence to the Mandatory Collaborator Git & Relay Workflow rule. Verified frozen file SHA-256 hashes (docs/README.md: 54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA, docs/guidance.md: A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E).

Source revision and evidence reference: laptop/api/schemas.py, docs/subham/phase0-handoffs-and-requests.md
Recipient(s): Rishav, Samik, Spandan, Rohan
For response: referenced entry IDs RISHAV-2026-09-15-010, RISHAV-2026-09-15-011, RISHAV-2026-09-15-012, SAMIK-2026-09-15-009 and ACK
```

```text
Entry ID: SUBHAM-2026-09-15-012 / 2026-09-15T11:55:00+05:30 / T+04:05
Author and type: Subham | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Hardware Alignment ACK
Message and requested action:
1. ACK to ROHAN-2026-09-15-003 (Item 2):
   - Confirmed Rohan's verification of 100% transport independence and network isolation for the ESP32-S3 sensor node over USB CDC/OTG.
   - Verified that Wi-Fi/Bluetooth are disabled on ESP32-S3, keeping the local Wi-Fi hotspot HTTP subnet (192.168.43.0/24) 100% dedicated to laptop memory API queries.
   - Verified error taxonomy alignment: sensor connection states (DISCONNECTED, STALE, DEGRADED_INVALID) produce distinct prompts from laptop API errors (HTTP 504 timeout, connection refused, age > 60s stale).
2. ACK to ROHAN-2026-09-15-005: Reviewed Rohan's acceptance gate roadmap (AC-08, AC-06, AC-09) and peer review role for AC-14 (Full Memory-to-Search Integration). Standing by for joint S16 candidate integration at T+14:30.

Source revision and evidence reference: docs/subham/phase0-handoffs-and-requests.md, docs/rohan/hardware-spec.md
Recipient(s): Rohan, Rishav, Samik, Spandan
For response: referenced entry IDs ROHAN-2026-09-15-003, ROHAN-2026-09-15-005 and ACK
```

```text
Entry ID: ROHAN-2026-09-15-006 / 2026-09-15T12:00:00+05:30 / T+04:10
Author and type: Rohan | HANDOFF & PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / S03-S08-S13 Pre-Delivery & Teammate Handoff Package
Message and requested action:
1. ACK to Subham (SUBHAM-2026-09-15-010 & SUBHAM-2026-09-15-012):
   - Acknowledged Subham's verified Handoff S04 / H4 delivery (FastAPI schemas, PRD v1 fixtures, SQLite schema, MemoryClientContract).
   - Acknowledged confirmation of sensor network isolation (pure USB CDC/OTG, zero Wi-Fi traffic on 192.168.43.0/24) and error taxonomy separation.
   - Confirmed joint readiness for S16 candidate integration at T+14:30 and AC-14 full flow integration peer review.
2. Delivered Comprehensive Phase 0 Document (docs/rohan/phase0-handoffs-and-requests.md):
   - Published full hardware, firmware, electrical, USB adapter specification, and cross-subsystem prerequisite matrix on main and communication branches.
3. Delivered Android USB Serial Adapter Implementation (dev.navisense.usb.UsbSensorAdapter):
   - Background worker thread with bounded read buffer (128 bytes) and transport abstraction (UsbTransport, StreamUsbTransport).
   - Monotonic receipt timing via injectable clock and automatic sequence loss counting (totalLostPackets diagnostic).
   - 3-valid-packet recovery logic and immediate proximity STOP candidate callback (onImmediateStopCandidate for readings <= 50 cm).
   - Exposed evaluateHealth(): SensorState API for Rishav's 50 ms watchdog loop.
   - Added comprehensive unit test suite dev.navisense.usb.UsbSensorAdapterTest covering stream processing, recovery, sequence loss diagnostics, and immediate STOP callbacks.
4. Source revision and evidence reference: commit e7c1fe6 on main; docs/rohan/phase0-handoffs-and-requests.md, android/app/src/main/java/dev/navisense/usb/UsbSensorAdapter.kt, android/app/src/test/java/dev/navisense/usb/UsbSensorAdapterTest.kt
Recipient(s): Rishav, Samik, Subham, Spandan
For response: referenced entry ID ROHAN-2026-09-15-006 and ACK / VERIFIED
```

```text
Entry ID: SUBHAM-2026-09-15-013 / 2026-09-15T12:05:00+05:30 / T+04:15
Author and type: Subham | HANDOFF & PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / S04 / H4 Code Commit Relay & Rohan ACK
Message and requested action:
1. Main Branch Code Commit Relay (Mandatory Collaborator Git & Relay Workflow):
   - Committed and pushed all Subham Phase 0 source code, schemas, unit tests, and documentation to main branch (commit 2f8223c on main):
     * laptop/api/schemas.py (FastAPI Pydantic schemas for PRD §23)
     * laptop/config/settings.py (Authoritative SQLite schema spec for PRD §11-12)
     * laptop/tests/fixtures/responses.json (Complete v1 response fixtures)
     * laptop/tests/test_api_schemas.py (8/8 automated unit tests passed via pytest)
     * android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt (Android Memory Client contract)
     * models/locate/metadata.json (Locate YOLO model metadata & alias mapping)
     * docs/subham/phase0-handoffs-and-requests.md (Comprehensive Phase 0 handoffs and prerequisite matrix)
2. ACK to ROHAN-2026-09-15-006:
   - Reviewed Rohan's delivery of UsbSensorAdapter and docs/rohan/phase0-handoffs-and-requests.md.
   - Confirmed receipt of sensor transport specifications.
   - Re-verified frozen file SHA-256 hashes (docs/README.md: 54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA, docs/guidance.md: A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E).

Source revision and evidence reference: commit 2f8223c on main; laptop/api/schemas.py, android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt, docs/subham/phase0-handoffs-and-requests.md
Recipient(s): Rishav, Samik, Spandan, Rohan
For response: referenced entry IDs ROHAN-2026-09-15-006 and ACK / VERIFIED
```

```text
Entry ID: SAMIK-2026-09-15-010 / 2026-09-15T12:05:00+05:30 / T+04:15
Author and type: Samik | PROGRESS & REVIEW
Phase / step / S-instance / H-contract: Phase 0 / Physical Qualification Device Verification & Hardware Alignment
Message and requested action:
1. Physical Android Qualification Device Verified & App Live:
   - Device ID: 6545Q8A6X89TW8ZX
   - Hardware Model: OPPO CPH2753 (SoC: MediaTek MT6835, 64-bit arm64-v8a)
   - Operating System: Android 16 (API Level 36)
   - Hardware Capabilities Verified via adb:
     * android.hardware.usb.host: Supported & active (USB OTG ready for Rohan's ESP32-S3 sensor node).
     * android.hardware.camera.level.full: Camera2 Level FULL hardware support.
     * Rear Camera Orientation: Facing Back, 90° clockwise orientation. Matches CoordinateTransformer.kt unletterboxing and upright portrait mapping.
     * Speech Engine: com.google.android.tts verified installed on device for offline English speech.
   - Master App Execution: android/app/build/outputs/apk/debug/app-debug.apk built and installed; dev.navisense.app.MainActivity running live with zero crashes, displaying accessible UI and prominent User STOP control.

2. Response to Rohan (ROHAN-2026-09-15-004 & ROHAN-2026-09-15-006):
   - [Rig Mechanical Dimensions & USB-C Clearance]: Confirmed chest mount phone clamp leaves bottom USB-C port unobstructed with ample physical clearance for right-angle OTG cable without mechanical strain.
   - [Coaxial Optical Alignment]: Confirmed rigid coaxial forward alignment between phone rear camera lens and HC-SR04 transducers (+/- 5° pitch/yaw deviation) at chest height (~100–120 cm).
   - [AC-06 Co-Benchmarking Commitment]: Confirmed Samik will run concurrent on-device vision inference (Locate + Mobility YOLO) during Rohan's AC-06 bench obstacle tests (T+19:00–19:30) to validate <= 100 ms STOP decision and <= 500 ms audio onset under live CPU/NPU contention.
   - [S03-S08-S13 Review]: ACK to ROHAN-2026-09-15-006; verified UsbSensorAdapter implementation and unit tests.

3. Status to Spandan:
   - Physical qualification phone is connected, authorized, and standing by for S02 / H1 TFLite model exports to benchmark mobile inference latency, frame rates, and memory footprint.

Source revision and evidence reference: commit 0bfbd3a on main; docs/implementation-state.md, android/app/build/outputs/apk/debug/app-debug.apk
Recipient(s): Rohan, Rishav, Spandan, Subham
For response: referenced entry IDs ROHAN-2026-09-15-004, ROHAN-2026-09-15-006 and ACK
```

```text
Entry ID: SAMIK-2026-09-15-011 / 2026-09-15T12:15:00+05:30 / T+04:25
Author and type: Samik | PROGRESS & INTEGRATION
Phase / step / S-instance / H-contract: Phase 0 / S01 / H6 Master Application Shell & Contracts Integration
Message and requested action:
1. Integration of Rishav's S01 / H6 Master Application Shell on main (Commit 78f3187):
   - Merged and verified all components from work/rishav-phase0 onto main:
     * dev.navisense.app.MainActivity, SessionCoordinator, NaviSenseApp.
     * Core contracts: AppMode, SessionGeneration, SessionToken, IClock, PathStatus, RiskLevel, WalkingCorridor, IRiskEngine, ISpeechArbiter, SpeechRequest.
     * Accessible layouts & styles: activity_main.xml, colors.xml, strings.xml, styles.xml.
     * Unified type bridges: NormalizedBoundingBox <-> NormalizedRect, BoundingBox <-> NormalizedRect, PerceptionFrameEvent <-> MobilePerceptionEvent, SearchConfirmationEvent <-> SearchEvent.
2. Verification Results:
   - 25/25 automated unit tests PASSED (0 failures, 0 errors).
   - android/app/build/outputs/apk/debug/app-debug.apk assembled successfully with CameraX 1.3.1, ViewBinding, and OkHttp 4.12.0.
   - Freshly installed and verified on physical qualification device (OPPO CPH2753, Android 16).
3. Frozen File Hashes:
   - docs/README.md: 54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA (MATCH)
   - docs/guidance.md: A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E (MATCH)

Source revision and evidence reference: commit 78f3187 on main; android/app/build/outputs/apk/debug/app-debug.apk
Recipient(s): Rishav, Subham, Rohan, Spandan
For response: referenced entry ID RISHAV-2026-09-15-011 and ACK / VERIFIED
```





