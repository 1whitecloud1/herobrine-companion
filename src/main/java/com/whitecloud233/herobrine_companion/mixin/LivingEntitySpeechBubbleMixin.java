package com.whitecloud233.herobrine_companion.mixin;

import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ai.SyncSpeechBubblePacket;
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
    @Unique
    private static final double HEROBRINE_COMPANION_SPEECH_SYNC_RANGE = 64.0D;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void herobrineCompanion$defineSpeechBubbleData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(HEROBRINE_COMPANION_SPEECH_BUBBLE, "");
        builder.define(HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT, 0);
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

        String sanitized = plainText;
        if (plainText.length() > HEROBRINE_COMPANION_MAX_SPEECH_LENGTH) {
            sanitized = plainText.substring(0, HEROBRINE_COMPANION_MAX_SPEECH_LENGTH - 3) + "...";
        }

        self.getEntityData().set(HEROBRINE_COMPANION_SPEECH_BUBBLE, sanitized);
        int safeDuration = Math.max(20, durationTicks);
        self.getEntityData().set(HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT, self.tickCount + safeDuration);
        if (!self.level().isClientSide) {
            PacketHandler.sendToNearby(new SyncSpeechBubblePacket(self.getId(), sanitized, safeDuration), self, HEROBRINE_COMPANION_SPEECH_SYNC_RANGE);
        }
    }

    @Override
    public void herobrineCompanion$clearSpeechBubble() {
        LivingEntity self = (LivingEntity) (Object) this;
        self.getEntityData().set(HEROBRINE_COMPANION_SPEECH_BUBBLE, "");
        self.getEntityData().set(HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT, 0);
        if (!self.level().isClientSide) {
            PacketHandler.sendToNearby(new SyncSpeechBubblePacket(self.getId(), "", 0), self, HEROBRINE_COMPANION_SPEECH_SYNC_RANGE);
        }
    }

    @Nullable
    @Override
    public Component herobrineCompanion$getSpeechBubble() {
        LivingEntity self = (LivingEntity) (Object) this;
        String serialized = self.getEntityData().get(HEROBRINE_COMPANION_SPEECH_BUBBLE);
        if (serialized == null || serialized.isBlank()) {
            return null;
        }

        return Component.literal(serialized);
    }

    @Override
    public boolean herobrineCompanion$hasSpeechBubble() {
        LivingEntity self = (LivingEntity) (Object) this;
        return self.tickCount < self.getEntityData().get(HEROBRINE_COMPANION_SPEECH_BUBBLE_EXPIRES_AT)
                && !self.getEntityData().get(HEROBRINE_COMPANION_SPEECH_BUBBLE).isBlank();
    }
}
