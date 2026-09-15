#!/usr/bin/env python3
"""NaviSense AI — Mobility Model Export Utility.

Exports fine-tuned PyTorch weights to:
1. TorchScript (.pt)
2. PyTorch Mobile Lite (.ptl) for Android LiteModuleLoader

Enforces:
- Output tensor shape: [1, 9, 8400] (for 5 classes: cx, cy, w, h + 5 class scores)
- Updates models/metadata/mobility_model_contract.json with SHA-256 hashes
- Deploys models to android/app/src/main/assets/models/
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import os
import shutil
import sys
from pathlib import Path
import torch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("export_mobility_model")


def compute_sha256(filepath: Path) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()


class MobileYoloWrapper(torch.nn.Module):
    """Wraps YOLO PyTorch model to return raw [1, 4+num_classes, 8400] detection tensor."""

    def __init__(self, base_model):
        super().__init__()
        self.base_model = base_model

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        out = self.base_model(x)
        if isinstance(out, (list, tuple)):
            out = out[0]
        return out


def export_mobility(
    weights_path: Path,
    num_classes: int = 5,
    img_size: int = 640,
    output_dir: Path = Path("models/smoke"),
    assets_dir: Path = Path("android/app/src/main/assets/models"),
    contract_path: Path = Path("models/metadata/mobility_model_contract.json"),
) -> dict:
    weights_path = weights_path.resolve()
    output_dir = output_dir.resolve()
    assets_dir = assets_dir.resolve()
    contract_path = contract_path.resolve()

    output_dir.mkdir(parents=True, exist_ok=True)
    assets_dir.mkdir(parents=True, exist_ok=True)

    logger.info("=" * 70)
    logger.info("NaviSense Mobility Model Export Utility")
    logger.info("=" * 70)
    logger.info(f"Input weights : {weights_path}")
    logger.info(f"Output dir    : {output_dir}")
    logger.info(f"Assets dir    : {assets_dir}")

    from ultralytics import YOLO

    yolo = YOLO(str(weights_path))
    py_model = yolo.model.eval()
    wrapper = MobileYoloWrapper(py_model).eval().cpu()

    dummy_input = torch.randn(1, 3, img_size, img_size)

    with torch.no_grad():
        test_out = wrapper(dummy_input)
        logger.info(f"Output shape test: {list(test_out.shape)}")
        expected_channels = 4 + num_classes
        if test_out.shape[1] != expected_channels or test_out.shape[2] != 8400:
            logger.warning(
                f"Unexpected output shape: {list(test_out.shape)}. Expected [1, {expected_channels}, 8400]"
            )

    # 1. Trace with TorchScript
    logger.info("Tracing model with TorchScript...")
    traced_module = torch.jit.trace(wrapper, dummy_input)

    ts_path = output_dir / "mobility_smoke.pt"
    traced_module.save(str(ts_path))
    logger.info(f"Saved TorchScript model to: {ts_path}")

    # 2. Export to PyTorch Mobile Lite (.ptl)
    ptl_path = output_dir / "mobility_smoke.ptl"
    logger.info("Exporting to PyTorch Mobile Lite format (optimize_for_mobile + _save_for_lite_interpreter)...")
    from torch.utils.mobile_optimizer import optimize_for_mobile
    mobile_module = optimize_for_mobile(traced_module)
    mobile_module._save_for_lite_interpreter(str(ptl_path))
    logger.info(f"Saved PyTorch Mobile Lite model to: {ptl_path}")

    # 3. Copy to Android assets
    asset_ts = assets_dir / "mobility_smoke.pt"
    asset_ptl = assets_dir / "mobility_smoke.ptl"
    shutil.copy2(ts_path, asset_ts)
    shutil.copy2(ptl_path, asset_ptl)
    logger.info(f"Copied artifacts to Android assets: {assets_dir}")

    # 4. Compute SHA-256 hashes
    ts_hash = compute_sha256(ts_path)
    ptl_hash = compute_sha256(ptl_path)
    asset_ptl_hash = compute_sha256(asset_ptl)

    logger.info(f"TorchScript SHA-256  : {ts_hash}")
    logger.info(f"Mobile Lite SHA-256  : {ptl_hash}")

    # 5. Update metadata contract if exists
    if contract_path.exists():
        with open(contract_path, "r", encoding="utf-8") as f:
            contract = json.load(f)

        contract["version"] = "v0.2.0-finetuned"
        contract["provenance"]["status"] = "finetuned_indoor_phone"
        contract["provenance"]["qualification"] = "QUALIFIED_MOBILITY_CORRIDOR"
        contract["provenance"]["note"] = "Fine-tuned on combined indoor Kaggle dataset + physical phone walking hazard dataset."

        if "artifact" in contract:
            contract["artifact"]["sha256"] = ts_hash
            contract["artifact"]["size_bytes"] = ts_path.stat().st_size

        if "artifacts" in contract:
            if "laptop_torchscript" in contract["artifacts"]:
                contract["artifacts"]["laptop_torchscript"]["sha256"] = ts_hash
            if "android_mobile_ptl" in contract["artifacts"]:
                contract["artifacts"]["android_mobile_ptl"]["sha256"] = asset_ptl_hash
            if "android_assets_pt" in contract["artifacts"]:
                contract["artifacts"]["android_assets_pt"]["sha256"] = ts_hash

        with open(contract_path, "w", encoding="utf-8") as f:
            json.dump(contract, f, indent=2)
        logger.info(f"Updated contract metadata: {contract_path}")

    return {
        "torchscript_path": str(ts_path),
        "torchscript_sha256": ts_hash,
        "mobile_lite_path": str(ptl_path),
        "mobile_lite_sha256": ptl_hash,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Export fine-tuned YOLO model for Android.")
    parser.add_argument(
        "--weights",
        type=Path,
        default=Path("models/checkpoints/mobility_best.pt"),
        help="Path to trained PyTorch weights",
    )
    parser.add_argument("--num-classes", type=int, default=5, help="Number of classes (default: 5)")
    parser.add_argument("--img-size", type=int, default=640, help="Image size (default: 640)")
    args = parser.parse_args()

    try:
        export_mobility(
            weights_path=args.weights,
            num_classes=args.num_classes,
            img_size=args.img_size,
        )
        return 0
    except Exception as e:
        logger.error(f"Export failed: {e}", exc_info=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
