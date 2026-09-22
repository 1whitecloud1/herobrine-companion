"""Freeze all delivered animation assets before replacing only the dash slot."""
import hashlib
import json
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/scythe_aerial_dash_08'
path=WORK/'before_aerial_dash_sha256.json'
if path.exists():
    print('AERIAL_BASELINE_ALREADY_EXISTS',path)
else:
    manifest={}
    with zipfile.ZipFile(WORK/'before_aerial_dash.zip','x',zipfile.ZIP_DEFLATED) as archive:
        for key,repo in [('neoforge',ROOT),('forge',ROOT.parent/'herobrine companion')]:
            paths=set()
            assets=repo/'src/main/resources/assets/herobrine_companion'
            paths.update(p for p in (assets/'animmodels/animations').rglob('*') if p.is_file())
            paths.update(p for p in (assets/'epicfight').rglob('*') if p.is_file())
            paths.update(p for p in (repo/'src/main/java').rglob('MediaPipeScythe*.java'))
            manifest[key]={}
            for p in sorted(paths):
                rel=p.relative_to(repo).as_posix()
                manifest[key][rel]=hashlib.sha256(p.read_bytes()).hexdigest()
                archive.write(p,key+'/'+rel)
    path.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print('AERIAL_BASELINE_SAVED',{k:len(v) for k,v in manifest.items()})
