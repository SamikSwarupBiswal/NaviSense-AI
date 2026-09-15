#!/usr/bin/env python3
"""Dataset preparation script for multi-class Locate + Obstacle detection.

Combines:
  1. datasets/locate_roboflow (keys, wallet)
  2. C:\\Users\\Samik\\Downloads\\archive.zip (chair, table, couch, door, openedDoor)

Target Unified Classes:
  0: keys
  1: wallet
  2: chair
  3: table
  4: couch
  5: door
"""

import os
import shutil
import zipfile
from pathlib import Path
from collections import Counter

ARCHIVE_ZIP = Path(r"C:\Users\Samik\Downloads\archive.zip")
LOCATE_ROBOFLOW = Path("datasets/locate_roboflow")
OUTPUT_DIR = Path("datasets/locate_obstacle_combined")

# Remapping for archive.zip classes:
# 0: door -> 5
# 4: chair -> 2
# 5: table -> 3
# 7: couch -> 4
# 8: openedDoor -> 5
ARCHIVE_REMAP = {
    0: 5,  # door
    4: 2,  # chair
    5: 3,  # table
    7: 4,  # couch
    8: 5,  # openedDoor
}

CLASS_NAMES = ["keys", "wallet", "chair", "table", "couch", "door"]


def prepare_dataset():
    print(f"Preparing unified Locate + Obstacle dataset in {OUTPUT_DIR}...")
    if OUTPUT_DIR.exists():
        shutil.rmtree(OUTPUT_DIR)

    for split in ["train", "valid", "test"]:
        (OUTPUT_DIR / split / "images").mkdir(parents=True, exist_ok=True)
        (OUTPUT_DIR / split / "labels").mkdir(parents=True, exist_ok=True)

    counts = Counter()

    # 1. Ingest datasets/locate_roboflow
    print("Ingesting datasets/locate_roboflow (keys, wallet)...")
    for split in ["train", "valid", "test"]:
        img_dir = LOCATE_ROBOFLOW / split / "images"
        lbl_dir = LOCATE_ROBOFLOW / split / "labels"
        if not img_dir.exists():
            continue

        for img_path in img_dir.glob("*.*"):
            dest_img = OUTPUT_DIR / split / "images" / f"locate_{img_path.name}"
            shutil.copy2(img_path, dest_img)

            lbl_path = lbl_dir / f"{img_path.stem}.txt"
            dest_lbl = OUTPUT_DIR / split / "labels" / f"locate_{img_path.stem}.txt"
            if lbl_path.exists():
                lines = lbl_path.read_text(encoding="utf-8").strip().splitlines()
                out_lines = []
                for line in lines:
                    parts = line.strip().split()
                    if not parts:
                        continue
                    cid = int(parts[0])
                    # keys=0, wallet=1 (already matches)
                    if cid in (0, 1):
                        out_lines.append(line)
                        counts[(split, CLASS_NAMES[cid])] += 1
                dest_lbl.write_text("\n".join(out_lines) + "\n", encoding="utf-8")
            else:
                dest_lbl.write_text("", encoding="utf-8")

    # 2. Ingest C:\Users\Samik\Downloads\archive.zip
    print(f"Ingesting {ARCHIVE_ZIP} (chair, table, couch, door)...")
    with zipfile.ZipFile(ARCHIVE_ZIP, "r") as z:
        for split in ["train", "valid", "test"]:
            label_prefix = f"{split}/labels/"
            label_files = [f for f in z.namelist() if f.startswith(label_prefix) and f.endswith(".txt")]

            for lbl_file in label_files:
                content = z.read(lbl_file).decode("utf-8").strip().splitlines()
                remapped_lines = []
                for line in content:
                    parts = line.strip().split()
                    if not parts:
                        continue
                    src_cid = int(parts[0])
                    if src_cid in ARCHIVE_REMAP:
                        target_cid = ARCHIVE_REMAP[src_cid]
                        remapped_line = f"{target_cid} " + " ".join(parts[1:])
                        remapped_lines.append(remapped_line)
                        counts[(split, CLASS_NAMES[target_cid])] += 1

                if not remapped_lines:
                    # Skip scenes without any relevant obstacle
                    continue

                stem = Path(lbl_file).stem
                # Find matching image in archive
                img_candidates = [
                    f"{split}/images/{stem}.jpg",
                    f"{split}/images/{stem}.jpeg",
                    f"{split}/images/{stem}.png",
                ]
                matched_img = None
                for c in img_candidates:
                    if c in z.namelist():
                        matched_img = c
                        break

                if not matched_img:
                    continue

                dest_img_name = f"obs_{Path(matched_img).name}"
                dest_lbl_name = f"obs_{stem}.txt"

                # Extract image
                with open(OUTPUT_DIR / split / "images" / dest_img_name, "wb") as f_out:
                    f_out.write(z.read(matched_img))

                # Write remapped label
                with open(OUTPUT_DIR / split / "labels" / dest_lbl_name, "w", encoding="utf-8") as f_out:
                    f_out.write("\n".join(remapped_lines) + "\n")

    # 3. Create data.yaml
    data_yaml = OUTPUT_DIR / "data.yaml"
    abs_path = OUTPUT_DIR.resolve().as_posix()
    yaml_content = f"""path: {abs_path}
train: train/images
val: valid/images
test: test/images

nc: 6
names:
  0: keys
  1: wallet
  2: chair
  3: table
  4: couch
  5: door
"""
    data_yaml.write_text(yaml_content, encoding="utf-8")
    print(f"data.yaml written to {data_yaml}")

    print("\nDataset preparation complete! Box counts per split:")
    for split in ["train", "valid", "test"]:
        total_imgs = len(list((OUTPUT_DIR / split / "images").glob("*.*")))
        print(f"\n--- {split.upper()} ({total_imgs} images) ---")
        for cname in CLASS_NAMES:
            c = counts.get((split, cname), 0)
            print(f"  {cname:10s}: {c} boxes")


if __name__ == "__main__":
    prepare_dataset()
