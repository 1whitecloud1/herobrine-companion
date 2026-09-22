from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[2]
for root,pkg in [(ROOT,'com/whitecloud233/herobrine_companion'),(Path('E:/java/herobrine companion'),'com/whitecloud233/modid/herobrine_companion')]:
    item=root/'src/main/java'/pkg/'item/PoemOfTheEndItem.java'
    text=item.read_text('utf-8')
    needle='            tooltipComponents.add(Component.translatable("item.herobrine_companion.poem_of_the_end.epicfight.input").withStyle(ChatFormatting.GRAY));'
    if 'poem_of_the_end.standalone.input' not in text:
        assert needle in text
        text=text.replace(needle,needle+'\n        } else if (!HeroEpicFightCompat.isLoaded()) {\n'
            '            tooltipComponents.add(Component.translatable("item.herobrine_companion.poem_of_the_end.standalone.input").withStyle(ChatFormatting.GRAY));\n'
            '            tooltipComponents.add(Component.translatable("item.herobrine_companion.poem_of_the_end.standalone.rules").withStyle(ChatFormatting.DARK_GRAY));')
        item.write_text(text,'utf-8')
    for lang,values,provider in [
        ('zh_cn',('无需史诗战斗：左键连按播放当前模式的四段全身普攻','伤害、命中时机和移动遵循原版规则'),'ModZhCnLangProvider.java'),
        ('en_us',('No Epic Fight required: successive attacks play this mode\'s four full-body strikes','Damage, hit timing and movement follow vanilla rules'),'ModEnUsLangProvider.java')]:
        for source in ['src/main/resources','src/generated/resources']:
            file=root/source/'assets/herobrine_companion/lang'/f'{lang}.json'
            if not file.exists():continue
            text=file.read_text('utf-8')
            if 'poem_of_the_end.standalone.input' in text:continue
            pos=text.rfind('}')
            prefix=text[:pos].rstrip()
            fields=',\n'.join('  '+json.dumps('item.herobrine_companion.poem_of_the_end.standalone.'+key)+': '+json.dumps(value,ensure_ascii=False) for key,value in zip(['input','rules'],values))
            file.write_text(prefix+',\n'+fields+'\n}\n','utf-8')
            json.loads(file.read_text('utf-8'))
        file=root/'src/main/java'/pkg/'datagen'/provider
        text=file.read_text('utf-8')
        if 'poem_of_the_end.standalone.input' in text:continue
        match=re.search(r'^.*add\("item\.herobrine_companion\.poem_of_the_end\.epicfight\.input".*$',text,re.M)
        assert match,file
        added='\n'.join('        add('+json.dumps('item.herobrine_companion.poem_of_the_end.standalone.'+key)+', '+json.dumps(value,ensure_ascii=False)+');' for key,value in zip(['input','rules'],values))
        text=text[:match.end()]+'\n'+added+text[match.end():]
        file.write_text(text,'utf-8')
print('Added mode-independent standalone usage hints in both languages and both versions.')
