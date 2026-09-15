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
def draw_guidelines(frame, sample_counts: dict):
    """Draw spatial zone overlay boundaries and hotkey guidance on preview frame."""
    if cv2 is None:
        return frame
    h, w = frame.shape[:2]
    x_33 = int(w * 0.33)
    x_66 = int(w * 0.66)

    overlay = frame.copy()
    # Zone lines
    cv2.line(overlay, (x_33, 0), (x_33, h), (0, 255, 255), 2)
    cv2.line(overlay, (x_66, 0), (x_66, h), (0, 255, 255), 2)

    # Zone labels
    cv2.putText(overlay, "LEFT ZONE", (20, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)
    cv2.putText(overlay, "CENTER ZONE", (x_33 + 20, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)
    cv2.putText(overlay, "RIGHT ZONE", (x_66 + 20, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)

    # Top stats bar
    keys_c = sample_counts.get("keys", 0)
    wall_c = sample_counts.get("wallet", 0)
    neg_c = sample_counts.get("neg", 0)
    both_c = sample_counts.get("both", 0)
    stats = f"Keys: {keys_c}/50 | Wallet: {wall_c}/50 | Both: {both_c} | Negatives: {neg_c}/20"
    cv2.putText(overlay, stats, (20, 65), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 255, 0), 2)

    # Bottom guidance overlay
    cv2.putText(overlay, "[k] Keys  [w] Wallet  [n] Negative", (20, h - 45), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
    cv2.putText(overlay, "[b] Both(K:Left,W:Right) [v] Both(K:Right,W:Left)", (20, h - 25), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)
    cv2.putText(overlay, "[c] Both(K:Ctr,W:Right)  [x] Both(K:Left,W:Ctr)  [q] Quit", (20, h - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 1)

    return cv2.addWeighted(overlay, 0.85, frame, 0.15, 0)


def save_labeled_sample(
    image,
    output_dir: Path,
    session_id: str,
    sample_idx: int,
    annotations: Optional[list] = None,
) -> Path:
    """Save captured image and corresponding YOLO .txt annotation file.
    
    annotations is a list of tuples: (class_id, (cx, cy, bw, bh))
    If annotations is None or empty: saves an empty 0-byte label file (negative frame).
    """
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

    if not annotations:
        # Negative frame per CAPTURE_CHECKLIST.md §4: 0-byte .txt file
        lbl_path.write_text("")
    else:
        lines = []
        for cls_id, (cx, cy, bw, bh) in annotations:
            lines.append(f"{cls_id} {cx:.6f} {cy:.6f} {bw:.6f} {bh:.6f}\n")
        lbl_path.write_text("".join(lines))

    return img_path


def detect_session_state(output_dir: Path, session_id: str) -> tuple[int, dict]:
    """Detect highest sample index and count existing annotations in session."""
    images_dir = output_dir / session_id / "images"
    labels_dir = output_dir / session_id / "labels"
    if not images_dir.exists():
        return 1, {"keys": 0, "wallet": 0, "both": 0, "neg": 0}

    indices = []
    prefix = f"{session_id}_"
    for img in images_dir.glob(f"{prefix}*.jpg"):
        stem = img.stem
        if stem.startswith(prefix):
            suffix = stem[len(prefix):]
            if suffix.isdigit():
                indices.append(int(suffix))

    max_idx = max(indices) if indices else 0
    next_idx = max_idx + 1

    counts = {"keys": 0, "wallet": 0, "both": 0, "neg": 0}
    if labels_dir.exists():
        for lbl in labels_dir.glob(f"{prefix}*.txt"):
            content = lbl.read_text().strip()
            if not content:
                counts["neg"] += 1
            else:
                lines = content.splitlines()
                classes = [line.split()[0] for line in lines if line.strip()]
                if "0" in classes and "1" in classes:
                    counts["both"] += 1
                elif "0" in classes:
                    counts["keys"] += 1
                elif "1" in classes:
                    counts["wallet"] += 1

    return next_idx, counts


def run_capture(
    camera_index: int = 0,
    output_dir: str = "datasets/raw/laptop",
    session_id: str = "SES_01_LAPTOP_WOOD",
    mock_mode: bool = False,
):
    out_path = Path(output_dir)
    print("=" * 75)
    print("NaviSense Tabletop Capture Tool (Phase 1 Preparation for Spandan)")
    print("=" * 75)
    print(f"Session ID  : {session_id}")
    print(f"Output Path : {out_path.resolve()}")
    print("\nControls:")
    print("  [k] : Capture frame for 'keys' only (class 0)")
    print("  [w] : Capture frame for 'wallet' only (class 1)")
    print("  --- DUAL OBJECT CAPTURES (Both Together) ---")
    print("  [b] : BOTH -> Keys in Left Zone, Wallet in Right Zone")
    print("  [v] : BOTH -> Keys in Right Zone, Wallet in Left Zone")
    print("  [c] : BOTH -> Keys in Center Zone, Wallet in Right Zone")
    print("  [x] : BOTH -> Keys in Left Zone, Wallet in Center Zone")
    print("  --- NEGATIVE / UTILITY ---")
    print("  [n] : Capture NEGATIVE frame (clean/clutter, 0-byte label)")
    print("  [s] : Change session ID")
    print("  [q] : Quit")
    print("=" * 75)

    sample_idx, counts = detect_session_state(out_path, session_id)
    if sample_idx > 1:
        print(f"[RESUME] Found existing {sample_idx - 1} frames. Resuming at index {sample_idx:04d}.")
        print(f"Current Counts: Keys={counts['keys'] + counts['both']} | Wallet={counts['wallet'] + counts['both']} | Both={counts['both']} | Negatives={counts['neg']}")

    if mock_mode or cv2 is None:
        print("[MOCK MODE] Mock capture mode active.")
        print("Saving test sample files (single, both, negative)...")
        # Single keys
        save_labeled_sample(None, out_path, session_id, sample_idx, [(0, (0.50, 0.55, 0.18, 0.18))])
        # Single wallet
        save_labeled_sample(None, out_path, session_id, sample_idx + 1, [(1, (0.50, 0.55, 0.20, 0.20))])
        # Both keys & wallet
        save_labeled_sample(
            None, out_path, session_id, sample_idx + 2,
            [(0, (0.20, 0.55, 0.18, 0.18)), (1, (0.80, 0.55, 0.20, 0.20))]
        )
        # Negative
        save_labeled_sample(None, out_path, session_id, sample_idx + 3, None)
        print(f"Saved mock samples to {out_path / session_id}")
        return

    cap = cv2.VideoCapture(camera_index)
    if not cap.isOpened():
        print(f"Error: Could not open camera at index {camera_index}")
        return

    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                print("Failed to read from camera.")
                break

            display_frame = draw_guidelines(frame, counts)
            cv2.imshow("NaviSense Tabletop Capture", display_frame)
            key = cv2.waitKey(1) & 0xFF

            if key == ord("q") or key == 27:  # 'q' or ESC
                break

            elif key == ord("k"):
                # Keys only (center)
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, [(0, (0.50, 0.55, 0.18, 0.18))])
                counts["keys"] += 1
                print(f"[{sample_idx}] Saved KEYS: {path.name} (Total keys: {counts['keys'] + counts['both']})")
                sample_idx += 1

            elif key == ord("w"):
                # Wallet only (center)
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, [(1, (0.50, 0.55, 0.20, 0.20))])
                counts["wallet"] += 1
                print(f"[{sample_idx}] Saved WALLET: {path.name} (Total wallet: {counts['wallet'] + counts['both']})")
                sample_idx += 1

            elif key == ord("b"):
                # BOTH: Keys Left, Wallet Right
                anns = [(0, (0.20, 0.55, 0.18, 0.18)), (1, (0.80, 0.55, 0.20, 0.20))]
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, anns)
                counts["both"] += 1
                print(f"[{sample_idx}] Saved BOTH (Keys:Left, Wallet:Right): {path.name}")
                sample_idx += 1

            elif key == ord("v"):
                # BOTH: Keys Right, Wallet Left
                anns = [(0, (0.80, 0.55, 0.18, 0.18)), (1, (0.20, 0.55, 0.20, 0.20))]
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, anns)
                counts["both"] += 1
                print(f"[{sample_idx}] Saved BOTH (Keys:Right, Wallet:Left): {path.name}")
                sample_idx += 1

            elif key == ord("c"):
                # BOTH: Keys Center, Wallet Right
                anns = [(0, (0.48, 0.55, 0.18, 0.18)), (1, (0.82, 0.55, 0.20, 0.20))]
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, anns)
                counts["both"] += 1
                print(f"[{sample_idx}] Saved BOTH (Keys:Center, Wallet:Right): {path.name}")
                sample_idx += 1

            elif key == ord("x"):
                # BOTH: Keys Left, Wallet Center
                anns = [(0, (0.18, 0.55, 0.18, 0.18)), (1, (0.52, 0.55, 0.20, 0.20))]
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, anns)
                counts["both"] += 1
                print(f"[{sample_idx}] Saved BOTH (Keys:Left, Wallet:Center): {path.name}")
                sample_idx += 1

            elif key == ord("n"):
                # Negative frame
                path = save_labeled_sample(frame, out_path, session_id, sample_idx, None)
                counts["neg"] += 1
                print(f"[{sample_idx}] Saved NEGATIVE (Clean Table): {path.name} (Total neg: {counts['neg']})")
                sample_idx += 1

            elif key == ord("s"):
                new_session = input("\nEnter new session ID: ").strip()
                if new_session:
                    session_id = new_session
                    sample_idx, counts = detect_session_state(out_path, session_id)
                    print(f"Switched to session: {session_id} (resuming at sample {sample_idx:04d})")

    finally:
        cap.release()
        cv2.destroyAllWindows()
        print("\nCapture session finished.")
        total_k = counts["keys"] + counts["both"]
        total_w = counts["wallet"] + counts["both"]
        print(f"Summary -> Keys: {total_k}/50 | Wallets: {total_w}/50 | Negatives: {counts['neg']}/20")


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

