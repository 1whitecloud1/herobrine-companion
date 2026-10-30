package com.whitecloud233.herobrine_companion.network;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exercises the actual vanilla system_chat wire codec and the production message wrappers. */
public final class SystemChatCodecChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        // The standalone codec JVM has no mod discovery phase or modded feature flags.
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        UUID id = UUID.fromString("a56ba812-03f2-443b-814f-4fa2f89c0d1d");
        for (String key : new String[]{"uuid", "preparing", "add_fresh_fail"}) {
            Component decoded = roundTrip(Component.translatable(
                    "message.herobrine_companion.chat_summon.debug." + key, id.toString()));
            check("UUID text survives " + key, contents(decoded).getArgs()[0].equals(id.toString()));
        }

        Set<String> choices = Set.of("follow", "standby");
        Component nested = Component.literal("nested").withStyle(ChatFormatting.AQUA);
        for (String[] wrapper : new String[][]{
                {"com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager", "system"},
                {"com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager", "chat"},
                {"com.whitecloud233.herobrine_companion.event.HeroCompanionCommands", "message"}}) {
            var method = Class.forName(wrapper[0]).getDeclaredMethod(wrapper[1], String.class, Object[].class);
            method.setAccessible(true);
            Component message = (Component) method.invoke(null, "test", new Object[]{id, choices, null, nested, 7, true});
            check("wrapper preserves boolean before encoding", contents(message).getArgs()[5].equals(true));
            Object[] decoded = contents(roundTrip(message)).getArgs();
            check("wrapper escapes UUID", decoded[0].equals(id.toString()));
            check("wrapper escapes collection", decoded[1].equals(choices.toString()));
            check("wrapper escapes null", decoded[2].equals("null"));
            check("wrapper preserves nested style", decoded[3] instanceof Component component
                    && component.getStyle().equals(nested.getStyle()));
            check("wrapper preserves number", decoded[4] instanceof Number number && number.intValue() == 7);
            // NBT represents true as a byte; the vanilla generic argument codec reads it as a Number.
            check("boolean survives NBT encoding", decoded[5].equals(true)
                    || decoded[5] instanceof Number number && number.byteValue() == 1);
        }

        for (Object invalid : new Object[]{id, choices, null}) {
            boolean rejected = false;
            try {
                roundTrip(Component.translatable("test", invalid));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            check("original unsafe argument is rejected", rejected);
        }
        System.out.println("SystemChatCodecChecks: " + checks + " checks passed");
    }

    private static Component roundTrip(Component message) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ClientboundSystemChatPacket.STREAM_CODEC.encode(buffer, new ClientboundSystemChatPacket(message, false));
            var decoded = ClientboundSystemChatPacket.STREAM_CODEC.decode(buffer);
            check("packet fully consumed", !buffer.isReadable());
            check("chat overlay flag retained", !decoded.overlay());
            return decoded.content();
        } finally {
            buffer.release();
        }
    }

    private static TranslatableContents contents(Component component) {
        if (component.getContents() instanceof TranslatableContents contents) return contents;
        throw new AssertionError("Expected translatable component");
    }

    private static void check(String label, boolean success) {
        if (!success) throw new AssertionError(label);
        checks++;
    }
}
