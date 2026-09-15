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


