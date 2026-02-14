package com.whitecloud233.herobrine_companion.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;

public class HeroDragonModel extends EntityModel<EnderDragon> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero_dragon"), "main");

    private final ModelPart body;
    private final ModelPart neck5;
    private final ModelPart neck4;
    private final ModelPart neck3;
    private final ModelPart neck2;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart wing;   // 左翼
    private final ModelPart wingtip;
    private final ModelPart wing1;  // 右翼
    private final ModelPart wingtip1;
    private final ModelPart rearleg; // 左后腿
    private final ModelPart rearlegtip;
    private final ModelPart rearfoot;
    private final ModelPart rearleg1; // 右后腿
    private final ModelPart rearlegtip1;
    private final ModelPart rearfoot1;
    private final ModelPart frontleg; // 左前腿
    private final ModelPart frontlegtip;
    private final ModelPart frontfoot;
    private final ModelPart frontleg1; // 右前腿
    private final ModelPart frontlegtip1;
    private final ModelPart frontfoot1;
    private final ModelPart tail;
    private final ModelPart tail2;
    private final ModelPart tail3;
    private final ModelPart tail4;
    private final ModelPart tail5;
    private final ModelPart tail6;
    private final ModelPart tail7;
    private final ModelPart tail8;
    private final ModelPart tail9;
    private final ModelPart tail10;
    private final ModelPart tail11;
    private final ModelPart tail12;
    
    private final ModelPart root;

    public HeroDragonModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck5 = this.body.getChild("neck5");
        this.neck4 = this.neck5.getChild("neck4");
        this.neck3 = this.neck4.getChild("neck3");
        this.neck2 = this.neck3.getChild("neck2");
        this.neck = this.neck2.getChild("neck");
        this.head = this.neck.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.wing = this.body.getChild("wing");
        this.wingtip = this.wing.getChild("wingtip");
        this.wing1 = this.body.getChild("wing1");
        this.wingtip1 = this.wing1.getChild("wingtip1");
        this.rearleg = this.body.getChild("rearleg");
        this.rearlegtip = this.rearleg.getChild("rearlegtip");
        this.rearfoot = this.rearlegtip.getChild("rearfoot");
        this.rearleg1 = this.body.getChild("rearleg1");
        this.rearlegtip1 = this.rearleg1.getChild("rearlegtip1");
        this.rearfoot1 = this.rearlegtip1.getChild("rearfoot1");
        this.frontleg = this.body.getChild("frontleg");
        this.frontlegtip = this.frontleg.getChild("frontlegtip");
        this.frontfoot = this.frontlegtip.getChild("frontfoot");
        this.frontleg1 = this.body.getChild("frontleg1");
        this.frontlegtip1 = this.frontleg1.getChild("frontlegtip1");
        this.frontfoot1 = this.frontlegtip1.getChild("frontfoot1");
        this.tail = this.body.getChild("tail");
        this.tail2 = this.tail.getChild("tail2");
        this.tail3 = this.tail2.getChild("tail3");
        this.tail4 = this.tail3.getChild("tail4");
        this.tail5 = this.tail4.getChild("tail5");
        this.tail6 = this.tail5.getChild("tail6");
        this.tail7 = this.tail6.getChild("tail7");
        this.tail8 = this.tail7.getChild("tail8");
        this.tail9 = this.tail8.getChild("tail9");
        this.tail10 = this.tail9.getChild("tail10");
        this.tail11 = this.tail10.getChild("tail11");
        this.tail12 = this.tail11.getChild("tail12");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 224).addBox(-12.0F, 0.0F, -16.0F, 24.0F, 24.0F, 64.0F, new CubeDeformation(0.0F))
        .texOffs(184, 317).addBox(-1.0F, -6.0F, -10.0F, 2.0F, 6.0F, 12.0F, new CubeDeformation(0.0F))
        .texOffs(320, 148).addBox(-1.0F, -6.0F, 10.0F, 2.0F, 6.0F, 12.0F, new CubeDeformation(0.0F))
        .texOffs(320, 166).addBox(-1.0F, -6.0F, 30.0F, 2.0F, 6.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 8.0F));

        PartDefinition neck5 = body.addOrReplaceChild("neck5", CubeListBuilder.create().texOffs(232, 292).addBox(-5.0F, -5.0F, -10.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(320, 322).addBox(-1.0F, -9.0F, -8.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.0F, -16.0F));

        PartDefinition neck4 = neck5.addOrReplaceChild("neck4", CubeListBuilder.create().texOffs(288, 272).addBox(-5.0F, -5.0F, -10.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(320, 312).addBox(-1.0F, -9.0F, -8.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -10.0F));

        PartDefinition neck3 = neck4.addOrReplaceChild("neck3", CubeListBuilder.create().texOffs(288, 252).addBox(-5.0F, -5.0F, -10.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(320, 194).addBox(-1.0F, -9.0F, -8.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -10.0F));

        PartDefinition neck2 = neck3.addOrReplaceChild("neck2", CubeListBuilder.create().texOffs(288, 232).addBox(-5.0F, -5.0F, -10.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(320, 184).addBox(-1.0F, -9.0F, -8.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -10.0F));

        PartDefinition neck = neck2.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(288, 212).addBox(-5.0F, -5.0F, -10.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(216, 297).addBox(-1.0F, -9.0F, -8.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -10.0F));

        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(176, 256).addBox(-6.0F, -1.0F, -30.0F, 12.0F, 5.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(176, 224).addBox(-8.0F, -8.0F, -16.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
        .texOffs(224, 204).addBox(-5.0F, -12.0F, -10.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(328, 284).addBox(-5.0F, -3.0F, -28.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(224, 214).addBox(3.0F, -12.0F, -10.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
        .texOffs(64, 332).addBox(3.0F, -3.0F, -28.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, -8.0F)); // 【修正】缩进 2 个单位

        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(176, 277).addBox(-6.0F, 0.0F, -15.0F, 12.0F, 4.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, -15.0F));

        PartDefinition wing = body.addOrReplaceChild("wing", CubeListBuilder.create().texOffs(224, 0).addBox(0.0F, -4.0F, -4.0F, 56.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(0, 0).addBox(0.0F, 0.0F, 2.0F, 56.0F, 0.0F, 56.0F, new CubeDeformation(0.0F)), PartPose.offset(12.0F, 1.0F, -6.0F));

        PartDefinition wingtip = wing.addOrReplaceChild("wingtip", CubeListBuilder.create().texOffs(224, 32).addBox(0.0F, -2.0F, -2.0F, 56.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(0, 56).addBox(0.0F, 0.0F, 2.0F, 56.0F, 0.0F, 56.0F, new CubeDeformation(0.0F)), PartPose.offset(56.0F, 0.0F, -2.0F));

        PartDefinition wing1 = body.addOrReplaceChild("wing1", CubeListBuilder.create().texOffs(224, 16).addBox(-56.0F, -4.0F, -4.0F, 56.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(0, 112).addBox(-56.0F, 0.0F, 2.0F, 56.0F, 0.0F, 56.0F, new CubeDeformation(0.0F)), PartPose.offset(-12.0F, 1.0F, -6.0F));

        PartDefinition wingtip1 = wing1.addOrReplaceChild("wingtip1", CubeListBuilder.create().texOffs(224, 40).addBox(-56.0F, -2.0F, -2.0F, 56.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
        .texOffs(0, 168).addBox(-56.0F, 0.0F, 2.0F, 56.0F, 0.0F, 56.0F, new CubeDeformation(0.0F)), PartPose.offset(-56.0F, 0.0F, -2.0F));

        PartDefinition rearleg = body.addOrReplaceChild("rearleg", CubeListBuilder.create().texOffs(224, 108).addBox(-8.0F, -4.0F, -8.0F, 16.0F, 32.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(-16.0F, 12.0F, 34.0F));

        PartDefinition rearlegtip = rearleg.addOrReplaceChild("rearlegtip", CubeListBuilder.create().texOffs(240, 204).addBox(-6.0F, -2.0F, 0.0F, 12.0F, 32.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 30.0F, -6.0F));

        PartDefinition rearfoot = rearlegtip.addOrReplaceChild("rearfoot", CubeListBuilder.create().texOffs(224, 48).addBox(-9.0F, 0.0F, -20.0F, 18.0F, 6.0F, 24.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 26.0F, 8.0F));

        PartDefinition rearleg1 = body.addOrReplaceChild("rearleg1", CubeListBuilder.create().texOffs(224, 156).addBox(-8.0F, -4.0F, -8.0F, 16.0F, 32.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(16.0F, 12.0F, 34.0F));

        PartDefinition rearlegtip1 = rearleg1.addOrReplaceChild("rearlegtip1", CubeListBuilder.create().texOffs(240, 248).addBox(-6.0F, -2.0F, 0.0F, 12.0F, 32.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 30.0F, -6.0F));

        PartDefinition rearfoot1 = rearlegtip1.addOrReplaceChild("rearfoot1", CubeListBuilder.create().texOffs(224, 78).addBox(-9.0F, 0.0F, -20.0F, 18.0F, 6.0F, 24.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 26.0F, 8.0F));

        PartDefinition frontleg = body.addOrReplaceChild("frontleg", CubeListBuilder.create().texOffs(288, 148).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 24.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-12.0F, 16.0F, -6.0F));

        PartDefinition frontlegtip = frontleg.addOrReplaceChild("frontlegtip", CubeListBuilder.create().texOffs(296, 312).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 24.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, 0.0F));

        PartDefinition frontfoot = frontlegtip.addOrReplaceChild("frontfoot", CubeListBuilder.create().texOffs(288, 108).addBox(-4.0F, 0.0F, -12.0F, 8.0F, 4.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.0F, -1.0F));

        PartDefinition frontleg1 = body.addOrReplaceChild("frontleg1", CubeListBuilder.create().texOffs(288, 180).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 24.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(12.0F, 16.0F, -6.0F));

        PartDefinition frontlegtip1 = frontleg1.addOrReplaceChild("frontlegtip1", CubeListBuilder.create().texOffs(160, 317).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 24.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, 0.0F));

        PartDefinition frontfoot1 = frontlegtip1.addOrReplaceChild("frontfoot1", CubeListBuilder.create().texOffs(288, 128).addBox(-4.0F, 0.0F, -12.0F, 8.0F, 4.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.0F, -1.0F));

        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(272, 292).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(328, 204).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, 48.0F));

        PartDefinition tail2 = tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(176, 297).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(328, 214).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(308, 48).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(328, 224).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail4 = tail3.addOrReplaceChild("tail4", CubeListBuilder.create().texOffs(308, 68).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(328, 234).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail5 = tail4.addOrReplaceChild("tail5", CubeListBuilder.create().texOffs(308, 88).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(328, 244).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail6 = tail5.addOrReplaceChild("tail6", CubeListBuilder.create().texOffs(0, 312).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(328, 254).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail7 = tail6.addOrReplaceChild("tail7", CubeListBuilder.create().texOffs(40, 312).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(328, 264).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail8 = tail7.addOrReplaceChild("tail8", CubeListBuilder.create().texOffs(80, 312).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(328, 274).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail9 = tail8.addOrReplaceChild("tail9", CubeListBuilder.create().texOffs(120, 312).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(0, 332).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail10 = tail9.addOrReplaceChild("tail10", CubeListBuilder.create().texOffs(216, 312).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(16, 332).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail11 = tail10.addOrReplaceChild("tail11", CubeListBuilder.create().texOffs(256, 312).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(32, 332).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        PartDefinition tail12 = tail11.addOrReplaceChild("tail12", CubeListBuilder.create().texOffs(312, 292).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
        .texOffs(48, 332).addBox(-1.0F, -9.0F, 2.0F, 2.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));

        // 恢复原始 UV 数据
        return LayerDefinition.create(meshdefinition, 512, 512);
    }

    @Override
    public void setupAnim(EnderDragon entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // 基础头部旋转
        this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);
        this.head.xRot = headPitch * ((float)Math.PI / 180F);
        
        // 呼吸动画
        float breatheSpeed = 0.1F;
        float breatheAmp = 0.05F;
        float breathe = (float)Math.sin(ageInTicks * breatheSpeed) * breatheAmp;
        
        this.body.y = 4.0F + breathe * 2.0F;
        this.body.xRot = breathe * 0.5F;

        // 脖子联动
        float neckBaseRot = this.head.yRot * 0.5F;
        this.neck5.yRot = neckBaseRot * 0.2F;
        this.neck4.yRot = neckBaseRot * 0.4F;
        this.neck3.yRot = neckBaseRot * 0.6F;
        this.neck2.yRot = neckBaseRot * 0.8F;
        this.neck.yRot = neckBaseRot;
        
        float neckPitch = this.head.xRot * 0.5F;
        // 脖子上下呼吸波动
        float neckBreathe = (float)Math.cos(ageInTicks * breatheSpeed) * 0.05F; 
        
        this.neck5.xRot = neckPitch * 0.2F + neckBreathe * 0.2F;
        this.neck4.xRot = neckPitch * 0.4F + neckBreathe * 0.4F;
        this.neck3.xRot = neckPitch * 0.6F + neckBreathe * 0.6F;
        this.neck2.xRot = neckPitch * 0.8F + neckBreathe * 0.8F;
        this.neck.xRot = neckPitch + neckBreathe;

        if (entity.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.SITTING_SCANNING) {
             // 飞行状态
             float flapSpeed = 0.2F;
             float flapAmp = 0.5F;
             float flap = (float) (Math.sin(ageInTicks * flapSpeed) * flapAmp);
             
             this.wing.zRot = flap;
             this.wing1.zRot = -flap;
             this.wingtip.zRot = -flap * 0.5F;
             this.wingtip1.zRot = flap * 0.5F;
             
             // 尾巴波浪
             float tailSpeed = 0.1F;
             float tailAmp = 0.2F;
             float tailVerticalAmp = 0.1F;
             
             ModelPart[] tails = {tail, tail2, tail3, tail4, tail5, tail6, tail7, tail8, tail9, tail10, tail11, tail12};
             for (int i = 0; i < tails.length; i++) {
                 float phase = i * 0.2F;
                 tails[i].yRot = (float)Math.sin(ageInTicks * tailSpeed - phase) * tailAmp;
                 tails[i].xRot = (float)Math.cos(ageInTicks * tailSpeed - phase) * tailVerticalAmp;
             }
             
             this.frontleg.xRot = 0.5F + breathe;
             this.frontleg1.xRot = 0.5F + breathe;
             this.rearleg.xRot = 0.8F + breathe;
             this.rearleg1.xRot = 0.8F + breathe;
             
        } else {
             // 臣服/趴下状态
             float wingFoldY = 0.2F;
             float wingFoldZ = -0.5F; // 内翼大幅向上

             // 左翼
             this.wing.yRot = wingFoldY;
             this.wing.zRot = wingFoldZ;
             this.wing.xRot = 0.0F;

             this.wingtip.zRot = 2.0F; // 外翼大幅向下
             this.wingtip.yRot = -0.5F;
             this.wingtip.xRot = 0.0F;
             
             // 右翼 (镜像)
             this.wing1.yRot = -wingFoldY;
             this.wing1.zRot = 0.5F; // 右边 Z 轴相反
             this.wing1.xRot = 0.0F;

             this.wingtip1.zRot = -2.0F;
             this.wingtip1.yRot = 0.5F;
             this.wingtip1.xRot = 0.0F;
             
             // 腿部弯曲 (趴下优化)
             float frontThighRot = -1.3F;
             float frontCalfRot = 1.5F;   
             float frontFootRot = -0.2F;  

             this.frontleg.xRot = frontThighRot;
             this.frontleg1.xRot = frontThighRot;

             this.frontlegtip.xRot = frontCalfRot;
             this.frontlegtip1.xRot = frontCalfRot;

             this.frontfoot.xRot = frontFootRot;
             this.frontfoot1.xRot = frontFootRot;
             
             float rearThighRot = -0.8F; 
             float rearCalfRot = 1.6F;
             float rearFootRot = -0.8F;  

             this.rearleg.xRot = rearThighRot;
             this.rearleg1.xRot = rearThighRot;

             this.rearlegtip.xRot = rearCalfRot;
             this.rearlegtip1.xRot = rearCalfRot;

             this.rearfoot.xRot = rearFootRot;
             this.rearfoot1.xRot = rearFootRot;
             
             this.body.y = 18.0F + breathe; 
             
             this.head.xRot += 0.3F + breathe * 0.2F; 
             this.head.y = 5.0F + breathe;
             
             // 尾巴微动
             float tailIdleSpeed = 0.05F;
             float tailIdleAmpY = 0.05F; 
             float tailIdleAmpX = 0.08F; 
             
             ModelPart[] tails = {tail, tail2, tail3, tail4, tail5, tail6, tail7, tail8, tail9, tail10, tail11, tail12};
             for (int i = 0; i < tails.length; i++) {
                 float phase = i * 0.3F; 
                 tails[i].yRot = (float)Math.sin(ageInTicks * tailIdleSpeed - phase) * tailIdleAmpY;
                 tails[i].xRot = 0.1F + (float)Math.sin(ageInTicks * tailIdleSpeed * 1.5F - phase) * tailIdleAmpX;
             }
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        body.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}