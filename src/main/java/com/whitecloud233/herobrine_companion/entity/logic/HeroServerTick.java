package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.HeroWorldData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
public class HeroServerTick {

    /**
     * @return true 如果实体应当继续存活，false 如果实体被 discard
     */
    public static boolean handleTick(HeroEntity hero, ServerLevel serverLevel) {
        // 1. 每秒更新一次皮肤和备份数据
        if (hero.tickCount % 20 == 0) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            int globalSkin = data.getSkinVariant();
            if (hero.getSkinVariant() != globalSkin) {
                hero.setSkinVariant(globalSkin);
            }
            if (globalSkin == HeroEntity.SKIN_CUSTOM) {
                String customName = data.getCustomSkinName();
                if (!hero.getCustomSkinName().equals(customName)) {
                    hero.setCustomSkinName(customName);
                }
            }
            // 备份装备
            if (hero.getOwnerUUID() != null) {
                data.setEquipment(hero.getOwnerUUID(), hero.getArmorItemsTag(), hero.getHandItemsTag());
                data.setCuriosBackItem(hero.getOwnerUUID(), hero.getCuriosBackItemTag());
            }
        }

        // 2. 持续性唯一性检查 (防伪造)
        int checkInterval = hero.tickCount < 200 ? 10 : 100;
        if (hero.tickCount % checkInterval == 0) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            UUID activeUUID = data.getActiveHeroUUID();

            if (activeUUID != null && !activeUUID.equals(hero.getUUID())) {
                // [1.21 修复] 删除了 activeExists 检查。
                // 只要当前活跃的不是自己，说明玩家已在别处重新召唤。旧皇必须无条件自尽！
                // 防止因新皇处于未加载区块导致旧皇“篡位复辟”而产生无限分身。
                HeroDataHandler.updateGlobalTrust(hero);
                hero.discard();
                return false;
            }

            if (activeUUID == null) {
                data.setActiveHeroUUID(hero.getUUID());
            }
            // 定期更新跨维度位置
            data.setLastKnownHeroPos(GlobalPos.of(hero.level().dimension(), hero.blockPosition()));
        }

        return true;
    }
}
