package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whitecloud233.herobrine_companion.client.animation.PoemSkinMesh;
import com.whitecloud233.herobrine_companion.client.animation.PoemSkinMesh.Part;
import com.whitecloud233.herobrine_companion.client.animation.StandalonePoemAnimation;
import com.whitecloud233.herobrine_companion.client.animation.StandalonePoemAnimation.Frame;
import com.whitecloud233.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.herobrine_companion.combat.poem.PoemBladeTrail;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Keeps the vanilla skin/armor/layer pipeline and changes only its vertex pose during a Poem combo. */
public final class StandalonePoemRenderer {
    private static final ThreadLocal<Deque<Context>> CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Context EMPTY = new Context(null, null, null);
    private static PlayerModel<AbstractClientPlayer> firstPersonWide, firstPersonSlim;
    private StandalonePoemRenderer() { }

    public static void push(LivingEntity entity, EntityModel<?> model, float partial) {
        Frame frame = entity instanceof Player player && model instanceof PlayerModel<?> ? StandalonePoemAnimation.frame(player, partial) : null;
        CONTEXT.get().push(frame == null ? EMPTY : new Context(entity, (HumanoidModel<?>) model, frame));
    }
    public static void pop() { if (!CONTEXT.get().isEmpty()) CONTEXT.get().pop(); }
    public static void clearContext() { CONTEXT.get().clear(); }
    private static Context context() { return CONTEXT.get().isEmpty() ? EMPTY : CONTEXT.get().peek(); }

    public static float attackYaw(LivingEntity entity, float bodyYaw, float partial) {
        Context context = context();
        return context.frame != null && context.entity == entity
                ? Mth.rotLerp(partial, entity.yRotO, entity.getYRot()) : bodyYaw;
    }

    public static boolean renderModel(Object model, PoseStack stack, VertexConsumer buffer, int light, int overlay, int color) {
        Context context = context();
        if (context.frame == null || !(model instanceof HumanoidModel<?> humanoid)) return false;
        body(humanoid, context.frame, stack, buffer, light, overlay, color, false);
        return true;
    }

    private static void body(HumanoidModel<?> model, Frame frame, PoseStack stack, VertexConsumer buffer,
                             int light, int overlay, int color, boolean armsOnly) {
        if (!armsOnly) {
            part(model.head, Part.HEAD, frame, stack, buffer, light, overlay, color);
            part(model.hat, Part.HEAD, frame, stack, buffer, light, overlay, color);
            part(model.body, Part.BODY, frame, stack, buffer, light, overlay, color);
            part(model.rightLeg, Part.RIGHT_LEG, frame, stack, buffer, light, overlay, color);
            part(model.leftLeg, Part.LEFT_LEG, frame, stack, buffer, light, overlay, color);
        }
        part(model.rightArm, Part.RIGHT_ARM, frame, stack, buffer, light, overlay, color);
        part(model.leftArm, Part.LEFT_ARM, frame, stack, buffer, light, overlay, color);
        if (model instanceof PlayerModel<?> player) {
            part(player.rightSleeve, Part.RIGHT_ARM, frame, stack, buffer, light, overlay, color);
            part(player.leftSleeve, Part.LEFT_ARM, frame, stack, buffer, light, overlay, color);
            if (!armsOnly) {
                part(player.jacket, Part.BODY, frame, stack, buffer, light, overlay, color);
                part(player.rightPants, Part.RIGHT_LEG, frame, stack, buffer, light, overlay, color);
                part(player.leftPants, Part.LEFT_LEG, frame, stack, buffer, light, overlay, color);
            }
        }
    }

    private static void part(ModelPart part, Part type, Frame frame, PoseStack stack, VertexConsumer buffer, int light, int overlay, int color) {
        if (part.visible) PoemSkinMesh.of(part, type).render(part, frame.skin(), frame.weight(), stack, buffer, light, overlay, color);
    }

    public static boolean renderHeld(LivingEntity entity, ItemStack item, ItemDisplayContext display, HumanoidArm arm,
                                     PoseStack stack, MultiBufferSource buffers, int light, ItemInHandRenderer renderer) {
        Context context = context();
        if (context.frame == null || context.entity != entity) return false;
        held(entity, item, display, arm, stack, buffers, light, renderer, context.model, context.frame);
        return true;
    }

    private static void held(LivingEntity entity, ItemStack item, ItemDisplayContext display, HumanoidArm arm,
                             PoseStack stack, MultiBufferSource buffers, int light, ItemInHandRenderer renderer,
                             HumanoidModel<?> model, Frame frame) {
        if (item.isEmpty()) return;
        if (arm == entity.getMainArm() && item.getItem() instanceof PoemOfTheEndItem) {
            weapon(item, frame, model, stack, buffers, light);
            StandalonePoemTrailRenderer.render(frame, model, stack, buffers);
            return;
        }
        ModelPart modelArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        int hand = PoemMotionLibrary.get().joint(arm == HumanoidArm.RIGHT ? "Hand_R" : "Hand_L");
        Matrix4f animated = new Matrix4f(PoemSkinMesh.DCC_TO_MODEL).mul(frame.skin()[hand])
                .mul(PoemSkinMesh.MODEL_TO_DCC).mul(PoemSkinMesh.initial(modelArm));
        Matrix4f transform = interpolate(PoemSkinMesh.current(modelArm), animated, frame.weight());
        stack.pushPose();
        multiply(stack, transform);
        stack.mulPose(Axis.XP.rotationDegrees(-90)); stack.mulPose(Axis.YP.rotationDegrees(180));
        stack.translate(arm == HumanoidArm.LEFT ? -.0625 : .0625, .125, -.625);
        renderer.renderItem(entity, item, display, arm == HumanoidArm.LEFT, stack, buffers, light);
        stack.popPose();
    }

    /** Rotate capes, wings, shoulder pets and head attachments with their corresponding joint. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void renderLayer(RenderLayer layer, PoseStack stack, MultiBufferSource buffers, int light, LivingEntity entity,
                                   float walk, float amount, float partial, float age, float yaw, float pitch) {
        Context context = context();
        if (context.frame == null || context.entity != entity || layer instanceof HumanoidArmorLayer || layer instanceof ItemInHandLayer) {
            layer.render(stack, buffers, light, entity, walk, amount, partial, age, yaw, pitch); return;
        }
        boolean head = layer instanceof CustomHeadLayer;
        int joint = PoemMotionLibrary.get().joint(head ? "Head" : "Chest");
        Matrix4f warp = new Matrix4f(PoemSkinMesh.DCC_TO_MODEL).mul(context.frame.skin()[joint]).mul(PoemSkinMesh.MODEL_TO_DCC);
        if (head) warp.mul(PoemSkinMesh.initial(context.model.head)).mul(PoemSkinMesh.current(context.model.head).invert());
        warp = interpolate(new Matrix4f(), warp, context.frame.weight());
        stack.pushPose();
        try {
            multiply(stack, warp);
            layer.render(stack, buffers, light, entity, walk, amount, partial, age, yaw, pitch);
        } finally { stack.popPose(); }
    }

    private static Matrix4f interpolate(Matrix4f from, Matrix4f to, float weight) {
        return PoemMotionLibrary.blend(new Matrix4f[]{from}, new Matrix4f[]{to}, weight)[0];
    }

    private static void multiply(PoseStack stack, Matrix4f transform) {
        stack.last().pose().mul(transform);
        stack.last().normal().mul(transform.normal(new org.joml.Matrix3f()));
    }

    private static final class Weapon {
        static final WeaponData DATA = PoemMotionLibrary.read(PoemMotionLibrary.DATA_ROOT + "weapon.json", WeaponData.class);
        static final Matrix4f TO_TOOL;
        static {
            float[][] rows = PoemMotionLibrary.get().rig.weapon_to_tool();
            float[] flat = new float[16];
            for (int i = 0; i < 4; i++) System.arraycopy(rows[i], 0, flat, i * 4, 4);
            TO_TOOL = PoemMotionLibrary.matrix(flat);
        }
    }

    public record WeaponData(String texture, float[][] vertices, int[][] faces, float[][] uv) { }

    private static void weapon(ItemStack item, Frame frame, HumanoidModel<?> model, PoseStack stack, MultiBufferSource buffers, int light) {
        PoemMotionLibrary lib = PoemMotionLibrary.get();
        Matrix4f mirror = frame.leftHanded() ? new Matrix4f().scaling(-1, 1, 1) : new Matrix4f();
        Matrix4f animated = new Matrix4f(PoemSkinMesh.DCC_TO_MODEL).mul(mirror).mul(frame.world()[lib.joint("Tool_R")]).mul(Weapon.TO_TOOL);
        ModelPart arm = frame.leftHanded() ? model.leftArm : model.rightArm;
        Matrix4f neutral = PoemSkinMesh.current(arm).mul(PoemSkinMesh.initial(arm).invert())
                .mul(PoemSkinMesh.DCC_TO_MODEL).mul(mirror).mul(lib.bind(lib.joint("Tool_R"))).mul(Weapon.TO_TOOL);
        // Matrix lerp is exactly the previous per-vertex position blend; reuse scratch vectors for this dense item mesh.
        Matrix4f posed = new Matrix4f(stack.last().pose()).mul(frame.weight() >= 1 ? animated : neutral.lerp(animated, frame.weight()));
        emitWeapon(posed, frame.leftHanded(), ItemRenderer.getFoilBufferDirect(buffers,
                PoemWeaponRenderTypes.base(), false, item.hasFoil()), light);
        // Finish the base pass before requesting another buffer: callers may use
        // a BufferSource that flushes the previous builder when its type changes.
        emitWeapon(posed, frame.leftHanded(), buffers.getBuffer(PoemWeaponRenderTypes.glow()), LightTexture.FULL_BRIGHT);
    }

    /** Both material passes use identical posed vertices; this path has no GPU state. */
    public static void emitWeapon(Matrix4f posed, boolean leftHanded, VertexConsumer buffer, int light) {
        Vector3f[] p = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        Vector3f normal = new Vector3f(), edge = new Vector3f();
        int[] indices = new int[4];
        for (int[] face : Weapon.DATA.faces()) {
            for (int k = 0; k < 4; k++) {
                indices[k] = face[leftHanded ? 3 - k : k];
                float[] raw = Weapon.DATA.vertices()[indices[k]];
                posed.transformPosition(raw[0], raw[1], raw[2], p[k]);
            }
            normal.set(p[1]).sub(p[0]).cross(edge.set(p[2]).sub(p[0]));
            if (normal.lengthSquared() < 1e-14f) continue;
            normal.normalize();
            for (int k = 0; k < 4; k++) {
                float[] uv = Weapon.DATA.uv()[indices[k]];
                PoemSkinMesh.emit(buffer, p[k], normal, uv[0], 1 - uv[1], light, OverlayTexture.NO_OVERLAY, -1);
            }
        }
    }

    public static void renderFirstPerson(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Frame frame = StandalonePoemAnimation.frame(mc.player, event.getPartialTick());
        if (frame == null) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer)) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        boolean slim = renderer.getModel().rightArm.getInitialPose().y == 2.5f;
        if (slim && firstPersonSlim == null) firstPersonSlim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        if (!slim && firstPersonWide == null) firstPersonWide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        PlayerModel<AbstractClientPlayer> model = slim ? firstPersonSlim : firstPersonWide;
        model.setupAnim(mc.player, 0, 0, mc.player.tickCount + event.getPartialTick(), 0, 0);
        model.leftSleeve.visible = mc.player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = mc.player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            stack.mulPose(Axis.XP.rotationDegrees(event.getInterpolatedPitch()));
            stack.translate(0, -mc.player.getEyeHeight(), 0);
            float playerScale = PoemBladeTrail.PLAYER_SCALE * mc.player.getScale();
            stack.scale(-playerScale, -playerScale, playerScale);
            stack.translate(0, -1.501, 0);
            if (!mc.player.isInvisible()) {
                body(model, frame, stack, event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(renderer.getTextureLocation(mc.player))),
                        event.getPackedLight(), OverlayTexture.NO_OVERLAY, -1, true);
            }
            weapon(mc.player.getMainHandItem(), frame, model, stack, event.getMultiBufferSource(), event.getPackedLight());
            StandalonePoemTrailRenderer.render(frame, model, stack, event.getMultiBufferSource());
            HumanoidArm offhand = mc.player.getMainArm().getOpposite();
            held(mc.player, mc.player.getOffhandItem(), offhand == HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    offhand, stack, event.getMultiBufferSource(), event.getPackedLight(), mc.gameRenderer.itemInHandRenderer, model, frame);
        } finally { stack.popPose(); }
    }

    private record Context(LivingEntity entity, HumanoidModel<?> model, Frame frame) { }
}
