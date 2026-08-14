package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientAiPrompts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ActorDialoguePromptPacket implements CustomPacketPayload {
    public static final Type<ActorDialoguePromptPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "actor_dialogue_prompt"));
    public static final StreamCodec<FriendlyByteBuf, ActorDialoguePromptPacket> STREAM_CODEC =
            StreamCodec.ofMember(ActorDialoguePromptPacket::encode, ActorDialoguePromptPacket::new);

    private final UUID jobId;
    private final UUID conversationScopeId;
    private final String systemPrompt;
    private final String userPrompt;
    private final String seedText;
    private final String fallbackKey;
    private final List<String> fallbackArgs;
    private final String outputLanguageCode;

    public ActorDialoguePromptPacket(UUID jobId, UUID conversationScopeId, String systemPrompt, String userPrompt,
                                     String seedText, String fallbackKey, List<String> fallbackArgs,
                                     String outputLanguageCode) {
        this.jobId = jobId == null ? new UUID(0L, 0L) : jobId;
        this.conversationScopeId = conversationScopeId == null ? new UUID(0L, 0L) : conversationScopeId;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.userPrompt = userPrompt == null ? "" : userPrompt;
        this.seedText = seedText == null ? "" : seedText;
        this.fallbackKey = fallbackKey == null ? "" : fallbackKey;
        this.fallbackArgs = fallbackArgs == null ? List.of() : List.copyOf(fallbackArgs);
        this.outputLanguageCode = normalizeLanguageCode(outputLanguageCode);
    }

    public ActorDialoguePromptPacket(FriendlyByteBuf buf) {
        this.jobId = buf.readUUID();
        this.conversationScopeId = buf.readUUID();
        this.systemPrompt = buf.readUtf(8192);
        this.userPrompt = buf.readUtf(8192);
        this.seedText = buf.readUtf(2048);
        this.fallbackKey = buf.readUtf(256);
        int argCount = buf.readVarInt();
        List<String> args = new ArrayList<>(argCount);
        for (int i = 0; i < argCount; i++) {
            args.add(buf.readUtf(512));
        }
        this.fallbackArgs = List.copyOf(args);
        this.outputLanguageCode = normalizeLanguageCode(buf.readUtf(32));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.jobId);
        buf.writeUUID(this.conversationScopeId);
        buf.writeUtf(this.systemPrompt, 8192);
        buf.writeUtf(this.userPrompt, 8192);
        buf.writeUtf(this.seedText, 2048);
        buf.writeUtf(this.fallbackKey, 256);
        buf.writeVarInt(this.fallbackArgs.size());
        for (String argument : this.fallbackArgs) {
            buf.writeUtf(argument == null ? "" : argument, 512);
        }
        buf.writeUtf(this.outputLanguageCode, 32);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ActorDialoguePromptPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAiPrompts.handleActorDialoguePrompt(
                packet.jobId,
                packet.conversationScopeId,
                packet.systemPrompt,
                packet.userPrompt,
                packet.seedText,
                packet.fallbackKey,
                packet.fallbackArgs,
                packet.outputLanguageCode
        ));
    }

    private static String normalizeLanguageCode(String rawLanguageCode) {
        if (rawLanguageCode == null) {
            return "en_us";
        }
        String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? "en_us" : normalized;
    }
}
