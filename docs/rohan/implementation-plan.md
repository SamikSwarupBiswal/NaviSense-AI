# Rohan — 24-Hour Individual Implementation Plan

Owner: **Rohan**. Responsibility: **Hardware, firmware and Android USB**. Status: **planned, not executed**.

Read [AGENTS](../../AGENTS.md), [PRD v3.2](../README.md) Sections 7, 13–19, 26–30, [master timed plan](../implementation-plan.md), [ownership guide](guidance.md), and [implementation state](../implementation-state.md) before implementation.

## 1. Start Here

1. Read all required rules and verify frozen-file hashes under AGENTS.md before modifying source.
2. Inspect existing files, current branch/worktree, actual progress and pending communication messages. Continue from the last verified handoff; do not restart from scratch.
3. Identify the actual event start and current T+ time. No calendar start has been supplied; the plan uses elapsed hours only.
4. Perform the mandatory phase-entry review for your next work package and record its permitted scope. Fixture-only entry cannot authorize a physical acceptance claim.
5. Own **esp32/navisense_sensor/, Android usb/, hardware test scripts**. Use concrete package boundaries in the master plan; request shared build/manifest/contracts changes from Rishav rather than editing another owner's work.
6. Follow steps below. Review/device reservations override coding windows; windows include reviews, not extra hours. Take shared breaks T+06:00–06:30, T+12:00–13:00, T+18:00–18:30.
7. Run checks, communicate delivery and obtain receiver review. Complete a phase-exit review before declaring the phase finished; update implementation-state with actual evidence.

## 2. Numbered Execution Steps

### Step 01 — T+00:00–02:00

**Objective:** Prove actual electrical/USB compatibility.

**Prerequisites:** Actual equipment and available operator.

**Actions in order:**

1. Identify exact phone, board, USB interface, GPIOs and power supply.
2. Verify level handling, electrical limits and mount before use.
3. Produce minimal valid/invalid serial smoke.
4. Use S03 phone slot with Rishav for USB and offline audio readiness observations.

**Verify:** Record real assembly and samples; diagrams/fixtures do not count as hardware proof.

**Deliver/review:** Produce S03 at T+01:45.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 02 — T+02:00–04:00

**Objective:** Implement exact sensor acquisition.

**Prerequisites:** S03 verified assembly.

**Actions in order:**

1. Implement bounded 10 Hz acquisitions, sequence-per-acquisition and completion uptime.
2. Emit PRD validity/distance values with no fabricated far distance or firmware backlog.
3. Bench-check known distances and no echo.
4. Use T+03:15–04:15 device reservation for stream proof.

**Verify:** Real serial records, preliminary measurements and protocol fixtures saved.

**Deliver/review:** Produce S05 at T+04:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 03 — T+04:15–07:00

**Objective:** Build Android USB/parser and live health feed.

**Prerequisites:** S01 contracts and S05 firmware.

**Actions in order:**

1. Implement permission, connection and read lifecycle.
2. Implement bounded LF/CRLF lines and strict field/range/sequence/uptime parsing.
3. Stamp receipt time and preserve usable close events before coalescing.
4. Use T+06:30 phone reservation to verify live adapter after the break.

**Verify:** Malformed input cannot crash; detach invalidates old distance; real phone receives data.

**Deliver/review:** Produce S08 at T+07:00.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 04 — T+07:20–11:30

**Objective:** Complete freshness, recovery and delay handling.

**Prerequisites:** S08 receiver findings.

**Actions in order:**

1. Implement three-valid-record recovery with required receipt spacing/gaps.
2. Expose health evaluation for Rishav's watchdog even when bytes stop.
3. Handle stale/invalid/reset/backlog cases and connection generations.
4. Use T+09:20–09:50 and T+11:20–11:45 device slots; prepare full replay set.

**Verify:** No malformed record refreshes age; first fresh close event remains eligible for STOP.

**Deliver/review:** Produce S13 at T+11:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 05 — T+11:45–16:00

**Objective:** Prepare integrated hardware and measurement stations.

**Prerequisites:** S13 and S14 risk/voice candidate.

**Actions in order:**

1. Join S14 and check sensor decisions are not behind inference.
2. Pre-mark bench distances and configure common physical-event/audio recording.
3. Review S16 including retained USB STOP in search.
4. Freeze firmware/mount/configuration for S17 and document limitations.

**Verify:** Real integrated warning observed and measurement tools ready before final gate slots.

**Deliver/review:** Receive S14 at T+13:00; review S16 at T+14:30; join S17.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

### Step 06 — T+16:00–24:00

**Objective:** Lead physical sensor, USB and audible STOP evidence.

**Prerequisites:** Frozen setup and available operator.

**Actions in order:**

1. Lead AC-08, AC-06 and AC-09 at reserved times.
2. Assist supervised walking and failure checks; review assigned gates.
3. Keep all invalid readings, packet loss and missed-STOP trials in counts.
4. Submit exact setup and evidence for S18.

**Verify:** Full physical-event-to-audio and calibration requirements, not parser tests alone.

**Deliver/review:** Gate appointments below; final bundle at T+23:30.

**Failure direction:** Keep the failing step open. Record the exact error, missing input, evidence and resolver in communication; continue only independent authorized work or explicitly labelled fixtures. Revise downstream times using the master delay rule. Never weaken PRD gates or fabricate successful results.

## 3. Exact Handoff Appointments

These rows are copied from the master schedule. A split review requires only your named sub-slot as receiver; a producer attends both sequential reviews. Delivery does not imply receiver acceptance.

| Instance | Delivery T+ | Review T+ | Producer | Receiver | Dependency |
|---|---|---|---|---|---|
| S01 / H6 | 01:00 | 01:00–01:15 | Rishav | Spandan, Subham, Samik, Rohan | Kickoff decisions |
| S03 / H3 | 01:45 | 01:45–02:00 | Rohan | Rishav | S01 |
| S05 / H3 | 04:00 | 04:00–04:15 | Rohan | Rishav | S03 |
| S08 / H3 | 07:00 | 07:00–07:20 | Rohan | Rishav | S05 |
| S13 / H3 | 11:30 | 11:30–11:45 | Rohan | Rishav | S08 |
| S14 / H6 | 13:00 | 13:00–13:30 | Rishav | Samik, Rohan | S08, S10, S13 |
| S16 / H4/H5/H6 | 14:30 | 14:30–15:00 | Subham, Samik, Rishav | Rohan | S11, S12, S14, S15 |
| S17 / H6 | 15:30 | 15:30–16:00 | Rishav | Spandan, Subham, Samik, Rohan | S16 plus resolved integration blockers |
| S18 / H6 | 23:30 | 23:30–23:45 | Rishav | Spandan, Subham, Samik, Rohan | All gate results recorded; retests complete or failures explicitly retained |

## 4. Acceptance and Review Appointments

All PRD counts/thresholds remain mandatory. The slot includes evidence inspection; prepared fixtures and scenes are required. Keep bundled gate results separate.

| T+ slot | Gate(s) | Lead | Contributors | Reviewer | Resource / preparation |
|---|---|---|---|---|---|
| 17:00–17:20 | AC-08 | Rohan | Rishav | Samik | Sensor bench — 120 readings over six distances with premarked positions; retain invalid counts |
| 17:20–17:40 | AC-10 | Rishav | Rohan, Samik | Subham | Replay harness — Execute already-authored deterministic transport/risk cases and inspect results |
| 17:40–18:00 | AC-11 | Rishav | Rohan | Samik | Phone/audio — Repeated speech, escalation and ten measured Stop cancellations |
| 18:30–19:00 | AC-01, AC-05 | Rishav (AC-01), Samik (AC-05) | Samik, Rohan, Spandan | Subham (AC-01), Rohan (AC-05) | Phone + sensor — One compatible 10-minute offline Mobility run; retain separate assertions for both gates |
| 19:00–19:30 | AC-06 | Rohan | Rishav, Samik | Subham | Phone + sensor + external recording — 20 bench entries at 30/40 cm; physical entry, packet, decision and audible timestamps |
| 19:30–20:00 | AC-09 | Rohan | Rishav | Samik | Phone + sensor — 10-minute stable stream then five reconnect cycles, denominators kept separate |
| 20:00–20:30 | AC-12 | Samik | Rishav, Rohan | Spandan | Phone + sensor/audio — Five-minute clear scene plus dark/covered/stall/sensor/TTS failure cases |
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

When asked to implement this plan, work for Rohan within the stated ownership. Read the governing files completely, verify frozen hashes, review current state and communication, and perform entry review before dependent work. Implement numbered steps against actual delivered prerequisites. Use the master plan's commands only after the required source/tooling exists; record the real command used. Do not invent board pins, network addresses, runtime outputs or training/measurement evidence. Respect other owners and shared resource slots. Run relevant verification, perform exit review, log handoffs in the communication branch, and update the mutable state file. Never mark absent hardware checks or unrun tests passed. Routine authorized work may proceed; frozen-file changes require explicit user authorization as stated in AGENTS.md.

## 7. Current Record

Event start/timezone: not recorded.
Current step: 01 planned.
Actual implementation/evidence: not recorded.
Entry/exit review: pending.
Gate results: use the central implementation-state register; no changes implied by this plan.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
