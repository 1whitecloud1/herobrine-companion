"""Refine difficult poses with Google's landmark graph and explicit body ROIs."""
from pathlib import Path
import argparse
import json
import time
import cv2
import numpy as np
from PIL import Image, ImageDraw
from google.protobuf import text_format
from mediapipe.framework import calculator_pb2
from mediapipe.framework.formats import rect_pb2
from mediapipe.python.solution_base import SolutionBase

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/scythe_mocap'
p=argparse.ArgumentParser();p.add_argument('--pilot',action='store_true');args=p.parse_args()
config=text_format.Parse('''
input_stream: "image"
input_stream: "roi"
input_side_packet: "model_complexity"
node {
 calculator: "PoseLandmarkByRoiCpu"
 input_stream: "IMAGE:image"
 input_stream: "ROI:roi"
 input_side_packet: "MODEL_COMPLEXITY:model_complexity"
 output_stream: "LANDMARKS:landmarks"
 output_stream: "WORLD_LANDMARKS:world_landmarks"
}
output_stream: "landmarks"
output_stream: "world_landmarks"
''',calculator_pb2.CalculatorGraphConfig())

def crop_for(frame):
    g=cv2.cvtColor(frame,cv2.COLOR_BGR2GRAY)
    mask=np.uint8(g<100)*255
    mask[:35]=0
    mask=cv2.morphologyEx(mask,cv2.MORPH_OPEN,np.ones((3,3),np.uint8))
    n,l,stats,centers=cv2.connectedComponentsWithStats(mask)
    valid=[k for k in range(1,n) if stats[k,4]>50 and stats[k,3]>8]
    if not valid: return (150,20,480,480)
    main=max(valid,key=lambda k:stats[k,4])
    center=centers[main]
    keep=[k for k in valid if np.linalg.norm(centers[k]-center)<170]
    x0=min(stats[k,0] for k in keep);y0=min(stats[k,1] for k in keep)
    x1=max(stats[k,0]+stats[k,2] for k in keep);y1=max(stats[k,1]+stats[k,3] for k in keep)
    side=max(260,round(max(x1-x0,y1-y0)*1.45))
    return (round((x0+x1-side)/2),round((y0+y1-side)/2),side,side)

def unrot(xy,k):
    x,y=xy[:,0].copy(),xy[:,1].copy()
    return [xy,np.c_[1-y,x],np.c_[1-x,1-y],np.c_[y,1-x]][k]

cap=cv2.VideoCapture(str(WORK/'reference_hd.mp4'))
images=[]
while True:
    ok,im=cap.read()
    if not ok: break
    images.append(im)
cap.release()
chosen=[0,60,90,180,210,270,300,326] if args.pilot else range(len(images))
rows=[];started=time.time()
with SolutionBase(graph_config=config,side_inputs={'model_complexity':2},
  calculator_params={'poselandmarkbyroicpu__tensorstoposelandmarksandsegmentation__ThresholdingCalculator.threshold':.15},
  outputs=['landmarks','world_landmarks']) as model:
    for f in chosen:
        hd=images[f]
        im=cv2.resize(hd,(854,426),interpolation=cv2.INTER_AREA)
        x,y,w,h=crop_for(im)
        # Preserve HD detail in the actual landmark-model input.
        sc=hd.shape[1]/854
        hx,hy,hw=round(x*sc),round(y*sc),round(w*sc)
        crop=np.full((hw,hw,3),137,np.uint8)
        sx,sy=max(0,hx),max(0,hy);ex,ey=min(hd.shape[1],hx+hw),min(hd.shape[0],hy+hw)
        crop[sy-hy:ey-hy,sx-hx:ex-hx]=hd[sy:ey,sx:ex]
        crop=cv2.resize(crop,(512,512),interpolation=cv2.INTER_AREA)
        candidates=[]
        for k in range(4):
            inp=np.ascontiguousarray(np.rot90(crop,k))
            result=model.process({'image':cv2.cvtColor(inp,cv2.COLOR_BGR2RGB),
              'roi':rect_pb2.NormalizedRect(x_center=.5,y_center=.5,width=1,height=1,rotation=0)})
            if result.landmarks is None:
                candidates.append(None);continue
            xy=np.array([[v.x,v.y] for v in result.landmarks.landmark]);xy=unrot(xy,k)*w+[x,y]
            world=np.array([[v.x,v.y,v.z] for v in result.world_landmarks.landmark]);world[:,:2]=unrot(world[:,:2]+.5,k)-.5
            vis=np.array([v.visibility for v in result.landmarks.landmark])
            candidates.append({'rotation_quarters':k,'xy':xy.tolist(),'world':world.tolist(),
              'visibility':vis.tolist(),'quality':float(vis[[0,11,12,13,14,15,16,23,24,25,26,27,28]].mean()),'method':'explicit_roi_hd'})
        rows.append({'frame':f,'time':f/30,'crop':[x,y,w,h],'candidates':candidates})
        if len(rows)%30==0:
            print(f'ROI_CAPTURE {f+1}/{len(images)} elapsed={time.time()-started:.1f}s',flush=True)
            (WORK/'pose_roi_candidates.json').write_text(json.dumps(rows),encoding='utf-8')
dest='pose_roi_pilot.json' if args.pilot else 'pose_roi_candidates.json'
(WORK/dest).write_text(json.dumps(rows),encoding='utf-8')
print('ROI_COMPLETE',len(rows),sum(any(c for c in r['candidates']) for r in rows),flush=True)

if args.pilot:
    sheet=Image.new('RGB',(4*360,len(rows)*300),(20,23,28));draw=ImageDraw.Draw(sheet)
    edges=[(11,12),(11,23),(12,24),(23,24),(11,13),(13,15),(12,14),(14,16),(23,25),(25,27),(24,26),(26,28)]
    for j,row in enumerate(rows):
        for k,c in enumerate(row['candidates']):
            im=cv2.resize(images[row['frame']],(854,426))
            if c:
                xy=np.array(c['xy']).astype(int)
                for a,b in edges: cv2.line(im,tuple(xy[a]),tuple(xy[b]),(0,200,255) if a%2==0 else (70,255,50),2,cv2.LINE_AA)
                for a in [0,11,12,13,14,15,16,23,24,25,26,27,28]:
                    cv2.circle(im,tuple(xy[a]),2,(255,70,70),-1)
            x,y,w,h=row['crop']; x0,y0=max(0,x),max(0,y)
            tile=Image.fromarray(cv2.cvtColor(im[y0:min(y+h,426),x0:min(x+w,854)],cv2.COLOR_BGR2RGB))
            tile.thumbnail((360,270));sheet.paste(tile,(k*360,j*300+27))
            draw.text((k*360+5,j*300+5),f"F{row['frame']} ROI {k*90}  "+(f"{c['quality']:.2f}" if c else 'none'),fill='white')
    sheet.save(WORK/'roi_pilot_review.jpg',quality=95)
