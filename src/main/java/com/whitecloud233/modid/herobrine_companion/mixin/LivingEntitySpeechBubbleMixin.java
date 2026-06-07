package com.whitecloud233.modid.herobrine_companion.mixin;

import com.whitecloud233.modid.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySpeechBubbleMixin implements SpeechBubbleAccessor {
    @Unique
    private static final EntityDataAccessor<String> HEROBRINE_COMPANION_SPEECH_BUBBLE =
            SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.STRING);
    @Unique
    private static final EntityDataAccessor<Integer> HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT =
            SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    @Unique
    private static final int HEROBRINE_COMPANION_MAX_SPEECH_LENGTH = 160;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void herobrineCompanion$defineSpeechBubbleData(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        self.getEntityData().define(HEROBRINE_COMPANION_SPEECH_BUBBLE, "");
        self.getEntityData().define(HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT, 0);
    }

    @Override
    public void herobrineCompanion$showSpeechBubble(Component component, int durationTicks) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (component == null) {
            herobrineCompanion$clearSpeechBubble();
            return;
        }

        String plainText = component.getString().trim();
        if (plainText.isEmpty()) {
            herobrineCompanion$clearSpeechBubble();
            return;
        }

        Component sanitized = component;
        if (plainText.length() > HEROBRINE_COMPANION_MAX_SPEECH_LENGTH) {
            sanitized = Component.literal(plainText.substring(0, HEROBRINE_COMPANION_MAX_SPEECH_LENGTH - 3) + "...");
        }

        String serialized = Component.Serializer.toJson(sanitized);
        if (serialized == null || serialized.isBlank()) {
            herobrineCompanion$clearSpeechBubble();
            return;
        }

        self.getEntityData().set(HEROBRINE_COMPANION_SPEECH_BUBBLE, serialized);
        self.getEntityData().set(HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT, self.tickCount + Math.max(20, durationTicks));
    }

    @Override
    public void herobrineCompanion$clearSpeechBubble() {
        LivingEntity self = (LivingEntity) (Object) this;
        self.getEntityData().set(HEROBRINE_COMPANION_SPEECH_BUBBLE, "");
        self.getEntityData().set(HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT, 0);
    }

    @Nullable
    @Override
    public Component herobrineCompanion$getSpeechBubble() {
        LivingEntity self = (LivingEntity) (Object) this;
        String serialized = self.getEntityData().get(HEROBRINE_COMPANION_SPEECH_BUBBLE);
        if (serialized == null || serialized.isBlank()) {
            return null;
        }

        try {
            Component component = Component.Serializer.fromJson(serialized);
            return component == null ? Component.literal(serialized) : component;
        } catch (Exception ignored) {
            return Component.literal(serialized);
        }
    }

    @Override
    public boolean herobrineCompanion$hasSpeechBubble() {
        LivingEntity self = (LivingEntity) (Object) this;
        return self.tickCount < self.getEntityData().get(HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT)
                && !self.getEntityData().get(HEROBRINE_COMPANION_SPEECH_BUBBLE).isBlank();
    }
}
