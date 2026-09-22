"""Port only the new scythe input/extra-action implementation to Forge 1.20.1."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
FORGE = ROOT.parent / 'herobrine companion'
PKG = 'com/whitecloud233/herobrine_companion'
FPKG = 'com/whitecloud233/modid/herobrine_companion'


def java(path):
    return (ROOT / 'src/main/java' / PKG / path).read_text('utf-8').replace(
        'com.whitecloud233.herobrine_companion', 'com.whitecloud233.modid.herobrine_companion')


def save(path, text):
    dest = FORGE / 'src/main/java' / FPKG / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text, encoding='utf-8')


def edit(path, before, after):
    dest = FORGE / 'src/main/java' / FPKG / path
    text = dest.read_text('utf-8')
    if after in text:
        return
    assert text.count(before) == 1, (path, before)
    dest.write_text(text.replace(before, after), encoding='utf-8')


for path in ['combat/PoemAttackInput.java', 'combat/PoemGestureQueue.java',
             'mixin/epicfight/PoemAttackControlMixin.java',
             'compat/epicfight/UnityScythePlungeAnimation.java',
             'compat/epicfight/UnityScytheExtraAnimations.java']:
    save(path, java(path))

dest = FORGE / 'src/main/resources/herobrine_companion.mixins.json'
config = json.loads(dest.read_text('utf-8'))
if 'epicfight.PoemAttackControlMixin' not in config['client']:
    config['client'].append('epicfight.PoemAttackControlMixin')
    dest.write_text(json.dumps(config, indent=2) + '\n', encoding='utf-8')

edit('compat/epicfight/UnityScytheAnimations.java', 'first ? .08F : .035F', 'first ? .10F : .035F')

text = java('client/event/PoemScytheInputEvents.java')
text = text.replace('net.neoforged.api.distmarker', 'net.minecraftforge.api.distmarker')
text = text.replace('net.neoforged.bus.api', 'net.minecraftforge.eventbus.api')
text = text.replace('import net.neoforged.fml.common.EventBusSubscriber;', 'import net.minecraftforge.fml.common.Mod;')
text = text.replace('import net.neoforged.neoforge.client.event.ClientTickEvent;', 'import net.minecraftforge.event.TickEvent;')
text = text.replace('net.neoforged.neoforge.client.event', 'net.minecraftforge.client.event')
text = text.replace('@EventBusSubscriber(', '@Mod.EventBusSubscriber(')
text = text.replace('public static void onTick(ClientTickEvent.Pre event) {',
                    'public static void onTick(TickEvent.ClientTickEvent event) {\n        if (event.phase != TickEvent.Phase.START) return;')
save('client/event/PoemScytheInputEvents.java', text)

text = java('compat/epicfight/PoemGestureController.java')
text = text.replace('import net.neoforged.neoforge.common.NeoForge;', 'import net.minecraftforge.common.MinecraftForge;')
text = text.replace('import net.neoforged.neoforge.event.tick.PlayerTickEvent;', 'import net.minecraftforge.event.TickEvent;')
text = text.replace('NeoForge.EVENT_BUS', 'MinecraftForge.EVENT_BUS')
text = text.replace('private static void onTick(PlayerTickEvent.Post event) {',
                    'private static void onTick(TickEvent.PlayerTickEvent event) {\n        if (event.phase != TickEvent.Phase.END) return;')
text = text.replace('event.getEntity()', 'event.player')
save('compat/epicfight/PoemGestureController.java', text)

save('network/PoemGesturePacket.java', '''package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Intent only; animation, state and blade damage are selected on the server. */
public record PoemGesturePacket(int gesture, int slot, int mode) {
    public PoemGesturePacket(FriendlyByteBuf buffer) {
        this(buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte());
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(gesture); buffer.writeByte(slot); buffer.writeByte(mode);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            if (context.getSender() != null) {
                HeroEpicFightCompat.queuePoemGesture(context.getSender(), gesture, slot, mode);
            }
        });
    }
}
''')
edit('client/event/KeyBindingHandler.java', 'event.register(SKILL_KEY);',
     'event.register(SKILL_KEY);\n            PoemScytheInputEvents.registerKeys(event);')
edit('compat/epicfight/UnityScytheAnimations.java', '        return List.copyOf(sets);',
     '        UnityScytheExtraAnimations.register(builder, armature, actorPrefix);\n        return List.copyOf(sets);')
edit('compat/epicfight/UnityScytheAnimations.java', '    private static <T extends AttackAnimation> T configure(',
     '    static <T extends AttackAnimation> T configure(')
edit('compat/epicfight/UnityScytheAttackAnimation.java', '    static final class Air extends AirSlashAnimation {',
     '    static class Air extends AirSlashAnimation {')
dest = FORGE / 'src/main/java' / FPKG / 'compat/epicfight/UnityScytheAttackAnimation.java'
text = dest.read_text('utf-8')
main = java('compat/epicfight/UnityScytheAttackAnimation.java')
start, end = text.index('    private static final class RootMotion {'), text.index('    @Override public TransformSheet')
block = main[main.index('    private static final class RootMotion {'):main.index('    @Override public TransformSheet')]
text = text[:start] + block + text[end:]
if 'import java.util.ArrayList;' not in text:
    text = text.replace('import java.util.Optional;', 'import java.util.Optional;\nimport java.util.ArrayList;\nimport yesman.epicfight.api.animation.Keyframe;')
dest.write_text(text, encoding='utf-8')
edit('compat/epicfight/HeroEpicFightBridge.java', '        registered = true;',
     '        registered = true;\n        PoemGestureController.register();')
edit('compat/epicfight/HeroEpicFightBridge.java', '    public static void onPoemModeChanged(Player player) {',
     '    public static void onPoemModeChanged(Player player) {\n        PoemGestureController.cancel(player);')
dest = FORGE / 'src/main/java' / FPKG / 'compat/epicfight/HeroEpicFightBridge.java'
text = dest.read_text('utf-8')
if 'static void resetPoemComboCounter' not in text:
    start = text.index('    public static void onPoemModeChanged')
    end = text.index('\n    /**', start)
    main = java('compat/epicfight/HeroEpicFightBridge.java')
    block = main[main.index('    public static void onPoemModeChanged'):main.index('\n    /**', main.index('    public static void onPoemModeChanged'))]
    block = block.replace('SkillSlots.COMBO_ATTACKS', 'SkillSlots.BASIC_ATTACK').replace(
        'EpicFightSkillDataKeys.COMBO_COUNTER', 'SkillDataKeys.COMBO_COUNTER.get()')
    dest.write_text(text[:start] + block + text[end:], encoding='utf-8')
main = java('compat/epicfight/HeroEpicFightCompat.java')
block = main[main.index('    public static boolean canUsePoemGestures'):main.index('\n    /**', main.index('    public static boolean canUsePoemGestures'))]
edit('compat/epicfight/HeroEpicFightCompat.java', '    public enum BridgeStatus {', block + '\n\n    public enum BridgeStatus {')
# New wire IDs must be appended. Existing Forge packet order is preserved.
dest = FORGE / 'src/main/java' / FPKG / 'network/PacketHandler.java'
text = dest.read_text('utf-8')
if 'PoemGesturePacket.class' not in text:
    lines = text.splitlines(keepends=True)
    last = max(i for i, line in enumerate(lines) if 'reg(id,' in line)
    lines.insert(last + 1, '        reg(id, PoemGesturePacket.class, PoemGesturePacket::encode, PoemGesturePacket::new, PoemGesturePacket::handle);\n')
    dest.write_text(''.join(lines), encoding='utf-8')
print('FORGE_EXTRA_INPUT_PORTED')

for name in ['PoemInputCheck.java', 'PoemControlEngineCheck.java', 'UnityExtrasRuntimeCheck.java', 'UnityModesRuntimeCheck.java']:
    text = (ROOT / 'scripts/epicfight' / name).read_text('utf-8').replace(
        'com.whitecloud233.herobrine_companion', 'com.whitecloud233.modid.herobrine_companion')
    text = text.replace('com/whitecloud233/herobrine_companion', 'com/whitecloud233/modid/herobrine_companion')
    if name == 'UnityExtrasRuntimeCheck.java':
        text = text.replace('private Fixture() { super(null); }', 'private Fixture() { super(); }')
        text = text.replace('    public static void main(String[] args) throws Exception {', '''    public static void main(String[] args) throws Exception {
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES, List.of(new net.minecraft.server.packs.PathPackResources(
                        "unity-extras-verifier", Path.of("src/main/resources"), false)))) {
            AnimationManager.setServerResourceManager(resources);
            verify();
        } finally { AnimationManager.setServerResourceManager(null); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void verify() throws Exception {''')
    elif name == 'UnityModesRuntimeCheck.java':
        text = text.replace('private Fixture() { super(null); }', 'private Fixture() { super(); }')
        text = text.replace('    public static void main(String[] args) throws Exception { verify(); }', '''    public static void main(String[] args) throws Exception {
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES, List.of(new net.minecraft.server.packs.PathPackResources(
                        "unity-modes-verifier", Path.of("src/main/resources"), false)))) {
            AnimationManager.setServerResourceManager(resources);
            verify();
        } finally { AnimationManager.setServerResourceManager(null); }
    }''')
    (FORGE / 'scripts/epicfight' / name).write_text(text, encoding='utf-8')
dest = FORGE / 'scripts/epicfight/UnityModesRuntimeCheck.java'
text = dest.read_text('utf-8').replace('    private static double compareMatrices(', '    static double compareMatrices(')
dest.write_text(text, encoding='utf-8')
dest = FORGE / 'scripts/epicfight/verify_mediapipe.gradle'
text = dest.read_text('utf-8')
if 'PoemControlEngineCheck.java' not in text:
    text = text.replace("'scripts/epicfight/PoemInputCheck.java'", "'scripts/epicfight/PoemInputCheck.java', 'scripts/epicfight/PoemControlEngineCheck.java'")
    text = text.replace('def checks = [', "def checks = [\n                verifyPoemControlEngine: 'com.whitecloud233.modid.herobrine_companion.compat.epicfight.PoemControlEngineCheck',")
if 'UnityExtrasRuntimeCheck.java' not in text:
    text = text.replace("'scripts/epicfight/UnityModesRuntimeCheck.java')", "'scripts/epicfight/UnityModesRuntimeCheck.java',\n                    'scripts/epicfight/PoemInputCheck.java', 'scripts/epicfight/UnityExtrasRuntimeCheck.java')")
    text = text.replace('def checks = [', "def checks = [\n                verifyPoemInput: 'com.whitecloud233.modid.herobrine_companion.compat.epicfight.PoemInputCheck',\n                verifyUnityExtras: 'com.whitecloud233.modid.herobrine_companion.compat.epicfight.UnityExtrasRuntimeCheck',")
dest.write_text(text, encoding='utf-8')
