package com.whitecloud233.modid.herobrine_companion.init;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组自持粒子注册（1.20.1 Forge 移植版）。
 *
 * <p>{@link #GLOW_TRAIL} 是终末之诗的发光刀光拖尾粒子：由 EpicFight 的挥砍轨迹
 * 系统按 item_skins/poem_of_the_end.json 里的 {@code particle_type} 生成，
 * 渲染走本模组自持的加法混合发光通道（见 compat.epicfight.GlowTrailRenderTypes），
 * 不依赖任何第三方模组的私有实现。</p>
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HerobrineCompanion.MODID);

    /** 终末之诗刀光：加法混合发光拖尾（overrideLimiter=true，粒子设置关闭也渲染） */
    public static final RegistryObject<SimpleParticleType> GLOW_TRAIL =
            PARTICLES.register("glow_trail", () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
