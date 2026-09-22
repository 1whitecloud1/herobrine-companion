"""Track the visible gray ground shadow to compensate for camera reframing."""
import json
from pathlib import Path
import cv2
import numpy as np
from scipy.ndimage import gaussian_filter1d,median_filter

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
observed=[];rows=[]
for f in range(519):
    im=cv2.imread(str(WORK/'frames'/f'{f:04d}.png'))
    gray=cv2.cvtColor(im,cv2.COLOR_BGR2GRAY);hsv=cv2.cvtColor(im,cv2.COLOR_BGR2HSV)
    background=float(np.median(gray[40:100,20:180]))
    mask=np.uint8((gray<background-5)&(hsv[:,:,1]<50))*255;mask[:320]=0
    opened=cv2.morphologyEx(mask,cv2.MORPH_OPEN,np.ones((2,43),np.uint8))
    _,_,stats,_=cv2.connectedComponentsWithStats(opened)
    candidates=[(float(y+1),int(area)) for x,y,w,h,area in stats[1:]
                if w>55 and h<28 and area>95]
    value=max(candidates,key=lambda c:c[0])[0] if candidates else None
    # The floor leaves the image during this jump; its true camera offset is
    # unknowable. Retargeting uses a documented aerial arc for those frames.
    if 349<=f<=372:value=None
    observed.append(value);rows.append(np.nan if value is None else value)
valid=np.flatnonzero(np.isfinite(rows));rows=np.array(rows)
filled=np.interp(np.arange(len(rows)),valid,rows[valid])
filled=gaussian_filter1d(median_filter(filled,size=7,mode='nearest'),2.5,mode='nearest')
report={'floor_y':filled.tolist(),'observed_y':observed,'method':'Horizontal gray ground-shadow tracking; median and Gaussian temporal filtering.',
        'unobserved_aerial_source_frames':[349,372]}
(WORK/'floor_tracking.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('FLOOR_TRACKED',len(valid),'/',len(rows),'range',float(filled.min()),float(filled.max()))
