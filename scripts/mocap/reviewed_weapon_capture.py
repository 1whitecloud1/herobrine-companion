"""Image measurements of the shaft, corrected where the gold pommel fooled CV.

MediaPipe provides the person. A shaft is not a MediaPipe landmark. These sparse
head/tail observations disambiguate OpenCV line detections in the same frames;
they are measurements of this clip, not a library of repeated swing curves.
"""
import json
from pathlib import Path
import cv2
import numpy as np
from scipy.ndimage import gaussian_filter1d

WORK = Path(__file__).resolve().parents[2]/'build/scythe_recapture_05'
# Original 30 Hz frames, zero based; points are in the 854 x 426 review images.
ANCHORS = [
    (15,280,150,450,230),(18,501,331,350,204),(21,494,332,340,206),
    (24,493,325,319,214),(27,475,271,295,273),(30,452,203,490,274),
    (33,236,167,432,270),(36,183,268,403,236),(39,420,66,393,220),
    (42,378,81,390,220),(45,363,82,368,224),(48,335,83,355,265),
    (51,270,160,408,276),(54,450,210,365,213),(57,163,180,405,195),
    (60,320,194,475,188),(63,348,214,484,179),(66,317,230,499,239),
    (69,481,148,405,232),(72,412,110,444,184),(75,556,212,364,176),
    (78,504,260,464,175),(81,342,256,495,264),(84,420,149,544,303),
    (93,198,261,365,217),(96,360,187,496,221),(99,224,209,464,251),
    (102,279,183,449,290),(105,257,183,426,280),(108,233,195,421,309),
    (111,330,177,339,290),(114,310,202,318,286),(117,543,204,375,223),
    (120,421,120,446,263),(123,486,219,379,260),(126,264,168,453,219),
    (129,381,194,295,276),(132,422,313,475,155),(135,536,192,381,155),
    (138,422,144,294,198),(141,318,341,328,261),(144,273,165,295,237),
    (147,214,307,363,205),(150,211,310,354,200),(153,250,292,352,223),
    (156,442,286,249,303),(159,331,261,287,377),(162,307,257,315,337),
    (165,371,288,480,273),(168,406,266,292,244),(170,200,255,257,158),
]


def capture_axes():
    observations = json.loads((WORK/'weapon_candidates.json').read_text())
    # Additional frame-by-frame checks at the fastest contacts. A 3-frame
    # review interval misses the complete dash slash at frame 97.
    extra=[(16,490,177,472,274),(17,533,320,344,215),
           (55,540,186,397,178),(56,498,162,335,173),
           (70,690,311,444,204),(71,255,80,499,187),
           (73,497,147,388,188),(74,537,180,392,193),
           (97,658,295,400,276),(98,217,255,425,240),
           (131,272,293,406,223),(133,492,258,456,154),
           (139,260,210,430,160),(140,136,307,389,163),
           (163,283,258,354,307),(164,248,261,438,291),
           (166,589,273,438,255),(167,535,273,400,260),(169,408,264,254,202)]
    anchors = np.array(sorted(ANCHORS+extra), float)
    positions = np.stack([np.interp(np.arange(171), anchors[:,0], anchors[:,j]) for j in range(1,5)],axis=1)
    result, measurements = [], []
    for f,(hx,hy,tx,ty) in enumerate(positions):
        head,tail=np.array([hx,hy]),np.array([tx,ty])
        expected=head-tail
        expected/=max(1e-9,np.linalg.norm(expected))
        choices=[]
        for line in observations[f]['candidates']:
            a,b=np.array(line['a']),np.array(line['b'])
            direction=(b-a)/np.linalg.norm(b-a)
            if np.dot(direction,expected)<0:direction=-direction
            normal=np.array([-direction[1],direction[0]])
            angle=np.arccos(np.clip(np.dot(direction,expected),-1,1))
            distance=(abs((head-a)@normal)+abs((tail-a)@normal))/2
            score=(angle/.22)**2+distance/12-np.log(max(line['length'],1)/100)
            choices.append((score,direction,line))
        score,direction,line=min(choices,key=lambda x:x[0])
        if score>3.5:
            direction=expected
            method='reviewed image measurement / occlusion interpolation'
        else:
            # Constrain a line on the blade or an afterimage to the observed
            # shaft. This also prevents accidental 180-degree head/tail swaps.
            direction=direction*.7+expected*.3
            direction/=np.linalg.norm(direction)
            method='OpenCV line + reviewed head identity'
        projected=float(np.clip(np.linalg.norm(head-tail)/235,.20,.89))
        # Camera-depth is ambiguous in one view. The forward hemisphere is the
        # combat constraint; image-plane direction/foreshortening stay captured.
        axis=np.array([direction[0]*projected,-np.sqrt(1-projected*projected),-direction[1]*projected])
        result.append(axis)
        measurements.append({'frame':f,'head':head.tolist(),'tail':tail.tolist(),'method':method,'line_score':float(score)})
    result=gaussian_filter1d(np.array(result),.55,axis=0,mode='nearest')
    result/=np.linalg.norm(result,axis=1)[:,None]
    (WORK/'weapon_selected.json').write_text(json.dumps({'anchors':anchors.tolist(),'axes':result.tolist(),'measurements':measurements},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return result


if __name__=='__main__':
    capture_axes()
