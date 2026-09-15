#!/usr/bin/env python3
"""Build a leakage-resistant Locate dataset from the two approved archives.

The script validates YOLO labels, removes exact/cross-export visual duplicates,
keeps capture groups in one split, and records provenance in JSON manifests.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import random
import re
import shutil
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

from PIL import Image


CANONICAL_CLASSES = {0: "keys", 1: "wallet"}
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png"}


@dataclass
class Sample:
    source: str
    archive: Path
    image_entry: str
    label_entry: str
    original_split: str
    source_name: str
    image_sha256: str
    difference_hash: int
    capture_group: str
    viewpoint: str
    width: int
    height: int
    labels: list[tuple[int, float, float, float, float]]
    split: str = ""
    output_stem: str = ""

    @property
    def negative(self) -> bool:
        return not self.labels


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def difference_hash(image: Image.Image) -> int:
    small = image.convert("L").resize((9, 8), Image.Resampling.LANCZOS)
    pixels = list(small.getdata())
    value = 0
    for row in range(8):
        offset = row * 9
        for col in range(8):
            value = (value << 1) | int(pixels[offset + col] > pixels[offset + col + 1])
    return value


def hamming_distance(left: int, right: int) -> int:
    return (left ^ right).bit_count()


def normalize_stem(filename: str) -> str:
    stem = Path(filename).stem
    return re.sub(r"\.rf\.[0-9a-f]+$", "", stem, flags=re.IGNORECASE)


def capture_group_for(filename: str) -> str:
    stem = normalize_stem(filename)
    # Keep a full SES/viewpoint batch together instead of splitting adjacent frames.
    session = re.match(r"(SES_\d+_[A-Z]+_[A-Z]+)", stem, flags=re.IGNORECASE)
    if session:
        return session.group(1).upper()
    named_view = re.match(r"((?:WEARABLE|SECURITY)[A-Z0-9_-]*?)(?:_\d+)?$", stem, flags=re.IGNORECASE)
    if named_view:
        return named_view.group(1).upper()
    # Phone IMG timestamps are grouped by capture minute; BURST frames stay together.
    timestamp = re.match(r"IMG(\d{12})", stem, flags=re.IGNORECASE)
    if timestamp:
        return f"IMG_{timestamp.group(1)}"
    return re.sub(r"(?:_BURST)?\d+$", "", stem, flags=re.IGNORECASE).upper()


def viewpoint_for(filename: str) -> str:
    upper = filename.upper()
    if "WEARABLE" in upper or "PHONE" in upper:
        return "phone"
    if "SECURITY" in upper or "LAPTOP" in upper or "CCTV" in upper:
        return "stationary"
    return "unknown"


def parse_labels(content: str, label_name: str) -> list[tuple[int, float, float, float, float]]:
    parsed: list[tuple[int, float, float, float, float]] = []
    for line_number, raw_line in enumerate(content.splitlines(), start=1):
        line = raw_line.strip()
        if not line:
            continue
        parts = line.split()
        try:
            class_id = int(parts[0])
            raw_coordinates = tuple(float(value) for value in parts[1:])
        except ValueError as error:
            raise ValueError(f"{label_name}:{line_number}: invalid numeric value") from error
        if class_id not in CANONICAL_CLASSES:
            raise ValueError(f"{label_name}:{line_number}: unsupported class id {class_id}")
        if len(parts) == 5:
            coordinates = raw_coordinates
        elif len(parts) >= 7 and len(raw_coordinates) % 2 == 0:
            # Roboflow may export polygon segments even for a detection project.
            # Convert each normalized polygon to its tight axis-aligned box.
            xs = raw_coordinates[0::2]
            ys = raw_coordinates[1::2]
            if not all(0.0 <= value <= 1.0 for value in raw_coordinates):
                raise ValueError(f"{label_name}:{line_number}: polygon coordinates must be within 0..1")
            left, right = min(xs), max(xs)
            top, bottom = min(ys), max(ys)
            coordinates = (
                (left + right) / 2.0,
                (top + bottom) / 2.0,
                right - left,
                bottom - top,
            )
        else:
            raise ValueError(
                f"{label_name}:{line_number}: expected a box or polygon, got {len(parts)} fields"
            )
        x_center, y_center, width, height = coordinates
        if not all(0.0 <= value <= 1.0 for value in coordinates):
            raise ValueError(f"{label_name}:{line_number}: coordinates must be within 0..1")
        if width <= 0.0 or height <= 0.0:
            raise ValueError(f"{label_name}:{line_number}: width and height must be positive")
        if x_center - width / 2 < -1e-6 or x_center + width / 2 > 1.0 + 1e-6:
            raise ValueError(f"{label_name}:{line_number}: horizontal box exceeds image")
        if y_center - height / 2 < -1e-6 or y_center + height / 2 > 1.0 + 1e-6:
            raise ValueError(f"{label_name}:{line_number}: vertical box exceeds image")
        parsed.append((class_id, x_center, y_center, width, height))
    return parsed


def read_archive(path: Path, source: str) -> list[Sample]:
    samples: list[Sample] = []
    with zipfile.ZipFile(path) as archive:
        entries = {entry.filename: entry for entry in archive.infolist() if not entry.is_dir()}
        image_entries = sorted(
            name for name in entries
            if Path(name).suffix.lower() in IMAGE_SUFFIXES and "/images/" in name.replace("\\", "/")
        )
        for image_entry in image_entries:
            normalized = image_entry.replace("\\", "/")
            split = normalized.split("/", 1)[0].lower()
            label_entry = re.sub(r"/images/", "/labels/", normalized, count=1)
            label_entry = str(Path(label_entry).with_suffix(".txt")).replace("\\", "/")
            if label_entry not in entries:
                raise ValueError(f"Missing label for {image_entry}: expected {label_entry}")
            image_bytes = archive.read(image_entry)
            label_text = archive.read(label_entry).decode("utf-8-sig")
            try:
                with Image.open(io.BytesIO(image_bytes)) as image:
                    image.load()
                    width, height = image.size
                    dhash = difference_hash(image)
            except Exception as error:
                raise ValueError(f"Unreadable image {image_entry}: {error}") from error
            source_name = Path(image_entry).name
            samples.append(
                Sample(
                    source=source,
                    archive=path,
                    image_entry=image_entry,
                    label_entry=label_entry,
                    original_split=split,
                    source_name=source_name,
                    image_sha256=sha256_bytes(image_bytes),
                    difference_hash=dhash,
                    capture_group=f"{source}:{capture_group_for(source_name)}",
                    viewpoint=viewpoint_for(source_name),
                    width=width,
                    height=height,
                    labels=parse_labels(label_text, label_entry),
                )
            )
    return samples


def deduplicate(samples: list[Sample]) -> tuple[list[Sample], list[dict]]:
    # Prefer the 640px baseline representation when the same scene exists in both exports.
    ordered = sorted(samples, key=lambda item: (item.source != "baseline_255", -item.width * item.height, item.source_name))
    kept: list[Sample] = []
    removed: list[dict] = []
    exact: dict[str, Sample] = {}
    for sample in ordered:
        duplicate = exact.get(sample.image_sha256)
        reason = "exact_sha256" if duplicate else None
        if duplicate is None:
            normalized = normalize_stem(sample.source_name).lower()
            for candidate in kept:
                # Cross-export duplicate: same original stem or virtually identical perceptual hash.
                same_stem = normalized == normalize_stem(candidate.source_name).lower()
                visually_same = sample.source != candidate.source and hamming_distance(
                    sample.difference_hash, candidate.difference_hash
                ) <= 1
                if same_stem or visually_same:
                    duplicate = candidate
                    reason = "same_source_stem" if same_stem else "cross_export_dhash_le_1"
                    break
        if duplicate is not None:
            removed.append({
                "removed": f"{sample.source}:{sample.image_entry}",
                "kept": f"{duplicate.source}:{duplicate.image_entry}",
                "reason": reason,
            })
            continue
        kept.append(sample)
        exact[sample.image_sha256] = sample
    return kept, removed


def assign_splits(samples: list[Sample], seed: int) -> None:
    groups: dict[str, list[Sample]] = defaultdict(list)
    for sample in samples:
        groups[sample.capture_group].append(sample)
    group_names = list(groups)
    random.Random(seed).shuffle(group_names)
    group_names.sort(key=lambda name: len(groups[name]), reverse=True)
    targets = {"train": 0.70 * len(samples), "valid": 0.15 * len(samples), "test": 0.15 * len(samples)}
    assigned = Counter()
    for group_name in group_names:
        # Put the next complete capture group into the most underfilled split.
        split = max(targets, key=lambda name: (targets[name] - assigned[name]) / max(targets[name], 1.0))
        for sample in groups[group_name]:
            sample.split = split
        assigned[split] += len(groups[group_name])


def safe_output_path(output: Path) -> Path:
    resolved = output.resolve()
    if resolved.exists():
        raise FileExistsError(f"Output already exists; choose a new versioned path: {resolved}")
    return resolved


def write_dataset(samples: list[Sample], removed: list[dict], output: Path, seed: int) -> None:
    output = safe_output_path(output)
    for split in ("train", "valid", "test"):
        (output / split / "images").mkdir(parents=True)
        (output / split / "labels").mkdir(parents=True)

    archive_handles: dict[Path, zipfile.ZipFile] = {}
    try:
        source_manifest = []
        for index, sample in enumerate(sorted(samples, key=lambda item: (item.split, item.source, item.source_name))):
            prefix = "base" if sample.source == "baseline_255" else "add"
            sample.output_stem = f"{prefix}_{index:04d}_{normalize_stem(sample.source_name)}"
            image_suffix = Path(sample.source_name).suffix.lower()
            image_output = output / sample.split / "images" / f"{sample.output_stem}{image_suffix}"
            label_output = output / sample.split / "labels" / f"{sample.output_stem}.txt"
            archive = archive_handles.get(sample.archive)
            if archive is None:
                archive = zipfile.ZipFile(sample.archive)
                archive_handles[sample.archive] = archive
            with archive.open(sample.image_entry) as source_stream, image_output.open("wb") as destination:
                shutil.copyfileobj(source_stream, destination)
            label_lines = [
                f"{class_id} {x:.8f} {y:.8f} {width:.8f} {height:.8f}"
                for class_id, x, y, width, height in sample.labels
            ]
            label_output.write_text("\n".join(label_lines) + ("\n" if label_lines else ""), encoding="utf-8")
            source_manifest.append({
                "output_image": image_output.relative_to(output).as_posix(),
                "output_label": label_output.relative_to(output).as_posix(),
                "source_dataset": sample.source,
                "source_archive": str(sample.archive),
                "source_image": sample.image_entry,
                "source_label": sample.label_entry,
                "source_image_sha256": sample.image_sha256,
                "capture_group": sample.capture_group,
                "viewpoint": sample.viewpoint,
                "original_split": sample.original_split,
                "split": sample.split,
                "width": sample.width,
                "height": sample.height,
                "negative": sample.negative,
                "classes": [CANONICAL_CLASSES[label[0]] for label in sample.labels],
            })
    finally:
        for archive in archive_handles.values():
            archive.close()

    split_counts = {}
    for split in ("train", "valid", "test"):
        split_samples = [sample for sample in samples if sample.split == split]
        class_counts = Counter(CANONICAL_CLASSES[label[0]] for sample in split_samples for label in sample.labels)
        split_counts[split] = {
            "images": len(split_samples),
            "keys_instances": class_counts["keys"],
            "wallet_instances": class_counts["wallet"],
            "negative_images": sum(sample.negative for sample in split_samples),
            "capture_groups": len({sample.capture_group for sample in split_samples}),
            "viewpoints": dict(Counter(sample.viewpoint for sample in split_samples)),
        }

    data_yaml = (
        f"path: {output.as_posix()}\n"
        "train: train/images\n"
        "val: valid/images\n"
        "test: test/images\n\n"
        "nc: 2\n"
        "names:\n"
        "  0: keys\n"
        "  1: wallet\n"
    )
    (output / "data.yaml").write_text(data_yaml, encoding="utf-8")
    (output / "source_manifest.json").write_text(json.dumps(source_manifest, indent=2), encoding="utf-8")
    (output / "split_manifest.json").write_text(json.dumps({
        "seed": seed,
        "split_policy": "capture_group_70_15_15",
        "classes": CANONICAL_CLASSES,
        "counts": split_counts,
        "samples": source_manifest,
    }, indent=2), encoding="utf-8")
    (output / "audit_report.json").write_text(json.dumps({
        "accepted_images": len(samples),
        "removed_duplicates": len(removed),
        "duplicates": removed,
        "counts": split_counts,
        "limitations": [
            "Files without explicit WEARABLE/PHONE/SECURITY/LAPTOP/CCTV names retain viewpoint=unknown.",
            "AC-02 device-specific quotas require a reviewed viewpoint manifest and independent held-out scenes.",
        ],
    }, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-zip", type=Path, required=True)
    parser.add_argument("--additional-zip", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("datasets/locate_combined_v2"))
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    for archive in (args.baseline_zip, args.additional_zip):
        if not archive.is_file():
            parser.error(f"Archive not found: {archive}")
    samples = read_archive(args.baseline_zip.resolve(), "baseline_255")
    samples.extend(read_archive(args.additional_zip.resolve(), "additional_220"))
    accepted, removed = deduplicate(samples)
    assign_splits(accepted, args.seed)
    write_dataset(accepted, removed, args.output, args.seed)
    print(json.dumps({"accepted_images": len(accepted), "removed_duplicates": len(removed)}, indent=2))


if __name__ == "__main__":
    main()
