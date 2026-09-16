#!/usr/bin/env python3
"""Audit and prepare 2-class Mobility dataset (0: table, 1: chair) from Kaggle.

Source dataset: aryakrisnaputra/objects-in-the-classroom
Target directory: datasets/mobility_chair_table/

Classes:
  0: table
  1: chair

Non-target classes are discarded from label files. Images containing no tables or chairs
are retained as background negatives (empty label files) to prevent false positive detections.
"""

import json
import os
import shutil
import sys
from pathlib import Path
import kagglehub

TARGET_DIR = Path("datasets/mobility_chair_table")
TARGET_CLASSES = {0: "table", 1: "chair"}


def audit_and_prepare():
    print("Downloading/resolving dataset aryakrisnaputra/objects-in-the-classroom via kagglehub...")
    raw_path_str = kagglehub.dataset_download("aryakrisnaputra/objects-in-the-classroom")
    raw_path = Path(raw_path_str)
    print(f"Raw dataset resolved at: {raw_path}")

    # Look for data.yaml or images/labels directory structure
    candidates = list(raw_path.rglob("data.yaml"))
    if candidates:
        dataset_root = candidates[0].parent
    else:
        dataset_root = raw_path

    print(f"Dataset root identified at: {dataset_root}")

    # Clean target directory
    if TARGET_DIR.exists():
        print(f"Cleaning existing target directory {TARGET_DIR}...")
        shutil.rmtree(TARGET_DIR)

    TARGET_DIR.mkdir(parents=True, exist_ok=True)

    splits = ["train", "val", "test"]
    stats = {
        "raw_path": str(raw_path),
        "splits": {}
    }

    total_tables = 0
    total_chairs = 0
    total_negatives = 0
    total_images_copied = 0

    for split in splits:
        split_stats = {
            "total_images": 0,
            "table_instances": 0,
            "chair_instances": 0,
            "negative_images": 0,
            "other_class_instances_dropped": 0
        }

        # Locate images and labels
        img_dir = dataset_root / "images" / split
        if not img_dir.exists():
            img_dir = dataset_root / split / "images"
        if not img_dir.exists():
            img_dir = dataset_root / "data" / "images" / split

        lbl_dir = dataset_root / "labels" / split
        if not lbl_dir.exists():
            lbl_dir = dataset_root / split / "labels"
        if not lbl_dir.exists():
            lbl_dir = dataset_root / "data" / "labels" / split

        if not img_dir.exists() or not lbl_dir.exists():
            print(f"Warning: Split {split} not found at {img_dir} or {lbl_dir}")
            continue

        target_img_dir = TARGET_DIR / "images" / split
        target_lbl_dir = TARGET_DIR / "labels" / split
        target_img_dir.mkdir(parents=True, exist_ok=True)
        target_lbl_dir.mkdir(parents=True, exist_ok=True)

        image_files = sorted([f for f in img_dir.iterdir() if f.suffix.lower() in [".jpg", ".jpeg", ".png"]])
        split_stats["total_images"] = len(image_files)

        for img_path in image_files:
            lbl_path = lbl_dir / f"{img_path.stem}.txt"
            target_annotations = []
            
            if lbl_path.exists():
                with open(lbl_path, "r", encoding="utf-8") as lf:
                    lines = lf.readlines()

                for line in lines:
                    parts = line.strip().split()
                    if len(parts) >= 5:
                        try:
                            cls_id = int(parts[0])
                            xc, yc, w, h = map(float, parts[1:5])
                            
                            # Validate coordinate bounds
                            if 0.0 <= xc <= 1.0 and 0.0 <= yc <= 1.0 and 0.0 < w <= 1.0 and 0.0 < h <= 1.0:
                                if cls_id == 0:
                                    target_annotations.append(f"0 {xc:.6f} {yc:.6f} {w:.6f} {h:.6f}")
                                    split_stats["table_instances"] += 1
                                    total_tables += 1
                                elif cls_id == 1:
                                    target_annotations.append(f"1 {xc:.6f} {yc:.6f} {w:.6f} {h:.6f}")
                                    split_stats["chair_instances"] += 1
                                    total_chairs += 1
                                else:
                                    split_stats["other_class_instances_dropped"] += 1
                        except ValueError:
                            continue

            # Copy image
            shutil.copy2(img_path, target_img_dir / img_path.name)
            # Write filtered labels (empty file if negative)
            target_lbl_file = target_lbl_dir / f"{img_path.stem}.txt"
            with open(target_lbl_file, "w", encoding="utf-8") as out_f:
                if target_annotations:
                    out_f.write("\n".join(target_annotations) + "\n")
                else:
                    split_stats["negative_images"] += 1
                    total_negatives += 1

            total_images_copied += 1

        stats["splits"][split] = split_stats
        print(f"Split [{split}]: {split_stats['total_images']} images, "
              f"Tables: {split_stats['table_instances']}, "
              f"Chairs: {split_stats['chair_instances']}, "
              f"Negatives: {split_stats['negative_images']}, "
              f"Dropped other: {split_stats['other_class_instances_dropped']}")

    stats["summary"] = {
        "total_images": total_images_copied,
        "total_tables": total_tables,
        "total_chairs": total_chairs,
        "total_negatives": total_negatives
    }

    # Write data.yaml for YOLOv8
    yaml_content = f"""# NaviSense 2-Class Mobility Candidate Dataset
path: {TARGET_DIR.resolve().as_posix()}
train: images/train
val: images/val
test: images/test

nc: 2
names:
  0: table
  1: chair
"""
    yaml_path = TARGET_DIR / "data.yaml"
    with open(yaml_path, "w", encoding="utf-8") as yf:
        yf.write(yaml_content)

    # Write audit report
    report_path = TARGET_DIR / "audit_report.json"
    with open(report_path, "w", encoding="utf-8") as rf:
        json.dump(stats, rf, indent=2)

    print(f"\nAudit and preparation complete! Output at: {TARGET_DIR}")
    print(f"data.yaml written to: {yaml_path}")
    print(f"Audit report written to: {report_path}")
    return stats


if __name__ == "__main__":
    audit_and_prepare()
