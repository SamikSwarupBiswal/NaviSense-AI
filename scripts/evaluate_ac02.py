#!/usr/bin/env python3
"""
NaviSense AI — AC-02 Model Quality Evaluation Tool
Lead: Spandan (Models & Datasets)
Governing Criteria: PRD v3.2 Section 29 (AC-02)

Requirements:
- >= 50 labeled instances per selected demo class per device (laptop, phone)
- >= 20 negative frames per device
- Match at IoU >= 0.50 and confidence >= 0.60
- Per-class precision >= 90% and recall >= 85% on each device
- Report raw counts: True Positives (TP), False Positives (FP), False Negatives (FN)
"""

import sys
import os
import json
import argparse
from typing import List, Dict, Tuple

def compute_iou(box1: List[float], box2: List[float]) -> float:
    """Computes IoU between two [x1, y1, x2, y2] boxes."""
    x1 = max(box1[0], box2[0])
    y1 = max(box1[1], box2[1])
    x2 = min(box1[2], box2[2])
    y2 = min(box1[3], box2[3])

    intersection = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    area1 = max(0.0, box1[2] - box1[0]) * max(0.0, box1[3] - box1[1])
    area2 = max(0.0, box2[2] - box2[0]) * max(0.0, box2[3] - box2[1])
    union = area1 + area2 - intersection

    if union <= 0.0:
        return 0.0
    return intersection / union

def evaluate_device_split(
    device_name: str,
    ground_truth: List[Dict],
    predictions: List[Dict],
    classes: List[str] = ["keys", "wallet"],
    iou_thresh: float = 0.50,
    conf_thresh: float = 0.60
) -> Dict:
    results = {
        "device": device_name,
        "classes": {},
        "negative_frames": {
            "total": 0,
            "false_alarms": 0
        },
        "overall_pass": False
    }

    class_stats = {
        c: {"gt_count": 0, "tp": 0, "fp": 0, "fn": 0, "precision": 0.0, "recall": 0.0, "pass": False}
        for c in classes
    }

    # Map image_id -> gt objects and pred objects
    gt_by_img = {}
    for item in ground_truth:
        img_id = item["image_id"]
        gt_by_img.setdefault(img_id, []).extend(item.get("annotations", []))
        if item.get("is_negative", False):
            results["negative_frames"]["total"] += 1

    preds_by_img = {}
    for item in predictions:
        img_id = item["image_id"]
        # Filter predictions by confidence threshold >= 0.60
        valid_preds = [p for p in item.get("detections", []) if p.get("confidence", 0.0) >= conf_thresh]
        preds_by_img.setdefault(img_id, []).extend(valid_preds)

    # Process all images
    all_imgs = set(list(gt_by_img.keys()) + list(preds_by_img.keys()))

    for img_id in all_imgs:
        img_gt = gt_by_img.get(img_id, [])
        img_preds = preds_by_img.get(img_id, [])
        is_neg = len(img_gt) == 0

        if is_neg and len(img_preds) > 0:
            results["negative_frames"]["false_alarms"] += len(img_preds)

        # Match per class
        for c in classes:
            c_gt = [g for g in img_gt if g["class_name"] == c]
            c_preds = [p for p in img_preds if p["class_name"] == c]
            class_stats[c]["gt_count"] += len(c_gt)

            # Sort predictions by confidence descending
            c_preds.sort(key=lambda x: x.get("confidence", 0.0), reverse=True)
            matched_gt = set()

            for p in c_preds:
                best_iou = 0.0
                best_gt_idx = -1
                for idx, g in enumerate(c_gt):
                    if idx in matched_gt:
                        continue
                    iou = compute_iou(p["bbox_xyxy"], g["bbox_xyxy"])
                    if iou > best_iou:
                        best_iou = iou
                        best_gt_idx = idx

                if best_iou >= iou_thresh and best_gt_idx != -1:
                    class_stats[c]["tp"] += 1
                    matched_gt.add(best_gt_idx)
                else:
                    class_stats[c]["fp"] += 1

            # Unmatched GTs are False Negatives
            class_stats[c]["fn"] += (len(c_gt) - len(matched_gt))

    # Compute metrics per class
    all_pass = True
    for c in classes:
        tp = class_stats[c]["tp"]
        fp = class_stats[c]["fp"]
        fn = class_stats[c]["fn"]
        gt_count = class_stats[c]["gt_count"]

        precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0

        # PRD Gate: precision >= 0.90, recall >= 0.85, instances >= 50
        quota_met = gt_count >= 50
        qual_met = (precision >= 0.90) and (recall >= 0.85)
        passed = quota_met and qual_met

        class_stats[c]["precision"] = round(precision, 4)
        class_stats[c]["recall"] = round(recall, 4)
        class_stats[c]["quota_met"] = quota_met
        class_stats[c]["pass"] = passed

        if not passed:
            all_pass = False

    neg_quota_met = results["negative_frames"]["total"] >= 20
    results["negative_frames"]["quota_met"] = neg_quota_met
    if not neg_quota_met:
        all_pass = False

    results["classes"] = class_stats
    results["overall_pass"] = all_pass
    return results

def main():
    parser = argparse.ArgumentParser(description="Evaluate AC-02 gate for Locate YOLO.")
    parser.add_argument("--gt", type=str, help="Path to ground truth JSON")
    parser.add_argument("--pred", type=str, help="Path to predictions JSON")
    parser.add_argument("--device", type=str, default="laptop", choices=["laptop", "phone"])
    args = parser.parse_args()

    if not args.gt or not args.pred:
        print("AC-02 Evaluation Harness (PRD v3.2 Section 29)")
        print("Usage: python3 scripts/evaluate_ac02.py --gt <gt.json> --pred <pred.json> --device <laptop|phone>")
        print()
        print("Required targets per device:")
        print("  - Labeled instances per class: >= 50")
        print("  - Negative frames: >= 20")
        print("  - Match IoU: >= 0.50, Confidence: >= 0.60")
        print("  - Per-class precision: >= 90.0%")
        print("  - Per-class recall: >= 85.0%")
        sys.exit(0)

    def load_records(path: str, is_gt: bool = True):
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, list):
            return data
        elif isinstance(data, dict):
            if "images" in data:
                return data["images"]
            elif "ground_truth_objects" in data:
                img_id = data.get("reference_image", {}).get("file_path", "ref_image")
                if is_gt:
                    return [{
                        "image_id": img_id,
                        "annotations": data["ground_truth_objects"],
                        "is_negative": len(data["ground_truth_objects"]) == 0
                    }]
                else:
                    mock_detections = [
                        {
                            "class_name": obj["class_name"],
                            "bbox_xyxy": obj["bbox_xyxy"],
                            "confidence": 1.0
                        }
                        for obj in data["ground_truth_objects"]
                    ]
                    return [{
                        "image_id": img_id,
                        "detections": mock_detections
                    }]
            elif "detections" in data and not is_gt:
                return [{
                    "image_id": data.get("image_id", "ref_image"),
                    "detections": data["detections"]
                }]
            else:
                return [data]
        return []

    gt_data = load_records(args.gt, is_gt=True)
    pred_data = load_records(args.pred, is_gt=False)

    results = evaluate_device_split(args.device, gt_data, pred_data)

    print("=" * 60)
    print(f"AC-02 Gate Evaluation Report — Device: {results['device'].upper()}")
    print("=" * 60)
    for cls_name, stats in results["classes"].items():
        status = "PASS" if stats["pass"] else "FAIL"
        print(f"Class: {cls_name:<10} [{status}]")
        print(f"  GT Count:   {stats['gt_count']:>4} (Quota >= 50: {'MET' if stats['quota_met'] else 'UNMET'})")
        print(f"  TP: {stats['tp']:>4} | FP: {stats['fp']:>4} | FN: {stats['fn']:>4}")
        print(f"  Precision: {stats['precision']*100:>6.2f}% (Target: >= 90.00%)")
        print(f"  Recall:    {stats['recall']*100:>6.2f}% (Target: >= 85.00%)")
        print("-" * 60)

    neg = results["negative_frames"]
    print(f"Negative Frames: Total={neg['total']}, False Alarms={neg['false_alarms']} (Quota >= 20: {'MET' if neg['quota_met'] else 'UNMET'})")
    print("=" * 60)
    print(f"OVERALL AC-02 VERDICT: {'PASSED' if results['overall_pass'] else 'FAILED / PENDING DATA'}")
    print("=" * 60)

    if not results["overall_pass"]:
        sys.exit(1)
    sys.exit(0)

if __name__ == "__main__":
    main()
