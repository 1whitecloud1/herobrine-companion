"""Run through Blender MCP. Create a preview from parsed Bedrock output."""
import bpy
import json
import math
import numpy as np
from pathlib import Path
from datetime import datetime
from mathutils import Matrix, Vector

ROOT = Path('E:/java/herobrine_companion')
WORK = ROOT/'build/netease_poem_longcombo_v1'
OUT = ROOT/'output/Herobrine_Poem_NetEase_LongCombo_01'
OUT.mkdir(parents=True, exist_ok=True)
if bpy.data.is_dirty and bpy.data.filepath:
    backup = WORK/('blender_before_netease_'+datetime.now().strftime('%Y%m%d_%H%M%S')+'.blend')
    bpy.ops.wm.save_as_mainfile(filepath=str(backup),copy=True)
data = json.loads((WORK/'preview.json').read_text(encoding='utf-8'))
matrices = np.load(WORK/'preview_matrices.npy')
scene = bpy.data.scenes.new('NetEase Poem - exported JSON')
scene['source'] = data['source']
scene['playback_speed'] = .5
scene['ground_chain_steps'] = data['ground_steps']
scene.render.engine = 'BLENDER_WORKBENCH'
scene.render.resolution_x = 960
scene.render.resolution_y = 720
scene.render.resolution_percentage = 100
scene.render.fps = 30
scene.render.image_settings.file_format = 'PNG'
scene.frame_start = 1
scene.frame_end = len(matrices)
scene.world = bpy.data.worlds.new('NetEase Poem world')
scene.world.color = (.08,.10,.14)
scene.display.shading.light = 'STUDIO'
scene.display.shading.studiolight_rotate_z = .5
scene.display.shading.color_type = 'TEXTURE'
scene.display.shading.show_shadows = True
scene.display.shading.show_cavity = True
scene.display.shading.cavity_type = 'BOTH'
scene.display.shading.show_object_outline = True
scene.display.shading.object_outline_color = (.025,.035,.05)
scene.display.shading.background_type = 'WORLD'
scene.view_settings.view_transform = 'Standard'


def material(name, image_path):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    image = bpy.data.images.load(str(image_path),check_existing=True)
    image.pack()
    texture = mat.node_tree.nodes.new('ShaderNodeTexImage')
    texture.image = image
    texture.interpolation = 'Closest'
    mat.node_tree.nodes.active = texture
    shader = mat.node_tree.nodes.get('Principled BSDF')
    mat.node_tree.links.new(texture.outputs['Color'],shader.inputs['Base Color'])
    mat.node_tree.links.new(texture.outputs['Alpha'],shader.inputs['Alpha'])
    shader.inputs['Roughness'].default_value = .7
    return mat


skin = material('NetEase original player skin',ROOT/'src/main/resources/assets/herobrine_companion/textures/entity/herobrine.png')
blade = material('NetEase original scythe',WORK/'resource_pack/textures/hc_poem_v1/scythe.png')
objects = []
for index, entry in enumerate(data['meshes']):
    mesh = bpy.data.meshes.new(entry['name'])
    mesh.from_pydata(entry['vertices'],[],entry['faces'])
    mesh.update()
    obj = bpy.data.objects.new(entry['name'],mesh)
    scene.collection.objects.link(obj)
    mesh.materials.append(skin if entry['kind']=='skin' else blade)
    layer = mesh.uv_layers.new()
    for loop in mesh.loops:
        layer.data[loop.index].uv = entry['uv'][loop.vertex_index]
    obj.rotation_mode = 'QUATERNION'
    previous = None
    for frame, sample in enumerate(matrices[:,index],1):
        location, rotation, scale = Matrix(sample.tolist()).decompose()
        if previous is not None and previous.dot(rotation) < 0:
            rotation.negate()
        previous = rotation.copy()
        obj.location, obj.rotation_quaternion, obj.scale = location, rotation, scale
        for path in ('location','rotation_quaternion','scale'):
            obj.keyframe_insert(data_path=path,frame=frame)
    objects.append(obj)
for obj in objects:
    for layer in obj.animation_data.action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    for point in curve.keyframe_points:
                        point.interpolation = 'LINEAR'

# A gridded floor makes the planted foot and cross-step easy to inspect.
floor_mat = bpy.data.materials.new('NetEase floor')
floor_mat.diffuse_color = (.13,.17,.21,1)
floor_mesh = bpy.data.meshes.new('NetEase floor')
floor_mesh.from_pydata([(-14,-14,-.04),(14,-14,-.04),(14,14,-.04),(-14,14,-.04)],[],[(0,1,2,3)])
floor_obj = bpy.data.objects.new('NetEase floor',floor_mesh)
scene.collection.objects.link(floor_obj)
floor_mesh.materials.append(floor_mat)
grid_mat = bpy.data.materials.new('NetEase grid')
grid_mat.diffuse_color = (.21,.27,.32,1)
for direction in range(2):
    for step in range(-12,13):
        mesh = bpy.data.meshes.new('grid')
        v = [[step-.009,-12,-.035],[step+.009,-12,-.035],[step+.009,12,-.035],[step-.009,12,-.035]]
        if direction:
            v = [[p[1],p[0],p[2]] for p in v]
        mesh.from_pydata(v,[],[(0,1,2,3)])
        obj = bpy.data.objects.new('grid',mesh)
        scene.collection.objects.link(obj)
        mesh.materials.append(grid_mat)

camera_data = bpy.data.cameras.new('NetEase review camera')
camera = bpy.data.objects.new(camera_data.name,camera_data)
scene.collection.objects.link(camera)
camera_data.type = 'ORTHO'
camera_data.ortho_scale = 5.8
camera.location = (5.8,10,4.6)
camera.rotation_euler = (Vector((0,0,1.0))-camera.location).to_track_quat('-Z','Y').to_euler()
scene.camera = camera
# Static body view for inspection. The wide shot camera is also included for
# the intentional thrown-scythe segment, whose radius exceeds normal framing.
wide_data = camera_data.copy();wide_data.name = 'NetEase whole trajectory camera';wide_data.ortho_scale = 16
wide = bpy.data.objects.new(wide_data.name,wide_data)
scene.collection.objects.link(wide)
wide.location = camera.location
wide.rotation_euler = camera.rotation_euler
for index, entry in enumerate(data['schedule']):
    if entry['time'] == 0:
        marker = scene.timeline_markers.new('%02d %s' % (entry['step'],entry['source']),frame=index+1)
scene.frame_set(91)
bpy.context.window.scene = scene
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == 'VIEW_3D':
            area.spaces.active.region_3d.view_perspective = 'CAMERA'
target = OUT/'NetEase_终末之诗_长连招验证.blend'
bpy.data.libraries.write(str(target),{scene},fake_user=True,compress=True)
report = {'file':str(target),'frames':scene.frame_end,'playback_fps':30,'source_fps':60,
          'render_engine':scene.render.engine,'objects':len(objects),'source':data['source'],
          'live_gameplay_tested':False}
(WORK/'blender_preview_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
scene.render.filepath = str(WORK/'preview_pose.png')
bpy.ops.render.render(write_still=True,scene=scene.name)
print('NETEASE_PREVIEW_READY',json.dumps(report,ensure_ascii=True))
