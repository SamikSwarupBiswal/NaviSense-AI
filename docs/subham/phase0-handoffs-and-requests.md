# Subham — Phase 0 Handoffs & Inter-Subsystem Requests

Owner: **Subham** (Laptop Locate, Memory, API & Android Memory Client)  
Subsystem Ownership: Laptop webcam Hard Scan pipeline, SQLite persistence, authenticated FastAPI local service, and Android memory networking client (`dev.navisense.networking`).  
Repository: [NaviSense-AI](https://github.com/SamikSwarupBiswal/NaviSense-AI)  
Branch: `communication`  

---

## 1. Executive Summary & Phase 0 Deliverables (Handoff S04 / H4)

Subham has completed all Phase 0 contracts, FastAPI REST API schemas, PRD response fixtures, automated pytest verification, SQLite schema specifications, and the Android `MemoryClientContract` interface.

### Key Deliverables Completed & Frozen-Verified:
1. **FastAPI REST Schemas** ([laptop/api/schemas.py](file:///c:/Users/LENOVO/Downloads/Navi%20Sense/laptop/api/schemas.py)):
   - `LocateStatus` Enum: `found`, `ambiguous`, `stale`, `historical_only`, `not_found`, `unsupported`.
   - `ServiceStatus` Enum: `ok`, `degraded`, `error`.
   - `Candidate`: Strictly validated bounding box `[x1, y1, x2, y2]` in `[0.0, 1.0]`, confidence in `[0.0, 1.0]`, zone ID, and zone name.
   - `LocateResponse` & `HealthResponse`: Standardized PRD §23 responses including canonical names, age tracking, scan ID, and camera profile ID.
2. **PRD v1 Response Fixtures** ([laptop/tests/fixtures/responses.json](file:///c:/Users/LENOVO/Downloads/Navi%20Sense/laptop/tests/fixtures/responses.json)):
   - Mock payloads covering all 6 location outcomes and service health check states.
3. **Automated Unit Tests** ([laptop/tests/test_api_schemas.py](file:///c:/Users/LENOVO/Downloads/Navi%20Sense/laptop/tests/test_api_schemas.py)):
   - 8/8 tests PASSED via `pytest laptop/tests/test_api_schemas.py`.
4. **Android Memory Client Contract** ([android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt](file:///c:/Users/LENOVO/Downloads/Navi%20Sense/android/app/src/main/java/dev/navisense/networking/MemoryClientContract.kt)):
   - Sealed class `LocateResult` (`Found`, `Ambiguous`, `Stale`, `HistoricalOnly`, `NotFound`, `Unsupported`, `NetworkError`).
   - Limits: `MAX_TIMEOUT_MS = 2000L`, `MAX_RESPONSE_BYTES = 65536L` (64 KiB), `STALE_THRESHOLD_SECONDS = 60.0s`.
   - `sessionGeneration` invalidation support for late callback rejection.
5. **SQLite Persistence Schema Spec** ([laptop/config/settings.py](file:///c:/Users/LENOVO/Downloads/Navi%20Sense/laptop/config/settings.py) / PRD §11-12):
   - Tables: `camera_profiles`, `scan_snapshots`, `observed_objects`.
   - Atomic scan snapshot transaction (10 frames in 2.0s, $\ge 0.60$ confidence in $\ge 6$ frames). Rollback on scan cancellation or Clear history.

---

## 2. Phase 0 Inter-Subsystem Information & Prerequisite Requests

To ensure seamless integration across Phase 1 to Phase 8, Subham requires the following acknowledgements, parameters, and contract alignments from teammates:

### A. To Spandan (Models & Datasets Lead)
- **Alias & Class Sign-Off:** Confirm baseline classes (`keys`, `wallet`) and canonical alias dictionary in `models/locate/metadata.json`.
- **Smoke Locate Model Artifact (S02 / H1):** Export laptop-compatible smoke Locate model (PyTorch / ONNX / TFLite) for RGB 640x640 input, normalized `[0, 1]`, outputting `[x1, y1, x2, y2, conf, cls]`.
- **Confidence Threshold:** Confirm detection confidence threshold ($\ge 0.60$ per PRD §10) and NMS IoU threshold ($0.45$).

### B. To Rishav (System Integration, Voice UX & Shell Lead)
- **Client Contract Verification:** Confirm `MemoryClientContract` interface matches coordinator expectations for calling `locateObject(queryName, sessionGeneration)`.
- **Authentication & Bearer Token:** Confirm pre-shared bearer token storage on Android (e.g., `BuildConfig.MEM_SERVICE_TOKEN`) without committing secrets to git.
- **Stale Callback Protection:** Confirm `SessionAuthority.isValid(sessionGeneration)` is checked on `LocateResult` return to prevent target mutation or speech after mode switches/STOP.
- **Hotspot Network Address:** Confirm static IP assignment for laptop endpoint (e.g., `http://192.168.43.100:8000`).

### C. To Rohan (Hardware, Firmware & Sensor Lead)
- **Network Isolation:** Confirm USB CDC/OTG sensor transport operates completely independent of the laptop HTTP Wi-Fi subnet.
- **Error Taxonomy:** Align error semantics so hardware failures (USB disconnect) and memory service failures (HTTP 504 / connection refused) are cleanly distinguished by Rishav's voice arbiter.

### D. To Samik (Android Perception & Search Lead)
- **Target Search Engine Alignment:** ACK received (SAMIK-2026-09-15-008). Confirmed `TargetSearchEngine.startSearch(targetClass, sessionGeneration, startMonotonicMs)` consumes `target_class` from `LocateResult.Found` upon arrival at the zone.

---

## 3. Subham Deliverables & Acceptance Gates Roadmap

| Timeline | Deliverable / Gate | Description & Role |
|---|---|---|
| **T+03:00** | **Handoff S04 (H4)** | Laptop REST API schemas, fixtures, SQLite schema spec, and Android `MemoryClientContract`. |
| **T+05:30** | **Handoff S07 (H4)** | Atomic 10-frame Hard Scan pipeline, non-overlapping zone mapping, and SQLite commit/rollback. |
| **T+10:00** | **Handoff S11 (H4)** | Android `MemoryClientContract` implementation with 2s timeout, 64 KiB cap, and token auth. |
| **T+14:30** | **Handoff S16** | Co-delivers Three-flow integrated candidate with Rishav and Samik. |
| **T+16:30–17:00** | **AC-03 (Lead)** | Executes 20 prepared Hard Scans on webcam proving class, zone, count, timing (< 2.0s), and rollback. |
| **T+16:30–17:00** | **AC-04 (Lead)** | Memory & API Fault Matrix: 60s vs 61s stale, clear during scan, malformed API, late network callbacks. |
| **T+18:30–21:00** | **Peer Reviews** | Reviews AC-01 (Rishav Mobility), AC-06 (Rohan STOP latency), AC-07 (Hallway), AC-10 (Risk replay), AC-13 (Android search). |
| **T+21:00–21:30** | **AC-14 (Lead)** | Leads 5 full end-to-end flows (Scan $\rightarrow$ Query $\rightarrow$ Walk $\rightarrow$ Arrival $\rightarrow$ Search $\rightarrow$ Found) & 5 stress cycles. |
| **T+23:30–24:00** | **Live Demo S18** | Operates laptop station: executes live Hard Scan, displays database log, proves truthful "last seen" reporting. |

---

## 4. Communication & Frozen File Hashes

- **docs/README.md SHA-256**: `54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA` (MATCH - Frozen)
- **docs/guidance.md SHA-256**: `A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E` (MATCH - Frozen)

---
*Communication Branch Rule: All work/progress/handoff/review/decision messages belong in communication's append-only team-chat.md.*
