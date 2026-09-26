#!/usr/bin/env python3
"""
Exact MITM Solver for FinalVerifier (448 Track 1 candidates -> 1 Track 2 survivor)
"""
import time

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
INTENDED_FLAG = "SETU{SHUKRACHARYAS_FORBIDDEN_KNOWLEDGE}"

INV_PRIME = 899433627  # pow(0x01000193, -1, 2**32)
PRINTABLE = [c for c in range(32, 127)]

def compute_step(h, prev_v, b, i):
    low = SBOX[b & 0x0F]
    high = SBOX[(b >> 4) & 0x0F]
    t = (high << 4) | low
    v = (t ^ prev_v ^ ((i * 37) & 0xFF) ^ 0x5A) & 0xFF
    next_h = ((h ^ v) * 0x01000193) & 0xFFFFFFFF
    return next_h, v

def solve_all_segments():
    seg_ranges = [
        (0, 4), (5, 9), (10, 14), (15, 19),
        (20, 24), (25, 29), (30, 34), (35, 38)
    ]
    current_states = [(0x811c9dc5, 0xA5, "")]
    t0 = time.time()
    
    for seg_idx, (start_i, end_i) in enumerate(seg_ranges):
        target_h = CHECKPOINTS[seg_idx]
        seg_len = end_i - start_i + 1
        
        # Group current prefixes by v_in
        prefixes_by_v = {}
        for h_in, v_in, prefix in current_states:
            if v_in not in prefixes_by_v:
                prefixes_by_v[v_in] = []
            prefixes_by_v[v_in].append(prefix)
            
        next_states = []
        h_in = current_states[0][0]
        
        for v_in, prefix_list in prefixes_by_v.items():
            valid_seg_transitions = [] # list of (v_out, seg_str)
            if seg_len == 5:
                # Left 3 chars -> forward 95^3
                left_map = {}
                for b0 in PRINTABLE:
                    h1, v1 = compute_step(h_in, v_in, b0, start_i)
                    for b1 in PRINTABLE:
                        h2, v2 = compute_step(h1, v1, b1, start_i + 1)
                        for b2 in PRINTABLE:
                            h3, v3 = compute_step(h2, v2, b2, start_i + 2)
                            key = (h3, v3)
                            if key not in left_map:
                                left_map[key] = []
                            left_map[key].append(chr(b0) + chr(b1) + chr(b2))
                
                v3_set = set(k[1] for k in left_map.keys())
                
                # Right 2 chars -> backwards from target_h
                for b3 in PRINTABLE:
                    low3 = SBOX[b3 & 0x0F]
                    high3 = SBOX[(b3 >> 4) & 0x0F]
                    t3 = (high3 << 4) | low3
                    for b4 in PRINTABLE:
                        low4 = SBOX[b4 & 0x0F]
                        high4 = SBOX[(b4 >> 4) & 0x0F]
                        t4 = (high4 << 4) | low4
                        for v3_val in v3_set:
                            v4 = (t3 ^ v3_val ^ (((start_i + 3) * 37) & 0xFF) ^ 0x5A) & 0xFF
                            v5 = (t4 ^ v4 ^ (((start_i + 4) * 37) & 0xFF) ^ 0x5A) & 0xFF
                            
                            h4 = ((target_h * INV_PRIME) & 0xFFFFFFFF) ^ v5
                            req_h3 = ((h4 * INV_PRIME) & 0xFFFFFFFF) ^ v4
                            if (req_h3, v3_val) in left_map:
                                for left_str in left_map[(req_h3, v3_val)]:
                                    valid_seg_transitions.append((v5, left_str + chr(b3) + chr(b4)))
            else:  # seg_len == 4 (last segment)
                left_map = {}
                for b0 in PRINTABLE:
                    h1, v1 = compute_step(h_in, v_in, b0, start_i)
                    for b1 in PRINTABLE:
                        h2, v2 = compute_step(h1, v1, b1, start_i + 1)
                        key = (h2, v2)
                        if key not in left_map:
                            left_map[key] = []
                        left_map[key].append(chr(b0) + chr(b1))
                v2_set = set(k[1] for k in left_map.keys())
                for b2 in PRINTABLE:
                    low2 = SBOX[b2 & 0x0F]
                    high2 = SBOX[(b2 >> 4) & 0x0F]
                    t2 = (high2 << 4) | low2
                    for b3 in PRINTABLE:
                        low3 = SBOX[b3 & 0x0F]
                        high3 = SBOX[(b3 >> 4) & 0x0F]
                        t3 = (high3 << 4) | low3
                        for v2_val in v2_set:
                            v3 = (t2 ^ v2_val ^ (((start_i + 2) * 37) & 0xFF) ^ 0x5A) & 0xFF
                            v4 = (t3 ^ v3 ^ (((start_i + 3) * 37) & 0xFF) ^ 0x5A) & 0xFF
                            h3 = ((target_h * INV_PRIME) & 0xFFFFFFFF) ^ v4
                            req_h2 = ((h3 * INV_PRIME) & 0xFFFFFFFF) ^ v3
                            if (req_h2, v2_val) in left_map:
                                for left_str in left_map[(req_h2, v2_val)]:
                                    valid_seg_transitions.append((v4, left_str + chr(b2) + chr(b3)))
                                    
            for v_out, seg_str in valid_seg_transitions:
                for prefix in prefix_list:
                    next_states.append((target_h, v_out, prefix + seg_str))
                    
        current_states = next_states
        print(f"[*] Checkpoint {seg_idx + 1}/8 (indices {start_i}..{end_i}): {len(current_states)} candidate prefixes (elapsed: {time.time()-t0:.2f}s)")
        
    return current_states

def run_track2(s):
    g = 0x1337c0de
    for i, c in enumerate(s):
        b = ord(c)
        rol5 = ((g << 5) | (g >> 27)) & 0xFFFFFFFF
        term = (INV_SBOX[b & 0x0F] * 31) & 0xFFFFFFFF
        g = (rol5 ^ term ^ b ^ ((i * 13) & 0xFF)) & 0xFFFFFFFF
    return g

if __name__ == "__main__":
    print("=== Starting Full MITM Uniqueness Solver ===")
    results = solve_all_segments()
    total_t1 = len(results)
    print(f"\n[+] Total Track 1 mathematical preimages: {total_t1}")
    
    survivors = []
    rejected = 0
    for h, v, cand in results:
        g = run_track2(cand)
        if g == TRACK2_TARGET:
            survivors.append(cand)
        else:
            rejected += 1
            
    print(f"[+] Track 2 Evaluation:")
    print(f"    Total Evaluated: {total_t1}")
    print(f"    Accepted (Survivors): {len(survivors)}")
    print(f"    Rejected: {rejected}")
    if survivors:
        print(f"    Unique Survivor: {survivors[0]}")
        
    assert total_t1 == 448, f"Expected 448 Track 1 candidates, got {total_t1}"
    assert len(survivors) == 1, f"Expected 1 survivor, got {len(survivors)}"
    assert survivors[0] == INTENDED_FLAG, f"Survivor mismatch: {survivors[0]}"
    assert rejected == 447, f"Expected 447 rejected, got {rejected}"
    print("\n[✓] Mathematical uniqueness 448 -> 1 verified successfully!")
