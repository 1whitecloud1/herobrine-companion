"""Google MediaPipe Pose Heavy, four image orientations, original source frames.

The input is a stylized animated character, so retain every candidate and every
confidence. Rotated crops allow the detector to see inverted acrobatic poses.
The later selection stage must account for tracking discontinuities explicitly.
"""
from pathlib import Path
import argparse
import json
import time
import cv2
import mediapipe as mp
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_mocap'
parser = argparse.ArgumentParser()
parser.add_argument('--stride', type=int, default=1)
args = parser.parse_args()
metadata = json.loads((WORK / 'reference_metadata.json').read_text(encoding='utf-8'))
frames = sorted((WORK / 'frames').glob('*.png'))

def crop_for(frame):
    h, w = frame.shape[:2]
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    mask = (gray < 88).astype(np.uint8) * 255
    # Ignore the distant corner watermark and thin ground shadows / shafts.
    mask[:25] = 0
    opened = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((3,3), np.uint8))
    closed = cv2.morphologyEx(opened, cv2.MORPH_CLOSE, np.ones((9,9), np.uint8))
    n, labels, stats, centers = cv2.connectedComponentsWithStats(closed)
    if n <= 1:
        return (0, 0, w, h)
    score = stats[1:, cv2.CC_STAT_AREA].astype(float)
    score[stats[1:, cv2.CC_STAT_HEIGHT] < 20] = 0
    k = 1 + int(np.argmax(score))
    x,y,bw,bh,area = stats[k]
    # Hands / hair can be separate from the dark costume. A roomy square keeps
    # them in the crop and is invariant to detector image rotation.
    side = max(230, int(max(bw,bh)*1.8))
    cx, cy = x+bw/2, y+bh/2-8
    return (int(cx-side/2), int(cy-side/2), side, side)

def make_crop(frame, box):
    x,y,w,h = box
    result = np.full((h,w,3), 137, np.uint8)
    sx0,sy0 = max(0,x),max(0,y)
    sx1,sy1 = min(frame.shape[1],x+w),min(frame.shape[0],y+h)
    result[sy0-y:sy1-y,sx0-x:sx1-x] = frame[sy0:sy1,sx0:sx1]
    return cv2.resize(result, (512,512), interpolation=cv2.INTER_CUBIC)

def unrotate_xy(xy, k):
    x,y = xy[:,0].copy(),xy[:,1].copy()
    if k == 0: return xy.copy()
    if k == 1: return np.c_[1-y,x]
    if k == 2: return np.c_[1-x,1-y]
    return np.c_[y,1-x]

rows = []
start = time.time()
with mp.solutions.pose.Pose(static_image_mode=True, model_complexity=2,
                           smooth_landmarks=False, min_detection_confidence=.35) as detector:
    for i,path in enumerate(frames):
        if i % args.stride:
            continue
        frame = cv2.imread(str(path))
        box = crop_for(frame)
        crop = make_crop(frame, box)
        candidates = []
        for k in range(4):
            rotated = np.ascontiguousarray(np.rot90(crop,k))
            result = detector.process(cv2.cvtColor(rotated,cv2.COLOR_BGR2RGB))
            if not result.pose_landmarks:
                candidates.append(None)
                continue
            xy = np.array([[p.x,p.y] for p in result.pose_landmarks.landmark])
            xy = unrotate_xy(xy,k)
            xy = xy*box[2]+np.array(box[:2])
            world = np.array([[p.x,p.y,p.z] for p in result.pose_world_landmarks.landmark])
            # Undo only the image-plane roll. MediaPipe depth is unchanged.
            world[:,:2] = unrotate_xy(world[:,:2]+.5,k)-.5
            visibility = np.array([p.visibility for p in result.pose_landmarks.landmark])
            core = [0,11,12,13,14,15,16,23,24,25,26,27,28]
            quality = float(np.mean(visibility[core]))
            candidates.append({'rotation_quarters':k,'xy':xy.tolist(),'world':world.tolist(),
                               'visibility':visibility.tolist(),'quality':quality})
        rows.append({'frame':i,'time':i/metadata['fps'],'crop':box,'candidates':candidates})
        if len(rows)%30 == 0:
            print(f'CAPTURE {i+1}/{len(frames)} candidates={sum(c is not None for c in candidates)} elapsed={time.time()-start:.1f}s',flush=True)
            (WORK / f'pose_candidates_stride{args.stride}.json').write_text(json.dumps(rows),encoding='utf-8')
(WORK / f'pose_candidates_stride{args.stride}.json').write_text(json.dumps(rows),encoding='utf-8')
print('CAPTURE_COMPLETE',json.dumps({'frames':len(rows),'any_detection':sum(any(c for c in row['candidates']) for row in rows),'elapsed':time.time()-start}),flush=True)

# A first-pass contact sheet; the full sequence selection is performed later.
edges = list(mp.solutions.pose.POSE_CONNECTIONS)
sheet=Image.new('RGB',(4*426,9*250),(20,24,29))
draw=ImageDraw.Draw(sheet)
for j in range(36):
    row=rows[round(j*(len(rows)-1)/35)]
    frame=cv2.imread(str(frames[row['frame']]))
    valid=[c for c in row['candidates'] if c]
    best=max(valid,key=lambda c:c['quality']) if valid else None
    if best:
        xy=np.array(best['xy']).astype(int)
        for a,b in edges:
            cv2.line(frame,tuple(xy[a]),tuple(xy[b]),(20,230,90),1,cv2.LINE_AA)
        for p in xy:
            cv2.circle(frame,tuple(p),2,(20,70,255),-1)
    im=Image.fromarray(cv2.cvtColor(frame,cv2.COLOR_BGR2RGB));im.thumbnail((426,220))
    x,y=(j%4)*426,(j//4)*250
    sheet.paste(im,(x,y+26))
    label=f"F{row['frame']:03d} {row['time']:.2f}s"
    label+=f"  vis {best['quality']:.2f} rot {best['rotation_quarters']*90}" if best else '  NO POSE'
    draw.text((x+6,y+6),label,fill='white')
sheet.save(WORK/f'pose_contact_stride{args.stride}.jpg',quality=95)
