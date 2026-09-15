# NaviSense AI — Team Communication Log

This file is the append-only record of all team chats, progress updates, handoffs, reviews, blockers, and architectural decisions on the `communication` branch, governed by [docs/AGENTS.md](docs/AGENTS.md).

## Communication branch — mandatory

All work/progress/handoff/review/decision messages belong in communication's append-only team-chat.md. Only chat/decision-log changes go there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized.

---

### Entry Log

```text
Entry ID: KICKOFF-2026-09-14-001 / 2026-09-14T20:45:00+05:30 / T+00:00
Author and type: Rishav | REVIEW
Phase / step / S-instance / H-contract: Phase 0 / Entry Review
Message and requested action:
Kickoff Phase 0 compatibility check. Verified frozen file hashes for docs/README.md and docs/guidance.md under AGENTS.md. Team ownership confirmed: Spandan (Models), Subham (Laptop Memory/API), Rohan (Hardware/Firmware/USB), Samik (Android Camera/AI/Search), Rishav (Risk/Voice/Integration).
Entry status: READY for Phase 0 setup and smoke reviews.
Source revision and evidence reference: main branch root
Recipient(s): Spandan, Subham, Rohan, Samik
For response: referenced entry ID and ACK
```

```text
Entry ID: RISHAV-2026-09-14-002 / 2026-09-14T21:45:00+05:30 / T+01:00
Author and type: Rishav | HANDOFF
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / S01 / H6
Message and requested action:
Delivering S01: Android app shell v0 foundation, package roots under dev.navisense, session_generation authority, and shared producer contracts interface. All producers inspect and build against these types.
Source revision and evidence reference: android/app/src/main/java/dev/navisense/
Recipient(s): Spandan, Subham, Samik, Rohan
For response: referenced entry ID and ACK
```

```text
Entry ID: SAMIK-2026-09-14-003 / 2026-09-14T22:15:00+05:30 / T+01:30
Author and type: Samik | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / S01
Message and requested action:
ACK RISHAV-2026-09-14-002. Confirmed dev.navisense package structure and session_generation contract. Setting up Android camera, inference, tracking, and search modules.
Source revision and evidence reference: commit on main
Recipient(s): Rishav
For response: referenced entry ID and ACK
```

```text
Entry ID: SAMIK-2026-09-14-004 / 2026-09-14T22:30:00+05:30 / T+01:45
Author and type: Samik | HANDOFF
Phase / step / S-instance / H-contract: Phase 0 / Step 01 / H2, H5
Message and requested action:
Delivered mobile perception and target search contracts and foundational pipeline:
1. dev.navisense.contracts.PerceptionContracts (Handoff H2): NormalizedRect with IoU, DetectedObject, FrameQualityStatus, MobilePerceptionEvent with monotonic timestamps and sessionGeneration.
2. dev.navisense.contracts.SearchContracts (Handoff H5): TargetDirection (LEFT/CENTER/RIGHT), SearchStatus, SearchEvent.
3. dev.navisense.camera.CoordinateTransformer: letterbox undoing and upright 0/90/180/270 deg rotation.
4. dev.navisense.camera.FrameQualityChecker: PRD §17.2 dark/covered (mean < 10) and featureless (stdDev < 5) rejection.
5. dev.navisense.tracking.VisualTracker: 1-to-1 greedy IoU matching (>= 0.30), 500 ms expiry, and 0.5s area growth tracking (>= 25%).
6. dev.navisense.search.TargetSearchEngine: PRD §20 3-of-5 confirmation within 1.0s, direction mapping, 15s timeout, and session generation invalidation.
7. Verification: 13/13 automated unit tests PASSED via .\gradlew.bat testDebugUnitTest.
Requested action: Rishav review contracts H2 & H5; Spandan prepare S02 smoke model exports for on-phone loading.
Source revision and evidence reference: commit c3a3a49 on main; PerceptionUnitTests.kt
Recipient(s): Rishav, Spandan
For response: referenced entry ID and VERIFIED / RETURNED
```

```text
Entry ID: SAMIK-2026-09-15-005 / 2026-09-15T09:30:00+05:30 / T+02:00
Author and type: Samik | CHAT
Phase / step / S-instance / H-contract: Phase 0 / S02 prerequisite
Message and requested action:
GitHub repository initialized at https://github.com/SamikSwarupBiswal/NaviSense-AI with main and communication branches. Standing by for Spandan's S02 smoke models (Locate + Mobility YOLO in TFLite format) to execute on-phone load and latency check.
Source revision and evidence reference: https://github.com/SamikSwarupBiswal/NaviSense-AI
Recipient(s): Team (Spandan, Subham, Rohan, Rishav)
```

```text
Entry ID: SAMIK-2026-09-15-006 / 2026-09-15T10:02:00+05:30 / T+02:15
Author and type: Samik | PROGRESS
Phase / step / S-instance / H-contract: Phase 0 / Team setup
Message and requested action:
Invited all four teammates with write access to https://github.com/SamikSwarupBiswal/NaviSense-AI:
- rishav-bits (Rishav) -> Invitation sent
- JeansGit77 (Rohan) -> Invitation sent
- spandanjit-ai (Spandan) -> Invitation sent
- shubhusden (Subham) -> Invitation sent
Please accept repository invitations to collaborate on main and communication branches.
Source revision and evidence reference: https://github.com/SamikSwarupBiswal/NaviSense-AI/invitations
Recipient(s): rishav-bits, JeansGit77, spandanjit-ai, shubhusden
```

