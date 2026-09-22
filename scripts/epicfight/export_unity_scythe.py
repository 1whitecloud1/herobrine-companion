"""Retarget native scythe FBX motion to the actual Epic Fight / Herobrine rig.

This produces an independent animation library. Existing combat resources and
V6 registrations are not changed. Native full-body orientation, root travel,
weapon release, regrips and source clip timing are retained.
"""
import argparse
import hashlib
import math
import sys

import numpy as np
from scipy.ndimage import gaussian_filter1d
from scipy.optimize import minimize
from scipy.spatial.transform import Rotation

from unity_scythe_common import (ASSETS,C,OUT,ROOT,TORSO_AXES,WEAPON_AXES,WORK,
                                GameMesh,NativeReference,affine,basis,load,rotations,unit,write)

HZ=240
WEAPON_SCALE=.62
# Place the original hand position within the longer Poem of the End handle.
# This is a translation along the shaft, never a roll of the blade.
GRIP_OFFSET=np.array([0.,0.,.25])


def smoothstep(x):
    x=np.clip(x,0.,1.)
    return x*x*(3-2*x)


class Retarget:
    def __init__(self,mesh,native):
        self.g=mesh;self.n=native
        self.root_scale=mesh.rest['Root'][2,3]/native.pos['pelvis'][2]
        lengths=[]
        for s in ('r','l'):
            palm=(native.pos['middle_02_'+s]+native.pos['thumb_03_'+s])*.5
            lengths.append(np.linalg.norm(native.pos['lowerarm_'+s]-native.pos['upperarm_'+s])+np.linalg.norm(palm-native.pos['lowerarm_'+s]))
        self.arm_scale=(np.linalg.norm(mesh.local['Hand_R'][:3,3])+np.linalg.norm(mesh.local['Tool_R'][:3,3]))/np.mean(lengths)
        _,ix=np.unique(np.floor(mesh.weapon_vertices/.07).astype(int),axis=0,return_index=True)
        self.weapon_samples=np.vstack([mesh.weapon_vertices[ix],np.c_[np.zeros(90),np.zeros(90),np.linspace(-2.08,.98,90)]])

    def fit_weapon(self,world,wp,wr,grips,weights,radii,initial,floor):
        """Fit the rigid pole, allowing a common slide of the hands on it."""
        boxes=[('Head',[0.,.25,0.],[.25,.25,.25]),
               ('Chest',[0.,.2,0.],[.25,.20,.125]),
               ('Torso',[0.,.125,0.],[.25,.175,.125])]
        rotations=np.array([world[n][:3,:3] for n,c,h in boxes])
        centers=np.array([world[n][:3,3]+world[n][:3,:3] @ c for n,c,h in boxes])
        extents=np.array([h for n,c,h in boxes])
        points=self.weapon_samples @ wr.T+wp
        hands={s:wp+wr @ grips[s]-world['Arm_'+s][:3,3] for s in ('R','L')}
        limits={s:radii[s]+(1-weights[s])*2/max(weights[s],.001) for s in ('R','L')}
        axis=unit(wr[:,2]);active=[s for s in weights if weights[s]>.95]
        lo=max([-.70]+[-1.98*WEAPON_SCALE-grips[s][2]*WEAPON_SCALE for s in active])
        hi=min([.70]+[1.05*WEAPON_SCALE-grips[s][2]*WEAPON_SCALE for s in active])
        if lo>hi:lo,hi=0.,0.
        cache={}
        def constraints(x):
            if 'x' in cache and np.array_equal(cache['x'],x):return cache['value']
            shift=x[:3];hand_shift=shift+axis*x[3]
            local=np.einsum('pbi,bij->pbj',points[:,None,:]+shift-centers[None,:,:],rotations)
            q=np.abs(local)-extents
            outside=np.maximum(q,0.)
            sdf=np.linalg.norm(outside,axis=2)+np.minimum(q.max(axis=2),0.)
            nearest=sdf.argmin(axis=0)
            active_q=q[nearest,np.arange(len(boxes))];loc=local[nearest,np.arange(len(boxes))]
            vec=np.maximum(active_q,0.);length=np.linalg.norm(vec,axis=1)
            grad=vec/np.maximum(length[:,None],1e-12)
            inside=length<1e-10;grad[inside]=0.
            grad[np.flatnonzero(inside),active_q[inside].argmax(axis=1)]=1.
            grad*=np.sign(loc)
            gradient=np.einsum('bij,bj->bi',rotations,grad)
            value=np.r_[limits['R']**2-np.sum((hands['R']+hand_shift)**2),
                        limits['L']**2-np.sum((hands['L']+hand_shift)**2),
                        shift[2]-floor,sdf.min(axis=0)-.018]
            jac=np.vstack([np.r_[-2*(hands['R']+hand_shift),-2*np.dot(hands['R']+hand_shift,axis)],
                           np.r_[-2*(hands['L']+hand_shift),-2*np.dot(hands['L']+hand_shift,axis)],
                           [0,0,1,0],np.c_[gradient,np.zeros(len(gradient))]])
            for s in active:
                value=np.r_[value,(wp+wr @ grips[s]+hand_shift)[2]-.20]
                jac=np.vstack([jac,[0,0,1,axis[2]]])
                delta=hands[s]+hand_shift
                value=np.r_[value,np.sum(delta*delta)-.16**2]
                jac=np.vstack([jac,np.r_[2*delta,2*np.dot(delta,axis)]])
            cache.update(x=x.copy(),value=value,jac=jac)
            return value
        initial4=np.r_[initial,0.]
        previous=np.zeros(4) if self.last_weapon_shift is None else self.last_weapon_shift
        if max(weights.values())<.01 or (constraints(initial4).min()>-1e-7 and np.linalg.norm(initial)<.04 and np.linalg.norm(previous)<.005):
            self.last_weapon_shift=initial4.copy()
            return initial,float(constraints(initial4)[3:6].min()+.018),0.
        def objective(x):
            hand=x[:3]+axis*x[3]
            prior=previous[:3]+axis*previous[3]
            value=constraints(x)
            penetration=np.minimum(value[3:6],0.)
            folded=np.minimum(value[7::2],0.)
            # A human's crossed grip can be infeasible around the larger block
            # head. Treat volume clearance as a continuous fitting cost, never
            # jump between disconnected hard-collision solutions in one frame.
            return (np.sum(hand*hand)+.12*np.sum(x[:3]**2)+.10*x[3]**2
                    +5.*np.sum((hand-prior)**2)+.3*(x[3]-previous[3])**2
                    +80.*np.sum(penetration**2)+80.*np.sum(folded**2))
        def jac(x):
            hand=x[:3]+axis*x[3];prior=previous[:3]+axis*previous[3]
            dh=2*hand+10.*(hand-prior)
            value=constraints(x)
            gradient=np.r_[dh+.24*x[:3],np.dot(dh,axis)+.20*x[3]+.60*(x[3]-previous[3])]
            gradient+=160.*np.minimum(value[3:6],0.) @ cache['jac'][3:6]
            gradient+=160.*np.minimum(value[7::2],0.) @ cache['jac'][7::2]
            return gradient
        seeds=[previous,initial4]
        for axis in (world['Head'][:3,0],world['Head'][:3,2],world['Head'][:3,1]):
            seeds.extend([np.r_[initial+axis*.3,0.],np.r_[initial-axis*.3,0.]])
        axis=unit(wr[:,2])
        seeds.extend([np.r_[initial-axis*lo,lo],np.r_[initial-axis*hi,hi]])
        hard=np.r_[np.arange(3),np.arange(6,6+2*len(active),2)]
        fits=[]
        for seed in seeds:
            result=minimize(objective,seed,jac=jac,method='SLSQP',
                     bounds=[(None,None)]*3+[(lo,hi)],
                     constraints=[dict(type='ineq',fun=lambda x:constraints(x)[hard],
                                       jac=lambda x:(constraints(x),cache['jac'][hard])[1])],
                     options=dict(maxiter=85,ftol=1e-9))
            if constraints(result.x)[hard].min()>-2e-5:
                fits.append(result.x)
                break
        if fits:
            shift=min(fits,key=objective)
        else:
            # Keep a measurable failed-fit result for the report and review.
            shift=initial4
        self.last_weapon_shift=shift.copy()
        return shift[:3],float(constraints(shift)[3:6].min()+.018),float(shift[3])

    def limb(self,world,side,goal,hint,leg=False,commit=True,pole=None):
        g=self.g
        upper=('Thigh_' if leg else 'Arm_')+side
        lower=('Leg_' if leg else 'Hand_')+side
        local_end=g.foot_centers[side] if leg else g.local['Tool_'+side][:3,3]
        a=world[upper][:3,3].copy()
        l1=np.linalg.norm(g.local[lower][:3,3]);l2=np.linalg.norm(local_end)
        vector=goal-a;reach=np.linalg.norm(vector);axis=unit(vector,(0.,0.,-1.))
        reach=np.clip(reach,abs(l1-l2)+.003,l1+l2-.001)
        end=a+axis*reach
        # A native knee position cannot be used as a pole after changing hip
        # width and leg proportions: almost straight legs then bend sideways
        # or backward. Transfer the native bending plane as a direction.
        pole=hint-a if pole is None else pole
        bend=pole-axis*np.dot(pole,axis)
        key=upper
        if np.linalg.norm(bend)<1e-6:
            bend=np.cross(world['Root'][:3,0],axis) if leg else np.cross(axis,world['Root'][:3,0])
        bend=unit(bend,(0.,1.,0.))
        previous=self.previous.get(key)
        if previous is not None:
            old_bend,old_hinge,old_axis=previous
            cross=np.cross(old_axis,axis);sine=np.linalg.norm(cross);cosine=np.clip(np.dot(old_axis,axis),-1,1)
            transport=Rotation.from_rotvec(unit(cross)*math.atan2(sine,cosine)).as_matrix() if sine>1e-8 else np.eye(3)
            old=transport @ old_bend
            old=unit(old-axis*np.dot(old,axis),bend)
            angle=math.atan2(np.dot(axis,np.cross(old,bend)),np.dot(old,bend))
            angle*=1-math.exp(-1/(HZ*(.014 if leg else .018)))
            angle=np.clip(angle,-math.radians(1500)/HZ,math.radians(1500)/HZ)
            bend=old*math.cos(angle)+np.cross(axis,old)*math.sin(angle)
        along=(l1*l1-l2*l2+reach*reach)/(2*reach)
        radius=math.sqrt(max(0.,l1*l1-along*along))
        if radius>1e-6:
            up=np.array([0.,0.,1.])-axis*axis[2]
            if np.linalg.norm(up)>1e-5:
                up=unit(up)
                required=((.205 if leg else .17)-a[2]-axis[2]*along)/(radius*up[2])
                if -1<required<1:
                    limit=math.acos(np.clip(required,-1,1))
                    angle=math.atan2(np.dot(axis,np.cross(up,bend)),np.dot(up,bend))
                    angle=np.clip(angle,-limit,limit)
                    bend=up*math.cos(angle)+np.cross(axis,up)*math.sin(angle)
        joint=a+axis*along+bend*radius
        hinge=unit(np.cross(axis,bend) if leg else np.cross(bend,axis))
        # A knee has an oriented hinge. Choosing its sign by proximity to the
        # previous frame can turn both the thigh and shin around by 180 degrees
        # after the straight-leg singularity. Arms keep their existing fit.
        if not leg:
            reference=previous[1] if previous is not None else world[upper][:3,0]
            if np.dot(hinge,reference)<0:hinge=-hinge
        if commit:self.previous[key]=(bend.copy(),hinge.copy(),axis.copy())
        world[upper][:3,:3]=basis(joint-a,hinge) @ basis(g.local[lower][:3,3],[1.,0.,0.]).T
        world[lower][:3,:3]=basis(end-joint,hinge) @ basis(local_end,[1.,0.,0.]).T
        world[lower][:3,3]=joint
        return end

    def knee_pole(self,positions,rs,side):
        suffix=side.lower()
        thigh='thigh_'+suffix
        a,k,e=(C @ positions[n+suffix] for n in ('thigh_','calf_','foot_'))
        axis=unit(e-a,(0.,0.,-1.))
        projected=k-a-axis*np.dot(k-a,axis)
        # At extension the position-derived plane has no stable direction.
        # The native thigh's rotation still supplies its anatomical front.
        forward=C @ rs[thigh] @ self.n.rot[thigh].T @ np.array([0.,-1.,0.])
        forward=unit(forward-axis*np.dot(forward,axis),unit(projected,(0.,1.,0.)))
        flex=np.linalg.norm(projected)/max(np.linalg.norm(k-a),1e-8)
        weight=float(smoothstep((flex-.04)/.10))
        return unit(forward*(1-weight)+unit(projected,forward)*weight,forward)

    def legs(self,world,positions,rs,origin):
        feet={}
        for side in ('R','L'):
            sn='calf_'+side.lower()
            center,lift=self.n.soles(positions,rs,side)
            xy=(C @ (center-origin)*self.root_scale)[:2]+self.g.rest['Root'][:2,3]
            goal=np.r_[xy,.1+lift*self.root_scale]
            hint=world['Root'][:3,3]+C @ (positions[sn]-positions['pelvis'])*self.root_scale
            pole=self.knee_pole(positions,rs,side)
            for _ in range(30):
                end=self.limb(world,side,goal,hint,leg=True,commit=False,pole=pole)
                offsets=(self.g.foot_points[side]-self.g.foot_centers[side]) @ world['Leg_'+side][:3,:3].T
                next_z=.012+lift*self.root_scale-float(offsets[:,2].min())
                if abs(goal[2]-next_z)<1e-7:break
                goal[2]+=.65*(next_z-goal[2])
            end=self.limb(world,side,goal,hint,leg=True,pole=pole)
            sole=self.g.foot_points[side] @ world['Leg_'+side][:3,:3].T+world['Leg_'+side][:3,3]
            feet[side]=dict(source_lift=lift,minimum=float(sole[:,2].min()),xy_error=float(np.linalg.norm(end[:2]-xy)),
                            center=end.tolist(),desired_xy=xy.tolist())
        return feet

    def convert(self,clip):
        g=self.g
        data=np.load(WORK/'native_samples'/f"{clip['name']}.npz")
        times=data['times'];names=list(data['names']);raw=data['world'].astype(float)
        rr=rotations(raw)
        if HZ==240:
            count=len(raw);q=Rotation.from_matrix(rr.reshape(-1,3,3)).as_quat().reshape(count,len(names),4)
            a,b=q[:-1],q[1:].copy();b[np.sum(a*b,axis=-1)<0]*=-1
            middle=a+b;middle/=np.linalg.norm(middle,axis=-1)[...,None]
            qq=np.empty((2*count-1,len(names),4));qq[::2]=q;qq[1::2]=middle
            positions=np.empty((2*count-1,len(names),3));positions[::2]=raw[:,:,:3,3];positions[1::2]=(raw[:-1,:,:3,3]+raw[1:,:,:3,3])*.5
            rr=Rotation.from_quat(qq.reshape(-1,4)).as_matrix().reshape(2*count-1,len(names),3,3)
            raw=np.broadcast_to(np.eye(4),(2*count-1,len(names),4,4)).copy()
            raw[:,:,:3,:3]=rr*.01;raw[:,:,:3,3]=positions
            times=np.arange(len(raw))/HZ
        ps={n:raw[:,i,:3,3] for i,n in enumerate(names)}
        rots={n:rr[:,i] for i,n in enumerate(names)}
        palms={s:(ps['middle_02_'+s.lower()]+ps['thumb_03_'+s.lower()])*.5 for s in ('R','L')}
        origin=ps['pelvis'][0].copy();origin[2]=0.
        local_grips={s:np.einsum('nij,nj->ni',np.transpose(rots['Scythe_Weapon_R'],(0,2,1)),palms[s]-ps['Scythe_Weapon_R']) for s in ('R','L')}
        contacts={}
        for s,grips in local_grips.items():
            radial=np.linalg.norm(grips[:,[0,2]],axis=1)
            w=smoothstep((.14-radial)/.075)*smoothstep((grips[:,1]+1.23)/.12)*smoothstep((.57-grips[:,1])/.12)
            contacts[s]=np.clip(gaussian_filter1d(w,HZ/120,mode='nearest'),0,1)
            contacts[s][contacts[s]>.995]=1.
            contacts[s][contacts[s]<.005]=0.
            # Put a gripping block palm on the pole center. Release motion is
            # preserved as the native hand travels away from that centerline.
            grips[:,0]*=1-contacts[s];grips[:,2]*=1-contacts[s]
        target_grips={s:(local_grips[s] @ WEAPON_AXES*self.arm_scale+GRIP_OFFSET)/WEAPON_SCALE for s in ('R','L')}
        worlds=[];metrics=[];self.previous={};self.last_weapon_shift=None;g.helper_angles={}
        torso={'Root':'pelvis','Torso':'spine_02','Chest':'spine_05','Head':'head'}
        for i,t in enumerate(times):
            p={n:v[i] for n,v in ps.items()};r={n:v[i] for n,v in rots.items()}
            world={}
            for n in g.names:
                pw=world[g.parents[n]] if g.parents[n] else np.eye(4)
                m=pw @ g.local[n]
                if n in torso:
                    m[:3,:3]=C @ r[torso[n]] @ TORSO_AXES
                    if n=='Root':
                        m[:3,3]=C @ (p['pelvis']-origin)*self.root_scale
                        m[:2,3]+=g.rest[n][:2,3]
                world[n]=m
            root_adaptation=max(0.,.48-world['Root'][2,3])
            if root_adaptation:
                for m in world.values():m[2,3]+=root_adaptation
            # Transfer clavicle motion relative to its own source bind pose,
            # keeping the Minecraft shoulder width in the neutral position.
            for s in ('R','L'):
                sn='upperarm_'+s.lower()
                bind_offset=self.n.rot['spine_05'].T @ (self.n.pos[sn]-self.n.pos['spine_05'])
                deviation=p[sn]-(p['spine_05']+r['spine_05'] @ bind_offset)
                shift=C @ deviation*self.arm_scale
                for n in ('Shoulder_'+s,'Arm_'+s,'Hand_'+s,'Tool_'+s,'Elbow_'+s):world[n][:3,3]+=shift
            feet=self.legs(world,p,r,origin)
            wanted={};hints={}
            for s in ('R','L'):
                shoulder=world['Arm_'+s][:3,3]
                source_shoulder=p['upperarm_'+s.lower()]
                wanted[s]=shoulder+C @ (palms[s][i]-source_shoulder)*self.arm_scale
                wanted[s][2]=max(.20,wanted[s][2])
                hints[s]=shoulder+C @ (p['lowerarm_'+s.lower()]-source_shoulder)*self.arm_scale
                # Free hands follow the native limb direction at actual game
                # limb length, before the rigid pole constraint is applied.
                delta=wanted[s]-shoulder
                radius=np.linalg.norm(g.local['Hand_'+s][:3,3])+np.linalg.norm(g.local['Tool_'+s][:3,3])-.001
                if np.linalg.norm(delta)>radius:wanted[s]=shoulder+unit(delta)*radius
                if np.linalg.norm(delta)<.16:wanted[s]=shoulder+unit(delta)*.16
            wr=C @ r['Scythe_Weapon_R'] @ WEAPON_AXES*WEAPON_SCALE
            grips={s:target_grips[s][i].copy() for s in ('R','L')}
            weights={s:float(contacts[s][i]) for s in ('R','L')}
            origin_r=wanted['R']-wr @ grips['R'];origin_l=wanted['L']-wr @ grips['L']
            left_anchor=weights['L']*(1-weights['R'])
            wp=origin_r*(1-left_anchor)+origin_l*left_anchor
            radii={s:np.linalg.norm(g.local['Hand_'+s][:3,3])+np.linalg.norm(g.local['Tool_'+s][:3,3])-.0015 for s in ('R','L')}
            slide=0.
            # A proportion change can make a crossed double grip unreachable.
            # Permit the support hand to slide on the handle, without changing
            # the weapon rotation, the blade, or either limb's length.
            if min(weights.values())>.999:
                for _ in range(60):
                    cr=world['Arm_R'][:3,3]-(wp+wr @ grips['R'])
                    cl=world['Arm_L'][:3,3]-(wp+wr @ grips['L'])
                    if np.linalg.norm(cr-cl)<radii['R']+radii['L']-.003:break
                    step=(grips['R'][2]-grips['L'][2])*.04
                    grips['L'][2]+=step;slide+=abs(step)*WEAPON_SCALE
            shift=np.zeros(3)
            blade= g.weapon_vertices @ wr.T+wp
            floor_needed=.012-float(blade[:,2].min())
            # Shared translation projects both attached palms into their reach
            # spheres; detached hands retain their independent authored motion.
            for _ in range(80):
                old=shift.copy()
                for s in ('R','L'):
                    center=world['Arm_'+s][:3,3]-(wp+wr @ grips[s])
                    delta=shift-center;distance=np.linalg.norm(delta)
                    limit=radii[s]+(1-weights[s])*2/max(weights[s],.001)
                    if distance>limit:shift=center+unit(delta)*limit
                if shift[2]<floor_needed:shift[2]=floor_needed
                if np.linalg.norm(shift-old)<1e-8:break
            shift,body_clearance,common_slide=self.fit_weapon(world,wp,wr,grips,weights,radii,shift,floor_needed)
            for s in grips:
                grips[s][2]+=common_slide/WEAPON_SCALE
                target_grips[s][i]=grips[s]
            wp+=shift
            grip_error={}
            for s in ('R','L'):
                attached=wp+wr @ grips[s]
                goal=wanted[s]*(1-weights[s])+attached*weights[s]
                end=self.limb(world,s,goal,hints[s])
                grip_error[s]=float(np.linalg.norm(end-attached)) if weights[s]>.999 else 0.
            world['Tool_R']=affine(wr,wp) @ g.correction
            world['Tool_L']=world['Hand_L'] @ g.local['Tool_L']
            g.helpers(world)
            body=g.skin(world)
            min_body=float(body[:,2].min())
            blade=g.weapon_vertices @ wr.T+wp
            min_blade=float(blade[:,2].min())
            # Keep adaptation visible in the report. It is never hidden as an
            # unreported scaling or erased root-motion track.
            metrics.append(dict(time=float(t),grip_error=grip_error,feet=feet,body_min=min_body,blade_min=min_blade,
                         grip_slide=slide,common_grip_slide=common_slide,grip_shift=float(np.linalg.norm(shift)),root_adaptation=root_adaptation,
                         contacts=weights,weapon_body_clearance=body_clearance,
                         source_root_height=float(p['pelvis'][2]*self.root_scale)))
            worlds.append(world)
        # The block head and bent elbow cubes are larger than a human's. Adapt
        # pelvis height smoothly where that volume would intersect the floor,
        # then resolve the feet against their original moving contact targets.
        required=np.array([max(0.,.012-m['body_min']) if m['body_min']<.008 else 0. for m in metrics])
        padding=round(HZ*.04);smooth=round(HZ*.025)
        envelope=np.array([max(required[max(0,i-padding):i+padding+1]) for i in range(len(required))])
        envelope=np.maximum(required,np.convolve(np.pad(envelope,(smooth,smooth),mode='edge'),np.ones(2*smooth+1)/(2*smooth+1),mode='valid'))
        self.previous={};g.helper_angles={}
        for i,(world,extra) in enumerate(zip(worlds,envelope)):
            if extra>1e-9:
                for m in world.values():m[2,3]+=extra
                metrics[i]['root_adaptation']+=float(extra)
                metrics[i]['blade_min']+=float(extra)
            p={n:v[i] for n,v in ps.items()};r={n:v[i] for n,v in rots.items()}
            metrics[i]['feet']=self.legs(world,p,r,origin)
            g.helpers(world)
            metrics[i]['body_min']=float(g.skin(world)[:,2].min())
        matrices=np.array([[w[n] for n in g.names] for w in worlds])
        local=np.empty_like(matrices)
        for j,n in enumerate(g.names):
            parent=g.parents[n]
            local[:,j]=np.linalg.inv(matrices[:,g.names.index(parent)]) @ matrices[:,j] if parent else matrices[:,j]
        entries=[dict(name=n,time=np.round(times,7).tolist(),transform=np.round(local[:,j].reshape(-1,16),8).tolist()) for j,n in enumerate(g.names)]
        path=OUT/'epicfight/assets/herobrine_companion/animmodels/animations/player/poem_unity09'/f"{clip['name'].lower()}.json"
        write(path,{'animation':entries})
        cache=WORK/'retargeted';cache.mkdir(exist_ok=True)
        np.savez_compressed(cache/(clip['name']+'.npz'),names=np.array(g.names),times=times,world=matrices,local=local,
                 grips=np.stack([target_grips['R'],target_grips['L']],axis=1),contacts=np.stack([contacts['R'],contacts['L']],axis=1))
        write(cache/(clip['name']+'_metrics.json'),metrics)
        report=dict(clip,output=path.relative_to(OUT).as_posix(),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                    keys_per_joint=len(times),export_hz=HZ,joints=len(g.names),root_scale=self.root_scale,arm_scale=self.arm_scale,
                    weapon_scale=WEAPON_SCALE,blade_axes=WEAPON_AXES.tolist(),
                    travel_blocks=(matrices[-1,0,:3,3]-matrices[0,0,:3,3]).tolist(),
                    max_grip_error=max(max(m['grip_error'].values()) for m in metrics),
                    max_sole_xy_error=max(m['feet'][s]['xy_error'] for m in metrics for s in ('R','L')),
                    minimum_body_height=min(m['body_min'] for m in metrics),minimum_blade_height=min(m['blade_min'] for m in metrics),
                    max_grip_slide=max(m['grip_slide'] for m in metrics),max_grip_shift=max(m['grip_shift'] for m in metrics),
                    max_root_adaptation=max(m['root_adaptation'] for m in metrics),
                    minimum_held_weapon_body_clearance=min((m['weapon_body_clearance'] for m in metrics if max(m['contacts'].values())>.99),default=1e6),
                    dual_grip_seconds=sum(min(m['contacts'].values())>.999 for m in metrics)/HZ,
                    weapon_release_seconds=sum(max(m['contacts'].values())<.1 for m in metrics)/HZ,
                    live_gameplay_tested=False)
        write(OUT/'reports'/f"{clip['name']}.json",report,True)
        print('RETARGETED',clip['name'],json_summary(report),flush=True)
        return report


def json_summary(r):
    return ' '.join(f'{n}={r[n]:.4f}' for n in ('max_grip_error','max_sole_xy_error','minimum_body_height','minimum_blade_height','max_grip_shift'))


def main():
    parser=argparse.ArgumentParser();parser.add_argument('clips',nargs='*');args=parser.parse_args()
    g=GameMesh();native=NativeReference();retarget=Retarget(g,native)
    catalog=load(WORK/'sample_catalog.json')
    selected=[c for c in catalog if not args.clips or c['name'] in args.clips]
    reports=[retarget.convert(c) for c in selected]
    write(OUT/'reports/conversion_catalog.json',reports,True)
    write(OUT/'reports/retarget_profile.json',dict(root_scale=retarget.root_scale,arm_scale=retarget.arm_scale,
          weapon_scale=WEAPON_SCALE,weapon_axes=WEAPON_AXES.tolist(),source_to_game=C.tolist(),
          source_fps=60,export_hz=HZ,weapon=g.weapon_report),True)


if __name__=='__main__':
    main()
