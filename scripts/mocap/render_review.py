"""Render review samples using the saved bake, independently of MCP latency."""
import bpy,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
OUT=ROOT/'output/Herobrine_Scythe_MediaPipe'
scene=bpy.context.scene
folder=WORK/'render_review';folder.mkdir(exist_ok=True)
scene.render.resolution_x=960;scene.render.resolution_y=540;scene.render.resolution_percentage=100
scene.eevee.taa_render_samples=12
frames=[1,16,45,81,120,149,181,193,224,267,271,297,312,327,341,356,371,401,445,519]
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
if args:frames=[int(f) for f in args]
for f in frames:
    scene.frame_set(f);scene.render.filepath=str(folder/f'{f:04d}.png')
    bpy.ops.render.render(write_still=True)
    print('RENDERED_FRAME',f,flush=True)
