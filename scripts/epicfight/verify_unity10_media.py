"""Check encoded dimensions, frame counts and the explicit half-speed variants."""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/Herobrine_Scythe_UnityModes_10_EpicFight'
videos = {}
for path in sorted(OUT.glob('*.mp4')):
    data = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-select_streams', 'v:0',
        '-show_entries', 'stream=codec_name,width,height,r_frame_rate,nb_frames:format=duration', '-of', 'json', str(path)]))
    stream = data['streams'][0]
    assert stream['codec_name'] == 'h264'
    assert (stream['width'], stream['height']) in [(1600, 980), (1280, 940)]
    assert stream['r_frame_rate'] == ('30/1' if path.stem.endswith('_05x') else '60/1')
    assert int(stream['nb_frames']) > 0
    videos[path.name] = dict(stream, duration_seconds=float(data['format']['duration']))
assert len(videos) == 10, len(videos)
for name in ['Unity10_chaining', 'Unity10_extra_attacks', 'UnityModes10_comparison']:
    normal, slow = videos[name + '.mp4'], videos[name + '_05x.mp4']
    assert normal['nb_frames'] == slow['nb_frames']
    assert abs(2*normal['duration_seconds'] - slow['duration_seconds']) < .04
    if name == 'Unity10_chaining':
        wanted = sum(len(c['frames']) for c in json.loads((OUT / 'reports/chain_preview_timeline.json').read_text('utf-8'))['chains'])
        assert int(normal['nb_frames']) == wanted
# Decode the captions actually stored in the delivered videos for visual review.
for name, time, label in [('Unity10_extra_attacks_05x.mp4', 12.7, 'extra_heavy_caption'),
                          ('Unity10_chaining_05x.mp4', 1.4, 'chain_caption'),
                          ('UnityModes10_comparison_05x.mp4', 5.2, 'combo_recovery_caption')]:
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-ss', str(time), '-i', str(OUT / name),
                    '-frames:v', '1', '-update', '1', str(OUT / 'reports' / (label + '.jpg'))], check=True)
(OUT / 'reports/media_validation.json').write_text(json.dumps(dict(status='passed', videos=videos), indent=2)+'\n', encoding='utf-8')
print('UNITY10_MEDIA_OK', len(videos), 'videos; original/half-speed frame counts match')
