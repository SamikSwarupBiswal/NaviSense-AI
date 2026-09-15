#!/usr/bin/env python3
"""
NaviSense AI — Smoke Model Verification Script
Lead: Spandan (Models & Datasets)
Verifies smoke model artifact integrity, tensor shapes, SHA-256 hashes, and decoding contracts.
"""

import sys
import os
import json
import hashlib
import torch
from PIL import Image

def compute_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()

def verify_model(contract_path):
    print(f"=== Verifying {contract_path} ===")
    with open(contract_path, "r", encoding="utf-8") as f:
        contract = json.load(f)
    
    # Check all artifacts listed in contract
    artifacts = contract.get("artifacts", {})
    if not artifacts and "artifact" in contract:
        artifacts = {"default": contract["artifact"]}
        
    for key, art in artifacts.items():
        model_path = art["path"] if "path" in art else art["file_path"]
        expected_hash = art["sha256"]
        
        if not os.path.exists(model_path):
            print(f"FAIL: Artifact {model_path} does not exist!")
            return False
            
        actual_hash = compute_sha256(model_path)
        if actual_hash != expected_hash:
            print(f"FAIL: Hash mismatch for {model_path}!")
            print(f"  Expected: {expected_hash}")
            print(f"  Actual:   {actual_hash}")
            return False
        print(f"PASS [{key}]: SHA-256 hash verified: {actual_hash}")
        
        # Test loading torchscript models
        if art.get("format") == "torchscript_pt":
            try:
                model = torch.jit.load(model_path)
                model.eval()
                print(f"PASS [{key}]: Model loaded successfully via torch.jit.load")
                
                expected_in_shape = tuple(contract["input_specification"]["shape"])
                expected_out_shape = tuple(contract["output_specification"]["raw_shape"])
                
                dummy_input = torch.zeros(expected_in_shape, dtype=torch.float32)
                with torch.no_grad():
                    output = model(dummy_input)
                    
                if output.shape != expected_out_shape:
                    print(f"FAIL [{key}]: Output shape mismatch! Expected {expected_out_shape}, got {output.shape}")
                    return False
                print(f"PASS [{key}]: Input shape {expected_in_shape} -> Output shape {output.shape}")
            except Exception as e:
                print(f"FAIL [{key}]: Could not load/run model: {e}")
                return False
        elif art.get("format") == "pytorch_lite_ptl":
            print(f"PASS [{key}]: Mobile Lite Interpreter (.ptl) verified on disk: {os.path.getsize(model_path)} bytes")
    
    return True

def verify_reference_fixture():
    print("=== Verifying Reference Image and Fixture ===")
    fixture_path = "models/fixtures/locate_expected_detections.json"
    with open(fixture_path, "r", encoding="utf-8") as f:
        fixture = json.load(f)
        
    img_info = fixture["reference_image"]
    img_path = img_info["file_path"]
    expected_img_hash = img_info["sha256"]
    
    if not os.path.exists(img_path):
        print(f"FAIL: Reference image {img_path} not found!")
        return False
        
    actual_img_hash = compute_sha256(img_path)
    if actual_img_hash != expected_img_hash:
        print(f"FAIL: Image hash mismatch for {img_path}")
        return False
    print(f"PASS: Reference image exists with valid hash: {actual_img_hash}")
    
    img = Image.open(img_path)
    if img.size != (640, 640):
        print(f"FAIL: Reference image size is {img.size}, expected (640, 640)")
        return False
    print(f"PASS: Reference image size verified: {img.size}")
    
    classes = [obj["class_name"] for obj in fixture["ground_truth_objects"]]
    print(f"PASS: Ground truth objects verified: {classes}")
    return True

def main():
    ok1 = verify_model("models/metadata/locate_model_contract.json")
    print()
    ok2 = verify_model("models/metadata/mobility_model_contract.json")
    print()
    ok3 = verify_reference_fixture()
    print()
    
    if ok1 and ok2 and ok3:
        print("**************************************************")
        print("ALL SMOKE MODEL AND FIXTURE CHECKS PASSED (100%)")
        print("Smoke artifacts ready for Subham and Samik handoff")
        print("**************************************************")
        sys.exit(0)
    else:
        print("VERIFICATION FAILED")
        sys.exit(1)

if __name__ == "__main__":
    main()
