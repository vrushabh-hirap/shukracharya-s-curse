#!/usr/bin/env python3
"""
tools/build_stage2_dex.py — Helper script to extract stage2.dex from Gradle build output.

Gradle's java-library plugin produces .class files, not a .dex directly.
This script uses the Android SDK's d8 tool to convert the .class files to a .dex.

Usage:
    python3 tools/build_stage2_dex.py

Output:
    stage2-dex-src/build/stage2.dex  (ready for encrypt_stage2.py)
"""

import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Android SDK d8 location — update if SDK path differs
SDK_DIR = Path(os.environ.get("ANDROID_SDK_ROOT",
               Path.home() / "Library" / "Android" / "sdk"))
D8      = SDK_DIR / "build-tools" / "35.0.0" / "d8"

STAGE2_BUILD = REPO_ROOT / "stage2-dex-src" / "build"
CLASSES_DIR  = STAGE2_BUILD / "classes" / "java" / "main"
OUT_DIR      = STAGE2_BUILD
OUT_DEX      = OUT_DIR / "stage2.dex"


def main():
    if not D8.exists():
        print(f"ERROR: d8 not found at {D8}")
        print("  Set ANDROID_SDK_ROOT or update D8 path in this script.")
        sys.exit(1)

    # Collect all .class files
    class_files = list(CLASSES_DIR.rglob("*.class"))
    if not class_files:
        print(f"ERROR: No .class files found in {CLASSES_DIR}")
        print("  Run first: ./gradlew :stage2-dex-src:compileJava")
        sys.exit(1)

    print(f"Found {len(class_files)} .class file(s)")

    cmd = [
        str(D8),
        "--min-api", "28",
        "--output", str(OUT_DIR),
    ] + [str(f) for f in class_files]

    print(f"Running: d8 --min-api 28 --output {OUT_DIR} [class files]")
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        print(f"ERROR: d8 failed:\n{result.stderr}")
        sys.exit(1)

    # d8 outputs as "classes.dex" — rename to stage2.dex
    produced = OUT_DIR / "classes.dex"
    if produced.exists():
        produced.rename(OUT_DEX)

    if not OUT_DEX.exists():
        print(f"ERROR: Expected {OUT_DEX} not produced.")
        sys.exit(1)

    dex_bytes = OUT_DEX.read_bytes()
    if not dex_bytes.startswith(b"dex\n"):
        print(f"WARNING: {OUT_DEX} doesn't have dex magic bytes.")
    else:
        print(f"SUCCESS: {OUT_DEX} ({len(dex_bytes):,} bytes) — valid DEX")

    print()
    print("Next step:")
    print("  python3 tools/encrypt_stage2.py --board <your_board> --nonce <nonce_hex>")


if __name__ == "__main__":
    main()
