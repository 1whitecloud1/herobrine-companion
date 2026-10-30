package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import com.whitecloud233.modid.herobrine_companion.util.LanguageCodes;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class ActorDialoguePromptPacket {
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
        this.jobId = jobId;
        this.conversationScopeId = conversationScopeId;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.userPrompt = userPrompt == null ? "" : userPrompt;
        this.seedText = seedText == null ? "" : seedText;
        this.fallbackKey = fallbackKey == null ? "" : fallbackKey;
        this.fallbackArgs = fallbackArgs == null ? List.of() : List.copyOf(fallbackArgs);
        this.outputLanguageCode = LanguageCodes.normalize(outputLanguageCode);
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
        this.outputLanguageCode = LanguageCodes.normalize(buf.readUtf(32));
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

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.handleActorDialoguePrompt(
                this.jobId,
                this.conversationScopeId,
                this.systemPrompt,
                this.userPrompt,
                this.seedText,
                this.fallbackKey,
                this.fallbackArgs,
                this.outputLanguageCode
        ));
    }

}
