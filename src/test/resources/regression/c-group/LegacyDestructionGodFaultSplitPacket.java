// Wire format frozen before C refactoring on 2026-10-22.
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
public class LegacyDestructionGodFaultSplitPacket {
    public final Vec3 origin;

    public final Vec3 direction;

    public final float length;

    public final int terrainHalfWidth;

    public final int splitDistance;

    public final int chargeTicks;

    public final int lifetime;

    public LegacyDestructionGodFaultSplitPacket(Vec3 origin, Vec3 direction, float length, int terrainHalfWidth, int splitDistance, int chargeTicks, int lifetime) {
        this.origin = origin;
        this.direction = direction;
        this.length = length;
        this.terrainHalfWidth = terrainHalfWidth;
        this.splitDistance = splitDistance;
        this.chargeTicks = chargeTicks;
        this.lifetime = lifetime;
    }

    public LegacyDestructionGodFaultSplitPacket(FriendlyByteBuf buf) {
        this.origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.direction = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.length = buf.readFloat();
        this.terrainHalfWidth = buf.readVarInt();
        this.splitDistance = buf.readVarInt();
        this.chargeTicks = buf.readVarInt();
        this.lifetime = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.origin.x);
        buf.writeDouble(this.origin.y);
        buf.writeDouble(this.origin.z);
        buf.writeDouble(this.direction.x);
        buf.writeDouble(this.direction.y);
        buf.writeDouble(this.direction.z);
        buf.writeFloat(this.length);
        buf.writeVarInt(this.terrainHalfWidth);
        buf.writeVarInt(this.splitDistance);
        buf.writeVarInt(this.chargeTicks);
        buf.writeVarInt(this.lifetime);
    }
}
