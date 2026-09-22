"""Join the package's authored start/loop/end tracks before retargeting.

FBX clips reset object translation independently. Align that object space at
each shared boundary without changing bone or weapon rotation, and retain one
copy of each boundary key. This is a single authored loop, not an endless skill.
"""
import json
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_unity_pack_09'
COMPOSITES = {
    'Speed_Attack_All': ['Speed_Attack_Start', 'Speed_Attack_Loop', 'Speed_Attack_End'],
    'Speed_Attack_Air_All': ['Speed_Attack_Air_Start', 'Speed_Attack_Air_Loop', 'Speed_Attack_Air_End'],
    'Attack_Air_to_Floor_01_All': ['Attack_Air_to_Floor_01_Start', 'Attack_Air_to_Floor_01_Loop', 'Attack_Air_to_Floor_01_End'],
}


def main():
    catalog_path = WORK / 'sample_catalog.json'
    catalog = json.loads(catalog_path.read_text('utf-8'))
    originals = {row['name']: row for row in catalog}
    reports = []
    for name, parts in COMPOSITES.items():
        worlds, roots, joins = [], [], []
        for part in parts:
            data = np.load(WORK / 'native_samples' / (part + '.npz'))
            world, root = data['world'].astype(np.float64), data['root'].astype(np.float64)
            if worlds:
                assert np.array_equal(names, data['names'])
                alignment = roots[-1][-1] @ np.linalg.inv(root[0])
                world, root = alignment @ world, alignment @ root
                error = float(np.max(np.abs(worlds[-1][-1] - world[0])))
                assert error < 1e-5, (name, part, error)
                joins.append(dict(next_source=part, frame=sum(len(x) for x in worlds) / 2 - .5,
                                  alignment=alignment.tolist(), maximum_boundary_matrix_error=error))
                world, root = world[1:], root[1:]
            else:
                names = data['names']
            worlds.append(world); roots.append(root)
        world, root = np.concatenate(worlds), np.concatenate(roots)
        times = np.arange(len(world)) / 120
        first = originals[parts[0]]['source_first_frame']
        last = originals[parts[-1]]['source_last_frame']
        assert abs(times[-1] - (last - first) / 60) < 1e-8
        np.savez_compressed(WORK / 'native_samples' / (name + '.npz'), names=names,
                            times=times, world=world, root=root)
        pelvis = list(names).index('pelvis')
        row = dict(name=name, category='AuthoredComposite', source=parts, fps=60,
                   source_first_frame=first, source_last_frame=last, sample_hz=120,
                   duration=float(times[-1]), source_travel=(world[-1, pelvis, :3, 3] - world[0, pelvis, :3, 3]).tolist(),
                   victim=False, full_combo=True, composition='One native start, loop and end; object-space alignment only')
        catalog = [r for r in catalog if r['name'] != name] + [row]
        reports.append(dict(name=name, parts=parts, joins=joins, duration=float(times[-1])))
        print('COMPOSED', name, 'frames', last - first, 'joins', [j['maximum_boundary_matrix_error'] for j in joins], flush=True)
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (WORK / 'native_extra_composition.json').write_text(json.dumps(reports, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
