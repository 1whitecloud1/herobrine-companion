"""Port only the independent Poem animation layer; preserve all existing EF/WOM changes."""
from pathlib import Path
import json
from generate_native_item_model import generate as generate_native_item_model

ROOT = Path(__file__).resolve().parents[2]
FORGE = Path('E:/java/herobrine companion')
PACKAGE = 'com.whitecloud233.herobrine_companion'
PORT_PACKAGE = 'com.whitecloud233.modid.herobrine_companion'
SOURCE = ROOT/'src/main/java'/PACKAGE.replace('.', '/')
TARGET = FORGE/'src/main/java'/PORT_PACKAGE.replace('.', '/')

files = [
    'combat/poem/PoemMotionLibrary.java', 'combat/poem/PoemComboClock.java',
    'combat/poem/PoemBladeTrail.java', 'combat/poem/PoemAttackStep.java',
    'combat/poem/StandalonePoemController.java', 'client/animation/StandalonePoemAnimation.java',
    'client/animation/PoemSkinMesh.java', 'client/render/StandalonePoemRenderer.java',
    'client/animation/PoemTrailMesh.java',
    'client/animation/PoemItemMesh.java', 'client/render/PoemOfTheEndMeshRenderer.java',
    'client/render/PoemRenderBackend.java',
    'client/render/PoemWeaponRenderTypes.java',
    'client/render/StandalonePoemTrailRenderer.java',
    'client/event/StandalonePoemEvents.java', 'mixin/client/PoemLivingRendererMixin.java',
    'mixin/client/PoemHumanoidRenderMixin.java', 'mixin/client/PoemHeldItemMixin.java',
]
for rel in files:
    text = (SOURCE/rel).read_text(encoding='utf-8').replace(PACKAGE, PORT_PACKAGE)
    text = text.replace('net.neoforged.api.', 'net.minecraftforge.api.')
    text = text.replace('net.neoforged.bus.api.', 'net.minecraftforge.eventbus.api.')
    text = text.replace('net.neoforged.fml.common.EventBusSubscriber', 'net.minecraftforge.fml.common.Mod')
    text = text.replace('@EventBusSubscriber(', '@Mod.EventBusSubscriber(')
    text = text.replace('net.neoforged.fml.', 'net.minecraftforge.fml.')
    text = text.replace('net.neoforged.neoforge.', 'net.minecraftforge.')
    if rel.endswith('StandalonePoemController.java'):
        text = text.replace('import net.minecraftforge.event.tick.PlayerTickEvent;', 'import net.minecraftforge.event.TickEvent;')
        text = text.replace('onTick(PlayerTickEvent.Post event)', 'onTick(TickEvent.PlayerTickEvent event)')
        text = text.replace('if (!(event.getEntity() instanceof ServerPlayer player)) return;',
                            'if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;')
    if rel.endswith('StandalonePoemEvents.java'):
        text = text.replace('import net.minecraftforge.client.event.ClientTickEvent;', 'import net.minecraftforge.event.TickEvent;')
        text = text.replace('onTick(ClientTickEvent.Post event) {',
                            'onTick(TickEvent.ClientTickEvent event) {\n        if (event.phase != TickEvent.Phase.END) return;')
    if rel.endswith('StandalonePoemRenderer.java'):
        text = text.replace('ResourceLocation.parse(DATA.texture())', 'new ResourceLocation(DATA.texture())')
    if rel.endswith(('StandalonePoemTrailRenderer.java', 'PoemTrailMesh.java', 'PoemOfTheEndMeshRenderer.java', 'PoemWeaponRenderTypes.java')):
        text = text.replace('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(')
        text = text.replace('buffer.addVertex(p.x, p.y, p.z).setColor(1f, 1f, 1f, alpha).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);',
                            'buffer.vertex(p.x, p.y, p.z).color(1f, 1f, 1f, alpha).uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();')
    if rel.endswith('PoemOfTheEndMeshRenderer.java'):
        text = text.replace('public final class PoemOfTheEndMeshRenderer extends BlockEntityWithoutLevelRenderer {',
                            'public final class PoemOfTheEndMeshRenderer extends BlockEntityWithoutLevelRenderer {\n'
                            '    private final PoemOfTheEndFallbackRenderer flatRenderer = new PoemOfTheEndFallbackRenderer();')
        text = text.replace('PoemOfTheEndFlatRenderer.render(', 'flatRenderer.renderByItem(')
        text = text.replace("            // NeoForge has already applied the item's JSON display transform.",
                            '            // Forge keeps the builtin/entity display empty; apply it exactly once.\n'
                            '            poseStack.translate(.5, .5, .5);\n'
                            '            PoemItemMesh.display(context).apply(false, poseStack);\n'
                            '            poseStack.translate(-.5, -.5, -.5);')
    if rel.endswith('PoemSkinMesh.java'):
        text = text.replace('cube.compile(new PoseStack().last(), capture, 0, 0, -1);',
                            'cube.compile(new PoseStack().last(), capture, 0, 0, 1, 1, 1, 1);')
        text = text.replace('buffer.addVertex(p.x, p.y, p.z, color, u, v, overlay, light, normal.x, normal.y, normal.z);',
                            'buffer.vertex(p.x, p.y, p.z).color(color).uv(u, v).overlayCoords(overlay).uv2(light).normal(normal.x, normal.y, normal.z).endVertex();')
        text = text.replace('VertexConsumer addVertex(float x, float y, float z) { point = new Vector3f(x, y, z);',
                            'VertexConsumer vertex(double x, double y, double z) { point = new Vector3f((float) x, (float) y, (float) z);')
        for old, new in [('setColor', 'color'), ('setUv1', 'overlayCoords'), ('setUv2', 'uv2'), ('setUv', 'uv'), ('setNormal', 'normal')]:
            text = text.replace('VertexConsumer ' + old + '(', 'VertexConsumer ' + new + '(')
        text = text.replace('vertices.add(new Vertex(point, u, v)); return this;', 'return this;')
        text = text.replace('    private static final class Capture implements VertexConsumer {',
                            '    private static final class Capture implements VertexConsumer {\n'
                            '        @Override public void endVertex() { vertices.add(new Vertex(point, u, v)); }\n'
                            '        @Override public void defaultColor(int r, int g, int b, int a) { }\n'
                            '        @Override public void unsetDefaultColor() { }')
    if rel.endswith('PoemHumanoidRenderMixin.java'):
        text = text.replace('VertexConsumer;III)V', 'VertexConsumer;IIFFFF)V')
        text = text.replace('int light, int overlay, int color, CallbackInfo ci)',
                            'int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci)')
        text = text.replace('if (StandalonePoemRenderer.renderModel',
                            'int color = net.minecraft.util.FastColor.ARGB32.color((int) (alpha * 255), (int) (red * 255), (int) (green * 255), (int) (blue * 255));\n        if (StandalonePoemRenderer.renderModel')
    dest = TARGET/rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text, encoding='utf-8')

request = '''package PACKAGE.network;

import PACKAGE.combat.poem.StandalonePoemController;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record PoemAnimationRequestPacket(int slot, int mode) {
    public PoemAnimationRequestPacket(FriendlyByteBuf buffer) { this(buffer.readByte(), buffer.readByte()); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(slot); buffer.writeByte(mode); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            if (context.getSender() != null) {
                if (slot == -1) StandalonePoemController.stop(context.getSender());
                else StandalonePoemController.request(context.getSender(), slot, mode);
            }
        });
    }
}
'''.replace('PACKAGE', PORT_PACKAGE)
packet = '''package PACKAGE.network;

import PACKAGE.client.animation.StandalonePoemAnimation;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record PoemAnimationPacket(int entityId, UUID playerId, int slot, int mode, int step, long startTick, long serverTick) {
    public PoemAnimationPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readUUID(), b.readByte(), b.readByte(), b.readByte(), b.readLong(), b.readLong());
    }
    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entityId); b.writeUUID(playerId); b.writeByte(slot); b.writeByte(mode);
        b.writeByte(step); b.writeLong(startTick); b.writeLong(serverTick);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> StandalonePoemAnimation.receive(this));
        context.setPacketHandled(true);
    }
}
'''.replace('PACKAGE', PORT_PACKAGE)
(TARGET/'network/PoemAnimationRequestPacket.java').write_text(request, encoding='utf-8')
(TARGET/'network/PoemAnimationPacket.java').write_text(packet, encoding='utf-8')

handler = TARGET/'network/PacketHandler.java'
text = handler.read_text(encoding='utf-8')
text = text.replace('private static final String PROTOCOL_VERSION = "1";',
                    'private static final String PROTOCOL_VERSION = "2"; // Independent Poem sync requires matching client/server builds.')
anchor = '        reg(id, PoemGesturePacket.class, PoemGesturePacket::encode, PoemGesturePacket::new, PoemGesturePacket::handle);'
assert anchor in text
if 'reg(id, PoemAnimationPacket.class,' not in text:
    text = text.replace(anchor, anchor + '\n'
        '        reg(id, PoemAnimationRequestPacket.class, PoemAnimationRequestPacket::encode, PoemAnimationRequestPacket::new, PoemAnimationRequestPacket::handle);\n'
        '        reg(id, PoemAnimationPacket.class, PoemAnimationPacket::encode, PoemAnimationPacket::new, PoemAnimationPacket::handle);')
handler.write_text(text, encoding='utf-8')
mixins = FORGE/'src/main/resources/herobrine_companion.mixins.json'
data = json.loads(mixins.read_text(encoding='utf-8'))
for name in ['PoemLivingRendererMixin', 'PoemHumanoidRenderMixin', 'PoemHeldItemMixin']:
    if 'client.' + name not in data['client']: data['client'].append('client.' + name)
mixins.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
for src in (ROOT/'src/main/resources/assets/herobrine_companion/poem_standalone').glob('*.json'):
    dest = FORGE/src.relative_to(ROOT)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(src.read_bytes())
texture = Path('src/main/resources/assets/herobrine_companion/textures/trail/poem_standalone_trail.png')
(FORGE/texture).write_bytes((ROOT/texture).read_bytes())
# The original atlases differ by nine pixels between versions. Split each
# version's own atlas so existing colors survive the native rendering fallback.
generate_native_item_model(FORGE)
print('Ported independent Poem animation, blade ribbons, attack steps and native item mesh; existing Forge packet IDs retained.')

verify = (ROOT/'scripts/poem/StandalonePoemCheck.java').read_text(encoding='utf-8').replace(PACKAGE, PORT_PACKAGE)
for packet in ['PoemAnimationPacket','PoemAnimationRequestPacket']:
    verify = verify.replace(packet+'.STREAM_CODEC.encode(buffer, packet);', 'packet.encode(buffer);')
    verify = verify.replace(packet+'.STREAM_CODEC.decode(buffer)', 'new '+packet+'(buffer)')
verify = verify.replace('VertexConsumer addVertex(float x, float y, float z) { this.x = x; this.y = y; this.z = z;',
                        'VertexConsumer vertex(double x, double y, double z) { this.x = (float) x; this.y = (float) y; this.z = (float) z;')
for old, new in [('setColor', 'color'), ('setUv1', 'overlayCoords'), ('setUv2', 'uv2'), ('setUv', 'uv'), ('setNormal', 'normal')]:
    verify = verify.replace('VertexConsumer ' + old + '(', 'VertexConsumer ' + new + '(')
verify = verify.replace('    private static final class Recorder implements VertexConsumer {',
                        '    private static final class Recorder implements VertexConsumer {\n'
                        '        public void endVertex() { }\n'
                        '        public void defaultColor(int r, int g, int b, int a) { }\n'
                        '        public void unsetDefaultColor() { }')
(FORGE/'scripts/poem').mkdir(parents=True, exist_ok=True)
(FORGE/'scripts/poem/StandalonePoemCheck.java').write_text(verify, encoding='utf-8')
(FORGE/'scripts/poem/verify_standalone.gradle').write_bytes((ROOT/'scripts/poem/verify_standalone.gradle').read_bytes())
