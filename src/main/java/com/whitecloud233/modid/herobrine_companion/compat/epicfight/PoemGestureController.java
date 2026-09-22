package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.combat.PoemGestureQueue;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/** Runs only behind the optional Epic Fight bridge, on the server thread. */
final class PoemGestureController {
    private static final Map<ServerPlayer, Pending> PLAYERS = new WeakHashMap<>();

    private PoemGestureController() { }

    static void register() { MinecraftForge.EVENT_BUS.addListener(PoemGestureController::onTick); }

    static boolean eligible(Player player) {
        if (player == null || !player.isAlive() || player.isRemoved() || player.isSpectator()
                || player.isPassenger() || player.isFallFlying() || player.isSleeping()
                || !(player.getMainHandItem().getItem() instanceof PoemOfTheEndItem)) return false;
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        return patch != null && patch.isEpicFightMode()
                && !patch.getEntityState().hurt() && !patch.getEntityState().knockDown();
    }

    static void enqueue(Player sender, int gesture, int slot, int mode) {
        if (!(sender instanceof ServerPlayer player) || !eligible(player)) return;
        if (slot != player.getInventory().selected
                || mode != ((PoemOfTheEndItem) player.getMainHandItem().getItem()).getMode(player.getMainHandItem())) return;
        Pending pending = PLAYERS.computeIfAbsent(player, unused -> new Pending());
        updateContext(player, pending);
        pending.queue.offer(gesture, player.level().getGameTime(), slot, mode);
        attempt(player, pending);
    }

    static void cancel(Player player) {
        Pending pending = PLAYERS.get(player);
        if (pending != null) pending.queue.cancel();
    }

    private static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        Pending pending = PLAYERS.get(player);
        if (pending == null) return;
        if (!eligible(player)) { pending.queue.cancel(); return; }
        updateContext(player, pending);
        attempt(player, pending);
    }

    private static void updateContext(ServerPlayer player, Pending pending) {
        ItemStack stack = player.getMainHandItem();
        if (pending.stack != stack || pending.dimension != player.level().dimension()) {
            pending.queue.cancel();
            pending.stack = stack;
            pending.dimension = player.level().dimension();
        }
    }

    private static void attempt(ServerPlayer player, Pending pending) {
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) { pending.queue.cancel(); return; }
        var state = patch.getEntityState();
        if (state.hurt() || state.knockDown()) { pending.queue.cancel(); return; }
        ItemStack stack = player.getMainHandItem();
        long tick = player.level().getGameTime();
        int gesture = pending.queue.poll(tick, player.getInventory().selected,
                ((PoemOfTheEndItem) stack.getItem()).getMode(stack),
                state.canBasicAttack() && !player.isUsingItem());
        if (gesture < 0) return;
        var moves = UnityScytheExtraAnimations.moves(false);
        if (moves.size() != 3) return;
        // The server decides ground versus air at execution, including buffered inputs.
        boolean airborne = !player.onGround();
        var move = moves.get(gesture);
        HeroEpicFightBridge.resetPoemComboCounter(patch);
        patch.resetActionTick();
        pending.queue.started(tick, (int) Math.ceil((move.timing(airborne).recovery() + .07F) * 20F));
        patch.playAnimationSynchronized(move.attack(airborne), 0F);
    }

    private static final class Pending {
        final PoemGestureQueue queue = new PoemGestureQueue();
        ItemStack stack;
        ResourceKey<Level> dimension;
    }
}
