"""Fresh Google MediaPipe Heavy capture of the actual 0.5x reference video."""
import hashlib
import argparse
import json
import math
import shutil
import subprocess
import time
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from google.protobuf import text_format
from mediapipe.framework import calculator_pb2
from mediapipe.framework.formats import rect_pb2
from mediapipe.python.solution_base import SolutionBase

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--work', type=Path, default=ROOT/'build/scythe_recapture_05')
parser.add_argument('--source-seconds', type=float)
args = parser.parse_args()
WORK = args.work.resolve()
WORK.mkdir(parents=True, exist_ok=True)
SOURCE = ROOT/'build/scythe_mocap/reference_hd.mp4'
SLOW = WORK/'reference_half_speed.mp4'
subprocess.run([shutil.which('ffmpeg'), '-hide_banner', '-loglevel', 'error', '-y', '-i', str(SOURCE),
                '-vf', 'setpts=2*PTS', '-r', '30', '-an', '-c:v', 'libx264', '-crf', '14',
                '-pix_fmt', 'yuv420p', str(SLOW)], check=True)
config = text_format.Parse('''
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
''', calculator_pb2.CalculatorGraphConfig())
CORE = [0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32]


def crop_box(im):
    gray = cv2.cvtColor(im, cv2.COLOR_BGR2GRAY)
    mask = np.uint8(gray < 100)*255
    mask[:35] = 0
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((3,3), np.uint8))
    _, _, stats, centers = cv2.connectedComponentsWithStats(mask)
    valid = [k for k in range(1,len(stats)) if stats[k,4] > 50 and stats[k,3] > 8]
    main = max(valid, key=lambda k:stats[k,4])
    keep = [k for k in valid if np.linalg.norm(centers[k]-centers[main]) < 145]
    x0, y0 = min(stats[k,0] for k in keep), min(stats[k,1] for k in keep)
    x1, y1 = max(stats[k,0]+stats[k,2] for k in keep), max(stats[k,1]+stats[k,3] for k in keep)
    size = max(250, round(max(x1-x0,y1-y0)*1.4))
    return [round((x0+x1-size)/2), round((y0+y1-size)/2), size, size]


def candidate(landmarks, world_landmarks, box, method, turn=0):
    if landmarks is None or world_landmarks is None:
        return None
    xy = np.array([[v.x,v.y] for v in landmarks.landmark])
    world = np.array([[v.x,v.y,v.z] for v in world_landmarks.landmark])
    if turn == 2:
        xy = 1-xy
        world[:,:2] *= -1
    xy = xy*box[2]+box[:2]
    vis = np.array([v.visibility for v in landmarks.landmark])
    return {'xy':xy.tolist(), 'world':world.tolist(), 'visibility':vis.tolist(),
            'quality':float(vis[CORE].mean()), 'method':method, 'rotation_quarters':turn}


cap = cv2.VideoCapture(str(SLOW))
fps, total = cap.get(cv2.CAP_PROP_FPS), int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
assert fps == 30 and 1000 < total < 1060
if args.source_seconds is not None:
    total = min(total, int(args.source_seconds * fps * 2) + 1)
rows = []
started = time.time()
with mp.solutions.pose.Pose(static_image_mode=False, model_complexity=2, smooth_landmarks=False,
                           min_detection_confidence=.3, min_tracking_confidence=.35) as tracker, \
     SolutionBase(graph_config=config, side_inputs={'model_complexity':2},
                  calculator_params={'poselandmarkbyroicpu__tensorstoposelandmarksandsegmentation__ThresholdingCalculator.threshold':.15},
                  outputs=['landmarks','world_landmarks']) as roi:
    for f in range(total):
        ok, hd = cap.read()
        if not ok:
            break
        im = cv2.resize(hd, (854,426), interpolation=cv2.INTER_AREA)
        box = crop_box(im)
        x,y,size,_ = box
        ratio = hd.shape[1]/854
        hx,hy,hw = round(x*ratio),round(y*ratio),round(size*ratio)
        crop = np.full((hw,hw,3), 137, np.uint8)
        sx,sy,ex,ey = max(0,hx),max(0,hy),min(hd.shape[1],hx+hw),min(hd.shape[0],hy+hw)
        crop[sy-hy:ey-hy,sx-hx:ex-hx] = hd[sy:ey,sx:ex]
        rgb = cv2.cvtColor(cv2.resize(crop,(512,512)), cv2.COLOR_BGR2RGB)
        tracked = tracker.process(rgb)
        candidates = [candidate(tracked.pose_landmarks, tracked.pose_world_landmarks, box, 'google_video_heavy')]
        predicted = roi.process({'image':rgb, 'roi':rect_pb2.NormalizedRect(x_center=.5,y_center=.5,width=1,height=1,rotation=0)})
        candidates.append(candidate(predicted.landmarks, predicted.world_landmarks, box, 'google_roi_heavy'))
        if not any(c and c['quality'] > .65 for c in candidates) or 8.5 < f/60 < 12.9:
            inverted = roi.process({'image':np.ascontiguousarray(np.rot90(rgb,2)),
                                   'roi':rect_pb2.NormalizedRect(x_center=.5,y_center=.5,width=1,height=1,rotation=0)})
            candidates.append(candidate(inverted.landmarks, inverted.world_landmarks, box, 'google_roi_heavy_inverted', 2))
        rows.append({'frame':f, 'time':f/fps, 'source_time':f/(fps*2), 'crop':box,
                     'candidates':[c for c in candidates if c is not None]})
        if f % 60 == 0:
            print('HALF_SPEED_CAPTURE', f, '/', total, 'source_seconds', round(f/60,2), 'elapsed', round(time.time()-started,1), flush=True)
            (WORK/'pose_half_speed_candidates.json').write_text(json.dumps(rows,separators=(',',':')),encoding='utf-8')
cap.release()
(WORK/'pose_half_speed_candidates.json').write_text(json.dumps(rows,separators=(',',':')),encoding='utf-8')
report = {'engine':'Google MediaPipe Pose Heavy / GHUM', 'mediapipe_version':mp.__version__,
          'source':str(SOURCE), 'source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
          'input':str(SLOW), 'input_sha256':hashlib.sha256(SLOW.read_bytes()).hexdigest(),
          'input_fps':fps, 'reference_playback_rate':.5, 'processed_frames':len(rows),
          'frames_with_candidates':sum(bool(r['candidates']) for r in rows), 'elapsed_seconds':time.time()-started,
          'note':'Fresh video tracking and independent ROI inference on every half-speed frame. Slowing playback repeats source frames; it does not create new image detail.'}
(WORK/'capture_report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('HALF_SPEED_CAPTURE_COMPLETE',json.dumps(report),flush=True)
