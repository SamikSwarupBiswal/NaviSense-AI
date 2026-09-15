# Rishav — 24-Hour Individual Implementation Plan

Owner: **Rishav**. Responsibility: **Risk, voice UX, lifecycle and integration**. Status: **planned, not executed**.

Read [AGENTS](../AGENTS.md), [PRD v3.2](../README.md) Sections 6, 13–21, 24–29, 32–36, [master timed plan](../implementation-plan.md), [ownership guide](guidance.md), and [implementation state](../implementation-state.md) before implementation.

## 1. Start Here

1. Read all required rules and verify frozen-file hashes under AGENTS.md before modifying source.
2. Inspect existing files, current branch/worktree, actual progress and pending communication messages. Continue from the last verified handoff; do not restart from scratch.
3. Identify the actual event start and current T+ time. No calendar start has been supplied; the plan uses elapsed hours only.
4. Perform the mandatory phase-entry review for your next work package and record its permitted scope. Fixture-only entry cannot authorize a physical acceptance claim.
5. Own **Android app/, contracts/, navigation/, voice/, shared build/manifest**. Use concrete package boundaries in the master plan; request shared build/manifest/contracts changes from Rishav rather than editing another owner's work.
6. Follow steps below. Review/device reservations override coding windows; windows include reviews, not extra hours. Take shared breaks T+06:00–06:30, T+12:00–13:00, T+18:00–18:30.
7. Run checks, communicate delivery and obtain receiver review. Complete a phase-exit review before declaring the phase finished; update implementation-state with actual evidence.

## 2. Numbered Execution Steps

### Step 01 — T+00:00–02:00

**Objective:** Create one app shell and session authority.

**Prerequisites:** Kickoff and tool/repository inspection.

**Actions in order:**

1. Create/verify project/wrapper and accessible Start/Stop shell.
2. Define producer event types, clock abstraction and session generation; assign packages.
3. Review contracts with all at S01.
4. Observe S03 USB/audio readiness and record actual blockers.

**Verify:** Buildable shell and shared contracts; offline TTS checked on actual phone.

**Deliver/review:** Produce S01 at T+01:00; receive S03 at T+01:45.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 02 — T+02:00–06:00

**Objective:** Implement deterministic risk using controllable time.

**Prerequisites:** PRD rules and fixture inputs.

**Actions in order:**

1. Create separate sensor/vision severity reducers and highest-severity combination.
2. Implement hold/release/loss timers and UNKNOWN/clear conditions.
3. Write boundary, equality, invalidity and track-churn tests with a fake clock.
4. Receive S04–S07 in their slots; connect agreed inputs without waiting for model qualification.

**Verify:** Close STOP is independent of inference; meaningful replay failures are visible.

**Deliver/review:** Receive S04–S07; prepare for S08.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 03 — T+06:30–10:20

**Objective:** Connect real producers and central watchdog/cancellation.

**Prerequisites:** S08 USB, S10 vision, S11 client.

**Actions in order:**

1. Connect real events with session/mode/connection identity.
2. Implement load/first-frame deadlines and independent 50 ms health checks.
3. Make Stop invalidate callbacks, target and speech without waiting on workers.
4. Implement foreground/audio/camera pause behavior and explicit restart.

**Verify:** Stalls are detected without new data; late callbacks cannot restart; API loss does not stop standalone mode.

**Deliver/review:** Receive S08/S10/S11.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 04 — T+10:20–13:00

**Objective:** Finish prioritized voice and baseline controls.

**Prerequisites:** Real inputs, S12 and S13.

**Actions in order:**

1. Implement single TTS arbiter with priority, interruption, cooldowns and health notices.
2. Add accessible selection, arrival, retry and cancellation controls.
3. Keep all producer speech indirect and recognition optional.
4. Prepare standalone integrated candidate before the T+12:00 break.

**Verify:** Actual camera/USB/TTS path works; no stale queued clearance; Stop available everywhere.

**Deliver/review:** Produce S14 at T+13:00; Samik/Rohan review to T+13:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 05 — T+13:30–15:30

**Objective:** Integrate memory/search and freeze candidate.

**Prerequisites:** S11/S12 and verified S15 models.

**Actions in order:**

1. Implement refresh-before-Guide with explicit stale/ambiguous/failure decisions.
2. Require user arrival and stationary search; retain USB STOP and explicit return to walking.
3. Co-deliver S16 and fix integration defects in owned code.
4. Package exact build/models/firmware/config hashes and prepared gate commands.

**Verify:** Three real flows, no silent fallback; candidate and measurement fixtures ready.

**Deliver/review:** Joint S16 at T+14:30; produce S17 at T+15:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 06 — T+16:00–24:00

**Objective:** Run cross-system gates and review final scope.

**Prerequisites:** S17 and scheduled owners/operators.

**Actions in order:**

1. Lead AC-01/AC-07/AC-10/AC-11/AC-15/AC-16; contribute to other tests.
2. Collect lead/reviewer results and update tracker.
3. Use T+22:45–23:30 for bounded fixes and required retests.
4. Deliver S18 and prepare launch at T+23:45–24:00; record owner acceptance only when justified.

**Verify:** All gates have actual results/evidence; failures remain explicit, not renamed complete.

**Deliver/review:** Produce S18 at T+23:30; review ends T+23:45.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

## 3. Exact Handoff Appointments

These rows are copied from the master schedule. A split review requires only your named sub-slot as receiver; a producer attends both sequential reviews. Delivery does not imply receiver acceptance.

| Instance | Delivery T+ | Review T+ | Producer | Receiver | Dependency |
|---|---|---|---|---|---|
| S01 / H6 | 01:00 | 01:00–01:15 | Rishav | Spandan, Subham, Samik, Rohan | Kickoff decisions |
| S03 / H3 | 01:45 | 01:45–02:00 | Rohan | Rishav | S01 |
| S04 / H4 | 03:00 | 03:00–03:15 | Subham | Rishav | S01 |
| S05 / H3 | 04:00 | 04:00–04:15 | Rohan | Rishav | S03 |
| S06 / H2 | 05:00 | 05:00–05:15 | Samik | Rishav | S01, S02 |
| S07 / H4 | 05:30 | 05:30–05:45 | Subham | Rishav | S02, S04 |
| S08 / H3 | 07:00 | 07:00–07:20 | Rohan | Rishav | S05 |
| S10 / H2 | 09:00 | 09:00–09:20 | Samik | Rishav | S06, S09 for real Locate integration |
| S11 / H4 | 10:00 | 10:00–10:20 | Subham | Rishav | S07, S01 |
| S12 / H5 | 11:00 | 11:00–11:20 | Samik | Rishav | S09, S10 |
| S13 / H3 | 11:30 | 11:30–11:45 | Rohan | Rishav | S08 |
| S14 / H6 | 13:00 | 13:00–13:30 | Rishav | Samik, Rohan | S08, S10, S13 |
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

When asked to implement this plan, work for Rishav within the stated ownership. Read the governing files completely, verify frozen hashes, review current state and communication, and perform entry review before dependent work. Implement numbered steps against actual delivered prerequisites. Use the master plan's commands only after the required source/tooling exists; record the real command used. Do not invent board pins, network addresses, runtime outputs or training/measurement evidence. Respect other owners and shared resource slots. Run relevant verification, perform exit review, log handoffs in the communication branch, and update the mutable state file. Never mark absent hardware checks or unrun tests passed. Routine authorized work may proceed; frozen-file changes require explicit user authorization as stated in AGENTS.md.

## 7. Current Record

Event start/timezone: not recorded.
Current step: 01 planned.
Actual implementation/evidence: not recorded.
Entry/exit review: pending.
Gate results: use the central implementation-state register; no changes implied by this plan.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
