#!/usr/bin/env python3
"""NaviSense AI — CLI Hard Scan Runner.

Executes stationary Hard Scan on the laptop camera per PRD v3.2 Section 10 & 11.2:
1. Samples 10 frames over 2.0s (200ms intervals)
2. Runs fine-tuned Locate YOLO model on each frame
3. Associates detections across frames with IoU >= 0.30
4. Enforces PRD §11.2 confirmation: >= 6 of 10 frames with conf >= 0.60 in same zone
5. Persists accepted objects to SQLite object_memory.db
6. Displays the updated Object Memory snapshot
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from typing import Any, Optional

# Ensure repository root is on sys.path
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from laptop.config.settings import LaptopConfig, SpatialZone
from laptop.scanner import HardScanEngine, OpenCvFrameSource, YoloLocateDetector
from laptop.storage.db import DatabaseManager


class ImageFrameSource:
    """Simulates camera frames from a static image file for testing."""

    def __init__(self, image_path: Path):
        import cv2
        self.image_path = image_path
        self._img = cv2.imread(str(image_path))
        if self._img is None:
            raise FileNotFoundError(f"Could not load image from: {image_path}")

    def capture_frame(self) -> Optional[Any]:
        return self._img.copy()

    def release(self):
        pass


def main():
    parser = argparse.ArgumentParser(description="NaviSense Stationary Hard Scan Runner")
    parser.add_argument("--camera", type=int, default=0, help="Camera device index (default: 0)")
    parser.add_argument("--image", type=str, default=None, help="Optional image file path to simulate camera")
    parser.add_argument("--db", type=str, default="laptop/object_memory.db", help="Path to SQLite DB")
    parser.add_argument("--model", type=str, default="models/locate/locate_best.pt", help="Path to model weights (.pt)")
    parser.add_argument("--conf", type=float, default=0.60, help="Confidence threshold (PRD §11.2: 0.60)")
    parser.add_argument("--area", type=str, default="hackathon_hall", help="Area name (default: hackathon_hall)")
    args = parser.parse_args()

    # Anchor paths to REPO_ROOT if relative
    db_path = Path(args.db)
    if not db_path.is_absolute():
        db_path = REPO_ROOT / db_path

    model_path = Path(args.model)
    if not model_path.is_absolute():
        model_path = REPO_ROOT / model_path

    print("=" * 70)
    print("NaviSense Stationary Hard Scan Runner (PRD v3.2 Section 10)")
    print("=" * 70)
    print(f"Repository Root: {REPO_ROOT}")
    if args.image:
        print(f"Input Source   : Image File ({args.image})")
    else:
        print(f"Input Source   : Webcam Device {args.camera}")
    print(f"Model Path     : {model_path}")
    print(f"Database Path  : {db_path}")
    print(f"Min Confidence : {args.conf}")
    print(f"Target Area    : {args.area}")
    print("=" * 70)

    # Initialize components
    config = LaptopConfig(
        camera_device_index=args.camera,
        detection_confidence_threshold=args.conf,
    )
    db = DatabaseManager(str(db_path))

    print("\n[1/4] Initializing Camera and YOLO Detector...")
    if args.image:
        img_p = Path(args.image)
        if not img_p.is_absolute():
            img_p = REPO_ROOT / img_p
        frame_source = ImageFrameSource(img_p)
    else:
        frame_source = OpenCvFrameSource(camera_index=args.camera)

    detector = YoloLocateDetector(model_path=str(model_path), conf_threshold=args.conf)

    engine = HardScanEngine(
        db_manager=db,
        config=config,
        frame_source=frame_source,
        detector=detector,
    )

    print("[2/4] Executing 2.0s Hard Scan (10 frames @ 200ms)...")
    start_t = time.time()
    result = engine.execute_scan(area=args.area)
    elapsed = time.time() - start_t

    # Release camera
    frame_source.release()

    print(f"[3/4] Scan Completed in {elapsed:.2f}s | Success: {result.success}")
    if not result.success:
        print(f"\nERROR: Scan failed: {result.error_message}")
        if "Capture failed" in str(result.error_message) and not args.image:
            print("\n" + "!" * 70)
            print("NOTE: On macOS, terminal apps require Camera permission to access the webcam:")
            print("  1. Open System Settings -> Privacy & Security -> Camera")
            print("  2. Ensure your Terminal / iTerm / VS Code / Cursor is toggled ON.")
            print("  3. Alternatively, you can test Hard Scan on a static image via:")
            print(f"     python3 laptop/tools/run_hard_scan.py --image models/reference_images/locate_ref_table.png")
            print("!" * 70 + "\n")
        sys.exit(1)

    print(f"  Scan ID         : {result.scan_id}")
    print(f"  Frames Sampled  : {result.sampled_frames}/10")
    print(f"  Accepted Objects: {len(result.accepted_observations)}")

    if result.accepted_observations:
        print("\n  --- Accepted Objects (PRD §11.2: >= 6 frames in same zone) ---")
        for idx, obs in enumerate(result.accepted_observations, start=1):
            print(
                f"  [{idx}] {obs.object_name.upper():<8} | Zone: {obs.zone:<12} | "
                f"Conf: {obs.confidence:.2f} | Seen: {obs.seen_frames}/{obs.sampled_frames} frames | "
                f"Pos: ({obs.relative_x:.2f}, {obs.relative_y:.2f})"
            )
    else:
        print("\n  No objects met the PRD §11.2 persistence criteria (conf >= 0.60 in >= 6/10 frames).")

    print("\n[4/4] Current Object Memory in SQLite:")
    print("-" * 70)
    for class_name in ["keys", "wallet"]:
        query_status, records = db.query_latest_scan(class_name, active_camera_profile_version=engine.active_camera_profile_version)
        if records:
            for r in records:
                age = db.compute_age_seconds(r.last_seen)
                stale_str = " (STALE)" if age > config.stale_threshold_seconds else ""
                print(
                    f"  {r.object_name.upper():<8} -> Zone: {r.zone:<12} | "
                    f"Conf: {r.confidence:.2f} | Last Seen: {r.last_seen} (age: {age}s){stale_str} | Status: {query_status.upper()}"
                )
        else:
            print(f"  {class_name.upper():<8} -> Not in active memory (status: {query_status})")
    print("-" * 70)
    print("Hard Scan completed successfully.\n")


if __name__ == "__main__":
    main()
