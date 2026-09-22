"""Retarget the final V6 bake to the stock Epic Fight player armature.

Run with Blender --background --factory-startup --python export_v6.py -- SOURCE_DIR.
The source is read only. Times use the delivered 30 / 1.001 FPS, including half frames.
"""
import hashlib
import json
import math
import sys
import zipfile
from pathlib import Path

import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export_v4 import affine, rotation, weapon_calibration, write, load, MAPPING, C, SCALE, REPO, ASSETS

OUT = REPO/'build/epicfight-v6'
DEST = ASSETS/'animmodels/animations/player/poem_v6'


def main():
    source_dir = Path(sys.argv[sys.argv.index('--')+1])
    motion = load(source_dir/'target_motion.json')
    profile = load(source_dir/'target_profile.json')
    assert motion['action'] == 'HB_Scythe_V6_Continuous_Combat'
    assert motion['frames'][0] == 1 and motion['frames'][-1] == 1681
    assert len(motion['poses']) == 3361
    OUT.mkdir(parents=True, exist_ok=True)
    jar = next((REPO/'.gradle-home/caches/modules-2/files-2.1/curse.maven/epic-fight-mod-405076/8080214').rglob('*.jar'))
    with zipfile.ZipFile(jar) as z:
        (OUT/'biped.json').write_bytes(z.read('assets/epicfight/animmodels/entity/biped.json'))
    mesh = load(OUT/'biped.json')
    local, rest, parents = {}, {}, {}
    def walk(nodes, parent=None):
        for node in nodes:
            n = node['name']
            local[n] = np.array(node['transform']).reshape(4,4)
            parents[n] = parent
            rest[n] = (rest[parent] if parent else np.eye(4)) @ local[n]
            walk(node.get('children',[]),n)
    walk(mesh['armature']['hierarchy'])
    names = list(rest)
    assert len(names) == 20
    neutral = {n:rotation(m) for n,m in rest.items()}
    for side in ('R','L'): neutral['Hand_'+side] = neutral['Arm_'+side]
    source_rest = {n:rotation(np.array(profile['bones'][s]['rest'])) for n,s in MAPPING.items()}
    poses = [{n:np.array(v) for n,v in row.items()} for row in motion['poses']]
    times = (np.array(motion['frames'])-1)*motion['fps_base']/motion['fps']
    # Align the first authored facing with the player's forward direction, once for
    # the entire timeline, preserving turns and seams between all 18 attacks.
    forward = C @ poses[0]['Bone.011'][:3,:3] @ np.array([0.,-1.,0.])
    yaw = math.pi/2-math.atan2(forward[1],forward[0])
    align = np.array(Matrix.Rotation(yaw,3,'Z'))
    axes = align @ C
    correction, weapon_report = weapon_calibration(source_dir)
    weapon_mesh = load(OUT/'weapon_mesh.json')
    weapon_vertices = np.array(weapon_mesh['vertices'])*SCALE
    # Group skin influences for fast, exact floor checks of the actual player mesh.
    vertices = mesh['vertices']
    points = np.array(vertices['positions']['array']).reshape(-1,3)
    influences = {n:[] for n in names}
    cursor = 0
    for i,count in enumerate(vertices['vcounts']['array']):
        for _ in range(count):
            j,wi = vertices['vindices']['array'][cursor:cursor+2]; cursor += 2
            influences[mesh['armature']['joints'][j]].append((i,vertices['weights']['array'][wi]))
    groups = {n:(np.array([i for i,w in g]),np.array([w for i,w in g])) for n,g in influences.items() if g}
    inv_rest = {n:np.linalg.inv(m) for n,m in rest.items()}
    def skin(world):
        result = np.zeros_like(points)
        for n,(ix,weights) in groups.items():
            result[ix] += ((np.c_[points[ix],np.ones(len(ix))] @ (world[n]@inv_rest[n]).T)[:,:3])*weights[:,None]
        return result
    def palm(world,side):
        return (world['Hand_'+side] @ np.r_[local['Tool_'+side][:3,3],1])[:3]
    def source_palm(row,side):
        return (row['Arm:'+side+':Lower'] @ [0,.48,0,1])[:3]
    def rotate_between(old,new):
        return np.array(Vector(old).rotation_difference(Vector(new)).to_matrix())
    def support_ik(world, goal):
        # Match the supporting palm without stretching either player arm segment.
        a = world['Arm_L'][:3,3].copy()
        b = world['Hand_L'][:3,3].copy()
        c = palm(world,'L')
        l1,l2 = np.linalg.norm(b-a),np.linalg.norm(c-b)
        direction = goal-a
        reach = np.linalg.norm(direction)
        d = np.clip(reach,abs(l1-l2)+1e-5,l1+l2-1e-5)
        axis = direction/reach
        bend = b-a-axis*np.dot(b-a,axis)
        if np.linalg.norm(bend)<1e-6: bend=np.cross(axis,[0.,0.,1.])
        bend /= np.linalg.norm(bend)
        along = (l1*l1-l2*l2+d*d)/(2*d)
        elbow = a+axis*along+bend*math.sqrt(max(0,l1*l1-along*along))
        end = a+axis*d
        world['Arm_L'][:3,:3] = rotate_between(b-a,elbow-a) @ world['Arm_L'][:3,:3]
        world['Hand_L'][:3,:3] = rotate_between(c-b,end-elbow) @ world['Hand_L'][:3,:3]
        world['Hand_L'][:3,3] = elbow

    worlds, grips, support_errors, lifts = [], [], [], []
    root_start = poses[0]['Bone.011'][:3,3].copy()
    root_start[2] = 0
    for tick,row in enumerate(poses):
        world = {}
        for n in names:
            pw = world[parents[n]] if parents[n] else np.eye(4)
            m = pw @ local[n]
            if n in MAPPING:
                m[:3,:3] = axes @ rotation(row[MAPPING[n]]) @ source_rest[n].T @ C.T @ neutral[n]
                if n == 'Root': m[:3,3] = rest[n][:3,3]+axes@(row['Bone.011'][:3,3]-root_start)*SCALE
            world[n] = m
        rp = source_palm(row,'Right')
        lp = source_palm(row,'Left')
        grip = np.linalg.solve(row['Weapon:Scythe'],np.r_[rp,1])[:3]*SCALE
        grips.append(grip)
        weight = motion['support_hand'][tick]['weight']
        if weight > 0:
            goal = palm(world,'R')+axes@(lp-rp)*SCALE
            old = palm(world,'L')
            support_ik(world,old+(goal-old)*weight)
            if weight>.99: support_errors.append(float(np.linalg.norm(palm(world,'L')-goal)))
        # Rebuild the deformation helpers after the support-arm adjustment.
        for n in names:
            if n.startswith(('Elbow_','Knee_')):
                side = n[-1]
                upper = ('Arm_' if n.startswith('Elbow') else 'Thigh_')+side
                lower = ('Hand_' if n.startswith('Elbow') else 'Leg_')+side
                q1 = Matrix(world[upper][:3,:3]).to_quaternion()
                q2 = Matrix(world[lower][:3,:3]).to_quaternion()
                world[n][:3,:3] = np.array(q1.slerp(q2,.5).to_matrix()) @ neutral[upper].T @ neutral[n]
                world[n][:3,3] = world[lower][:3,3]
        # Use the *scaled* weapon matrix and the changing palm-to-weapon offset.
        # V6's 0.62 weapon scale and sliding grip must both survive the retarget.
        socket = affine(axes@row['Weapon:Scythe'][:3,:3],
                        palm(world,'R')+axes@(row['Weapon:Scythe'][:3,3]-rp)*SCALE)
        world['Tool_R'] = socket @ correction
        world['Tool_L'] = world['Hand_L'] @ local['Tool_L']
        wp = (np.c_[weapon_vertices,np.ones(len(weapon_vertices))] @ socket.T)[:,:3]
        minimum = min(skin(world)[:,2].min(),wp[:,2].min())
        lifts.append(max(0,.008-minimum))
        worlds.append(world)
    # Smooth conservative floor clearance with a 0.1-second envelope. Source and
    # target limb proportions differ; only pelvis elevation is compensated.
    lifts = np.array(lifts)
    envelope = np.array([max(lifts[max(0,i-6):min(len(lifts),i+7)]) for i in range(len(lifts))])
    envelope = np.convolve(np.pad(envelope,(3,3),mode='edge'),np.ones(7)/7,mode='valid')
    envelope = np.maximum(envelope,lifts)
    sampled = []
    for i,world in enumerate(worlds):
        for m in world.values(): m[2,3] += envelope[i]
        sampled.append({n:np.linalg.inv(world[parents[n]] if parents[n] else np.eye(4))@world[n] for n in names})
    # Six authored swings per passage. Markers identify gestures; split at the
    # minimum blade angular speed between each pair, away from either impact.
    rotations = [Matrix(rotation(row['Weapon:Scythe'])).to_quaternion() for row in poses]
    speed = np.zeros(len(times))
    for i in range(1,len(times)-1):
        speed[i] = 2*math.acos(min(1,abs(rotations[i-1].dot(rotations[i+1]))))/(times[i+1]-times[i-1])
    speed = np.convolve(np.pad(speed,(3,3),mode='edge'),np.ones(7)/7,mode='valid')
    markers = motion['markers']
    ix = lambda f: int(np.argmin(np.abs(np.array(motion['frames'])-f)))
    segments = []
    for passage in range(3):
        group = markers[passage*7:passage*7+7]
        attacks = [ix(x['frame']) for x in group[1:]]
        bounds = [ix(group[0]['frame'])]
        for a,b in zip(attacks,attacks[1:]):
            lo,hi = round(a+(b-a)*.30),round(a+(b-a)*.70)
            bounds.append(lo+int(np.argmin(speed[lo:hi+1])))
        bounds.append(ix(markers[(passage+1)*7]['frame']) if passage<2 else len(times)-1)
        for k,(a,b) in enumerate(zip(bounds,bounds[1:])):
            span = b-a
            lo,hi = a+max(3,int(span*.10)),b-max(6,int(span*.10))
            peak = lo+int(np.argmax(speed[lo:hi+1]))
            threshold = speed[peak]*.45
            h0,h1 = peak,peak
            while h0>lo and speed[h0-1]>threshold: h0-=1
            while h1<hi and speed[h1+1]>threshold: h1+=1
            # Keep a useful collision interval even for a very sharp speed spike.
            h0=min(h0,max(lo,peak-6)); h1=max(h1,min(hi,peak+6))
            segments.append({'name':f'combo_{len(segments)+1:02d}', 'label':group[k+1]['label'],
                'start_index':a,'end_index':b,'start':float(times[a]),'end':float(times[b]),
                'duration':float(times[b]-times[a]),'hit_start':float(times[h0]-times[a]),
                'contact':float(times[h1]-times[a]),'recovery':float(times[b]-times[a]-.1),
                'peak_index':peak})
    def clip(a,b, include=None, static=False, zero_travel=False):
        entries = []
        for n in names:
            if include is not None and n not in include: continue
            matrices = np.array([x[n] for x in sampled[a:b+1]])
            t = times[a:b+1]-times[a]
            if n=='Root':
                matrices[:,:2,3] -= matrices[0,:2,3]-rest[n][:2,3]
                if zero_travel: matrices[:,:2,3] = rest[n][:2,3]
            if static:
                matrices=np.stack([matrices[0],matrices[0]])
                t=np.array([0.,1.])
            entries.append({'name':n,'time':np.round(t,6).tolist(),
                            'transform':np.round(matrices.reshape(-1,16),7).tolist()})
        return {'animation':entries}
    write(DEST/'full.json',clip(0,len(times)-1))
    for s in segments: write(DEST/(s['name']+'.json'),clip(s['start_index'],s['end_index']))
    write(DEST/'ready.json',clip(0,0,static=True))
    upper = {n for n in names if n!='Root' and not n.startswith(('Thigh_','Leg_','Knee_'))}
    write(DEST/'hold.json',clip(0,0,include=upper,static=True))
    # Sprint uses the opening step-cut; airborne attack uses the first heavy cut,
    # trimmed around impact so the player can regain control promptly.
    specials = []
    for name,s,lead,tail in [('dash',segments[0],.42,.36),('air',segments[4],.28,.32)]:
        peak=s['peak_index']; a=max(s['start_index'],peak-round(lead/(times[1]-times[0])))
        b=min(s['end_index'],peak+round(tail/(times[1]-times[0])))
        info={'name':name,'start_index':a,'end_index':b,'duration':float(times[b]-times[a]),
              'hit_start':max(.08,float(times[peak]-times[a])-.12),
              'contact':min(float(times[b]-times[a])-.1,float(times[peak]-times[a])+.14)}
        info['recovery']=info['duration']-.06
        specials.append(info)
        write(DEST/(name+'.json'),clip(a,b,zero_travel=name=='air'))
    report={'action':motion['action'],'source_dir':str(source_dir),'source_sha256':hashlib.sha256((source_dir/'target_motion.json').read_bytes()).hexdigest(),
            'duration_seconds':float(times[-1]),'sample_hz':float(1/(times[1]-times[0])), 'samples':len(times),'joint_count':len(names),
            'axis_source_to_epicfight':axes.tolist(),'weapon':weapon_report,'weapon_scale':motion['weapon_scale'],
            'segments':segments,'specials':specials,'max_support_hand_error_blocks':max(support_errors,default=0),
            'max_floor_correction_blocks':float(envelope.max()),'floor_correction_blocks':envelope.tolist(),
            'grip_range':np.array(grips).ptp(axis=0).tolist() if hasattr(np.array(grips),'ptp') else np.ptp(grips,axis=0).tolist()}
    write(OUT/'conversion_report.json',report,True)
    write(OUT/'reference_samples.json',{'source_grips_scaled':np.array(grips).tolist(),'source_weapon_vertices':weapon_vertices.tolist(),
          'source_weapon_faces':weapon_mesh['faces'],'source_weapon_uv':weapon_mesh['uv']})
    # Runtime timing resource is intentionally separate from the large animation matrices.
    write(ASSETS/'epicfight/poem_v6_timing.json',{'segments':segments,'specials':specials},True)
    print('V6_EXPORT',json.dumps({k:v for k,v in report.items() if k not in ('floor_correction_blocks','weapon','segments')},ensure_ascii=True))
    for s in segments: print('SEGMENT',s['name'],round(s['duration'],3),round(s['hit_start'],3),round(s['contact'],3))


if __name__=='__main__': main()
