# NaviSense AI — Five-Member Execution Guidance

Prepared: 2026-09-14. Authority: [PRD v3.2](README.md).

Central progress tracker: [implementation-state.md](implementation-state.md). Use it for current phase, member, handoff, blocker and acceptance results; the initial status tables below are a frozen planning baseline; record subsequent evidence only in the mutable tracker.

**Status: user-assigned ownership; implementation remains planned, not completed or accepted.** The workspace currently contains the PRD and these guides; source code, training results, hardware qualification, and acceptance evidence have not been established here. The user's current allocation is authoritative: Spandan owns models/datasets, Subham laptop memory/API, Rohan hardware/USB, Samik Android camera/inference, and Rishav risk/voice/integration. Superseded assignments must not be used. No completion dates or device availability are assumed.

**Frozen reference:** This file is frozen after the present documentation update. Verify its SHA-256 against [AGENTS](../AGENTS.md); routine progress, schedule and decision updates belong in mutable implementation files. The 24-hour schedule and detailed directions are in [implementation-plan.md](implementation-plan.md).

## 1. Team Ownership

| Member | Primary responsibility | Owns the resulting behavior | Individual guide |
|---|---|---|---|
| Spandan | Models, datasets, training, exports, model evaluation | Both Locate artifacts and the Mobility artifact have documented labels, preprocessing, provenance, and measured quality | [Spandan](spandan/guidance.md) |
| Rishav | Android application, risk, voice, lifecycle, integration | Standalone walking, path/risk decisions, accessible controls, prioritized speech, cancellation, and integrated user flows | [Rishav](rishav/guidance.md) |
| Subham | Laptop Locate service, Hard Scan, memory, REST, Android memory client | Reliable observations and honest object queries reach the phone through the agreed contract | [Subham](subham/guidance.md) |
| Samik | Android camera, model runtime, visual tracking, nearby/final search engine | Both models run locally with correct coordinates, fresh detections, and target confirmation | [Samik](samik/guidance.md) |
| Rohan | ESP32-S3, HC-SR04, mounting, USB transport and Android sensor adapter | Real distance reaches Android with correct validity, freshness, recovery, and diagnostics | [Rohan](rohan/guidance.md) |

Each member implements and tests their own subsystem. Rishav coordinates integration; he is not responsible for repairing everyone else's subsystem or running all tests alone. A handoff is complete only when its named receiver verifies it. The project owner records final acceptance explicitly; this plan does not assert who has already approved it.

## 2. Source Ownership and Shared Files

These are **planned logical ownership paths**, not existing files or a requirement to create multiple Gradle modules. Keep one Android application with small packages; Rishav establishes actual package paths in Phase 0.

| Owner | Planned owned paths/responsibility |
|---|---|
| Spandan | `models/locate/`, `models/mobility/`, dataset manifests/labels under `datasets/`, model export/training/evaluation scripts under `scripts/` |
| Rishav | Android application shell, `android/navigation/`, `android/voice/`, shared contracts/configuration, build files/manifest/dependency integration, app-wide lifecycle and integration tests |
| Subham | `laptop/` including its vision adapter, scan coordinator, configuration, SQLite, API, tests; `android/networking/` memory client and its tests |
| Samik | `android/camera/`, `android/inference/`, mobile tracking and target-search engine under `android/search/`, associated tests and instrumentation |
| Rohan | `esp32/navisense_sensor/`, `android/usb/`, transport/parser tests and hardware measurement scripts |

Spandan supplies detector artifacts and decoding metadata; Subham owns the laptop runtime wrapper and Samik owns the Android runtime wrapper. Rohan owns distance production/health, not risk or speech. Samik owns track evidence and search confirmation, not navigation decisions or app-wide mode changes. Subham validates API responses; Rishav owns selection/confirmation screens and when a target becomes active.

Only Rishav integrates shared Android build/manifest changes after receiving the exact requested edit from the affected owner. Shared interface changes require producer and receiver agreement and a compatibility check. Do not overwrite another member's work, silently change thresholds/protocols, or introduce a second application/framework to avoid coordination. Keep secrets, local observations, continuous recordings, and large weights out of ordinary source commits; hand off approved artifact locations and hashes.

## 3. Rules Everyone Must Preserve

1. Read the PRD sections relevant to your subsystem before coding. The PRD overrides these summaries; do not silently resolve a conflict by changing the product.
2. Standalone walking must start without laptop, internet, a memory query, or a destination. Phone-only vision is degraded; it never proves a clear path.
3. Android Locate is mandatory. Run one vision model at a time. FinalSearch/Found are stationary modes, with USB STOP monitoring retained as specified.
4. Unknown object classes can still trigger a distance warning. Invalid/no-echo/stale data never means clear. A forward beam does not establish full-width traversability.
5. No automatic route, arrival estimate, side-escape instruction, exact object depth, or ownership recognition is part of the baseline. Arrival is explicitly confirmed.
6. User Stop cancels the session; a hazard STOP warns while monitoring continues. Late work cannot restore a cancelled session.
7. All speech goes through Rishav's shared arbiter. No detector, USB module, API client, or search engine calls TTS directly.
8. Use the PRD's thresholds and contracts. Examples/mocks do not prove real hardware or model behavior. Record failures; do not lower gates to make a demo pass.
9. Defer OCR, scene extras, voice recognition, and routing until baseline work is accepted. Accessible controls are mandatory from the first app shell.
10. Execute critical-distance tests on the bench and walking tests only in the supervised PRD envelope. Wiring/pin selection must be verified on the actual assembly.

## 4. Phases and Dependencies

The numbering below preserves PRD Section 32. Phase 0 makes its pre-phase compatibility check explicit; it is not a new product feature. Phase numbers describe deliverables, not a rule that everyone must wait for the preceding number. Phases 1–5 can progress together after Phase 0. Phase 7's app shell/Stop foundations begin early; final voice integration follows risk integration.

| Phase | Work split | Exit/handoff |
|---|---|---|
| 0 — Compatibility and contracts | Rishav creates the Android skeleton/shared interfaces and records open decisions. Spandan proposes the small demo class set and smoke artifacts. Samik tests both exports/runtime on the actual phone. Rohan verifies board, phone USB host/data/power, mount, and offline sensor stream. Subham supplies sample REST responses and checks laptop prerequisites. All contribute representative camera data. | Actual device/configuration recorded; both models load for a smoke check; offline TTS and USB path demonstrated or specific blockers recorded. Producer/receiver contracts agreed. Smoke success is not final acceptance. |
| 1 — Stationary Vision | Spandan collects/labels/trains Locate and produces artifacts; Mobility starts from pretrained weights. Subham integrates webcam and laptop detector. Samik validates phone viewpoints/export decoding. | Versioned class map, split manifest, candidate hashes; actual webcam detections reach Subham's adapter; Spandan hands models to Subham and Samik. |
| 2 — Hard Scan + Memory | Subham builds configured zones, matching/persistence, transactional scans, SQLite, queries, clear/profile invalidation, and REST fixtures. Spandan diagnoses detector errors. Rishav agrees phone presentation of ambiguity/staleness. | Scan/storage/query fixtures pass; real scan evidence collected; REST responses available to Android. AC-03/AC-04 evidence starts here. |
| 3 — Sensor Node | Rohan assembles and measures hardware, writes exact firmware protocol, verifies invalid/no-echo behavior and serial records. Rishav reviews risk-input requirements, without moving risk logic to ESP32. | Actual board/pin/power/mount details, firmware revision, serial samples and AC-08 bench results handed to Rishav. |
| 4 — Android Camera AI | Samik integrates CameraX, chosen runtime, both models, transforms, quality/freshness checks, tracking, and target-search confirmation. Rishav supplies app lifecycle and accessible start/stop shell. Spandan corrects export/model issues. | Fresh typed detections/tracks and search events reach the app; both modes run locally; load/camera failure paths observable. |
| 5 — USB Integration | Rohan implements Android USB permission, parsing, sequence/uptime handling, recovery and health events. Rishav connects lifecycle/watchdog scheduling and UI state. | Real sensor events reach Android; malformed/queued/detached/recovered cases verified. Parser replay does not substitute for AC-09 hardware transport. |
| 6 — Fusion + Risk | Rishav implements corridor/risk source precedence, STOP overrides, holds, clear/unknown state and timers. Samik supplies visual histories/transforms; Rohan supplies distance/health and replay cases. | AC-10 rule replay passes; actual camera and sensor feed the same engine; critical distance does not wait for inference. |
| 7 — Voice UX | Rishav finishes TTS arbitration, cooldowns, readiness, pause/Stop, accessible controls, and error notices. Everyone makes their callbacks obey session cancellation. | AC-11 and AC-15 pass for fixtures/device cases; AC-06 physical-event-to-audio test run with Rohan; standalone flow works without networking. |
| 8 — Laptop-to-Phone + Search | Subham completes validated Android REST client. Rishav integrates refresh, candidate selection, stale confirmation and explicit arrival. Samik connects mandatory Locate search/Found and return-to-Mobility. Spandan fixes validated model failures. | Standalone walking, direct nearby search, and full memory-to-final-search flow demonstrated separately; AC-13/AC-14 evidence recorded. |
| 9 — Calibration and Acceptance | All members run their assigned gates on the same recorded setup; fix faults in owned modules and rerun affected gates. Rishav consolidates results and coordinates supervised rehearsal/project-owner review. | Every applicable AC gate has result and evidence, failures resolved or explicitly unqualified, and project-owner acceptance recorded before declaring the MVP accepted. |

### What to do while a dependency is blocked

- If training is incomplete, Subham uses recorded detection fixtures and Samik uses clearly labelled compatible smoke artifacts; neither claims Locate quality passed.
- If hardware is unavailable, Rohan supplies protocol replays and Rishav tests risk rules against them; AC-06/AC-08/AC-09 remain physically unverified.
- If the phone/runtime smoke fails, Spandan and Samik jointly resolve export/runtime compatibility before committing to training/export settings; laptop memory work can continue.
- If the memory network is unavailable, standalone walking and direct nearby search proceed; the memory integration gate stays open.
- Subsystem work may continue against agreed fixtures, but an integrated/physical gate cannot close on fixtures alone.

## 5. Handoff Contracts

Agree concrete function/type names in Phase 0; do not add another transport or copy of the PRD schema. These are minimum payload responsibilities within the existing application.

| Handoff | Producer → receiver | Required content and receiver check |
|---|---|---|
| H1 — Models | Spandan → Subham and Samik | Artifact path/hash, class order/canonical aliases, input type/shape, preprocessing, decoding/NMS, training/split provenance, evaluation limits. Receivers run the same reference images and check labels/boxes and artifact loading on their actual runtime. |
| H2 — Mobile perception | Samik → Rishav | Session/mode, frame ID and monotonic capture/delivery times, geometry version, model identity, quality/usability, normalized upright boxes, confidence, track/history, health/error events. Rishav verifies stale/duplicate/wrong-generation evidence cannot affect risk. No speech or risk decisions in this handoff. |
| H3 — Sensor events | Rohan → Rishav | USB connection/session identity, receipt time, sequence/uptime, validity/distance, recovery/health/error state. Preserve critical close events before coalescing newer distances. Rishav verifies STOP precedes inference and timers still fire when input stops. |
| H4 — Memory | Subham → Rishav | Exact PRD v1 REST samples/client results, success/error distinctions, computed age, candidate validation and cancellable requests. Rishav verifies refresh, explicit selection/confirmation, and old-response rejection; no coordinates interpreted as a route. |
| H5 — Search events | Samik → Rishav | Supported target class, current camera position, confirmation/multiple-candidate/timeout/error event with current session identity. Rishav controls modes/Found and speech priority; test simultaneous near-target STOP. |
| H6 — Build integration | Each owner → Rishav | Exact dependency/manifest/config edits, owned changes, reproducible commands and relevant results. Rishav integrates once and verifies the app still builds and existing flows remain intact. |

Session generation and app modes have one authority: Rishav's coordinator. Each producer accepts the current session context and stops/invalidates callbacks when told. Rohan separately owns USB connection identity; Samik separately owns camera geometry identity. Receivers must not invent timestamps or silently reinterpret units/coordinate systems.

## 6. Acceptance Ownership

The PRD contains the exact samples and thresholds; this table assigns execution responsibility without changing them. The lead assembles the complete result, contributors run their parts, and the reviewer checks evidence. No one marks a cross-device gate passed on a single isolated component.

| Gate | Lead | Required contributors | Evidence reviewer |
|---|---|---|---|
| AC-01 — Standalone offline run | Rishav | Samik, Rohan | Subham |
| AC-02 — Locate quality on both devices | Spandan | Subham, Samik | Rishav |
| AC-03 — Hard Scan | Subham | Spandan | Samik |
| AC-04 — Memory/API cases | Subham | Rishav | Spandan |
| AC-05 — Mobile performance | Samik | Rishav, Spandan | Rohan |
| AC-06 — Physical obstacle to STOP/audio | Rohan | Rishav, Samik | Subham |
| AC-07 — Supervised obstacle encounters | Rishav | Rohan, Samik, Spandan | Subham |
| AC-08 — Sensor measurements | Rohan | Rishav | Samik |
| AC-09 — Real USB and reconnects | Rohan | Rishav | Samik |
| AC-10 — Deterministic risk/transport replay | Rishav | Rohan, Samik | Subham |
| AC-11 — Speech/cancellation timing | Rishav | Rohan | Samik |
| AC-12 — Clear/degraded/failure cases | Samik | Rishav, Rohan | Spandan |
| AC-13 — Android nearby/final search | Samik | Spandan, Rishav | Subham |
| AC-14 — Full memory/search integration | Subham | Rishav, Samik, Spandan | Rohan |
| AC-15 — Lifecycle and late callbacks | Rishav | Subham, Samik, Rohan | Spandan |
| AC-16 — Accessible baseline flows | Rishav | Subham, Samik, Rohan | Spandan |

## 7. Handoff and Status Format

Use this record in each member guide when work progresses. Link real source/artifacts/results; do not fill missing evidence with a completion claim.

```text
Owner / phase:
Status: planned | in progress | blocked | ready for review | verified | accepted
Source revision and changed paths:
Artifact locations and hashes:
Exact run/test commands and prerequisites:
Results: pass/fail, sample counts, timings, evidence paths
Known limitations and untested behavior:
Receiver / reviewer:
Blocker and person needed to resolve it:
Next action:
Receiver review and project-owner acceptance (when applicable):
```

`verified` means the stated checks were performed, not that all PRD gates passed. `accepted` requires explicit acceptance for the stated scope. Rishav's consolidated implementation-state table must distinguish automated checks, real-device checks, supervised demonstration, and owner acceptance.

### Initial Team Status

| Owner | Status | First action | Current blocker/evidence gap |
|---|---|---|---|
| Spandan | Planned | Select the small demo object set and prepare smoke model metadata with Samik | Dataset, trained artifacts, and metrics not established |
| Rishav | Planned | Establish Android shell, shared interfaces, decisions and integration checklist | App/build configuration and chosen phone not established |
| Subham | Planned | Build sample PRD responses and laptop scan/query fixtures | Laptop service and physical scan evidence not established |
| Samik | Planned | Run both model exports on the chosen phone and record decoding/performance | Phone/runtime compatibility not established |
| Rohan | Planned | Identify exact hardware and prove electrical/USB prerequisites | Wiring, USB host behavior and sensor calibration not established |

### Phase 0 Decision Baseline — Record Actual Values in Implementation State

| Decision | Responsible members | Current value |
|---|---|---|
| Phone model/Android version and test availability | Rishav, Samik, Rohan | Not recorded |
| Board revision, GPIOs, supply, USB interface, mount | Rohan | Not recorded |
| Demo Locate classes and shared aliases | Spandan, Subham, Samik | Not recorded |
| Model versions/hashes, Android runtime and decoding | Spandan, Samik | Not recorded |
| Actual Android package paths and shared event types | Rishav with all producers | Not recorded |
| Camera profile/zones and isolated memory network setup | Subham with Rohan/Rishav as needed | Not recorded; never put the bearer token here |

Keep this guide unchanged. Update implementation-state.md, implementation-plan.md and individual execution plans for actual coordination/evidence. The PRD/README and this main guidance are frozen; AGENTS.md defines the hard boundary and authorized change procedure.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
