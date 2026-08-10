package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 给 Hero 的标准 biped 骨架动态补上夜幕爪/刺轮武器专用关节（Claw_R/Claw_L/wheel）。
 *
 * <p>原因：爪/刺轮的 EFN 动画绑定专用骨架（weapon/nf_claw、weapon/thornwheel），其武器模型与
 * 命中框挂在 Claw_R/Claw_L/wheel 关节上；Hero 的 {@link Armature}（HumanoidArmature）没有这些
 * 关节 → 武器无法绑定（模型脱节）、命中框缺失（技能无伤害/不完整）。同名补上后，动画 clip 与
 * 命中框按名字自动绑定，无需 clip 重映射。
 *
 * <p>关节挂在手部工具关节下（Claw_R/wheel → Tool_R，Claw_L → Tool_L），跟随持物手。
 * 需同步扩容 Armature 内部 private final 结构：jointCount / jointByName / jointById / poseMatrices。
 */
final class HeroNightfallArmatureJoints {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HeroNightfallArmatureJoints() {
    }

    static void addNightfallWeaponJoints(Armature armature) {
        if (armature == null) {
            LOGGER.warn("[HeroEFDebug] addNightfallWeaponJoints skipped: armature is null");
            return;
        }
        if (armature.hasJoint("Claw_R")) {
            LOGGER.info("[HeroEFDebug] addNightfallWeaponJoints skipped: Claw_R already present");
            return;
        }
        Joint toolR = armature.searchJointByName("Tool_R");
        Joint toolL = armature.searchJointByName("Tool_L");
        if (toolR == null || toolL == null) {
            LOGGER.warn("[HeroEFDebug] addNightfallWeaponJoints skipped: Tool_R/L not found (toolR={},toolL={})",
                    toolR != null, toolL != null);
            return;
        }

        try {
            int oldCount = (Integer) field("jointCount").get(armature);
            Joint clawR = new Joint("Claw_R", oldCount, new OpenMatrix4f());
            Joint clawL = new Joint("Claw_L", oldCount + 1, new OpenMatrix4f());
            Joint wheel = new Joint("wheel", oldCount + 2, new OpenMatrix4f());

            toolR.addSubJoints(clawR, wheel);
            toolL.addSubJoints(clawL);

            field("jointCount").setInt(armature, oldCount + 3);

            @SuppressWarnings("unchecked")
            Map<String, Joint> byName = (Map<String, Joint>) field("jointByName").get(armature);
            byName.put("Claw_R", clawR);
            byName.put("Claw_L", clawL);
            byName.put("wheel", wheel);

            @SuppressWarnings("unchecked")
            Int2ObjectMap<Joint> byId = (Int2ObjectMap<Joint>) field("jointById").get(armature);
            byId.put(clawR.getId(), clawR);
            byId.put(clawL.getId(), clawL);
            byId.put(wheel.getId(), wheel);

            // 渲染矩阵缓存按 jointCount 分配，随新增关节扩容，否则按 id 索引越界
            field("poseMatrices").set(armature, OpenMatrix4f.allocateMatrixArray(oldCount + 3));

            LOGGER.info("[HeroEFDebug] Added Claw_R/Claw_L/wheel joints to Hero nightfall armature (oldCount={})", oldCount);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("Failed to add nightfall weapon joints to Hero armature", exception);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = Armature.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
