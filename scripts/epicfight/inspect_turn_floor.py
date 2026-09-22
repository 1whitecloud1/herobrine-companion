"""Report actual skinned knee/sole floor failures before any global lift."""
from pathlib import Path
import sys

p=Path(__file__).resolve().parent/'verify_mediapipe.py'
source_code=p.read_text(encoding='utf-8')
exec(compile(source_code[:source_code.index('turning_verified=False')],str(p),'exec'))
peaks=[]
for i,time in enumerate(times):
    w=compose(values,i)
    for matrix in w.values():
        matrix[2,3]-=report['floor_correction_blocks'][i]
    vv=skin(w)
    k=int(vv[:,2].argmin())
    weights={n:float(ww[list(ix).index(k)]) for n,(ix,ww) in groups.items() if k in ix}
    peaks.append((float(vv[k,2]),float(time*fps+1),k,weights,
                  {n:w[n][:3,3].round(4).tolist() for n in ('Root','Thigh_R','Leg_R','Knee_R','Thigh_L','Leg_L','Knee_L')}))
print('FLOOR_PEAKS',sorted(peaks)[:6],flush=True)
