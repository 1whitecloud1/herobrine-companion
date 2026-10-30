package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.family.HerobrineFamilySummonManager;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroCombatPower;
import com.whitecloud233.modid.herobrine_companion.entity.gift.HeroGiftReturnService;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDimensionHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroLogic;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroLifecycleHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public class HeroServerTick {

    public static boolean tickBehavior(HeroEntity hero) {
        if (hero.level().isClientSide) return true;
        // 试炼擂台不运行陪伴 AI，但本体仍须参与唯一性检查。
        if (hero.isAlive() && !hero.isRemoved()) HeroBrain.ACTIVE_HEROES.add(hero);
        if (hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            if (hero.tickCount == 1 || hero.tickCount % 20 == 0) HeroLifecycleHandler.checkUniqueness(hero);
            if (hero.isRemoved() || !maintainPersistence(hero, (ServerLevel) hero.level())) return false;
            HeroDimensionHandler.handleVoidProtection(hero);
            return true;
        }

        HeroLogic.tick(hero);
        if (hero.isRemoved()) return false;
        HeroGiftReturnService.tick(hero);
        hero.holdForGiftGesture();
        if (hero.isAlive()) {
            HeroBrain brain = hero.getHeroBrain();
            brain.tick();
            hero.getHeroAgent().tick(hero);
            hero.getHeroAgent().tickTasks(hero);
            if (hero.getMindState() != brain.getState()) hero.setMindState(brain.getState());
            if (hero.level() instanceof ServerLevel serverLevel) {
                return handleTick(hero, serverLevel);
            }
        }
        return true;
    }

    public static boolean handleTick(HeroEntity hero, ServerLevel serverLevel) {
        if (!maintainPersistence(hero, serverLevel)) return false;

        HeroCombatPower.applyTo(hero);
        HerobrineFamilySummonManager.serverTick(hero, serverLevel);
        return true;
    }

    private static boolean maintainPersistence(HeroEntity hero, ServerLevel serverLevel) {
        if (hero.isRemoved() || !hero.isAlive()) return false;
        UUID ownerUUID = hero.getOwnerUUID();

        // 没主人的实体不管它
        if (ownerUUID == null) return true;

        HeroWorldData data = HeroWorldData.get(serverLevel);

        // 必须先确认本体身份，再写任何装备备份。重复实体退出前按同一规则交接数据。
        int checkInterval = hero.tickCount < 200 ? 10 : 100;
        if (hero.tickCount % checkInterval == 0 || hero.tickCount % 20 == 0) {
            HeroEntity activeHero = HeroLifecycleHandler.findActiveHero(serverLevel, ownerUUID);
            if (activeHero != null && activeHero != hero) {
                HeroStateManager.syncEntityToEntity(hero, activeHero);
                hero.discard();
                return false;
            }
            data.setActiveHeroUUID(ownerUUID, hero.getUUID());
            data.setLastKnownHeroPos(ownerUUID, GlobalPos.of(hero.level().dimension(), hero.blockPosition()));
        }

        // 1. 每秒更新一次皮肤和备份数据
        if (hero.tickCount % 20 == 0) {
            HeroStateManager.restoreEquipmentFromGlobal(hero);
            int globalSkin = data.getSkinVariant(ownerUUID);
            if (hero.getSkinVariant() != globalSkin) {
                hero.setSkinVariant(globalSkin);
            }
            if (globalSkin == HeroEntity.SKIN_CUSTOM) {
                String customName = data.getCustomSkinName(ownerUUID);
                if (!hero.getCustomSkinName().equals(customName)) {
                    hero.setCustomSkinName(customName);
                }
            }
            HeroStateManager.backupDirtyState(hero);
        }

        return true;
    }
}
