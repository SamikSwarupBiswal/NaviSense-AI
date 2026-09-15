#!/usr/bin/env python3
"""NaviSense Mobility Combined Dataset Generator.

Unifies:
1. datasets/mobility_indoor (from thepbordin/indoor-object-detection)
2. datasets/phone_walking/captured_dataset (from physical Android phone)

Produces:
- datasets/mobility_combined/
    images/train, images/val
    labels/train, labels/val
    data.yaml
    manifest.json

Target Classes (PRD §8.2 / models/metadata/mobility_model_contract.json):
  0: person
  1: chair
  2: table
  3: backpack
  4: bottle
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import random
import shutil
import sys
from pathlib import Path
from typing import Dict, List, Tuple

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("prepare_mobility_dataset")

TARGET_CLASSES = {
    0: "person",
    1: "chair",
    2: "table",
    3: "backpack",
    4: "bottle",
}


def count_labels(label_file: Path) -> Tuple[Dict[int, int], bool]:
    counts = {k: 0 for k in TARGET_CLASSES}
    if not label_file.exists() or label_file.stat().st_size == 0:
        return counts, True

    has_objects = False
    with open(label_file, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) >= 5:
                try:
                    cls_id = int(parts[0])
                    if cls_id in counts:
                        counts[cls_id] += 1
                        has_objects = True
                except ValueError:
                    continue
    return counts, not has_objects


def prepare_combined_dataset(
    indoor_dir: Path,
    phone_dir: Path,
    output_dir: Path,
    phone_val_ratio: float = 0.20,
    seed: int = 42,
) -> Dict[str, object]:
    indoor_dir = indoor_dir.resolve()
    phone_dir = phone_dir.resolve()
    output_dir = output_dir.resolve()

    logger.info("Initializing unified mobility dataset preparation...")
    logger.info(f"Indoor source : {indoor_dir}")
    logger.info(f"Phone source  : {phone_dir}")
    logger.info(f"Destination   : {output_dir}")

    # Output layout
    train_img_dir = output_dir / "images" / "train"
    val_img_dir = output_dir / "images" / "val"
    train_lbl_dir = output_dir / "labels" / "train"
    val_lbl_dir = output_dir / "labels" / "val"

    for d in (train_img_dir, val_img_dir, train_lbl_dir, val_lbl_dir):
        if d.exists():
            shutil.rmtree(d)
        d.mkdir(parents=True, exist_ok=True)

    stats = {
        "indoor_train_count": 0,
        "indoor_val_count": 0,
        "phone_train_count": 0,
        "phone_val_count": 0,
        "total_train_images": 0,
        "total_val_images": 0,
        "class_counts_train": {name: 0 for name in TARGET_CLASSES.values()},
        "class_counts_val": {name: 0 for name in TARGET_CLASSES.values()},
        "negatives_train": 0,
        "negatives_val": 0,
    }

    # 1. Ingest indoor dataset splits
    for split in ("train", "val"):
        src_img_dir = indoor_dir / "images" / split
        src_lbl_dir = indoor_dir / "labels" / split
        dst_img_dir = val_img_dir if split == "val" else train_img_dir
        dst_lbl_dir = val_lbl_dir if split == "val" else train_lbl_dir

        if not src_img_dir.exists():
            logger.warning(f"Indoor split directory missing: {src_img_dir}")
            continue

        for img_path in sorted(src_img_dir.glob("*.*")):
            if img_path.suffix.lower() not in (".jpg", ".jpeg", ".png"):
                continue
            lbl_path = src_lbl_dir / f"{img_path.stem}.txt"

            new_img_name = f"indoor_{img_path.name}"
            new_lbl_name = f"indoor_{img_path.stem}.txt"

            shutil.copy2(img_path, dst_img_dir / new_img_name)
            dst_lbl = dst_lbl_dir / new_lbl_name
            if lbl_path.exists():
                shutil.copy2(lbl_path, dst_lbl)
            else:
                dst_lbl.write_text("")

            counts, is_neg = count_labels(dst_lbl)
            if split == "val":
                stats["indoor_val_count"] += 1
                if is_neg:
                    stats["negatives_val"] += 1
                for cid, c in counts.items():
                    stats["class_counts_val"][TARGET_CLASSES[cid]] += c
            else:
                stats["indoor_train_count"] += 1
                if is_neg:
                    stats["negatives_train"] += 1
                for cid, c in counts.items():
                    stats["class_counts_train"][TARGET_CLASSES[cid]] += c

    logger.info(
        f"Copied indoor dataset: {stats['indoor_train_count']} train, {stats['indoor_val_count']} val"
    )

    # 2. Ingest phone walking captured dataset
    phone_img_dir = phone_dir / "images"
    phone_lbl_dir = phone_dir / "labels"
    phone_images = sorted(
        [p for p in phone_img_dir.glob("*.*") if p.suffix.lower() in (".jpg", ".jpeg", ".png")]
    )

    random.seed(seed)
    # Deterministic shuffle for split
    shuffled_phone = list(phone_images)
    random.shuffle(shuffled_phone)

    val_cutoff = max(1, int(len(shuffled_phone) * phone_val_ratio))
    val_set = set(shuffled_phone[:val_cutoff])

    for img_path in phone_images:
        is_val = img_path in val_set
        dst_img_dir = val_img_dir if is_val else train_img_dir
        dst_lbl_dir = val_lbl_dir if is_val else train_lbl_dir

        lbl_path = phone_lbl_dir / f"{img_path.stem}.txt"

        new_img_name = f"phone_{img_path.name}"
        new_lbl_name = f"phone_{img_path.stem}.txt"

        shutil.copy2(img_path, dst_img_dir / new_img_name)
        dst_lbl = dst_lbl_dir / new_lbl_name
        if lbl_path.exists():
            shutil.copy2(lbl_path, dst_lbl)
        else:
            dst_lbl.write_text("")

        counts, is_neg = count_labels(dst_lbl)
        if is_val:
            stats["phone_val_count"] += 1
            if is_neg:
                stats["negatives_val"] += 1
            for cid, c in counts.items():
                stats["class_counts_val"][TARGET_CLASSES[cid]] += c
        else:
            stats["phone_train_count"] += 1
            if is_neg:
                stats["negatives_train"] += 1
            for cid, c in counts.items():
                stats["class_counts_train"][TARGET_CLASSES[cid]] += c

    logger.info(
        f"Copied phone walking dataset: {stats['phone_train_count']} train, {stats['phone_val_count']} val"
    )

    stats["total_train_images"] = stats["indoor_train_count"] + stats["phone_train_count"]
    stats["total_val_images"] = stats["indoor_val_count"] + stats["phone_val_count"]

    # 3. Write data.yaml
    data_yaml_path = output_dir / "data.yaml"
    with open(data_yaml_path, "w", encoding="utf-8") as f:
        f.write("# NaviSense Combined Mobility YOLO Dataset\n")
        f.write(f"path: {output_dir.as_posix()}\n")
        f.write("train: images/train\n")
        f.write("val: images/val\n\n")
        f.write(f"nc: {len(TARGET_CLASSES)}\n")
        f.write(f"names: {json.dumps(TARGET_CLASSES)}\n")

    # 4. Write manifest.json
    manifest_path = output_dir / "manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(stats, f, indent=2)

    logger.info("=== Dataset Unification Complete ===")
    logger.info(f"Total Train Images: {stats['total_train_images']} (Negatives: {stats['negatives_train']})")
    logger.info(f"Total Val Images  : {stats['total_val_images']} (Negatives: {stats['negatives_val']})")
    logger.info(f"Train Detections  : {stats['class_counts_train']}")
    logger.info(f"Val Detections    : {stats['class_counts_val']}")
    logger.info(f"data.yaml written to: {data_yaml_path}")

    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare combined NaviSense Mobility dataset.")
    parser.add_argument(
        "--indoor-dir",
        type=Path,
        default=Path("datasets/mobility_indoor"),
        help="Path to indoor dataset directory",
    )
    parser.add_argument(
        "--phone-dir",
        type=Path,
        default=Path("datasets/phone_walking/captured_dataset"),
        help="Path to phone walking captured dataset directory",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("datasets/mobility_combined"),
        help="Path to output combined dataset",
    )
    parser.add_argument(
        "--val-ratio",
        type=float,
        default=0.20,
        help="Ratio of phone images for validation split",
    )
    parser.add_argument("--seed", type=int, default=42, help="Random seed for split")
    args = parser.parse_args()

    try:
        prepare_combined_dataset(
            indoor_dir=args.indoor_dir,
            phone_dir=args.phone_dir,
            output_dir=args.output_dir,
            phone_val_ratio=args.val_ratio,
            seed=args.seed,
        )
        return 0
    except Exception as e:
        logger.error(f"Failed to prepare combined dataset: {e}", exc_info=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
