#!/usr/bin/env python3
"""Curate the free-exercise-db dataset into a MuscleTome bundled asset.

Dev-time tool only — the app never uses the network. Downloads the upstream
dataset (Unlicense), filters to the approved Phase 4 scope, sorts
deterministically, and writes the asset envelope consumed by
BundledCatalogImporter:

    { "source": "free_exercise_db", "revision": "<sha256>", "records": [...] }

Scope (approved): categories strength, powerlifting, olympic weightlifting.
Records with unmapped primary muscles are kept in the asset; the runtime
quality gate decides to skip them (see docs/canonical-exercise-import.md).

Usage:
    python3 tools/curate_free_exercise_db.py [--output app/src/main/assets/catalog/free_exercise_db.json]
"""

import argparse
import hashlib
import json
import pathlib
import sys
import urllib.request

DATASET_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
SOURCE_KEY = "free_exercise_db"
APPROVED_CATEGORIES = {"strength", "powerlifting", "olympic weightlifting"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    default_out = (
        pathlib.Path(__file__).resolve().parent.parent
        / "app/src/main/assets/catalog/free_exercise_db.json"
    )
    parser.add_argument("--output", type=pathlib.Path, default=default_out)
    args = parser.parse_args()

    print(f"Downloading {DATASET_URL} …")
    with urllib.request.urlopen(DATASET_URL) as response:
        dataset = json.loads(response.read().decode("utf-8"))

    if not isinstance(dataset, list):
        print("Upstream dataset is not a record list", file=sys.stderr)
        return 1

    records = [
        record
        for record in dataset
        if (record.get("category") or "") in APPROVED_CATEGORIES
    ]
    # Deterministic order: stable upstream order preserved (ids are unique),
    # sorted by id for a stable diffable asset.
    records.sort(key=lambda record: record.get("id") or "")

    unique_ids = len({record.get("id") for record in records})
    if unique_ids != len(records):
        print("Dataset contains duplicate ids", file=sys.stderr)
        return 1

    canonical_json = json.dumps(records, ensure_ascii=False, sort_keys=True)
    revision = hashlib.sha256(canonical_json.encode("utf-8")).hexdigest()

    envelope = {
        "source": SOURCE_KEY,
        "revision": revision,
        "records": records,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(envelope, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    total = len(dataset)
    print(f"Total upstream records:        {total}")
    print(f"Records in approved scope:     {len(records)}")
    print(f"Skipped (out-of-scope):        {total - len(records)}")
    print(f"Revision (sha256 of records):  {revision}")
    print(f"Asset written to:              {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
