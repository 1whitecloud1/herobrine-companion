package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.destructiongod.network.*;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketChallengeArenaSlice;
import com.whitecloud233.modid.herobrine_companion.regression.SourceFixturesChecks;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;

/** Checks exact pre-refactor wire bytes, not just mutually compatible new encoder/decoder pairs. */
public final class EffectPacketCodecChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        for (boolean flash : new boolean[]{false,true}) {
            Vec3 a=new Vec3(30_000_000.125,-0.0,-30_000_000.875), b=new Vec3(Math.nextUp(a.x),-64.25,2D/3D), c=new Vec3(-.5,.75,.25);
            packet(PaleLightningArcPacket.class,a,b);
            packet(DestructionGodLightningArcPacket.class,a,b,1.25F,257);
            packet(DestructionGodLightningPacket.class,a,b,.875F);
            packet(DestructionGodOrbPacket.class,a,b,129,.25F,9.5F,18F);
            packet(DestructionGodFaultSplitPacket.class,a,b,63.25F,4,128,257,600);
            packet(DestructionGodThunderSkyNetPacket.class,a,128.25D,32.5F,257,0x89ABCDEF);
            packet(SPacketChallengeArenaSlice.class,a,b,c,-.25F,64.5F,129,257,flash);
        }
        System.out.println("EffectPacketCodecChecks: " + checks + " checks passed");
    }
    private static void packet(Class<?> type,Object... args) throws Exception {
        String name="Legacy"+type.getSimpleName();
        Class<?>[] signature=Arrays.stream(args).map(value -> value instanceof Integer ? int.class
                : value instanceof Float ? float.class : value instanceof Double ? double.class
                : value instanceof Boolean ? boolean.class : value.getClass()).toArray(Class<?>[]::new);
        try (var fixture=SourceFixturesChecks.compile(name,SourceFixturesChecks.resource(name+".java"))) {
            Object before=fixture.type().getConstructor(signature).newInstance(args);
            Object now=type.getConstructor(signature).newInstance(args);
            FriendlyByteBuf legacy=new FriendlyByteBuf(Unpooled.buffer()), current=new FriendlyByteBuf(Unpooled.buffer());
            FriendlyByteBuf decodedBytes=null, roundTrip=new FriendlyByteBuf(Unpooled.buffer());
            try {
                fixture.type().getMethod("encode",FriendlyByteBuf.class).invoke(before,legacy);
                type.getMethod("encode",FriendlyByteBuf.class).invoke(now,current);
                byte[] expected=bytes(legacy);
                check(type.getSimpleName()+" exact legacy bytes",Arrays.equals(expected,bytes(current)));
                decodedBytes=new FriendlyByteBuf(Unpooled.wrappedBuffer(expected));
                Object decoded=type.getConstructor(FriendlyByteBuf.class).newInstance(decodedBytes);
                check(type.getSimpleName()+" consumes exactly one payload",!decodedBytes.isReadable());
                type.getMethod("encode",FriendlyByteBuf.class).invoke(decoded,roundTrip);
                check(type.getSimpleName()+" legacy decode/encode retains doubles and tail fields",Arrays.equals(expected,bytes(roundTrip)));
            } finally {
                legacy.release();current.release();roundTrip.release();if(decodedBytes!=null)decodedBytes.release();
            }
        }
    }
    private static byte[] bytes(FriendlyByteBuf buffer) {
        byte[] result=new byte[buffer.writerIndex()];buffer.getBytes(0,result);return result;
    }
    private static void check(String label,boolean condition) { if(!condition)throw new AssertionError(label);checks++; }
}
