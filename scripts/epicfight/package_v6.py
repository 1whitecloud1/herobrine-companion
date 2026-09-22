"""Package V6 player animation resources, integration sources and verification reports."""
import hashlib
import json
from pathlib import Path
import zipfile
from PIL import Image,ImageDraw,ImageFont

REPO=Path(__file__).resolve().parents[2]
OUT=REPO/'build/epicfight-v6'
RESOURCE=REPO/'src/main/resources'
assets=RESOURCE/'assets/herobrine_companion'
clips=sorted((assets/'animmodels/animations/player/poem_v6').glob('*.json'))
assert len(clips)==23
reports=[OUT/n for n in ('conversion_report.json','validation_report.json','engine_validation.json','registry_validation.json','probe.json')]
assert all(p.exists() for p in reports)
canvas=Image.new('RGB',(1800,1030),(15,18,24)); draw=ImageDraw.Draw(canvas)
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',23)
draw.text((22,12),'Poem of the End | V6 player combo | 18 attacks / 3 passages',fill='white',font=font)
for i in range(18):
    im=Image.open(OUT/f'preview/{i+1:02d}.png').convert('RGB').resize((300,300))
    x=i%6*300;y=60+i//6*323;canvas.paste(im,(x,y));draw.text((x+12,y+294),f'{i+1:02d}',fill='white',font=font)
preview=OUT/'Poem_V6_player_preview.png';canvas.save(preview)
resources=clips+list((assets/'animmodels/animations/player/poem_v6/data').glob('*.json'))
resources += [assets/'epicfight/poem_v6_timing.json',RESOURCE/'data/herobrine_companion/capabilities/weapons/poem_of_the_end.json']
files={p.relative_to(RESOURCE).as_posix():p for p in resources}
files['README.md']=REPO/'docs/epicfight-poem-v6.md'
files[preview.name]=preview
for p in reports: files['reports/'+p.name]=p
if (OUT/'speed_revision.json').exists():
    files['reports/speed_revision.json']=OUT/'speed_revision.json'
for name in ('PoemScythePlayerAnimations.java','HeroEpicFightBridge.java','HeroNightfallAnimationRegistry.java'):
    files['integration/'+name]=REPO/'src/main/java/com/whitecloud233/herobrine_companion/compat/epicfight'/name
for name in ('export_v4.py','verify_v4.py','probe_v6.py','export_v6.py','verify_v6.py','V6ClipCheck.java','V6RegistrationCheck.java','verify_v6.gradle','package_v6.py'):
    files['scripts/epicfight/'+name]=Path(__file__).parent/name
manifest={n:hashlib.sha256(p.read_bytes()).hexdigest() for n,p in files.items()}
archive=OUT/'Poem_of_the_End_EpicFight_V6.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for n,p in sorted(files.items()): z.write(p,n)
    z.writestr('SHA256.json',json.dumps(manifest,indent=2)+'\n')
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert all(hashlib.sha256(z.read(n)).hexdigest()==h for n,h in manifest.items())
print('V6_PACKAGE_OK',archive,archive.stat().st_size,'bytes')
