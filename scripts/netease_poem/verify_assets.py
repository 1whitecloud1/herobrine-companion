"""Round-trip the shipped Bedrock JSON against the corrected Unity12 matrices."""
import hashlib
import numpy as np

from common import *
from export_assets import BODY_PARTS, visual_world, with_recovery, PLAYBACK_SPEED, RECOVERY_SECONDS


def main():
    manifest = read(WORK/'library.json')
    assert manifest['playback_speed'] == PLAYBACK_SPEED == .8
    assert abs(manifest['ground_chain_seconds'] * PLAYBACK_SPEED - manifest['ground_chain_source_seconds']) < 1e-5
    _, _, rest = source_rig()
    body = read(RP/'models/entity/hc_poem_v1_player.geo.json')['minecraft:geometry'][0]
    weapon = read(RP/'models/entity/hc_poem_v1_scythe.geo.json')['minecraft:geometry'][0]
    correction = np.array(read(ASSETS/'poem_standalone/rig.json')['weapon_to_tool'])
    calibration = np.array(read(ROOT/'build/epicfight-aerial-dash/conversion_report.json')['weapon']['model_to_socket'])
    reflect = np.eye(4); reflect[:3,:3] = np.diag([-1,1,1])/16.
    raw_to_library = calibration @ reflect
    reports = []
    for entry in manifest['clips']:
        anim = read(RP/'animations'/('hc_poem_v1_%02d.animation.json' % entry['id']))['animations'][entry['animation']]
        assert ') * ' + QUERY + 'rate' in anim['anim_time_update']
        assert abs(anim['animation_length'] - entry['length'] - RECOVERY_SECONDS * PLAYBACK_SPEED) < 1e-6
        assert 0 <= entry['step_at'] < entry['length']
        source_file = ASSETS/'animmodels/animations/player/poem_unity09'/(entry['key']+'.json')
        assert hashlib.sha256(source_file.read_bytes()).hexdigest() == entry['source_sha256']
        times, source = source_clip(entry['key'], entry['length'])
        world, _ = visual_world(times, source, entry['context'])
        times, world = with_recovery(times, world, rest)
        worst = 0.; blade_angle = 0.
        for index, time in enumerate(times):
            actual = sample_geometry(body, anim, time)
            for name in BODY_PARTS:
                expect = B @ world[name][index] @ np.linalg.inv(rest[name]) @ BI
                diff = np.max(np.abs(expect - actual[PREFIX+name.lower()]))
                worst = max(worst, diff)
            actual_weapon = sample_geometry(weapon, anim, time)[PREFIX+'weapon']
            expected_weapon = B @ world['Tool_R'][index] @ correction @ raw_to_library
            worst = max(worst, float(np.max(np.abs(expected_weapon - actual_weapon))))
            # Check the blade-side basis, not just the pole: a 180-degree blade
            # roll would preserve pole direction but fail this assertion.
            er = expected_weapon[:3,:3] / np.linalg.norm(expected_weapon[:3,:3],axis=0)
            ar = actual_weapon[:3,:3] / np.linalg.norm(actual_weapon[:3,:3],axis=0)
            angle = np.degrees(np.arccos(np.clip((np.trace(er.T@ar)-1)/2,-1,1)))
            blade_angle = max(blade_angle, float(angle))
        assert worst < .0008, (entry['key'], worst)
        assert blade_angle < .01, (entry['key'], blade_angle)
        # Recovery finishes with the original geometry at the correct pivots.
        final = sample_geometry(body, anim, times[-1])
        for name in BODY_PARTS:
            # JSON timestamps are rounded to microseconds; the very last key
            # can be interpolated a fraction of a microsecond before neutral.
            assert np.max(np.abs(final[PREFIX+name.lower()] - np.eye(4))) < .0008
        reports.append({'id':entry['id'],'key':entry['key'],'sample_count':len(times),
                        'max_matrix_component_error_pixels':worst,'max_blade_roll_error_degrees':blade_angle})
    assert len(manifest['ground_chain']) == len(set(manifest['ground_chain'])) == 22
    assert set(manifest['ground_chain'] + manifest['air_chain'] + manifest['sprint_chain']) == set(range(1,31))
    write(WORK/'asset_validation.json', {'passed':True,'clips':reports,
        'legs_compared_to_unity12':True,'blade_orientation_compared':True,
        'playback_speed':PLAYBACK_SPEED,'ground_chain_seconds':manifest['ground_chain_seconds'],
        'recovery_real_seconds':RECOVERY_SECONDS,
        'recovery_returns_to_bind':True,'live_gameplay_tested':False},True)
    print('ASSET_ROUNDTRIP_OK',len(reports),'clips;',sum(r['sample_count'] for r in reports),'samples;',
          'max matrix error',max(r['max_matrix_component_error_pixels'] for r in reports),'pixels')


if __name__ == '__main__':
    main()
