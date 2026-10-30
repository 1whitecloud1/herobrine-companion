package com.whitecloud233.herobrine_companion.structure.template;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;

/** Reject missing mod blocks/properties instead of silently replacing them with air on load. */
final class TemplateStateValidator {
    static void validate(CompoundTag structure) throws IOException {
        for (Tag value : structure.getList("palette", Tag.TAG_COMPOUND)) {
            CompoundTag state = (CompoundTag) value;
            ResourceLocation id = ResourceLocation.tryParse(state.getString("Name"));
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) throw new IOException("Missing block: " + state.getString("Name"));
            var definition = BuiltInRegistries.BLOCK.get(id).getStateDefinition();
            CompoundTag properties = state.getCompound("Properties");
            for (String name : properties.getAllKeys()) {
                var property = definition.getProperty(name);
                if (property == null || property.getValue(properties.getString(name)).isEmpty())
                    throw new IOException("Unsupported block property: " + id + "[" + name + "=" + properties.getString(name) + "]");
            }
        }
    }
}
