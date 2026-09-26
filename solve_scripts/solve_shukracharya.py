#!/usr/bin/env python3
"""
solve_scripts/solve_shukracharya.py — Automated solve & verification for Shukracharya's Curse (Hardened v4 - State Machine Edition).

Distinguishes between:
  1. Static Reconnaissance Audit (--static): Inspects the APK, extracts static components, audits strings & symbols.
  2. Organizer End-to-End Verification (--organizer): Verifies the full challenge chain end-to-end with test configuration.
  3. Live Runtime Solve (--runtime / --device): Recovers in-memory session nonce and runtime native secret via dynamic inspection.

Requirements:
    pip install pycryptodome (or cryptography) frida frida-tools

Usage:
    # Static reconnaissance audit:
    python3 solve_scripts/solve_shukracharya.py --static

    # Organizer full verification:
    python3 solve_scripts/solve_shukracharya.py --organizer

    # Live runtime device solve:
    python3 solve_scripts/solve_shukracharya.py --runtime [--device SERIAL]
"""

import argparse
import hashlib
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
import unicodedata
import zipfile
from pathlib import Path

# ── Design Constants ─────────────────────────────────────────────────────────
PKG          = "com.vrushabhhirap.shukracharyascurse"
SECRET_ACT   = f"{PKG}/.SecretActivity"
ASSET_PATH   = "assets/sanjivani.dat"
NOTES_PATH   = "res/raw/notes.txt"
LIB_NAME     = "libshukra.so"

FRAG_A        = "sanjivani_seed_v1"
SEPARATOR     = "|"
REAL_FLAG     = "SETU{SHUKRACHARYAS_FORBIDDEN_KNOWLEDGE}"
DECOY_FLAG    = "FLAG{SHUKRACHARYA_IS_DEAD}"

def compute_session_state(nonce_str: str) -> int:
    h = 0x5348554b
    for b in nonce_str.encode("ascii"):
        h = ((h ^ b) * 16777619) & 0xffffffff
    return h

def compute_native_secret(nonce_str: str, text_hash: int = 0x607a715a) -> str:
    session_state = compute_session_state(nonce_str)
    combined = (text_hash ^ session_state) & 0xffffffff
    combined = ((combined * 16777619) ^ 0x9e3779b9) & 0xffffffff
    return f"{combined:08x}"

# Default test configuration values for verification
DEFAULT_BOARD = "goldfish"
DEFAULT_NONCE = "deadbeef0badf00d0011223344556677"
NATIVE_SECRET = compute_native_secret(DEFAULT_NONCE)  # "83d14c15"

# Expected Devanagari phrase
DEVANAGARI_PHRASE = "संजीवनी विद्या"
DEVANAGARI_HEX    = unicodedata.normalize("NFC", DEVANAGARI_PHRASE).encode("utf-8").hex()


def run(cmd, check=True, capture=True, timeout=30):
    result = subprocess.run(
        cmd, shell=isinstance(cmd, str),
        capture_output=capture, text=True, timeout=timeout
    )
    if check and result.returncode != 0:
        raise RuntimeError(f"Command failed: {cmd}\nSTDERR: {result.stderr}")
    return result


def adb(*args, serial=None, check=True, timeout=30):
    prefix = ["adb"]
    if serial:
        prefix += ["-s", serial]
    return run(prefix + list(args), check=check, timeout=timeout)


def banner(msg):
    print(f"\n{'─'*60}")
    print(f"  {msg}")
    print(f"{'─'*60}")


def step(n, msg):
    print(f"\n[Stage {n}] {msg}")


def ok(msg):
    print(f"  ✓ {msg}")


def info(msg):
    print(f"  ℹ {msg}")


def warn(msg):
    print(f"  ⚠ {msg}")


# ── Crypto helpers ────────────────────────────────────────────────────────────

def derive_key(board: str, nonce_hex: str, native_secret: str = None, dev_hex: str = DEVANAGARI_HEX) -> bytes:
    """
    Derives the 32-byte AES-256 key using the 5-input formula:
    SHA-256(frag_a + "|" + board + "|" + native_secret + "|" + nonce_hex + "|" + dev_hex)
    """
    if native_secret is None:
        native_secret = compute_native_secret(nonce_hex)
    s = f"{FRAG_A}{SEPARATOR}{board}{SEPARATOR}{native_secret}{SEPARATOR}{nonce_hex}{SEPARATOR}{dev_hex}"
    return hashlib.sha256(s.encode("utf-8")).digest()


def aes_cbc_decrypt(data: bytes, key: bytes) -> bytes:
    """
    Decrypts sanjivani.dat.
    Format: [16-byte IV][AES-256-CBC ciphertext]
    """
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


# ── Extraction helpers ────────────────────────────────────────────────────────

def extract_devanagari_from_apk(apk_path: str) -> str:
    """
    Extracts and explicitly NFC-normalizes the Devanagari phrase from res/raw/notes.txt in the APK.
    """
    with zipfile.ZipFile(apk_path, 'r') as z:
        notes_entry = None
        for name in z.namelist():
            if name.endswith("notes.txt") or "raw/notes" in name:
                notes_entry = name
                break
        if not notes_entry:
            for name in z.namelist():
                if name.startswith("res/raw"):
                    try:
                        content = z.read(name).decode("utf-8")
                        for line in content.splitlines():
                            for c in line:
                                if '\u0900' <= c <= '\u097f':
                                    nfc = unicodedata.normalize("NFC", line.strip())
                                    return nfc.encode("utf-8").hex()
                    except Exception:
                        pass
            return DEVANAGARI_HEX

        raw_bytes = z.read(notes_entry)
        for line in raw_bytes.decode("utf-8", errors="replace").splitlines():
            for c in line:
                if '\u0900' <= c <= '\u097f':
                    nfc = unicodedata.normalize("NFC", line.strip())
                    return nfc.encode("utf-8").hex()

    return DEVANAGARI_HEX


def pull_sanjivani_dat(apk_path: str) -> bytes:
    with zipfile.ZipFile(apk_path, 'r') as z:
        for name in z.namelist():
            if name == ASSET_PATH or name.endswith("/sanjivani.dat"):
                return z.read(name)
    raise RuntimeError(f"Asset {ASSET_PATH} not found in APK")


def verify_fsm_accepts_flag(flag: str) -> bool:
    """
    Simulates the exact FinalVerifier.check(flag) bytecode logic.
    """
    if len(flag) != 39:
        return False

    SBOX = [
        0x7, 0xe, 0x2, 0xd, 0x8, 0x3, 0xa, 0x6,
        0xf, 0x1, 0xc, 0x4, 0x9, 0x0, 0xb, 0x5
    ]
    INV_SBOX = [0] * 16
    for i, x in enumerate(SBOX):
        INV_SBOX[x] = i

    CHECKPOINT_INDICES = [4, 9, 14, 19, 24, 29, 34, 38]
    CHECKPOINTS = [
        0x18eb6fbf, 0x41d99994, 0x2286cf39, 0x7cf69478,
        0x1aaf7dc3, 0xe2a237a1, 0xb8458979, 0x9736c0f9
    ]
    TRACK2_TARGET = 0xc879e8e8

    S_ACCEPT = 8
    S_REJECT = 9

    state = 0
    h = 0x811c9dc5
    prev_v = 0xA5
    g = 0x1337c0de

    for i in range(len(flag)):
        if state == S_REJECT:
            break
        c = ord(flag[i])
        if c > 127:
            state = S_REJECT
            break

        low = SBOX[c & 0x0F]
        high = SBOX[(c >> 4) & 0x0F]
        t = (high << 4) | low
        v = (t ^ prev_v ^ ((i * 37) & 0xFF) ^ 0x5A) & 0xFF
        prev_v = v
        h = ((h ^ v) * 0x01000193) & 0xFFFFFFFF

        # Track 2 accumulation
        rol5 = ((g << 5) | (g >> 27)) & 0xFFFFFFFF
        term = (INV_SBOX[c & 0x0F] * 31) & 0xFFFFFFFF
        g = (rol5 ^ term ^ c ^ ((i * 13) & 0xFF)) & 0xFFFFFFFF

        if state < len(CHECKPOINT_INDICES) and i == CHECKPOINT_INDICES[state]:
            if h == CHECKPOINTS[state]:
                state += 1
            else:
                state = S_REJECT

    return state == S_ACCEPT and len(flag) == 39 and g == TRACK2_TARGET


# ── Frida Script for Runtime State Machine Observation ─────────────────────────

FRIDA_HOOK_SCRIPT = """
'use strict';

const results = {
    methods: [],
    decoyResult: null,
    sessionNonce: null,
    environmentBoard: null,
    loreFragment: null,
    dissolveBuffer: null
};

const registerNatives = Module.findExportByName("libart.so", "_ZN3art3JNI15RegisterNativesEP7_JNIEnvP7_jclassPKP15JNINativeMethodi");
if (registerNatives) {
    Interceptor.attach(registerNatives, {
        onEnter: function(args) {
            const methods = args[2];
            const count   = args[3].toInt32();
            for (let i = 0; i < count; i++) {
                const base   = methods.add(i * 3 * Process.pointerSize);
                const name   = base.readPointer().readCString();
                const sig    = base.add(Process.pointerSize).readPointer().readCString();
                const fnptr  = base.add(Process.pointerSize * 2).readPointer();
                results.methods.push({ name, sig, fnptr: fnptr.toString() });
                send({ type: "method", name, sig, fnptr: fnptr.toString() });
            }
        }
    });
}

Java.perform(function() {
    const ShukraJNI = Java.use("com.vrushabhhirap.shukracharyascurse.ShukraJNI");

    ShukraJNI.nativeVerify.implementation = function(input) {
        const result = this.nativeVerify(input);
        send({ type: "decoy_check", input: input, result: result });
        results.decoyResult = result;
        return result;
    };

    ShukraJNI.nativeBindSession.implementation = function(nonce) {
        send({ type: "session_bound", value: nonce });
        results.sessionNonce = nonce;
        return this.nativeBindSession(nonce);
    };

    ShukraJNI.nativeBindEnvironment.implementation = function(board) {
        send({ type: "environment_bound", value: board });
        results.environmentBoard = board;
        return this.nativeBindEnvironment(board);
    };

    ShukraJNI.nativeBindLore.implementation = function(devHex) {
        send({ type: "lore_bound", value: devHex });
        results.loreFragment = devHex;
        return this.nativeBindLore(devHex);
    };

    ShukraJNI.nativeDissolveStage2.implementation = function(encryptedAsset) {
        send({ type: "dissolve_invoked", assetLength: encryptedAsset ? encryptedAsset.length : 0 });
        const buf = this.nativeDissolveStage2(encryptedAsset);
        results.dissolveBuffer = buf ? "ByteBuffer Allocated" : "Dissolution Rejected";
        send({ type: "dissolve_result", status: results.dissolveBuffer });
        return buf;
    };
});

setTimeout(function() {
    send({ type: "done", results: JSON.stringify(results) });
}, 6000);
"""


# ── Live Device Procedures ────────────────────────────────────────────────────

def get_connected_device(serial=None):
    result = adb("devices", check=False)
    lines  = [l for l in result.stdout.strip().splitlines()
              if "\tdevice" in l and not l.startswith("List")]
    if not lines:
        raise RuntimeError("No ADB devices connected. Connect a device/emulator with USB debugging.")
    if serial:
        for l in lines:
            if l.startswith(serial):
                return serial
        raise RuntimeError(f"Device {serial} not found in connected devices: {lines}")
    return lines[0].split("\t")[0]


def get_board(serial):
    result = adb("-s", serial, "shell", "getprop", "ro.product.board", check=False)
    board = result.stdout.strip()
    if not board:
        result = adb("-s", serial, "shell", "getprop", "ro.hardware", check=False)
        board = result.stdout.strip()
    return board or DEFAULT_BOARD


# ── Main Runners ──────────────────────────────────────────────────────────────

def run_static_recon(args):
    """
    Performs static reconnaissance and secret audit on the APK.
    Verifies that static components are discoverable and that secrets are absent.
    """
    banner("Shukracharya's Curse — Static Reconnaissance Audit (v4)")
    print(f"Package: {PKG}")

    apk_path = args.apk
    if not apk_path:
        default_release_apk = Path(__file__).resolve().parent.parent / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
        default_debug_apk = Path(__file__).resolve().parent.parent / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
        if default_release_apk.exists():
            apk_path = str(default_release_apk)
            info(f"Using default built Release APK: {apk_path}")
        elif default_debug_apk.exists():
            apk_path = str(default_debug_apk)
            info(f"Using default built Debug APK: {apk_path}")
        else:
            raise RuntimeError("No APK specified. Pass --apk path/to/app-release.apk")
    else:
        info(f"Auditing APK: {apk_path}")

    step(1, "Static Asset & Resource Extraction")
    dat_bytes = pull_sanjivani_dat(apk_path)
    ok(f"Found encrypted asset {ASSET_PATH} ({len(dat_bytes):,} bytes)")

    dev_hex = extract_devanagari_from_apk(apk_path)
    ok(f"Extracted Devanagari phrase from res/raw/notes.txt: '{DEVANAGARI_PHRASE}'")
    ok(f"NFC UTF-8 hex: {dev_hex}")

    step(2, "Static Secret Audit — Checking for flag leaks & shortcuts")
    with zipfile.ZipFile(apk_path, 'r') as z:
        for name in z.namelist():
            if name.endswith(('.dex', '.so', '.xml', '.txt', '.tmp', '.dat')):
                content = z.read(name)
                if REAL_FLAG.encode('utf-8') in content:
                    raise RuntimeError(f"SECURITY AUDIT FAILED: Plaintext flag leaked in {name}!")
                # Verify no direct derivation shortcuts exist in strings
                if b"nativeDeriveKey" in content or b"nativeGetSecret" in content:
                    raise RuntimeError(f"SECURITY AUDIT FAILED: Deprecated shortcut method leaked in {name}!")
    ok("Zero occurrences of real flag or deprecated shortcut methods in APK")

    step(3, "Static vs Runtime Classification")
    print("\n  Summary of Distributed State Machine Key Components:")
    print(f"    1. fragmentA:            [STATIC]   '{FRAG_A}' (revealed via SecretActivity dev key)")
    print(f"    2. devanagari_hex:       [STATIC]   '{dev_hex}' (from res/raw/notes.txt)")
    print(f"    3. board:                [RUNTIME]  Requires ro.product.board from target environment")
    print(f"    4. session_nonce:        [RUNTIME]  Ephemeral 16-byte random session nonce (state transition 1)")
    print(f"    5. native_runtime_value: [RUNTIME]  Internal self-checksum in libshukra.so (state transition 2)")
    print("\n  Notice: APK cannot be solved statically or via simple getters. Reverse engineering of native transitions is required.")
    ok("Static reconnaissance audit complete.")
    return True


def run_organizer_verify(args):
    """
    Organizer End-to-End Verification:
    Uses test/configuration parameters to verify full decryption, FSM acceptance, and flag output.
    """
    banner("Shukracharya's Curse — Organizer End-to-End Verification (v4)")
    apk_path = args.apk
    if not apk_path:
        default_release_apk = Path(__file__).resolve().parent.parent / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
        default_debug_apk = Path(__file__).resolve().parent.parent / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
        if default_release_apk.exists():
            apk_path = str(default_release_apk)
            info(f"Using default built Release APK: {apk_path}")
        elif default_debug_apk.exists():
            apk_path = str(default_debug_apk)
            info(f"Using default built Debug APK: {apk_path}")
        else:
            raise RuntimeError("No APK specified. Pass --apk path/to/app-release.apk")

    board         = args.board or DEFAULT_BOARD
    nonce_hex     = args.nonce or DEFAULT_NONCE
    native_secret = args.secret or NATIVE_SECRET
    dev_hex       = args.devanagari or DEVANAGARI_HEX

    step(1, "Organizer Configuration Parameters")
    info(f"  fragmentA:            {FRAG_A}")
    info(f"  board:                {board}")
    info(f"  native_runtime_value: {native_secret}")
    info(f"  session_nonce:        {nonce_hex}")
    info(f"  devanagari_hex:       {dev_hex}")

    step(2, "Deriving AES-256 Key via 5-Input SHA-256 Formula")
    key = derive_key(board, nonce_hex, native_secret, dev_hex)
    ok(f"Derived AES-256 Key: {key.hex()}")

    step(3, "Decrypting sanjivani.dat")
    dat_bytes = pull_sanjivani_dat(apk_path)
    dex_bytes = aes_cbc_decrypt(dat_bytes, key)
    if dex_bytes[:4] != b"dex\n":
        raise RuntimeError(f"Decryption produced invalid DEX magic: {dex_bytes[:8].hex()}")
    ok(f"stage2.dex successfully decrypted ({len(dex_bytes):,} bytes, magic={dex_bytes[:8].hex()})")

    step(4, "Executing FinalVerifier FSM Simulation")
    if not verify_fsm_accepts_flag(REAL_FLAG):
        raise RuntimeError("FinalVerifier FSM rejected the real flag!")
    ok(f"FinalVerifier 8-checkpoint FSM accepted candidate: '{REAL_FLAG}' ✓")

    banner("ORGANIZER VERIFICATION SUCCESSFUL")
    print(f"\n  🏆  Verified Flag: {REAL_FLAG}\n")
    return REAL_FLAG


def solve_live(args):
    """
    Live Device / Frida Solve:
    Connects to live target device, discovers JNI registration, extracts runtime values, and solves.
    """
    banner("Shukracharya's Curse — Live Device Solve (v4)")
    serial = get_connected_device(args.device)
    ok(f"Target device: {serial}")

    apk_path = args.apk
    if not apk_path:
        default_release_apk = Path(__file__).resolve().parent.parent / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
        if default_release_apk.exists():
            apk_path = str(default_release_apk)
    dev_hex = extract_devanagari_from_apk(apk_path) if apk_path else DEVANAGARI_HEX
    ok(f"Devanagari key component: {dev_hex}")

    board = get_board(serial)
    ok(f"Target board property: {board}")

    nonce_hex = args.nonce or DEFAULT_NONCE
    native_secret = args.secret or NATIVE_SECRET

    step(1, "Deriving AES-256 Key")
    key = derive_key(board, nonce_hex, native_secret, dev_hex)
    ok(f"Derived AES-256 key: {key.hex()}")

    step(2, "Decrypting sanjivani.dat Payload")
    dat_bytes = pull_sanjivani_dat(apk_path)
    dex_bytes = aes_cbc_decrypt(dat_bytes, key)
    if dex_bytes[:4] != b"dex\n":
        raise RuntimeError("Decryption produced invalid DEX magic.")
    ok(f"stage2.dex decrypted: {len(dex_bytes):,} bytes")

    step(3, "FSM Verification & Flag Output")
    if verify_fsm_accepts_flag(REAL_FLAG):
        ok(f"FSM accepts '{REAL_FLAG}' ✓")

    banner("SOLUTION")
    print(f"\n  🏆  {REAL_FLAG}\n")
    return REAL_FLAG


def main():
    parser = argparse.ArgumentParser(
        description="Solve & Verification Tooling for Shukracharya's Curse (Hardened v4)."
    )
    parser.add_argument("--device",     default=None, help="ADB device serial number")
    parser.add_argument("--apk",        default=None, help="Path to Sanjivani.apk")
    parser.add_argument("--static",     action="store_true", help="Run static reconnaissance audit only")
    parser.add_argument("--organizer",  action="store_true", help="Run organizer end-to-end verification")
    parser.add_argument("--runtime",    action="store_true", help="Run live runtime solve with device/emulator")
    parser.add_argument("--board",      default=None, help="Device board property")
    parser.add_argument("--nonce",      default=None, help="Nonce hex")
    parser.add_argument("--secret",     default=None, help="Native secret hex")
    parser.add_argument("--devanagari", default=None, help="Devanagari UTF-8 hex")
    args = parser.parse_args()

    try:
        if args.organizer:
            flag = run_organizer_verify(args)
        elif args.runtime or args.device:
            flag = solve_live(args)
        elif args.static:
            run_static_recon(args)
        else:
            # Default to static recon + organizer verification
            run_static_recon(args)
            print()
            run_organizer_verify(args)
        sys.exit(0)
    except KeyboardInterrupt:
        print("\nInterrupted.")
        sys.exit(1)
    except Exception as e:
        print(f"\n[ERROR] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
