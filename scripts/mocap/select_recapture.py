"""Resolve anatomical identity in the fresh capture, preserving distinct poses."""
import json
import argparse
from pathlib import Path
import cv2
import numpy as np
from scipy.ndimage import gaussian_filter1d, median_filter
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--work', type=Path, default=ROOT/'build/scythe_recapture_05')
args = parser.parse_args()
WORK = args.work.resolve()
rows = json.loads((WORK/'pose_half_speed_candidates.json').read_text())
N = len(rows)
CORE = np.array([0,11,12,13,14,15,16,23,24,25,26,27,28,29,30,31,32])
PERM = np.arange(33)
for a,b in [(1,4),(2,5),(3,6),(7,8),(9,10)]+[(j,j+1) for j in range(11,32,2)]:
    PERM[a],PERM[b] = b,a
cap = cv2.VideoCapture(str(WORK/'reference_half_speed.mp4'))
states = []
for f,row in enumerate(rows):
    ok,im = cap.read()
    assert ok
    im = cv2.resize(im,(854,426))
    gray = cv2.cvtColor(im,cv2.COLOR_BGR2GRAY)
    mask = (gray < 118)|(gray > 168)
    mask[:45,680:] = False
    dist = cv2.distanceTransform(np.uint8(~mask),cv2.DIST_L2,3)
    cs = []
    for cand in row['candidates']:
        if cand['quality'] < .4:
            continue
        for swap in [False,True]:
            xy,world,vis = [np.array(cand[k]) for k in ['xy','world','visibility']]
            if swap:
                xy,world,vis = xy[PERM],world[PERM],vis[PERM]
            pix = np.rint(xy[CORE]).astype(int)
            pix[:,0] = pix[:,0].clip(0,853)
            pix[:,1] = pix[:,1].clip(0,425)
            unary = -4*np.log(max(.01,cand['quality']))+np.minimum(25,dist[pix[:,1],pix[:,0]]).mean()/7
            hip = (xy[23]+xy[24])/2
            if row['source_time'] < 5.6:
                unary += max(0,xy[0,1]-hip[1]+20)/8
            if f == 0:
                unary += min(20,np.sum((xy[16]-[327,256])**2)/450)
            cs.append({'xy':xy,'world':world,'visibility':vis,'quality':cand['quality'],
                       'method':cand['method'],'swap':swap,'unary':unary})
    states.append(cs)
cap.release()
valid = [i for i,s in enumerate(states) if s]
costs, backs = [], []
for vi,f in enumerate(valid):
    cs = states[f]
    unary = np.array([c['unary'] for c in cs])
    if vi == 0:
        costs.append(unary);backs.append(None);continue
    previous = states[valid[vi-1]]
    trans = np.zeros((len(previous),len(cs)))
    for a,p in enumerate(previous):
        for b,c in enumerate(cs):
            weights = np.minimum(p['visibility'][CORE],c['visibility'][CORE]).clip(.1,1)
            delta = np.sum((p['xy'][CORE]-c['xy'][CORE])**2,axis=1)
            depth = np.sum((p['world'][CORE]-c['world'][CORE])**2,axis=1)
            trans[a,b] = np.average(np.minimum(delta,6000),weights=weights)/150+np.average(np.minimum(depth,2),weights=weights)*1.5
    total = costs[-1][:,None]+trans
    backs.append(total.argmin(axis=0))
    costs.append(unary+total.min(axis=0))
selected = {}
state = int(costs[-1].argmin())
for vi in range(len(valid)-1,-1,-1):
    selected[valid[vi]] = states[valid[vi]][state]
    if vi:
        state = int(backs[vi][state])
xy,world,vis = np.zeros((N,33,2)),np.zeros((N,33,3)),np.zeros((N,33))
for f,c in selected.items():
    xy[f],world[f],vis[f] = c['xy'],c['world'],c['visibility']
for array in [xy,world,vis]:
    flat = array.reshape(N,-1)
    for col in range(flat.shape[1]):
        flat[:,col] = np.interp(np.arange(N),valid,flat[valid,col])
xy = gaussian_filter1d(xy,.65,axis=0,mode='nearest')
world = gaussian_filter1d(world,1.3,axis=0,mode='nearest')
scale = []
for f in range(N):
    samples = []
    for a,b in [(11,13),(13,15),(12,14),(14,16),(23,25),(25,27),(24,26),(26,28),(11,23),(12,24)]:
        metric = np.linalg.norm(world[f,a,:2]-world[f,b,:2])
        if metric > .12 and min(vis[f,a],vis[f,b]) > .4:
            samples.append(np.linalg.norm(xy[f,a]-xy[f,b])/metric)
    scale.append(np.median(samples) if samples else 150.)
scale = gaussian_filter1d(median_filter(scale,size=21),12).clip(85,340)
hipxy = xy[:,[23,24]].mean(axis=1)
points = np.zeros((N,33,3))
points[:,:,0] = (xy[:,:,0]-hipxy[:,None,0])/scale[:,None]
points[:,:,1] = world[:,:,2]
points[:,:,2] = -(xy[:,:,1]-hipxy[:,None,1])/scale[:,None]
points -= points[:,[23,24]].mean(axis=1)[:,None,:]
np.savez_compressed(WORK/'capture_selected.npz',xy=xy,world=world,visibility=vis,points=points,scale=scale,hipxy=hipxy)
selection = [{'half_frame':int(f),'source_time':f/60,'method':c['method'],'side_swap':c['swap'],'visibility':c['quality']} for f,c in sorted(selected.items())]
(WORK/'selection_report.json').write_text(json.dumps({'frames':N,'missing_frames':[f for f in range(N) if f not in selected], 'selection':selection},indent=2)+'\n',encoding='utf-8')
frames = [0,12,21,28,33,40,48,55,64,70,79,88,96,104,113,121,132,140,150,159]
sheet = Image.new('RGB',(4*420,5*350),'#16212e')
draw = ImageDraw.Draw(sheet)
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',18)
cap = cv2.VideoCapture(str(WORK/'reference_half_speed.mp4'))
for idx,f in enumerate(frames):
    half = f*2
    cap.set(cv2.CAP_PROP_POS_FRAMES,half)
    ok,im = cap.read()
    assert ok
    im = cv2.resize(im,(854,426))
    pixels = np.rint(xy[half]).astype(int)
    for a,b in [(11,12),(11,23),(12,24),(23,24),(11,13),(13,15),(12,14),(14,16),(23,25),(25,27),(24,26),(26,28),(27,31),(28,32)]:
        color = (70,250,70) if a%2 else (40,180,255)
        cv2.line(im,tuple(pixels[a]),tuple(pixels[b]),color,2,cv2.LINE_AA)
    for a in [15,16,27,28]:
        cv2.putText(im,str(a),tuple(pixels[a]),cv2.FONT_HERSHEY_SIMPLEX,.38,(220,60,20),1,cv2.LINE_AA)
    x,y,w,h = rows[half]['crop']
    crop = im[max(0,y):min(y+h,426),max(0,x):min(x+w,854)]
    tile = Image.fromarray(cv2.cvtColor(crop,cv2.COLOR_BGR2RGB))
    tile.thumbnail((420,314))
    xx,yy = idx%4*420,idx//4*350
    sheet.paste(tile,(xx+(420-tile.width)//2,yy+32))
    draw.text((xx+10,yy+5),f'源帧 {f+1} · {f/30:.2f}s · 新 MediaPipe',font=font,fill='white')
cap.release()
sheet.save(WORK/'fresh_capture_grounded_sheet.jpg',quality=95)
print('FRESH_CAPTURE_SELECTED',N,'frames',len(valid),'detected')
