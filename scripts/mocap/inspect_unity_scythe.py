"""Inspect the native FBX takes in the user's local Unity scythe package."""
import json
import sys
from pathlib import Path

import bpy
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_unity_pack_09'
EXTRACTED = WORK / 'extracted/Assets/Scythe Animation Pack'


def inspect(path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.fps = 60
    bpy.ops.import_scene.fbx(filepath=str(path), use_anim=True,
                             anim_offset=0.0, automatic_bone_orientation=False)
    rigs = [o for o in scene.objects if o.type == 'ARMATURE']
    result = dict(file=str(path), fps=scene.render.fps, fps_base=scene.render.fps_base,
                  objects=[dict(name=o.name, type=o.type, matrix=list(map(list, o.matrix_world)),
                                vertices=len(o.data.vertices) if o.type == 'MESH' else None,
                                modifiers=[dict(type=m.type, target=m.object.name if m.type == 'ARMATURE' and m.object else None) for m in o.modifiers])
                           for o in scene.objects], rigs=[], actions=[])
    for a in bpy.data.actions:
        result['actions'].append(dict(name=a.name, range=list(a.frame_range)))
    for rig in rigs:
        bones = [dict(name=b.name, parent=b.parent.name if b.parent else None,
                      head=list(b.head_local), tail=list(b.tail_local), rest=list(map(list,b.matrix_local)))
                 for b in rig.data.bones]
        action = rig.animation_data.action if rig.animation_data else None
        end = float(action.frame_range[1]) if action else 0.
        samples = []
        wanted = [b.name for b in rig.data.bones if b.name in ('root','pelvis','spine_01','spine_02','spine_03','spine_04','spine_05','head',
                   'upperarm_l','upperarm_r','lowerarm_l','lowerarm_r','hand_l','hand_r','thigh_l','thigh_r','calf_l','calf_r','foot_l','foot_r')
                  or any(x in b.name.lower() for x in ('weapon','scythe','sword'))]
        for frame in np.linspace(0.,end,7):
            scene.frame_set(int(frame), subframe=float(frame % 1))
            deps = bpy.context.evaluated_depsgraph_get()
            ev = rig.evaluated_get(deps)
            samples.append(dict(frame=float(frame), world={n:list(map(list,ev.matrix_world @ ev.pose.bones[n].matrix)) for n in wanted}))
        result['rigs'].append(dict(name=rig.name, action=action.name if action else None,
                                  bones=bones, samples=samples))
    return result


def main():
    args = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    names = args or ['9CG_Scythe', 'Combo_Attack_01_All', 'Combo_Attack_03_All', 'Run_Attack_02', 'Dash_Air_Attack']
    reports = []
    for name in names:
        found = list(EXTRACTED.rglob(name+'.fbx'))
        assert len(found) == 1, (name, found)
        data = inspect(found[0])
        (WORK / (name+'_inspection.json')).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
        print('FBX_INSPECT',json.dumps({k:v for k,v in data.items() if k!='rigs'}), flush=True)
        for rig in data['rigs']:
            print('BONES',name,len(rig['bones']),', '.join(b['name'] for b in rig['bones']), flush=True)
        reports.append(data)
    bpy.ops.wm.save_as_mainfile(filepath=str(WORK/'source_inspection.blend'))


if __name__ == '__main__':
    main()
