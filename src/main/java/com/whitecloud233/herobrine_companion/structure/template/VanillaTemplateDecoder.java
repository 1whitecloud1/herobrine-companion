package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.nbt.*;
import java.io.IOException;

/** Structure-block .nbt imports use the first palette, matching a deterministic building variant. */
final class VanillaTemplateDecoder {
    static ImportedTemplate decode(CompoundTag root) throws IOException {
        int[] size = TemplateNbtBuilder.position(root.getList("size", Tag.TAG_INT));
        TemplateNbtBuilder out = new TemplateNbtBuilder(size[0], size[1], size[2]);
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty()) palette = root.getList("palettes", Tag.TAG_LIST).getList(0);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (Tag value : blocks) {
            CompoundTag block = (CompoundTag) value;
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size()) throw new IOException("Invalid palette index");
            int[] pos = TemplateNbtBuilder.position(block.getList("pos", Tag.TAG_INT));
            out.block(pos[0], pos[1], pos[2], palette.getCompound(state),
                    block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt") : null);
        }
        return out.finish("nbt", root.contains("DataVersion") ? root.getInt("DataVersion") : 500);
    }
}
