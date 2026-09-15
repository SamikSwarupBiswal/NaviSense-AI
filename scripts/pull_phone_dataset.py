#!/usr/bin/env python3
"""Pulls captured walking obstacle dataset from connected Android phone to local repository.

Usage:
  python scripts/pull_phone_dataset.py [--dest datasets/phone_walking]
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path


def find_adb() -> str:
    # Try environment path first
    local_app_data = os.environ.get("LOCALAPPDATA", "")
    default_adb = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
    if default_adb.exists():
        return str(default_adb)
    return "adb"


def pull_dataset(dest_dir: Path) -> int:
    adb = find_adb()
    remote_path = "/sdcard/Android/data/dev.navisense/files/captured_dataset"

    dest_dir.mkdir(parents=True, exist_ok=True)
    print(f"Connecting to Android device via adb: {adb}")
    print(f"Pulling from remote: {remote_path} -> {dest_dir.resolve()}")

    cmd = [adb, "pull", remote_path, str(dest_dir)]
    res = subprocess.run(cmd)

    if res.returncode == 0:
        # Count files
        images = list((dest_dir / "captured_dataset" / "images").glob("*.jpg")) if (dest_dir / "captured_dataset" / "images").exists() else []
        labels = list((dest_dir / "captured_dataset" / "labels").glob("*.txt")) if (dest_dir / "captured_dataset" / "labels").exists() else []
        print(f"\n[SUCCESS] Pulled {len(images)} images and {len(labels)} label files to {dest_dir.resolve()}")
        return 0
    else:
        print(f"\n[ERROR] Failed to pull dataset from device. Return code: {res.returncode}")
        return res.returncode


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Pull captured walking obstacle dataset from phone.")
    parser.add_argument(
        "--dest",
        type=Path,
        default=Path("datasets/phone_walking"),
        help="Local destination directory (default: datasets/phone_walking)",
    )
    args = parser.parse_args()
    sys.exit(pull_dataset(args.dest))
