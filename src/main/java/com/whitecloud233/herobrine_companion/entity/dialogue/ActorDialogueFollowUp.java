package com.whitecloud233.herobrine_companion.entity.dialogue;

@FunctionalInterface
public interface ActorDialogueFollowUp {
    ActorDialogueSpec create(String previousReply);
}
