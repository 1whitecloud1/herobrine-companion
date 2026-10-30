// Wire format frozen before C refactoring on 2026-10-22.
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
public class LegacyDestructionGodLightningArcPacket {
    public final Vec3 startPos;

    public final Vec3 endPos;

    public final float width;

    public final int lifetimeTicks;

    public LegacyDestructionGodLightningArcPacket(Vec3 startPos, Vec3 endPos) {
        this(startPos, endPos, 0.05F, 12);
    }

    public LegacyDestructionGodLightningArcPacket(Vec3 startPos, Vec3 endPos, float width, int lifetimeTicks) {
        this.startPos = startPos;
        this.endPos = endPos;
        this.width = width;
        this.lifetimeTicks = lifetimeTicks;
    }

    public LegacyDestructionGodLightningArcPacket(FriendlyByteBuf buf) {
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.endPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.width = buf.readFloat();
        this.lifetimeTicks = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.startPos.x);
        buf.writeDouble(this.startPos.y);
        buf.writeDouble(this.startPos.z);
        buf.writeDouble(this.endPos.x);
        buf.writeDouble(this.endPos.y);
        buf.writeDouble(this.endPos.z);
        buf.writeFloat(this.width);
        buf.writeVarInt(this.lifetimeTicks);
    }
}
