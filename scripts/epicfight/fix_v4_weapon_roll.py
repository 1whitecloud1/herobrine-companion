"""Roll the V4 scythe 180 degrees about its own pole axis.

Why
---
The V4 clips come from a *sword* retarget (`HB_Scythe_V4_Quaternius_Heavy_Combo`).
A sword source carries no information about which side a scythe's hook faces: the
V6 pipeline had to solve that blade roll from video (`weapon_contact.py`,
`WeaponContact.orientation(angle, depth, roll)`, `preferred_roll = pi`, and its own
docstring notes "blade roll ... not identifiable from the video"). V4 has no such
solve, which is exactly the reported "第一段刀刃朝向反了".

What
----
The weapon mesh lives in the blend's bone space ("b-space"): the pole runs along +Z
through the origin (handle at -Z, blade head at +Z, hook toward -Y, per the geo cube
centroids daoren/yuan at y ~ -0.27). Rolling the mesh 180 degrees about that pole
axis flips the hook to the other side while leaving the pole direction, the grip
point and the entire body animation untouched:

    world_geometry = socket @ v_bs,   socket := Tool_R_world @ inv(correction)
    want           = socket @ Rz(180) @ v_bs
    => T_local'    = T_local @ (R_corr^T @ Rz(180) @ R_corr), translation unchanged

Only the joint's orientation rolls in place: the translation column is preserved, so
the grip, the collision box offset and the blade trail stay where they were. Applying
it twice restores the original data (it is an exact involution).

Only the Tool_R span is rewritten, byte for byte, in the file's own number style, so
every other joint of the clip stays bit-identical.

Usage (dry run first):
    blender --background --factory-startup --python fix_v4_weapon_roll.py
    blender --background --factory-startup --python fix_v4_weapon_roll.py -- --apply
"""
import hashlib
import json
import re
import sys
from pathlib import Path

import numpy as np

TARGET = Path(r'E:\java\herobrine companion\src\main\resources\assets\herobrine_companion\animmodels\animations\hero')
PIPELINE = Path(r'E:\java\herobrine_companion\src\main\resources\assets\herobrine_companion\animmodels\animations\hero')
REPORT = Path(r'E:\java\herobrine_companion\build\epicfight-v4\conversion_report.json')

CLIPS = ['hero_scythe_combo_v4'] + [f'hero_scythe_combo_v4_{i}' for i in range(1, 5)]

# SHA-256 prefixes of the delivered (pre-fix) clips. Requiring them makes a second
# application impossible: the patched files no longer match, so the script aborts.
EXPECTED = {
    'hero_scythe_combo_v4': '0a9d81a9d059',
    'hero_scythe_combo_v4_1': '878576fb024e',
    'hero_scythe_combo_v4_2': '018810cb51e0',
    'hero_scythe_combo_v4_3': '4652bea2751e',
    'hero_scythe_combo_v4_4': '7854d0cf20d0',
}

POLE_ROLL_DEGREES = 180.0
POLE_AXIS_BS = np.array([0.0, 0.0, 1.0])   # b-space pole: handle -Z, blade +Z
HOOK_AXIS_BS = np.array([0.0, -1.0, 0.0])  # b-space hook side (blade curls to -Y)

NUMBER = re.compile(r'-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?')
# The delivered clips store 7-decimal numbers, so a frame's socket can drift by a few
# hundredths of a degree between before/after. Anything below this is rounding, and it
# is three orders of magnitude under the 180 deg hook flip that is the point of the fix.
POLE_TOLERANCE_DEGREES = 0.1
STYLES = [('round7', lambda v: json.dumps(round(float(v), 7))),
          ('round6', lambda v: json.dumps(round(float(v), 6))),
          ('repr', lambda v: json.dumps(float(v))),
          ('round8', lambda v: json.dumps(round(float(v), 8))),
          ('round5', lambda v: json.dumps(round(float(v), 5)))]


def rotation_matrix(degrees, axis=POLE_AXIS_BS):
    t = np.radians(degrees)
    x, y, z = axis / np.linalg.norm(axis)
    c, s = np.cos(t), np.sin(t)
    C = 1 - c
    return np.array([
        [c + x * x * C, x * y * C - z * s, x * z * C + y * s],
        [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
        [z * x * C - y * s, z * y * C + x * s, c + z * z * C],
    ])


def transform_span(text):
    """Byte span of the Tool_R entry's "transform" array, brackets included."""
    anchor = text.index('{"name":"Tool_R"')
    start = text.index('[', text.index('"transform"', anchor))
    depth = 0
    for i in range(start, len(text)):
        if text[i] == '[':
            depth += 1
        elif text[i] == ']':
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise ValueError('unbalanced transform array')


def pick_style(tokens, values):
    for name, fmt in STYLES:
        if len(tokens) == len(values) and all(fmt(v) == t for t, v in zip(tokens, values)):
            return name, fmt
    return None, None


def main():
    apply = '--apply' in sys.argv
    weapon = json.loads(REPORT.read_text(encoding='utf-8'))['weapon']
    correction = np.array(weapon['model_to_socket'], dtype=float) @ np.linalg.inv(
        np.array(weapon['item_render_transform'], dtype=float))
    rot = correction[:3, :3]
    inv_rot = np.linalg.inv(rot)
    roll = rotation_matrix(POLE_ROLL_DEGREES)
    M = inv_rot @ roll @ rot
    axis, angle = np.linalg.eig(M)[1][:, 0].real, np.degrees(np.arccos(np.clip((np.trace(M) - 1) / 2, -1, 1)))
    print(f'POLE_ROLL {POLE_ROLL_DEGREES} deg -> Tool_R-local rotation of {angle:.6f} deg about '
          f'{np.round(axis, 6).tolist()} (b-space pole is [0,0,1])')

    failures = []
    for clip in CLIPS:
        for root, label in ((TARGET, 'target'), (PIPELINE, 'pipeline')):
            path = root / f'{clip}.json'
            raw = path.read_bytes()
            digest = hashlib.sha256(raw).hexdigest()[:12]
            if digest != EXPECTED[clip]:
                failures.append(f'{label}:{clip}: unexpected sha {digest} (already patched?)')
                continue
            text = raw.decode('utf-8')
            data = json.loads(text)
            tool = next(e for e in data['animation'] if e['name'] == 'Tool_R')
            start, end = transform_span(text)
            span = text[start:end]
            tokens = NUMBER.findall(span)
            flat_old = [v for frame in tool['transform'] for v in frame]
            style_name, fmt = pick_style(tokens, flat_old)
            if fmt is None:
                failures.append(f'{label}:{clip}: unknown number style in Tool_R span')
                continue

            pole_shift = hook_shift = origin_shift = 0.0
            new_frames, new_flat = [], []
            for frame in tool['transform']:
                base = np.array(frame, dtype=float).reshape(4, 4)
                fixed = base.copy()
                fixed[:3, :3] = base[:3, :3] @ M
                new_flat.extend(fixed.reshape(-1).tolist())
                new_frames.append('[' + ','.join(fmt(v) for v in fixed.reshape(-1)) + ']')
                # The mesh is placed by socket = Tool_R_world @ inv(correction), so the
                # b-space pole/hook directions are socket @ Z and socket @ -Y.
                socket_before = base[:3, :3] @ inv_rot
                socket_after = fixed[:3, :3] @ inv_rot
                pole_shift = max(pole_shift, float(np.degrees(np.arccos(np.clip(
                    np.dot(socket_before @ POLE_AXIS_BS, socket_after @ POLE_AXIS_BS), -1, 1)))))
                hook_shift = max(hook_shift, float(np.degrees(np.arccos(np.clip(
                    np.dot(socket_before @ HOOK_AXIS_BS, socket_after @ HOOK_AXIS_BS), -1, 1)))))
                origin_shift = max(origin_shift, float(np.abs(base[:3, 3] - fixed[:3, 3]).max()))
                if abs(np.linalg.det(fixed[:3, :3]) - 1.0) > 1e-5:
                    failures.append(f'{label}:{clip}: degenerate rotation after roll')
                    break
            if pole_shift > POLE_TOLERANCE_DEGREES or abs(hook_shift - 180.0) > POLE_TOLERANCE_DEGREES \
                    or origin_shift > 1e-6:
                failures.append(f'{label}:{clip}: invariants broken '
                                f'(pole {pole_shift}, hook {hook_shift}, origin {origin_shift})')
                continue

            rewritten = text[:start] + '[' + ','.join(new_frames) + ']' + text[end:]
            check = json.loads(rewritten)
            if [e['name'] for e in check['animation']] != [e['name'] for e in data['animation']]:
                failures.append(f'{label}:{clip}: joint list changed')
                continue
            for before, after in zip(data['animation'], check['animation']):
                if before['name'] == 'Tool_R':
                    continue
                if before != after:
                    failures.append(f'{label}:{clip}: unrelated joint {before["name"]} changed')
                    break
            moved = next(e for e in check['animation'] if e['name'] == 'Tool_R')
            err = float(np.abs(np.array(moved['transform'], dtype=float).reshape(-1)
                               - np.array(new_flat)).max())
            if err > 1e-7:
                failures.append(f'{label}:{clip}: written Tool_R mismatch {err}')
                continue

            print(f'{label:8s} {clip:26s} frames {len(tool["transform"]):4d} style {style_name:6s} '
                  f'pole {pole_shift:.2e}deg hook {hook_shift:.6f}deg origin {origin_shift:.1e} '
                  f'bytes {len(raw)} -> {len(rewritten.encode("utf-8"))}')
            if apply:
                path.write_bytes(rewritten.encode('utf-8'))
                print(f'         written  sha {hashlib.sha256(path.read_bytes()).hexdigest()[:12]}')

    if failures:
        print('\nFAILED:')
        for f in failures:
            print('  ', f)
        raise SystemExit(1)
    print('\nOK -', 'roll applied' if apply else 'validated (dry run); re-run with -- --apply')


if __name__ == '__main__':
    main()
