"""Collect the verified game JAR, usage notes, offline poses and audit reports."""
import hashlib
import json
import shutil
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/epicfight-mediapipe'
OUT = ROOT / 'output/Herobrine_Scythe_Recapture_05_EpicFight'
load = lambda p: json.loads(p.read_text(encoding='utf-8'))
jar_report = load(WORK / 'jar_validation.json')
registry = load(WORK / 'registry_validation.json')
geometry = load(WORK / 'validation_report.json')
assert registry['player_modes'] == {'0': 'MediaPipe', '1': 'V6', '2': 'MediaPipe', '3': 'MediaPipe'}
assert registry['hero_combo_slots'] == 8 and registry['production_hit_phases_checked']
assert registry['full_body_attack_policy_checked']
assert len(load(WORK / 'engine_validation.json')) == 24
assert geometry['v6_resources_unchanged'] and geometry['minimum_mesh_height_blocks'] >= 0
assert geometry['minimum_weapon_height_blocks'] >= 0
assert max(geometry['right_grip_max_error_blocks'], geometry['left_grip_max_error_blocks']) < .012
assert geometry['maximum_planted_foot_drift_blocks'] < .005 and geometry['maximum_support_sole_height_blocks'] < .035
assert geometry['ordinary_attacks_always_grounded'] and geometry['distinct_attack_trajectories']
jar = ROOT / jar_report['jar']
assert hashlib.sha256(jar.read_bytes()).hexdigest() == jar_report['sha256']
OUT.mkdir(parents=True, exist_ok=True)
target = OUT / (jar.stem + '-MediaPipe-Recapture05.jar')
shutil.copy2(jar, target)
shutil.copy2(ROOT / 'docs/epicfight-poem-mediapipe.md', OUT / 'README_中文.md')
reports = OUT / 'reports'
reports.mkdir(exist_ok=True)
for name in ('conversion_report.json', 'validation_report.json', 'engine_validation.json',
             'registry_validation.json', 'movement_validation.json', 'jar_validation.json', 'v6_baseline_sha256.json'):
    shutil.copy2(WORK / name, reports / name)
for name in ('engine_validation.json', 'reach_validation.json'):
    shutil.copy2(ROOT / 'build/epicfight-v6' / name, reports / ('v6_' + name))
for name in ('capture_report.json', 'selection_report.json'):
    shutil.copy2(ROOT/'build/scythe_recapture_05'/name, reports/name)
for name in ('retarget_report.json', 'footstep_polish_report.json', 'blender_bake_report.json', 'source_review_report.json'):
    shutil.copy2(ROOT/'output/Herobrine_Scythe_Recapture_05/capture'/name, reports/name)
conversion = load(WORK/'conversion_report.json')
assert conversion['combat_edit'] == 'mediapipe_recapture_05'
frames = [f for s in conversion['segments'] for f in (s['first_frame'],round(s['first_frame']+s['contacts'][0]['end']*conversion['source_fps']))]
sheet = Image.new('RGB', (2160, 1780), '#17222e')
draw = ImageDraw.Draw(sheet)
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 21)
draw.text((20, 12), 'Epic Fight 游戏网格离线预览 · 左手握中段 / 右手握柄尾', font=font, fill='#e5f5fa')
for i, frame in enumerate(frames):
    x, y = i % 4 * 540, 48 + i // 4 * 432
    im = Image.open(WORK / f'preview/{frame:04d}.png').convert('RGB').resize((540, 405), Image.Resampling.LANCZOS)
    sheet.paste(im, (x, y))
    draw.rectangle((x + 8, y + 8, x + 420, y + 38), fill='#17222e')
    label=conversion['segments'][i//2]['label']+(' · 起手' if i%2==0 else ' · 挥砍')
    draw.text((x + 14, y + 10), label, font=font, fill='#e5f5fa')
sheet.save(OUT / 'Game_Mesh_Poses.jpg', quality=94)
for video in (WORK/'preview').glob('Recapture05_*.mp4'):
    shutil.copy2(video, OUT/video.name)
manifest = {p.relative_to(OUT).as_posix(): {'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
            for p in sorted(OUT.rglob('*')) if p.is_file() and p.name != 'manifest.json'}
(OUT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('GAME_PACKAGE_COMPLETE', json.dumps({'jar': str(target), 'bytes': target.stat().st_size,
      'files': len(manifest) + 1, 'sha256': jar_report['sha256']}, ensure_ascii=True))
