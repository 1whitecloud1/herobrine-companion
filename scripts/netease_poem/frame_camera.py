"""Frame the full blade arc, including the intentional throwing sequence."""
import numpy as np
from scipy.ndimage import maximum_filter1d, gaussian_filter1d
from common import *

data=read(WORK/'preview.json'); matrices=np.load(WORK/'preview_matrices.npy')
direction=np.array([5.8,10.,3.6]);direction/=np.linalg.norm(direction)
right=np.cross([0.,0.,1.],direction);right/=np.linalg.norm(right)
up=np.cross(direction,right)
axes=np.array([right,up])
low=np.full((len(matrices),2),1e10);high=-low.copy()
for j,mesh in enumerate(data['meshes']):
    vertices=np.array(mesh['vertices'])
    world=np.einsum('nij,vj->nvi',matrices[:,j,:3,:3],vertices)+matrices[:,j,None,:3,3]
    projected=world@axes.T
    low=np.minimum(low,projected.min(axis=1));high=np.maximum(high,projected.max(axis=1))
span=high-low
center=(low+high)/2
origin=np.array([0.,0.,1.])
desired=center-origin@axes.T
desired=gaussian_filter1d(desired,12,axis=0)
half_extent=np.maximum(np.abs(low-(origin@axes.T+desired)),np.abs(high-(origin@axes.T+desired)))
scale=np.maximum(5.8,np.maximum(half_extent[:,0]*.75,half_extent[:,1])*2*1.20)
# Look ahead before a fast release; no blade is cropped by a late camera zoom.
scale=gaussian_filter1d(maximum_filter1d(scale,size=61,mode='nearest'),8)
positions=origin+desired[:,0,None]*right+desired[:,1,None]*up+direction*np.linalg.norm([5.8,10.,3.6])
write(WORK/'camera.json',{'positions':positions.tolist(),'scales':scale.tolist()})
print('CAMERA',float(scale.min()),float(scale.max()))
