package com.whitecloud233.herobrine_companion.destructiongod.client.model;

import com.whitecloud233.herobrine_companion.destructiongod.entity.DestructionGodHerobrineEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class DestructionGodHerobrineModel extends PlayerModel<DestructionGodHerobrineEntity> {
    public DestructionGodHerobrineModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(DestructionGodHerobrineEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        float idlePulse = Mth.sin(ageInTicks * 0.08F) * 0.04F;
        this.body.y = -0.5F + idlePulse * 0.5F;
        this.head.y = this.body.y;
        this.hat.y = this.head.y;
        this.jacket.y = this.body.y;
        this.rightArm.y = 2.0F + this.body.y;
        this.leftArm.y = 2.0F + this.body.y;
        this.rightSleeve.y = this.rightArm.y;
        this.leftSleeve.y = this.leftArm.y;
        this.rightLeg.y = 12.0F + this.body.y;
        this.leftLeg.y = 12.0F + this.body.y;
        this.rightPants.y = this.rightLeg.y;
        this.leftPants.y = this.leftLeg.y;

        if (!entity.isCastingSkill()) {
            float phaseBreath = entity.getBossPhase() >= DestructionGodHerobrineEntity.PHASE_3 ? 0.08F : 0.04F;
            this.rightArm.xRot = -0.15F + idlePulse;
            this.leftArm.xRot = -0.18F - idlePulse;
            this.rightArm.zRot = 0.05F + phaseBreath;
            this.leftArm.zRot = -0.05F - phaseBreath;
            this.body.xRot = 0.03F;
            return;
        }

        float duration = Math.max(1.0F, entity.getSkillDuration());
        float progress = Mth.clamp(entity.getSkillTicks() / duration, 0.0F, 1.0F);
        float trigger = entity.getSkillTriggerTick() / duration;
        float windup = smoothWindow(progress, 0.0F, Math.max(0.2F, trigger));
        float release = smoothWindow(progress, Math.max(0.12F, trigger - 0.08F), 1.0F);

        switch (entity.getSkillState()) {
            case DestructionGodHerobrineEntity.SKILL_WORLD_REND -> applyWorldRendPose(windup, release);
            case DestructionGodHerobrineEntity.SKILL_APOCALYPSE_CRACK -> applyApocalypseCrackPose(windup, release);
            case DestructionGodHerobrineEntity.SKILL_WORLD_PEEL -> applyWorldPeelPose(windup, release, ageInTicks);
            case DestructionGodHerobrineEntity.SKILL_WORLD_COLLAPSE -> applyWorldCollapsePose(windup, release, ageInTicks);
            case DestructionGodHerobrineEntity.SKILL_SCYTHE_ICHIMONJI -> applyIchimonjiPose(windup, release);
            case DestructionGodHerobrineEntity.SKILL_SCYTHE_REVERSE_MOON -> applyReverseMoonPose(windup, release, ageInTicks);
            case DestructionGodHerobrineEntity.SKILL_SCYTHE_EXECUTION -> applyExecutionPose(windup, release, ageInTicks);
            case DestructionGodHerobrineEntity.SKILL_SCYTHE_FAULT_SPLIT -> applyFaultSplitPose(windup, release, ageInTicks);
            case DestructionGodHerobrineEntity.SKILL_DESTRUCTION_LIGHTNING -> applyApocalypseCrackPose(windup, release);
            case DestructionGodHerobrineEntity.SKILL_DESTRUCTION_GOD_ORB -> applyDestructionGodOrbPose(windup, release, ageInTicks);
            default -> {
            }
        }

        copyHumanoidProperties();
    }

    private void applyWorldRendPose(float windup, float release) {
        this.body.xRot = 0.30F * windup - 0.16F * release;
        this.body.yRot = -0.62F * windup + 0.86F * release;
        this.head.xRot -= 0.26F * windup;
        this.head.yRot -= 0.26F * windup;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, -2.95F);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, -0.85F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, 0.25F);
        this.leftArm.xRot = Mth.lerp(windup, this.leftArm.xRot, -1.65F);
        this.leftArm.yRot = Mth.lerp(windup, this.leftArm.yRot, 0.55F);
        this.leftArm.zRot = Mth.lerp(windup, this.leftArm.zRot, -0.38F);

        this.rightArm.xRot = Mth.lerp(release, this.rightArm.xRot, 0.82F);
        this.rightArm.yRot = Mth.lerp(release, this.rightArm.yRot, 1.40F);
        this.rightArm.zRot = Mth.lerp(release, this.rightArm.zRot, -1.22F);
        this.leftArm.xRot = Mth.lerp(release, this.leftArm.xRot, -0.72F);
        this.leftArm.yRot = Mth.lerp(release, this.leftArm.yRot, -0.24F);
        this.leftArm.zRot = Mth.lerp(release, this.leftArm.zRot, 0.14F);

        this.rightLeg.xRot = -0.62F * release - 0.16F * windup;
        this.leftLeg.xRot = 0.34F * release + 0.08F * windup;
    }

    private void applyApocalypseCrackPose(float windup, float release) {
        this.body.xRot = -0.08F + 0.12F * windup;
        this.body.yRot = 0.35F * Mth.sin(release * (float) Math.PI);
        this.head.xRot -= 0.10F * windup;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, -1.7F);
        this.leftArm.xRot = Mth.lerp(windup, this.leftArm.xRot, -1.7F);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, -0.90F);
        this.leftArm.yRot = Mth.lerp(windup, this.leftArm.yRot, 0.90F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, 0.70F);
        this.leftArm.zRot = Mth.lerp(windup, this.leftArm.zRot, -0.70F);

        this.rightArm.xRot = Mth.lerp(release, this.rightArm.xRot, -0.35F);
        this.leftArm.xRot = Mth.lerp(release, this.leftArm.xRot, -0.35F);
        this.rightArm.yRot = Mth.lerp(release, this.rightArm.yRot, 0.45F);
        this.leftArm.yRot = Mth.lerp(release, this.leftArm.yRot, -0.45F);
    }

    private void applyWorldPeelPose(float windup, float release, float ageInTicks) {
        float orbit = Mth.sin(ageInTicks * 0.45F) * 0.10F;
        this.body.xRot = -0.15F * windup;
        this.head.xRot -= 0.12F * windup;
        this.body.y -= 1.2F * windup;
        this.head.y = this.body.y;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, -1.85F + orbit);
        this.leftArm.xRot = Mth.lerp(windup, this.leftArm.xRot, -1.05F - orbit);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, -0.15F);
        this.leftArm.yRot = Mth.lerp(windup, this.leftArm.yRot, 0.55F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, 0.28F);
        this.leftArm.zRot = Mth.lerp(windup, this.leftArm.zRot, -0.55F);

        this.rightArm.xRot = Mth.lerp(release, this.rightArm.xRot, -0.20F);
        this.leftArm.xRot = Mth.lerp(release, this.leftArm.xRot, -0.45F);
    }

    private void applyWorldCollapsePose(float windup, float release, float ageInTicks) {
        float tremor = Mth.sin(ageInTicks * 0.9F) * 0.05F;
        this.body.y -= 2.2F * windup;
        this.head.y = this.body.y;
        this.body.xRot = -0.08F;
        this.head.xRot = -0.55F + tremor;
        this.rightArm.xRot = -3.0F + tremor;
        this.leftArm.xRot = -3.0F - tremor;
        this.rightArm.yRot = -0.15F + 0.10F * release;
        this.leftArm.yRot = 0.15F - 0.10F * release;
        this.rightArm.zRot = 0.18F;
        this.leftArm.zRot = -0.18F;
        this.rightLeg.xRot = 0.08F * tremor;
        this.leftLeg.xRot = -0.08F * tremor;
    }

    private void applyIchimonjiPose(float windup, float release) {
        this.body.xRot = 0.08F * windup;
        this.body.yRot = -0.42F * windup + 0.65F * release;
        this.head.xRot -= 0.08F * windup;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, -2.15F);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, -0.48F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, 0.12F);
        this.leftArm.xRot = Mth.lerp(windup, this.leftArm.xRot, -1.05F);
        this.leftArm.yRot = Mth.lerp(windup, this.leftArm.yRot, 0.35F);
        this.leftArm.zRot = Mth.lerp(windup, this.leftArm.zRot, -0.22F);

        this.rightArm.xRot = Mth.lerp(release, this.rightArm.xRot, 0.42F);
        this.rightArm.yRot = Mth.lerp(release, this.rightArm.yRot, 1.18F);
        this.rightArm.zRot = Mth.lerp(release, this.rightArm.zRot, -1.08F);
        this.leftArm.xRot = Mth.lerp(release, this.leftArm.xRot, -0.42F);
        this.leftArm.yRot = Mth.lerp(release, this.leftArm.yRot, -0.12F);

        this.rightLeg.xRot = -0.32F * release;
        this.leftLeg.xRot = 0.20F * release;
    }

    private void applyReverseMoonPose(float windup, float release, float ageInTicks) {
        float pulse = Mth.sin(ageInTicks * 0.55F) * 0.08F;
        this.body.xRot = -0.04F + 0.10F * windup;
        this.body.yRot = 0.52F * windup - 0.35F * release;
        this.head.yRot += 0.18F * windup;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, -1.55F + pulse);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, 0.95F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, 0.45F);
        this.leftArm.xRot = Mth.lerp(windup, this.leftArm.xRot, -0.82F);
        this.leftArm.yRot = Mth.lerp(windup, this.leftArm.yRot, -0.35F);
        this.leftArm.zRot = Mth.lerp(windup, this.leftArm.zRot, -0.42F);

        this.rightArm.xRot = Mth.lerp(release, this.rightArm.xRot, -0.22F);
        this.rightArm.yRot = Mth.lerp(release, this.rightArm.yRot, -1.05F);
        this.rightArm.zRot = Mth.lerp(release, this.rightArm.zRot, -0.82F);
        this.leftArm.xRot = Mth.lerp(release, this.leftArm.xRot, -0.38F);
        this.leftArm.yRot = Mth.lerp(release, this.leftArm.yRot, 0.45F);

        this.rightLeg.xRot = 0.18F + pulse;
        this.leftLeg.xRot = -0.24F - pulse;
    }

    private void applyExecutionPose(float windup, float release, float ageInTicks) {
        float tremor = Mth.sin(ageInTicks * 1.1F) * 0.06F;
        this.body.y -= 1.8F * windup;
        this.head.y = this.body.y;
        this.body.xRot = -0.12F + 0.22F * release;
        this.body.yRot = -0.22F * windup;
        this.head.xRot = -0.38F + tremor;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, -2.85F);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, -0.18F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, 0.08F);
        this.leftArm.xRot = Mth.lerp(windup, this.leftArm.xRot, -1.25F);
        this.leftArm.yRot = Mth.lerp(windup, this.leftArm.yRot, 0.48F);
        this.leftArm.zRot = Mth.lerp(windup, this.leftArm.zRot, -0.25F);

        this.rightArm.xRot = Mth.lerp(release, this.rightArm.xRot, 0.68F + tremor);
        this.rightArm.yRot = Mth.lerp(release, this.rightArm.yRot, 0.62F);
        this.rightArm.zRot = Mth.lerp(release, this.rightArm.zRot, -0.38F);
        this.leftArm.xRot = Mth.lerp(release, this.leftArm.xRot, -0.55F);

        this.rightLeg.xRot = -0.55F * release;
        this.leftLeg.xRot = 0.28F * release;
    }

    private void applyFaultSplitPose(float windup, float release, float ageInTicks) {
        float tremor = Mth.sin(ageInTicks * 0.78F) * 0.05F;
        this.body.y -= 1.1F * windup;
        this.head.y = this.body.y;
        this.body.xRot = 0.14F * windup - 0.12F * release;
        this.body.yRot = -0.48F * windup + 0.82F * release;
        this.head.xRot = -0.18F * windup + tremor * 0.35F;
        this.head.yRot -= 0.18F * windup;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, -3.10F + tremor);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, -0.72F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, 0.18F);
        this.leftArm.xRot = Mth.lerp(windup, this.leftArm.xRot, -1.28F - tremor);
        this.leftArm.yRot = Mth.lerp(windup, this.leftArm.yRot, 0.52F);
        this.leftArm.zRot = Mth.lerp(windup, this.leftArm.zRot, -0.34F);

        this.rightArm.xRot = Mth.lerp(release, this.rightArm.xRot, 0.88F + tremor);
        this.rightArm.yRot = Mth.lerp(release, this.rightArm.yRot, 1.52F);
        this.rightArm.zRot = Mth.lerp(release, this.rightArm.zRot, -1.30F);
        this.leftArm.xRot = Mth.lerp(release, this.leftArm.xRot, -0.66F);
        this.leftArm.yRot = Mth.lerp(release, this.leftArm.yRot, -0.22F);
        this.leftArm.zRot = Mth.lerp(release, this.leftArm.zRot, 0.12F);

        this.rightLeg.xRot = -0.42F * release - 0.10F * windup;
        this.leftLeg.xRot = 0.24F * release + 0.08F * windup;
    }

    private void applyDestructionGodOrbPose(float windup, float release, float ageInTicks) {
        float pulse = Mth.sin(ageInTicks * 0.85F) * 0.08F;
        this.body.y -= 1.35F * windup;
        this.head.y = this.body.y;
        this.body.xRot = -0.10F - 0.18F * windup + 0.12F * release;
        this.body.yRot = -0.28F * windup + 0.40F * release;
        this.head.xRot = -0.22F - 0.18F * windup + pulse * 0.4F;

        this.rightArm.xRot = Mth.lerp(windup, this.rightArm.xRot, -2.80F + pulse);
        this.rightArm.yRot = Mth.lerp(windup, this.rightArm.yRot, -0.18F);
        this.rightArm.zRot = Mth.lerp(windup, this.rightArm.zRot, 0.16F);
        this.leftArm.xRot = Mth.lerp(windup, this.leftArm.xRot, -0.95F - pulse);
        this.leftArm.yRot = Mth.lerp(windup, this.leftArm.yRot, 0.42F);
        this.leftArm.zRot = Mth.lerp(windup, this.leftArm.zRot, -0.35F);

        this.rightArm.xRot = Mth.lerp(release, this.rightArm.xRot, -3.35F);
        this.rightArm.yRot = Mth.lerp(release, this.rightArm.yRot, 0.42F);
        this.rightArm.zRot = Mth.lerp(release, this.rightArm.zRot, -0.12F);
        this.leftArm.xRot = Mth.lerp(release, this.leftArm.xRot, -0.45F);
        this.leftArm.yRot = Mth.lerp(release, this.leftArm.yRot, -0.18F);

        this.rightLeg.xRot = -0.24F * windup;
        this.leftLeg.xRot = 0.16F * release;
    }

    private float smoothWindow(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return smoothStep(Mth.clamp((value - start) / (end - start), 0.0F, 1.0F));
    }

    private float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private void copyHumanoidProperties() {
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
    }
}

