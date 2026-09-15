# Spandan — Models and Dataset Guidance

Authority: [PRD v3.2](../README.md). Coordination: [shared guidance](../guidance.md).

Detailed 24-hour execution plan: [numbered implementation steps](implementation-plan.md). Follow [AGENTS](../AGENTS.md) for frozen boundaries, communication, continuation and mandatory phase reviews.

**Ownership: assigned by the user. Implementation status: planned, not completed or accepted.** No trained model, quality result, or completed Spandan work is asserted.

## Your Responsibility

Own the Locate dataset/training and both model families' reproducible artifacts. Locate must work on the stationary laptop and on Android search viewpoints. Mobility starts from pretrained weights; fine-tune only for demonstrated failures. You own model quality and export metadata, while Subham owns the laptop adapter and Samik owns the Android inference adapter.

Read PRD Sections 8–9, 10–11, 17, 20, 29–31, and 38. Own model/dataset manifests, export/training/evaluation scripts and model artifact locations. Coordinate any runtime input/output change with both receivers before replacing an artifact.

## Phase-by-Phase Work

| Phase | Your actions | Deliverable / receiver |
|---|---|---|
| 0 — Compatibility | Propose a small explicit demo class set; keys/wallet are examples, not a fixed full vocabulary. Give Samik compatible Locate/Mobility smoke artifacts and metadata. Agree aliases with Subham. Resolve actual phone export compatibility before large training runs. | Class/alias proposal, artifact hashes, reference images and export metadata → Subham, Samik; decisions → Rishav |
| 1 — Stationary Vision | Collect laptop and phone viewpoints with team help, label consistently, split by capture session/layout, train Locate from suitable starting weights, evaluate per class. Retain pretrained Mobility unless measured failures justify fine-tuning. | Dataset/split manifest, training configuration/results, Locate candidate, Mobility candidate → Subham, Samik |
| 2 — Hard Scan + Memory | Review real misses/false positives from Subham. Distinguish detector errors from zone/persistence bugs. Improve data only against documented failures; do not alter memory rules to hide them. | Labelled failure cases and justified new model candidate if needed → Subham |
| 3 — Sensor Node | Supply representative known/unknown-class obstacle examples; do not make class recognition a prerequisite for Rohan's distance alerts. | Demo obstacle list → Rohan, Rishav |
| 4 — Android Camera AI | Compare decoded labels/boxes on shared reference images with Samik. Verify mobile export accuracy independently of laptop accuracy. Track hash/version differences after export or quantization. | Reviewed Android model outputs and AC-02 mobile evidence → Samik, Rishav |
| 5 — USB Integration | Help select an obstacle that produces a usable echo without a supported visual class for later fusion tests. | Clearly identified unlabeled test cases → Rohan, Rishav |
| 6 — Fusion + Risk | Diagnose false/missed visual detections with Samik; Rishav owns risk policy. Do not change confidence/risk thresholds without recorded review and affected retests. | Failure analysis tied to model hash → Samik, Rishav |
| 7 — Voice UX | Check spoken names use the agreed class aliases and do not imply ownership. Avoid adding an LLM to the baseline flow. | Naming issues and expected labels → Rishav |
| 8 — Integration + Search | Evaluate final-search failure cases at recorded phone ranges/viewpoints. Supply corrected artifacts only with updated manifests and receiver checks. | Final tested model candidates → Subham, Samik |
| 9 — Acceptance | Lead AC-02 and support AC-07/AC-13/AC-14. Review the evidence assigned to you in the shared guide. Retain all failed evaluations and retests. | Per-device/per-class results, limitations and final artifact hashes → Rishav |

## First Deliverables

1. A class/alias list and capture checklist covering both laptop and phone search images.
2. Smoke artifacts that let Subham and Samik implement adapters before training is complete, explicitly labelled as smoke-only when unqualified.
3. A split manifest preventing adjacent-frame leakage across training, validation and test.
4. A reproducible export/evaluation command with prerequisites and artifact hashes.

Do not claim training completion without actual training output and saved artifacts. A downloaded pretrained detector is not a custom-trained Locate model.

## Handoff H1 — Required Contents

- Artifact path/hash, model version and task; final supported classes and numeric class order.
- Input shape/type, color order, normalization/quantization, resize/letterbox details, output decoding and NMS settings.
- Training data/split provenance and commands, laptop/mobile evaluation results, reference inputs and expected outputs.
- Known unsupported objects, lighting/range limitations, and which tests have not run.

Subham must verify actual laptop inference; Samik must verify actual phone loading and decoded detections. Their receiver confirmation is required before calling the artifact integrated.

## Acceptance You Lead

**AC-02:** At least 50 labelled instances per chosen class per device and 20 negative frames per device; evaluate laptop and Android separately at PRD settings. Required per-class precision is at least 90% and recall at least 85%. Report raw counts, misses and false positives; do not substitute aggregate mAP for these gates.

Support search/obstacle tests with the exact evaluated model versions. Do not silently reuse previous metrics after changing weights, export, preprocessing, labels or resolution.

## Boundaries and Blockers

Do not own the API, database, Android lifecycle, USB parser or risk engine. If the phone export fails, work with Samik rather than creating a phone-to-laptop inference dependency. If data is insufficient, record which classes/scenes block acceptance; keep a smaller agreed demo scope instead of claiming unsupported classes work.

## Current Handoff Record

Status: planned. Artifacts/evidence: not yet recorded. Receivers: Subham and Samik. Next action: complete Phase 0 class/artifact proposal and phone export smoke check. Record subsequent handoffs using the shared guide's status format.

## Communication branch — mandatory

Use the dedicated `communication` Git branch and its append-only `team-chat.md` for work chats, progress, blockers, handoff delivery/acknowledgement, review responses and decisions. Only chat/decision-log changes belong there; code, models, datasets, firmware and implementation/status documents stay on their work branches. Link evidence and source revisions in messages. Chat is not test evidence or authority to alter frozen files. Follow AGENTS.md for synchronization and conflict handling. Record actual times; never claim a handoff accepted without the receiver's response. The branch/log must be set up when Git is initialized; this documentation does not claim they already exist.
