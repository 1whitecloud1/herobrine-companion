"""Shared mesh and matrix utilities for the native Unity scythe conversion."""
import json
from pathlib import Path

import numpy as np
from scipy.spatial.transform import Rotation

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT/'build/scythe_unity_pack_09'
OUT = ROOT/'output/Herobrine_Scythe_UnityPack_09'
ASSETS = ROOT/'src/main/resources/assets/herobrine_companion'
C = np.diag([-1., -1., 1.])
# Native: shaft +Y, main hook +Z. Poem of the End: shaft +Z, hook -Y.
WEAPON_AXES = np.array([[1.,0.,0.],[0.,0.,1.],[0.,-1.,0.]])
# Native torso +X points up, +Y backward, +Z to the character's right.
TORSO_AXES = np.array([[0.,1.,0.],[0.,0.,1.],[1.,0.,0.]])


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def write(path, value, pretty=False):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,allow_nan=False,
                    indent=2 if pretty else None,separators=None if pretty else (',',':'))+'\n',encoding='utf-8')


def unit(v, fallback=(1.,0.,0.)):
    d=np.linalg.norm(v)
    return np.asarray(v)/d if d>1e-10 else np.asarray(fallback,dtype=float)


def affine(r=np.eye(3),p=(0.,0.,0.)):
    m=np.eye(4);m[:3,:3]=r;m[:3,3]=p
    return m


def rotations(m):
    u,_,vt=np.linalg.svd(np.asarray(m)[...,:3,:3])
    return u @ vt


def basis(direction,hinge):
    y=unit(direction,(0.,1.,0.))
    x=unit(hinge-y*np.dot(hinge,y))
    z=unit(np.cross(x,y))
    return np.column_stack((np.cross(y,z),y,z))


class GameMesh:
    def __init__(self):
        self.data=load(ROOT/'build/epicfight-mediapipe/biped.json')
        self.local,self.rest,self.parents={},{},{}
        def walk(nodes,parent=None):
            for node in nodes:
                n=node['name'];self.local[n]=np.array(node['transform']).reshape(4,4)
                self.parents[n]=parent
                self.rest[n]=(self.rest[parent] if parent else np.eye(4)) @ self.local[n]
                walk(node.get('children',[]),n)
        walk(self.data['armature']['hierarchy'])
        self.names=list(self.rest)
        self.inverse={n:np.linalg.inv(m) for n,m in self.rest.items()}
        self.neutral={n:rotations(m) for n,m in self.rest.items()}
        v=self.data['vertices'];self.vertices=np.array(v['positions']['array']).reshape(-1,3)
        self.groups={n:[] for n in self.names};cursor=0
        for i,count in enumerate(v['vcounts']['array']):
            for _ in range(count):
                j,wi=v['vindices']['array'][cursor:cursor+2];cursor+=2
                self.groups[self.data['armature']['joints'][j]].append((i,v['weights']['array'][wi]))
        self.groups={n:(np.array([i for i,w in rows]),np.array([w for i,w in rows])) for n,rows in self.groups.items() if rows}
        self.faces,self.uvs=[],[];uv=np.array(v['uvs']['array']).reshape(-1,2)
        for n,part in v['parts'].items():
            if n.endswith(('Sleeve','Pants')) or n in ('hat','jacket'):
                continue
            for tri in np.array(part['array']).reshape(-1,3,3):
                self.faces.append(tuple(int(x) for x in tri[:,0]))
                self.uvs.extend([(float(uv[k,0]),1-float(uv[k,1])) for k in tri[:,1]])
        old=load(ROOT/'build/epicfight-aerial-dash/reference_samples.json')
        self.foot_points={s:np.array(old['foot_local_points'][s]) for s in ('R','L')}
        self.foot_centers={s:np.array(old['foot_local_centers'][s]) for s in ('R','L')}
        self.weapon_vertices=np.array(old['source_weapon_vertices'])
        self.weapon_faces=old['source_weapon_faces'];self.weapon_uv=old['source_weapon_uv']
        self.weapon_report=load(ROOT/'build/epicfight-aerial-dash/conversion_report.json')['weapon']
        self.correction=np.array(self.weapon_report['model_to_socket']) @ np.linalg.inv(self.weapon_report['item_render_transform'])
        self.correction_inverse=np.linalg.inv(self.correction)
        self.helper_angles={}

    def skin(self,world):
        result=np.zeros_like(self.vertices)
        for n,(ix,weights) in self.groups.items():
            result[ix]+=(np.c_[self.vertices[ix],np.ones(len(ix))] @ (world[n] @ self.inverse[n]).T)[:,:3]*weights[:,None]
        return result

    def palm(self,world,side):
        return (world['Hand_'+side] @ np.r_[self.local['Tool_'+side][:3,3],1])[:3]

    def helpers(self,world):
        for name in self.names:
            if name.startswith(('Elbow_','Knee_')):
                side=name[-1]
                upper=('Arm_' if name.startswith('Elbow') else 'Thigh_')+side
                lower=('Hand_' if name.startswith('Elbow') else 'Leg_')+side
                upper_r=rotations(world[upper]);lower_r=rotations(world[lower])
                relative=upper_r.T @ lower_r
                angle=np.arctan2(relative[2,1]-relative[1,2],relative[1,1]+relative[2,2])
                if name in self.helper_angles:
                    previous=self.helper_angles[name]
                    angle=previous+np.arctan2(np.sin(angle-previous),np.cos(angle-previous))
                self.helper_angles[name]=angle
                r=upper_r @ Rotation.from_rotvec([angle*.5,0.,0.]).as_matrix()
                world[name][:3,:3]=r @ self.neutral[upper].T @ self.neutral[name]
                world[name][:3,3]=world[lower][:3,3]


class NativeReference:
    def __init__(self):
        self.data=load(WORK/'native_reference.json')
        self.rest={n:np.array(v) for n,v in self.data['rest'].items()}
        self.rot={n:rotations(v) for n,v in self.rest.items()}
        self.pos={n:v[:3,3] for n,v in self.rest.items()}
        body=self.data['meshes']['NCG_Mesh'];v=np.array(body['vertices'])
        self.foot_points={};self.foot_centers={}
        for side in ('R','L'):
            suffix=side.lower();n='foot_'+suffix
            ix=[i for i,g in enumerate(body['weights']) if sum(g.get(k,0) for k in (n,'ball_'+suffix))>.6 and v[i,2]<.04]
            assert len(ix)>8,(side,len(ix))
            local=(v[ix]-self.pos[n]) @ self.rot[n]
            self.foot_points[side]=local;self.foot_centers[side]=local.mean(axis=0)

    def soles(self,positions,rs,side):
        n='foot_'+side.lower()
        points=self.foot_points[side] @ rs[n].T+positions[n]
        return points.mean(axis=0),max(0.,float(points[:,2].min()))
