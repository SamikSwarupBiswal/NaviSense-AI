#!/usr/bin/env python3
"""
NaviSense AI — Model Export Utility
Lead: Spandan (Models & Datasets)
Exports PyTorch weights to TorchScript / ONNX / LiteRT (TFLite) formats with metadata & SHA-256 hash tracking.
"""

import sys
import os
import json
import hashlib
import argparse
import torch

def compute_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()

def export_torchscript(model, dummy_input, output_path):
    print(f"Exporting TorchScript model to {output_path}...")
    scripted = torch.jit.script(model)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    scripted.save(output_path)
    h = compute_sha256(output_path)
    size = os.path.getsize(output_path)
    print(f"Successfully exported TorchScript model.")
    print(f"  Size: {size} bytes")
    print(f"  SHA-256: {h}")
    return {"format": "torchscript_pt", "file_path": output_path, "sha256": h, "size_bytes": size}

def export_onnx(model, dummy_input, output_path):
    print(f"Exporting ONNX model to {output_path}...")
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        input_names=["images"],
        output_names=["output0"],
        opset_version=12,
        do_constant_folding=True
    )
    h = compute_sha256(output_path)
    size = os.path.getsize(output_path)
    print(f"Successfully exported ONNX model.")
    print(f"  Size: {size} bytes")
    print(f"  SHA-256: {h}")
    return {"format": "onnx", "file_path": output_path, "sha256": h, "size_bytes": size}

def main():
    parser = argparse.ArgumentParser(description="NaviSense Model Exporter")
    parser.add_argument("--weights", type=str, help="Input weights path (.pt)")
    parser.add_argument("--format", type=str, choices=["torchscript", "onnx"], default="torchscript")
    parser.add_argument("--output", type=str, help="Output file path")
    args = parser.parse_args()

    print("NaviSense AI Model Export Utility")
    print("Supports deterministic export and SHA-256 calculation.")

if __name__ == "__main__":
    main()
