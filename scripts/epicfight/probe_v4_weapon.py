"""Read-only probe: how does the V4 source .blend actually place the scythe?

Run: blender --background --factory-startup --disable-autoexec <V4.blend> \
         --python scripts/epicfight/probe_v4_weapon.py
Nothing is written into the .blend; results go to stdout as PROBE_* JSON lines.
"""
import json
import math
from pathlib import Path

import bpy
import numpy as np

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / 'build/epicfight-v4'
HZ = 120


def m16(m):
    return [round(float(v), 8) for v in np.array(m).reshape(-1)]


def main():
    print('PROBE_FILE', bpy.data.filepath)
    print('PROBE_REPO', str(REPO))
    objects = []
    for ob in bpy.data.objects:
        entry = {
            'name': ob.name,
            'type': ob.type,
            'parent': ob.parent.name if ob.parent else None,
            'parent_type': ob.parent_type,
            'parent_bone': ob.parent_bone or None,
            'verts': len(ob.data.vertices) if ob.type == 'MESH' else 0,
            'matrix_world': m16(ob.matrix_world),
            'scale': m16(np.diag(np.array(ob.matrix_world)[:3, :3])),
        }
        if ob.type == 'MESH' and len(ob.data.vertices):
            co = np.array([v.co[:] for v in ob.data.vertices])
            entry['local_bbox'] = [co.min(axis=0).round(6).tolist(), co.max(axis=0).round(6).tolist()]
        entry['modifiers'] = [m.type for m in ob.modifiers]
        objects.append(entry)
    print('PROBE_OBJECTS', json.dumps(objects, ensure_ascii=False))

    rig = bpy.data.objects.get('Bones:Character')
    if rig is None:
        print('PROBE_NO_RIG')
        return
    print('PROBE_RIG', json.dumps({'matrix_world': m16(rig.matrix_world),
                                   'scale': m16(rig.scale),
                                   'bones': [b.name for b in rig.data.bones]}, ensure_ascii=False))
    for name in ('Weapon:Scythe', 'Bone.011', 'Arm:Right:Lower'):
        bone = rig.data.bones.get(name)
        if bone is None:
            print('PROBE_BONE_MISSING', name)
            continue
        print('PROBE_BONE', json.dumps({
            'name': name,
            'parent': bone.parent.name if bone.parent else None,
            'matrix_local': m16(bone.matrix_local),
            'head_local': [round(float(v), 8) for v in bone.head_local],
            'tail_local': [round(float(v), 8) for v in bone.tail_local],
        }, ensure_ascii=False))

    scene = bpy.context.scene
    action = rig.animation_data.action if rig.animation_data else None
    print('PROBE_ACTION', action.name if action else None,
          list(action.frame_range) if action else None,
          scene.render.fps / scene.render.fps_base)

    mesh_names = [ob['name'] for ob in objects if ob['type'] == 'MESH' and ob['verts']]
    ticks = [0, 6, 12, 24, 36, 48, 54, 60, 72, 84, 96, 108, 120]
    rows = []
    for tick in ticks:
        frame = 1 + tick * 30 / HZ
        scene.frame_set(math.floor(frame), subframe=frame % 1)
        deps = bpy.context.evaluated_depsgraph_get()
        evaluated = rig.evaluated_get(deps)
        pose = np.array(evaluated.pose.bones['Weapon:Scythe'].matrix)
        hand = np.array(evaluated.pose.bones['Arm:Right:Lower'].matrix)
        row = {'tick': tick,
               'P_weapon_armature': m16(pose),
               'P_hand_armature': m16(hand),
               'meshes': {}}
        for name in mesh_names:
            ob = bpy.data.objects[name]
            ob_eval = ob.evaluated_get(deps)
            world = np.array(ob_eval.matrix_world)
            co = np.array([v.co[:] for v in ob_eval.data.vertices])
            pts = (np.c_[co, np.ones(len(co))] @ world.T)[:, :3]
            row['meshes'][name] = {'matrix_world': m16(world),
                                   'world_bbox': [pts.min(axis=0).round(6).tolist(),
                                                  pts.max(axis=0).round(6).tolist()],
                                   'centroid': pts.mean(axis=0).round(6).tolist()}
        rows.append(row)
    print('PROBE_TICKS', json.dumps(rows, ensure_ascii=False))
    print('PROBE_DONE')


if __name__ == '__main__':
    main()
