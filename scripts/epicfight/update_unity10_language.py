"""Keep Poem's default tap/hold instructions consistent in both distributions."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HINT = 'item.herobrine_companion.poem_of_the_end.epicfight.input'
MODE = 'item.herobrine_companion.poem_of_the_end.mode.usage.3'
labels = {
    'zh_cn': {HINT: '§7史诗战斗：短按左键接连招；长按0.35秒重击', MODE: '§7左键连击：挥镰与裂痕'},
    'en_us': {HINT: '§7Epic Fight: Tap Attack for combos; hold 0.35s for Heavy', MODE: '§7Attack Combo: Scythe Slashes & Rifts'},
}

for repo, package in [(ROOT, 'com/whitecloud233/herobrine_companion'),
                      (ROOT.parent / 'herobrine companion', 'com/whitecloud233/modid/herobrine_companion')]:
    for lang, entries in labels.items():
        for source in ['main', 'generated']:
            path = repo / f'src/{source}/resources/assets/herobrine_companion/lang/{lang}.json'
            if not path.exists():
                continue
            data = json.loads(path.read_text('utf-8-sig'))
            data.pop('key.herobrine_companion.poem_heavy_attack', None)
            data.update(entries)
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        provider = 'ModZhCnLangProvider.java' if lang == 'zh_cn' else 'ModEnUsLangProvider.java'
        path = repo / 'src/main/java' / package / 'datagen' / provider
        source = path.read_text('utf-8')
        if HINT not in source:
            block = ''.join('        add(' + json.dumps(k) + ', ' + json.dumps(v, ensure_ascii=False) + ');\n' for k, v in entries.items())
            source = source.replace('    protected void addTranslations() {', '    protected void addTranslations() {\n' + block)
            path.write_text(source, encoding='utf-8')
    path = repo / 'src/main/java' / package / 'item/PoemOfTheEndItem.java'
    source = path.read_text('utf-8').replace('// 长按左键极速连击', '// 挥镰连击与裂痕')
    if HINT not in source:
        anchor = '        tooltipComponents.add(Component.translatable("item.herobrine_companion.poem_of_the_end.usage").withStyle(ChatFormatting.DARK_GRAY));'
        assert source.count(anchor) == 1
        source = source.replace(anchor, '        if (HeroEpicFightCompat.isRuntimeBridgeReady()) {\n'
                                '            tooltipComponents.add(Component.translatable("' + HINT + '").withStyle(ChatFormatting.GRAY));\n'
                                '        }\n' + anchor)
    path.write_text(source, encoding='utf-8')
print('UNITY10_INPUT_LABELS_UPDATED')
