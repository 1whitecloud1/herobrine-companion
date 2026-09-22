"""Decode the game assets, check both grips/FK and render the actual EF mesh."""
import hashlib
import json
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix, Vector

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / 'build/epicfight-mediapipe'
ASSETS = REPO / 'src/main/resources/assets/herobrine_companion'
DEST = ASSETS / 'animmodels/animations/player/poem_mediapipe'
PREVIEW = Path(sys.argv[sys.argv.index('--preview-dir')+1]) if '--preview-dir' in sys.argv else OUT/'preview'
load = lambda p: json.loads(p.read_text(encoding='utf-8'))
report, source, mesh_data = [load(OUT / n) for n in ('conversion_report.json', 'reference_samples.json', 'biped.json')]
local, rest, parents = {}, {}, {}


def walk(nodes, parent=None):
    for node in nodes:
        n = node['name']
        local[n] = np.array(node['transform']).reshape(4, 4)
        parents[n] = parent
        rest[n] = (rest[parent] if parent else np.eye(4)) @ local[n]
        walk(node.get('children', []), n)
walk(mesh_data['armature']['hierarchy'])


def decode(name):
    rows = load(DEST / (name + '.json'))['animation']
    times = np.array(rows[0]['time'])
    assert times[0] == 0 and np.all(np.diff(times) > 0)
    assert len(rows) == len({r['name'] for r in rows})
    values = {}
    for row in rows:
        assert row['name'] in rest and np.array_equal(times, row['time'])
        m = np.array(row['transform']).reshape(-1, 4, 4)
        assert len(m) == len(times) and np.isfinite(m).all()
        assert np.max(np.abs(m[:, 3] - [0, 0, 0, 1])) < 1e-7
        values[row['name']] = m
    return times, values


def compose(values, tick, runtime=True):
    world = {}
    i, u = int(tick), tick % 1
    for n in rest:
        m = values[n][i] if n in values else local[n]
        if runtime and n in values:
            inverse = np.linalg.inv(local[n])
            t, q, s = Matrix(inverse @ m).decompose()
            if u:
                t2, q2, s2 = Matrix(inverse @ values[n][i + 1]).decompose()
                t, q, s = t.lerp(t2, u), q.slerp(q2, u), s.lerp(s2, u)
            m = local[n] @ np.array(Matrix.LocRotScale(t, q, s))
        world[n] = (world[parents[n]] if parents[n] else np.eye(4)) @ m
    return world


times, values = decode('full')
fps = report['source_fps']
assert len(values) == 20 and len(times) == report['samples'] and abs(times[-1] - report['duration_seconds']) < 1e-6
max_seam, last = 0., 1
for s in report['segments']:
    t, vv = decode(s['name'])
    a, b = [int(np.argmin(np.abs(times - (f - 1) / fps))) for f in (s['first_frame'], s['last_frame'])]
    assert s['first_frame'] == last
    last = s['last_frame']
    assert abs(t[-1] - s['duration']) < 1e-6
    assert all(0 <= h['start'] < h['end'] < s['recovery'] < s['duration'] for h in s['contacts'])
    for n, m in vv.items():
        expected = values[n][a:b + 1].copy()
        if n == 'Root':
            expected[:, :2, 3] -= expected[0, :2, 3] - rest[n][:2, 3]
        max_seam = max(max_seam, float(np.max(np.abs(m - expected))))
assert last == report['source_frames'] and max_seam < 3e-7
for s in load(ASSETS/'epicfight/poem_mediapipe_timing.json')['specials']:
    t, vv = decode(s['name'])
    assert abs(t[-1] - s['duration']) < 1e-6
    assert all(0 <= h['start'] < h['end'] < s['recovery'] < t[-1] for h in s['contacts'])
    if s['name'] == 'air':
        assert np.ptp(vv['Root'][:, :2, 3], axis=0).max() < 1e-7
for name in ('ready', 'hold'):
    t, vv = decode(name)
    assert all(np.array_equal(m[0], m[-1]) for m in vv.values())
for name in ['full', 'ready', 'hold'] + [s['name'] for s in report['segments']]:
    assert (DEST / (name + '.json')).read_bytes() == (ASSETS / 'animmodels/animations/hero/poem_mediapipe' / (name + '.json')).read_bytes()
for name, digest in load(OUT / 'v6_baseline_sha256.json').items():
    assert hashlib.sha256((REPO / name).read_bytes()).hexdigest() == digest, 'V6 resource changed: ' + name
v = mesh_data['vertices']
pos = np.array(v['positions']['array']).reshape(-1, 3)
groups, cursor = {n: [] for n in rest}, 0
for i, count in enumerate(v['vcounts']['array']):
    for _ in range(count):
        j, k = v['vindices']['array'][cursor:cursor + 2]
        cursor += 2
        groups[mesh_data['armature']['joints'][j]].append((i, v['weights']['array'][k]))
groups = {n: (np.array([i for i, w in g]), np.array([w for i, w in g])) for n, g in groups.items() if g}
inv_rest = {n: np.linalg.inv(m) for n, m in rest.items()}


def skin(world):
    result = np.zeros_like(pos)
    for n, (ix, weights) in groups.items():
        result[ix] += (np.c_[pos[ix], np.ones(len(ix))] @ (world[n] @ inv_rest[n]).T)[:, :3] * weights[:, None]
    return result


correction = np.array(report['weapon']['model_to_socket']) @ np.linalg.inv(np.array(report['weapon']['item_render_transform']))
weapon_vertices = np.array(source['source_weapon_vertices'])
weapon_h = np.c_[weapon_vertices, np.ones(len(weapon_vertices))]
grips = {'R': np.array(source['source_grips_scaled']), 'L': np.array(source['source_left_grips_scaled'])}
max_grip = {'R': 0., 'L': 0.}
max_offset = {'R': 0., 'L': 0.}
grip_peaks, floor_peaks = [], []
max_trs = max_limb = 0.
min_floor = min_weapon = 100.
max_foot_xy = max_plant_drift = max_support_gap = 0.
previous_plants = {}
blade_positions, shaft_axes = [], []
feature_times, shape_features, leg_features = [], [], []
body_features, body_headings, root_heights = [], [], []
foot_positions=[]
for tick in np.arange(0, len(times) - .5, .5):
    w = compose(values, tick)
    i, u = int(tick), tick % 1
    frame = 1 + fps * (times[i] if not u else (times[i] + times[i + 1]) / 2)
    if not u:
        exact = compose(values, tick, False)
        max_trs = max(max_trs, max(float(np.max(np.abs(w[n] - exact[n]))) for n in rest))
    socket = w['Tool_R'] @ np.linalg.inv(correction)
    blade_positions.append((socket @ [0, 0, 1.65 * 10/16, 1])[:3])
    shaft_axes.append(socket[:3, 2]/np.linalg.norm(socket[:3, 2]))
    support_heights = []
    for side in ('R', 'L'):
        foot = w['Leg_' + side]
        center = (foot @ np.r_[source['foot_local_centers'][side], 1])[:3]
        row = source['foot_plants'][i][side]
        next_row = source['foot_plants'][min(i+1, len(times)-1)][side]
        expected = np.array(row['xy'])*(1-u)+np.array(next_row['xy'])*u
        max_foot_xy = max(max_foot_xy, float(np.linalg.norm(center[:2]-expected)))
        planted = row['planted'] and (not u or next_row['planted'])
        foot_vertices = np.c_[source['foot_local_points'][side], np.ones(len(source['foot_local_points'][side]))]
        height = float((foot_vertices @ foot.T)[:, 2].min())
        if planted:
            support_heights.append(height)
            if side in previous_plants:
                max_plant_drift = max(max_plant_drift, float(np.linalg.norm(center[:2]-previous_plants[side])))
            else:
                previous_plants[side] = center[:2].copy()
        else:
            previous_plants.pop(side, None)
    assert support_heights, ('Both feet airborne', frame)
    max_support_gap = max(max_support_gap, min(support_heights))
    for s in ('R', 'L'):
        grip = grips[s][i] if not u else (grips[s][i] + grips[s][i + 1]) / 2
        palm = (w['Hand_' + s] @ np.r_[local['Tool_' + s][:3, 3], 1])[:3]
        error = float(np.linalg.norm(palm - (socket @ np.r_[grip, 1])[:3]))
        max_offset[s] = max(max_offset[s], error)
        release_start, release_end = report['release_interval_source_frames'] or (float('inf'), float('inf'))
        attached = (not any(release_start <= k <= release_end for k in (np.floor(frame - 1), np.ceil(frame - 1)))) if s == 'R' else (
                source['support_weights'][i] > .999 and (not u or source['support_weights'][i + 1] > .999))
        if attached:
            max_grip[s] = max(max_grip[s], error)
        if error > .01:
            grip_peaks.append({'frame': frame, 'hand': s, 'error': error, 'attached': attached})
    min_floor = min(min_floor, float(skin(w)[:, 2].min()))
    min_weapon = min(min_weapon, float((weapon_h @ socket.T)[:, 2].min()))
    if (weapon_h @ socket.T)[:, 2].min() < 0:
        floor_peaks.append({'frame': frame, 'height': float((weapon_h @ socket.T)[:, 2].min())})
    for n in ('Hand_R', 'Hand_L', 'Leg_R', 'Leg_L'):
        max_limb = max(max_limb, abs(float(np.linalg.norm(w[n][:3, 3] - w[parents[n]][:3, 3]) - np.linalg.norm(local[n][:3, 3]))))
    root_position=w['Root'][:3,3]
    feet_centers=[(w['Leg_'+s]@np.r_[source['foot_local_centers'][s],1])[:3]-root_position for s in ('R','L')]
    foot_positions.append([p+root_position for p in feet_centers])
    legs=[w['Leg_'+s][:3,3]-root_position for s in ('R','L')]+feet_centers
    hands=[(w['Hand_'+s]@np.r_[local['Tool_'+s][:3,3],1])[:3]-root_position for s in ('R','L')]
    leg_features.append(np.concatenate(legs))
    shape_features.append(np.concatenate([shaft_axes[-1],*hands,*legs]))
    root_rotation = w['Root'][:3, :3]
    body_features.append(np.concatenate([root_rotation.T @ shaft_axes[-1],
                                        *(root_rotation.T @ p for p in hands),
                                        *(root_rotation.T @ p for p in legs)]))
    headings = []
    for bone in ('Root', 'Chest', 'Head'):
        rotation_delta = w[bone][:3, :3] @ rest[bone][:3, :3].T
        headings.append(np.arctan2(rotation_delta[1, 0], rotation_delta[0, 0]))
    body_headings.append(headings)
    root_heights.append(root_position[2])
    feature_times.append((frame-1)/fps)

# Compare root-relative trajectories on normalized phase. Different filenames,
# timing, or translated copies of the same swing do not pass this check.
feature_times=np.array(feature_times)
shape_features,leg_features=np.array(shape_features),np.array(leg_features)
shapes=[];leg_shapes=[];attack_metrics=[]
body_shapes=[]
body_features=np.array(body_features)
body_headings=np.degrees(np.unwrap(np.array(body_headings), axis=0))
for s in report['segments']:
    sample_times=s['start']+np.linspace(0,s['duration'],41)
    shapes.append(np.column_stack([np.interp(sample_times,feature_times,c) for c in shape_features.T]))
    leg_shapes.append(np.column_stack([np.interp(sample_times,feature_times,c) for c in leg_features.T]))
    body_shapes.append(np.column_stack([np.interp(sample_times,feature_times,c) for c in body_features.T]))
    mask=(feature_times>=s['start']-1e-7)&(feature_times<=s['start']+s['duration']+1e-7)
    axes=np.array(shaft_axes)[mask]
    sweep_degrees=float(np.degrees(np.arccos(np.clip(np.sum(axes[:-1]*axes[1:],axis=1),-1,1))).sum())
    contact=s['contacts'][0]
    contact_mask=(feature_times>=s['start']+contact['start']-1e-7)&(feature_times<=s['start']+contact['end']+1e-7)
    contact_axes=np.array(shaft_axes)[contact_mask]
    contact_degrees=float(np.degrees(np.arccos(np.clip(np.sum(contact_axes[:-1]*contact_axes[1:],axis=1),-1,1))).sum())
    attack_metrics.append({'name':s['name'],'shaft_direction_span_degrees':float(np.degrees(np.arccos(np.clip(axes@axes.T,-1,1))).max()),
                           'sweep_path_degrees':sweep_degrees,'contact_sweep_degrees':contact_degrees,'travel_blocks':s['travel_blocks']})
    heading=body_headings[mask]
    attack_metrics[-1].update(
        body_turn_degrees={n:float(heading[-1,j]-heading[0,j]) for j,n in enumerate(('Root','Chest','Head'))},
        body_heading_range_degrees={n:float(np.ptp(heading[:,j])) for j,n in enumerate(('Root','Chest','Head'))},
        root_height_range_blocks=[float(np.min(np.array(root_heights)[mask])),float(np.max(np.array(root_heights)[mask]))],
        shaft_elevation_range_degrees=np.degrees(np.arcsin(np.clip([axes[:,2].min(),axes[:,2].max()],-1,1))).tolist())
    steps=np.array(foot_positions)[mask,:,:2]
    attack_metrics[-1].update(
        foot_path_blocks={side:float(np.linalg.norm(np.diff(steps[:,j,:],axis=0),axis=1).sum()) for j,side in enumerate(('R','L'))},
        maximum_foot_separation_blocks=float(np.linalg.norm(steps[:,0,:]-steps[:,1,:],axis=1).max()))
differences=[]
for a in range(len(shapes)):
    for b in range(a+1,len(shapes)):
        differences.append({'a':report['segments'][a]['name'],'b':report['segments'][b]['name'],
                            'shape_rms':float(np.sqrt(np.mean((shapes[a]-shapes[b])**2))),
                            'body_local_shape_rms':float(np.sqrt(np.mean((body_shapes[a]-body_shapes[b])**2))),
                            'leg_shape_rms_blocks':float(np.sqrt(np.mean((leg_shapes[a]-leg_shapes[b])**2)))})
minimum_shape=min(r['shape_rms'] for r in differences)
minimum_legs=min(r['leg_shape_rms_blocks'] for r in differences)
minimum_body_shape=min(r['body_local_shape_rms'] for r in differences)
turning_verified=False
if report['combat_edit']=='mediapipe_turning_07':
    # A wide blade arc alone did not fix Wide06. Require full pelvis, chest and
    # head turns in the exact game matrices, plus distinct shapes after removing
    # root rotation. Merely rotating five copies of one slash fails this audit.
    expected_turns=[2,3,4,6,7]
    for k in expected_turns:
        m=attack_metrics[k]
        assert all(abs(m['body_turn_degrees'][bone])>285 for bone in ('Root','Chest','Head')), ('Lost full-body turn',m)
    assert minimum_body_shape>.08, ('Repeated body-relative attacks',differences)
    assert attack_metrics[4]['root_height_range_blocks'][0]+.13 < attack_metrics[2]['root_height_range_blocks'][0], 'Lunge silhouette was flattened'
    assert attack_metrics[7]['root_height_range_blocks'][0]+.17 < attack_metrics[2]['root_height_range_blocks'][0], 'Low finish was flattened'
    assert all(m['sweep_path_degrees']>170 and m['shaft_direction_span_degrees']>140 for m in attack_metrics), ('Small attack arcs',attack_metrics)
    assert np.abs(np.diff(body_headings,axis=0)).max()<25, 'Discontinuous body turn'
    assert all(m['maximum_foot_separation_blocks']>.55 and max(m['foot_path_blocks'].values())>.65 for m in attack_metrics), ('Constrained footwork',attack_metrics)
    turning_verified=True
validation = {'source_samples': len(times), 'runtime_samples': 2 * len(times) - 1,
              'player_clips': len(report['segments'])+5, 'hero_clips':len(report['segments'])+3, 'duration_seconds': float(times[-1]),
              'segment_reconstruction_max_error': max_seam, 'runtime_trs_max_matrix_error': max_trs,
              'right_grip_max_error_blocks': max_grip['R'], 'left_grip_max_error_blocks': max_grip['L'],
              'all_pose_offset_max_error_blocks': max_offset,
              'max_limb_length_error_blocks': max_limb, 'minimum_mesh_height_blocks': min_floor,
              'minimum_weapon_height_blocks': min_weapon, 'v6_resources_unchanged': True,
              'maximum_foot_xy_error_blocks':max_foot_xy, 'maximum_planted_foot_drift_blocks':max_plant_drift,
              'maximum_support_sole_height_blocks':max_support_gap,
              'ordinary_attacks_always_grounded':max_support_gap<.035, 'weapon_release_frames':0,
              'distinct_attack_trajectories':minimum_shape>.04 and minimum_legs>.025,
              'minimum_attack_shape_rms':minimum_shape,'minimum_leg_shape_rms_blocks':minimum_legs,
              'minimum_body_local_shape_rms':minimum_body_shape,'full_body_turns_verified':turning_verified,
              'attack_metrics':attack_metrics,'attack_pair_differences':differences,
              'grip_peaks': sorted(grip_peaks, key=lambda x: -x['error'])[:12],
              'floor_peaks': sorted(floor_peaks, key=lambda x: x['height'])[:8]}
(OUT / 'validation_report.json').write_text(json.dumps(validation, indent=2) + '\n', encoding='utf-8')
print('MEDIAPIPE_VALIDATION', json.dumps(validation), flush=True)
assert max_trs < .001 and max(max_grip.values()) < .012 and max_limb < .0001 and min_floor >= 0 and min_weapon >= 0
assert max_foot_xy < .005 and max_plant_drift < .005 and max_support_gap < .035
assert minimum_shape>.04 and minimum_legs>.025, ('Repeated attack/footwork trajectories',minimum_shape,minimum_legs)
if report['combat_edit']=='mediapipe_wide_swing_06':
    assert all(m['shaft_direction_span_degrees']>155 and 185<m['sweep_path_degrees']<330
               and m['contact_sweep_degrees']>135 for m in attack_metrics), ('Small or oscillating attack arcs',attack_metrics)
if '--no-render' in sys.argv:
    raise SystemExit(0)

# Skin the real game mesh using the decoded runtime matrices, not the source rig.
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
video = '--video' in sys.argv
side_view = '--side' in sys.argv
scene.render.engine = 'CYCLES' if '--cycles' in sys.argv else 'BLENDER_EEVEE'
scene.cycles.samples = 24
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1080, 810
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.world = bpy.data.worlds.new('World')
scene.world.use_nodes = True
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.12, .15, .20, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = .5


def material(name, color, texture=None):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = (*color, 1)
    if texture:
        tex = m.node_tree.nodes.new('ShaderNodeTexImage')
        tex.image = bpy.data.images.load(str(ASSETS / texture))
        tex.interpolation = 'Closest'
        m.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    return m


faces, uvs = [], []
uv_array = np.array(v['uvs']['array']).reshape(-1, 2)
for name, part in v['parts'].items():
    if name.endswith(('Sleeve', 'Pants')) or name in ('hat', 'jacket'):
        continue
    for tri in np.array(part['array']).reshape(-1, 3, 3):
        faces.append(tuple(int(x) for x in tri[:, 0]))
        uvs.extend([(float(uv_array[k, 0]), 1 - float(uv_array[k, 1])) for k in tri[:, 1]])
body = bpy.data.meshes.new('Epic Fight BIPED')
body.from_pydata(pos, [], faces)
uv = body.uv_layers.new()
for i, co in enumerate(uvs):
    uv.data[i].uv = co
obj = bpy.data.objects.new('Herobrine - game mesh', body)
scene.collection.objects.link(obj)
body.materials.append(material('Herobrine', (.1, .65, .65), 'textures/entity/herobrine.png'))
weapon = bpy.data.meshes.new('Poem of the End')
weapon.from_pydata(weapon_vertices, [], source['source_weapon_faces'])
scythe = bpy.data.objects.new('Poem of the End - game socket', weapon)
scene.collection.objects.link(scythe)
uv = weapon.uv_layers.new()
for i, co in enumerate(source['source_weapon_uv']):
    uv.data[i].uv = co
weapon.materials.append(material('Scythe', (.15, .3, .36), 'textures/item/poem_of_the_end_geo.png'))
bpy.ops.mesh.primitive_plane_add(size=200)
ground = material('Ground', (.11, .13, .16))
bpy.context.object.data.materials.append(ground)
if video:
    geometry = ground.node_tree.nodes.new('ShaderNodeNewGeometry')
    tiles = ground.node_tree.nodes.new('ShaderNodeTexChecker')
    tiles.inputs['Color1'].default_value = (.10, .125, .16, 1)
    tiles.inputs['Color2'].default_value = (.16, .185, .22, 1)
    tiles.inputs['Scale'].default_value = 2
    ground.node_tree.links.new(geometry.outputs['Position'], tiles.inputs['Vector'])
    ground.node_tree.links.new(tiles.outputs['Color'], ground.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
for location, power, size in [((4, 3, 7), 1300, 7), ((-4, -3, 5), 1000, 6)]:
    data = bpy.data.lights.new('Softbox', 'AREA')
    data.energy, data.shape, data.size = power, 'DISK', size
    lamp = bpy.data.objects.new('Softbox', data)
    scene.collection.objects.link(lamp)
    lamp.location = location
    lamp.rotation_euler = (Vector((0, 0, 1)) - lamp.location).to_track_quat('-Z', 'Y').to_euler()
data = bpy.data.cameras.new('Review camera')
camera = bpy.data.objects.new('Review camera', data)
scene.collection.objects.link(camera)
scene.camera = camera
data.type, data.ortho_scale = 'ORTHO', 4.2
review_frames = [frame for s in report['segments'] for frame in
                 (s['first_frame'], round(s['first_frame']+s['contacts'][0]['end']*fps))]
if '--frames' in sys.argv:
    review_frames = []
    for item in sys.argv[sys.argv.index('--frames') + 1:]:
        if item.startswith('--'):break
        review_frames.append(int(item))
if video:
    review_frames = range(1, report['source_frames'] + 1, 2)
    scene.render.resolution_x, scene.render.resolution_y = 960, 720
    scene.eevee.taa_render_samples = 16
    scene.render.image_settings.color_mode = 'RGB'
    scene.render.use_persistent_data = True
    # Frame the complete long-blade arc with one stable camera scale. A scale
    # chosen for the old narrow swings clips the raised blade in Wide06.
    video_scale = 8.0 if side_view else 5.2
    direction = Vector((10, 3, 3.2) if side_view else (-7, 10, 3.8))
    basis = np.array((-direction).to_track_quat('-Z', 'Y').to_matrix())
    for frame in review_frames:
        w = compose(values, int(np.argmin(np.abs(times - (frame - 1) / fps))))
        geometry = np.vstack([skin(w), (weapon_h @ (w['Tool_R'] @ np.linalg.inv(correction)).T)[:, :3]])
        center = np.array((0., sum(s['travel_blocks'] for s in report['segments'])/2+.3, 1.25)) if side_view else np.array((w['Root'][0, 3], w['Root'][1, 3]+.50, 1.35))
        projected = (geometry-center) @ basis
        video_scale = max(video_scale, float(np.abs(projected[:, 0]).max())*2.16,
                          float(np.abs(projected[:, 1]).max())*2.16*960/720)
for sequence, frame in enumerate(review_frames):
    w = compose(values, int(np.argmin(np.abs(times - (frame - 1) / fps))))
    for vertex, p in zip(body.vertices, skin(w)):
        vertex.co = p
    body.update()
    scythe.matrix_world = Matrix(w['Tool_R'] @ np.linalg.inv(correction))
    geometry = np.vstack([skin(w), (weapon_h @ (w['Tool_R'] @ np.linalg.inv(correction)).T)[:, :3]])
    target = Vector((geometry.min(axis=0) + geometry.max(axis=0)) / 2)
    if video:
        target = Vector((w['Root'][0, 3], w['Root'][1, 3] + .50, 1.35))
        if side_view:
            # A fixed world camera makes the real forward distance visible.
            target = Vector((0., sum(s['travel_blocks'] for s in report['segments'])/2+.3, 1.25))
    camera.location = target + Vector((10, 3, 3.2) if side_view else (-7, 10, 3.8))
    camera.rotation_euler = (target - camera.location).to_track_quat('-Z', 'Y').to_euler()
    projected = (geometry - np.array(target)) @ np.array(camera.rotation_euler.to_matrix())
    extent = np.ptp(projected, axis=0)
    data.ortho_scale = video_scale if video else max(4.2, extent[0] * 1.18, extent[1] * 1080 / 810 * 1.18)
    sequence_name = 'sequence_side' if side_view else 'sequence_front'
    scene.render.filepath = str(PREVIEW / (f'{sequence_name}/{sequence:04d}.png' if video else f'{frame:04d}.png'))
    bpy.ops.render.render(write_still=True)
print('MEDIAPIPE_GAME_MESH_PREVIEWS_COMPLETE', flush=True)
