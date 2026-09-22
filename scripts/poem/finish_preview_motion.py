"""Blender MCP: exact keyed socket transforms and camera framing for the returning throw."""
import bpy,json
import numpy as np
from pathlib import Path
from mathutils import Matrix,Vector

root=Path('E:/java/herobrine_companion');out=root/'output/Herobrine_Scythe_UnityModes_11_Standalone'
data=json.loads((root/'build/poem_standalone/vanilla_mesh_preview.json').read_text('utf-8'))
rig=json.loads((root/'src/main/resources/assets/herobrine_companion/poem_standalone/rig.json').read_text('utf-8'))
weapon_data=json.loads((root/'src/main/resources/assets/herobrine_companion/poem_standalone/weapon.json').read_text('utf-8'))
report=json.loads((out/'reports/blender_preview.json').read_text('utf-8'))
vertices=np.array(weapon_data['vertices']);correction=np.array(rig['weapon_to_tool'])
for info in report['scenes']:
    scene=bpy.data.scenes[info['scene']]
    for window in bpy.context.window_manager.windows:window.scene=scene
    weapon=next(o for o in scene.objects if o.name.endswith('calibrated scythe'))
    weapon.animation_data_clear();weapon.rotation_mode='QUATERNION'
    camera=scene.camera;camera.animation_data_clear();camera.data.animation_data_clear()
    right=np.array(camera.rotation_euler.to_matrix()@Vector((1,0,0)))
    up=np.array(camera.rotation_euler.to_matrix()@Vector((0,1,0)))
    forward=np.array(camera.rotation_euler.to_matrix()@Vector((0,0,-1)))
    schedule=data[0]['meshes'][0]['poses'][info['mode']]['schedule'];bounds=[]
    for index,key in enumerate(schedule):
        transform=np.array(key['tool']).reshape(4,4).T@correction
        loc,rotation,scale=Matrix(transform.tolist()).decompose()
        weapon.location=loc;weapon.rotation_quaternion=rotation;weapon.scale=scale
        for name in ['location','rotation_quaternion','scale']:weapon.keyframe_insert(data_path=name,frame=index+1)
        body=np.concatenate([np.array(part['poses'][info['mode']]['frames'][index]) for part in data[0]['meshes']])
        body=np.c_[-body[:,0],-body[:,2],1.5-body[:,1]]
        points=np.r_[body,vertices@transform[:3,:3].T+transform[:3,3]]
        x=points@right;y=points@up
        bounds.append((x.min(),x.max(),y.min(),y.max()))
    bounds=np.array(bounds)
    centers=np.c_[(bounds[:,0]+bounds[:,1])/2,(bounds[:,2]+bounds[:,3])/2]
    centers=np.array([centers[max(0,i-3):min(len(centers),i+4)].mean(axis=0) for i in range(len(centers))])
    extents=2*np.max(np.abs(bounds-np.c_[centers[:,0],centers[:,0],centers[:,1],centers[:,1]]),axis=1)*1.2
    extents=np.array([max(5.6,extents[max(0,i-3):min(len(extents),i+4)].max()) for i in range(len(extents))])
    for i,(center,size) in enumerate(zip(centers,extents)):
        camera.location=right*center[0]+up*center[1]-forward*15
        camera.data.ortho_scale=float(size)
        camera.keyframe_insert(data_path='location',frame=i+1);camera.data.keyframe_insert(data_path='ortho_scale',frame=i+1)
    for owner in [weapon,camera,camera.data]:
        if owner.animation_data and owner.animation_data.action:
            for layer in owner.animation_data.action.layers:
                for strip in layer.strips:
                    for bag in strip.channelbags:
                        for curve in bag.fcurves:
                            for point in curve.keyframe_points:point.interpolation='LINEAR'
    scene.frame_set(info['pose_frame']);scene.render.filepath=str(out/'reports'/('mode_'+str(info['mode'])+'_pose.png'))
    info['camera_ortho_range']=[float(extents.min()),float(extents.max())]
    print('UNITY11_CAMERA_FIT',info['label'],info['camera_ortho_range'])
target=out/'Unity11_独立玩家动画_顶点验证.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(target),compress=True)
report['camera_fit']='Full animated player and scythe bounds, including the thunder returning throw'
(out/'reports/blender_preview.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
