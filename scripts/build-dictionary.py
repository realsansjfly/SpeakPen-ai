#!/usr/bin/env python3
"""Build the built-in dictionary asset (word + zh gloss + phonetic + pos + collins + exchange).

Input:
  --source  path to ECDICT ecdict.csv at the commit pinned in
            app/src/main/assets/vocabulary/manifest.json ("source".commit)
  --root    repository root (defaults to the parent of this script)

Output:
  app/src/main/assets/vocabulary/dictionary.tsv
  a JSON report on stdout with the sha256 and entry counts to paste into manifest.json

The three word lists (cet4.txt / cet6.txt / ielts.txt) stay untouched: they are the
"word form only" provenance assets that scripts/verify-vocabulary-build.py checks.
This script only *adds* the Chinese side of the data, which the app used to throw away.

Sorting is by lowercase word so the app can render an A-Z index without re-sorting.
"""

import argparse
import csv
import hashlib
import json
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

TAGS = ("cet4", "cet6", "ielts")
COLUMNS = ("word", "phonetic", "pos", "collins", "translation", "exchange", "tags")

# ECDICT writes schwa as U+04D9 (Cyrillic) and the fishhook g as U+0261 in places;
# normalise both to the IPA characters so the UI renders them consistently.
PHONETIC_FIX = {"\u04d9": "\u0259", "\u0261": "g"}

# Sense prefix that ECDICT puts in front of each translation line, e.g. "vt." / "a.".
POS_RE = re.compile(r"^\s*([a-z]{1,5}\.)")


def read_word_list(path):
    """Words exactly as stored in the shipped list asset."""
    with open(path, encoding="utf-8") as f:
        return [line.strip() for line in f if line.strip()]


def clean_inline(value):
    """Collapse tabs/newlines so a row always stays on one line."""
    return value.replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()


def derive_pos(translation):
    """Ordered, de-duplicated sense prefixes: 'vt. 放弃…\\nn. 放任…' -> 'vt./n.'."""
    seen = []
    for line in translation.split("\\n"):
        match = POS_RE.match(line)
        if not match:
            continue
        tag = match.group(1)
        if tag not in seen:
            seen.append(tag)
        if len(seen) == 3:
            break
    return "/".join(seen)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, help="ecdict.csv path")
    parser.add_argument("--root", default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    asset_dir = os.path.join(args.root, "app", "src", "main", "assets", "vocabulary")
    wanted = {}
    for tag in TAGS:
        for word in read_word_list(os.path.join(asset_dir, tag + ".txt")):
            wanted.setdefault(word, []).append(tag)

    rows = {}
    scanned = 0
    with open(args.source, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            scanned += 1
            word = (row.get("word") or "").strip()
            if word not in wanted:
                continue
            if word in rows:
                continue  # first exact-case hit wins; list assets came from this file
            translation = clean_inline(row.get("translation") or "")
            if not translation:
                continue  # a word without zh gloss is useless here
            phonetic = (row.get("phonetic") or "").strip()
            for bad, good in PHONETIC_FIX.items():
                phonetic = phonetic.replace(bad, good)
            rows[word] = {
                "word": word,
                "phonetic": clean_inline(phonetic),
                "pos": derive_pos(translation),
                "collins": (row.get("collins") or "0").strip() or "0",
                "translation": translation,
                "exchange": clean_inline(row.get("exchange") or ""),
                "tags": " ".join(t for t in TAGS if t in wanted[word]),
            }

    missing = sorted(set(wanted) - set(rows))
    ordered = sorted(rows.values(), key=lambda r: (r["word"].lower(), r["word"]))

    lines = ["\t".join(COLUMNS)]
    for row in ordered:
        lines.append("\t".join(row[c] for c in COLUMNS))
    payload = "\n".join(lines) + "\n"

    out_path = os.path.join(asset_dir, "dictionary.tsv")
    if not args.dry_run:
        with open(out_path, "w", encoding="utf-8", newline="") as f:
            f.write(payload)

    digest = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    per_tag = {tag: sum(1 for r in ordered if tag in r["tags"].split()) for tag in TAGS}
    report = {
        "scanned": scanned,
        "rows": len(ordered),
        "missing": missing[:20],
        "missingCount": len(missing),
        "perTag": per_tag,
        "bytes": len(payload.encode("utf-8")),
        "sha256": digest,
        "manifest": {
            "dictionary": {
                "file": "dictionary.tsv",
                "rows": len(ordered),
                "sha256": digest,
            }
        },
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
