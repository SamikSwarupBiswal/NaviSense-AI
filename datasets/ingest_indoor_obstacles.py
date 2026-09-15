#!/usr/bin/env python3
"""Ingestion and class remapping tool for 'thepbordin/indoor-object-detection' Kaggle dataset.

Converts public indoor obstacle annotations into the NaviSense Mobility YOLO format
governed by models/metadata/mobility_model_contract.json and PRD §8.2.

Target NaviSense Mobility YOLO Classes:
  0: person
  1: chair
  2: table
  3: backpack
  4: bottle

The source dataset (thepbordin/indoor-object-detection) contains:
  Door, openedDoor, cabinetDoor, refrigeratorDoor, window, chair, table, cabinet, sofa, pole

This tool extracts 'chair' and 'table' annotations, re-indexes them to 1 and 2,
preserves or generates clean train/val splits, and optionally retains empty
scenes as valid negative frames (empty .txt files) per PRD §29 (AC-02/AC-12).
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import shutil
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("ingest_indoor_obstacles")

# Target class mapping per models/metadata/mobility_model_contract.json
TARGET_CLASS_MAP = {
    0: "person",
    1: "chair",
    2: "table",
    3: "backpack",
    4: "bottle",
}

# Source dataset typical class indices (0-indexed)
# Ref: https://www.kaggle.com/datasets/thepbordin/indoor-object-detection
DEFAULT_SOURCE_CLASS_NAMES = [
    "door",              # 0
    "openeddoor",        # 1
    "cabinetdoor",       # 2
    "refrigeratordoor",  # 3
    "window",            # 4
    "chair",             # 5
    "table",             # 6
    "cabinet",           # 7
    "sofa",              # 8
    "pole",              # 9
]


def load_source_classes(data_yaml_path: Optional[Path]) -> List[str]:
    """Load class names from a data.yaml if available, else use defaults."""
    if data_yaml_path and data_yaml_path.exists():
        try:
            import yaml  # type: ignore
            with open(data_yaml_path, "r", encoding="utf-8") as f:
                data = yaml.safe_load(f)
                if "names" in data:
                    names = data["names"]
                    if isinstance(names, list):
                        return [str(n).strip().lower() for n in names]
                    elif isinstance(names, dict):
                        return [str(names[k]).strip().lower() for k in sorted(names.keys())]
        except Exception as e:
            logger.warning(f"Failed to parse {data_yaml_path}: {e}. Falling back to standard class map.")
    return DEFAULT_SOURCE_CLASS_NAMES


def build_remapping_table(
    source_classes: List[str],
    include_sofa_as_table: bool = False,
) -> Dict[int, int]:
    """Map source dataset class IDs to NaviSense Mobility class IDs.

    Chair (5) -> 1
    Table (6) -> 2
    Sofa (8)  -> 2 (optional)
    """
    remapping: Dict[int, int] = {}
    for src_id, name in enumerate(source_classes):
        clean_name = name.strip().lower()
        if clean_name == "chair":
            remapping[src_id] = 1  # chair
        elif clean_name in ("table", "desk"):
            remapping[src_id] = 2  # table
        elif include_sofa_as_table and clean_name in ("sofa", "couch"):
            remapping[src_id] = 2  # table / furniture hazard

    logger.info("Class remapping table:")
    for src_id, target_id in remapping.items():
        src_name = source_classes[src_id] if src_id < len(source_classes) else f"id_{src_id}"
        logger.info(f"  Source [{src_id}] '{src_name}' -> Target [{target_id}] '{TARGET_CLASS_MAP[target_id]}'")
    return remapping


def process_label_file(
    src_label_path: Path,
    remapping: Dict[int, int],
) -> Tuple[List[str], Dict[int, int]]:
    """Reads a YOLO .txt file, re-indexes allowed classes, and returns new lines and counts."""
    output_lines: List[str] = []
    class_counts: Dict[int, int] = {k: 0 for k in TARGET_CLASS_MAP.keys()}

    if not src_label_path.exists() or src_label_path.stat().st_size == 0:
        return output_lines, class_counts

    with open(src_label_path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) < 5:
                continue
            try:
                src_cls = int(parts[0])
            except ValueError:
                continue

            if src_cls in remapping:
                target_cls = remapping[src_cls]
                coords = parts[1:5]
                # Validate coordinates in [0, 1]
                try:
                    vals = [float(v) for v in coords]
                    if all(0.0 <= v <= 1.0 for v in vals):
                        output_lines.append(f"{target_cls} " + " ".join(f"{v:.6f}" for v in vals))
                        class_counts[target_cls] += 1
                except ValueError:
                    continue

    return output_lines, class_counts


def ingest_dataset(
    input_dir: Path,
    output_dir: Path,
    keep_empty_as_negatives: bool = True,
    include_sofa: bool = False,
    val_ratio: float = 0.2,
) -> Dict[str, object]:
    """Ingest images and annotations, apply remapping, and produce a clean YOLO dataset."""
    input_dir = input_dir.resolve()
    output_dir = output_dir.resolve()

    if not input_dir.exists():
        raise FileNotFoundError(f"Input directory does not exist: {input_dir}")

    yaml_candidates = list(input_dir.glob("*.yaml")) + list(input_dir.glob("*.yml"))
    yaml_path = yaml_candidates[0] if yaml_candidates else None
    source_classes = load_source_classes(yaml_path)
    remapping = build_remapping_table(source_classes, include_sofa_as_table=include_sofa)

    # Find all images
    image_extensions = {".jpg", ".jpeg", ".png", ".bmp"}
    all_images = [p for p in input_dir.rglob("*") if p.suffix.lower() in image_extensions]

    if not all_images:
        raise RuntimeError(f"No images found in {input_dir}")

    logger.info(f"Discovered {len(all_images)} images in {input_dir}")

    # Prepare output layout
    train_img_dir = output_dir / "images" / "train"
    val_img_dir = output_dir / "images" / "val"
    train_lbl_dir = output_dir / "labels" / "train"
    val_lbl_dir = output_dir / "labels" / "val"

    for d in (train_img_dir, val_img_dir, train_lbl_dir, val_lbl_dir):
        d.mkdir(parents=True, exist_ok=True)

    stats = {
        "total_source_images": len(all_images),
        "processed_train": 0,
        "processed_val": 0,
        "skipped_images": 0,
        "class_counts_train": {name: 0 for name in TARGET_CLASS_MAP.values()},
        "class_counts_val": {name: 0 for name in TARGET_CLASS_MAP.values()},
        "negatives_train": 0,
        "negatives_val": 0,
    }

    # Deterministic train/val assignment
    sorted_images = sorted(all_images, key=lambda p: p.name)
    val_step = max(1, int(1.0 / val_ratio)) if val_ratio > 0 else 0

    for idx, img_path in enumerate(sorted_images):
        is_val = (val_step > 0) and (idx % val_step == 0)
        split = "val" if is_val else "train"
        target_img_dir = val_img_dir if is_val else train_img_dir
        target_lbl_dir = val_lbl_dir if is_val else train_lbl_dir

        # Look for corresponding label file in same dir or in parallel 'labels' dir
        label_path = img_path.with_suffix(".txt")
        if not label_path.exists():
            # Try parallel directory replacement: /images/ -> /labels/
            parent_name = img_path.parent.name
            if "image" in parent_name.lower():
                alt_parent = img_path.parent.parent / img_path.parent.name.replace("image", "label").replace("Image", "Label")
                alt_label = alt_parent / f"{img_path.stem}.txt"
                if alt_label.exists():
                    label_path = alt_label

        lines, counts = process_label_file(label_path, remapping)

        has_target_objects = len(lines) > 0
        if not has_target_objects and not keep_empty_as_negatives:
            stats["skipped_images"] += 1
            continue

        # Copy image
        dst_img = target_img_dir / img_path.name
        shutil.copy2(img_path, dst_img)

        # Write label file (empty .txt for negative per PRD §29 AC-02)
        dst_lbl = target_lbl_dir / f"{img_path.stem}.txt"
        with open(dst_lbl, "w", encoding="utf-8") as f:
            if lines:
                f.write("\n".join(lines) + "\n")

        # Update stats
        if is_val:
            stats["processed_val"] += 1
            if not has_target_objects:
                stats["negatives_val"] += 1
            for cls_id, count in counts.items():
                stats["class_counts_val"][TARGET_CLASS_MAP[cls_id]] += count
        else:
            stats["processed_train"] += 1
            if not has_target_objects:
                stats["negatives_train"] += 1
            for cls_id, count in counts.items():
                stats["class_counts_train"][TARGET_CLASS_MAP[cls_id]] += count

    # Write data.yaml for Ultralytics YOLO training
    data_yaml = output_dir / "data.yaml"
    with open(data_yaml, "w", encoding="utf-8") as f:
        f.write("# NaviSense Mobility YOLO Dataset\n")
        f.write(f"path: {output_dir.as_posix()}\n")
        f.write("train: images/train\n")
        f.write("val: images/val\n\n")
        f.write(f"nc: {len(TARGET_CLASS_MAP)}\n")
        f.write(f"names: {json.dumps(TARGET_CLASS_MAP)}\n")

    # Write manifest summary
    manifest_path = output_dir / "manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(stats, f, indent=2)

    logger.info("Ingestion completed successfully.")
    logger.info(f"Train images: {stats['processed_train']} (Negatives: {stats['negatives_train']})")
    logger.info(f"Val images: {stats['processed_val']} (Negatives: {stats['negatives_val']})")
    logger.info(f"Train detections: {stats['class_counts_train']}")
    logger.info(f"Val detections: {stats['class_counts_val']}")
    logger.info(f"Output saved to: {output_dir}")

    return stats


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Ingest thepbordin/indoor-object-detection Kaggle dataset into NaviSense Mobility YOLO format."
    )
    parser.add_argument(
        "--input-dir",
        type=Path,
        required=True,
        help="Path to unzipped thepbordin/indoor-object-detection dataset directory.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("datasets/mobility_indoor"),
        help="Path to output processed YOLO dataset (default: datasets/mobility_indoor).",
    )
    parser.add_argument(
        "--include-sofa",
        action="store_true",
        help="Map sofa/couch instances to table/furniture hazard (class 2).",
    )
    parser.add_argument(
        "--drop-negatives",
        action="store_true",
        help="Drop images with no chair/table instead of keeping them as empty negative frames.",
    )
    parser.add_argument(
        "--val-ratio",
        type=float,
        default=0.20,
        help="Ratio of images allocated to validation split (default: 0.20).",
    )

    args = parser.parse_args()
    try:
        ingest_dataset(
            input_dir=args.input_dir,
            output_dir=args.output_dir,
            keep_empty_as_negatives=not args.drop_negatives,
            include_sofa=args.include_sofa,
            val_ratio=args.val_ratio,
        )
        return 0
    except Exception as e:
        logger.error(f"Ingestion failed: {e}", exc_info=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
