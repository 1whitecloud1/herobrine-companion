"""Audit unchanged Turn07/V6 assets, then package both sprint-attack builds."""
import copy
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/scythe_aerial_dash_08'
SOURCE=ROOT/'output/Herobrine_Scythe_AerialDash_08'
OUT=ROOT/'output/Herobrine_Scythe_AerialDash_08_EpicFight'
RES=Path('src/main/resources')
BASE=Path('assets/herobrine_companion')
TIMING=BASE/'epicfight/poem_mediapipe_timing.json'
DASH=BASE/'animmodels/animations/player/poem_mediapipe/dash.json'
load=lambda p:json.loads(p.read_text(encoding='utf-8'))
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'reports').mkdir(exist_ok=True)
before=load(WORK/'before_aerial_dash_sha256.json')
geometry=load(ROOT/'build/epicfight-aerial-dash/validation_report.json')
conversion=load(ROOT/'build/epicfight-aerial-dash/conversion_report.json')
blend=SOURCE/'Herobrine_腾空旋镰_AerialDash08.blend'
assert sha(blend)==conversion['blend_sha256']
assert sha(ROOT/RES/DASH)==conversion['clip_sha256']
assert geometry['maximum_hook_component_along_body_axis']<.15
assert geometry['minimum_outward_hook_radius_gain_blocks']>.25
assert geometry['max_export_floor_correction_blocks']==0.
assert max(geometry['grip_error_blocks'].values())<.012
entries=[]
with zipfile.ZipFile(WORK/'before_aerial_dash.zip') as snapshot:
    for key,repo,version,loader,package,major,filename in [
        ('neoforge',ROOT,'1.21.1','NeoForge','com/whitecloud233/herobrine_companion',65,
         'Herobrine Companion-0.38-1.21.1-neoforge.jar'),
        ('forge',ROOT.parent/'herobrine companion','1.20.1','Forge','com/whitecloud233/modid/herobrine_companion',61,
         'Herobrine Companion-1.20.1-forge-0.38.jar'),
    ]:
        log=(WORK/f'{key}_build.log').read_text(encoding='utf-8',errors='replace')
        assert 'BUILD SUCCESSFUL' in log and 'AERIAL_DASH_RUNTIME_OK' in log
        timeline=load(repo/RES/TIMING)
        old_timeline=json.loads(snapshot.read(key+'/'+(RES/TIMING).as_posix()))
        restored=copy.deepcopy(timeline);restored.pop('dash_edit',None)
        restored['specials'][0]=old_timeline['specials'][0]
        assert restored==old_timeline,('Ordinary/air timing changed',key)
        assert timeline['dash_edit']=='mediapipe_aerial_dash_08'
        preserved=[]
        for rel,digest in before[key].items():
            path=repo/rel
            if rel in ((RES/DASH).as_posix(),(RES/TIMING).as_posix()) or rel.endswith('/MediaPipeScytheAnimations.java'):
                continue
            assert sha(path)==digest,('Unexpected change',key,rel)
            preserved.append(rel)
        verification=repo/'build/epicfight-mediapipe'
        runtime=load(verification/'aerial_dash_runtime_validation.json')
        registry=load(verification/'registry_validation.json')
        assert runtime['landing_height_blocks']==0 and runtime['entity_rise_blocks']>1
        assert registry['player_modes']=={'0':'MediaPipe','1':'V6','2':'MediaPipe','3':'V6'}
        assert len(load(verification/'engine_validation.json'))==24
        assert len(load(verification/'movement_validation.json'))==20
        v6=load(verification/'v6_baseline_sha256.json')
        jar=repo/'build/libs'/filename
        with zipfile.ZipFile(jar) as packed:
            assert packed.testzip() is None
            for rel in before[key]:
                if rel.startswith('src/main/resources/'):
                    resource=Path(rel).relative_to(RES).as_posix()
                    assert packed.read(resource)==(repo/rel).read_bytes(),('Stale JAR resource',key,rel)
            for rel,digest in v6.items():
                path=repo/rel if rel.replace('\\','/').startswith('src/') else repo/RES/rel
                assert sha(path)==digest
                assert hashlib.sha256(packed.read(path.relative_to(repo/RES).as_posix())).hexdigest()==digest
            for name in ('PoemScythePlayerAnimations','MediaPipeScytheAnimations','MediaPipeScytheAttackAnimation','MediaPipeScytheDashAnimation'):
                path=package+'/compat/epicfight/'+name+'.class'
                body=packed.read(path)
                assert body[:4]==b'\xca\xfe\xba\xbe' and int.from_bytes(body[6:8],'big')==major
                if loader=='NeoForge':
                    assert body==(repo/'build/classes/java/main'/path).read_bytes()
                elif name=='MediaPipeScytheDashAnimation':
                    # Forge remaps Minecraft symbols when reobfuscating the
                    # JAR. Check our own stable methods in the shipped class.
                    assert all(token in body for token in (b'jumpCoordinates',b'getCoord',b'NO_GRAVITY_TIME',b'MOVE_VERTICAL'))
        target=OUT/f'Herobrine_Companion-0.38-{version}-{loader}-AerialDash08.jar'
        shutil.copy2(jar,target)
        dest=OUT/'reports'/version;dest.mkdir(exist_ok=True)
        for n in ('aerial_dash_runtime_validation.json','engine_validation.json','movement_validation.json','registry_validation.json','v6_baseline_sha256.json'):
            shutil.copy2(verification/n,dest/n)
        for n in ('engine_validation.json','reach_validation.json'):
            shutil.copy2(repo/'build/epicfight-v6'/n,dest/('v6_'+n))
        entries.append(dict(version=version,loader=loader,jar=target.name,sha256=sha(target),bytes=target.stat().st_size,
                            preserved_snapshot_files=len(preserved),preserved_v6_resources=len(v6),
                            ordinary_and_air_timing_unchanged=True,live_gameplay_tested=False))
shutil.copy2(blend,OUT/blend.name)
for path in (SOURCE/'preview').glob('AerialDash08_*'):
    if path.suffix in ('.mp4','.jpg'):shutil.copy2(path,OUT/path.name)
for path in (SOURCE/'capture').glob('*.json'):shutil.copy2(path,OUT/'reports'/path.name)
for n in ('conversion_report.json','validation_report.json'):
    shutil.copy2(ROOT/'build/epicfight-aerial-dash'/n,OUT/'reports'/n)
shutil.copy2(WORK/'before_aerial_dash_sha256.json',OUT/'reports/preservation_baseline.json')
shutil.copy2(ROOT/'docs/epicfight-poem-aerial-dash08.md',OUT/'README_中文.md')
(OUT/'reports/jar_validation.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
manifest={p.relative_to(OUT).as_posix():dict(bytes=p.stat().st_size,sha256=sha(p))
          for p in OUT.rglob('*') if p.is_file() and p.name!='manifest.json'}
(OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('AERIAL_DASH_PACKAGE_COMPLETE',json.dumps(entries,ensure_ascii=False),flush=True)
