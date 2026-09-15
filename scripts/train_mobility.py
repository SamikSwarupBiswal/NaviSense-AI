#!/usr/bin/env python3
"""NaviSense AI — Mobility Model Fine-Tuning Pipeline.

Fine-tunes YOLOv8n on the combined indoor + phone walking dataset.
Governed by:
- PRD v3.2 Section 8.2, 9.2, 17.1
- models/metadata/mobility_model_contract.json

Target Classes:
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
import time
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("train_mobility")


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


def train_mobility(
    data_yaml: Path,
    epochs: int = 25,
    batch_size: int = 16,
    img_size: int = 640,
    weights: str = "yolov8n.pt",
    seed: int = 42,
    device: str = "0",
    output_dir: Path = Path("models/checkpoints"),
) -> Path:
    set_seed(seed)
    data_yaml = data_yaml.resolve()
    output_dir = output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    if not data_yaml.exists():
        raise FileNotFoundError(f"data.yaml not found at: {data_yaml}")

    logger.info("=" * 70)
    logger.info("NaviSense Mobility YOLO Fine-Tuning Pipeline")
    logger.info("=" * 70)
    logger.info(f"Data config : {data_yaml}")
    logger.info(f"Base weights: {weights}")
    logger.info(f"Epochs      : {epochs}")
    logger.info(f"Batch size  : {batch_size}")
    logger.info(f"Img size    : {img_size}")
    logger.info(f"Seed        : {seed}")
    logger.info(f"Device      : {device}")
    logger.info(f"Output dir  : {output_dir}")

    from ultralytics import YOLO
    import torch

    actual_device = device
    if device in ("0", "cuda:0", "cuda") and not torch.cuda.is_available():
        logger.warning("CUDA requested but not available. Falling back to CPU.")
        actual_device = "cpu"

    logger.info(f"Loading initial model: {weights}")
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
        name="mobility_yolo",
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
    run_dir = output_dir / "runs" / "mobility_yolo"
    best_pt = run_dir / "weights" / "best.pt"
    if not best_pt.exists():
        best_pt = run_dir / "weights" / "last.pt"

    dest_best = output_dir / "mobility_best.pt"
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
        "best_weights_path": str(dest_best),
    }

    report_path = output_dir / "mobility_training_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(eval_summary, f, indent=2)

    logger.info("=" * 70)
    logger.info("Training Summary Metrics:")
    logger.info(f"  mAP50     : {eval_summary['map50']:.4f}")
    logger.info(f"  mAP50-95  : {eval_summary['map50_95']:.4f}")
    logger.info(f"  Precision : {eval_summary['precision']:.4f}")
    logger.info(f"  Recall    : {eval_summary['recall']:.4f}")
    logger.info(f"Report saved: {report_path}")
    logger.info("=" * 70)

    return dest_best


def main() -> int:
    parser = argparse.ArgumentParser(description="Fine-tune NaviSense Mobility YOLO model.")
    parser.add_argument(
        "--data",
        type=Path,
        default=Path("datasets/mobility_combined/data.yaml"),
        help="Path to dataset data.yaml",
    )
    parser.add_argument("--epochs", type=int, default=25, help="Number of epochs (default: 25)")
    parser.add_argument("--batch-size", type=int, default=16, help="Batch size (default: 16)")
    parser.add_argument("--img-size", type=int, default=640, help="Image size (default: 640)")
    parser.add_argument("--weights", type=str, default="yolov8n.pt", help="Initial weights")
    parser.add_argument("--device", type=str, default="0", help="CUDA device index or 'cpu'")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("models/checkpoints"),
        help="Output directory for checkpoints",
    )
    args = parser.parse_args()

    try:
        train_mobility(
            data_yaml=args.data,
            epochs=args.epochs,
            batch_size=args.batch_size,
            img_size=args.img_size,
            weights=args.weights,
            seed=args.seed,
            device=args.device,
            output_dir=args.output_dir,
        )
        return 0
    except Exception as e:
        logger.error(f"Training failed: {e}", exc_info=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
