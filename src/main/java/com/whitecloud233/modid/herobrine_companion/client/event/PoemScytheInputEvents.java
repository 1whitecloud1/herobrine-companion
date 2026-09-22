package com.whitecloud233.modid.herobrine_companion.client.event;

import com.mojang.blaze3d.platform.InputConstants;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.combat.PoemAttackInput;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.PoemGesturePacket;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Tap attack for the native EF combo, hold for heavy, or press a modifier chord. */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class PoemScytheInputEvents {
    private static final String CATEGORY = "key.categories.herobrine_companion";
    public static final KeyMapping FLURRY = new KeyMapping("key.herobrine_companion.poem_flurry_modifier",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, CATEGORY);
    public static final KeyMapping UPPERCUT = new KeyMapping("key.herobrine_companion.poem_uppercut_modifier",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, CATEGORY);
    public static final KeyMapping HEAVY_MODIFIER = new KeyMapping("key.herobrine_companion.poem_heavy_modifier",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
    private static final KeyMapping[] MODIFIERS = {FLURRY, UPPERCUT, HEAVY_MODIFIER};
    private static final PoemAttackInput INPUT = new PoemAttackInput();
    private static final Set<InputConstants.Key> SCANCODES = new HashSet<>();
    private static Player contextPlayer;
    private static ItemStack contextStack;
    private static int contextSlot = -1;
    private static int contextMode = -1;

    private PoemScytheInputEvents() { }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping modifier : MODIFIERS) event.register(modifier);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (process(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
        InputConstants.Key scan = InputConstants.Type.SCANCODE.getOrCreate(event.getScanCode());
        if (event.getAction() == GLFW.GLFW_RELEASE) SCANCODES.remove(scan); else SCANCODES.add(scan);
        process(key, event.getAction());
        if (!scan.equals(key)) process(scan, event.getAction());
    }

    private static boolean process(InputConstants.Key key, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (!context(mc)) return false;
        boolean pressed = action == GLFW.GLFW_PRESS;
        boolean released = action == GLFW.GLFW_RELEASE;
        if (key.equals(mc.options.keyUse.getKey()) && sharesUse(mc)) {
            if (pressed && !shift(mc)) INPUT.usePressed();
            if (INPUT.holdsUse()) {
                discard(mc.options.keyUse);
                if (released && INPUT.useReleased()) KeyMapping.click(mc.options.keyUse.getKey());
                return true;
            }
        }
        if (key.equals(EpicFightKeys.attack().getKey())) {
            if (pressed) send(INPUT.attackPressed(gesture(mc), Util.getMillis()));
            boolean consumed = INPUT.consumesAttack();
            if (released) send(INPUT.attackReleased(Util.getMillis()));
            if (consumed) discardAttack(mc);
            return consumed;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!context(mc)) return;
        if ((event.isAttack() || event.getKeyMapping() == mc.options.keyAttack)
                && mc.options.keyAttack.getKey().equals(EpicFightKeys.attack().getKey())) {
            if (down(mc, EpicFightKeys.attack())) send(INPUT.attackPressed(gesture(mc), Util.getMillis()));
            if (INPUT.consumesAttack() || INPUT.hasEngineControl()) {
                event.setCanceled(true); event.setSwingHand(false);
                discardAttack(mc);
            }
        } else if (event.isUseItem() && sharesUse(mc) && !shift(mc)
                && (INPUT.holdsUse() || down(mc, mc.options.keyUse))) {
            INPUT.usePressed();
            event.setCanceled(true); event.setSwingHand(false);
            discard(mc.options.keyUse);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (!context(mc)) return;
        advanceAttack(mc);
        if (INPUT.holdsUse()) {
            discard(mc.options.keyUse);
            if (!down(mc, mc.options.keyUse) && INPUT.useReleased()) KeyMapping.click(mc.options.keyUse.getKey());
        }
        for (KeyMapping modifier : MODIFIERS) while (modifier.consumeClick()) { }
    }

    private static void advanceAttack(Minecraft mc) {
        if (INPUT.consumesAttack()) {
            send(down(mc, EpicFightKeys.attack()) ? INPUT.attackHeld(Util.getMillis()) : INPUT.attackReleased(Util.getMillis()));
        }
        if (INPUT.consumesAttack() || INPUT.hasEngineControl()) discardAttack(mc);
    }

    /** Called before EF consumes its own mappings; client tick ordering cannot duplicate a tap. */
    public static int takeEngineControl() {
        Minecraft mc = Minecraft.getInstance();
        if (context(mc)) advanceAttack(mc);
        return INPUT.takeEngineControl();
    }

    public static boolean blocksNativeAttack() { return INPUT.consumesAttack(); }

    public static boolean blocksNativeInnate() {
        return INPUT.consumesAttack() && EpicFightKeys.innate().getKey().equals(EpicFightKeys.attack().getKey());
    }

    public static boolean blocksNativeGuard() {
        return INPUT.holdsUse() && EpicFightKeys.guard().getKey().equals(Minecraft.getInstance().options.keyUse.getKey());
    }

    private static int gesture(Minecraft mc) {
        for (int i = MODIFIERS.length - 1; i >= 0; i--) {
            KeyMapping modifier = MODIFIERS[i];
            if (!modifier.getKey().equals(EpicFightKeys.attack().getKey()) && down(mc, modifier)
                    && !(shift(mc) && modifier.getKey().equals(mc.options.keyUse.getKey()))) return i;
        }
        return -1;
    }

    private static boolean sharesUse(Minecraft mc) {
        for (KeyMapping modifier : MODIFIERS) {
            if (!modifier.isUnbound() && modifier.getKey().equals(mc.options.keyUse.getKey())
                    && !modifier.getKey().equals(EpicFightKeys.attack().getKey())) return true;
        }
        return false;
    }

    private static boolean shift(Minecraft mc) { return mc.player.isShiftKeyDown() || down(mc, mc.options.keyShift); }

    private static boolean down(Minecraft mc, KeyMapping mapping) {
        if (mapping.isUnbound()) return false;
        InputConstants.Key key = mapping.getKey();
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(window, key.getValue());
        return SCANCODES.contains(key);
    }

    private static boolean context(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.screen != null || mc.isPaused() || !mc.isWindowActive()
                || !(player.getMainHandItem().getItem() instanceof PoemOfTheEndItem poem)
                || !HeroEpicFightCompat.canUsePoemGestures(player)) {
            reset(mc); return false;
        }
        int slot = player.getInventory().selected, mode = poem.getMode(player.getMainHandItem());
        if (contextPlayer != player || contextStack != player.getMainHandItem() || contextSlot != slot || contextMode != mode) {
            reset(mc);
            contextPlayer = player; contextStack = player.getMainHandItem(); contextSlot = slot; contextMode = mode;
        }
        return true;
    }

    private static void reset(Minecraft mc) {
        if (INPUT.consumesAttack() || INPUT.hasEngineControl()) discardAttack(mc);
        if (INPUT.holdsUse()) discard(mc.options.keyUse);
        INPUT.reset();
        contextPlayer = null; contextStack = null; contextSlot = contextMode = -1;
    }

    private static void discard(KeyMapping mapping) {
        mapping.setDown(false);
        while (mapping.consumeClick()) { }
    }

    private static void discardAttack(Minecraft mc) {
        KeyMapping attack = EpicFightKeys.attack();
        discard(attack);
        if (mc.options.keyAttack.getKey().equals(attack.getKey())) discard(mc.options.keyAttack);
        if (EpicFightKeys.innate().getKey().equals(attack.getKey())) discard(EpicFightKeys.innate());
    }

    private static void send(int gesture) {
        if (gesture >= 0 && gesture < 3) PacketHandler.sendToServer(new PoemGesturePacket(gesture, contextSlot, contextMode));
    }

    /** Loaded only after the optional EF bridge has accepted a Poem input context. */
    private static final class EpicFightKeys {
        static KeyMapping attack() { return yesman.epicfight.client.input.EpicFightKeyMappings.ATTACK; }
        static KeyMapping innate() { return yesman.epicfight.client.input.EpicFightKeyMappings.WEAPON_INNATE_SKILL; }
        static KeyMapping guard() { return yesman.epicfight.client.input.EpicFightKeyMappings.GUARD; }
    }
}
