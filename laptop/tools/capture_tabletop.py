"""Interactive Laptop Tabletop Dataset Capture Tool.

Collects tabletop dataset for Spandan (AC-02) per datasets/CAPTURE_CHECKLIST.md:
- >= 50 labeled instances of 'keys'
- >= 50 labeled instances of 'wallet'
- >= 20 negative frames (empty / non-target clutter)
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

try:
    import cv2
except ImportError:
    cv2 = None


def draw_guidelines(frame, zones):
    """Draw spatial zone overlay boundaries on the video preview frame."""
    if cv2 is None:
        return frame
    h, w = frame.shape[:2]
    # Draw vertical separator lines at 33% and 66% width
    x_33 = int(w * 0.33)
    x_66 = int(w * 0.66)

    overlay = frame.copy()
    cv2.line(overlay, (x_33, 0), (x_33, h), (0, 255, 255), 2)
    cv2.line(overlay, (x_66, 0), (x_66, h), (0, 255, 255), 2)

    cv2.putText(overlay, "LEFT ZONE", (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
    cv2.putText(overlay, "CENTER ZONE", (x_33 + 20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
    cv2.putText(overlay, "RIGHT ZONE", (x_66 + 20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)

    return cv2.addWeighted(overlay, 0.8, frame, 0.2, 0)


def save_labeled_sample(
    image,
    output_dir: Path,
    session_id: str,
    sample_idx: int,
    class_id: Optional[int],
    box_norm: Optional[tuple] = None,
) -> Path:
    """Save captured image and corresponding YOLO .txt annotation file."""
    images_dir = output_dir / session_id / "images"
    labels_dir = output_dir / session_id / "labels"
    images_dir.mkdir(parents=True, exist_ok=True)
    labels_dir.mkdir(parents=True, exist_ok=True)

    base_name = f"{session_id}_{sample_idx:04d}"
    img_path = images_dir / f"{base_name}.jpg"
    lbl_path = labels_dir / f"{base_name}.txt"

    if cv2 is not None and image is not None:
        cv2.imwrite(str(img_path), image)
    else:
        # Placeholder for mock / headless mode
        img_path.write_bytes(b"\xff\xd8\xff\xe0" + b"\x00" * 100)

    if class_id is None:
        # Negative frame per CAPTURE_CHECKLIST.md §4: 0-byte .txt file
        lbl_path.write_text("")
    else:
        # YOLO format: <class_id> <cx> <cy> <w> <h>
        cx, cy, bw, bh = box_norm if box_norm else (0.5, 0.5, 0.2, 0.2)
        lbl_path.write_text(f"{class_id} {cx:.6f} {cy:.6f} {bw:.6f} {bh:.6f}\n")

    return img_path


def run_capture(
    camera_index: int = 0,
    output_dir: str = "datasets/raw/laptop",
    session_id: str = "SES_01_LAPTOP_WOOD",
    mock_mode: bool = False,
):
    out_path = Path(output_dir)
    print("=" * 70)
    print("NaviSense Tabletop Capture Tool (Phase 1 Preparation for Spandan)")
    print("=" * 70)
    print(f"Session ID  : {session_id}")
    print(f"Output Path : {out_path.resolve()}")
    print("Controls:")
    print("  [k] : Capture frame for 'keys' (class 0)")
    print("  [w] : Capture frame for 'wallet' (class 1)")
    print("  [n] : Capture negative frame (clean table/non-target items, 0-byte label)")
    print("  [s] : Change session ID")
    print("  [q] : Quit")
    print("=" * 70)

    if mock_mode or cv2 is None:
        print("[MOCK MODE] Mock capture mode active (cv2 unavailable or mock requested).")
        print("Saving 3 test sample files to verify pipeline...")
        save_labeled_sample(None, out_path, session_id, 1, 0, (0.5, 0.5, 0.15, 0.15))
        save_labeled_sample(None, out_path, session_id, 2, 1, (0.3, 0.4, 0.20, 0.20))
        save_labeled_sample(None, out_path, session_id, 3, None)
        print(f"Saved mock samples to {out_path / session_id}")
        return

    cap = cv2.VideoCapture(camera_index)
    if not cap.isOpened():
        print(f"Error: Could not open camera at index {camera_index}")
        return

    sample_idx = 1
    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                print("Failed to read from camera.")
                break

            display_frame = draw_guidelines(frame, None)
            cv2.putText(
                display_frame,
                f"Session: {session_id} | Samples: {sample_idx-1}",
                (20, display_frame.shape[0] - 20),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.6,
                (0, 255, 0),
                2,
            )

            cv2.imshow("NaviSense Tabletop Capture", display_frame)
            key = cv2.waitKey(1) & 0xFF

            if key == ord("q") or key == 27:  # 'q' or ESC
                break
            elif key == ord("k"):
                # Save keys (class 0)
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, class_id=0)
                print(f"[{sample_idx}] Saved KEYS sample: {path.name}")
                sample_idx += 1
            elif key == ord("w"):
                # Save wallet (class 1)
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, class_id=1)
                print(f"[{sample_idx}] Saved WALLET sample: {path.name}")
                sample_idx += 1
            elif key == ord("n"):
                # Save negative frame
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, class_id=None)
                print(f"[{sample_idx}] Saved NEGATIVE sample (0-byte label): {path.name}")
                sample_idx += 1
            elif key == ord("s"):
                new_session = input("\nEnter new session ID: ").strip()
                if new_session:
                    session_id = new_session
                    sample_idx = 1
                    print(f"Switched to session: {session_id}")

    finally:
        cap.release()
        cv2.destroyAllWindows()
        print("\nCapture session finished.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="NaviSense Tabletop Capture Tool")
    parser.add_argument("--camera", type=int, default=0, help="Camera device index")
    parser.add_argument("--output", type=str, default="datasets/raw/laptop", help="Output directory")
    parser.add_argument("--session", type=str, default="SES_01_LAPTOP_WOOD", help="Session ID")
    parser.add_argument("--mock", action="store_true", help="Run in mock mode without webcam")
    args = parser.parse_args()

    run_capture(
        camera_index=args.camera,
        output_dir=args.output,
        session_id=args.session,
        mock_mode=args.mock,
    )
