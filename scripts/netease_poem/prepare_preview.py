"""Build preview inputs from the actual delivered geometry and animation JSON."""
import math
import numpy as np

from common import *

FACES = {'north':[4,0,2,6], 'south':[1,5,7,3], 'east':[5,4,6,7],
         'west':[0,1,3,2], 'up':[6,2,3,7], 'down':[5,1,0,4]}


def mesh_data(geometry, kind):
    result = []
    tw = geometry['description']['texture_width']; th = geometry['description']['texture_height']
    for bone in geometry['bones']:
        points, faces, uv = [], [], []
        for cube in bone.get('cubes', []):
            inflate = cube.get('inflate',0.)
            low = np.array(cube['origin']) - inflate
            size = np.array(cube['size']) + 2*inflate
            vertices = low + np.array(list(np.ndindex(2,2,2))) * size
            m = BI @ cube_matrix(cube)
            vertices = (np.c_[vertices,np.ones(8)] @ m.T)[:,:3]
            for face, corners in FACES.items():
                if face not in cube['uv']:
                    continue
                u,v = cube['uv'][face]['uv']; w,h = cube['uv'][face]['uv_size']
                coords = [(u,v+h),(u+w,v+h),(u+w,v),(u,v)]
                start = len(points)
                # BI changes handedness, so reverse the face winding.
                points.extend(vertices[corners][::-1].tolist())
                uv.extend([(x/tw,1-y/th) for x,y in coords[::-1]])
                faces.append([start,start+1,start+2,start+3])
        if points:
            result.append({'name':bone['name'],'kind':kind,'vertices':points,'faces':faces,'uv':uv})
    return result


def main():
    manifest = read(WORK/'library.json'); clips = {c['id']:c for c in manifest['clips']}
    body = read(RP/'models/entity/hc_poem_v1_player.geo.json')['minecraft:geometry'][0]
    weapon = read(RP/'models/entity/hc_poem_v1_scythe.geo.json')['minecraft:geometry'][0]
    meshes = mesh_data(body,'skin') + mesh_data(weapon,'weapon')
    names = [m['name'] for m in meshes]
    schedule, matrices = [], []
    # Ground chain first; then the 8 distinct airborne clips.
    order = manifest['ground_chain'] + manifest['air_chain']
    previous = None
    for order_index, clip_id in enumerate(order):
        clip = clips[clip_id]
        anim = read(RP/'animations'/('hc_poem_v1_%02d.animation.json'%clip_id))['animations'][clip['animation']]
        for t in np.arange(0, clip['length'], 1./60):
            worlds = sample_geometry(body,anim,t)
            worlds.update(sample_geometry(weapon,anim,t))
            pose = np.array([BI @ worlds[n] @ B for n in names])
            # NetEase controller blend_transition: crossfade the previous pose
            # for the first 80 ms, using quaternion interpolation per bone.
            if previous is not None and t < .08:
                weight = min(1.,t/.08)
                for j in range(len(names)):
                    pose[j] = interpolate_matrices(np.array([0.,1.]), np.array([previous[j],pose[j]]), [weight])[0]
            schedule.append({'clip':clip_id,'step':order_index+1,'time':round(float(t),6),
                             'label':clip['label'],'context':clip['context'], 'source':clip['key']})
            matrices.append(pose)
        previous = matrices[-1].copy()
    np.save(WORK/'preview_matrices.npy',np.array(matrices,dtype=np.float32))
    write(WORK/'preview.json',{'meshes':meshes,'schedule':schedule,'source_fps':60,'playback_fps':30,
            'ground_steps':22,'ground_seconds':manifest['ground_chain_seconds'],
            'source':'Actual exported Bedrock JSON (offline preview; not a game capture)'})
    print('PREVIEW_INPUTS',len(schedule),'frames;',len(meshes),'meshes; 0.5x')


if __name__ == '__main__':
    main()
