package com.whitecloud233.herobrine_companion.entity.dialogue;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public interface SpeechBubbleAccessor {
    void herobrineCompanion$showSpeechBubble(Component component, int durationTicks);

    void herobrineCompanion$clearSpeechBubble();

    @Nullable
    Component herobrineCompanion$getSpeechBubble();

    boolean herobrineCompanion$hasSpeechBubble();
}
