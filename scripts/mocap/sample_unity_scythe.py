"""Read native 60 fps FBX animation, including object root motion and weapon.

Run in background Blender. Only the extracted local package is read; sampled
data and the reference mesh are written under build/scythe_unity_pack_09.
"""
import contextlib
import io
import json
import re
import sys
from pathlib import Path

import bpy
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_unity_pack_09'
EXTRACTED = WORK / 'extracted/Assets/Scythe Animation Pack'
HZ = 120


def import_fbx(path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.context.scene.render.fps = 60
    # The package stores its animation-only skeleton in the first keyed pose.
    # Sample absolute matrices; copying those action deltas onto the model's
    # different bind pose would corrupt the retarget.
    with contextlib.redirect_stdout(io.StringIO()):
        bpy.ops.import_scene.fbx(filepath=str(path), use_anim=True,
                                 anim_offset=0.0, automatic_bone_orientation=False,
                                 use_custom_props=False)
    return next(o for o in bpy.context.scene.objects if o.type == 'ARMATURE')


def reference_model():
    rig = import_fbx(EXTRACTED / 'Model/9CG_Scythe.fbx')
    names = [b.name for b in rig.data.bones]
    ref = dict(bones=names, parents={b.name:b.parent.name if b.parent else None for b in rig.data.bones},
               rest={b.name:list(map(list, rig.matrix_world @ b.matrix_local)) for b in rig.data.bones}, meshes={})
    for obj in bpy.context.scene.objects:
        if obj.type != 'MESH':
            continue
        mesh = obj.data
        v = [list(obj.matrix_world @ p.co) for p in mesh.vertices]
        weights = []
        for p in mesh.vertices:
            weights.append({obj.vertex_groups[g.group].name:g.weight for g in p.groups if g.weight > 0})
        ref['meshes'][obj.name] = dict(vertices=v, faces=[list(p.vertices) for p in mesh.polygons],
                    uv=[list(x.uv) for x in mesh.uv_layers.active.data] if mesh.uv_layers else [],
                    weights=weights, parent=obj.parent.name if obj.parent else None, parent_bone=obj.parent_bone)
    (WORK/'native_reference.json').write_text(json.dumps(ref, separators=(',',':')), encoding='utf-8')
    w = np.array(ref['rest']['Scythe_Weapon_R']);r=w[:3,:3]/.01
    v=(np.array(ref['meshes']['Scythe']['vertices'])-w[:3,3]) @ r
    print('NATIVE_WEAPON_LOCAL_METERS', v.min(0).tolist(), v.max(0).tolist(), flush=True)
    return names


def main():
    sample_dir = WORK/'native_samples'
    sample_dir.mkdir(exist_ok=True)
    names = reference_model()
    args = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    paths = sorted((EXTRACTED/'Animations/FBX/02_Attack').rglob('*.fbx'))
    reports = []
    for path in paths:
        if path.stem.startswith('Target_'):
            continue  # The three execution victims use a different, unarmed rig.
        if args and path.stem not in args:
            continue
        text = path.with_suffix('.fbx.meta').read_text('utf-8')
        meta_first = float(re.search(r'firstFrame: (\S+)', text).group(1))
        meta_last = float(re.search(r'lastFrame: (\S+)', text).group(1))
        cache = sample_dir/(path.stem+'.npz')
        if cache.exists():
            data=np.load(cache)
            assert list(data['names'])==names
            times,matrices=data['times'],data['world']
            first,last=meta_first,meta_last
            assert abs(times[-1]-(last-first)/60)<1e-6
        else:
            rig = import_fbx(path)
            assert set(names) == {b.name for b in rig.data.bones}
            scene = bpy.context.scene
            action = rig.animation_data.action
            first, last = list(action.frame_range)
            assert scene.render.fps == 60
            assert abs(first-meta_first) < .001 and abs(last-meta_last) < .001, (path.stem,first,last,meta_first,meta_last)
            times = np.arange(round((last-first)/60*HZ)+1)/HZ
            matrices = np.empty((len(times),len(names),4,4), dtype=np.float32)
            root = np.empty((len(times),4,4), dtype=np.float32)
            for i,t in enumerate(times):
                frame = float(first+t*60)
                scene.frame_set(int(frame), subframe=frame%1)
                ev = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
                rw=ev.matrix_world
                root[i] = rw
                matrices[i] = [np.array(rw @ ev.pose.bones[n].matrix) for n in names]
            np.savez_compressed(cache, names=np.array(names), times=times, world=matrices, root=root)
        pi = names.index('pelvis')
        report = dict(name=path.stem, category=path.parent.relative_to(EXTRACTED/'Animations/FBX/02_Attack').as_posix(),
                      source=path.relative_to(WORK/'extracted').as_posix(), fps=60, source_first_frame=first,
                      source_last_frame=last, sample_hz=HZ, duration=float(times[-1]),
                      source_travel=(matrices[-1,pi,:3,3]-matrices[0,pi,:3,3]).tolist(),
                      victim=path.stem.startswith('Target_'), full_combo=path.stem.endswith('_All'))
        reports.append(report)
        print('SAMPLED',path.stem,round(times[-1],3),flush=True)
    (WORK/'sample_catalog.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding='utf-8')
    print('NATIVE_SAMPLING_COMPLETE',len(reports),sum(x['duration'] for x in reports),flush=True)


if __name__ == '__main__':
    main()
