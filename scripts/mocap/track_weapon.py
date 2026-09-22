"""Track the visible scythe shaft and gold blade mount in the reference frames."""
from pathlib import Path
import json
import math
import os
import cv2
import numpy as np
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[2];WORK=Path(os.environ.get('HEROBRINE_CAPTURE_WORK', ROOT/'build/scythe_mocap'))
rows=[]
for f,path in enumerate(sorted((WORK/'frames').glob('*.png'))):
    im=cv2.imread(str(path));h,w=im.shape[:2]
    g=cv2.cvtColor(im,cv2.COLOR_BGR2GRAY);hsv=cv2.cvtColor(im,cv2.COLOR_BGR2HSV)
    dark=np.uint8(g<119)*255
    dark[:45,680:]=0
    edges=cv2.Canny(g,35,100);edges[cv2.dilate(dark,np.ones((3,3),np.uint8))==0]=0
    lines=cv2.HoughLinesP(edges,1,np.pi/720,threshold=24,minLineLength=32,maxLineGap=12)
    gold=(hsv[:,:,0]>10)&(hsv[:,:,0]<42)&(hsv[:,:,1]>48)&(hsv[:,:,2]>42)&(hsv[:,:,2]<220)
    gold[:45,680:]=False
    n,labels,stats,centers=cv2.connectedComponentsWithStats(np.uint8(gold))
    blobs=[{'xy':centers[k].tolist(),'area':int(stats[k,4])} for k in range(1,n) if stats[k,4]>=3]
    if blobs:
        main=max(blobs,key=lambda b:b['area'])
        near=[b for b in blobs if np.linalg.norm(np.array(b['xy'])-main['xy'])<35]
        gold_center=np.average([b['xy'] for b in near],axis=0,weights=[b['area'] for b in near])
    else: gold_center=None
    cands=[]
    for ln in lines[:,0] if lines is not None else []:
        a,b=np.array(ln[:2],float),np.array(ln[2:],float)
        length=float(np.linalg.norm(b-a));u=(b-a)/length;normal=np.array([-u[1],u[0]])
        pts=a+np.linspace(.1,.9,20)[:,None]*(b-a)
        # Broad silhouettes produce lines as well. Favor a thin rod over limbs.
        density=[]
        for off in (-7,-4,0,4,7):
            pp=np.rint(pts+normal*off).astype(int)
            pp[:,0]=pp[:,0].clip(0,w-1);pp[:,1]=pp[:,1].clip(0,h-1)
            density.append(np.mean(g[pp[:,1],pp[:,0]]<105))
        broad=min(density[0],density[-1])+min(density[1],density[-2])
        score=length/(1+2.2*broad)
        if min(a[1],b[1])>365: score*=.2
        gd=None
        if gold_center is not None:
            gd=float(abs(np.dot(gold_center-a,normal)))
            score*=.35+.65*math.exp(-gd*gd/(2*18*18))
        cands.append({'a':a.tolist(),'b':b.tolist(),'score':score,'length':length,'gold_distance':gd})
    cands.sort(key=lambda c:c['score'],reverse=True)
    # Collapse near-identical opposite edges of the rod.
    keep=[]
    for c in cands:
        a,b=np.array(c['a']),np.array(c['b']);u=(b-a)/c['length']
        if any(abs(np.dot(u,(np.array(t['b'])-t['a'])/t['length']))>.995 and abs(np.cross(u,np.array(t['a'])-a))<7 for t in keep):continue
        keep.append(c)
        if len(keep)>=6:break
    rows.append({'frame':f,'gold_center':gold_center.tolist() if gold_center is not None else None,'gold_blobs':blobs,'candidates':keep})
(WORK/'weapon_candidates.json').write_text(json.dumps(rows),encoding='utf-8')
out=WORK/'weapon_tracking';out.mkdir(exist_ok=True)
for page,start in enumerate(range(0,len(rows),120)):
    sheet=Image.new('RGB',(4*427,4*250),(21,24,30));draw=ImageDraw.Draw(sheet)
    for j,f in enumerate(range(start,min(start+120,len(rows)),8)):
        row=rows[f];im=cv2.imread(str(WORK/'frames'/f'{f:04d}.png'))
        for k,c in enumerate(row['candidates'][:2]):
            a,b=np.array(c['a']).astype(int),np.array(c['b']).astype(int)
            cv2.line(im,tuple(a),tuple(b),[(0,0,255),(0,240,240)][k],2,cv2.LINE_AA)
        if row['gold_center']:
            cv2.circle(im,tuple(np.array(row['gold_center']).astype(int)),6,(255,140,0),2)
        tile=Image.fromarray(cv2.cvtColor(im,cv2.COLOR_BGR2RGB));tile.thumbnail((427,218))
        x,y=(j%4)*427,(j//4)*250;sheet.paste(tile,(x,y+24))
        draw.text((x+6,y+5),f'F{f:03d} {f/30:.2f}s',fill='white')
    sheet.save(out/f'candidate_page_{page+1}.jpg',quality=95)
print('WEAPON_TRACKING',len(rows),'frames',sum(bool(r['candidates']) for r in rows),'with visible segments')
