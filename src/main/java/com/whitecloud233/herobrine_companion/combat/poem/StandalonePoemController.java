package com.whitecloud233.herobrine_companion.combat.poem;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.PoemAnimationPacket;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-selected combos and blade sweeps; accepted hits use vanilla's damage/enchantment pipeline. */
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class StandalonePoemController {
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
    private StandalonePoemController() { }

    public static boolean enabled() { return !ModList.get().isLoaded("epicfight"); }

    public static boolean usable(Player player) {
        return enabled() && player != null && player.isAlive() && !player.isSpectator()
                && !player.isSleeping() && !player.isPassenger() && !player.isFallFlying()
                && !player.isSwimming() && !player.isUsingItem() && player.hurtTime == 0
                && player.getMainHandItem().getItem() instanceof PoemOfTheEndItem;
    }

    public static int mode(Player player) {
        return player.getMainHandItem().getItem() instanceof PoemOfTheEndItem poem
                ? poem.getMode(player.getMainHandItem()) : -1;
    }

    public static void request(ServerPlayer player, int slot, int mode) {
        if (!usable(player) || slot < 0 || slot > 8 || mode < 0 || mode > 3
                || player.getInventory().selected != slot || mode(player) != mode) return;
        State state = STATES.computeIfAbsent(player, ignored -> new State(slot, mode, player.getMainHandItem()));
        if (!matches(player, state)) {
            state = new State(slot, mode, player.getMainHandItem()); STATES.put(player, state);
        }
        long now = player.level().getGameTime();
        // Finish the old contact interval before an input can advance the combo.
        collide(player, state, now);
        if (STATES.get(player) == state && matches(player, state) && state.clock.request(mode, now)) begin(player, state);
    }

    public static void stop(ServerPlayer player) {
        State state = STATES.remove(player);
        if (state != null) broadcast(player, new PoemAnimationPacket(player.getId(), player.getUUID(),
                state.slot, state.mode, -1, 0, player.level().getGameTime()));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State state = STATES.get(player);
        if (state == null) return;
        if (!matches(player, state)) {
            stop(player); return;
        }
        long now = player.level().getGameTime();
        collide(player, state, now);
        if (STATES.get(player) != state) return;
        if (!matches(player, state)) {
            stop(player); return;
        }
        if (state.clock.tick(now)) begin(player, state);
        if (state.attackStep.poll(state.clock, player.level().getGameTime())) attackStep(player, state.clock.step());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (enabled() && player.getMainHandItem().getItem() instanceof PoemOfTheEndItem
                && (usable(player) || player instanceof ServerPlayer && STATES.containsKey(player))
                && !PoemMeleeHit.allows(player, event.getTarget())) {
            // Reject the old crosshair attack, including raw attack packets sent during a combo.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onSweepAttack(SweepAttackEvent event) {
        if (PoemMeleeHit.allows(event.getEntity(), event.getTarget())) {
            // NeoForge can force a sweep after the item ability check; authored OBB cuts already hit every target.
            event.setSweeping(false);
            event.setCanceled(true);
        }
    }

    private static void begin(ServerPlayer player, State state) {
        state.sweep = new PoemBladeSweep(state.mode, state.clock.step(), state.clock.start(), actor(player));
        state.strengths = new float[state.clock.timing().contacts().size()];
        Arrays.fill(state.strengths, Float.NaN);
        player.swing(InteractionHand.MAIN_HAND);
        sync(player, state);
    }

    private static PoemBladeSweep.Actor actor(Player player) {
        float scale = player.getScale();
        return new PoemBladeSweep.Actor(player.position(), player.getYRot(),
                player.getMainArm() == HumanoidArm.LEFT, player.isCrouching() ? -.125 * scale : 0, scale);
    }

    private static boolean matches(Player player, State state) {
        return usable(player) && player.getInventory().selected == state.slot && mode(player) == state.mode
                && player.getMainHandItem() == state.weapon;
    }

    private static void collide(ServerPlayer player, State state, double tick) {
        if (state.sweep == null) return;
        var sweeps = state.sweep.poll(tick, actor(player));
        if (state.sweep.cancelled()) { stop(player); return; }
        for (PoemBladeSweep.Sweep sweep : sweeps) {
            if (STATES.get(player) != state || !matches(player, state)) return;
            if (Float.isNaN(state.strengths[sweep.contact()])) {
                state.strengths[sweep.contact()] = player.getAttackStrengthScale(.5f);
                // A cut consumes charge once, even on a miss; all targets share that charge.
                player.resetAttackStrengthTicker();
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, player.getSoundSource(), .7f, 1f);
            }
            for (Entity target : player.level().getEntities(player, sweep.bounds(), entity -> targetable(player, entity))) {
                if (STATES.get(player) != state || !matches(player, state)) return;
                Entity owner = target instanceof PartEntity<?> part ? part.getParent() : target;
                if (player.hasLineOfSight(target) && state.sweep.claim(sweep, owner.getUUID(), target.getBoundingBox())) {
                    PoemMeleeHit.attack(player, target, state.strengths[sweep.contact()]);
                }
            }
        }
    }

    private static boolean targetable(Player player, Entity target) {
        Entity owner = target instanceof PartEntity<?> part ? part.getParent() : target;
        return owner != player && owner.isAlive() && !owner.isSpectator() && target.isAttackable() && target.isPickable()
                && !player.isPassengerOfSameVehicle(owner) && !player.isAlliedTo(owner)
                && (!(owner instanceof LivingEntity living) || player.canAttack(living));
    }

    private static void attackStep(ServerPlayer player, int step) {
        if (!player.onGround() || player.getAbilities().flying || player.isShiftKeyDown()
                || player.isInWaterOrBubble() || player.isInLava() || player.horizontalCollision) return;
        Vec3 current = player.getDeltaMovement();
        Vec3 next = PoemAttackStep.velocity(current, player.getYRot(), step);
        if (next.equals(current)) return;
        player.setDeltaMovement(next);
        // The owning client simulates player movement; a server-only push would never move it.
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    @SubscribeEvent
    public static void onTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject) {
            State state = STATES.get(subject);
            if (state != null && state.clock.active(subject.level().getGameTime())) {
                PacketHandler.sendToPlayer(packet(subject, state), viewer);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    private static PoemAnimationPacket packet(ServerPlayer player, State state) {
        return new PoemAnimationPacket(player.getId(), player.getUUID(), state.slot, state.mode,
                state.clock.step(), (long) state.clock.start(), player.level().getGameTime());
    }
    private static void sync(ServerPlayer player, State state) { broadcast(player, packet(player, state)); }
    private static void broadcast(ServerPlayer player, PoemAnimationPacket packet) {
        PacketHandler.sendToPlayer(packet, player); PacketHandler.sendToTracking(packet, player);
    }
    private static final class State {
        final int slot, mode;
        final ItemStack weapon;
        final PoemComboClock clock = new PoemComboClock();
        final PoemAttackStep attackStep = new PoemAttackStep();
        PoemBladeSweep sweep;
        float[] strengths;
        State(int slot, int mode, ItemStack weapon) { this.slot = slot; this.mode = mode; this.weapon = weapon; }
    }
}
