#!/usr/bin/env python3
"""
tools/encrypt_stage2.py — Build-time script (Hardened v5 - Native Matrix Evolution).
Encrypts stage2.dex into app/src/main/assets/sanjivani.dat using exact state evolution algorithm.
"""

import argparse
import hashlib
import os
import secrets
import struct
import sys
import unicodedata
from pathlib import Path

# Base prime seed matching SHUKRA_PRIME_SEED in shukra_native.c
SHUKRA_PRIME_SEED = [
    0x5348554b, 0x52414348, 0x41525941, 0x53414e4a,
    0x4956414e, 0x49564944, 0x59415631, 0x9e3779b9
]

DEFAULT_BOARD = "goldfish"
DEVANAGARI_PHRASE = "संजीवनी विद्या"

REPO_ROOT   = Path(__file__).resolve().parent.parent
DEFAULT_DEX  = REPO_ROOT / "stage2-dex-src" / "build" / "stage2.dex"
DEFAULT_OUT  = REPO_ROOT / "app" / "src" / "main" / "assets" / "sanjivani.dat"

def rotr(x: int, n: int) -> int:
    return ((x >> n) | (x << (32 - n))) & 0xffffffff

def simulate_native_state_evolution(board: str, lore: str) -> tuple[bytes, int]:
    """
    Simulates the exact C state evolution engine in shukra_native.c.
    Returns: (32-byte AES key, dynamic stateToken)
    """
    matrix = list(SHUKRA_PRIME_SEED)

    # Step 1: sha256_transform code window folding (deterministic standard SHA-256 block 0 loop)
    # Using the standard SHA-256 transform code pattern initialized in native library
    # Here we emulate the standard 256-byte code window folding
    K = [
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,
        0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,
        0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,
        0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,
        0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,
        0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,
        0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,
        0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,
        0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    ]
    # Synthetic code window projection
    code_bytes = b"".join(struct.pack("<I", k) for k in K)

    for i in range(256):
        b = code_bytes[i % len(code_bytes)]
        matrix[i % 8] = (((matrix[i % 8] ^ b) * 16777619) ^ (i * 31)) & 0xffffffff

    # Step 2: Hardware entropy diffusion
    board_bytes = board.encode("utf-8")
    for i, b in enumerate(board_bytes):
        matrix[i % 8] = rotr(matrix[i % 8] ^ (b << 16), (i % 24) + 1)
        matrix[(i + 1) % 8] = (matrix[(i + 1) % 8] + (b * 0x01000193)) & 0xffffffff

    # Step 3: Sacred lore invocation
    lore_nfc = unicodedata.normalize("NFC", lore).encode("utf-8")
    for i, c in enumerate(lore_nfc):
        matrix[i % 8] = ((matrix[i % 8] ^ c) * 16777619) & 0xffffffff
        matrix[(i + 3) % 8] ^= rotr(c, 3)

    # Step 4: AES Key & Dynamic Token computation
    raw_matrix = struct.pack("<8I", *matrix)
    key = hashlib.sha256(raw_matrix).digest()
    token = (matrix[0] ^ matrix[4] ^ 0x5a5aa5a5) & 0xffffffff

    return key, token


def encrypt(dex_bytes: bytes, key: bytes) -> bytes:
    """AES-256-CBC encryption with PKCS#7 padding"""
    try:
        from Crypto.Cipher import AES
        from Crypto.Util.Padding import pad
        iv = secrets.token_bytes(16)
        cipher = AES.new(key, AES.MODE_CBC, iv)
        padded = pad(dex_bytes, AES.block_size)
        return iv + cipher.encrypt(padded)
    except ImportError:
        from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
        from cryptography.hazmat.primitives import padding
        iv = secrets.token_bytes(16)
        padder = padding.PKCS7(128).padder()
        padded = padder.update(dex_bytes) + padder.finalize()
        cipher = Cipher(algorithms.AES(key), modes.CBC(iv))
        encryptor = cipher.encryptor()
        return iv + encryptor.update(padded) + encryptor.finalize()


def decrypt(data: bytes, key: bytes) -> bytes:
    """AES-256-CBC decryption with PKCS#7 unpadding"""
    try:
        from Crypto.Cipher import AES
        from Crypto.Util.Padding import unpad
        iv = data[:16]
        ct = data[16:]
        cipher = AES.new(key, AES.MODE_CBC, iv)
        return unpad(cipher.decrypt(ct), AES.block_size)
    except ImportError:
        from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
        from cryptography.hazmat.primitives import padding
        iv = data[:16]
        ct = data[16:]
        cipher = Cipher(algorithms.AES(key), modes.CBC(iv))
        decryptor = cipher.decryptor()
        padded = decryptor.update(ct) + decryptor.finalize()
        unpadder = padding.PKCS7(128).unpadder()
        return unpadder.update(padded) + unpadder.finalize()


def main():
    parser = argparse.ArgumentParser(description="Encrypt stage2.dex via native matrix state evolution.")
    parser.add_argument("--dex",    default=str(DEFAULT_DEX),  help="Path to stage2.dex")
    parser.add_argument("--board",  default=DEFAULT_BOARD,     help="android.os.Build.BOARD")
    parser.add_argument("--lore",   default=DEVANAGARI_PHRASE, help="Devanagari phrase")
    parser.add_argument("--output", default=str(DEFAULT_OUT),  help="Output sanjivani.dat")
    args = parser.parse_args()

    key, token = simulate_native_state_evolution(args.board, args.lore)

    dex_path = Path(args.dex)
    if not dex_path.exists():
        print(f"ERROR: {dex_path} not found. Run ./gradlew :stage2-dex-src:compileJava && python3 tools/build_stage2_dex.py")
        sys.exit(1)

    dex_bytes = dex_path.read_bytes()
    encrypted = encrypt(dex_bytes, key)

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(encrypted)

    # Self-test
    recovered = decrypt(encrypted, key)
    assert recovered == dex_bytes, "FATAL: Self-test failed"

    print(f"[encrypt_stage2] Board:         {args.board}")
    print(f"[encrypt_stage2] Lore:          {args.lore}")
    print(f"[encrypt_stage2] Dynamic Key:   {key.hex()}")
    print(f"[encrypt_stage2] State Token:   0x{token:08x} ({token})")
    print(f"[encrypt_stage2] Output:        {out_path} ({len(encrypted)} bytes)")
    print(f"[encrypt_stage2] Self-test:     PASS")


if __name__ == "__main__":
    main()
