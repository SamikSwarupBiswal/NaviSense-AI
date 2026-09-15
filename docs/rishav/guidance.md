# Rishav — Android Risk, Voice and Integration Guidance

Authority: [PRD v3.2](../README.md). Coordination: [shared guidance](../guidance.md).

Detailed 24-hour execution plan: [numbered implementation steps](implementation-plan.md). Follow [AGENTS](../AGENTS.md) for frozen boundaries, communication, continuation and mandatory phase reviews.

**Ownership: assigned by the user. Implementation status: planned, not completed or accepted.** App implementation, integration tests and owner acceptance have not been established.

## Your Responsibility

Own the Android shell, accessible user flows, session coordinator, risk/path state, shared speech arbiter, and integration. Coordinate shared build/manifest changes and acceptance reporting. Each teammate still owns and fixes their own subsystem; integration ownership does not transfer their implementation work to you.

Read PRD Sections 6, 13–21, 24–29, and 32–36. Own app-wide configuration/build files, navigation, voice, lifecycle, shared event contracts, and integration tests. Samik supplies camera/tracks/search events, Rohan supplies sensor events, and Subham supplies validated memory results.

## Phase-by-Phase Work

| Phase | Your actions | Deliverable / receiver |
|---|---|---|
| 0 — Compatibility | Create one Android app skeleton with accessible Start/Stop, choose package boundaries, define producer events and session context, and prove offline TTS on the chosen phone. Record device/runtime/class/USB decisions with owners. | Buildable shell and shared contracts → all Android contributors |
| 1 — Stationary Vision | Confirm model/class handoffs are scoped to the selected demo. Begin coordinator and fake-input risk fixtures without claiming model qualification. | Shared target/class expectations → Spandan, Subham, Samik |
| 2 — Hard Scan + Memory | Agree selection/stale/ambiguous UI with Subham; consume documented fixtures to develop screens independently of live API readiness. | Accessible query/selection flow with labelled fixtures → Subham |
| 3 — Sensor Node | Review events needed for immediate close STOP, health and connection identity. Help Rohan record actual hardware prerequisites. | H3 receiver agreement → Rohan |
| 4 — Android Camera AI | Connect Samik's fresh frame/track/search events, enforce mode/model readiness and a 5-second load limit, and handle camera failure. | App coordinator integration → Samik |
| 5 — USB Integration | Connect Rohan's events and independent health watchdog. Ensure sensor risk runs without waiting on vision or network calls. | Real sensor status and lifecycle integration → Rohan |
| 6 — Fusion + Risk | Implement separate sensor/vision severities, highest-risk selection, release timers, tentative-track clearance reset, and BLOCKED/CLEAR_OBSERVED/UNKNOWN. Validate against replay cases. | Risk engine and AC-10 evidence → reviewers |
| 7 — Voice UX | Implement one speech arbiter, STOP preemption, cooldowns, sensor-loss notice, pause/error handling, accessible controls and immediate user cancellation. | Working standalone experience; AC-11/AC-15 contributions → team |
| 8 — Integration + Search | Integrate Subham's real client and Samik's search engine. Refresh on Guide, require selection/stale confirmation and explicit arrival, preserve proximity STOP in stationary search, and make return-to-walking explicit. | Complete three user experiences → Subham leads AC-14 evidence |
| 9 — Acceptance | Lead your gates, request others' evidence, resolve integration issues through the module owners, coordinate supervised rehearsal, and record project-owner review. | Consolidated acceptance state in implementation-state.md; exact evidence locations |

## Risk and Lifecycle Rules to Implement Exactly

- Fresh ultrasonic distance <= 50 cm produces STOP even without a visual match; <= 100 cm slow-down; <= 150 cm awareness. PRD Section 17 controls recovery exceptions, holds and vision rules.
- Invalid data is not clear. Source holds are independent and track-ID churn cannot erase a STOP. Check deadlines at least every 50 ms even if producers stop sending.
- Use one session generation authority. Stop/pause/mode transitions invalidate old asynchronous work and speech. User Stop is available during loading, prompts, errors and TTS, with no confirmation dialog.
- Standalone Mobility needs neither target nor laptop. Losing the API cannot stop it. Phone-only vision has no CLEAR_OBSERVED state.
- FinalSearch/Found are stationary; do not run Mobility rules on Locate outputs. USB STOP takes priority over a found announcement. Do not restart walking automatically.
- Keep operation foreground-only. Fatal camera/model/audio failures pause and require explicit restart. Start checks readiness; merely showing the camera screen does not establish readiness.

## Interfaces and Handoffs

Receive H2/H5 from Samik, H3 from Rohan, and H4 from Subham. Reject wrong-generation/stale events and keep each module's error distinct from an empty successful result. Integrate shared build edits through H6. Give producers cancellation hooks rather than asking them to infer global state.

Your output is the composed application, reproducible integration commands, and evidence showing real producer connections. Keep fake-data controls out of the claimed production/demo path and label any fixture-only run.

## Acceptance You Lead

Lead **AC-01, AC-07, AC-10, AC-11, AC-15 and AC-16**. Contribute to hardware latency, memory, runtime and end-to-end gates as assigned in the shared table.

Important evidence includes the 10-minute offline standalone run, exact replay outcomes, 20 supervised obstacle encounters, <= 250 ms handler-to-silence cancellation, and TalkBack operation with speech recognition disabled. Gate thresholds and test denominators remain the PRD's authority. Rohan leads physical STOP latency; Subham leads full memory/search evidence; do not mark those passed yourself without their complete results and review.

## First Work and Boundaries

Start with the shell, Stop, shared event types and fixture-driven integration; do not wait for final training to implement lifecycle controls. Do not reimplement Samik's decoder/tracker, Rohan's USB parser, or Subham's memory logic. Optional OCR, LLMs, routing and automatic escape directions are not priorities.

## Current Handoff Record

Status: planned. Source/build and evidence: not yet recorded. Receivers: all members for shared contracts; assigned reviewers for gates. Next action: establish Phase 0 shell/interfaces and populate the mutable implementation-state decision table. Use the shared status format for later records.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
