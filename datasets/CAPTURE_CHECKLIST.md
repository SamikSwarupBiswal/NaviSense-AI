# NaviSense AI — Locate Dataset Capture Checklist (Phase 1 Preparation)

Lead: **Spandan** | Receivers: **Subham** (Laptop Views) & **Samik** (Phone Views)
Governing Requirements: [PRD v3.2](../docs/README.md) Sections 9, 29, 30 | Gate: **AC-02**

---

## 1. Objective and Quotas

To fulfill **AC-02**, we must collect high-quality, diverse, disjoint image sets for both target devices:
- **Laptop Tabletop Camera (Subham):**
  - >= 50 labeled instances of `keys`
  - >= 50 labeled instances of `wallet`
  - >= 20 negative frames (empty table, non-target items only)
- **Android Phone Camera (Samik):**
  - >= 50 labeled instances of `keys` (close-range, handheld search angles)
  - >= 50 labeled instances of `wallet` (close-range, handheld search angles)
  - >= 20 negative frames (walking perspectives, non-target surfaces)

---

## 2. Hard Partitioning Rules (Zero Leakage)

1. **Session & Layout Isolation:**
   - Partition data strictly by **Capture Session / Layout ID**, NEVER by random adjacent frames from a video stream.
   - Example: Session 1 (morning, wood table, desk lamp) -> `train`
   - Session 2 (afternoon, white desk, daylight) -> `train`
   - Session 3 (overhead light, blue mat) -> `val`
   - Session 4 (held-out novel layout, distinct clutter/angle) -> `test`
2. **Disjoint Test Set:**
   - The test split (minimum 10% of data, containing at least 50 test instances per class per device for AC-02) must NEVER be seen during training or validation hyperparameter tuning.

---

## 3. Systematic Variations Matrix

For each session, systematically record images across the following parameters:

| Variation Axis | Configurations to Capture |
|---|---|
| **Placement** | Center of table, left edge, right edge, near camera, far camera |
| **Orientation** | Standard orientation, rotated 45°, rotated 90°, upside down |
| **Lighting** | Bright overhead room light, directional desk lamp, dim/warm evening light, window backlight |
| **Occlusion** | Unobstructed (clear view), 25% occluded under notebook/paper, 50% occluded |
| **Clutter** | Isolated object on surface, placed next to laptop/monitor, placed next to bottle/mug, placed among pens/cables |
| **Distance (Phone)** | Very close (20-30 cm, macro search), medium (50-80 cm), desk overview (100-120 cm) |
| **Distance (Laptop)** | Stationary fixed webcam angle at normal operating tabletop distance |

---

## 4. Annotation Guidelines (YOLO Format)

1. Annotations must be saved in normalized YOLO `.txt` format:
   `<class_id> <x_center> <y_center> <width> <height>`
   where:
   - `class_id = 0` for `keys`
   - `class_id = 1` for `wallet`
   - all coordinates are floats in `[0.0, 1.0]` relative to image width and height.
2. Bounding Box Tightness:
   - Include keyrings, fob, and all attached keys in the `keys` box.
   - For `wallet`, enclose the full outer boundary of the leather/fabric body.
   - For partially occluded objects, draw the box tightly around the **visible extent** only.
3. Negative Frames:
   - Negative images must have an **empty `.txt` file** of 0 bytes with matching basename.

---

## 5. Handoff Timeline

- **T+01:45:** Checklist distributed to Subham and Samik.
- **T+02:30:** Initial capture batches received by Spandan.
- **T+04:00:** Full annotation and split verification completed by Spandan.
