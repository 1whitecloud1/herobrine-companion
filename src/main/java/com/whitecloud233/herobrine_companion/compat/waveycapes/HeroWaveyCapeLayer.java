package com.whitecloud233.herobrine_companion.compat.waveycapes;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

public class HeroWaveyCapeLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {

    private static final int PART_COUNT = 16;

    private ModelPart[] customCape = new ModelPart[PART_COUNT];

    public HeroWaveyCapeLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> renderLayerParent) {
        super(renderLayerParent);
        buildMesh();
    }

    private void buildMesh() {
        customCape = new ModelPart[PART_COUNT];
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        for (int i = 0; i < PART_COUNT; i++) {
            partDefinition.addOrReplaceChild("customCape_" + i,
                    CubeListBuilder.create().texOffs(0, (int) (i * (16f / PART_COUNT))).addBox(-5.0F,
                            i * (16f / PART_COUNT), -1.0F, 10.0F, (16f / PART_COUNT), 1.0F, CubeDeformation.NONE,
                            1.0F, 0.5F),
                    PartPose.offset(0.0F, 0.0F, 0.0F));
        }
        ModelPart modelPart = partDefinition.bake(64, 64);
        for (int i = 0; i < PART_COUNT; i++) {
            this.customCape[i] = modelPart.getChild("customCape_" + i);
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, HeroEntity hero,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (hero.isInvisible()) {
            return;
        }

        ResourceLocation capeTexture = getCapeTexture(hero);
        if (capeTexture == null) {
            return;
        }

        ItemStack chestItem = hero.getItemBySlot(EquipmentSlot.CHEST);
        if (chestItem.is(Items.ELYTRA)) {
            return;
        }

        HeroCapeState state = HeroCapeState.get(hero);
        state.prepare(hero, PART_COUNT);
        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.entityCutoutNoCull(capeTexture));

        if (WaveyCapesRuntime.getCapeStyle() == WaveyCapesRuntime.CapeStyleMode.SMOOTH) {
            renderSmoothCape(poseStack, vertexConsumer, hero, state, partialTick, packedLight);
            return;
        }

        for (int part = 0; part < PART_COUNT; part++) {
            ModelPart model = customCape[part];
            modifyPoseStack(poseStack, hero, state, partialTick, part);
            model.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    private void renderSmoothCape(PoseStack poseStack, VertexConsumer bufferBuilder, HeroEntity hero,
            HeroCapeState state, float partialTick, int packedLight) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f oldPositionMatrix = null;
        for (int part = 0; part < PART_COUNT; part++) {
            modifyPoseStack(poseStack, hero, state, partialTick, part);

            if (oldPositionMatrix == null) {
                oldPositionMatrix = poseStack.last().pose();
            }

            if (part == 0) {
                addTopVertex(bufferBuilder, poseStack.last().pose(), oldPositionMatrix, 0.3F, 0, 0F, -0.3F, 0, -0.06F,
                        part, packedLight);
            }

            if (part == PART_COUNT - 1) {
                addBottomVertex(bufferBuilder, poseStack.last().pose(), poseStack.last().pose(), 0.3F,
                        (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, (part + 1) * (0.96F / PART_COUNT), -0.06F,
                        part, packedLight);
            }

            addLeftVertex(bufferBuilder, poseStack.last().pose(), oldPositionMatrix, -0.3F,
                    (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, part * (0.96F / PART_COUNT), -0.06F, part,
                    packedLight);

            addRightVertex(bufferBuilder, poseStack.last().pose(), oldPositionMatrix, 0.3F,
                    (part + 1) * (0.96F / PART_COUNT), 0F, 0.3F, part * (0.96F / PART_COUNT), -0.06F, part,
                    packedLight);

            addBackVertex(bufferBuilder, poseStack.last().pose(), oldPositionMatrix, 0.3F,
                    (part + 1) * (0.96F / PART_COUNT), -0.06F, -0.3F, part * (0.96F / PART_COUNT), -0.06F, part,
                    packedLight);

            addFrontVertex(bufferBuilder, oldPositionMatrix, poseStack.last().pose(), 0.3F,
                    (part + 1) * (0.96F / PART_COUNT), 0F, -0.3F, part * (0.96F / PART_COUNT), 0F, part,
                    packedLight);

            oldPositionMatrix = new Matrix4f(poseStack.last().pose());
            poseStack.popPose();
        }
    }

    private void modifyPoseStack(PoseStack poseStack, HeroEntity hero, HeroCapeState state, float partialTick, int part) {
        poseStack.pushPose();
        if (WaveyCapesRuntime.getCapeMovement() != WaveyCapesRuntime.CapeMovementMode.VANILLA
                && state.getSimulation() != null) {
            modifyPoseStackSimulation(poseStack, hero, state, partialTick, part);
            return;
        }
        modifyPoseStackVanilla(poseStack, hero, state, partialTick, part);
    }

    private void modifyPoseStackSimulation(PoseStack poseStack, HeroEntity hero, HeroCapeState state, float partialTick,
            int part) {
        Object simulation = state.getSimulation();
        if (WaveyCapesRuntime.isSimulationEmpty(simulation)) {
            modifyPoseStackVanilla(poseStack, hero, state, partialTick, part);
            return;
        }

        poseStack.translate(0.0D, 0.0D, 0.125D);

        WaveyCapesRuntime.Point root = WaveyCapesRuntime.getPoint(simulation, 0, partialTick);
        WaveyCapesRuntime.Point current = WaveyCapesRuntime.getPoint(simulation, part, partialTick);

        float x = current.x() - root.x();
        if (x > 0) {
            x = 0;
        }
        float y = root.y() - part - current.y();
        float z = root.z() - current.z();

        float partRotation = getRotation(partialTick, part, simulation);
        float height = 0.0F;
        if (hero.isCrouching()) {
            height += 25.0F;
            poseStack.translate(0, 0.15F, 0);
        }

        float naturalWindSwing = getNaturalWindSwing(part, hero.isUnderWater());
        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + height + naturalWindSwing));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-z / PART_COUNT, y / PART_COUNT, x / PART_COUNT);
        poseStack.translate(0, (0.48 / 16), -(0.48 / 16));
        poseStack.translate(0, part * 1f / PART_COUNT, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(-partRotation));
        poseStack.translate(0, -part * 1f / PART_COUNT, 0);
        poseStack.translate(0, -(0.48 / 16), (0.48 / 16));
    }

    private float getRotation(float partialTick, int part, Object simulation) {
        if (part >= PART_COUNT - 1) {
            return part > 0 ? getRotation(partialTick, part - 1, simulation) : 0.0F;
        }
        WaveyCapesRuntime.Point current = WaveyCapesRuntime.getPoint(simulation, part, partialTick);
        WaveyCapesRuntime.Point next = WaveyCapesRuntime.getPoint(simulation, part + 1, partialTick);
        float angleX = next.x() - current.x();
        float angleY = next.y() - current.y();
        return (float) (Math.toDegrees(Math.atan2(angleX, angleY)) + 180.0D);
    }

    private void modifyPoseStackVanilla(PoseStack poseStack, HeroEntity hero, HeroCapeState state, float partialTick,
            int part) {
        poseStack.translate(0.0D, 0.0D, 0.125D);
        double d = state.getXCloak(partialTick) - Mth.lerp(partialTick, hero.xo, hero.getX());
        double e = state.getYCloak(partialTick) - Mth.lerp(partialTick, hero.yo, hero.getY());
        double m = state.getZCloak(partialTick) - Mth.lerp(partialTick, hero.zo, hero.getZ());
        float bodyRotation = Mth.rotLerp(partialTick, hero.yBodyRotO, hero.yBodyRot);
        double o = Mth.sin(bodyRotation * 0.017453292F);
        double p = -Mth.cos(bodyRotation * 0.017453292F);
        float height = (float) e * 10.0F;
        height = Mth.clamp(height, -6.0F, 32.0F);
        float swing = (float) (d * o + m * p) * easeOutSine(1.0F / PART_COUNT * part) * 100;
        swing = Mth.clamp(swing, 0.0F, 150.0F * easeOutSine(1F / PART_COUNT * part));
        float sidewaysRotationOffset = (float) (d * p - m * o) * 100.0F;
        sidewaysRotationOffset = Mth.clamp(sidewaysRotationOffset, -20.0F, 20.0F);
        if (hero.isCrouching()) {
            height += 25.0F;
            poseStack.translate(0, 0.15F, 0);
        }

        float naturalWindSwing = getNaturalWindSwing(part, hero.isUnderWater());
        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + swing / 2.0F + height + naturalWindSwing));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sidewaysRotationOffset / 2.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - sidewaysRotationOffset / 2.0F));
    }

    private float getNaturalWindSwing(int part, boolean underwater) {
        long highlightedPart = (System.currentTimeMillis() / (underwater ? 9 : 3)) % 360;
        float relativePart = (float) (part + 1) / PART_COUNT;
        if (WaveyCapesRuntime.getWindMode() == WaveyCapesRuntime.WindModeValue.WAVES) {
            return (float) (Math.sin(Math.toRadians(relativePart * 360 - highlightedPart)) * 3);
        }
        return 0;
    }

    private ResourceLocation getCapeTexture(HeroEntity hero) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        if (hero.getOwnerUUID() != null) {
            Player owner = minecraft.level.getPlayerByUUID(hero.getOwnerUUID());
            if (owner instanceof AbstractClientPlayer clientPlayer) {
                ResourceLocation capeTexture = getOwnerCapeTexture(clientPlayer);
                if (capeTexture != null) {
                    return capeTexture;
                }
            }
        }

        if (hero.getOwnerUUID() == null || minecraft.getConnection() == null) {
            return null;
        }

        PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(hero.getOwnerUUID());
        if (playerInfo == null || playerInfo.getSkin().capeTexture() == null) {
            return null;
        }
        return playerInfo.getSkin().capeTexture();
    }

    private ResourceLocation getOwnerCapeTexture(AbstractClientPlayer player) {
        ResourceLocation capeTexture = player.getSkin().capeTexture();
        if (capeTexture == null || !player.isModelPartShown(PlayerModelPart.CAPE)) {
            return null;
        }
        return capeTexture;
    }

    private static void addBackVertex(VertexConsumer bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1,
            float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float swapValue;
        Matrix4f swapMatrix;
        if (x1 < x2) {
            swapValue = x1;
            x1 = x2;
            x2 = swapValue;
        }

        if (y1 < y2) {
            swapValue = y1;
            y1 = y2;
            y2 = swapValue;

            swapMatrix = matrix;
            matrix = oldMatrix;
            oldMatrix = swapMatrix;
        }

        float minU = .015625F;
        float maxU = .171875F;
        float minV = .03125F;
        float maxV = .53125F;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        writeVertex(bufferBuilder, oldMatrix, x1, y2, z1, maxU, minV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, oldMatrix, x2, y2, z1, minU, minV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x2, y1, z2, minU, maxV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x1, y1, z2, maxU, maxV, light, 1.0F, 0.0F, 0.0F);
    }

    private static void addFrontVertex(VertexConsumer bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1,
            float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float swapValue;
        Matrix4f swapMatrix;
        if (x1 < x2) {
            swapValue = x1;
            x1 = x2;
            x2 = swapValue;
        }

        if (y1 < y2) {
            swapValue = y1;
            y1 = y2;
            y2 = swapValue;

            swapMatrix = matrix;
            matrix = oldMatrix;
            oldMatrix = swapMatrix;
        }

        float minU = .1875F;
        float maxU = .34375F;
        float minV = .03125F;
        float maxV = .53125F;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        writeVertex(bufferBuilder, oldMatrix, x1, y1, z1, minU, maxV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, oldMatrix, x2, y1, z1, maxU, maxV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x2, y2, z2, maxU, minV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x1, y2, z2, minU, minV, light, 1.0F, 0.0F, 0.0F);
    }

    private static void addLeftVertex(VertexConsumer bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1,
            float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float swapValue;
        if (x1 < x2) {
            swapValue = x1;
            x1 = x2;
            x2 = swapValue;
        }

        if (y1 < y2) {
            swapValue = y1;
            y1 = y2;
            y2 = swapValue;
        }

        float minU = 0;
        float maxU = .015625F;
        float minV = .03125F;
        float maxV = .53125F;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        writeVertex(bufferBuilder, matrix, x2, y1, z1, minU, maxV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x2, y1, z2, maxU, maxV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, oldMatrix, x2, y2, z2, maxU, minV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, oldMatrix, x2, y2, z1, minU, minV, light, 1.0F, 0.0F, 0.0F);
    }

    private static void addRightVertex(VertexConsumer bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1,
            float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float swapValue;
        if (x1 < x2) {
            swapValue = x1;
            x1 = x2;
            x2 = swapValue;
        }

        if (y1 < y2) {
            swapValue = y1;
            y1 = y2;
            y2 = swapValue;
        }

        float minU = .171875F;
        float maxU = .1875F;
        float minV = .03125F;
        float maxV = .53125F;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        writeVertex(bufferBuilder, matrix, x2, y1, z2, minU, maxV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x2, y1, z1, maxU, maxV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, oldMatrix, x2, y2, z1, maxU, minV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, oldMatrix, x2, y2, z2, minU, minV, light, 1.0F, 0.0F, 0.0F);
    }

    private static void addBottomVertex(VertexConsumer bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1,
            float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float swapValue;
        if (x1 < x2) {
            swapValue = x1;
            x1 = x2;
            x2 = swapValue;
        }

        if (y1 < y2) {
            swapValue = y1;
            y1 = y2;
            y2 = swapValue;
        }

        float minU = .171875F;
        float maxU = .328125F;
        float minV = 0;
        float maxV = .03125F;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        writeVertex(bufferBuilder, oldMatrix, x1, y2, z2, maxU, minV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, oldMatrix, x2, y2, z2, minU, minV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x2, y1, z1, minU, maxV, light, 1.0F, 0.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x1, y1, z1, maxU, maxV, light, 1.0F, 0.0F, 0.0F);
    }

    private static void addTopVertex(VertexConsumer bufferBuilder, Matrix4f matrix, Matrix4f oldMatrix, float x1,
            float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float swapValue;
        if (x1 < x2) {
            swapValue = x1;
            x1 = x2;
            x2 = swapValue;
        }

        if (y1 < y2) {
            swapValue = y1;
            y1 = y2;
            y2 = swapValue;
        }

        float minU = .015625F;
        float maxU = .171875F;
        float minV = 0;
        float maxV = .03125F;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        writeVertex(bufferBuilder, oldMatrix, x1, y2, z1, maxU, maxV, light, 0.0F, 1.0F, 0.0F);
        writeVertex(bufferBuilder, oldMatrix, x2, y2, z1, minU, maxV, light, 0.0F, 1.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x2, y1, z2, minU, minV, light, 0.0F, 1.0F, 0.0F);
        writeVertex(bufferBuilder, matrix, x1, y1, z2, maxU, minV, light, 0.0F, 1.0F, 0.0F);
    }

    private static float easeOutSine(float x) {
        return Mth.sin((x * Mth.PI) / 2f);
    }

    private static void writeVertex(VertexConsumer bufferBuilder, Matrix4f matrix, float x, float y, float z, float u,
            float v, int light, float normalX, float normalY, float normalZ) {
        bufferBuilder.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(normalX, normalY, normalZ);
    }
}
