package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilySummonManager;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLifecycleHandler;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public class HeroServerTick {

    public static boolean handleTick(HeroEntity hero, ServerLevel serverLevel) {
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
            data.setEquipment(ownerUUID, hero.getArmorItemsTag(), hero.getHandItemsTag());
            data.setCuriosBackItem(ownerUUID, hero.getCuriosBackItemTag());
            data.setAccessoriesData(ownerUUID, hero.getAccessoriesDataTag());
        }

        HerobrineFamilySummonManager.serverTick(hero, serverLevel);
        return true;
    }
}
