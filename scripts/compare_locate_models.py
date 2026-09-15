#!/usr/bin/env python3
"""Compare Locate checkpoints on a labeled YOLO image directory."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from ultralytics import YOLO


def label_counts(label_path: Path) -> set[int]:
    if not label_path.exists():
        return set()
    classes = set()
    for line in label_path.read_text(encoding="utf-8").splitlines():
        fields = line.split()
        if fields:
            classes.add(int(fields[0]))
    return classes


def evaluate(model_path: Path, image_dir: Path, limit: int, device: str) -> dict:
    model = YOLO(str(model_path))
    images = sorted(path for path in image_dir.iterdir() if path.suffix.lower() in {".jpg", ".jpeg", ".png"})[:limit]
    labels_dir = image_dir.parent / "labels"
    summary = {
        "model": str(model_path),
        "images": len(images),
        "at_0_25": {"images_with_detection": 0, "class_hits": {"keys": 0, "wallet": 0}},
        "at_0_60": {"images_with_detection": 0, "class_hits": {"keys": 0, "wallet": 0}},
        "details": [],
    }
    for image in images:
        truth = label_counts(labels_dir / f"{image.stem}.txt")
        result = model.predict(str(image), imgsz=640, conf=0.001, iou=0.45, device=device, verbose=False)[0]
        predictions = [
            {"class_id": int(cls), "confidence": float(confidence)}
            for cls, confidence in zip(result.boxes.cls.tolist(), result.boxes.conf.tolist())
        ]
        detail = {"image": image.name, "truth": sorted(truth), "max": {"keys": 0.0, "wallet": 0.0}}
        for prediction in predictions:
            name = "keys" if prediction["class_id"] == 0 else "wallet"
            detail["max"][name] = max(detail["max"][name], prediction["confidence"])
        for threshold_name, threshold in (("at_0_25", 0.25), ("at_0_60", 0.60)):
            accepted = [prediction for prediction in predictions if prediction["confidence"] >= threshold]
            if accepted:
                summary[threshold_name]["images_with_detection"] += 1
            for class_id, class_name in ((0, "keys"), (1, "wallet")):
                if class_id in truth and any(item["class_id"] == class_id for item in accepted):
                    summary[threshold_name]["class_hits"][class_name] += 1
        summary["details"].append(detail)
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, action="append", required=True)
    parser.add_argument("--images", type=Path, required=True)
    parser.add_argument("--limit", type=int, default=30)
    parser.add_argument("--device", default="0", help="Ultralytics device; defaults to the first GPU")
    args = parser.parse_args()
    reports = [evaluate(model, args.images, args.limit, args.device) for model in args.model]
    print(json.dumps(reports, indent=2))


if __name__ == "__main__":
    main()
