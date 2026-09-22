"""Fast source-rig review and motion diagnostics before the Blender/EF bake."""
import json,math,sys,argparse
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from scipy.spatial.transform import Rotation
from combat_cleanup import CombatRig,PROFILE,unit

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--output',type=Path,default=ROOT/'output/Herobrine_Scythe_Recapture_05')
parser.add_argument('--video',action='store_true')
args=parser.parse_args()
OUT=args.output.resolve()
data=json.loads((OUT/'capture/target_motion.json').read_text(encoding='utf-8'))
profile=json.loads(PROFILE.read_text(encoding='utf-8'))
rig=CombatRig(profile,data)
poses=[{n:np.array(v) for n,v in row.items()} for row in data['poses']]
body_faces=profile['character_faces'];weapon_faces=profile['weapon_faces']
vertex_bones=[max(v['weights'],key=v['weights'].get) if v['weights'] else 'Body' for v in profile['character_vertices']]
palette={'Head':(160,127,91),'Body':(29,136,142),'Chest':(35,154,163)}
colors=[]
for face in body_faces:
    bone=vertex_bones[face[0]]
    colors.append(palette.get(bone,(51,50,133) if 'Leg' in bone else (167,136,102)))
view=unit([4.,-7.,2.8]);right=unit(np.cross([0,0,1],view));up=np.cross(view,right)
camera=np.column_stack([right,up,view])
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',14)
light=unit([-.3,-.5,1.])


def render(index):
    pose=poses[index];body=rig.skin(pose)
    weapon=rig.weapon@pose['Weapon:Scythe'][:3,:3].T+pose['Weapon:Scythe'][:3,3]
    center=pose['Bone.011'][:3,3]+[0,-.35,.65]
    width,height,scale=640,520,85
    def project(points):
        points=(np.asarray(points)-center)@camera
        return np.c_[width/2+points[:,0]*scale,height/2-points[:,1]*scale,points[:,2]]
    im=Image.new('RGB',(width,height),'#19242f');draw=ImageDraw.Draw(im)
    ground=rig.ground
    for x in np.arange(-6.4,6.5,.8):
        p=project([[x,-9,ground],[x,3,ground]])
        draw.line([tuple(t) for t in p[:,:2]],fill='#334250',width=1)
    for y in np.arange(-9.6,4,.8):
        p=project([[-6.4,y,ground],[6.4,y,ground]])
        draw.line([tuple(t) for t in p[:,:2]],fill='#334250',width=1)
    polygons=[]
    for vertices,faces,cs in [(body,body_faces,colors),(weapon,weapon_faces,[(112,143,141)]*len(weapon_faces))]:
        proj=project(vertices)
        for face,color in zip(faces,cs):
            v=vertices[face]
            normal=unit(np.cross(v[1]-v[0],v[2]-v[0]))
            shade=.48+.52*max(0,np.dot(normal,light))
            color=tuple(round(c*shade) for c in color)
            q=proj[face]
            polygons.append((q[:,2].mean(),[tuple(t) for t in q[:,:2]],color))
    for _,polygon,color in sorted(polygons,key=lambda x:x[0]):draw.polygon(polygon,fill=color)
    f=data['footwork'][index];s=data['attack_segments'][f['segment']-1]
    draw.text((12,10),f"{f['segment']:02d} {s['label']} · F{index+1}",font=font,fill='#eff5fa')
    draw.text((12,36),f"源帧 {f['reference_frame']:.1f} / 前进 {-pose['Bone.011'][1,3]*.625:.2f} 格",font=small,fill='#b9c8d4')
    return im


selected=[f for s in data['attack_segments'] for f in [s['first_frame']-1,s['contact_frames'][0][1]-1]]
sheet=Image.new('RGB',(2560,2080),'#19242f')
for i,f in enumerate(selected):sheet.paste(render(f),(i%4*640,i//4*520))
(OUT/'preview').mkdir(exist_ok=True)
sheet.save(OUT/'preview/source_pose_sheet.jpg',quality=94)
diagnostics=[]
for s in data['attack_segments']:
    first,last=s['first_frame']-1,s['last_frame']-1
    rows=poses[first:last+1]
    shaft=np.array([r['Weapon:Scythe'][:3,2]/rig.scale for r in rows])
    angles=np.degrees(np.arccos(np.clip(shaft@shaft.T,-1,1)))
    feet={side:np.array([[data['footwork'][i]['foot_'+side+'x'],data['footwork'][i]['foot_'+side+'y']] for i in range(first,last+1)]) for side in ['l','r']}
    diagnostics.append({'name':s['name'],'max_shaft_direction_span_degrees':float(angles.max()),
        'feet_path_blocks':{side:float(np.linalg.norm(np.diff(p,axis=0),axis=1).sum()*.625) for side,p in feet.items()},
        'root_height_range_blocks':float(np.ptp([r['Bone.011'][2,3] for r in rows])*.625),
        'travel_blocks':s['travel_blocks']})
turns={n:np.degrees(Rotation.from_matrix([poses[i][n][:3,:3].T@poses[i+1][n][:3,:3]/(rig.scale**2 if n=='Weapon:Scythe' else 1.) for i in range(len(poses)-1)]).magnitude()) for n in ['Chest','Weapon:Scythe','Leg:Left:Upper','Leg:Right:Upper']}
report={'cuts':diagnostics,'largest_adjacent_turns':{n:[{'frame':int(i+1),'degrees':float(a[i])} for i in np.argsort(a)[-6:][::-1]] for n,a in turns.items()},
        'airborne_frames':[i+1 for i,r in enumerate(data['footwork']) if min(r['foot_l_lift'],r['foot_r_lift'])>1e-8],
        'min_source_mesh_height':min(float(rig.skin(p)[:,2].min()-rig.ground) for p in poses)}
(OUT/'capture/source_review_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
if '--video' in sys.argv:
    folder=OUT/'preview/source_sequence';folder.mkdir(exist_ok=True)
    for i,index in enumerate(range(0,len(poses),2)):render(index).save(folder/f'{i:04d}.png')
