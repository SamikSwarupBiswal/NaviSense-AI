#!/usr/bin/env python3
"""NaviSense AI — Fine-tune 2-Class Mobility Candidate (table=0, chair=1).

Governed by:
- docs/samik/chair-table-fusion-implementation-plan.md
- PRD Section 17.1

Target Classes:
  0: table
  1: chair
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import os
import shutil
import sys
import time
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("train_mobility_chair_table")

DATA_YAML = Path("datasets/mobility_chair_table/data.yaml")
OUTPUT_DIR = Path("models/mobility_chair_table")


def compute_sha256(filepath: Path) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def train_and_export(
    data_yaml: Path = DATA_YAML,
    epochs: int = 25,
    batch_size: int = 16,
    img_size: int = 640,
    weights: str = "yolov8n.pt",
    seed: int = 42,
    device: str = "0",
    output_dir: Path = OUTPUT_DIR,
):
    import torch
    from ultralytics import YOLO

    output_dir.mkdir(parents=True, exist_ok=True)
    data_yaml = data_yaml.resolve()

    if not data_yaml.exists():
        raise FileNotFoundError(f"data.yaml not found at: {data_yaml}")

    actual_device = device
    if device in ("0", "cuda:0", "cuda") and not torch.cuda.is_available():
        logger.warning("CUDA requested but not available. Falling back to CPU.")
        actual_device = "cpu"

    logger.info("=" * 70)
    logger.info("NaviSense 2-Class Mobility Fine-Tuning Pipeline (0: table, 1: chair)")
    logger.info("=" * 70)
    logger.info(f"Data config : {data_yaml}")
    logger.info(f"Base weights: {weights}")
    logger.info(f"Epochs      : {epochs}")
    logger.info(f"Batch size  : {batch_size}")
    logger.info(f"Img size    : {img_size}")
    logger.info(f"Device      : {actual_device}")

    model = YOLO(weights)
    start_time = time.time()

    results = model.train(
        data=str(data_yaml),
        epochs=epochs,
        batch=batch_size,
        imgsz=img_size,
        seed=seed,
        device=actual_device,
        project=str(output_dir / "runs"),
        name="candidate_01",
        exist_ok=True,
        pretrained=True,
        verbose=True,
        plots=True,
        save=True,
        cache=False,
    )

    elapsed_s = time.time() - start_time
    logger.info(f"Training completed in {elapsed_s:.1f} seconds.")

    run_dir = output_dir / "runs" / "candidate_01"
    best_pt = run_dir / "weights" / "best.pt"
    if not best_pt.exists():
        best_pt = run_dir / "weights" / "last.pt"

    dest_best_pt = output_dir / "mobility_chair_table_best.pt"
    shutil.copy2(best_pt, dest_best_pt)
    pt_hash = compute_sha256(dest_best_pt)
    logger.info(f"Saved best weights: {dest_best_pt} (SHA-256: {pt_hash})")

    # Run validation
    logger.info("Running validation evaluation on test/val set...")
    val_model = YOLO(str(dest_best_pt))
    metrics = val_model.val(data=str(data_yaml), imgsz=img_size, batch=batch_size, device=actual_device)

    # Export to TFLite FP16
    logger.info("Exporting to TFLite (FP16 format for mobile GPU delegate)...")
    exported_path = val_model.export(format="tflite", imgsz=img_size, half=True)
    logger.info(f"Exported TFLite model: {exported_path}")

    tflite_src = Path(exported_path)
    dest_tflite = output_dir / "mobility_chair_table.tflite"
    if tflite_src.exists():
        shutil.copy2(tflite_src, dest_tflite)
        tflite_hash = compute_sha256(dest_tflite)
        logger.info(f"Saved TFLite candidate to: {dest_tflite} (SHA-256: {tflite_hash})")
    else:
        dest_tflite = None
        tflite_hash = None

    summary = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "dataset": str(data_yaml),
        "epochs": epochs,
        "batch_size": batch_size,
        "elapsed_seconds": elapsed_s,
        "map50": float(metrics.box.map50) if hasattr(metrics.box, "map50") else 0.0,
        "map50_95": float(metrics.box.map) if hasattr(metrics.box, "map") else 0.0,
        "precision": float(metrics.box.mp) if hasattr(metrics.box, "mp") else 0.0,
        "recall": float(metrics.box.mr) if hasattr(metrics.box, "mr") else 0.0,
        "classes": {
            "0": "table",
            "1": "chair"
        },
        "best_pt_sha256": pt_hash,
        "tflite_sha256": tflite_hash
    }

    report_path = output_dir / "training_summary.json"
    with open(report_path, "w", encoding="utf-8") as rf:
        json.dump(summary, rf, indent=2)

    logger.info(f"Training summary report saved: {report_path}")
    return summary


if __name__ == "__main__":
    train_and_export(epochs=15, batch_size=16)
