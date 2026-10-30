package com.whitecloud233.modid.herobrine_companion.structure.template;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import java.io.*;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Bounded NBT input and format dispatch. No GUI, registry, network, or filesystem dependencies. */
public final class TemplateImportService {
    private TemplateImportService() { }

    public static ImportedTemplate decode(String filename, byte[] bytes) throws IOException {
        return decode(filename, bytes, Integer.MAX_VALUE);
    }

    /** Validate the game's data version before expanding or scanning any block volume. */
    public static ImportedTemplate decode(String filename, byte[] bytes, int maximumDataVersion) throws IOException {
        if (bytes.length == 0 || bytes.length > TemplateLimits.MAX_FILE_BYTES) throw new IOException("File must be between 1 byte and 8 MiB");
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try (InputStream raw = new ByteArrayInputStream(bytes);
             InputStream unpacked = bytes.length >= 2 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139 ? new GZIPInputStream(raw) : raw;
             DataInputStream data = new DataInputStream(unpacked)) {
            CompoundTag root = NbtIo.read(data, new NbtAccounter(TemplateLimits.MAX_NBT_BYTES));
            CompoundTag schematic = root.contains("Schematic", net.minecraft.nbt.Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
            int version = switch (extension) {
                case "litematic" -> root.contains("MinecraftDataVersion") ? root.getInt("MinecraftDataVersion") : 1343;
                case "schematic" -> 1343;
                case "schem" -> schematic.contains("DataVersion") ? schematic.getInt("DataVersion") : 1631;
                default -> root.contains("DataVersion") ? root.getInt("DataVersion") : 500;
            };
            if (version > maximumDataVersion) throw new IOException("Template data version " + version + " is newer than server "
                    + maximumDataVersion + "; convert the building to the server's Minecraft version first");
            return switch (extension) {
                case "nbt" -> VanillaTemplateDecoder.decode(root);
                case "litematic" -> LitematicTemplateDecoder.decode(root);
                case "schem" -> SpongeTemplateDecoder.decode(root);
                case "schematic" -> LegacyTemplateDecoder.decode(root);
                default -> throw new IOException("Supported formats: .nbt, .litematic, .schem, .schematic");
            };
        } catch (RuntimeException error) {
            throw new IOException("Malformed or oversized template: " + error.getMessage(), error);
        }
    }
}
