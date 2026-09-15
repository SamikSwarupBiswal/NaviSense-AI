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
    candidate_paths = [
        "/sdcard/Android/data/dev.navisense.debug/files/captured_dataset",
        "/sdcard/Android/data/dev.navisense/files/captured_dataset",
    ]

    dest_dir.mkdir(parents=True, exist_ok=True)
    print(f"Connecting to Android device via adb: {adb}")

    for remote_path in candidate_paths:
        # Check if remote path exists
        check = subprocess.run([adb, "shell", f"ls {remote_path}"], capture_output=True, text=True)
        if check.returncode == 0:
            print(f"Found remote dataset at: {remote_path}")
            print(f"Pulling from remote: {remote_path} -> {dest_dir.resolve()}")
            cmd = [adb, "pull", remote_path, str(dest_dir)]
            res = subprocess.run(cmd)
            if res.returncode == 0:
                images = list(dest_dir.rglob("*.jpg"))
                labels = list(dest_dir.rglob("*.txt"))
                print(f"\n[SUCCESS] Pulled {len(images)} images and {len(labels)} label files to {dest_dir.resolve()}")
                return 0

    print("\n[ERROR] No captured_dataset directory found under dev.navisense.debug or dev.navisense.")
    return 1


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
