"""Render native FBX and decoded Epic Fight animation side by side in Blender."""
import argparse
import json
import math
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix,Vector

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/scythe_unity_pack_09'
OUT=ROOT/'output/Herobrine_Scythe_UnityPack_09'
ASSETS=ROOT/'src/main/resources/assets/herobrine_companion'
C=np.diag([-1.,-1.,1.])
load=lambda p:json.loads(p.read_text(encoding='utf-8'))


def material(name,color,texture=None):
    m=bpy.data.materials.new(name);m.use_nodes=True;m.diffuse_color=(*color,1)
    bsdf=m.node_tree.nodes.get('Principled BSDF');bsdf.inputs['Base Color'].default_value=(*color,1)
    bsdf.inputs['Roughness'].default_value=.65
    if texture:
        tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(texture))
        tex.interpolation='Closest';m.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
    return m


def mesh_object(name,vertices,faces,mat,uv=None):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.materials.append(mat)
    if uv:
        layer=mesh.uv_layers.new()
        for p,co in zip(layer.data,uv):p.uv=co
    ob=bpy.data.objects.new(name,mesh);bpy.context.scene.collection.objects.link(ob)
    return ob


def main():
    p=argparse.ArgumentParser();p.add_argument('clips',nargs='+');p.add_argument('--video',action='store_true')
    p.add_argument('--curated',action='store_true')
    p.add_argument('--render-fps',type=int,default=30)
    p.add_argument('--frames',nargs='*',type=float);p.add_argument('--side',action='store_true');p.add_argument('--fast',action='store_true')
    args=p.parse_args(sys.argv[sys.argv.index('--')+1:])
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene=bpy.context.scene;scene.render.engine='BLENDER_EEVEE'
    scene.render.resolution_x=1600;scene.render.resolution_y=800;scene.render.resolution_percentage=100
    scene.eevee.taa_render_samples=8 if args.fast else 24
    scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGB';scene.render.use_persistent_data=True
    scene.world=bpy.data.worlds.new('World');scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.10,.13,.18,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value=.5
    scene.view_settings.view_transform='Standard'
    game=load(ROOT/'build/epicfight-mediapipe/biped.json')
    rest,parents={},{ }
    def walk(nodes,parent=None):
        for node in nodes:
            n=node['name'];parents[n]=parent
            rest[n]=(rest[parent] if parent else np.eye(4)) @ np.array(node['transform']).reshape(4,4)
            walk(node.get('children',[]),n)
    walk(game['armature']['hierarchy'])
    inverse={n:np.linalg.inv(m) for n,m in rest.items()}
    v=game['vertices'];positions=np.array(v['positions']['array']).reshape(-1,3)
    groups={n:[] for n in rest};cursor=0
    for i,count in enumerate(v['vcounts']['array']):
        for _ in range(count):
            j,wi=v['vindices']['array'][cursor:cursor+2];cursor+=2
            groups[game['armature']['joints'][j]].append((i,v['weights']['array'][wi]))
    groups={n:(np.array([i for i,w in rows]),np.array([w for i,w in rows])) for n,rows in groups.items() if rows}
    faces,uvs=[],[];uv=np.array(v['uvs']['array']).reshape(-1,2)
    for name,part in v['parts'].items():
        if name.endswith(('Sleeve','Pants')) or name in ('hat','jacket'):continue
        for tri in np.array(part['array']).reshape(-1,3,3):
            faces.append(tuple(int(x) for x in tri[:,0]))
            uvs.extend([(float(uv[k,0]),1-float(uv[k,1])) for k in tri[:,1]])
    body=mesh_object('Herobrine | decoded Epic Fight JSON',positions,faces,material('Herobrine',(.1,.65,.65),ASSETS/'textures/entity/herobrine.png'),uvs)
    weapon=load(ROOT/'build/epicfight-aerial-dash/reference_samples.json')
    wv=np.array(weapon['source_weapon_vertices'])
    scythe=mesh_object('Poem of the End | calibrated game item',wv,weapon['source_weapon_faces'],material('Poem of the End',(.2,.6,.7),ASSETS/'textures/item/poem_of_the_end_geo.png'),weapon['source_weapon_uv'])
    wr=load(OUT/'reports/retarget_profile.json')['weapon']
    correction=np.array(wr['model_to_socket']) @ np.linalg.inv(wr['item_render_transform'])
    ci=np.linalg.inv(correction)
    native=load(WORK/'native_reference.json');nm=native['meshes']['NCG_Mesh']
    nv=np.array(nm['vertices']);ng={}
    for i,weights in enumerate(nm['weights']):
        for n,w in weights.items():ng.setdefault(n,[]).append((i,w))
    ng={n:(np.array([i for i,w in rows]),np.array([w for i,w in rows])) for n,rows in ng.items()}
    ni={n:np.linalg.inv(np.array(m)) for n,m in native['rest'].items()}
    source_body=mesh_object('Original FBX | native skeleton',nv,nm['faces'],material('Native character',(.53,.59,.66)))
    nwm=native['meshes']['Scythe'];nwv=np.array(nwm['vertices'])
    source_scythe=mesh_object('Original scythe',nwv,nwm['faces'],material('Native scythe',(.15,.44,.48)))
    bpy.ops.mesh.primitive_plane_add(size=500)
    ground=material('Half block grid',(.12,.16,.21));bpy.context.object.data.materials.append(ground)
    geo=ground.node_tree.nodes.new('ShaderNodeNewGeometry');checker=ground.node_tree.nodes.new('ShaderNodeTexChecker')
    checker.inputs['Color1'].default_value=(.105,.137,.18,1);checker.inputs['Color2'].default_value=(.14,.177,.23,1);checker.inputs['Scale'].default_value=2
    ground.node_tree.links.new(geo.outputs['Position'],checker.inputs['Vector']);ground.node_tree.links.new(checker.outputs['Color'],ground.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
    lamps=[]
    for pos,power,size in [((0,3,9),1900,9),((-5,-3,6),1500,7)]:
        light=bpy.data.lights.new('Softbox','AREA');light.energy=power;light.size=size
        ob=bpy.data.objects.new('Softbox',light);scene.collection.objects.link(ob);ob.location=pos;lamps.append((ob,Vector(pos)))
    camera=bpy.data.objects.new('Comparison camera',bpy.data.cameras.new('Comparison camera'));scene.collection.objects.link(camera)
    camera.data.type='ORTHO';camera.data.ortho_scale=10.0;scene.camera=camera
    direction=Vector((0.,10.,4.5))
    # Native and game characters share a follow camera; the moving grid shows
    # the retained root trajectory. No action is mislabeled as a hand-authored cut.
    for name in args.clips:
        source_name=name;source_start=0.;source_lift=0.
        final=ROOT/'output/Herobrine_Scythe_UnityModes_10_EpicFight'
        if args.curated:
            key,motion=name.split('/')
            if key=='extra':
                timeline=load(final/'resources/assets/herobrine_companion/epicfight/poem_unity09_extras.json')
                timing=next(t for m in timeline['moves'] for t in (m['ground'],m['air']) if t['name']==motion)
            else:
                timeline=load(final/'resources/assets/herobrine_companion/epicfight/poem_unity09_timing.json')
                mode=next(m for m in timeline['modes'] if m['key']==key)
                if motion=='full':source_name=mode['ground_source']
                else:timing=next(t for t in mode['segments']+mode['specials'] if t['name']==motion)
            if motion!='full':
                source_name=timing['source_clip'];source_start=timing['source_first_frame']/60
                source_lift=timing['standalone_initial_height_removed']/load(OUT/'reports/retarget_profile.json')['root_scale']
            path=final/'resources/assets/herobrine_companion/animmodels/animations/player/poem_unity09'/(name+'.json')
        else:
            path=OUT/'epicfight/assets/herobrine_companion/animmodels/animations/player/poem_unity09'/(name.lower()+'.json')
        rows=load(path)['animation'];times=np.array(rows[0]['time'])
        local={x['name']:np.array(x['transform']).reshape(-1,4,4) for x in rows}
        world={}
        for n in rest:world[n]=world[parents[n]] @ local[n] if parents[n] else local[n]
        data=np.load(WORK/'native_samples'/(source_name+'.npz'));n_names=list(data['names']);nworld=data['world']
        hz=round(1/(times[1]-times[0]));interval=hz//args.render_fps
        frames=list(range(0,len(times),interval)) if args.video else [min(len(times)-1,round(f*hz/60)) for f in (args.frames or np.linspace(0,times[-1]*60,6))]
        sequence='sequence' if args.render_fps==30 else 'sequence_'+str(args.render_fps)
        folder=(final if args.curated else OUT)/'preview'/(sequence if args.video else 'stills')/name
        folder.mkdir(parents=True,exist_ok=True)
        for k,index in enumerate(frames):
            output=folder/f'{k if args.video else index:04d}.png'
            if args.video and args.render_fps==60 and k%2==0:
                prior=final/'preview/sequence'/name/f'{k//2:04d}.png'
                if prior.exists() and not output.exists():
                    import shutil
                    shutil.copyfile(prior,output)
            if output.exists():continue
            w={n:m[index] for n,m in world.items()};nw=dict(zip(n_names,nworld[min(len(nworld)-1,round((source_start+times[index])*120))]))
            pp=np.zeros_like(positions)
            for n,(ix,weights) in groups.items():pp[ix]+=(np.c_[positions[ix],np.ones(len(ix))] @ (w[n] @ inverse[n]).T)[:,:3]*weights[:,None]
            target_shift=np.array([-2.35,0.,0.]);pp+=target_shift
            body.data.vertices.foreach_set('co',pp.ravel());body.data.update()
            socket=w['Tool_R'] @ ci;socket[:3,3]+=target_shift;scythe.matrix_world=Matrix(socket)
            native_points=np.zeros_like(nv)
            for n,(ix,weights) in ng.items():native_points[ix]+=(np.c_[nv[ix],np.ones(len(ix))] @ (nw[n] @ ni[n]).T)[:,:3]*weights[:,None]
            native_shift=np.r_[w['Root'][:2,3]-(C @ nw['pelvis'][:3,3])[:2],-source_lift]+np.array([2.35,0.,0.])
            native_points=native_points @ C.T+native_shift
            source_body.data.vertices.foreach_set('co',native_points.ravel());source_body.data.update()
            ww=(np.c_[nwv,np.ones(len(nwv))] @ (nw['Scythe_Weapon_R'] @ ni['Scythe_Weapon_R']).T)[:,:3] @ C.T+native_shift
            source_scythe.data.vertices.foreach_set('co',ww.ravel());source_scythe.data.update()
            target=Vector((w['Root'][0,3],w['Root'][1,3],1.15+max(0.,w['Root'][2,3]-.764)*.7))
            camera.data.ortho_scale=11.
            camera.location=target+direction;camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
            for lamp,offset in lamps:
                lamp.location=target+offset;lamp.rotation_euler=(target-lamp.location).to_track_quat('-Z','Y').to_euler()
            scene.render.filepath=str(output)
            bpy.ops.render.render(write_still=True)
        print('UNITY_PREVIEW_COMPLETE',name,len(frames),str(folder),flush=True)


if __name__=='__main__':main()
