"""Verify installed files, combine rollback history, and package the patch."""
import ast
import hashlib
import shutil
import zipfile
from common import *


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    manifests=sorted((OUT/'backups').glob('*/install_manifest.json'))
    merged={}
    for path in manifests:
        journal=read(path)
        assert journal['status']=='installed_and_hash_verified'
        for source in journal['files']:
            key=(source['project'],source['relative'])
            if key not in merged:
                entry=dict(source)
                if not entry['created']:
                    backup=path.parent/entry['backup_relative']
                    entry['backup_relative']=backup.relative_to(OUT).as_posix()
                merged[key]=entry
            else:
                merged[key]['installed_sha256']=source['installed_sha256']
                merged[key]['installed_bytes']=source['installed_bytes']
    checks=[]
    for entry in merged.values():
        path=Path(entry['project'])/entry['relative']
        data=path.read_bytes()
        assert sha(data)==entry['installed_sha256'],path
        if path.suffix=='.json':read(path)
        elif path.suffix=='.py':ast.parse(data.decode('utf-8-sig'),filename=str(path))
        if not entry['created']:
            assert sha((OUT/entry['backup_relative']).read_bytes())==entry['before_sha256']
        checks.append(str(path))
    for source in RP.rglob('*'):
        if source.is_file():
            for project,pack in [(STORY,'resource_pack_f20afb6b'),(CORE,'resource_pack_hc_core_main')]:
                assert (project/pack/source.relative_to(RP)).read_bytes()==source.read_bytes()
    for source in (BP/'HCPoemNetease').glob('*.py'):
        a=STORY/'behavior_pack_8c3d0351/HCPoemNetease'/source.name
        b=CORE/'behavior_pack_hc_core_main/HCPoemNetease'/source.name
        assert a.read_bytes()==b.read_bytes()==source.read_bytes()
    for name in ('asset_validation.json','runtime_validation.json','speed08_validation.json','media_validation.json','source_provenance.json'):
        assert read(WORK/name)['passed'],name
    journal={'targets':[str(STORY),str(CORE)],'files':list(merged.values()),
             'description':'Restore the state before any changes in this NetEase animation integration.'}
    write(OUT/'restore_manifest.json',journal,True)
    shutil.copyfile(ROOT/'scripts/netease_poem/restore_install.py',OUT/'恢复接入前.py')
    reports=OUT/'reports';reports.mkdir(exist_ok=True)
    for name in ('library.json','conversion_report.json','asset_validation.json','runtime_validation.json','speed08_validation.json',
                 'source_provenance.json','media_validation.json','blender_preview_report.json'):
        shutil.copyfile(WORK/name,reports/name)
    shutil.copyfile(WORK/'settings_api_reference.txt',reports/'settings_api_reference.txt')
    library=read(WORK/'library.json')
    report={'passed':True,'installed_file_count':len(merged),'installed_files_hash_verified':checks,
            'both_projects_have_identical_animation_runtime':True,'both_projects_have_identical_animation_resources':True,
            'native_mod_settings_switch_installed':True,
            'settings_path':'模组设置 → 终末之诗 → 网易版终末之诗动作',
            'playback_speed':library['playback_speed'],'ground_chain_seconds':library['ground_chain_seconds'],
            'recovery_real_seconds':library['recovery_seconds'],'movement':library['movement'],
            'preview_playback_speed':0.5,'preview_represents_current_game_speed':False,
            'preview_includes_server_movement':False,
            'attack_pose_baseline_verified':True,
            'json_and_python_parse_passed':True,'source_matches_unity12_jar':True,'live_gameplay_tested':False}
    write(reports/'installed_validation.json',report,True)
    readme='''终末之诗 · 网易版长连招接入 01

两个工程已经直接更新，可以在 MCStudio 重新进入测试世界加载新增脚本和资源：

- 剧情工程：E:\\MCStudioDownload\\work\\m13525918851@163.com\\Cpp\\AddOn\\adfe8b2fa8444c3aaffbeca53d5ef7d8
- 陪伴核心工程：E:\\MCStudioDownload\\work\\m13525918851@163.com\\Cpp\\AddOn\\3055269d404f439eba5592a9d4b52113

游戏内开关：模组设置 → 终末之诗 → 网易版终末之诗动作。首次安装更新脚本后重新进入世界，默认开启；之后切换开关会立即生效，不必重新进世界。在正式发布环境中，页面名称跟随模组信息显示，开关名称仍为“网易版终末之诗动作”。

开关同时控制地面长连招、疾跑和空中攻击，以及自己随招产生的小幅跟进。关闭时立刻恢复原来的玩家与手持物品显示，清空排队动作，并通知服务端取消自己正在同步的连招及尚未触发的跟进；已经产生的移动由游戏物理自然消退。本机收到其他玩家的动画消息也不会重新激活动作。重新开启后，从下一次攻击重新起手，并恢复观看其他玩家的攻击。设置保存在本机，跨世界、两个工程共用；其他玩家保留各自的选择。两个工程一起加载时只注册一套动作系统和一个开关，不会重复添加设置或叠加位移。

全部 30 段攻击都改为上一版的 0.8 倍速，动作持续时间相应增加 25%。连续按攻击键会逐段衔接 22 段地面长连招，连续衔接时由约 19.2 秒延长到约 24 秒（换算后合计 23.977 秒，实际受脚本 tick 对齐影响）。停手后完成当前片段并用 0.14 秒收招，收招时间不随减速拉长；持续攻击时在片段边界直接接下一刀。短暂停顿可继续下一段，停顿超过约 1.1 秒重新起手。输入只缓存一次，并会过期，快速点击不会积攒一串停不下来的动作。转身、旋身、跨步、速斩、挑空与重击已经编入同一条连招，没有增加四模式切换。

疾跑攻击在 3 种对应招式中轮换，腾空攻击在 8 种对应招式中轮换。总共接入 30 个不同攻击片段，其中疾跑招式也出现在地面长连招里。第一人称有对应的镰刀动画；第三人称显示完整身体、膝盖和肘部动作。角色皮肤使用玩家当前贴图。

剧情工程原本没有终末之诗物品，已补齐同名物品、镰刀模型、贴图和手持显示。可以使用：

```text
/give @s herobrine_companion:poem_of_the_end
```

上一版只在模型里表现跨步，没有推动玩家的实际位置。本次增加服务端的小幅水平跟进，在每刀接近发力时触发一次；疾跑招式稍强，空中招式较轻。推进方向取该刀起手时的水平朝向，转身和抬头不会把推进方向转到身后或产生额外抬升。保留原有侧向移动与跳跃、下落速度；已有前向移动足够快、正在后退或受到较强击退时跳过推进。实际前进距离由地面摩擦、碰撞和原有速度决定，脚本中的速度值不是方块距离。关闭开关、换物品、死亡、换维度及离线会取消尚未触发的推进。

原伤害、命中和技能继续由游戏及原有技能脚本处理。模型中的跨步和身体旋转保留，真实跟进通过网易的玩家运动接口应用，不使用瞬移。核心工程原有技能前置仍然需要加载。剧情工程新增物品和攻击动画并不自行提供核心包的技能逻辑。

动作来自 Unity12 腿部修正版，30 个源文件与已经交付的 Unity12 JAR 逐一比对一致。此次网易速度与位移调整没有改动 Java 工程或 V6 动作。镰刀与身体分别驱动，保留抛镰、回收以及刀刃侧向。

已完成网易 JSON 的逐帧矩阵回读、源数据一致性检查、{runtime_checks} 项输入、同步、位移与设置模拟检查，以及两个工程的安装哈希检查。检查覆盖 0.8 倍时序、连续接刀、短收招、转身推进方向、保留垂直速度、防止重复推进和开关取消等行为。没有启动网易客户端，也没有进行实机测试，位移手感和碰撞效果仍需进游戏确认。此前用 Blender MCP 生成的预览继续保留为姿势参考；它没有展示此次的真实位移与 0.8 倍游戏节奏。

当前适配标准皮肤布局、宽/细手臂和常见原版护甲。护甲代理模型跟随屈膝和屈肘；官方 4D 皮肤、自定义护甲、皮革染色、盔甲纹饰及附魔发光尚未完整适配。更换物品、死亡、切换维度和退出系统时会恢复原玩家与手持模型显示。

交付文件：

- NetEase_终末之诗_长连招与空中攻击_05x.mp4：旧版姿势参考。前半是 22 段地面连招，后半是 8 种腾空攻击，按源动作 0.5 倍速输出，每段显示真实来源名称。它不代表此次 0.8 倍实机速度，也不包含服务端真实位移。
- NetEase_终末之诗_长连招验证.blend：旧版离线姿势预览工程，可逐帧查看动作。
- NetEase_长连招关键姿势.jpg：原关键姿势总览。
- 工程补丁目录 / ZIP：只包含本次改动，不是完整的原模组整合包。两个指定工程已经安装，不需要再次覆盖。
- reports：转换、输入同步、媒体和安装验证报告。

如果要改成“点一次，自动播放整套”，把两个工程行为包下 HCPoemNetease/config.py 的 PLAY_STYLE 从 buffered 改成 full，然后重新加载世界。默认仍为连续攻击逐段接续。

备份集中放在 backups，并提供了合并的 restore_manifest.json。恢复脚本默认只检查；加 --apply 才恢复接入前的 6 个原文件并删除此次新增文件。它会先校验哈希，遇到安装后又被手工修改的文件会停止，避免覆盖后续工作。运行示例：

```powershell
& 'E:/java/herobrine_companion/build/scythe_mocap/.venv/Scripts/python.exe' -X utf8 'E:/java/herobrine_companion/output/Herobrine_Poem_NetEase_LongCombo_01/恢复接入前.py' --apply
```

另外修正了核心工程 world_behavior_packs.json / world_resource_packs.json 中仍指向 0.2.12 的本包引用，使其与工程现有 0.2.13 清单一致。其他依赖版本未改变。
'''
    original_count=sum(not e['created'] for e in merged.values())
    readme=readme.replace('{runtime_checks}',str(read(WORK/'runtime_validation.json')['check_count']))
    readme=readme.replace('6 个原文件',str(original_count)+' 个原文件')
    (OUT/'使用说明.md').write_text(readme,encoding='utf-8')
    patch=OUT/'工程补丁'
    for entry in merged.values():
        root=Path(entry['project']);source=root/entry['relative'];target=patch/root.name/entry['relative']
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
    archive=OUT/'NetEase_终末之诗_双工程接入包.zip'
    files=[OUT/'使用说明.md',OUT/'恢复接入前.py',OUT/'restore_manifest.json',
           OUT/'NetEase_终末之诗_长连招与空中攻击_05x.mp4',OUT/'NetEase_终末之诗_长连招验证.blend',OUT/'NetEase_长连招关键姿势.jpg']
    files+=list(patch.rglob('*'))+list(reports.rglob('*'))
    files+=[OUT/e['backup_relative'] for e in merged.values() if not e['created']]
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path in sorted(set(p for p in files if p.is_file())):
            z.write(path,path.relative_to(OUT).as_posix())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
    sums=[]
    for path in [archive]+[p for p in files[:6] if p.is_file()]:
        sums.append(sha(path.read_bytes())+'  '+path.name)
    (OUT/'SHA256SUMS.txt').write_text('\n'.join(sums)+'\n',encoding='utf-8')
    print('DELIVERY_VERIFIED',len(merged),'files;',original_count,'original files backed up;',archive.stat().st_size,'zip bytes')


if __name__=='__main__':main()
