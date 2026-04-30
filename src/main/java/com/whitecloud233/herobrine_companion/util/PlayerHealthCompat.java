package com.whitecloud233.herobrine_companion.util;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 与会修改玩家 MAX_HEALTH 的外部模组做软兼容，当前主要处理 Spice of Life: Carrot Edition。
 */
public final class PlayerHealthCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String SOL_MOD_ID = "solcarrot";
    private static final UUID SOL_HEALTH_MODIFIER_ID = UUID.fromString("b20d3436-0d39-4868-96ab-d0a4856e68c6");

    private static volatile boolean solReflectionInitialized;
    private static volatile boolean solReflectionFailed;
    private static Method solUpdateFoodHpModifierMethod;
    private static Method solFoodListGetMethod;
    private static Method solGetProgressInfoMethod;
    private static Method solMilestonesAchievedMethod;
    private static Method solGetBaseHeartsMethod;
    private static Method solGetHeartsPerMilestoneMethod;

    private PlayerHealthCompat() {
    }

    /**
     * 让 Spice of Life 先把 MAX_HEALTH modifier 刷到玩家身上，避免读取到过期的生命上限。
     */
    public static void syncExternalMaxHealth(Player player) {
        if (player == null || !ModList.get().isLoaded(SOL_MOD_ID)) {
            return;
        }

        initializeSolReflection();
        if (solUpdateFoodHpModifierMethod == null) {
            return;
        }

        try {
            solUpdateFoodHpModifierMethod.invoke(null, player);
        } catch (ReflectiveOperationException exception) {
            logSolReflectionFailure("Failed to sync Spice of Life max health modifier", exception);
        }
    }

    /**
     * 计算救援逻辑应当参考的“基础生命池”。
     * 对 SOL 的额外奖励红心不再线性抬高救援线，避免玩家一受伤就被拉回到半血附近。
     */
    public static float getRescueHealthPool(Player player) {
        if (player == null) {
            return 20.0F;
        }

        syncExternalMaxHealth(player);

        float maxHealth = player.getMaxHealth();
        float spiceBonusHealth = getSpiceBonusHealth(player);
        if (spiceBonusHealth <= 0.0F) {
            return maxHealth;
        }

        return Mth.clamp(maxHealth - spiceBonusHealth, 1.0F, maxHealth);
    }

    private static float getSpiceBonusHealth(Player player) {
        if (!ModList.get().isLoaded(SOL_MOD_ID)) {
            return 0.0F;
        }

        initializeSolReflection();

        try {
            if (solFoodListGetMethod != null
                    && solGetProgressInfoMethod != null
                    && solMilestonesAchievedMethod != null
                    && solGetBaseHeartsMethod != null
                    && solGetHeartsPerMilestoneMethod != null) {
                Object foodList = solFoodListGetMethod.invoke(null, player);
                if (foodList == null) {
                    return 0.0F;
                }

                Object progressInfo = solGetProgressInfoMethod.invoke(foodList);
                int milestonesAchieved = ((Number) solMilestonesAchievedMethod.invoke(progressInfo)).intValue();
                int baseHearts = ((Number) solGetBaseHeartsMethod.invoke(null)).intValue();
                int heartsPerMilestone = ((Number) solGetHeartsPerMilestoneMethod.invoke(null)).intValue();

                float bonusHealth = 2.0F * ((baseHearts - 10) + (milestonesAchieved * heartsPerMilestone));
                return Math.max(0.0F, bonusHealth);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logSolReflectionFailure("Failed to query Spice of Life bonus health", exception);
        }

        AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return 0.0F;
        }

        AttributeModifier modifier = maxHealthAttribute.getModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("herobrine_companion", SOL_HEALTH_MODIFIER_ID.toString()));
        return modifier == null ? 0.0F : (float) Math.max(0.0D, modifier.amount());
    }

    private static void initializeSolReflection() {
        if (solReflectionInitialized) {
            return;
        }

        synchronized (PlayerHealthCompat.class) {
            if (solReflectionInitialized) {
                return;
            }

            try {
                Class<?> maxHealthHandlerClass = Class.forName("com.cazsius.solcarrot.tracking.MaxHealthHandler");
                solUpdateFoodHpModifierMethod = maxHealthHandlerClass.getMethod("updateFoodHPModifier", Player.class);

                Class<?> foodListClass = Class.forName("com.cazsius.solcarrot.tracking.FoodList");
                solFoodListGetMethod = foodListClass.getMethod("get", Player.class);
                solGetProgressInfoMethod = foodListClass.getMethod("getProgressInfo");

                Class<?> progressInfoClass = Class.forName("com.cazsius.solcarrot.tracking.ProgressInfo");
                solMilestonesAchievedMethod = progressInfoClass.getMethod("milestonesAchieved");

                Class<?> solConfigClass = Class.forName("com.cazsius.solcarrot.SOLCarrotConfig");
                solGetBaseHeartsMethod = solConfigClass.getMethod("getBaseHearts");
                solGetHeartsPerMilestoneMethod = solConfigClass.getMethod("getHeartsPerMilestone");
            } catch (ReflectiveOperationException exception) {
                logSolReflectionFailure("Failed to initialize Spice of Life compatibility hooks", exception);
            } finally {
                solReflectionInitialized = true;
            }
        }
    }

    private static void logSolReflectionFailure(String message, Exception exception) {
        if (solReflectionFailed) {
            return;
        }

        solReflectionFailed = true;
        LOGGER.warn("{}; falling back to vanilla max-health handling.", message, exception);
    }
}
