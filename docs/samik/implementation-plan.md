# Samik — 24-Hour Individual Implementation Plan

Owner: **Samik**. Responsibility: **Android camera, inference, tracking and search**. Status: **planned, not executed**.

Read [AGENTS](../AGENTS.md), [PRD v3.2](../README.md) Sections 8–9, 13, 16–20, 25–26, 29, [master timed plan](../implementation-plan.md), [ownership guide](guidance.md), and [implementation state](../implementation-state.md) before implementation.

## 1. Start Here

1. Read all required rules and verify frozen-file hashes under AGENTS.md before modifying source.
2. Inspect existing files, current branch/worktree, actual progress and pending communication messages. Continue from the last verified handoff; do not restart from scratch.
3. Identify the actual event start and current T+ time. No calendar start has been supplied; the plan uses elapsed hours only.
4. Perform the mandatory phase-entry review for your next work package and record its permitted scope. Fixture-only entry cannot authorize a physical acceptance claim.
5. Own **Android camera/, inference/, search/ and corresponding tests**. Use concrete package boundaries in the master plan; request shared build/manifest/contracts changes from Rishav rather than editing another owner's work.
6. Follow steps below. Review/device reservations override coding windows; windows include reviews, not extra hours. Take shared breaks T+06:00–06:30, T+12:00–13:00, T+18:00–18:30.
7. Run checks, communicate delivery and obtain receiver review. Complete a phase-exit review before declaring the phase finished; update implementation-state with actual evidence.

## 2. Numbered Execution Steps

### Step 01 — T+00:00–02:30

**Objective:** Select working runtime and capture phone examples.

**Prerequisites:** S01/S02, actual phone.

**Actions in order:**

1. Inspect/create owned runtime adapters under agreed package.
2. Use S02 phone slot to load both exports and check input/output/labels.
3. Record actual runtime choice and request shared build changes through Rishav.
4. Capture representative phone views at T+02:00–02:30 for Spandan.

**Verify:** Both smoke artifacts load with understood transforms; no custom accuracy claim yet.

**Deliver/review:** S02 receiver T+01:15–01:30; data by T+02:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 02 — T+02:30–05:00

**Objective:** Implement fresh, correctly transformed camera evidence.

**Prerequisites:** Agreed runtime and event types.

**Actions in order:**

1. Integrate CameraX with at most one pending frame.
2. Decode labels/boxes and undo letterbox/crop/rotation before normalization.
3. Emit distinct frame/capture/delivery/geometry/session metadata and explicit errors.
4. Reject bad boxes and unusable timestamps; use desktop fixtures during Rohan's phone slots.

**Verify:** Reference outputs match; real frames observed; stale/wrong-generation results rejected.

**Deliver/review:** Produce S06 at T+05:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 03 — T+05:15–09:00

**Objective:** Add quality checks, tracking and real candidate checks.

**Prerequisites:** S06; S09 when available.

**Actions in order:**

1. Implement PRD frame-quality rejection and one-to-one short-lived matching.
2. Track persistence, expiry and area history without making risk decisions.
3. Reset on geometry/model changes.
4. Review trained Locate at T+08:00–08:20 and report model versus decoder defects.

**Verify:** Duplicate/low-confidence inputs cannot refresh evidence; both runtime adapters validated.

**Deliver/review:** Receive S09; produce S10 at T+09:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 04 — T+09:20–11:00

**Objective:** Implement direct/final target-search events.

**Prerequisites:** S09 artifact and target/session contract.

**Actions in order:**

1. Filter supported class and implement 3-of-5 fresh confirmation.
2. Emit current-camera left/center/right, multiple-candidate and 15-second ready-search timeout.
3. Return events only; Rishav owns global Found/modes and speech.
4. Test cancelled/old results and negatives; use fixtures during USB/client device reservations.

**Verify:** Nearby search runs locally with no laptop; direction and timeout follow PRD.

**Deliver/review:** Produce S12 at T+11:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 05 — T+11:20–15:30

**Objective:** Integrate safe mode switching and prepare benchmarks.

**Prerequisites:** S12, S14, S15 and coordinator.

**Actions in order:**

1. Review S14 with Rohan.
2. Verify final model at T+13:30–13:45.
3. Connect search/Found and switch cleanup with Rishav; preserve USB STOP priority.
4. Prepare real timing instrumentation, quality runner and present/absent scenes.

**Verify:** S16 three-flow candidate works; exact models/runtime/geometry in S17.

**Deliver/review:** Join S16 at T+14:30 and S17 at T+15:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 06 — T+16:00–24:00

**Objective:** Lead runtime, degraded-state and search evidence.

**Prerequisites:** Frozen phone/artifacts and prepared tests.

**Actions in order:**

1. Lead AC-05/AC-12/AC-13 and supply phone AC-02 output.
2. Contribute to risk/hardware/lifecycle tests and reviews.
3. Measure composed app rather than just standalone model speed.
4. Record all search trials and changed artifact impacts before S18.

**Verify:** Required FPS/latency/search counts and failure tests recorded without omitted misses.

**Deliver/review:** Gate appointments below; final bundle at T+23:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

## 3. Exact Handoff Appointments

These rows are copied from the master schedule. A split review requires only your named sub-slot as receiver; a producer attends both sequential reviews. Delivery does not imply receiver acceptance.

| Instance | Delivery T+ | Review T+ | Producer | Receiver | Dependency |
|---|---|---|---|---|---|
| S01 / H6 | 01:00 | 01:00–01:15 | Rishav | Spandan, Subham, Samik, Rohan | Kickoff decisions |
| S02 / H1 | 01:15 | 01:15–01:30 Samik; 01:30–01:45 Subham | Spandan | Samik, Subham | S01 |
| S06 / H2 | 05:00 | 05:00–05:15 | Samik | Rishav | S01, S02 |
| S09 / H1 | 08:00 | 08:00–08:20 Samik; 08:20–08:40 Subham | Spandan | Samik, Subham | S02, actual training and export completion |
| S10 / H2 | 09:00 | 09:00–09:20 | Samik | Rishav | S06, S09 for real Locate integration |
| S12 / H5 | 11:00 | 11:00–11:20 | Samik | Rishav | S09, S10 |
| S14 / H6 | 13:00 | 13:00–13:30 | Rishav | Samik, Rohan | S08, S10, S13 |
| S15 / H1 | 13:30 | 13:30–13:45 Samik; 13:45–14:00 Subham | Spandan | Samik, Subham | S09, completed correction/evaluation |
| S16 / H4/H5/H6 | 14:30 | 14:30–15:00 | Subham, Samik, Rishav | Rohan | S11, S12, S14, S15 |
| S17 / H6 | 15:30 | 15:30–16:00 | Rishav | Spandan, Subham, Samik, Rohan | S16 plus resolved integration blockers |
| S18 / H6 | 23:30 | 23:30–23:45 | Rishav | Spandan, Subham, Samik, Rohan | All gate results recorded; retests complete or failures explicitly retained |

## 4. Acceptance and Review Appointments

All PRD counts/thresholds remain mandatory. The slot includes evidence inspection; prepared fixtures and scenes are required. Keep bundled gate results separate.

| T+ slot | Gate(s) | Lead | Contributors | Reviewer | Resource / preparation |
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

## 5. Required Handoff Contents

- Source revision, changed owned paths and exact artifact/configuration hashes.
- Actual setup/build/run/test commands and prerequisites.
- Results with sample counts, failures and evidence type: fixture, real device or supervised run.
- Planned and actual delivery/review timestamps, receiver verdict and next action.
- Blocker resolver and rescheduled dependent appointment if needed.
- Updated central implementation-state rows. Do not update frozen README/main guidance.

## 6. AI Execution Instruction

When asked to implement this plan, work for Samik within the stated ownership. Read the governing files completely, verify frozen hashes, review current state and communication, and perform entry review before dependent work. Implement numbered steps against actual delivered prerequisites. Use the master plan's commands only after the required source/tooling exists; record the real command used. Do not invent board pins, network addresses, runtime outputs or training/measurement evidence. Respect other owners and shared resource slots. Run relevant verification, perform exit review, log handoffs in the communication branch, and update the mutable state file. Never mark absent hardware checks or unrun tests passed. Routine authorized work may proceed; frozen-file changes require explicit user authorization as stated in AGENTS.md.

## 7. Current Record

Event start/timezone: not recorded.
Current step: 01 smoke corrections tested; Step 02 live CameraX pending.
Actual implementation/evidence: Codex review fixes on 2026-09-15; 44 JVM tests and 4 OPPO instrumentation tests pass. Corrected benchmark asserts actual forward execution; synthetic timing is not AC-05. See central implementation-state correction record.
Entry/exit review: owned regression fixes authorized by user; named receiver re-review pending; Phase 0 exit reopened.
Gate results: use the central implementation-state register; no changes implied by this plan.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
