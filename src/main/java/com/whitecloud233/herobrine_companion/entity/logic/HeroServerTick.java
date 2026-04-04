package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
public class HeroServerTick {

    /**
     * @return true 如果实体应当继续存活，false 如果实体被 discard
     */
    public static boolean handleTick(HeroEntity hero, ServerLevel serverLevel) {
        UUID ownerUUID = hero.getOwnerUUID();

        // 没主人的实体不管它
        if (ownerUUID == null) return true;

        HeroWorldData data = HeroWorldData.get(serverLevel);

        // 1. 每秒更新一次皮肤和备份数据
        if (hero.tickCount % 20 == 0) {
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
        }

        // 2. 持续性唯一性检查 - 严格比对存档内记录的“唯一合法存活者”
        int checkInterval = hero.tickCount < 200 ? 10 : 100;
        if (hero.tickCount % checkInterval == 0) {
            UUID activeUUID = data.getActiveHeroUUID(ownerUUID);

            if (activeUUID != null && !activeUUID.equals(hero.getUUID())) {
                boolean activeExists = false;
                // 全维度扫描存档里记录的正统合法体是否还活着
                for (ServerLevel lvl : serverLevel.getServer().getAllLevels()) {
                    if (lvl.getEntity(activeUUID) != null) {
                        activeExists = true;
                        break;
                    }
                }

                if (activeExists) {
                    // 正统合法体还活着，我就是个意外产生的克隆幽灵，自我销毁
                    HeroDataHandler.updateGlobalTrust(hero);
                    hero.discard();
                    return false;
                } else {
                    // 记录上的合法体其实已经死了/被删了，那我接管合法身份
                    data.setActiveHeroUUID(ownerUUID, hero.getUUID());
                }
            }

            if (activeUUID == null) {
                data.setActiveHeroUUID(ownerUUID, hero.getUUID());
            }

            // 持续向全服硬盘写入自己的最新坐标，方便 SourceFlowItem 跨维度寻人
            data.setLastKnownHeroPos(ownerUUID, GlobalPos.of(hero.level().dimension(), hero.blockPosition()));
        }

        return true;
    }
}