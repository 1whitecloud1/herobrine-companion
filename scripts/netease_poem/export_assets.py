"""Export Unity12's corrected poses into a single NetEase attack library."""
import copy
import hashlib
import math
import runpy
import shutil

import numpy as np
from scipy.spatial import cKDTree
from scipy.spatial.transform import Rotation

from common import *

RUNTIME_CONFIG = runpy.run_path(str(ROOT/'scripts/netease_poem/runtime/HCPoemNetease/config.py'))
PLAYBACK_SPEED = RUNTIME_CONFIG['PLAYBACK_SPEED']
RECOVERY_SECONDS = RUNTIME_CONFIG['RECOVERY_SECONDS']

BODY_PARTS = ['Root', 'Torso', 'Chest', 'Head', 'Arm_R', 'Hand_R', 'Elbow_R',
              'Arm_L', 'Hand_L', 'Elbow_L', 'Thigh_R', 'Leg_R', 'Knee_R', 'Thigh_L', 'Leg_L', 'Knee_L']


def skin_box(origin, size, uv, full_height=None, cut_bottom=0., inflate=0.):
    """Split vanilla box UVs at the real rig hinge, without stretching the skin."""
    w, h, d = size
    full = full_height or h
    u, v = uv
    top = full - cut_bottom - h
    faces = {
        'north': {'uv': [u + d, v + d + top], 'uv_size': [w, h]},
        'east': {'uv': [u, v + d + top], 'uv_size': [d, h]},
        'south': {'uv': [u + 2 * d + w, v + d + top], 'uv_size': [w, h]},
        'west': {'uv': [u + d + w, v + d + top], 'uv_size': [d, h]},
        'up': {'uv': [u + d, v], 'uv_size': [w, d]},
        'down': {'uv': [u + d + w, v + d], 'uv_size': [w, -d]},
    }
    result = {'origin': origin, 'size': size, 'uv': faces}
    if inflate:
        result['inflate'] = inflate
    return result


def body_geometry(slim=False):
    _, _, rest = source_rig()
    bones = {n: {'name': PREFIX + n.lower(), 'pivot': np.round((B @ rest[n])[:3, 3], 6).tolist(), 'cubes': []}
             for n in BODY_PARTS}
    bones['Head']['cubes'] = [skin_box([-4, 24, -4], [8, 8, 8], [0, 0]),
                              skin_box([-4, 24, -4], [8, 8, 8], [32, 0], inflate=.5)]
    # Each limb has a rigid upper/lower piece and a narrow half-angle seam.
    definitions = [
        ('Torso', 'Chest', None, [-4, 12, -2], [8, 12, 4], [16, 16], [16, 32]),
        ('Leg_R', 'Thigh_R', 'Knee_R', [-4, 0, -2], [4, 12, 4], [0, 16], [0, 32]),
        ('Leg_L', 'Thigh_L', 'Knee_L', [0, 0, -2], [4, 12, 4], [16, 48], [0, 48]),
        ('Hand_R', 'Arm_R', 'Elbow_R', [-7 if slim else -8, 12, -2], [3 if slim else 4, 12, 4], [40, 16], [40, 32]),
        ('Hand_L', 'Arm_L', 'Elbow_L', [4, 12, -2], [3 if slim else 4, 12, 4], [32, 48], [48, 48]),
    ]
    for lower, upper, seam, origin, size, uv, outer in definitions:
        y = float(16 * rest[upper if upper == 'Chest' else lower][2, 3])
        split = y - origin[1]
        assert 0 < split < size[1]
        for name, offset, height in [(lower, 0., split), (upper, split, size[1] - split)]:
            o = [origin[0], origin[1] + offset, origin[2]]
            s = [size[0], height, size[2]]
            bones[name]['cubes'].append(skin_box(o, s, uv, size[1], offset))
            bones[name]['cubes'].append(skin_box(o, s, outer, size[1], offset, .25))
        if seam:
            o = [origin[0] + .04, y - .3, origin[2] + .04]
            s = [size[0] - .08, .6, size[2] - .08]
            bones[seam]['cubes'].append(skin_box(o, s, uv, size[1], split - .3))
    return {'description': {'identifier': 'geometry.hc_poem_v1.player_' + ('slim' if slim else 'wide'),
                            'texture_width': 64, 'texture_height': 64,
                            'visible_bounds_width': 4, 'visible_bounds_height': 5,
                            'visible_bounds_offset': [0, 1.5, 0]}, 'bones': list(bones.values())}


def weapon_geometry():
    geometry = copy.deepcopy(read(ASSETS / 'geo/item/poem_of_the_end.geo.json')['minecraft:geometry'][0])
    geometry['description'].update(identifier='geometry.hc_poem_v1.scythe', visible_bounds_width=24,
                                    visible_bounds_height=24, visible_bounds_offset=[0, 2, 0])
    for bone in geometry['bones']:
        bone['name'] = PREFIX + 'scythe_' + bone['name'].lower()
        bone['parent'] = PREFIX + 'scythe_' + bone['parent'].lower() if 'parent' in bone else PREFIX + 'weapon'
    geometry['bones'].insert(0, {'name': PREFIX + 'weapon', 'pivot': [0, 0, 0]})
    return geometry


def armor_geometry(slot):
    geometry = body_geometry(False)
    geometry['description'].update(identifier='geometry.hc_poem_v1.armor_'+str(slot), texture_height=32)
    allowed = ({'Head'}, {'Torso','Chest','Arm_R','Hand_R','Elbow_R','Arm_L','Hand_L','Elbow_L'},
               {'Torso','Chest','Thigh_R','Leg_R','Knee_R','Thigh_L','Leg_L','Knee_L'},
               {'Thigh_R','Leg_R','Knee_R','Thigh_L','Leg_L','Knee_L'})[slot]
    for bone, name in zip(geometry['bones'], BODY_PARTS):
        if name not in allowed or not bone.get('cubes'):
            bone['cubes'] = []
            continue
        cube = copy.deepcopy(bone['cubes'][0])
        if name != 'Head':
            origin, size = cube['origin'], cube['size']
            arm = name.startswith(('Arm','Hand','Elbow'))
            leg = name.startswith(('Leg','Thigh','Knee'))
            uv = [40,16] if arm else [0,16] if leg else [16,16]
            cube = skin_box(origin,size,uv,12,origin[1]-(0 if leg else 12))
        cube['inflate'] = .5 if slot==2 else 1.
        bone['cubes'] = [cube]
    return geometry


def library():
    timing = read(ASSETS / 'epicfight/poem_unity09_timing.json')
    extras = read(ASSETS / 'epicfight/poem_unity09_extras.json')
    entries, by_key = [], {}
    labels = {
        'normal': ['前跨横扫', '反手回斩', '支撑脚转体横扫', '转身双段挥镰'],
        'realm_breaker': ['斜切起手', '跨步回扫', '转髋斜斩', '回身连斩'],
        'thunder': ['上挑接横扫', '反向快速斩', '回旋抛镰与回收', '接镰转身斩'],
        'void_shatter': ['蓄势大横斩', '双段转体斩', '支撑脚回旋斩', '旋身终结斩'],
    }
    for mode in timing['modes']:
        for i, meta in enumerate(mode['segments']):
            key = mode['key'] + '/' + meta['name']
            entry = dict(meta, key=key, label=labels[mode['key']][i], context='ground')
            # The end of a source phrase has a long return-to-ready. Cut only
            # after its last swing, never inside a turn or a scythe return.
            entry['length'] = (min(meta['duration'], meta['contacts'][-1]['end'] + .12)
                               if i == 3 else meta['duration'])
            entries.append(entry); by_key[key] = entry
        for meta in mode['specials']:
            key = mode['key'] + '/' + meta['name']
            context = 'air' if meta['name'] == 'air' or mode['key'] == 'thunder' else 'sprint'
            label = ('腾空挥镰 ' if context == 'air' else '疾跑转身挥镰 ') + str(mode['mode'] + 1)
            entry = dict(meta, key=key, label=label, context=context)
            tail = .2 if mode['key'] == 'thunder' else .16
            entry['length'] = min(meta['duration'], meta['contacts'][-1]['end'] + tail)
            entries.append(entry); by_key[key] = entry
    for move in extras['moves']:
        for context in ('ground', 'air'):
            meta = move[context]
            key = 'extra/' + meta['name']
            entry = dict(meta, key=key, label=('空中' if context == 'air' else '') + move['label'], context=context)
            entry['length'] = min(meta['duration'], meta['contacts'][-1]['end'] + .14)
            entries.append(entry); by_key[key] = entry
    for i, entry in enumerate(entries, 1):
        entry['id'] = i
        entry['length'] = round(entry['length'], 6)
        entry['chain_at'] = entry['length']
        # Start the small physical follow-through just before the first swing.
        # Stored in source-animation seconds, like length/chain_at.
        entry['step_at'] = round(max(0., min(entry['contacts'][0]['start'] - .04,
                                            entry['length'] * .4)), 6)
        entry['animation'] = 'animation.hc_poem_v1.clip_%02d' % i
        entry['alias'] = PREFIX + 'clip_%02d' % i
    # All 16 ordinary attacks stay in their native phrase order. Six distinct
    # library moves bridge the phrases, forming one long ground chain.
    order = []
    for mode, bridge in [('normal', ['extra/heavy_ground', 'normal/dash']),
                         ('realm_breaker', ['extra/flurry_ground', 'realm_breaker/dash']),
                         ('thunder', ['extra/uppercut_ground']),
                         ('void_shatter', ['void_shatter/dash'])]:
        order += [mode + '/combo_%02d' % i for i in range(1, 5)] + bridge
    ground = [by_key[key]['id'] for key in order]
    air = [by_key[key]['id'] for key in ['normal/air', 'realm_breaker/air', 'extra/uppercut_air',
            'thunder/air', 'thunder/dash', 'extra/flurry_air', 'void_shatter/air', 'extra/heavy_air']]
    sprint = [by_key[key]['id'] for key in ['normal/dash', 'void_shatter/dash', 'realm_breaker/dash']]
    return entries, ground, air, sprint


def visual_world(times, source, context):
    """Keep the actor near its collision box; retain stride, turn and hip sway.

    Large Unity locomotion is factored out as a common
    translation, so both feet, both hands and the scythe keep their relative pose.
    Runtime adds a small horizontal impulse through server-side player physics.
    """
    world = {n: w.copy() for n, w in source.items()}
    root = source['Root'][:, :3, 3]
    path = root[:, :2] - root[0, :2]
    progress = times / max(times[-1], .001)
    detrended = path - progress[:, None] * path[-1]
    length = np.linalg.norm(detrended, axis=1)
    sway = detrended * np.minimum(1., .45 / np.maximum(length, 1e-8))[:, None]
    # A short lunge remains visible even for a nearly straight source root path.
    direction = path[-1] / max(np.linalg.norm(path[-1]), 1e-8)
    sway += np.sin(progress * math.pi)[:, None] * direction * .14
    shift = np.zeros((len(times), 3))
    shift[:, :2] = sway - root[:, :2]
    # Uppercut clips can rise >2 blocks. Keep the real player position under
    # input control and retain a bounded visual jump instead of a floating proxy.
    rise = root[:, 2] - root[0, 2]
    positive = np.maximum(rise, 0.)
    limit = .55 if context == 'ground' else .30
    shift[:, 2] = np.minimum(positive, limit) - positive
    for n in world:
        world[n][:, :3, 3] += shift
    return world, shift


def with_recovery(times, world, rest):
    # An explicit quaternion return is necessary: multiplying a wound 360-degree
    # Euler track by a fade weight would introduce a backwards spin on release.
    # Animation clocks run at 0.8x, while release recovery stays 0.14 real seconds.
    tail_seconds = RECOVERY_SECONDS * PLAYBACK_SPEED
    tail = np.linspace(0., tail_seconds, 10)[1:]
    result = {}
    for name, matrices in world.items():
        target = rest[name].copy()
        if name == 'Tool_R':
            target[:3, :3] *= .62
        blend = interpolate_matrices(np.array([0., tail_seconds]), np.array([matrices[-1], target]), tail)
        result[name] = np.concatenate([matrices, blend])
    return np.r_[times, times[-1] + tail], result


def make_controller(entries):
    states = {'idle': {'transitions': [{'clip_%02d' % e['id']: QUERY + 'clip == ' + str(e['id'])}
                                       for e in entries], 'blend_transition': .08}}
    for entry in entries:
        states['clip_%02d' % entry['id']] = {
            'animations': [entry['alias']], 'blend_transition': .08,
            'transitions': [{'idle': QUERY + 'active < 0.5'}] +
                [{'clip_%02d' % other['id']: QUERY + 'clip == ' + str(other['id'])}
                 for other in entries if other['id'] != entry['id']],
        }
    return {'format_version': '1.10.0', 'animation_controllers': {
        'controller.animation.hc_poem_v1.player': {'initial_state': 'idle', 'states': states}}}


def main():
    RP.mkdir(parents=True, exist_ok=True)
    BP.mkdir(parents=True, exist_ok=True)
    rig, names, rest = source_rig()
    entries, ground, air, sprint = library()
    geometry = body_geometry()
    weapon_geo = weapon_geometry()
    write(RP/'models/entity/hc_poem_v1_player.geo.json', {'format_version': '1.12.0',
                'minecraft:geometry': [geometry, body_geometry(True)]}, True)
    write(RP/'models/entity/hc_poem_v1_scythe.geo.json', {'format_version': '1.12.0',
                'minecraft:geometry': [weapon_geo]})
    write(RP/'models/entity/hc_poem_v1_armor.geo.json', {'format_version':'1.12.0',
                 'minecraft:geometry':[armor_geometry(slot) for slot in range(4)]})
    calibration = np.array(read(ROOT/'build/epicfight-aerial-dash/conversion_report.json')['weapon']['model_to_socket'])
    reflection = np.eye(4); reflection[:3, :3] = np.diag([-1., 1., 1.]) / 16.
    raw_to_library = calibration @ reflection
    source_geo = read(ASSETS/'geo/item/poem_of_the_end.geo.json')['minecraft:geometry'][0]
    original_points = geometry_points(source_geo)
    source_points = np.array(read(ASSETS/'poem_standalone/weapon.json')['vertices'])
    target_points = (np.c_[original_points, np.ones(len(original_points))] @ raw_to_library.T)[:, :3]
    nearest = cKDTree(source_points).query(target_points)[0]
    assert nearest.max() < .007, 'Existing weapon geometry no longer matches the calibrated source.'
    correction = np.array(rig['weapon_to_tool'])
    animations = {}
    stats = []
    for entry in entries:
        times, source = source_clip(entry['key'], entry['length'])
        world, shift = visual_world(times, source, entry['context'])
        times, world = with_recovery(times, world, rest)
        channels = {}
        max_residual = 0.
        for n in BODY_PARTS:
            skin = B @ world[n] @ np.linalg.inv(rest[n]) @ BI
            pivot = (B @ rest[n])[:3, 3]
            posed_pivot = np.einsum('nij,j->ni', skin[:, :3, :3], pivot) + skin[:, :3, 3]
            _, rot, scale, residual = decompose_series(skin)
            max_residual = max(max_residual, residual)
            channels[PREFIX+n.lower()] = {'position': curve(times, posed_pivot-pivot),
                                         'rotation': curve(times, rot), 'scale': curve(times, scale)}
        weapon = B @ world['Tool_R'] @ correction @ raw_to_library
        pos, rot, scale, residual = decompose_series(weapon)
        max_residual = max(max_residual, residual)
        channels[PREFIX+'weapon'] = {'position': curve(times, pos), 'rotation': curve(times, rot), 'scale': curve(times, scale)}
        # The same weapon track is adapted to the camera without turning the
        # camera itself. A separate root supplies the first-person scale/offset.
        anim = {'loop': 'hold_on_last_frame', 'animation_length': float(times[-1]),
                'anim_time_update': 'math.clamp(' + QUERY + 'time + math.min(math.max(query.life_time - ' + QUERY + 'stamp, 0), 0.1) * ' + QUERY + 'rate, 0, ' + str(float(times[-1])) + ')',
                'bones': channels}
        animations[entry['animation']] = anim
        file_name = 'hc_poem_v1_%02d.animation.json' % entry['id']
        write(RP/'animations'/file_name, {'format_version': '1.10.0', 'animations': {entry['animation']: anim}})
        source_file = ASSETS/'animmodels/animations/player/poem_unity09'/(entry['key']+'.json')
        entry['source_sha256'] = hashlib.sha256(source_file.read_bytes()).hexdigest()
        stats.append({'id': entry['id'], 'samples': len(times), 'trs_residual': max_residual,
                      'max_root_visual_offset_blocks': float(np.max(np.linalg.norm((world['Root'][:, :2, 3]), axis=1))),
                      'source': entry['key'], 'source_sha256': entry['source_sha256']})
        assert max_residual < .0001, (entry['key'], max_residual)
    # Only original model bones are hidden; the separate posed geometry keeps
    # its own names. The condition never applies to map portraits or paper dolls.
    mask = {'loop': True, 'override_previous_animation': True, 'bones': {
        n: {'scale': 0.} for n in ('body', 'head', 'hat', 'rightarm', 'leftarm', 'rightleg', 'leftleg',
                'jacket', 'rightpants', 'leftpants', 'rightsleeve', 'leftsleeve', 'cape')}}
    write(RP/'animations/hc_poem_v1_mask.animation.json', {'format_version':'1.10.0',
        'animations': {'animation.hc_poem_v1.hide_original': mask}})
    write(RP/'animation_controllers/hc_poem_v1.animation_controllers.json', make_controller(entries))
    # First person has its own named geometry root; third-person curves are
    # inherited as children, preserving blade roll and the release/regrip path.
    fp_geo = copy.deepcopy(weapon_geo)
    fp_geo['description']['identifier'] = 'geometry.hc_poem_v1.scythe_fp'
    fp_geo['bones'][0]['parent'] = PREFIX+'fp_root'
    fp_geo['bones'].insert(0, {'name': PREFIX+'fp_root', 'pivot':[0,0,0]})
    write(RP/'models/entity/hc_poem_v1_scythe_fp.geo.json', {'format_version':'1.12.0', 'minecraft:geometry':[fp_geo]})
    write(RP/'animations/hc_poem_v1_fp.animation.json', {'format_version':'1.10.0', 'animations':{
        'animation.hc_poem_v1.fp': {'loop':True, 'bones':{PREFIX+'fp_root':{'scale':.48, 'position':[-2, 10, -6]}}}}})
    controllers = {
        'controller.render.hc_poem_v1.body': {'arrays': {'geometries': {'Array.body': ['Geometry.hc_poem_v1_wide','Geometry.hc_poem_v1_slim']}},
            'geometry': 'Array.body['+QUERY+'slim]', 'materials':[{'*':'Material.default'}], 'textures':['Texture.default']},
        'controller.render.hc_poem_v1.weapon': {'geometry':'Geometry.hc_poem_v1_weapon', 'materials':[{'*':'Material.hc_poem_v1_weapon'}], 'textures':['Texture.hc_poem_v1_weapon']},
        'controller.render.hc_poem_v1.weapon_fp': {'geometry':'Geometry.hc_poem_v1_weapon_fp', 'materials':[{'*':'Material.hc_poem_v1_weapon'}], 'textures':['Texture.hc_poem_v1_weapon']},
    }
    for slot in range(4):
        textures = ['Texture.default'] + ['Texture.'+PREFIX+'armor_'+str(slot)+'_'+str(index) for index in range(1,8)]
        controllers['controller.render.hc_poem_v1.armor_'+str(slot)] = {
            'arrays':{'textures':{'Array.armor':textures}}, 'geometry':'Geometry.'+PREFIX+'armor_'+str(slot),
            'materials':[{'*':'Material.'+PREFIX+'armor'}], 'textures':['Array.armor['+QUERY+'armor_'+str(slot)+']']}
    write(RP/'render_controllers/hc_poem_v1.render_controllers.json', {'format_version':'1.10.0','render_controllers':controllers}, True)
    texture = ASSETS/'textures/item/poem_of_the_end_geo.png'
    target = RP/'textures/hc_poem_v1/scythe.png';target.parent.mkdir(parents=True, exist_ok=True);shutil.copyfile(texture,target)
    manifest = {'version':1, 'source':'Unity12 (leg stance corrected)', 'source_package':'Scythe Animation Pack1.0.unitypackage',
                'sample_hz':60, 'ground_chain':ground, 'air_chain':air, 'sprint_chain':sprint,
                'playback_speed':PLAYBACK_SPEED, 'recovery_seconds':RECOVERY_SECONDS,
                'ground_chain_source_seconds':round(sum(e['length'] for i in ground for e in entries if e['id']==i),6),
                'ground_chain_seconds':round(sum(e['length'] for i in ground for e in entries if e['id']==i)/PLAYBACK_SPEED,6),
                'clips':entries, 'visual_only':False, 'movement':'server_horizontal_follow_through',
                'step_forward_speed':RUNTIME_CONFIG['STEP_FORWARD_SPEED'], 'live_gameplay_tested':False}
    write(WORK/'library.json',manifest,True)
    write(WORK/'conversion_report.json', {'clips':stats,'weapon_geometry_max_difference_blocks':float(nearest.max()),
                 'source_leg_fix_retained':True,'raw_blade_roll_changed':False, 'live_gameplay_tested':False},True)
    (BP/'HCPoemNetease').mkdir(parents=True,exist_ok=True)
    runtime = {'ground':ground,'air':air,'sprint':sprint,
               'clips':{e['id']:{k:e[k] for k in ('id','length','chain_at','step_at','alias','animation','context')} for e in entries}}
    text = '# -*- coding: utf-8 -*-\n# Generated from the verified Unity12 library; Python 2.7 compatible.\n'
    text += 'DATA = ' + repr(runtime) + '\n'
    (BP/'HCPoemNetease/library.py').write_text(text,encoding='utf-8')
    print('EXPORTED',len(entries),'clips;',len(ground),'ground chain segments;',manifest['ground_chain_seconds'],'seconds; max TRS residual',max(s['trs_residual'] for s in stats))


if __name__ == '__main__':
    main()
