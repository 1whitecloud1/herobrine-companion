package com.whitecloud233.herobrine_companion.mixin;

import moe.plushie.armourers_workshop.api.core.IResourceLocation;
import moe.plushie.armourers_workshop.core.entity.EntityProfile;
import moe.plushie.armourers_workshop.init.ModEntityProfiles;
import moe.plushie.armourers_workshop.core.utils.OpenResourceLocation;
import moe.plushie.armourers_workshop.core.menu.SkinSlotType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = ModEntityProfiles.class, remap = false)
public class MixinModEntityProfiles {

    private static EntityProfile FORCED_HERO_PROFILE = null;

    @Inject(method = "getProfile(Lnet/minecraft/world/entity/EntityType;)Lmoe/plushie/armourers_workshop/core/entity/EntityProfile;", at = @At("HEAD"), cancellable = true)
    private static void onGetProfile(EntityType<?> entityType, CallbackInfoReturnable<EntityProfile> cir) {
        var name = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        if (name != null && name.getNamespace().equals("herobrine_companion") && name.getPath().equals("hero")) {
            if (FORCED_HERO_PROFILE == null) {
                System.out.println("[Herobrine AW] Building custom EntityProfile for hero...");

                OpenResourceLocation registryName = OpenResourceLocation.parse("armourers_workshop:player");

                Map<SkinSlotType, String> supports = new LinkedHashMap<>();
                supports.put(SkinSlotType.HEAD, "1");
                supports.put(SkinSlotType.CHEST, "1");
                supports.put(SkinSlotType.LEGS, "1");
                supports.put(SkinSlotType.FEET, "1");
                supports.put(SkinSlotType.WINGS, "1");
                supports.put(SkinSlotType.OUTFIT, "1");
                supports.put(SkinSlotType.BOW, "1");
                supports.put(SkinSlotType.SWORD, "1");

                // --- 核心修复：提供标准的玩家骨骼映射 ---
                List<IResourceLocation> transformers = new ArrayList<>();
                // 告诉 AW 使用原版玩家的骨骼绑定逻辑
                transformers.add(OpenResourceLocation.parse("armourers_workshop:player"));

                FORCED_HERO_PROFILE = new EntityProfile(registryName, supports, transformers, false);
            }
            cir.setReturnValue(FORCED_HERO_PROFILE);
        }
    }
}