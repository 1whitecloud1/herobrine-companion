"""Port only the mode VFX renderer and shader files from NeoForge to Forge."""
from pathlib import Path

NEO = Path(__file__).resolve().parents[2]
FORGE = NEO.parent / 'herobrine companion'
neo_package = 'com.whitecloud233.herobrine_companion'
forge_package = 'com.whitecloud233.modid.herobrine_companion'

for name in ('VoidRiftProjection', 'VoidRiftPostEffect', 'VoidRiftRenderer'):
    source = NEO / 'src/main/java' / neo_package.replace('.', '/') / 'client/render' / (name + '.java')
    target = FORGE / 'src/main/java' / forge_package.replace('.', '/') / 'client/render' / source.name
    text = source.read_text(encoding='utf8').replace(neo_package, forge_package)
    text = text.replace('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(')
    if name == 'VoidRiftPostEffect':
        text = text.replace('net.neoforged.api.distmarker', 'net.minecraftforge.api.distmarker')
        text = text.replace('net.neoforged.bus.api', 'net.minecraftforge.eventbus.api')
        text = text.replace('import net.neoforged.fml.common.EventBusSubscriber;', 'import net.minecraftforge.fml.common.Mod;')
        text = text.replace('net.neoforged.neoforge.client.event.ClientTickEvent', 'net.minecraftforge.event.TickEvent')
        text = text.replace('net.neoforged.neoforge.client.event', 'net.minecraftforge.client.event')
        text = text.replace('@EventBusSubscriber(', '@Mod.EventBusSubscriber(')
        text = text.replace('bus = EventBusSubscriber.Bus.MOD', 'bus = Mod.EventBusSubscriber.Bus.MOD')
        text = text.replace('public static void onTick(ClientTickEvent.Post event) { syncLevel(); }',
                            'public static void onTick(TickEvent.ClientTickEvent event) {\n'
                            '        if (event.phase == TickEvent.Phase.END) syncLevel();\n    }')
        text = text.replace('RenderTarget output, boolean linear)', 'RenderTarget output)')
        text = text.replace('super.addPass(name, input, output, linear)', 'super.addPass(name, input, output)')
    if name == 'VoidRiftRenderer':
        text = text.replace('.addVertex(pose,', '.vertex(pose,').replace('.setColor(r, g, b, a);', '.color(r, g, b, a).endVertex();')
    target.write_text(text, encoding='utf8', newline='\n')
    print(target)

root = Path('src/main/resources/assets/herobrine_companion/shaders')
for relative in ('post/void_rift_lens.json', 'program/void_rift_lens.json',
                 'program/void_rift_lens.vsh', 'program/void_rift_lens.fsh',
                 'program/void_rift_copy.json', 'program/void_rift_copy.fsh'):
    (FORGE / root / relative).write_bytes((NEO / root / relative).read_bytes())
print('Ported 3 Java classes and 6 identical shader resources; gameplay remains version-specific.')

verifier = NEO / 'scripts/poem/PoemModeFxCheck.java'
if verifier.exists():
    text = verifier.read_text(encoding='utf8').replace(neo_package, forge_package)
    text = text.replace('PaleLightningPacket.STREAM_CODEC.encode(buffer, packet);', 'packet.encode(buffer);')
    text = text.replace('PaleLightningPacket.STREAM_CODEC.decode(buffer)', 'new PaleLightningPacket(buffer)')
    for field in ('x', 'y', 'z', 'width'):
        text = text.replace('decoded.' + field + '()', 'decoded.' + field)
    (FORGE / 'scripts/poem/PoemModeFxCheck.java').write_text(text, encoding='utf8', newline='\n')
    (FORGE / 'scripts/poem/verify_mode_fx.gradle').write_bytes((NEO / 'scripts/poem/verify_mode_fx.gradle').read_bytes())
