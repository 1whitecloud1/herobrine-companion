"""Blender background: half-speed previews from the actual independent renderer export."""
import bpy
import json
import sys
from pathlib import Path

root=Path('E:/java/herobrine_companion')
out=root/'output/Herobrine_Scythe_UnityModes_11_Standalone'
report=json.loads((out/'reports/blender_preview.json').read_text('utf-8'))
single='--single' in sys.argv
for info in report['scenes']:
    scene=bpy.data.scenes[info['scene']]
    for window in bpy.context.window_manager.windows:window.scene=scene
    scene.render.engine='BLENDER_WORKBENCH'
    scene.render.resolution_x=640;scene.render.resolution_y=640;scene.render.resolution_percentage=100
    scene.display.shading.light='STUDIO';scene.display.shading.color_type='TEXTURE'
    scene.display.shading.show_shadows=True;scene.display.shading.show_cavity=True
    scene.display.shading.cavity_type='WORLD';scene.display.shading.background_type='WORLD'
    scene.world.color=(.13,.17,.22)
    scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGB'
    scene.render.image_settings.compression=10
    for obj in scene.objects:
        if obj.type=='MESH':
            for mat in obj.data.materials:
                if mat and mat.use_nodes:
                    tex=next((n for n in mat.node_tree.nodes if n.type=='TEX_IMAGE'),None)
                    if tex:mat.node_tree.nodes.active=tex
    directory=root/'build/poem_standalone/frames'/str(info['mode']);directory.mkdir(parents=True,exist_ok=True)
    scene.render.filepath=str(directory/'f_')
    if single:
        scene.frame_set(info['pose_frame']);scene.render.filepath=str(directory/'sample.png')
        bpy.ops.render.render(write_still=True,scene=scene.name)
        break
    # An explicit scene/frame avoids Blender's animation operator inheriting another scene's range.
    for frame in range(1,info['frames']+1):
        scene.frame_set(frame)
        scene.render.filepath=str(directory/f'f_{frame:04d}.png')
        bpy.ops.render.render(write_still=True,scene=scene.name)
    print('UNITY11_VIDEO_FRAMES',info['mode'],info['frames'],flush=True)
