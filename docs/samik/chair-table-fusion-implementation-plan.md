# Chair/table accuracy and YOLO–ESP32 fusion implementation plan

Prepared by Codex, 2026-09-16, at source `b49e6c33e4065ab8061809e1a1f9840783bd0bef`. Status: **planning and source/dataset-metadata inspection only**. No training, model replacement, code fix, device test or receiver acceptance is claimed. Actual event T+ remains unverified.

Authority: [AGENTS](../../AGENTS.md), [frozen PRD](../README.md), [frozen guidance](../guidance.md), [current state](../implementation-state.md), [master plan](../implementation-plan.md), [Samik's plan](implementation-plan.md), and [Spandan's plan](../spandan/implementation-plan.md). This plan complements the GPS distance plan; geographic routing is a separate defect.

## 1. Decisions and expected result

Yes: evaluate the supplied classroom dataset for a **two-class Mobility candidate**, fine-tuned from pretrained detection weights, with canonical labels `0: table`, `1: chair`. Keep Locate for keys/wallet search separately. A two-class model intentionally cannot identify people, couches or arbitrary obstacles; ultrasonic generic warnings must remain active for those objects. Do not claim broader visual coverage or improved accuracy merely because the class count is smaller.

Yes: combine YOLO and ESP32 in a deterministic on-phone engine. The existing `RiskEngine` already does this, so repair and test it rather than building a duplicate app/service or using an LLM. Keep two questions separate: **how urgent is the hazard?** and **what visual label is sufficiently supported?** Sensor proximity must never wait for a label.

Desired sequence: sensor detects hazard → immediate generic warning if identity is unknown → fresh, persistent, unambiguous camera evidence arrives → one named refinement at the same severity. Example: “Slow down. Obstacle ahead.” followed by “Slow down. Chair ahead.” No assertion that the ultrasonic distance is the chair's exact distance.

## 2. Dataset findings and remaining uncertainty

Source: [Objects in the Classroom](https://www.kaggle.com/datasets/aryakrisnaputra/objects-in-the-classroom), handle `aryakrisnaputra/objects-in-the-classroom`.

Read-only Kaggle metadata inspection reported 20 categories, 185,396,026 bytes, last update 2025-03-24, and license metadata MIT. This is publisher metadata, not a completed provenance/license audit. The first file-list page exposes `objects in the classroom/data.yaml` and `data/images/test/...` files. Some filenames share a base with different `.rf.` suffixes: investigate augmented/near-duplicate families across splits; filename patterns alone do not prove leakage.

The actual 359-byte YAML was fetched through Kaggle's public single-file API:

```yaml
path: '/content/yolo_20/data'
train: images/train
val: images/val
test: images/test
nc: 20
names:
  0: table
  1: chair
  # 18 other classes omitted here, not removed from the source dataset
```

This confirms class IDs and intended splits. It does **not** establish chair/table instance counts, bounding-box completeness, class balance, source diversity, split integrity or phone-view accuracy. Full image/annotation audit is a prerequisite. Replace the Colab-only absolute path in the derived configuration, not in the original dataset.

During implementation, use the supplied download approach, record the returned versioned directory and checksums, and pin that resolved version for reproducibility:

```python
import kagglehub
path = kagglehub.dataset_download("aryakrisnaputra/objects-in-the-classroom")
print(path)
```

Do not repeatedly download “latest” during reproducibility runs. Keep raw download immutable and outside ordinary source commits. Inspect the actual license/provenance files before distributing derived images. Downloading does not count as training.

## 3. Current source findings

Source root below: `android/app/src/main/java/dev/navisense/`.

| Component | Verified observation | Required treatment |
|---|---|---|
| `app/MainActivity.kt` | Both Mobility and Locate select the combined six-class TFLite model | Integrate a qualified two-class Mobility candidate independently; retain Locate model/decoder for search |
| `navigation/RiskEngine.kt` | Has separate sensor/vision risks and takes their maximum | Preserve this architecture; do not average severity with detection confidence |
| `associateLabel` | Camera path requires one persistent corridor track and <=200 ms capture/receipt separation | Make this the common association rule for all event orders |
| `processSensorEvent` | Copies `visualObstacleLabel` using camera delivery age up to 1 s | This bypasses the stricter association path; eliminate the shortcut |
| `app/SessionCoordinator.kt` | Uses `associatedObjectLabel ?: visualObstacleLabel` for combined warnings | Avoid using an unrelated visual label to name a sensor-led hazard |
| `voice/SpeechArbiter.kt` | Same-priority refinement uses string tests such as `contains("Obstacle")` | Replace with structured hazard/refinement identity, freshness and session checks |
| `navigation/FusionVisionTrackStore.kt` | Current persistence is two frames/1.5 s at confidence 0.30 | Reconcile with frozen risk contract; do not silently carry experimental settings into qualification |
| `RiskEngine.kt` | Current sensor bands are STOP 2–100 cm, SLOW 101–150 cm; release values also differ from PRD | Record policy drift explicitly; default remediation to frozen policy unless explicit scoped authorization establishes another baseline |
| `datasets/ingest_indoor_obstacles.py` | Existing importer remaps chair/table to 1/2 and supports sofa-as-table | Do not reuse those IDs or sofa mapping for this two-class candidate |

These findings explain weaknesses, not the exact physical cause of every generic utterance. A generic warning may be correct because there is no confident visual identity. Reproduce with event traces before attributing it solely to TTS or fusion.

## 4. Terminology and contracts

| Term | Meaning |
|---|---|
| Transfer learning | Fine-tune pretrained detector weights on the new data rather than learning visual features from scratch |
| Domain shift | Difference between dataset pictures and actual phone/mount, lighting, motion, furniture and room views |
| Precision / recall | Correct detections divided by all detections / correctly detected objects divided by labelled objects |
| mAP50 / mAP50–95 | Detection ranking metrics across classes/IoU thresholds; useful comparisons, not substitutes for raw per-class gate counts |
| IoU | Intersection-over-union of boxes, used for matching and evaluation |
| Hard negative | A verified scene with no target class that resembles it, such as shelves mistaken for tables |
| Leakage | Related images or augmented copies appearing in both training and evaluation, inflating apparent quality |
| Export parity | Agreement between training-runtime and deployed-runtime labels, confidence and geometry |
| Persistent track | Same-class detections linked over distinct fresh frames; predicted display boxes are not safety evidence |
| Temporal association | Camera capture and sensor receipt close enough in the same monotonic clock domain to permit cautious naming |
| Hazard episode | Proposed bounded identity for one continuing risk situation; independent of track-ID churn |
| Semantic refinement | Adding a newly supported class name without lowering risk or delaying the original alert |
| Provenance | Model hash, event/track IDs, timestamps and association reason explaining a spoken label |

Extend existing typed contracts minimally. Suggested decision fields: session generation, hazard episode ID, sensor severity, vision severity, combined severity, visual label evidence, associated label evidence, association status/reason, evidence expiry, and decision time. A visual label is distinct from a sensor-associated label.

Suggested speech metadata: hazard ID, severity, label or generic identity, label revision, evidence expiry, session generation. Treat human-readable phrase as output, not the source of identity. Receiver-compatible changes belong to Rishav after Samik supplies the exact request.

## 5. Workstream A — Two-class model accuracy

### A0. Entry review and unchanged baseline

Spandan leads training; Samik reviews phone/runtime inputs. Synchronize branches, read current messages, record source revision and frozen hashes, confirm compute/storage and actual phone slot. Record PENDING/READY-FIXTURES/READY based on evidence, without impersonating a peer ACK. Capture baseline results using current combined model on the same held-out phone set planned for the candidate. Store current hashes for rollback.

Exit: baseline failures and evaluation protocol exist; available GPU/device is inspected, not assumed. Paid compute provisioning is outside this plan's execution authorization.

### A1. Audit raw data before filtering

1. Inventory images, labels, split names, resolutions and class/object counts. Confirm bounding-box annotation format by reading labels, not just YAML.
2. Validate each image/label pair, class ID, finite normalized coordinates, positive size and box bounds. Quarantine corrupt/missing/ambiguous annotations; distinguish genuine negatives from unlabeled positives.
3. Visually inspect rendered annotations for at least 50 examples per target class, or all if fewer, spread across splits and scene families. Record incomplete boxes, partial furniture, clutter and definitions of “table.”
4. Audit exact and perceptual duplicates and common `.rf.` source families across splits. Preserve an existing test set only if independent; otherwise regroup by source scene before any training. Keep all related copies in one split.
5. Report chairs/tables per split, negatives, exclusions, duplicate groups and source diversity. A small test set or mostly catalog images cannot establish walking performance.

Exit: deterministic audit report and split manifest; unusable labels are corrected or excluded with reasons.

### A2. Build the two-class derived dataset

1. Keep source class `0 table` as output `0 table`; keep `1 chair` as `1 chair`. Drop other annotation rows, not whole images containing mixed classes.
2. Retain audited negative scenes with empty target labels. Check that chairs/tables visible in such images really are absent or annotated; otherwise correct them before treating the frame as negative.
3. Do not map couch, cabinet or bookshelf to table. Write an explicit semantic policy for desk/table variants and attached desk-chair furniture.
4. Store derived data under a new candidate location, e.g. `datasets/mobility_chair_table/`, with `nc: 2` and exact ordered names. Keep existing datasets and models intact.
5. Prefer source/session-grouped train/validation/test partitions; use approximately 70/15/15 only when counts support independent evaluation. Record actual counts rather than forcing percentages at the expense of test quality.
6. Store checksums, exclusion/remapping manifest and resolved dataset version. Unit-check remapping with mixed-class, negative, invalid-label and duplicate fixtures.

YOLO's annotation format is one zero-based class ID plus normalized box center/size per row. See [official dataset format](https://docs.ultralytics.com/datasets/detect/). The converter must actually remove/remap annotations; selecting runtime classes alone does not produce a verified two-class dataset/head.

### A3. Add representative phone data

Samik captures with Spandan's annotation guide using the actual mounted phone: chair/table fronts, sides, legs, partial views, overlapping objects, dim/bright light, reflective furniture, motion blur, empty corridors and confusing non-target furniture. Include stationary and supervised walking views; no unsupervised collision trials.

Collect independent rooms/sessions and keep the final phone test scenes unseen during training/tuning. Initial collection target: 300–600 diverse annotated phone frames if practical, selected from separate encounters rather than adjacent-frame bursts. This is a planning estimate, not a required gate or a claim that such data exists. Reserve at least 50 labelled instances per target class plus 50 audited negative frames in an independent phone-view evaluation set. Record shortages and uncertainty.

Exit: enough representative evidence to compare models; do not promise that a classroom-only dataset generalizes to the user's furniture.

### A4. Fine-tune a bounded candidate

1. Reuse the existing supported YOLOv8n family initially; record weights hash, installed library versions, seed and compute. Avoid changing model family and decoder simultaneously.
2. Start with 640-pixel input, batch selected from measured memory, maximum 80 epochs, early stopping patience 15, seed 42 and moderate realistic augmentation. These are proposed starting settings, tuned on validation only.
3. Measure the first few epochs before estimating runtime. Stop invalid/overfitting runs and retain failure logs; do not run speculative large sweeps.
4. Compare current combined model, unmodified pretrained chair/dining-table baseline, and new two-class candidate on the same independent test images. Declare the pretrained dining-table versus custom-table mapping and its limits.
5. Tune confidence/NMS on validation. Report a precision–recall curve and the deployment operating point; lowering confidence is a trade-off, not an accuracy fix.
6. Inspect per-class false positives/negatives by lighting, object scale, occlusion and viewpoint. Add failure data only to a new training/validation revision, keeping the test set protected.

Illustrative command after the verified configuration exists, not executed by this plan:

```text
yolo detect train model=yolov8n.pt data=datasets/mobility_chair_table/data.yaml imgsz=640 epochs=80 patience=15 batch=8 seed=42 project=runs/mobility_chair_table name=candidate_01
```

Adjust batch to actual hardware and paths to the inspected workspace. Check options against the pinned installed version and [training documentation](https://docs.ultralytics.com/modes/train/). Candidate completion requires saved checkpoints and actual logs, not a command alone.

### A5. Export and prove Android parity

1. Export through the repository's known compatible TFLite toolchain. Pin dependencies; do not assume the latest exporter matches the existing Android runtime.
2. Evaluate FP32/FP16 compatibility first. INT8 is deferred unless measured need justifies representative calibration and a separate accuracy evaluation.
3. Record input shape/dtype, RGB order, scaling, letterbox padding, output layout, NMS placement and exact class order. Do not hardcode the old six-class channel count: inspect the real two-class tensor and configure decoding accordingly.
4. Compare desktop and Android results on identical labelled inputs, including orientation/aspect-ratio fixtures. Check rotated coordinates and per-class IDs before attributing misses to training.
5. Benchmark full capture-to-result latency, p50/p95/p99, fresh-result frequency, rejected-frame fraction, memory and thermal behavior in a 10-minute mounted-phone run. Record actual GPU delegate activation or failure; “GPU selected” is not a speed measurement.
6. Preserve the 500 ms capture-freshness bound and sufficient fresh-frame cadence for the frozen persistence policy. A model that cannot satisfy these conditions is unqualified; do not loosen safety policy to hide latency.
7. Package artifact under `models/mobility/` with matching Android asset, checksum and metadata. MainActivity changes select it for MOBILITY only, through Rishav's integration. Locate/search remains independently configured.

Exit: both classes meet agreed quality and mobile runtime gates; no “15–30 FPS” or “zero frame drops” claim without measurements.

### A6. Candidate selection and quality gate

Proposed project-specific release target: per-class precision >=90% and recall >=85% at IoU >=0.5 on the independent phone set, with raw TP/FP/FN counts and confidence intervals or explicit sample-size limitations. These are proposed Mobility qualification criteria, not a claim that AC-02's Locate gate is now passed. Candidate must improve documented chair/table misses without unacceptable false positives versus baseline and pass runtime freshness checks.

If the candidate fails, retain the previous known artifact and report limitations. A two-class deployment narrows named visual coverage; it does not establish complete obstacle detection. Rerun affected PRD gates separately, especially mobile runtime, path degradation, supervised encounters and cancellation.

## 6. Workstream B — Repair the existing fusion and speech engine

Samik owns fusion under the recorded user assignment in implementation-state. Rohan owns sensor transport; Rishav owns app/voice integration; Spandan owns model artifacts. This plan does not move those responsibilities.

### B0. Reproduce with an explanatory trace

Run chair-only, table-only, mixed-object and no-recognized-object scenes with/without ESP32. For each utterance record bounded diagnostics: session/connection IDs, sensor sequence/receipt/health, frame capture/delivery/quality, model hash, accepted detections, persistent tracks, association decision, risk sources, speech submission and rejection reason. Do not store continuous raw video or private room imagery in Git.

Classify each generic warning as `NO_DETECTION`, `LOW_CONFIDENCE`, `NOT_PERSISTENT`, `OUTSIDE_CORRIDOR`, `STALE`, `MISALIGNED`, `AMBIGUOUS`, `WRONG_SESSION`, or `SPEECH_SUPPRESSED`. First measure which category dominates. There is no blanket requirement to replace every generic warning with a class name.

### B1. Validate independent evidence and restore policy consistency

1. Continue using typed `SensorEvent`, `MobilePerceptionEvent` and `FusionInput` reducer. Check monotonic time, current mode/session, sensor connection, advancing sequence and frame IDs before applying evidence.
2. Sensor packets remain usable only within 300 ms; camera captures within 500 ms; track expiry 500 ms. Association requires <=200 ms camera-capture/sensor-receipt separation, exactly one persistent corridor candidate, and current valid evidence.
3. Centralize policy constants and reconciliation tests. Frozen sensor bands are <=50 cm STOP, >50–100 SLOW, >100–150 AWARENESS; releases are >65/>115/>165 cm held for one second. Current source differs. Restore contract consistency unless a verified explicit authorization for a different setting exists; never update frozen hashes to hide drift.
4. Frozen visual qualification is confidence >=0.40 in at least three of the latest five distinct processed frames within one second, same-class IoU >=0.30. Tune model candidate thresholds separately from safety qualification. Any proposal to change the contract is separately recorded and requires scoped authorization.
5. Retain UNKNOWN, independent holds, recovery and cancellation rules from the PRD. Sensor no-echo is not “far” and a missing detection is not a clear corridor.

Exit: replay boundaries agree with the actual authorized contract; experimental settings are not silently accepted.

### B2. Compute identity the same way after every event

1. Maintain a bounded snapshot/history of accepted sensor and vision evidence. Keep capture and receipt clocks distinct; one-way serial receipt is not proof of exact ultrasonic acquisition time.
2. On sensor, vision and watchdog events, expire evidence first, update that source, then run the same pure association function against the latest usable candidates.
3. Delete the sensor-event shortcut that copies `visualObstacleLabel` using delivery age. Remove stale cached labels on invalidation, model/geometry/session change and reconnect.
4. Return either named association with track/evidence IDs and expiry, or generic with an explicit rejection reason. Multiple tracks, even two chairs, fail the single-candidate association rule.
5. Keep independent visual awareness available when a persistent chair/table is seen but cannot be associated with the echo. Do not use that independent label to rename a sensor-led hazard automatically.
6. Preserve maximum-of-source severity. An unknown reflecting box still receives proximity STOP; recognition confidence never reduces or delays it.

Exit: equivalent fresh evidence yields the same identity regardless of sensor-first or camera-first arrival; watchdog expiration reliably removes a name.

### B3. Create structured, bounded speech refinement

1. Derive a hazard episode from existing session/risk state. Keep it stable across harmless tracker-ID churn; end/reset it on cancellation or properly released hazard. Do not conflate a new object with an old episode merely because its class name matches.
2. Emit immediate generic hazard speech when no association exists. Never wait one second for tracking before a close STOP.
3. When fresh association becomes available for the same episode/severity, emit one semantic refinement event. It is not a severity escalation; do not misuse `isEscalation` to bypass every cooldown.
4. Replace string matching in SpeechArbiter with typed metadata. Permit one same-severity generic-to-named refinement for that episode/revision, with expiry/session validation at playback. It may interrupt a generic phrase while retaining “STOP” or “Slow down” at the front.
5. A lower-priority visual description cannot preempt a sensor STOP. If association is ambiguous, retain the generic urgent warning; optional separate visual awareness waits for policy eligibility and is dropped if stale.
6. Do not submit a new utterance on every 10 Hz sensor event. Emit on hazard entry, severity escalation, valid label refinement and required cooldown reminders. Mark a refinement delivered only when speech is accepted; keep at most one current pending refinement and discard it on expiry/Stop.
7. On label loss, invalidate pending named speech immediately. Avoid named/generic/named chatter from jitter; no hysteresis may extend evidence past freshness expiry. Same-episode corrections require fresh qualification, not the old label cache.
8. Record explicit suppression reasons. Generic speech is not always a bug; an unexplained dropped valid refinement is.

Exit: ESP32-first replay produces immediate generic speech and at most one timely named update when eligible, without interrupt loops or delaying risk.

### B4. Expected behavior matrix

| Sensor evidence | Visual evidence | Output |
|---|---|---|
| Fresh 40 cm | None | Immediate “STOP.”; no inferred name |
| Fresh 80 cm | One qualified aligned chair | “Slow down. Chair ahead.” |
| Fresh 120 cm | One qualified aligned table | “Table ahead.” |
| Fresh 80 cm, camera initially missing | Chair becomes eligible later | Generic slow warning first, one named refinement while evidence is fresh |
| Fresh 40 cm | Chair and table both in corridor | Generic STOP; do not guess which produced echo |
| Fresh close return | Stale chair or chair outside corridor | Generic warning with diagnostic rejection reason |
| No usable sensor | Persistent chair in corridor | Visual awareness/vision risk as qualified; no metric distance, no clear-path claim |
| No usable sensor and no visual hazard | None | UNKNOWN under existing degraded policy |
| Sensor >150 cm | Qualified vision hazard | Vision warning remains; sensor cannot vote it away |
| Any | User Stop or old-session event | Cancel/silence or reject; never restart from late evidence |

### B5. Tests before device deployment

Pure reducer tests: exact risk boundaries; stale/future/duplicate events; all event-order permutations with fixed evidence; exactly-one versus multiple tracks; association at 199/200/201 ms; evidence age at 299/300/301 and 499/500/501 ms; class changes; track churn; geometry/model changes; sensor reconnect; hold/release and UNKNOWN; no effect from unrelated labels.

Speech tests with fake clock/player: generic accepted before label; generic still speaking; generic already completed; same-priority named refinement; stronger active hazard; cooldown; rejected-then-valid delivery; evidence expires while pending; repeated sensor packets; name changes; cancellation and stale generation. Assert zero duplicate refinement loops and no lower-priority suppression of STOP.

Integration tests: MainActivity selects independent models; correct two-class decoder metadata; Locate still finds keys/wallet; Search mode does not run Mobility risk on Locate output; stationary search retains ultrasonic STOP; camera failure/timeout preserves required pause semantics.

## 7. Physical verification and acceptance evidence

Perform stationary sensor/voice bench tests first, then supervised mounted-phone encounters. Reserve the actual phone/ESP32 rather than interrupting another member's tests. Record exact APK/model/firmware hashes and mount alignment.

| Check | Proposed sample / pass evidence |
|---|---|
| Model comparison | Independent >=50 labelled instances per chair/table plus >=50 negative frames; per-class TP/FP/FN and scenario breakdown |
| Runtime | 10-minute run, actual delegate and p50/p95/p99 full-pipeline latency, fresh-result cadence, frame rejections and thermal behavior |
| Eligible named association | Ten controlled chair and ten table trials; every suppressed/misnamed case explained; qualify timing and sample limits |
| Ambiguous/unknown obstacles | Ten trials including two objects, unsupported object and outside-corridor object; zero wrong echo identities |
| Sensor-first refinement | Ten trials; immediate generic severity and one named refinement when subsequently eligible; no repeat loop |
| Detach/reconnect and stale frames | Five cycles each; no stale name retained across connection/session boundaries |
| Stop | Ten trials including inference/TTS/pending refinement; existing <=250 ms handler-to-silence requirement measured |
| Search regression | Existing keys/wallet tests and applicable physical AC-13 trials on unchanged Locate configuration |

Proposed refinement budget: <=300 ms from eligible association decision to accepted speech request when no higher-priority conflict exists; measure separately from TTS audible onset and from detection delay. This budget does not replace PRD's physical sensor-to-audio STOP gate. Eligible cases with suppression remain failures or diagnosed policy conflicts, not silently removed from denominators.

All checks above are NOT RUN in this planning task. AC-01/05/06/07/10/11/12/13/15 and other affected gates retain their independent requirements; passing a model dataset test does not establish walking acceptance.

## 8. File ownership and handoffs

| Owner | Existing files / proposed responsibility | Receiver |
|---|---|---|
| Spandan | `datasets/` audit/derived manifest, `scripts/` training/export/evaluation, `models/mobility/` artifacts and metadata | Samik verifies Android decoding/runtime; Rishav receives candidate identity |
| Samik | `navigation/RiskEngine.kt`, `FusionVisionTrackStore.kt`, owned fusion contracts/tests, `camera/`, `inference/` adapter compatibility | Rishav verifies integration; Rohan reviews sensor evidence semantics |
| Rohan | `usb/` / firmware producer validation and actual mount/bench measurements | Samik receives valid, timed sensor evidence |
| Rishav | `app/MainActivity.kt`, `SessionCoordinator.kt`, `voice/SpeechArbiter.kt`, speech contract and shared build edits | Samik/Rohan review integrated behavior |
| Subham | No new implementation assigned; existing laptop Locate stays separate | Relevant model/search compatibility review only if its contract changes |

H1 model handoff includes weights/export hashes, ordered labels, preprocessing/output tensors, dataset split provenance, per-class results, measured latency and reference inputs. H2/H3/H6 integration handoff includes event contracts, association rejection reasons, speech metadata, exact patch requests, replay results, device evidence and unresolved failures. Delivery is not ACK.

## 9. Execution order, estimates and stopping conditions

1. Entry/source/device diagnosis and dataset audit: estimate 1–2 hours, dependent on download and annotation quality.
2. Prepare independent evaluation/splits and phone data: estimate 2–4 hours of operator work; missing data may require more.
3. Fine-tuning/export: estimate only after measured epoch/export time; do not promise a GPU or completion time.
4. Fusion/structured speech repair and replay tests: estimate 3–5 hours after contract review.
5. Android integration, qualification and receiver review: estimate 2–4 hours plus device availability.

Model work and independent fusion fixtures may proceed as separate owner workstreams after their own entry reviews. No subagents or paid compute are started by this plan. Training need not block fixing event association; untrained candidates cannot replace working artifacts.

Stop deployment if labels/boxes are wrong after export, both classes do not meet the agreed evaluation target, timing cannot satisfy freshness/persistence, sensor STOP regresses, ambiguous scenes produce wrong names, or receiver review returns defects. Continue bounded diagnostics and retain the last known artifact. Do not hide poor accuracy by lowering thresholds or making generic warnings always say “chair.”

## 10. Completion record and next action

Next action: Spandan audits the downloaded chair/table annotations and pins dataset version; Samik reproduces the ESP32-first speech path using current model and event traces; Rishav reviews proposed structured speech changes. The first deliverables are dataset-readiness counts and a classified speech-failure trace, not a training-success claim.

At each completed unit: update implementation-state with actor, revision, artifact hash, commands, results, limitations and next owner; commit/push intended implementation to main and append the relay on communication. Run appropriate unit/build checks after implementation; documentation-only checks do not require retraining or APK installation. Obtain named receiver review and keep product-owner acceptance separate.

Frozen files remain unchanged. Verified planning baseline: README `54B140D1442F9E82DFCA024DE157BD6505452906968EC92588D8B2337F5990BA`; guidance `A317342E58F0F29528002A3581C804B403070DB99F8EE8622914722609D7596E`.

## Communication branch — mandatory

All work/progress/handoff/review/decision messages belong in communication's append-only `team-chat.md`. Only chat/decision-log changes go there; implementation, plans and status changes remain on main under AGENTS.md. Preserve concurrent messages, record real times and evidence, obtain receiver responses, and never treat chat as permission to alter frozen contracts or as physical verification.
