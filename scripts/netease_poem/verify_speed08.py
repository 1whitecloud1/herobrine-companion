"""Compare this timing/movement update with the saved installed-asset baseline."""
from common import *


def main():
    baseline=Path(read(WORK/'speed08_baseline.json')['backup'])
    old_root=baseline/'resource_pack'
    old_library=read(baseline/'library.json')
    library=read(WORK/'library.json')
    old_clips={entry['id']:entry for entry in old_library['clips']}
    compared_curves=0
    for entry in library['clips']:
        previous=old_clips[entry['id']]
        for key in ('key','source_sha256','length','chain_at','context','contacts'):
            assert entry[key]==previous[key],(entry['id'],key)
        relative=Path('animations')/('hc_poem_v1_%02d.animation.json' % entry['id'])
        old=read(old_root/relative)['animations'][entry['animation']]
        new=read(RP/relative)['animations'][entry['animation']]
        assert old['bones'].keys()==new['bones'].keys(),entry['id']
        for bone,channels in old['bones'].items():
            assert channels.keys()==new['bones'][bone].keys(),(entry['id'],bone)
            for channel,curve in channels.items():
                other=new['bones'][bone][channel]
                if isinstance(curve,dict):
                    original={key:value for key,value in curve.items() if float(key)<=entry['length']+1e-6}
                    current={key:value for key,value in other.items() if float(key)<=entry['length']+1e-6}
                    assert original==current,(entry['id'],bone,channel)
                    compared_curves+=1
                else:
                    assert curve==other,(entry['id'],bone,channel)
        for key,value in old.items():
            if key not in ('anim_time_update','animation_length','bones'):
                assert value==new[key],(entry['id'],key)
    old_files={path.relative_to(old_root) for path in old_root.rglob('*') if path.is_file()}
    new_files={path.relative_to(RP) for path in RP.rglob('*') if path.is_file()}
    assert old_files==new_files
    unchanged=[]
    for relative in sorted(old_files):
        if relative.parts[0]!='animations':
            assert (old_root/relative).read_bytes()==(RP/relative).read_bytes(),relative
            unchanged.append(relative.as_posix())
    for key in ('ground_chain','air_chain','sprint_chain'):
        assert old_library[key]==library[key],key
    write(WORK/'speed08_validation.json',{
        'passed':True,'baseline':str(baseline),'clips_compared':len(library['clips']),
        'attack_curves_unchanged':compared_curves,'unchanged_non_animation_resources':unchanged,
        'blade_and_body_attack_poses_unchanged':True,'chain_order_unchanged':True,
        'playback_speed':library['playback_speed'],'ground_chain_seconds':library['ground_chain_seconds'],
        'recovery_real_seconds':library['recovery_seconds'],
        'movement':library['movement'],'live_gameplay_tested':False},True)
    print('SPEED08_BASELINE_OK',len(library['clips']),'clips;',compared_curves,
          'attack curves and',len(unchanged),'non-animation resources unchanged')


if __name__=='__main__':main()
