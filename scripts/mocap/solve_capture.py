"""Select continuous MediaPipe candidates and reconstruct reference trajectories.

No library animation is substituted. Missing observations are interpolated and
listed in the report. Camera depth remains a monocular estimate.
"""
from pathlib import Path
import json
import math
import cv2
import numpy as np
from scipy.ndimage import gaussian_filter1d, median_filter
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
raw=json.loads((WORK/'pose_candidates_stride1.json').read_text())
roi=json.loads((WORK/'pose_roi_candidates.json').read_text())
weapons=json.loads((WORK/'weapon_candidates.json').read_text())
N=len(raw);FPS=30
CORE=np.array([0,11,12,13,14,15,16,23,24,25,26,27,28])
PERM=np.arange(33)
for a,b in [(1,4),(2,5),(3,6),(7,8),(9,10)]+[(j,j+1) for j in range(11,32,2)]: PERM[a],PERM[b]=b,a
EDGES=[(11,12),(11,23),(12,24),(23,24),(11,13),(13,15),(12,14),(14,16),(23,25),(25,27),(24,26),(26,28)]

def sample_dist(dist,xy):
    p=np.rint(xy).astype(int)
    p[:,0]=p[:,0].clip(0,dist.shape[1]-1);p[:,1]=p[:,1].clip(0,dist.shape[0]-1)
    return dist[p[:,1],p[:,0]]

allc=[];missing=[]
for i in range(N):
    im=cv2.imread(str(WORK/'frames'/f'{i:04d}.png'))
    g=cv2.cvtColor(im,cv2.COLOR_BGR2GRAY)
    fg=(g<117)|(g>166)
    fg[:43,680:]=False
    dist=cv2.distanceTransform(np.uint8(~fg),cv2.DIST_L2,3)
    hair=(g>172)
    hair[:43,680:]=False
    hd=cv2.distanceTransform(np.uint8(~hair),cv2.DIST_L2,3)
    cs=[]
    for method,src in [('detector',raw[i]),('roi_hd',roi[i])]:
        for k,c in enumerate(src['candidates']):
            if c is None or c['quality']<.40:continue
            for swap in (False,True):
                xy=np.array(c['xy']);w=np.array(c['world']);v=np.array(c['visibility'])
                if swap: xy,w,v=xy[PERM],w[PERM],v[PERM]
                center=(xy[23]+xy[24])*.5
                shoulder=(xy[11]+xy[12])*.5
                torso=np.linalg.norm(center-shoulder)
                maskcost=float(np.mean(np.minimum(35,sample_dist(dist,xy[CORE])))/9)
                nose_to_hair=float(min(45,sample_dist(hd,xy[[0]])[0]))/20
                unary=-5*math.log(max(.01,c['quality']))+maskcost+nose_to_hair
                unary+=max(0,22-torso)/8
                if 264<=i<=271:
                    # In the visible handstand the head is below the pelvis.
                    # Some confident candidates mistake the light skirt for hair.
                    unary+=max(0,35-(xy[0,1]-center[1]))/5
                if weapons[i]['candidates'] and not 346<=i<=379:
                    line=weapons[i]['candidates'][0]
                    la,lb=np.array(line['a']),np.array(line['b'])
                    lu=(lb-la)/np.linalg.norm(lb-la);ln=np.array([-lu[1],lu[0]])
                    ld,rd=np.abs((xy[[15,16]]-la)@ln)
                    unary+=max(0,rd-ld-7)/18
                if i==0:
                    # The opening grip is visible at the left side of the image.
                    # Use it once to resolve MediaPipe's front/back label ambiguity.
                    unary+=min(20,np.linalg.norm(xy[16]-[327,256])**2/450)
                cs.append({'xy':xy,'world':w,'visibility':v,'quality':c['quality'],'unary':unary,
                           'method':method,'rotation':k,'swap':swap})
    if not cs:missing.append(i)
    allc.append(cs)

valid=[i for i in range(N) if allc[i]]
costs=[];backs=[]
for vi,f in enumerate(valid):
    cs=allc[f];unary=np.array([c['unary'] for c in cs])
    if vi==0:costs.append(unary);backs.append(None);continue
    previous=allc[valid[vi-1]];dt=f-valid[vi-1]
    trans=np.empty((len(previous),len(cs)))
    for a,p in enumerate(previous):
        for b,c in enumerate(cs):
            weight=np.minimum(p['visibility'][CORE],c['visibility'][CORE]).clip(.1,1)
            pd=p['xy'][CORE]-c['xy'][CORE]
            cost=np.average(np.minimum(np.sum(pd*pd,axis=1),10000),weights=weight)/(260*dt)
            # Depth jumps and left/right identity changes have no pixel cue.
            wd=p['world'][CORE]-c['world'][CORE]
            cost+=np.average(np.minimum(np.sum(wd*wd,axis=1),2),weights=weight)*2.0/dt
            trans[a,b]=cost
    total=costs[-1][:,None]+trans
    backs.append(np.argmin(total,axis=0));costs.append(unary+total.min(axis=0))
chosen={};state=int(np.argmin(costs[-1]))
for vi in range(len(valid)-1,-1,-1):
    chosen[valid[vi]]=allc[valid[vi]][state]
    if vi:state=int(backs[vi][state])

xy=np.zeros((N,33,2));world=np.zeros((N,33,3));vis=np.zeros((N,33));quality=np.zeros(N)
for f,c in chosen.items():xy[f]=c['xy'];world[f]=c['world'];vis[f]=c['visibility'];quality[f]=c['quality']
for arr in (xy,world,vis):
    flat=arr.reshape(N,-1)
    for d in range(flat.shape[1]):flat[:,d]=np.interp(np.arange(N),valid,flat[valid,d])
rawxy=xy.copy();rawworld=world.copy()
# Suppress isolated erroneous joints, while retaining genuine fast arcs.
repaired=[]
for j in range(33):
    med=median_filter(xy[:,j],size=(3,1),mode='nearest')
    bad=(np.linalg.norm(xy[:,j]-med,axis=1)>24)&(vis[:,j]<.8)
    if bad.any():
        xy[bad,j]=med[bad]
        repaired.extend([{'frame':int(f),'landmark':j} for f in np.flatnonzero(bad)])
xy=gaussian_filter1d(xy,.65,axis=0,mode='nearest')
world=gaussian_filter1d(world,1.1,axis=0,mode='nearest')

# MediaPipe estimates metric joint positions relative to the hips. Fit its
# image-plane scale robustly; target bone lengths will be enforced later.
segments=[(11,13),(13,15),(12,14),(14,16),(23,25),(25,27),(24,26),(26,28),(11,23),(12,24)]
scale=[]
for f in range(N):
    candidates=[]
    for a,b in segments:
        d=np.linalg.norm(world[f,a,:2]-world[f,b,:2]);pix=np.linalg.norm(xy[f,a]-xy[f,b])
        if d>.12 and min(vis[f,a],vis[f,b])>.4:candidates.append(pix/d)
    scale.append(np.median(candidates) if candidates else 190)
scale=gaussian_filter1d(median_filter(np.array(scale),size=15),8)
scale=np.clip(scale,110,340)
hipxy=(xy[:,23]+xy[:,24])*.5
points=np.zeros_like(world)
points[:,:,0]=(xy[:,:,0]-hipxy[:,None,0])/scale[:,None]
points[:,:,2]=-(xy[:,:,1]-hipxy[:,None,1])/scale[:,None]
points[:,:,1]=world[:,:,2]
points-=((points[:,23]+points[:,24])*.5)[:,None,:]

# Scythe line candidates: favor observed gold mount, a nearby hand, and
# continuous projected rotation. Treat the released pole separately.
states=[]
# Signed blade directions read from the visible reference, used only to resolve
# the rod's 180-degree ambiguity and the brief effect-obscured observations.
angle_anchors={0:-135,8:170,24:-35,32:160,40:90,48:100,64:-165,72:150,
 80:-125,88:168,96:160,104:150,112:65,120:100,128:75,136:0,144:150,
 148:-145,152:-140,160:70,168:0,176:-80,184:115,192:180,200:-110,
 208:25,216:-146,224:160,240:180,248:-5,256:-8,264:180,272:180,
 280:70,288:-70,296:-75,304:-90,312:-80,320:-80,328:-90,336:-78,
 344:-80,368:-82,376:-85,384:175,400:45,408:45,416:45,424:-135,
 432:45,440:30,448:10,464:-145,472:-40,480:50,488:80,496:75,
 504:-132,512:-142,518:-140}
for f,row in enumerate(weapons):
    cs=[]
    gold=np.array(row['gold_center']) if row['gold_center'] is not None else None
    wrists=xy[f,[15,16]]
    for idx,c in enumerate(row['candidates']):
        a,b=np.array(c['a']),np.array(c['b']);u=(b-a)/np.linalg.norm(b-a);mid=(a+b)*.5
        normal=np.array([-u[1],u[0]])
        handdist=float(np.min(np.abs((wrists-a)@normal)))
        base=-2.3*math.log(max(1,c['score'])/100)+min(60,handdist)/20
        if 346<=f<=379:base-=min(60,handdist)/20
        for sign in (-1,1):
            d=u*sign;angle=math.atan2(-d[1],d[0])
            unary=base
            if gold is not None:
                projection=float(np.dot(gold-mid,d))
                unary+=max(0,-projection)/22
            if 292<=f<=379:unary+=max(0,-d[1])*7
            if f in angle_anchors:
                error=(angle-math.radians(angle_anchors[f])+np.pi)%(2*np.pi)-np.pi
                unary+=10*error*error
            cs.append({'a':a,'b':b,'d':d,'angle':angle,'unary':unary,'index':idx})
    states.append(cs)
wc=[];wb=[]
for f,cs in enumerate(states):
    unary=np.array([c['unary'] for c in cs])
    if f==0:wc.append(unary);wb.append(None);continue
    tr=np.array([[(((a['angle']-b['angle']+np.pi)%(2*np.pi)-np.pi)**2)*1.4 for b in cs] for a in states[f-1]])
    total=wc[-1][:,None]+tr;wb.append(total.argmin(axis=0));wc.append(unary+total.min(axis=0))
weapon_sel=[];s=int(wc[-1].argmin())
for f in range(N-1,-1,-1):
    weapon_sel.append(states[f][s]);s=int(wb[f][s]) if f else s
weapon_sel.reverse()
angles=np.unwrap([c['angle'] for c in weapon_sel]);angles=gaussian_filter1d(angles,.6)
shaft=np.zeros((N,3));grip_px=np.zeros((N,2));blade_px=np.zeros((N,2));grip_hand=[]
for f,c in enumerate(weapon_sel):
    d=np.array([math.cos(angles[f]),-math.sin(angles[f])]);a=c['a'];normal=np.array([-d[1],d[0]])
    wrists=xy[f,[15,16]]
    # Reference weapon is primarily right-hand held; prefer it unless the
    # visible left hand is materially closer to the measured shaft.
    distances=np.abs((wrists-a)@normal)
    hand=1 if distances[1]<=distances[0]+10 else 0
    contact=wrists[hand]-normal*np.dot(wrists[hand]-a,normal)
    grip_px[f]=contact;grip_hand.append('Right' if hand else 'Left')
    gold=weapons[f]['gold_center']
    blade_px[f]=np.array(gold) if gold is not None else contact+d*90
    delta=points[f,15]-points[f,16]
    depth=0.0
    if distances.max()<14 and np.linalg.norm(wrists[0]-wrists[1])>16:
        projection=np.dot(wrists[0]-wrists[1],d)
        if abs(projection)>10:
            depth=float(np.clip(delta[1]/max(np.linalg.norm(delta),.01),-.65,.65))*np.sign(projection)
    shaft[f]=[d[0]*math.sqrt(1-depth*depth),depth,-d[1]*math.sqrt(1-depth*depth)]
shaft=gaussian_filter1d(shaft,1.0,axis=0);shaft/=np.linalg.norm(shaft,axis=1)[:,None]
grip_px=gaussian_filter1d(grip_px,.65,axis=0)
blade_px=gaussian_filter1d(blade_px,.8,axis=0)

report={'engine':'Google MediaPipe 0.10.21 BlazePose GHUM Heavy (TensorFlow Lite)',
  'source':'https://www.bilibili.com/video/BV1JH4y1r7po/','source_author':'Pirate_Gn',
  'frames':N,'fps':FPS,'source_duration':N/FPS,'missing_pose_frames':missing,
  'median_landmark_visibility':float(np.median(quality[quality>0])),
  'low_visibility_frames':[int(i) for i in range(N) if quality[i]<.65],
  'joint_outliers_repaired':repaired,
  'selection':[{'frame':f,'method':c['method'],'rotation':c['rotation']*90,'side_swap':c['swap'],'visibility':c['quality']} for f,c in chosen.items()],
  'method':'Independent detector and explicit ROI candidates; temporal dynamic programming; short gap interpolation; image-plane fit and MediaPipe depth.',
  'limitations':'Monocular depth, occluded joints, and camera-depth root translation are estimates. Visibility is model confidence, not measured accuracy. Weapon orientation combines image tracking and hand depth.',
  'release_interval_source_frames':[346,379]}
report['weapon_direction_observations_degrees']=angle_anchors
(WORK/'capture_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
np.savez_compressed(WORK/'capture_solved.npz',xy=xy,world=world,points=points,visibility=vis,
  quality=quality,scale=scale,hipxy=hipxy,shaft=shaft,grip_px=grip_px,blade_px=blade_px,
  source_xy=rawxy,source_world=rawworld)
(WORK/'weapon_selection.json').write_text(json.dumps({'angles':angles.tolist(),'grip_hand':grip_hand,
 'segments':[{'a':c['a'].tolist(),'b':c['b'].tolist()} for c in weapon_sel]}),encoding='utf-8')

sheet=Image.new('RGB',(4*427,9*242),(20,24,29));draw=ImageDraw.Draw(sheet)
video=cv2.VideoWriter(str(WORK/'capture_overlay.avi'),cv2.VideoWriter_fourcc(*'MJPG'),30,(854,426))
selected={round(j*(N-1)/35):j for j in range(36)}
for f in range(N):
    im=cv2.imread(str(WORK/'frames'/f'{f:04d}.png'));p=np.rint(xy[f]).astype(int)
    for a,b in EDGES:cv2.line(im,tuple(p[a]),tuple(p[b]),(20,220,255) if a%2==0 else (65,240,50),2,cv2.LINE_AA)
    for j in CORE:cv2.circle(im,tuple(p[j]),2,(255,80,80),-1)
    a=grip_px[f];d=np.array([shaft[f,0],-shaft[f,2]]);d=d/max(np.linalg.norm(d),.01)
    cv2.arrowedLine(im,tuple(np.rint(a-d*40).astype(int)),tuple(np.rint(a+d*90).astype(int)),(230,180,30),2,cv2.LINE_AA,tipLength=.08)
    cv2.putText(im,f'{f/30:.2f}s  F{f:03d}  vis {quality[f]:.2f}',(12,411),cv2.FONT_HERSHEY_SIMPLEX,.45,(255,255,255),1,cv2.LINE_AA)
    video.write(im)
    if f in selected:
        j=selected[f];x,y=(j%4)*427,(j//4)*242
        tile=Image.fromarray(cv2.cvtColor(im,cv2.COLOR_BGR2RGB));tile.thumbnail((427,218));sheet.paste(tile,(x,y+23))
        draw.text((x+6,y+5),f'F{f:03d}  {f/30:.2f}s  visibility {quality[f]:.2f}',fill='white')
video.release();sheet.save(WORK/'capture_selected_contact.jpg',quality=95)
print('CAPTURE_SOLVED',json.dumps({k:report[k] for k in ['frames','missing_pose_frames','median_landmark_visibility','low_visibility_frames']}))
