# Subham — Laptop Locate, Memory and API Guidance

Authority: [PRD v3.2](../README.md). Coordination: [shared guidance](../guidance.md).

Detailed 24-hour execution plan: [numbered implementation steps](implementation-plan.md). Follow [AGENTS](../AGENTS.md) for frozen boundaries, communication, continuation and mandatory phase reviews.

**Ownership: assigned by the user. Implementation status: planned, not completed or accepted.** No laptop service, database, API or Subham test result is claimed complete.

## Your Responsibility

Own the complete laptop path from webcam to trusted last-seen observation and object query. Also own the Android memory networking client so response validation has one implementation owner. Rishav owns the client-facing screens and target activation; Spandan supplies models rather than your laptop adapter.

Read PRD Sections 8–13, 23–26, 29–32. Own `laptop/` and the Android networking package/tests. No remote delete/scan endpoint is required; local scan and Clear history controls are yours.

## Phase-by-Phase Work

| Phase | Your actions | Deliverable / receiver |
|---|---|---|
| 0 — Compatibility | Verify laptop camera/Python prerequisites, propose a fixed-camera zone profile, and prepare exact v1 response fixtures for found/ambiguous/stale/missing/errors. Agree classes/aliases with Spandan and client contracts with Rishav. | Fixture responses and laptop prerequisites → Rishav |
| 1 — Stationary Vision | Integrate Spandan's artifact through a laptop detector adapter. Verify reference-image labels/boxes and real webcam output. Preserve frame capture timestamps. | Real stationary detections and H1 receiver check → Spandan |
| 2 — Hard Scan + Memory | Implement the exact scan sample/persistence rules, non-overlapping zones, snapshot transaction, SQLite schema, camera profile invalidation, local clear and query semantics. Build FastAPI endpoints and fault fixtures. | Scan/query/API service with AC-03/AC-04 evidence → Rishav |
| 3 — Sensor Node | Review common API/sensor error semantics with Rishav; continue independent laptop work rather than waiting for hardware. | Clear distinction between missing object and unavailable service |
| 4 — Android Camera AI | Implement the Android client against fixtures without touching Samik's inference path. Use agreed shared app types and ask Rishav to integrate dependencies. | Cancellable memory client skeleton → Rishav |
| 5 — USB Integration | Verify network requests never block camera/risk or USB handling; help review producer cancellation boundaries. | Client responsiveness/failure evidence → Rishav |
| 6 — Fusion + Risk | Review AC-10 evidence as assigned; check memory zones never become invented route turns or risk inputs. | Review findings → Rishav |
| 7 — Voice UX | Provide correct last-seen, ambiguous, historical-only and failure results. Rishav handles all speech and user confirmations. Test late client callbacks after Stop. | Validated app-facing results → Rishav |
| 8 — Integration + Search | Connect actual laptop and phone on the permitted network. Verify refresh-before-Guide, selection invalidation, explicit stale fallback, arrival and target-class transfer. | H4 complete; lead AC-14 integrated evidence |
| 9 — Acceptance | Lead AC-03/AC-04/AC-14, support other client/lifecycle tests and review assigned cross-system evidence. | Run commands, schema/API test results and physical scan/full-flow evidence → Rishav |

## Memory Rules to Preserve

- Ten distinct scan frames in the 2-second schedule; accept an instance at confidence >= 0.60 in at least 6/10 frames with stable zone and matching. Finish or fail within the PRD deadline; do not duplicate frames to fill the sample count.
- Store one scan snapshot transactionally. Use the PRD schema unchanged unless a reviewed PRD change authorizes otherwise. Do not announce success before commit.
- Cancelled scans, profile changes and Clear history must invalidate pending commits. Failed writes roll back. A late scan cannot restore deleted history.
- Always say last seen; query/refresh never updates observation timestamps. Age > 60 seconds is stale; same-class instances remain ambiguous without selection. A later empty scan does not prove removal.
- Do not claim physical identity or object ownership. Old camera profiles cannot supply usable targets. Operator-marked calibration and clock prerequisites must remain explicit.

## API and Client Checklist

Implement only the PRD v1 health and locate endpoints with required authentication/network limits. Keep secrets out of source/logs and use parameterized SQL.

On Android enforce the 2-second total timeout, one active request, 64 KiB response cap, exact status/candidate/type/range/time validation, and current request/session identity. Network errors are not not_found. Reject mismatched class and inconsistent age; never silently truncate ambiguous candidates. Track age conservatively with monotonic elapsed time rather than phone wall-clock subtraction.

Rishav owns when refresh runs and the confirmation UI. Return typed results that allow him to invalidate missing/deleted selections, display stale age, and offer the PRD's explicit offline fallback. Do not mutate the active app target from a late client callback.

## Handoff H4

Give Rishav service/client source locations, setup/run/test commands, fixture cases, local endpoint configuration instructions without the bearer token, real response samples with non-sensitive demo data, and all known limitations. He must verify actual phone queries and cancelled/stale/ambiguous behavior. Fixtures alone cannot close full integration.

## Acceptance You Lead

- **AC-03:** Twenty Hard Scans, required class/zone/count correctness and timing, plus invalid/incomplete no-write behavior.
- **AC-04:** All memory/API fault and boundary fixtures, including age 60/61, clear/profile changes during scan, rollback, invalid responses and late callbacks.
- **AC-14:** Five full memory flows and five model-switch/Stop cycles with Rishav, Samik and Spandan. Rohan reviews the evidence.

## Current Handoff Record

Status: planned. Source, artifacts and evidence: not yet recorded. Primary receiver: Rishav; model producer: Spandan. Next action: Phase 0 fixture/API contract and laptop capture prerequisite checks. Use the shared status format for subsequent handoffs.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
