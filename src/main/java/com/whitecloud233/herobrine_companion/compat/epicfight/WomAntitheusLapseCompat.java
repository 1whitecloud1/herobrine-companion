package com.whitecloud233.herobrine_companion.compat.epicfight;

import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillDataKey;
import yesman.epicfight.skill.SkillDataManager;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/** A finishing animation can outlive the weapon skill that started it. */
public final class WomAntitheusLapseCompat {
    private WomAntitheusLapseCompat() { }

    public static <T> void resetOnlyOwnedSkillData(LivingEntityPatch<?> entitypatch,
                                                  SkillDataManager manager, DeferredHolder<SkillDataKey<?>, ? extends SkillDataKey<T>> key, T value) {
        WomSkillState state = LazyWomSkills.STATE;
        if (state == null) {
            // WOM is optional. A changed API must not reset another weapon's state.
            return;
        }
        Skill expected;
        if (key == state.lapse() || key == state.particle()) {
            expected = state.passive();
        } else if (key == state.active()) {
            expected = state.innate();
        } else {
            manager.setDataSync(key, value);
            return;
        }
        if (expected != null && entitypatch instanceof ServerPlayerPatch player) {
            setDataSyncIfOwned(player.getSkill(expected), manager, key, value);
        }
    }

    public static <T> void setDataSyncIfOwned(SkillContainer owner, SkillDataManager target,
                                             DeferredHolder<SkillDataKey<?>, ? extends SkillDataKey<T>> key, T value) {
        // Skill slots survive a weapon change, but their data and ownership do not.
        // Shared keys such as ACTIVE must not reset a different weapon's skill.
        if (owner != null && owner.getDataManager() == target && target.hasData(key)) {
            target.setDataSync(key, value);
        }
    }

    // Resolve only when WOM's animation callback runs, after its skills and keys
    // have been registered. No WOM types appear in our compiled class signatures.
    private static final class LazyWomSkills {
        private static final WomSkillState STATE = resolve();

        private static WomSkillState resolve() {
            try {
                ClassLoader loader = WomAntitheusLapseCompat.class.getClassLoader();
                Class<?> keys = Class.forName("reascer.wom.skill.WOMSkillDataKeys", false, loader);
                Class<?> skills = Class.forName("reascer.wom.gameasset.WOMSkills", false, loader);
                return new WomSkillState(readKey(keys, "LAPSE"), readKey(keys, "PARTICLE"),
                        readKey(keys, "ACTIVE"), (Skill) ((Supplier<?>) skills.getField("DEMON_MARK_PASSIVE").get(null)).get(),
                        (Skill) ((Supplier<?>) skills.getField("DEMONIC_ASCENSION").get(null)).get());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private static DeferredHolder<?, ?> readKey(Class<?> keys, String name) throws ReflectiveOperationException {
            return (DeferredHolder<?, ?>) keys.getField(name).get(null);
        }
    }

    private record WomSkillState(DeferredHolder<?, ?> lapse, DeferredHolder<?, ?> particle,
                                 DeferredHolder<?, ?> active, Skill passive, Skill innate) { }
}
