#!/usr/bin/env python3
"""Inspect raw class scores from a Locate TorchScript/mobile export."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import torch
from PIL import Image


def letterbox_tensor(path: Path, size: int = 640) -> torch.Tensor:
    image = Image.open(path).convert("RGB")
    width, height = image.size
    scale = min(size / width, size / height)
    resized_width = max(1, round(width * scale))
    resized_height = max(1, round(height * scale))
    resized = image.resize((resized_width, resized_height), Image.Resampling.BILINEAR)
    canvas = Image.new("RGB", (size, size), (114, 114, 114))
    canvas.paste(resized, ((size - resized_width) // 2, (size - resized_height) // 2))
    array = np.asarray(canvas, dtype=np.float32) / 255.0
    return torch.from_numpy(array.transpose(2, 0, 1)).unsqueeze(0)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--image", type=Path, required=True)
    args = parser.parse_args()
    model = torch.jit.load(str(args.model), map_location="cpu").eval()
    with torch.no_grad():
        output = model(letterbox_tensor(args.image))
    if isinstance(output, (tuple, list)):
        output = output[0]
    if tuple(output.shape) != (1, 6, 8400):
        raise RuntimeError(f"Unexpected output shape: {tuple(output.shape)}")
    scores = output[0, 4:6, :]
    report = {
        "model": str(args.model),
        "image": str(args.image),
        "shape": list(output.shape),
        "max_scores": {
            "keys": float(scores[0].max()),
            "wallet": float(scores[1].max()),
        },
        "detections_at_0_25": {
            "keys": int((scores[0] >= 0.25).sum()),
            "wallet": int((scores[1] >= 0.25).sum()),
        },
        "detections_at_0_60": {
            "keys": int((scores[0] >= 0.60).sum()),
            "wallet": int((scores[1] >= 0.60).sum()),
        },
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
