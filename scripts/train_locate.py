#!/usr/bin/env python3
"""NaviSense AI — Locate Model Training / Fine-tuning Pipeline.

Fine-tunes YOLOv8n on the custom Locate tabletop dataset (keys, wallet).
Governed by:
- PRD v3.2 Section 9.2, 9.3, 29 (AC-02)
- models/metadata/locate_model_contract.json

Target Classes:
  0: keys
  1: wallet
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import random
import shutil
import sys
import time
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("train_locate")


def set_seed(seed: int = 42):
    random.seed(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)
    try:
        import numpy as np
        np.random.seed(seed)
    except ImportError:
        pass
    try:
        import torch
        torch.manual_seed(seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(seed)
    except ImportError:
        pass


def select_device(requested_device: str | None = None) -> str:
    import torch
    if requested_device:
        return requested_device
    if torch.cuda.is_available():
        return "cuda:0"
    if torch.backends.mps.is_available():
        return "mps"
    return "cpu"


def train_locate(
    data_yaml: Path,
    epochs: int = 30,
    batch_size: int = 16,
    img_size: int = 640,
    weights: str = "yolov8n.pt",
    seed: int = 42,
    device: str | None = None,
    output_dir: Path = Path("models/locate"),
) -> Path:
    set_seed(seed)
    data_yaml = data_yaml.resolve()
    output_dir = output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    if not data_yaml.exists():
        raise FileNotFoundError(f"data.yaml not found at: {data_yaml}")

    actual_device = select_device(device)

    logger.info("=" * 70)
    logger.info("NaviSense Locate YOLO Training Pipeline")
    logger.info("=" * 70)
    logger.info(f"Data config : {data_yaml}")
    logger.info(f"Base weights: {weights}")
    logger.info(f"Epochs      : {epochs}")
    logger.info(f"Batch size  : {batch_size}")
    logger.info(f"Img size    : {img_size}")
    logger.info(f"Seed        : {seed}")
    logger.info(f"Device      : {actual_device}")
    logger.info(f"Output dir  : {output_dir}")

    from ultralytics import YOLO

    logger.info(f"Loading base YOLO weights: {weights}")
    model = YOLO(weights)

    start_time = time.time()
    logger.info("Starting training run...")

    results = model.train(
        data=str(data_yaml),
        epochs=epochs,
        batch=batch_size,
        imgsz=img_size,
        seed=seed,
        device=actual_device,
        project=str(output_dir / "runs"),
        name="locate_yolo",
        exist_ok=True,
        pretrained=True,
        verbose=True,
        plots=True,
        save=True,
        cache=False,
    )

    elapsed_s = time.time() - start_time
    logger.info(f"Training finished in {elapsed_s:.1f} seconds.")

    # Locate best checkpoint
    run_dir = output_dir / "runs" / "locate_yolo"
    best_pt = run_dir / "weights" / "best.pt"
    if not best_pt.exists():
        best_pt = run_dir / "weights" / "last.pt"

    dest_best = output_dir / "locate_best.pt"
    if best_pt.exists():
        shutil.copy2(best_pt, dest_best)
        logger.info(f"Saved best model checkpoint to: {dest_best}")
    else:
        raise RuntimeError(f"No trained weights found in {run_dir / 'weights'}")

    # Run validation evaluation
    logger.info("Running post-training validation evaluation...")
    val_model = YOLO(str(dest_best))
    metrics = val_model.val(data=str(data_yaml), imgsz=img_size, batch=batch_size, device=actual_device)

    # Extract summary metrics
    eval_summary = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "epochs": epochs,
        "batch_size": batch_size,
        "elapsed_seconds": elapsed_s,
        "map50": float(metrics.box.map50) if hasattr(metrics.box, "map50") else 0.0,
        "map50_95": float(metrics.box.map) if hasattr(metrics.box, "map") else 0.0,
        "precision": float(metrics.box.mp) if hasattr(metrics.box, "mp") else 0.0,
        "recall": float(metrics.box.mr) if hasattr(metrics.box, "mr") else 0.0,
        "classes": {
            "0": "keys",
            "1": "wallet",
        },
        "per_class": {},
    }

    if hasattr(metrics.box, "p") and hasattr(metrics.box, "r"):
        class_names = ["keys", "wallet"]
        for idx, name in enumerate(class_names):
            p = float(metrics.box.p[idx]) if idx < len(metrics.box.p) else 0.0
            r = float(metrics.box.r[idx]) if idx < len(metrics.box.r) else 0.0
            eval_summary["per_class"][name] = {"precision": p, "recall": r}

    metrics_path = output_dir / "training_metrics.json"
    with open(metrics_path, "w", encoding="utf-8") as f:
        json.dump(eval_summary, f, indent=2)
    logger.info(f"Saved evaluation metrics to: {metrics_path}")

    return dest_best


def main():
    parser = argparse.ArgumentParser(description="NaviSense Locate YOLO Fine-Tuning")
    parser.add_argument(
        "--data",
        type=Path,
        default=Path("datasets/locate_roboflow/data.yaml"),
        help="Path to data.yaml",
    )
    parser.add_argument("--epochs", type=int, default=30, help="Training epochs")
    parser.add_argument("--batch-size", type=int, default=16, help="Batch size")
    parser.add_argument("--img-size", type=int, default=640, help="Image size")
    parser.add_argument("--weights", type=str, default="yolov8n.pt", help="Pretrained weights")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument("--device", type=str, default=None, help="Device (cpu, mps, cuda:0)")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("models/locate"),
        help="Output directory",
    )

    args = parser.parse_args()
    train_locate(
        data_yaml=args.data,
        epochs=args.epochs,
        batch_size=args.batch_size,
        img_size=args.img_size,
        weights=args.weights,
        seed=args.seed,
        device=args.device,
        output_dir=args.output_dir,
    )


if __name__ == "__main__":
    main()
