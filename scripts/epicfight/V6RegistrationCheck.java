package com.whitecloud233.herobrine_companion.compat.epicfight;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.event.types.registry.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

/** Registry wiring check without starting a game or changing a running world. */
public class V6RegistrationCheck {
    public static void main(String[] args) throws Exception {
        PoemScythePlayerAnimations.registerAnimations(new AnimationManager.AnimationBuilder("herobrine_companion", b -> {}));
        var manager = AnimationManager.getInstance();
        var registered = manager.getAnimations(a -> a.registryName().getPath().startsWith("player/poem_v6/"));
        if (registered.size() != 23) throw new AssertionError("Expected 23 registered animations");
        // The capability's sound/particle holders require a complete NeoForge
        // bootstrap. Inspect the registered deferred slots here; do not substitute
        // fake runtime objects or claim to have executed a game-side capability.
        var field = PoemScythePlayerAnimations.class.getDeclaredField("combo");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var combo = (java.util.List<AnimationManager.AnimationAccessor<? extends AttackAnimation>>) field.get(null);
        Map<ResourceLocation, Function<Item, ? extends CapabilityItem.Builder<?>>> presets = new HashMap<>();
        PoemScythePlayerAnimations.registerWeaponPreset(new WeaponCapabilityPresetRegistryEvent(presets));
        if (presets.get(PoemScythePlayerAnimations.WEAPON_TYPE) == null) throw new AssertionError("Missing weapon preset factory");
        if (combo.size() != 20) throw new AssertionError("Expected 18 normal + sprint + air");
        for (int i=0; i<18; i++) {
            if (!combo.get(i).registryName().getPath().endsWith("combo_%02d".formatted(i+1))) {
                throw new AssertionError("Wrong normal combo slot " + i);
            }
        }
        if (!combo.get(18).registryName().getPath().endsWith("dash")
                || !combo.get(19).registryName().getPath().endsWith("air")) throw new AssertionError("Wrong special attack slots");
        String data = Files.readString(Path.of("src/main/resources/data/herobrine_companion/capabilities/weapons/poem_of_the_end.json"));
        if (!data.contains(PoemScythePlayerAnimations.WEAPON_TYPE.toString())) throw new AssertionError("Item type not bound");
        Files.writeString(Path.of("build/epicfight-v6/registry_validation.json"),
                "{\"registered_clips\":23,\"normal_combo_slots\":18,\"dash_slot\":18,\"air_slot\":19,\"item_binding_verified\":true,\"capability_builder_executed\":false}\n");
        System.out.println("V6_REGISTRY_OK: item preset -> 18 player combos + sprint + air, 23 registered resources");
    }
}
