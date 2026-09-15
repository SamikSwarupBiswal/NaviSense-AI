# Spandan — 24-Hour Individual Implementation Plan

Owner: **Spandan**. Responsibility: **Models and datasets**. Status: **planned, not executed**.

Read [AGENTS](../../AGENTS.md), [PRD v3.2](../README.md) Sections 8–9, 10–11, 20, 29–31, [master timed plan](../implementation-plan.md), [ownership guide](guidance.md), and [implementation state](../implementation-state.md) before implementation.

## 1. Start Here

1. Read all required rules and verify frozen-file hashes under AGENTS.md before modifying source.
2. Inspect existing files, current branch/worktree, actual progress and pending communication messages. Continue from the last verified handoff; do not restart from scratch.
3. Identify the actual event start and current T+ time. No calendar start has been supplied; the plan uses elapsed hours only.
4. Perform the mandatory phase-entry review for your next work package and record its permitted scope. Fixture-only entry cannot authorize a physical acceptance claim.
5. Own **models/, datasets/ manifests, scripts/ training/export/evaluation**. Use concrete package boundaries in the master plan; request shared build/manifest/contracts changes from Rishav rather than editing another owner's work.
6. Follow steps below. Review/device reservations override coding windows; windows include reviews, not extra hours. Take shared breaks T+06:00–06:30, T+12:00–13:00, T+18:00–18:30.
7. Run checks, communicate delivery and obtain receiver review. Complete a phase-exit review before declaring the phase finished; update implementation-state with actual evidence.

## 2. Numbered Execution Steps

### Step 01 — T+00:00–01:15

**Objective:** Unblock model adapters.

**Prerequisites:** Kickoff class/runtime decisions.

**Actions in order:**

1. Select two feasible demo classes and canonical aliases; record actual starting weights and provenance.
2. Export compatible Locate/Mobility smoke artifacts; clearly label unqualified smoke weights.
3. Write input/output, label-order, preprocessing, decoding and hash metadata; provide shared reference images.

**Verify:** Both receivers can load smoke artifacts; this does not establish custom-object accuracy.

**Deliver/review:** Produce S02; sequential receiver checks finish T+01:45.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 02 — T+01:45–04:00

**Objective:** Build a valid training/evaluation dataset.

**Prerequisites:** Actual objects, cameras and agreed labels.

**Actions in order:**

1. Define capture/annotation/session rules.
2. Receive phone examples from Samik and laptop examples from Subham by T+02:30; vary placement, light, clutter and occlusion.
3. Annotate, inspect labels and partition by session/layout before training.
4. Reserve complete PRD held-out counts per device; do not shrink them to meet a suggested split percentage.

**Verify:** Manifest covers classes, session IDs, provenance and disjoint splits; report insufficient counts.

**Deliver/review:** Data-readiness update to Subham, Samik and Rishav by T+04:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 03 — T+04:00–08:00

**Objective:** Train and export a real Locate candidate.

**Prerequisites:** Verified labels/splits and actual training compute.

**Actions in order:**

1. Run reproducible fine-tuning with real epoch logs and saved checkpoints.
2. Estimate ETA from observed epoch duration; automated work can continue during the shared break.
3. Keep Mobility pretrained unless measured evidence justifies fine-tuning.
4. Export the completed candidate and check label/output compatibility.

**Verify:** Saved checkpoint and export exist; report per-class results and misses without hiding failures.

**Deliver/review:** Produce S09 at T+08:00; Samik reviews before Subham, ending T+08:40.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 04 — T+08:40–13:30

**Objective:** Correct demonstrated faults and freeze model identity.

**Prerequisites:** S09 receiver findings.

**Actions in order:**

1. Separate decoder/export faults from detection errors with Samik/Subham.
2. Correct justified data/export issues and rerun affected checks.
3. Prepare held-out evaluation inputs and reproducible commands.
4. Freeze planned candidate hashes and package limitations; no speculative retraining after handoff.

**Verify:** Both receiver reference outputs agree; training outputs and evaluation limits recorded.

**Deliver/review:** Produce S15 at T+13:30; sequential reviews finish T+14:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 05 — T+14:00–16:00

**Objective:** Prepare quality evidence and final build inputs.

**Prerequisites:** Reviewed S15 artifacts.

**Actions in order:**

1. Pre-stage labelled reference/test images and negative scenes.
2. Help diagnose S16 only within model ownership.
3. Deliver exact hashes, metadata and evaluation commands for S17.

**Verify:** Same artifacts are used for both integration and gate runs.

**Deliver/review:** Join S16/S17; lead AC-02 at T+16:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 06 — T+16:00–24:00

**Objective:** Execute quality gates and assigned reviews.

**Prerequisites:** Frozen candidate and reserved tests.

**Actions in order:**

1. Lead AC-02 on laptop and phone separately.
2. Support search/obstacle/full-flow tests; review assigned evidence.
3. Retain raw counts, failures and each artifact identity.
4. Submit evidence before S18 and identify any unqualified classes/experiences.

**Verify:** Full PRD quality requirements, not aggregate mAP alone, determine the gate.

**Deliver/review:** Gate appointments below; final bundle at T+23:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

## 3. Exact Handoff Appointments

These rows are copied from the master schedule. A split review requires only your named sub-slot as receiver; a producer attends both sequential reviews. Delivery does not imply receiver acceptance.

| Instance | Delivery T+ | Review T+ | Producer | Receiver | Dependency |
|---|---|---|---|---|---|
| S01 / H6 | 01:00 | 01:00–01:15 | Rishav | Spandan, Subham, Samik, Rohan | Kickoff decisions |
| S02 / H1 | 01:15 | 01:15–01:30 Samik; 01:30–01:45 Subham | Spandan | Samik, Subham | S01 |
| S09 / H1 | 08:00 | 08:00–08:20 Samik; 08:20–08:40 Subham | Spandan | Samik, Subham | S02, actual training and export completion |
| S15 / H1 | 13:30 | 13:30–13:45 Samik; 13:45–14:00 Subham | Spandan | Samik, Subham | S09, completed correction/evaluation |
| S17 / H6 | 15:30 | 15:30–16:00 | Rishav | Spandan, Subham, Samik, Rohan | S16 plus resolved integration blockers |
| S18 / H6 | 23:30 | 23:30–23:45 | Rishav | Spandan, Subham, Samik, Rohan | All gate results recorded; retests complete or failures explicitly retained |

## 4. Acceptance and Review Appointments

All PRD counts/thresholds remain mandatory. The slot includes evidence inspection; prepared fixtures and scenes are required. Keep bundled gate results separate.

| T+ slot | Gate(s) | Lead | Contributors | Reviewer | Resource / preparation |
|---|---|---|---|---|---|
| 16:00–16:30 | AC-02 | Spandan | Subham, Samik | Rishav | Phone + laptop — Held-out quality on the exact exported models; annotations ready beforehand |
| 16:30–17:00 | AC-03, AC-04 | Subham | Spandan, Rishav | Samik (AC-03), Spandan (AC-04) | Laptop; phone for client validation — 20 prepared Hard Scans plus prebuilt API/transaction/client fixture suite |
| 18:30–19:00 | AC-01, AC-05 | Rishav (AC-01), Samik (AC-05) | Samik, Rohan, Spandan | Subham (AC-01), Rohan (AC-05) | Phone + sensor — One compatible 10-minute offline Mobility run; retain separate assertions for both gates |
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

When asked to implement this plan, work for Spandan within the stated ownership. Read the governing files completely, verify frozen hashes, review current state and communication, and perform entry review before dependent work. Implement numbered steps against actual delivered prerequisites. Use the master plan's commands only after the required source/tooling exists; record the real command used. Do not invent board pins, network addresses, runtime outputs or training/measurement evidence. Respect other owners and shared resource slots. Run relevant verification, perform exit review, log handoffs in the communication branch, and update the mutable state file. Never mark absent hardware checks or unrun tests passed. Routine authorized work may proceed; frozen-file changes require explicit user authorization as stated in AGENTS.md.

## 7. Current Record

Event start/timezone: not recorded.
Current step: 01 planned.
Actual implementation/evidence: not recorded.
Entry/exit review: pending.
Gate results: use the central implementation-state register; no changes implied by this plan.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
