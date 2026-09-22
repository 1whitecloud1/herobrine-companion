"""Install into exactly the two requested NetEase projects, with a rollback log."""
import argparse
import hashlib
import json
import shutil
from datetime import datetime

from common import *

TARGETS = [(STORY,'behavior_pack_8c3d0351','resource_pack_f20afb6b','WhiteCloudScripts/modMain.py'),
           (CORE,'behavior_pack_hc_core_main','resource_pack_hc_core_main','HCCoreScripts/modMain.py')]


def encoded(value):
    return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def changes():
    values = {}
    for project,bp,rp,main in TARGETS:
        for source in RP.rglob('*'):
            if source.is_file():
                values[project/rp/source.relative_to(RP)] = source.read_bytes()
        for source in (BP/'HCPoemNetease').glob('*.py'):
            values[project/bp/'HCPoemNetease'/source.name] = source.read_bytes()
        target = project/bp/main
        original = target.read_text(encoding='utf-8-sig')
        lines = original.splitlines(True)
        if 'from HCPoemNetease import register_server' not in original:
            result = []
            client_added = server_added = 0
            for line in lines:
                result.append(line)
                if 'serverApi.RegisterSystem(' in line:
                    result += ['        from HCPoemNetease import register_server\n','        register_server()\n']
                    server_added += 1
                elif 'clientApi.RegisterSystem(' in line:
                    result += ['        from HCPoemNetease import register_client\n','        register_client()\n']
                    client_added += 1
            assert client_added == server_added == 1,(target,client_added,server_added)
            values[target] = ''.join(result).encode('utf-8')
        # Keep existing held-item appearance at idle. These are exact copies
        # of the current prerequisite pack's definitions, not a new weapon ID.
        for rel in ['attachables/poem_of_the_end.json','animations/poem_of_the_end.animation.json',
                    'animations/liandao.animation.json','textures/entity/liandao.png']:
            values[project/rp/rel] = (REQUIRED/'resource_pack_hc_required_sub'/rel).read_bytes()
        values[project/rp/'models/entity/poem_of_the_end.geo.json'] = (CORE/'resource_pack_hc_core_main/models/entity/poem_of_the_end.geo.json').read_bytes()

    project,bp,rp,_ = TARGETS[0]
    # The story project had no Poem item. Reuse the established identifier,
    # model and attack values so the animation can actually be triggered here.
    item = read(CORE/'behavior_pack_hc_core_main/netease_items_beh/poem_of_the_end.json')
    item['minecraft:item']['description']['category'] = 'equipment'
    item['minecraft:item']['components']['netease:customtips']['value'] = (
        '§6只是虚影而已...\n§b连续攻击衔接 22 段长连招，停手收招\n§7疾跑与腾空时使用对应攻击动作')
    values[project/bp/'netease_items_beh/poem_of_the_end.json'] = encoded(item)
    values[project/rp/'netease_items_res/poem_of_the_end.json'] = (CORE/'resource_pack_hc_core_main/netease_items_res/poem_of_the_end.json').read_bytes()
    icon_path = 'textures/items/herobrine_companion/poem_of_the_end.png'
    values[project/rp/icon_path] = (CORE/'resource_pack_hc_core_main'/icon_path).read_bytes()
    atlas = read(project/rp/'textures/item_texture.json')
    for name in ['herobrine_companion:poem_of_the_end','poem_of_the_end']:
        atlas.setdefault('texture_data',{})[name] = {'textures':icon_path[:-4]}
    values[project/rp/'textures/item_texture.json'] = encoded(atlas)
    lang = project/rp/'texts/zh_CN.lang'
    content = lang.read_text(encoding='utf-8-sig') if lang.exists() else ''
    key = 'item.herobrine_companion:poem_of_the_end.name='
    content = '\n'.join(line for line in content.splitlines() if not line.startswith(key))+'\n'+key+'终末之诗\n'
    values[lang] = content.encode('utf-8')
    # Core manifests are already 0.2.13, but the project world list still
    # selected 0.2.12. Correct only those two existing entries.
    for kind,pack in [('behavior','behavior_pack_hc_core_main'),('resource','resource_pack_hc_core_main')]:
        manifest = read(CORE/pack/'pack_manifest.json')['header']
        path = CORE/('world_'+kind+'_packs.json')
        listing = read(path)
        for entry in listing:
            if entry['pack_id'] == manifest['uuid']:
                entry['version'] = manifest['version']
        values[path] = encoded(listing)
    return {path:data for path,data in values.items() if not path.exists() or path.read_bytes()!=data}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    for name in ('asset_validation.json','runtime_validation.json'):
        assert read(WORK/name)['passed'],name
    updates=changes()
    roots=[target[0].resolve() for target in TARGETS]
    for path in updates:
        assert any(path.resolve().is_relative_to(root) for root in roots),path
    summary={str(root):sum(path.is_relative_to(root) for path in updates) for root in roots}
    print('INSTALL_PLAN',json.dumps(summary,ensure_ascii=False),'bytes',sum(map(len,updates.values())))
    if not args.apply or not updates:
        return
    stamp=datetime.now().strftime('%Y%m%d_%H%M%S')
    backup=OUT/'backups'/stamp
    entries=[]
    # All preimages are backed up before the first project mutation.
    for path,data in updates.items():
        root=next(root for root in roots if path.is_relative_to(root))
        relative=path.relative_to(root)
        entry={'project':str(root),'relative':relative.as_posix(),'created':not path.exists(),
               'installed_sha256':digest(data),'installed_bytes':len(data)}
        if path.exists():
            old=path.read_bytes()
            old_path=backup/root.name/relative
            old_path.parent.mkdir(parents=True,exist_ok=True)
            old_path.write_bytes(old)
            entry.update(before_sha256=digest(old),backup_relative=old_path.relative_to(backup).as_posix())
        entries.append(entry)
    journal={'stamp':stamp,'status':'backed_up','backup':str(backup),'targets':[str(p) for p in roots],'files':entries}
    write(backup/'install_manifest.json',journal,True)
    for path,data in updates.items():
        path.parent.mkdir(parents=True,exist_ok=True)
        temp=path.with_name(path.name+'.hc_poem_v1.tmp')
        temp.write_bytes(data)
        temp.replace(path)
    for entry in entries:
        path=Path(entry['project'])/entry['relative']
        assert digest(path.read_bytes())==entry['installed_sha256'],path
    journal['status']='installed_and_hash_verified'
    write(backup/'install_manifest.json',journal,True)
    write(WORK/'install_manifest.json',journal,True)
    print('INSTALLED',len(entries),'files; backup:',backup)


if __name__=='__main__':main()
