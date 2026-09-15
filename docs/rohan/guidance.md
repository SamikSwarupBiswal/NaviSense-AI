# Rohan — Hardware, Firmware and USB Guidance

Authority: [PRD v3.2](../README.md). Coordination: [shared guidance](../guidance.md).

Detailed 24-hour execution plan: [numbered implementation steps](implementation-plan.md). Follow [AGENTS](../../AGENTS.md) for frozen boundaries, communication, continuation and mandatory phase reviews.

**Ownership: assigned by the user. Implementation status: planned, not completed or accepted.** Wiring, voltage/power checks, calibration and actual phone USB operation are not established.

## Your Responsibility

Own HC-SR04/ESP32-S3 assembly, mount and electrical verification, firmware, USB serial contract, Android USB adapter, sensor health/recovery and measurements. The ESP32 only measures/reports; Rishav's Android engine decides risk and speech. Your ownership includes the phone-side parser so there is no gap between a working serial terminal and a working Android sensor feed.

Read PRD Sections 7, 13–19, 25–30, and 38. Own firmware, Android USB package/tests, and hardware measurement scripts/configuration records. Ask Rishav to integrate manifest/USB dependency changes.

## Phase-by-Phase Work

| Phase | Your actions | Deliverable / receiver |
|---|---|---|
| 0 — Compatibility | Record exact board/phone/USB interface/GPIOs/supply/mount. Verify ECHO level handling and actual host power/data. Prove basic streaming to the selected phone with Rishav/Samik before assuming compatibility. | Hardware prerequisite record, valid/invalid stream samples and blockers → Rishav |
| 1 — Stationary Vision | Supply supported mounting/coverage limits to Spandan and Samik so collection/demo objects fit the intended envelope. Start firmware/parser fixtures independently of model training. | Coverage and test-object constraints → team |
| 2 — Hard Scan + Memory | Continue firmware/parser work; no dependency on laptop database. Review shared failure/status contracts rather than adding a memory link to the sensor. | Parser replay inputs → Rishav |
| 3 — Sensor Node | Assemble, measure and write exact PRD acquisition protocol. Verify no echo/out-of-range invalidity and no firmware backlog. Run sensor bench measurements. | Firmware revision, serial records, electrical/mount details and AC-08 evidence → Rishav |
| 4 — Android Camera AI | Coordinate fixed alignment/geometry with Samik. Implement USB connection/session and parser beneath Rishav's app shell. | H3 sensor event adapter → Rishav |
| 5 — USB Integration | Finish permission/attach/detach, bounded line parsing, sequence/uptime logic, receipt timestamps, recovery and delay checks. Test on the real phone, not only a serial terminal. | Live sensor events and AC-09 evidence → Rishav |
| 6 — Fusion + Risk | Supply close/invalid/stale/backlog/reset/duplicate fixtures and live observations. Ensure critical close records are preserved before coalescing newer values. Rishav decides STOP. | Transport half of AC-10; H3 receiver verification → Rishav |
| 7 — Voice UX | Lead physical-obstacle-to-audible-STOP measurements with Rishav. Include unrecognized objects and missing packets; do not measure only from packet receipt. | AC-06 recordings/timestamps/results → Rishav; Subham reviews |
| 8 — Integration + Search | Verify USB STOP remains active during stationary search/Found and sensor loss does not require laptop access. Review AC-14 integration evidence. | Real hardware/search interaction evidence → Rishav, Subham |
| 9 — Acceptance | Lead AC-06/AC-08/AC-09, support supervised walkthroughs and failure cases, and review assigned runtime/full-flow evidence. | Qualified assembly configuration and remaining limits → Rishav |

## Firmware Contract

Use the PRD's one format at 10 Hz; no alternate short parser:

```text
V=1,SEQ=1021,UP_MS=102100,DIST_CM=83,VALID=1
V=1,SEQ=1022,UP_MS=102200,DIST_CM=-1,VALID=0
```

SEQ increments per acquisition, including invalid/unsent measurements. UP_MS records measurement completion. VALID=1 requires the PRD integer range; invalid/no echo uses -1/0, never a fabricated far distance. Bound pulse measurement and avoid unsent backlogs. Do not choose GPIOs or assume power wiring from the generic board name; verify the actual assembly as required by Section 7.

## Android Sensor Adapter

- Parse partial LF/CRLF lines with the 128-byte limit; discard oversize records through newline. Reject invalid schema/types/ranges/versions and preserve sequence loss diagnostics.
- Own connection/session identity and monotonic receipt timing. Enforce <= 300 ms receipt-age validity, immediate invalidation on newer invalid data, and PRD sequence/uptime wrap/reset handling.
- Implement three-valid-record recovery with spacing/gap requirements. A first fresh close reading may still be forwarded for immediate STOP; it cannot establish clearance/recovery.
- Inspect close records before coalescing the latest distance. Report health changes independently from distance values; do not reuse old values across connections.
- Rishav owns the 50 ms app watchdog and risk decisions. Give him callable health evaluation/current state so staleness can be detected even when no new bytes arrive.
- Receipt age is not guaranteed physical measurement age. Follow the PRD delay checks and disclose the one-way protocol's limits; real event-to-audio testing is mandatory.
- Permission denial, detach and corrupt input must not crash or block the app. Never call TTS from the sensor module or silently continue after session cancellation.

## Handoff H3

Provide firmware/source revision, exact hardware setup and verified connection instructions, serial samples, parser replay/test commands, Android event types, measurements and known limits. Give Rishav actual live distance/health events and demonstrate close-without-YOLO, detach/staleness and recovery. Include artifact/evidence paths, not just “sensor working.”

## Acceptance You Lead

- **AC-08:** Twenty readings at each of 30/50/75/100/150/200 cm, >= 90% valid per distance, median absolute error <= 5 cm; retain spread and invalid counts.
- **AC-09:** Ten-minute stable real USB run, delivery/loss measures, then five detach/reattach cycles with required detection/notice/recovery timing. Parser fixtures do not close this gate.
- **AC-06:** Twenty bench presentations, ten at 30 cm and ten at 40 cm. All must yield STOP; decision <= 100 ms after the first accepted close packet, audible onset <= 500 ms after it, and physical entry-to-audio <= 750 ms. Missing packets/STOP are failures. Rishav supplies risk/TTS instrumentation and Samik ensures inference load is represented.

Use stationary bench tests for critical distances. During supervised walking, help maintain the defined speed/mount/obstacle envelope; do not claim coverage of drop-offs, every material or every height.

## Current Handoff Record

Status: planned. Hardware choices, firmware, measurements and evidence: not yet recorded. Primary receiver: Rishav; mounting collaborator: Samik. Next action: Phase 0 actual electrical/USB compatibility proof. Use the shared status format for later handoffs.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
