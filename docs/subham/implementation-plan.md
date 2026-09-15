# Subham — 24-Hour Individual Implementation Plan

Owner: **Subham**. Responsibility: **Laptop memory, API and Android client**. Status: **planned, not executed**.

Read [AGENTS](../../AGENTS.md), [PRD v3.2](../README.md) Sections 10–13, 23–26, 29, [master timed plan](../implementation-plan.md), [ownership guide](guidance.md), and [implementation state](../implementation-state.md) before implementation.

## 1. Start Here

1. Read all required rules and verify frozen-file hashes under AGENTS.md before modifying source.
2. Inspect existing files, current branch/worktree, actual progress and pending communication messages. Continue from the last verified handoff; do not restart from scratch.
3. Identify the actual event start and current T+ time. No calendar start has been supplied; the plan uses elapsed hours only.
4. Perform the mandatory phase-entry review for your next work package and record its permitted scope. Fixture-only entry cannot authorize a physical acceptance claim.
5. Own **laptop/ and Android networking/**. Use concrete package boundaries in the master plan; request shared build/manifest/contracts changes from Rishav rather than editing another owner's work.
6. Follow steps below. Review/device reservations override coding windows; windows include reviews, not extra hours. Take shared breaks T+06:00–06:30, T+12:00–13:00, T+18:00–18:30.
7. Run checks, communicate delivery and obtain receiver review. Complete a phase-exit review before declaring the phase finished; update implementation-state with actual evidence.

## 2. Numbered Execution Steps

### Step 01 — T+00:00–03:00

**Objective:** Establish laptop adapter and exact API fixtures.

**Prerequisites:** Kickoff, S01 and S02.

**Actions in order:**

1. Inspect/create Python package, requirements-dev file and test harness.
2. Review smoke model at T+01:30; verify webcam access and supply laptop captures by T+02:30.
3. Create exact found/ambiguous/stale/historical/error response fixtures.
4. Agree typed client outputs with Rishav; do not add unnecessary endpoints.

**Verify:** Fixtures match PRD v1; commands and prerequisites are reproducible.

**Deliver/review:** Produce S04 at T+03:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 02 — T+03:15–05:30

**Objective:** Implement atomic scans and truthful object memory.

**Prerequisites:** S04 contract and labelled detector fixtures/smoke adapter.

**Actions in order:**

1. Implement ten distinct scheduled frames and accepted-match persistence in non-overlapping zones.
2. Implement authoritative SQLite schema and one-transaction scan snapshots.
3. Invalidate commits on cancellation, Clear history and profile changes; handle rollback.
4. Implement latest-snapshot selection, age/clock validation and stale/ambiguous semantics.

**Verify:** Test clear-during-scan, partial failures, duplicates, age 60/61, old profiles and empty later scans.

**Deliver/review:** Produce S07 at T+05:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 03 — T+05:45–08:40

**Objective:** Finish real service and connect trained artifact.

**Prerequisites:** S07 review; S09 when delivered.

**Actions in order:**

1. Implement authenticated health/locate routes, bounded inputs and parameterized SQL.
2. Keep tokens out of logs/source and use the permitted isolated network.
3. Prepare actual run command bound to the verified interface.
4. Review S09 laptop candidate at T+08:20–08:40 and connect its accepted artifact.

**Verify:** Real webcam/service outputs recorded; negative lookup differs from unavailable service.

**Deliver/review:** Service is available for S11; report S09 receiver findings to Spandan.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 04 — T+08:40–10:00

**Objective:** Implement the cancellable Android memory client.

**Prerequisites:** S01 shared types and actual API contract.

**Actions in order:**

1. Implement 2-second total timeout, one request, response cap and strict fields/status/class/age checks.
2. Preserve request/session identity and discard late replies.
3. Return results without mutating global target or speaking.
4. Use T+09:50 phone reservation for live connectivity checks.

**Verify:** Malformed/late responses cannot create a target; actual phone query works or blocker recorded.

**Deliver/review:** Produce S11 at T+10:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 05 — T+10:20–15:30

**Objective:** Integrate memory-to-search and stage tests.

**Prerequisites:** S11/S12 and Rishav coordinator.

**Actions in order:**

1. Help Rishav wire refresh-before-Guide, explicit stale/ambiguous selection and deletion invalidation.
2. Prepare 20 scan layouts and all AC-04 fixtures.
3. Review S15 model at T+13:45–14:00.
4. Co-deliver actual full-flow candidate and resolve owned defects.

**Verify:** Laptop positions never become invented routes/phone directions; independent modes survive API loss.

**Deliver/review:** Joint S16 at T+14:30; S17 review at T+15:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 06 — T+16:00–24:00

**Objective:** Lead memory and integration evidence.

**Prerequisites:** Frozen build and prepared cases.

**Actions in order:**

1. Lead AC-03/AC-04 and AC-14; contribute to other client/lifecycle checks.
2. Review assigned cross-system gates.
3. Record commands, counts, actual outputs and prior failures.
4. Submit evidence for S18 without marking unfinished checks passed.

**Verify:** All PRD scan/query faults and real integrated flows executed for claimed gates.

**Deliver/review:** Gate appointments below; final bundle at T+23:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

## 3. Exact Handoff Appointments

These rows are copied from the master schedule. A split review requires only your named sub-slot as receiver; a producer attends both sequential reviews. Delivery does not imply receiver acceptance.

| Instance | Delivery T+ | Review T+ | Producer | Receiver | Dependency |
|---|---|---|---|---|---|
| S01 / H6 | 01:00 | 01:00–01:15 | Rishav | Spandan, Subham, Samik, Rohan | Kickoff decisions |
| S02 / H1 | 01:15 | 01:15–01:30 Samik; 01:30–01:45 Subham | Spandan | Samik, Subham | S01 |
| S04 / H4 | 03:00 | 03:00–03:15 | Subham | Rishav | S01 |
| S07 / H4 | 05:30 | 05:30–05:45 | Subham | Rishav | S02, S04 |
| S09 / H1 | 08:00 | 08:00–08:20 Samik; 08:20–08:40 Subham | Spandan | Samik, Subham | S02, actual training and export completion |
| S11 / H4 | 10:00 | 10:00–10:20 | Subham | Rishav | S07, S01 |
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
| 17:20–17:40 | AC-10 | Rishav | Rohan, Samik | Subham | Replay harness — Execute already-authored deterministic transport/risk cases and inspect results |
| 18:30–19:00 | AC-01, AC-05 | Rishav (AC-01), Samik (AC-05) | Samik, Rohan, Spandan | Subham (AC-01), Rohan (AC-05) | Phone + sensor — One compatible 10-minute offline Mobility run; retain separate assertions for both gates |
| 19:00–19:30 | AC-06 | Rohan | Rishav, Samik | Subham | Phone + sensor + external recording — 20 bench entries at 30/40 cm; physical entry, packet, decision and audible timestamps |
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

When asked to implement this plan, work for Subham within the stated ownership. Read the governing files completely, verify frozen hashes, review current state and communication, and perform entry review before dependent work. Implement numbered steps against actual delivered prerequisites. Use the master plan's commands only after the required source/tooling exists; record the real command used. Do not invent board pins, network addresses, runtime outputs or training/measurement evidence. Respect other owners and shared resource slots. Run relevant verification, perform exit review, log handoffs in the communication branch, and update the mutable state file. Never mark absent hardware checks or unrun tests passed. Routine authorized work may proceed; frozen-file changes require explicit user authorization as stated in AGENTS.md.

## 7. Current Record

Event start/timezone: not recorded.
Current step: 01 planned.
Actual implementation/evidence: not recorded.
Entry/exit review: pending.
Gate results: use the central implementation-state register; no changes implied by this plan.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
