# Samik — Android Perception and Search Guidance

Authority: [PRD v3.2](../README.md). Coordination: [shared guidance](../guidance.md).

Detailed 24-hour execution plan: [numbered implementation steps](implementation-plan.md). Follow [AGENTS](../../AGENTS.md) for frozen boundaries, communication, continuation and mandatory phase reviews.

**Ownership: assigned by the user. Implementation status: planned, not completed or accepted.** No Android inference speed, accuracy, or search result is established.

## Your Responsibility

Own CameraX capture, both Android model adapters, preprocessing/decoding, coordinate transforms, visual tracking, image quality/freshness signals, and the nearby/final-search engine. Spandan owns weights/training/export metadata; you prove those artifacts run correctly on the actual phone. Rishav owns global mode changes, risk policy, UI controls and speech.

Read PRD Sections 8–9, 13, 16–20, 25–26, and 29–31. Own camera/inference/tracking/search packages and their tests/instrumentation. Ask Rishav to integrate shared build/manifest changes rather than editing another owner's configuration independently.

## Phase-by-Phase Work

| Phase | Your actions | Deliverable / receiver |
|---|---|---|
| 0 — Compatibility | With Spandan, load both model exports on the chosen phone and measure initial timings. Choose one runtime based on evidence, validate label decoding and record model input/output metadata. Agree frame/search events with Rishav. | Phone smoke evidence and H2/H5 contracts → Spandan, Rishav |
| 1 — Stationary Vision | Capture representative phone search viewpoints and compare reference outputs with Spandan. Share data without mixing training/test sessions. | Phone data and export feedback → Spandan |
| 2 — Hard Scan + Memory | Review Subham's AC-03 evidence as assigned; agree that laptop normalized positions are not phone-camera directions. Continue independent camera implementation. | Coordinate-boundary review → Subham, Rishav |
| 3 — Sensor Node | Help Rohan establish camera orientation/mount geometry and review sensor measurement evidence. | Recorded camera geometry/alignment → Rohan |
| 4 — Android Camera AI | Build capture, latest-frame processing, both runtime adapters, quality checks, normalized upright boxes and short-lived matching/tracks. Add deterministic target confirmation and local search. | Actual phone detections/tracks/search events → Rishav |
| 5 — USB Integration | Pair camera evidence with Rohan's real stream only through Rishav's engine; verify frame/capture clocks and that inference does not block USB. | Instrumented real-input feed → Rishav |
| 6 — Fusion + Risk | Supply one-to-one tracks, area history, confidence and geometry identity; create fixtures for stale/duplicate/multi-object/rotation cases. Rishav computes corridor risk and release state. | H2 plus vision portions of AC-10 → Rishav |
| 7 — Voice UX | Ensure camera/search callbacks obey session invalidation and never call TTS directly. Review speech/cancellation timing as assigned. | Late-callback and model-load failure tests → Rishav |
| 8 — Integration + Search | Connect search events to Rishav's modes; test direct nearby search offline and memory-target final search. Confirm current phone coordinates, timeout, multiple targets, and model switch cleanup. | H5 and AC-13 evidence; AC-14 support → Rishav, Subham |
| 9 — Acceptance | Lead AC-05/AC-12/AC-13 and collect the phone half of AC-02 with Spandan. Review assigned hardware/camera/speech gates. | Exact phone/runtime/hash settings and measured results → Rishav |

## Implementation Boundaries

- One active vision model at a time; Android Locate is not optional and phone-to-laptop inference is not a substitute.
- Decode outputs using the artifact's actual contract. Correct letterboxing, crop, orientation and clipping before emitting normalized boxes. Never infer numeric class IDs from another export.
- Include session, mode, capture timestamp, delivery timestamp, frame/geometry identity and quality status in H2. Same frame cannot count multiple times toward track/search persistence.
- Keep at most one pending frame, drop superseded work, and discard stale/out-of-order/wrong-session results. Report camera/model failure distinctly from a successful frame with no detections.
- Implement PRD image-quality checks and expose unusable imagery so Rishav's watchdog can pause. Reset visual histories on geometry/model changes.
- Track matching supplies evidence only. Rishav owns corridor thresholds, risk/clear states, source holds and all alert decisions.
- Search confirmation requires confidence >= 0.60 in 3 of the latest 5 fresh frames within 1 second. Search direction is current phone image position, not laptop memory coordinates. Timeout is 15 seconds after ready search, subject to Rishav's speech priority.

## Handoffs H2 and H5

Deliver source/instrumentation commands, tested model hashes, reference detections, fresh/invalid/error events, and search confirmed/multiple/timeout cases. Show Rishav live phone evidence and replay edge cases. He must verify no old event changes state after Stop or model switching. Provide diagnostics rather than speaking or changing global modes inside perception code.

## Acceptance You Lead

- **AC-05:** At least 10 processed FPS over every 60-second window of the 10-minute run; capture-to-risk p95 <= 300 ms across at least 100 samples. Measure with Rishav's real risk pipeline, not just model benchmark time.
- **AC-12:** Clear/degraded checks including dark/covered imagery, sensor loss, camera stalls and TTS failures. Rohan supplies sensor cases; Rishav supplies state/speech behavior. You assemble the combined evidence.
- **AC-13:** Ten searches per selected class, half direct/offline, plus ten absent-target trials per class. Meet detection time, direction accuracy, zero false-found, timeout and separate loading targets from the PRD.

If export speed/quality fails, work with Spandan and rerun affected gates. Do not hide latency by using old frames or omit missed searches from denominators.

## Current Handoff Record

Owner / phase: Samik / Phase 0
Status: ready for review
Source revision and changed paths: android/app/src/main/java/dev/navisense/contracts/, camera/, inference/, tracking/, search/
Artifact locations and hashes: MobilePerceptionEvent (H2), SearchEvent (H5), PerceptionUnitTests (13/13 passing)
Exact run/test commands and prerequisites: .\gradlew.bat testDebugUnitTest
Results: pass (13/13 unit tests passed in 49ms; verified transforms, PRD §17.2 dark/featureless quality gating, 1-to-1 tracking, area growth, and PRD §20 search confirmation/direction/timeout)
Known limitations and untested behavior: On-device physical phone benchmark pending physical USB attachment over adb.
Receiver / reviewer: Rishav (for contracts and integration); Spandan (for smoke model metadata)
Blocker and person needed to resolve it: None. Physical phone needed for AC-05 live timing.
Next action: Await S02 smoke model export from Spandan; connect phone for on-device load and timing test.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
