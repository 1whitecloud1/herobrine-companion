package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// [修复] NeoForge 正确的包导入
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

/**
 * NeoForge 1.21.1 适配版
 * 处理 Herobrine 守卫状态下的物理规则干涉 (绝对防爆)
 */
@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.GAME)
public class HeroGuardEventHandler {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;

        // [修复] 1.21 Mojang 映射下，获取位置的方法是 center()
        Vec3 explosionPos = event.getExplosion().center();

        // 1. 范围检测 (32格)
        double searchRadius = 32.0;
        AABB searchBox = new AABB(
                explosionPos.x - searchRadius, explosionPos.y - searchRadius, explosionPos.z - searchRadius,
                explosionPos.x + searchRadius, explosionPos.y + searchRadius, explosionPos.z + searchRadius
        );

        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            // 2. 检查守卫状态 (Action 3)
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {

                BlockPos guardedPos = hero.getInvitedPos();
                double distSqr = guardedPos.distToCenterSqr(explosionPos);

                // 3. 判定神之领域范围 (20格)
                // 只要爆炸源位于守护点 20 格内，抹除伤害
                if (distSqr < 400.0) {
                    event.getAffectedBlocks().clear();
                    event.getAffectedEntities().clear();
                    return;
                }
            }
        }
    }
}