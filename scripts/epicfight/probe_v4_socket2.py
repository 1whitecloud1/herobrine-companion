"""Space-consistent comparison of the V4 weapon socket against the scene's weapon.

Key detail: export_v4.py maps source->EF with C = diag(-1,-1,1). The animated
'Poem of the End | 原版镰刀' object lives in Blender space, so its rotation must be
compared as C @ R_object versus C @ P_weapon (not R_object versus C @ P_weapon).

Read-only. Run:
blender --background --factory-startup --disable-autoexec <V4.blend> --python <this>
"""
import json
import math
import sys
from pathlib import Path

import bpy
import numpy as np

REPO = Path(r'E:\java\herobrine_companion')
sys.path.insert(0, str(REPO / 'scripts' / 'epicfight'))
from export_v4 import load, geo_points  # noqa: E402

SWAP = np.array([[1., 0, 0], [0, 0, 1], [0, 1, 0]])
PIVOT = np.array([-6., 34., 0.])
C = np.diag([-1., -1., 1.])
HZ = 120


def rotation(m):
    u, _, vt = np.linalg.svd(np.array(m)[:3, :3])
    r = u @ vt
    if np.linalg.det(r) < 0:
        u[:, -1] *= -1
        r = u @ vt
    return r


def angle_axis(rot):
    ang = float(np.degrees(np.arccos(np.clip((np.trace(rot) - 1) / 2, -1, 1))))
    if ang < 1e-6:
        return 0.0, None
    w, v = np.linalg.eig(rot)
    ax = np.real(v[:, np.argmin(np.abs(w - 1))])
    return round(ang, 4), np.round(ax / np.linalg.norm(ax), 5).tolist()


def main():
    ob = bpy.data.objects['Poem of the End | 原版镰刀']
    cons = []
    for c in ob.constraints:
        d = {'type': c.type, 'name': c.name,
             'target': c.target.name if getattr(c, 'target', None) else None,
             'subtarget': getattr(c, 'subtarget', ''),
             'owner_space': getattr(c, 'owner_space', None),
             'target_space': getattr(c, 'target_space', None),
             'mix_mode': getattr(c, 'mix_mode', None),
             'influence': getattr(c, 'influence', None),
             'mix_mode_rot': getattr(c, 'mix_mode_rot', None)}
        cons.append(d)
    print('PROBE_CONSTRAINTS', json.dumps(cons, ensure_ascii=False))
    print('PROBE_OBJ_BASIS', json.dumps({
        'rotation_euler_deg': [round(math.degrees(v), 4) for v in ob.rotation_euler],
        'rotation_mode': ob.rotation_mode,
        'scale': [round(v, 6) for v in ob.scale],
        'location': [round(v, 6) for v in ob.location]}))

    src = bpy.data.objects.get('Source:Quaternius_UAL2')
    if src is not None:
        print('PROBE_SOURCE_RIG', json.dumps({
            'bones': [b.name for b in src.data.bones],
            'action': src.animation_data.action.name if src.animation_data and src.animation_data.action else None,
            'frame_range': list(src.animation_data.action.frame_range) if src.animation_data and src.animation_data.action else None}))
    for n in ('Source:Mannequin', 'Source:Mannequin.001'):
        o = bpy.data.objects.get(n)
        if o:
            print('PROBE_SOURCE_OBJ', json.dumps({
                'name': n, 'parent': o.parent.name if o.parent else None,
                'parent_type': o.parent_type, 'parent_bone': o.parent_bone or None,
                'constraints': [c.type for c in o.constraints],
                'matrix_world': np.round(np.array(o.matrix_world), 6).tolist()}))

    rig = bpy.data.objects['Bones:Character']
    scene = bpy.context.scene
    rows = []
    for tick in [0, 6, 12, 24, 36, 48, 60, 72, 84, 96, 108, 120]:
        frame = 1 + tick * 30 / HZ
        scene.frame_set(math.floor(frame), subframe=frame % 1)
        deps = bpy.context.evaluated_depsgraph_get()
        p_rot = rotation(rig.evaluated_get(deps).pose.bones['Weapon:Scythe'].matrix)
        m_rot = rotation(ob.evaluated_get(deps).matrix_world)
        root_rot = rotation(rig.evaluated_get(deps).pose.bones['Bone.011'].matrix)
        d_src = p_rot @ m_rot.T
        d_socket = (C @ m_rot) @ (C @ p_rot).T
        a1, x1 = angle_axis(d_src)
        a2, x2 = angle_axis(d_socket)
        rows.append({'tick': tick, 'delta_source_angle': a1, 'delta_source_axis': x1,
                     'delta_ef_angle': a2, 'delta_ef_axis': x2,
                     'delta_ef_matrix': np.round(d_socket, 5).tolist(),
                     'forward_source': np.round(root_rot @ np.array([0., -1., 0.]), 5).tolist()})
    print('PROBE_ROWS', json.dumps(rows))
    a_src = [r['delta_source_angle'] for r in rows]
    a_ef = [r['delta_ef_angle'] for r in rows]
    print('PROBE_SUMMARY', json.dumps({
        'source_space_delta_deg_min': min(a_src), 'source_space_delta_deg_max': max(a_src),
        'source_space_delta_axis': rows[0]['delta_source_axis'],
        'ef_space_delta_deg_min': min(a_ef), 'ef_space_delta_deg_max': max(a_ef),
        'ef_space_delta_axis': rows[0]['delta_ef_axis'],
        'ef_delta_is_identity': bool(max(a_ef) < 3),
        'forward_source_tick0': rows[0]['forward_source']}))
    print('PROBE_DONE')


if __name__ == '__main__':
    main()
