package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.util.DebugLog;
import com.whitecloud233.herobrine_companion.entity.cooking.CookingService;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroObserver;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroPrankHandler;
import com.whitecloud233.herobrine_companion.entity.ai.learning.state.GlitchLordStateDefinition;
import com.whitecloud233.herobrine_companion.entity.ai.learning.state.PranksterStateDefinition;
import com.whitecloud233.herobrine_companion.entity.logic.HeroInteractionHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroInvitationHelper;
import com.whitecloud233.herobrine_companion.event.HeroVisuals;
import com.whitecloud233.herobrine_companion.entity.visual.HeroVisualState;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;


public class HeroLogic {

    /** 临时排查埋点（定位「邀请做菜后 Hero 不干活」），问题定位后删除。 */
    private static final org.slf4j.Logger DIAG = org.slf4j.LoggerFactory.getLogger("HeroCookDiag");

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide) {
            HeroVisuals.tickClientLocomotion(hero);
        } else {
            serverTick(hero);
        }
    }

    static float advanceWalkAnimationTicks(float ticks, boolean floating, boolean groundWalking,
                                           double movedX, double movedZ) {
        return HeroVisualState.advanceWalkAnimationTicks(ticks, floating, groundWalking, movedX, movedZ);
    }

    private static void serverTick(HeroEntity hero) {
        if (hero.isRemoved()) return;
        if (hero.tickCount == 20) {
            HeroLifecycleHandler.checkUniqueness(hero);
            if (hero.isRemoved()) return;
            if (!hero.isLoadedFromDisk() && hero.getOwnerUUID() != null) {
                Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
                if (owner != null) {
                    HeroStateManager.restoreFromGlobal(hero, owner);
                }
            }
        }

        if (hero.tickCount % 100 == 0) {
            HeroLifecycleHandler.checkUniqueness(hero);
            if (hero.isRemoved()) return;
        }

        if (hero.getOwnerUUID() == null) {
            boolean isFreshSpawn = !hero.isLoadedFromDisk();
            boolean safeToBind = isFreshSpawn ? (hero.tickCount > 20) : (hero.tickCount > 600);

            if (safeToBind && hero.tickCount % 100 == 0) {
                findAndSetOwner(hero);
            }
        }

        if (hero.tickCount == 5) {
            HeroDataHandler.syncGlobalTrust(hero);
        }

        if (hero.tickCount % 100 == 0 && hero.getTrustLevel() <= 0) {
            HeroDataHandler.restoreTrustFromPlayer(hero);
        }

        if (hero.tickCount % 20 == 0) {
            boolean isEndRing = hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY;
            String key = isEndRing ? "entity.herobrine_companion.herobrine" : "entity.herobrine_companion.hero";
            Component expectedName = Component.translatable(key);
            if (!hero.getCustomName().equals(expectedName)) {
                hero.setCustomName(expectedName);
            }
        }

        HeroDimensionHandler.handleVoidProtection(hero);

        HeroDialogueHandler.tick(hero);
        CookingService.tickVisuals(hero);
        HeroPrankHandler.tick(hero);
        HeroObserver.tick(hero);
        GlitchLordStateDefinition.tickPersistentState(hero);
        PranksterStateDefinition.tickPersistentState(hero);

        if (hero.tickCount % 100 == 50) {
            checkPendingQuestRewards(hero);
            checkPendingLoreFragments(hero);
        }

        if (hero.isPassenger()) {
            hero.setYHeadRot(hero.yBodyRot);
        }
    }

    private static void findAndSetOwner(HeroEntity hero) {
        if (hero.level().isClientSide()) return;

        Player closestPlayer = hero.level().getNearestPlayer(hero, 64.0D);

        if (closestPlayer instanceof ServerPlayer serverPlayer) {
            hero.setOwnerUUID(serverPlayer.getUUID());
            HeroStateManager.restoreFromGlobal(hero, serverPlayer);
        }
    }

    private static void checkPendingQuestRewards(HeroEntity hero) {
        if (hero.getOwnerUUID() == null) return;
        Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        if (owner == null) return;

        CompoundTag data = owner.getPersistentData();
        if (data.contains("HeroPendingTrustReward")) {
            int reward = data.getInt("HeroPendingTrustReward");
            hero.increaseTrust(reward);
            owner.sendSystemMessage(Component.translatable("message.herobrine_companion.trust_increase", reward, hero.getTrustLevel()));
            data.remove("HeroPendingTrustReward");
        }
        if (data.contains("HeroPendingQuestClear")) {
            hero.removeTag("player_doing_quest");
            data.remove("HeroPendingQuestClear");
        }
    }

    private static void checkPendingLoreFragments(HeroEntity hero) {
        if (hero.getOwnerUUID() == null) return;
        Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        if (owner == null) return;

        CompoundTag data = owner.getPersistentData();
        if (data.contains("HeroPendingLore", Tag.TAG_LIST)) {
            ListTag list = data.getList("HeroPendingLore", Tag.TAG_STRING);

            for (int i = 0; i < list.size(); i++) {
                String fragmentId = list.getString(i);
                hero.getHeroBrain().inputLoreFragment(owner.getUUID(), fragmentId);
            }

            if (!list.isEmpty()) {
                hero.level().playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.0f);
            }

            data.remove("HeroPendingLore");
        }
    }

    public static InteractionResult onInteract(HeroEntity hero, Player player, InteractionHand hand) {
        return HeroInteractionHandler.onInteract(hero, player, hand);
    }

    public static boolean onHurt(HeroEntity hero, DamageSource source, float amount) {
        return HeroCombatHandler.onHurt(hero, source, amount);
    }

    public static void setupHiddenTeam(HeroEntity hero) {
        HeroVisuals.setupHiddenTeam(hero);
    }

    public static void handlePlayerInvitation(HeroEntity hero, Player player, BlockPos clickedPos, int actionType) {
        // 石磨是九格结构，点哪一格都该落在同一台磨上；不归一化的话换个格子点就会被当成「另一次邀请」。
        BlockPos pos = actionType == CookingService.INVITED_ACTION_MILLSTONE
                ? CookingService.millstoneCenter(hero.level(), clickedPos)
                : clickedPos;
        BlockPos currentInvitedPos = hero.getInvitedPos();
        if (currentInvitedPos != null && currentInvitedPos.equals(pos)) {
            if (actionType == CookingService.INVITED_ACTION_COOK
                    && hero.getInvitedAction() == CookingService.INVITED_ACTION_COOK
                    && !player.isShiftKeyDown()) {
                player.sendSystemMessage(CookingService.cycleCookSelection(hero, pos));
                return;
            }

            if (hero.getInvitedAction() == CookingService.INVITED_ACTION_COOK) {
                CookingService.endCookChunkTicket(hero);
            }
            if (hero.getInvitedAction() == CookingService.INVITED_ACTION_MILLSTONE) {
                CookingService.releaseMillstone(hero, pos);
            }
            hero.setInvitedPos(null);
            hero.setInvitedAction(HeroInvitationHelper.ACTION_NONE);

            if (hero.isPassenger()) {
                hero.stopRiding();
            }

            player.sendSystemMessage(Component.translatable("message.herobrine_companion.invite_cancel"));
            return;
        }

        if (actionType == CookingService.INVITED_ACTION_COOK) {
            int optionCount = CookingService.getCookOptions(hero, pos).size();
            DebugLog.info(DIAG,
                    "[CookDiag] logic.invite :: pos={} options={}", pos.toShortString(), optionCount);
            if (optionCount == 0) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.invite_cook_none"));
                return;
            }
        }

        // 石磨只能分给一只生物：先占住的话，Hero 上去会把原来的动物挤掉。
        if (actionType == CookingService.INVITED_ACTION_MILLSTONE) {
            if (CookingService.isMillstoneBusy(hero, pos) || !CookingService.canHostMillstone(hero, pos)) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.invite_millstone_busy"));
                return;
            }
        }

        applyInvitation(hero, player, pos, actionType, false);
    }

    public static boolean handleCookSelection(HeroEntity hero, Player player, BlockPos pos, ResourceLocation recipeId, int repeatCount) {
        return player instanceof ServerPlayer serverPlayer
                && CookingService.requestCookSelection(hero, serverPlayer, pos, recipeId, repeatCount);
    }

    private static void applyInvitation(HeroEntity hero, Player player, BlockPos pos, int actionType, boolean preserveCookSelection) {
        if (hero.getInvitedAction() == CookingService.INVITED_ACTION_COOK
                && (actionType != CookingService.INVITED_ACTION_COOK || !pos.equals(hero.getInvitedPos()))) {
            CookingService.endCookChunkTicket(hero);
        }

        // 换邀请目标或换动作时先松绑：石磨会一直把 Hero 按在磨盘上，不先松开就走去干别的会一路被拽回来。
        if (hero.getInvitedAction() == CookingService.INVITED_ACTION_MILLSTONE
                && (actionType != CookingService.INVITED_ACTION_MILLSTONE || !pos.equals(hero.getInvitedPos()))) {
            CookingService.releaseMillstone(hero, hero.getInvitedPos());
        }

        hero.setInvitedPos(pos);
        hero.setInvitedAction(actionType);
        hero.setMillstoneBound(false);

        if (actionType == CookingService.INVITED_ACTION_COOK && !preserveCookSelection) {
            CookingService.resetCookSelection(hero, pos);
        }

        if (actionType == CookingService.INVITED_ACTION_COOK) {
            CookingService.beginCookChunkTicket(hero, pos);
        }

        if (actionType == CookingService.INVITED_ACTION_MILLSTONE) {
            // 石磨区块卸载后绑定就断了，拉磨期间必须把区块钉住。
            CookingService.beginCookChunkTicket(hero, pos);
        }

        if (actionType == HeroInvitationHelper.ACTION_REST) {
            if (hero.isFloating()) {
                hero.setFloating(false);
                hero.setNoGravity(false);
            }
        }

        if (actionType == CookingService.INVITED_ACTION_MILLSTONE) {
            // 拉磨要悬在磨台上，石磨每 tick 会把脚底重新摆到磨盘高度；这里先关掉重力，避免落地判定介入。
            if (hero.isFloating()) {
                hero.setFloating(false);
            }
            hero.setNoGravity(true);
            hero.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            hero.fallDistance = 0.0F;
        }

        hero.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);

        String baseKey = switch (actionType) {
            case HeroInvitationHelper.ACTION_INSPECT -> "message.herobrine_companion.invite_inspect";
            case HeroInvitationHelper.ACTION_REST -> "message.herobrine_companion.invite_rest";
            case HeroInvitationHelper.ACTION_GUARD -> "message.herobrine_companion.invite_guard";
            case HeroInvitationHelper.ACTION_COOK -> "message.herobrine_companion.invite_cook";
            case HeroInvitationHelper.ACTION_MILLSTONE -> "message.herobrine_companion.invite_millstone";
            default -> "message.herobrine_companion.invite_confirm";
        };

        int variant = hero.getRandom().nextInt(3) + 1;
        player.sendSystemMessage(Component.translatable(baseKey + "_" + variant));
    }
}