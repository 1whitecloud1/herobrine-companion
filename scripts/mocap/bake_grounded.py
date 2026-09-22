"""Bake the combat edit in the dedicated Blender MCP authoring process."""
import json
import hashlib
import os
from pathlib import Path
import bpy
from mathutils import Matrix, Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(os.environ.get('HEROBRINE_MOCAP_OUTPUT', ROOT/'output/Herobrine_Scythe_Grounded_Combat'))
data = json.loads((OUT/'capture/target_motion.json').read_text(encoding='utf-8'))
source = ROOT/'output/Herobrine_Scythe_MediaPipe/Herobrine_镰刀动捕_MediaPipe.blend'
bpy.ops.wm.open_mainfile(filepath=str(source))
rig = bpy.data.objects['Bones:Character']
scene = bpy.context.scene
rig.animation_data_clear()
action = bpy.data.actions.new(data['action'])
action.use_fake_user = True
rig.animation_data_create()
rig.animation_data.action = action
old_quat = {}
for f, row in zip(data['frames'], data['poses']):
    for pb in rig.pose.bones:
        if pb.name not in row:
            continue
        for constraint in pb.constraints:
            constraint.mute = True
        kwargs = {'parent_matrix':Matrix(row[pb.parent.name]), 'parent_matrix_local':pb.parent.bone.matrix_local} if pb.parent else {}
        basis = pb.bone.convert_local_to_pose(Matrix(row[pb.name]), pb.bone.matrix_local, invert=True, **kwargs)
        loc, quat, scale = basis.decompose()
        if pb.name in old_quat and quat.dot(old_quat[pb.name]) < 0:
            quat.negate()
        old_quat[pb.name] = quat.copy()
        pb.rotation_mode = 'QUATERNION'
        pb.location, pb.rotation_quaternion, pb.scale = loc, quat, scale
        for prop in ['location', 'rotation_quaternion', 'scale']:
            pb.keyframe_insert(prop, frame=f, group=pb.name)
for layer in action.layers:
    for strip in layer.strips:
        for bag in strip.channelbags:
            for fc in bag.fcurves:
                for point in fc.keyframe_points:
                    point.interpolation = 'LINEAR'
action['source_url'] = data['source_url']
action['motion_sha256'] = hashlib.sha256((OUT/'capture/target_motion.json').read_bytes()).hexdigest()
action['capture_engine'] = 'Google MediaPipe GHUM Heavy + reviewed combat retarget'
action['combat_cleanup'] = data['combat_cleanup']['notes']
action.frame_start, action.frame_end = 1, data['frames'][-1]
action.use_frame_range = True
scene.render.fps, scene.render.fps_base = data['fps'], data['fps_base']
scene.frame_start, scene.frame_end = 1, data['frames'][-1]
scene.timeline_markers.clear()
for s in data['attack_segments']:
    scene.timeline_markers.new(s['label'], frame=s['first_frame'])
camera = bpy.data.objects.new('Camera | advancing combat', bpy.data.cameras.new('Camera | advancing combat'))
scene.collection.objects.link(camera)
camera.data.type, camera.data.ortho_scale = 'ORTHO', 7.2
direction = Vector((6, -10, 4)).normalized()
camera.rotation_euler = (-direction).to_track_quat('-Z', 'Y').to_euler()
for f, row in zip(data['frames'], data['poses']):
    target = rig.matrix_world @ (Matrix(row['Bone.011']).translation+Vector((0, 0, .45)))
    camera.location = target+direction*20
    camera.keyframe_insert('location', frame=f)
scene.camera = camera
scene.render.resolution_x, scene.render.resolution_y = 960, 720
scene.render.resolution_percentage = 100
scene.render.engine = 'BLENDER_EEVEE'
scene['motion_notes'] = data['combat_cleanup']['notes']
scene['preview_rates'] = '60 fps combat timing; reference reviewed at 0.5x'
rig['combat_grip'] = 'Left hand: shaft middle (-1.25). Right hand: tail (-3.00). No releases.'
errors = []
for f in sorted({1, data['frames'][-1]} | {s['contact_frames'][0][0] for s in data['attack_segments']}):
    scene.frame_set(f)
    bpy.context.view_layer.update()
    evaluated = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
    wanted = data['poses'][f-1]
    errors.append(max(abs(evaluated.pose.bones[n].matrix[r][c]-wanted[n][r][c]) for n in wanted for r in range(4) for c in range(4)))
assert max(errors) < .0001, max(errors)
scene.frame_set(1)
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == 'VIEW_3D':
            area.spaces.active.region_3d.view_perspective = 'CAMERA'
filenames = {'mediapipe_recapture_05': 'Herobrine_半速重捕_八式进步镰斩.blend',
             'mediapipe_wide_swing_06': 'Herobrine_大幅镰斩_Wide06.blend',
             'mediapipe_turning_07': 'Herobrine_转体镰斩_Turn07.blend',
             'mediapipe_aerial_dash_08': 'Herobrine_腾空旋镰_AerialDash08.blend'}
path = OUT/filenames.get(data['combat_cleanup']['version'], 'Herobrine_跨步快斩_MediaPipe.blend')
bpy.ops.wm.save_as_mainfile(filepath=str(path), compress=True)
report = {'file':str(path), 'frames':len(data['frames']), 'fps':data['fps'], 'max_matrix_error':max(errors),
          'source_sha256':action['motion_sha256'],
          'connection':'Blender MCP 127.0.0.1:9877', 'source_blend_preserved':str(source)}
(OUT/'capture/blender_bake_report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print('GROUNDED_BAKE', json.dumps(report, ensure_ascii=True), flush=True)
