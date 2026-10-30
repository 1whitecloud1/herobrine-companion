// Wire format frozen before C refactoring on 2026-10-22.
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
public class LegacyDestructionGodThunderSkyNetPacket {
    public final Vec3 center;

    public final double cloudY;

    public final float radius;

    public final int lifetime;

    public final int seed;

    public LegacyDestructionGodThunderSkyNetPacket(Vec3 center, double cloudY, float radius, int lifetime, int seed) {
        this.center = center;
        this.cloudY = cloudY;
        this.radius = radius;
        this.lifetime = lifetime;
        this.seed = seed;
    }

    public LegacyDestructionGodThunderSkyNetPacket(FriendlyByteBuf buf) {
        this.center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.cloudY = buf.readDouble();
        this.radius = buf.readFloat();
        this.lifetime = buf.readVarInt();
        this.seed = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.center.x);
        buf.writeDouble(this.center.y);
        buf.writeDouble(this.center.z);
        buf.writeDouble(this.cloudY);
        buf.writeFloat(this.radius);
        buf.writeVarInt(this.lifetime);
        buf.writeInt(this.seed);
    }
}
