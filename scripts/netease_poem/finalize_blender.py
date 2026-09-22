import bpy,json
from pathlib import Path
root=Path('E:/java/herobrine_companion')
work=root/'build/netease_poem_longcombo_v1'
scene=bpy.data.scenes['NetEase Poem - exported JSON']
data=json.loads((work/'camera.json').read_text(encoding='utf-8'))
for frame,(location,scale) in enumerate(zip(data['positions'],data['scales']),1):
    scene.camera.location=location
    scene.camera.keyframe_insert(data_path='location',frame=frame)
    scene.camera.data.ortho_scale=scale
    scene.camera.data.keyframe_insert(data_path='ortho_scale',frame=frame)
scene.display.render_aa='FXAA'
scene.render.image_settings.compression=15
(work/'frames').mkdir(exist_ok=True)
scene.render.filepath=str(work/'frames/frame_')
scene.frame_set(91)
bpy.data.libraries.write(str(root/'output/Herobrine_Poem_NetEase_LongCombo_01/NetEase_终末之诗_长连招验证.blend'),{scene},fake_user=True,compress=True)
print('NETEASE_CAMERA_SAVED',scene.frame_end)
