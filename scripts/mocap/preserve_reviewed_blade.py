"""Record the blade orientation the user reviewed before applying their flip."""
import json
from pathlib import Path
import bpy

path=Path(__file__).resolve().parents[2]/'build/scythe_turn_07/reviewed_blade_before.json'
if not path.exists():
    scene=bpy.context.scene
    rig=bpy.data.objects['Bones:Character']
    assert rig.animation_data.action.name.startswith('HB_Scythe_MediaPipe_Turning_07')
    frame=scene.frame_current
    rows=[]
    for f in range(scene.frame_start,scene.frame_end+1):
        scene.frame_set(f)
        bpy.context.view_layer.update()
        rows.append([list(row) for row in rig.pose.bones['Weapon:Scythe'].matrix.to_3x3().normalized()])
    path.write_text(json.dumps(dict(source_blend=bpy.data.filepath,
                        source_motion_sha256=rig.animation_data.action['motion_sha256'],
                        weapon_rotations=rows)),encoding='utf-8')
    scene.frame_set(frame)
    print('REVIEWED_BLADE_PRESERVED',len(rows))
