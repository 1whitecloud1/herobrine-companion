"""Build an editable Blender project from the shipped Epic Fight animations."""
import hashlib
import json
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix, Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/Herobrine_Scythe_UnityModes_10_EpicFight'
ASSETS = ROOT / 'src/main/resources/assets/herobrine_companion'
load = lambda p: json.loads(p.read_text(encoding='utf-8'))


def material(name, color, texture=None):
    mat = bpy.data.materials.new(name); mat.use_nodes = True
    shader = mat.node_tree.nodes.get('Principled BSDF')
    shader.inputs['Base Color'].default_value = (*color, 1)
    shader.inputs['Roughness'].default_value = .65
    if texture:
        node = mat.node_tree.nodes.new('ShaderNodeTexImage'); node.image = bpy.data.images.load(str(texture))
        node.interpolation = 'Closest'; node.image.pack()
        mat.node_tree.links.new(node.outputs['Color'], shader.inputs['Base Color'])
    return mat


def mesh(name, vertices, faces, mat, uv):
    data = bpy.data.meshes.new(name); data.from_pydata(vertices, [], faces); data.materials.append(mat)
    if uv:
        layer = data.uv_layers.new()
        for p, co in zip(layer.data, uv): p.uv = co
    obj = bpy.data.objects.new(name, data); bpy.context.scene.collection.objects.link(obj)
    return obj


def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene; scene.name = '终末之诗 · Unity 四模式'
    scene.render.fps = 60; scene.render.fps_base = 1
    scene.render.engine = 'BLENDER_EEVEE'; scene.eevee.taa_render_samples = 32
    scene.render.resolution_x = 1280; scene.render.resolution_y = 960; scene.render.resolution_percentage = 100
    scene.world = bpy.data.worlds.new('Studio'); scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.10, .13, .18, 1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value = .6
    scene.view_settings.view_transform = 'Standard'
    model = load(ROOT / 'build/epicfight-mediapipe/biped.json')
    local, rest, parents = {}, {}, {}
    def walk(nodes, parent=None):
        for node in nodes:
            n = node['name']; parents[n] = parent; local[n] = np.array(node['transform']).reshape(4, 4)
            rest[n] = rest[parent] @ local[n] if parent else local[n]
            walk(node.get('children', []), n)
    walk(model['armature']['hierarchy'])
    armature = bpy.data.armatures.new('Epic Fight · 20 joints')
    rig = bpy.data.objects.new('Herobrine · Epic Fight rig', armature); scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig; rig.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    for n in rest:
        bone = armature.edit_bones.new(n); bone.head = (0, 0, 0); bone.tail = (0, .16, 0); bone.matrix = Matrix(rest[n])
        if parents[n]: bone.parent = armature.edit_bones[parents[n]]
        bone.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    rest_error = max(float(np.abs(np.array(armature.bones[n].matrix_local) - rest[n]).max()) for n in rest)
    print('RIG_REST_ERROR', rest_error, flush=True)
    assert rest_error < .001, rest_error
    blender_rest = {n: np.array(armature.bones[n].matrix_local) for n in rest}
    blender_local = {n: np.linalg.inv(blender_rest[parents[n]]) @ blender_rest[n] if parents[n] else blender_rest[n] for n in rest}
    rig.show_in_front = True; armature.display_type = 'STICK'
    for pb in rig.pose.bones: pb.rotation_mode = 'QUATERNION'
    v = model['vertices']; vertices = np.array(v['positions']['array']).reshape(-1, 3)
    uv = np.array(v['uvs']['array']).reshape(-1, 2); faces = []; uvs = []
    for name, part in v['parts'].items():
        if name.endswith(('Sleeve', 'Pants')) or name in ('hat', 'jacket'): continue
        for tri in np.array(part['array']).reshape(-1, 3, 3):
            faces.append(tuple(int(i) for i in tri[:, 0])); uvs.extend([(float(uv[k, 0]), 1-float(uv[k, 1])) for k in tri[:, 1]])
    body = mesh('Herobrine · 游戏网格', vertices, faces, material('Herobrine skin', (.1, .6, .6), ASSETS / 'textures/entity/herobrine.png'), uvs)
    for n in rest: body.vertex_groups.new(name=n)
    cursor = 0
    for i, count in enumerate(v['vcounts']['array']):
        for _ in range(count):
            j, wi = v['vindices']['array'][cursor:cursor+2]; cursor += 2
            body.vertex_groups[model['armature']['joints'][j]].add([i], v['weights']['array'][wi], 'REPLACE')
    modifier = body.modifiers.new('Epic Fight linear skinning', 'ARMATURE'); modifier.object = rig
    modifier.use_deform_preserve_volume = False; body.parent = rig
    weapon = load(ROOT / 'build/epicfight-aerial-dash/reference_samples.json')
    scythe = mesh('终末之诗 · 校准后的游戏镰刀', weapon['source_weapon_vertices'], weapon['source_weapon_faces'],
                  material('Poem of the End', (.2, .6, .7), ASSETS / 'textures/item/poem_of_the_end_geo.png'), weapon['source_weapon_uv'])
    calibration = load(ROOT / 'build/epicfight-aerial-dash/conversion_report.json')['weapon']
    correction = np.array(calibration['model_to_socket']) @ np.linalg.inv(calibration['item_render_transform'])
    socket = bpy.data.objects.new('Tool_R · 游戏武器插槽', None); scene.collection.objects.link(socket)
    constraint = socket.constraints.new('COPY_TRANSFORMS'); constraint.target = rig; constraint.subtarget = 'Tool_R'
    scythe.parent = socket; scythe.matrix_basis = Matrix(np.linalg.inv(correction))
    timeline = load(ASSETS / 'epicfight/poem_unity09_timing.json')
    rig.animation_data_create(); rig.animation_data.use_nla = False
    actions = []; errors = []
    names = {'full': '完整连招', 'dash': '疾跑攻击', 'air': '空中攻击', 'ready': '战斗待机', 'hold': '移动持镰'}
    extras = load(ASSETS / 'epicfight/poem_unity09_extras.json')
    extra_names = {m[k]['name']: m['label'] + (' · 地面' if k == 'ground' else ' · 空中') for m in extras['moves'] for k in ('ground', 'air')}
    names.update(extra_names)
    groups = [(m, ['full', 'combo_01', 'combo_02', 'combo_03', 'combo_04', 'dash', 'air', 'ready', 'hold']) for m in timeline['modes']]
    groups.append((dict(key='extra', label='额外招式'), list(extra_names)))
    for mode, motions in groups:
        for motion in motions:
            path = ASSETS / ('animmodels/animations/player/poem_unity09/' + mode['key'] + '/' + motion + '.json')
            rows = load(path)['animation']; times = np.array(rows[0]['time']); frames = 1 + times * 60
            action = bpy.data.actions.new(mode['label'] + ' · ' + names.get(motion, '连招 ' + motion[-2:]))
            action.use_fake_user = True; action.use_frame_range = True
            action.frame_start = 1; action.frame_end = float(frames[-1])
            action['epicfight_resource'] = 'player/poem_unity09/' + mode['key'] + '/' + motion
            action['json_sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
            action['native_speed'] = 1.; action['source_fps'] = 60; action['retarget_sample_hz'] = 240
            if mode['key'] == 'extra':
                timing = next(t for m in extras['moves'] for t in (m['ground'], m['air']) if t['name'] == motion)
            elif motion in ('full', 'ready', 'hold'):
                timing = None
            else:
                timing = next(t for t in mode['segments'] + mode['specials'] if t['name'] == motion)
            if timing:
                action['source_clip'] = timing['source_clip']
                action['chain_window_seconds'] = timing['recovery']
                action['last_contact_end_seconds'] = timing['contacts'][-1]['end']
            slot = action.slots.new(id_type='OBJECT', name=rig.name)
            layer = action.layers.new('Epic Fight'); strip = layer.strips.new(type='KEYFRAME')
            bag = strip.channelbag(slot, ensure=True)
            channels = {r['name']: np.array(r['transform']).reshape(-1, 4, 4) for r in rows}
            for n in rest:
                transforms = channels.get(n, np.broadcast_to(local[n], (len(frames), 4, 4)))
                bases = np.linalg.inv(blender_local[n]) @ transforms
                values = {'location': [], 'rotation_quaternion': [], 'scale': []}; previous = None
                for matrix in bases:
                    location, q, scale = Matrix(matrix).decompose()
                    if previous is not None and q.dot(previous) < 0: q.negate()
                    previous = q.copy(); values['location'].append(tuple(location)); values['rotation_quaternion'].append(tuple(q)); values['scale'].append(tuple(scale))
                for attribute, array in values.items():
                    array = np.array(array)
                    for column in range(array.shape[1]):
                        curve = bag.fcurves.new(data_path='pose.bones["' + n + '"].' + attribute, index=column)
                        curve.keyframe_points.add(len(frames))
                        curve.keyframe_points.foreach_set('co', np.c_[frames, array[:, column]].ravel())
                        for key in curve.keyframe_points: key.interpolation = 'LINEAR'
                        curve.update()
            rig.animation_data.action = action; rig.animation_data.action_slot = slot
            for sample in (0, len(frames)//2, len(frames)-1):
                frame = float(frames[sample]); scene.frame_set(int(frame), subframe=frame-int(frame))
                bpy.context.view_layer.update(); evaluated = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
                wanted = {}
                for n in rest:
                    value = channels[n][sample] if n in channels else local[n]
                    wanted[n] = wanted[parents[n]] @ value if parents[n] else value
                    errors.append(float(np.abs(np.array(evaluated.pose.bones[n].matrix) - wanted[n]).max()))
            actions.append((mode['key'], motion, action, slot))
            print('BAKED_NATIVE_MODE', action.name, len(frames), flush=True)
    assert max(errors) < .003, max(errors)
    default = next(row for row in actions if row[:2] == ('extra', 'heavy_ground'))
    rig.animation_data.action = default[2]; rig.animation_data.action_slot = default[3]
    scene.frame_start = 1; scene.frame_end = round(default[2].frame_end); scene.frame_set(22)
    heavy = next(m['ground'] for m in extras['moves'] if m['key'] == 'heavy')
    scene.timeline_markers.new('重击 · 命中开始', frame=1+round(heavy['contacts'][0]['start']*60))
    scene.timeline_markers.new('重击 · 命中结束', frame=1+round(heavy['contacts'][-1]['end']*60))
    scene.timeline_markers.new('重击 · 可接普攻', frame=1+round(heavy['recovery']*60))
    bpy.ops.mesh.primitive_plane_add(size=1000)
    ground = bpy.context.object; ground.name = '地面 · 每格 0.5 方块'
    floor = material('Half-block grid', (.12, .16, .21)); ground.data.materials.append(floor)
    nodes = floor.node_tree.nodes; checker = nodes.new('ShaderNodeTexChecker'); geo = nodes.new('ShaderNodeNewGeometry')
    checker.inputs['Color1'].default_value = (.105, .137, .18, 1); checker.inputs['Color2'].default_value = (.14, .177, .23, 1); checker.inputs['Scale'].default_value = 2
    floor.node_tree.links.new(geo.outputs['Position'], checker.inputs['Vector']); floor.node_tree.links.new(checker.outputs['Color'], nodes.get('Principled BSDF').inputs['Base Color'])
    focus = bpy.data.objects.new('Camera follows Root', None); scene.collection.objects.link(focus); focus.location.z = .5
    follow = focus.constraints.new('COPY_LOCATION'); follow.target = rig; follow.subtarget = 'Root'; follow.use_offset = True
    camera = bpy.data.objects.new('Review camera', bpy.data.cameras.new('Review camera')); scene.collection.objects.link(camera)
    camera.parent = focus; camera.location = (3, 7, 3); camera.data.type = 'ORTHO'; camera.data.ortho_scale = 6.5
    track = camera.constraints.new('TRACK_TO'); track.target = focus; track.track_axis = 'TRACK_NEGATIVE_Z'; track.up_axis = 'UP_Y'; scene.camera = camera
    for pos, power in [((1, 4, 7), 1700), ((-5, -2, 5), 1400)]:
        data = bpy.data.lights.new('Studio softbox', 'AREA'); data.energy = power; data.size = 7
        lamp = bpy.data.objects.new(data.name, data); scene.collection.objects.link(lamp); lamp.parent = focus; lamp.location = pos
        track = lamp.constraints.new('TRACK_TO'); track.target = focus; track.track_axis = 'TRACK_NEGATIVE_Z'; track.up_axis = 'UP_Y'
    rig['source_package'] = 'Scythe Animation Pack1.0.unitypackage'
    rig['mapping'] = '普通: Combo 01 / 破境: Combo 02 / 鸣雷: Combo 03 / 碎空: Combo 04'
    rig['review'] = '42 actions; native speed at 60 fps. Includes three ground/air gestures and heavy attack.'
    text = bpy.data.texts.new('README · 四模式')
    text.write('终末之诗 Unity 四模式\n\n动作编辑器中选择普通、破境、鸣雷、碎空。每种模式含四段连招、疾跑、空中、完整连招、待机与持镰动作。\n'
               '短按左键松开接普攻；长按左键0.35秒重击，每次按住只触发一次。侧键4＋左键速斩，侧键5＋左键挑空，右键＋左键重击。\n'
               '地面重击换为第5组踏步挥斩；重击末次命中后0.12秒、普攻末段末次命中后0.10秒可衔接下一击。\n'
               '游戏中的空中重击会向真实地面下降，并在原动作的空中举镰段等待接近地面，最多3秒；本工程展示原始时间轴。\n'
               '源 FBX 为 60 FPS，当前工程按原速播放；独立 MP4 提供 0.5 倍速。\n'
               '骨架、网格和武器为实际游戏数据。移动持镰在游戏中以手臂叠加层播放。\n'
               '方块体型适配包含握杆滑动、地面净空修正，以及不超过 6% 的手臂比例补偿。极端交叉握持仍可能短暂穿过方块躯干。\n'
               'V6 资源保留；四种模式当前均使用 Unity 动作。未进行真人进游戏测试。\n')
    for obj in bpy.context.selected_objects: obj.select_set(False)
    body.select_set(True); bpy.context.view_layer.objects.active = body
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == 'VIEW_3D':
                area.spaces.active.region_3d.view_perspective = 'CAMERA'
                area.spaces.active.shading.type = 'MATERIAL'; area.spaces.active.overlay.show_overlays = False
            elif area.type == 'DOPESHEET_EDITOR': area.spaces.active.mode = 'ACTION'
    path = OUT / 'Herobrine_终末之诗_Unity10_四模式与重击.blend'
    bpy.ops.wm.save_as_mainfile(filepath=str(path), compress=True)
    report = dict(actions=len(actions), bones=len(rest), max_evaluated_matrix_error=max(errors),
                  path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    (OUT / 'reports/blender_project_validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print('UNITY_MODES_BLEND_COMPLETE', json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == '__main__': main()
