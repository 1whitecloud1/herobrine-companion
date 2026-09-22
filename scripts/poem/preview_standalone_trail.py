"""Geometry QA from the Java sampler export; this is not an in-game screenshot."""
from pathlib import Path
import json
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/poem_trail_step'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def main():
    frames = read(OUT / 'trail_preview.json')
    meshes = read(ROOT / 'build/poem_standalone/vanilla_mesh_preview.json')[0]['meshes']
    weapon = read(ROOT / 'src/main/resources/assets/herobrine_companion/poem_standalone/weapon.json')
    rig = read(ROOT / 'src/main/resources/assets/herobrine_companion/poem_standalone/rig.json')
    vertices, faces = np.array(weapon['vertices']), np.array(weapon['faces'])
    fig = plt.figure(figsize=(16, 15), facecolor='#111a25')
    labels = ['Normal', 'Realm breaker', 'Thunder', 'Void shatter']
    for index, frame in enumerate(frames):
        ax = fig.add_subplot(4, 4, index + 1, projection='3d', facecolor='#111a25')
        for mesh in meshes:
            sequence = mesh['poses'][frame['mode']]
            candidates = [(i, abs(key['time'] - frame['time'])) for i, key in enumerate(sequence['schedule']) if key['step'] == frame['step']]
            sample = min(candidates, key=lambda row: row[1])[0]
            p = np.array(sequence['frames'][sample])
            dcc = np.column_stack((-p[:, 0], -p[:, 2], 1.5 - p[:, 1]))
            ax.add_collection3d(Poly3DCollection(dcc.reshape(-1, 4, 3), facecolor='#73818f', edgecolor='#354351', linewidth=.15, alpha=.9))
        transform = np.array(frame['tool']).reshape(4, 4, order='F') @ np.array(rig['weapon_to_tool'])
        placed = vertices @ transform[:3, :3].T + transform[:3, 3]
        ax.add_collection3d(Poly3DCollection(placed[faces], facecolor='#d8dee5', linewidth=0, alpha=.9))
        for strip in frame['ribbons']:
            edges = np.array(strip)
            quads, colors = [], []
            for a, b in zip(edges[:-1], edges[1:]):
                quads.append([a[:3], a[3:6], b[3:6], b[:3]])
                colors.append((.45, .85, 1, .7 * float((a[6] + b[6]) / 2)))
            ax.add_collection3d(Poly3DCollection(quads, facecolors=colors, linewidth=0))
        ax.set(xlim=(-2.6, 2.6), ylim=(-2.6, 2.6), zlim=(-.25, 3), box_aspect=(1, 1, .67))
        ax.view_init(elev=18, azim=-65)
        ax.set_axis_off()
        ax.set_title(f"{labels[frame['mode']]}  /  Cut {frame['step'] + 1}", color='#dbe7f0', fontsize=12, pad=-3)
    fig.suptitle('Standalone Poem: blade ribbon geometry, all 16 cuts', color='#ebf8ff', fontsize=21, y=.977)
    fig.text(.5, .947, 'Java sampler output. White = current blade; cyan = its recent sweep. Geometry preview, not gameplay.', ha='center', color='#9fb4c6', fontsize=10)
    fig.subplots_adjust(left=.015, right=.985, top=.927, bottom=.018, wspace=-.07, hspace=-.01)
    fig.savefig(OUT / 'trail_geometry_preview.png', dpi=130, facecolor=fig.get_facecolor())
    plt.close(fig)
    print(OUT / 'trail_geometry_preview.png')


if __name__ == '__main__':
    main()
