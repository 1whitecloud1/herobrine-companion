// Wire format frozen before C refactoring on 2026-10-22.
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
public class LegacyPaleLightningArcPacket {
    public final Vec3 startPos;

    public final Vec3 endPos;

    public LegacyPaleLightningArcPacket(Vec3 startPos, Vec3 endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public LegacyPaleLightningArcPacket(FriendlyByteBuf buf) {
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.endPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.startPos.x);
        buf.writeDouble(this.startPos.y);
        buf.writeDouble(this.startPos.z);
        buf.writeDouble(this.endPos.x);
        buf.writeDouble(this.endPos.y);
        buf.writeDouble(this.endPos.z);
    }
}
