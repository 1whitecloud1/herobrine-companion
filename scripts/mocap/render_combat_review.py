"""Fixed front-quarter and side cameras for checking forward attack reach."""
import bpy
import sys
import json
import numpy as np
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_mocap'
scene = bpy.context.scene
args = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
animation = 'animation' in args
frames = [int(a) for a in args if a.isdigit()] or [1, 7, 14, 17, 22, 37, 54, 74]
scene.render.resolution_x = 1280
scene.render.resolution_y = 900
scene.render.resolution_percentage = 100
scene.eevee.taa_render_samples = 16
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGB'
scene.render.image_settings.compression = 20

def camera(name, direction):
    cam = bpy.data.objects.get(name)
    if cam is None:
        cam = bpy.data.objects.new(name, bpy.data.cameras.new(name))
        scene.collection.objects.link(cam)
    target = Vector((-.05, -.55, 1.65))
    cam.location = target+Vector(direction)
    cam.rotation_euler = (target-cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = 6.0
    cam.data.clip_end = 200
    return cam

views = [('quarter', camera('Camera | combat quarter', (8., -12., 3.2))),
         ('side', camera('Camera | combat side', (-13., -2., 2.0)))]
def fit_opening(cam):
    bpy.context.view_layer.update()
    view = np.asarray(cam.matrix_world.inverted())
    points = []
    for f in range(1, 83, 2):
        scene.frame_set(f)
        depsgraph = bpy.context.evaluated_depsgraph_get()
        for name in ['Character', 'Poem of the End | 原版镰刀']:
            obj = bpy.data.objects[name].evaluated_get(depsgraph)
            mesh = obj.to_mesh()
            co = np.empty(len(mesh.vertices)*3)
            mesh.vertices.foreach_get('co', co)
            co = np.c_[co.reshape(-1, 3), np.ones(len(mesh.vertices))]
            projected = co @ np.asarray(obj.matrix_world).T @ view.T
            points.append(projected[:, :2])
            obj.to_mesh_clear()
    points = np.vstack(points)
    low, high = points.min(axis=0), points.max(axis=0)
    center = (low+high)*.5
    orient = cam.matrix_world.to_3x3()
    cam.location += orient @ Vector((float(center[0]), float(center[1]), 0.))
    cam.data.ortho_scale = max(float(high[0]-low[0]), float(high[1]-low[1])*1280/900)*1.12

for view, cam in views:
    fit_opening(cam)
    scene.camera = cam
    folder = WORK / f'combat_{view}'
    folder.mkdir(exist_ok=True)
    if animation:
        scene.frame_start = 1
        scene.frame_end = 82
        scene.render.filepath = str(folder / 'frame_')
        bpy.ops.render.render(animation=True)
    else:
        for f in frames:
            scene.frame_set(f)
            scene.render.filepath = str(folder / f'frame_{f:04d}.png')
            bpy.ops.render.render(write_still=True)
            grips = {}
            rig = bpy.data.objects['Bones:Character']
            for side in ['Left', 'Right']:
                bone = rig.pose.bones[f'Arm:{side}:Lower']
                point = bone.matrix.translation+bone.matrix.col[1].to_3d()*.48
                projected = world_to_camera_view(scene, cam, rig.matrix_world@point)
                grips[side] = [float(projected.x*1280), float((1.-projected.y)*900)]
            (folder / f'grips_{f:04d}.json').write_text(json.dumps(grips), encoding='utf-8')
            print('COMBAT_REVIEW', view, f, flush=True)
print('COMBAT_REVIEW_COMPLETE', flush=True)
