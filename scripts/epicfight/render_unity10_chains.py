"""Show the shipped poses switching at recovery, with a 0.10 s pose blend.

This is an offline Blender preview, not a recording of input/network latency.
"""
import bisect
import json
import math
from pathlib import Path

import bpy
from mathutils import Matrix

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/Herobrine_Scythe_UnityModes_10_EpicFight'
ASSETS = OUT / 'resources/assets/herobrine_companion'
load = lambda p: json.loads(p.read_text('utf-8'))


def interpolate(a, b, factor):
    pa, qa, sa = a.decompose()
    pb, qb, sb = b.decompose()
    return Matrix.LocRotScale(pa.lerp(pb, factor), qa.slerp(qb, factor), sa.lerp(sb, factor))


class Motion:
    def __init__(self, key, name):
        data = load(ASSETS / f'animmodels/animations/player/poem_unity09/{key}/{name}.json')['animation']
        self.times = data[0]['time']
        self.joints = {r['name']: [Matrix([m[i:i+4] for i in range(0, 16, 4)]) for m in r['transform']] for r in data}

    def sample(self, time, anchor=(0., 0.)):
        index = max(0, min(len(self.times) - 2, bisect.bisect_right(self.times, time) - 1))
        fraction = max(0., min(1., (time - self.times[index]) / (self.times[index+1] - self.times[index])))
        pose = {n: interpolate(values[index], values[index+1], fraction) for n, values in self.joints.items()}
        pose['Root'][0][3] += anchor[0]
        pose['Root'][1][3] += anchor[1]
        return pose


def main():
    scene = bpy.context.scene
    rig = next(o for o in bpy.data.objects if o.type == 'ARMATURE')
    rig.animation_data.action = None
    inverse = {}
    for b in rig.data.bones:
        local = b.parent.matrix_local.inverted() @ b.matrix_local if b.parent else b.matrix_local
        inverse[b.name] = local.inverted()
    scene.render.resolution_x = 1280
    scene.render.resolution_y = 800
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.eevee.taa_render_samples = 8
    scene.render.use_persistent_data = True
    timeline = load(ASSETS / 'epicfight/poem_unity09_timing.json')
    extras = load(ASSETS / 'epicfight/poem_unity09_extras.json')
    heavy = next(m['ground'] for m in extras['moves'] if m['key'] == 'heavy')
    reports = []
    for mode in timeline['modes']:
        next_timing = mode['segments'][0]
        target = Motion(mode['key'], 'combo_01')
        for kind in ('heavy_to_light', 'combo_restart'):
            timing = heavy if kind == 'heavy_to_light' else mode['segments'][-1]
            source = Motion('extra' if kind == 'heavy_to_light' else mode['key'], timing['name'])
            # Round the unlocked window to a game tick. Never cut a hit phase.
            switch = math.ceil(timing['recovery'] * 20) / 20
            blend = .10
            duration = switch + blend + next_timing['duration']
            outgoing = source.sample(switch)
            anchor = (outgoing['Root'][0][3], outgoing['Root'][1][3])
            incoming = target.sample(0., anchor)
            folder = OUT / 'preview/chains_60' / mode['key'] / kind
            folder.mkdir(parents=True, exist_ok=True)
            frames = []
            for frame in range(math.ceil(duration * 60)):
                time = frame / 60
                if time <= switch:
                    pose = source.sample(time)
                    phase, source_time = 'source', time
                elif time < switch + blend:
                    factor = (time - switch) / blend
                    pose = {n: interpolate(outgoing[n], incoming[n], factor) for n in outgoing}
                    phase, source_time = 'blend', 0.
                else:
                    source_time = time - switch - blend
                    pose = target.sample(source_time, anchor)
                    phase = 'next'
                for name, matrix in pose.items():
                    rig.pose.bones[name].matrix_basis = inverse[name] @ matrix
                bpy.context.view_layer.update()
                path = folder / f'{frame:04d}.png'
                frames.append(dict(frame=frame, phase=phase, source_time=source_time))
                if path.exists():
                    continue
                scene.render.filepath = str(path)
                bpy.ops.render.render(write_still=True)
            reports.append(dict(mode=mode['key'], label=mode['label'], kind=kind,
                                source_clip=timing['source_clip'], next_clip=next_timing['source_clip'],
                                source_first_frame=timing['source_first_frame'], recovery_seconds=timing['recovery'],
                                last_contact_end=timing['contacts'][-1]['end'],
                                switch_seconds=switch, blend_seconds=blend, duration=duration, frames=frames))
            print('RENDERED_CHAIN', mode['key'], kind, len(frames), flush=True)
    report = dict(preview='Blender pose blend; not live gameplay', render_fps=60, chains=reports)
    (OUT / 'reports/chain_preview_timeline.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print('UNITY10_CHAIN_PREVIEWS_RENDERED', flush=True)


if __name__ == '__main__':
    main()
