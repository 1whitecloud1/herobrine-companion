package com.whitecloud233.herobrine_companion.init;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组自持粒子注册。
 * <p>{@link #GLOW_TRAIL} 是终末之诗的发光刀光拖尾粒子：由 EpicFight 的挥砍轨迹
 * 系统按 item_skins/poem_of_the_end.json 里的 {@code particle_type} 生成，
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, HerobrineCompanion.MODID);

    /** 终末之诗刀光：加法混合发光拖尾（overrideLimiter=true，粒子设置关闭也渲染） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GLOW_TRAIL =
            PARTICLES.register("glow_trail", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
