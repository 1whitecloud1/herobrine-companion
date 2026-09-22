"""Package the converted animation resources, integration source and QA reports."""
import hashlib
import json
from pathlib import Path
import zipfile

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[2]
OUT = REPO/'build/epicfight-v4'
RESOURCE = REPO/'src/main/resources'
asset_dir = RESOURCE/'assets/herobrine_companion/animmodels'
clips = sorted((asset_dir/'animations/hero').glob('hero_scythe_combo_v4*.json'))
assert len(clips) == 5
reports = [OUT/n for n in ('conversion_report.json','validation_report.json','engine_validation.json')]
assert all(p.exists() for p in reports)

times=[0,.575,.9,1.3,1.7167,2.05,2.4,2.7333,3.05,3.5,3.8,4.2]
sheet=Image.new('RGB',(1600,1370),(20,27,37))
draw=ImageDraw.Draw(sheet)
font_path=Path('C:/Windows/Fonts/arial.ttf')
font=ImageFont.truetype(str(font_path),20) if font_path.exists() else ImageFont.load_default()
title=ImageFont.truetype(str(font_path),34) if font_path.exists() else font
draw.text((22,16),'HEROBRINE V4  /  EPIC FIGHT',font=title,fill=(238,245,255))
draw.text((24,62),'Decoded Epic Fight BIPED mesh  |  4.20 s  |  120 Hz  |  Offline animation preview',font=font,fill=(153,205,218))
for i,t in enumerate(times):
    tile=Image.open(OUT/f'preview/{i:02d}.png').convert('RGB').resize((400,400))
    x,y=(i%4)*400,110+(i//4)*420
    sheet.paste(tile,(x,y))
    draw.text((x+12,y+400),f'{t:.3f} s',fill='white',font=font)
preview=OUT/'Herobrine_EpicFight_V4_preview.png'
sheet.save(preview)

files={str(p.relative_to(RESOURCE)).replace('\\','/'):p for p in clips}
armature=asset_dir/'entity/hero_biped_nightfall.json'
files[str(armature.relative_to(RESOURCE)).replace('\\','/')]=armature
license_file=RESOURCE/'META-INF/licenses/herobrine_scythe_v4.txt'
files['LICENSE_SOURCE.txt']=license_file
files['README.md']=REPO/'docs/epicfight-scythe-v4.md'
files[preview.name]=preview
for p in reports:files['reports/'+p.name]=p
for name in ('HeroScytheComboBehaviors.java','HeroNightfallAnimationRegistry.java'):
    files['integration/'+name]=REPO/'src/main/java/com/whitecloud233/herobrine_companion/compat/epicfight'/name
for p in Path(__file__).parent.iterdir():
    if p.is_file():files['scripts/epicfight/'+p.name]=p
manifest={name:hashlib.sha256(p.read_bytes()).hexdigest() for name,p in files.items()}
archive=OUT/'Herobrine_EpicFight_V4.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name,p in sorted(files.items()):z.write(p,name)
    z.writestr('SHA256.json',json.dumps(manifest,indent=2)+'\n')
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for name,digest in manifest.items():assert hashlib.sha256(z.read(name)).hexdigest()==digest
print('PACKAGE_OK',archive,archive.stat().st_size,'bytes',len(files)+1,'entries')
