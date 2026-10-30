package com.whitecloud233.modid.herobrine_companion.compat.kubejs;

import com.whitecloud233.modid.herobrine_companion.regression.SourceFixturesChecks;
import java.nio.file.Files;
import java.nio.file.Path;

/** Uses real Minecraft NBT buffers with item/registry fixtures, without booting a loader. */
public final class KubeJSContentCodecChecks {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String base = "src/main/java/com/whitecloud233/modid/herobrine_companion/";
        String production = Files.readString(root.resolve(base + "event/HeroContentSnapshot.java"));
        production = production.replaceFirst("(?m)^package [^;]+;", "").replaceAll("(?m)^import [^;]+;", "");
        int start = production.indexOf("    public static byte[] encode(MinecraftServer server)");
        int end = production.indexOf("    public static byte[] encode(List<HeroRewards.Reward>", start);
        production = production.substring(0, start) + production.substring(end);
        production = production.replace(", HolderLookup.Provider registries", "")
                .replace("item.save(registries)", "item.save(new CompoundTag())")
                .replace("MerchantOffers.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), offers).getOrThrow()", "offers.createTag()")
                .replace("ItemStack.parse(registries, items.getCompound(j)).orElseThrow(() -> new IllegalArgumentException(\"Invalid reward item\"))", "ItemStack.of(items.getCompound(j))")
                .replace("MerchantOffers.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), root.getCompound(\"Trades\")).getOrThrow()", "new MerchantOffers(root.getCompound(\"Trades\"))");
        String limits = Files.readString(root.resolve(base + "event/HeroContentLimits.java"))
                .replaceFirst("(?m)^package [^;]+;", "").replaceAll("(?m)^import [^;]+;", "")
                .replace("public final class ", "public static final class ");
        String source = "import java.util.*; import net.minecraft.nbt.*; import net.minecraft.network.FriendlyByteBuf; import io.netty.buffer.Unpooled; public class ContentFixture {" + STUBS + limits + production + TESTS + "}";
        try (var fixture = SourceFixturesChecks.compile("ContentFixture", source)) {
            int checks = (int) fixture.type().getMethod("run").invoke(null);
            System.out.println("KUBEJS_CONTENT_NBT_OK: " + checks + " transport/boundary checks");
        }
    }
    private static final String STUBS = """
        static class ItemStack {
            int count;
            CompoundTag metadata;
            ItemStack(int count, String name) { this.count = count; metadata = new CompoundTag(); metadata.putString("Name", name); }
            boolean isEmpty() { return count < 1; }
            int getCount() { return count; }
            int getMaxStackSize() { return 64; }
            ItemStack copy() { ItemStack result = new ItemStack(count, ""); result.metadata = metadata.copy(); return result; }
            CompoundTag save(CompoundTag tag) { tag.putInt("Count", count); tag.put("Metadata", metadata.copy()); return tag; }
            static ItemStack of(CompoundTag tag) { ItemStack result = new ItemStack(tag.getInt("Count"), ""); result.metadata = tag.getCompound("Metadata").copy(); return result; }
        }
        static class MerchantOffers extends ArrayList<CompoundTag> {
            MerchantOffers() {}
            MerchantOffers(CompoundTag tag) { var recipes = tag.getList("Recipes", Tag.TAG_COMPOUND); for (int i = 0; i < recipes.size(); i++) add(recipes.getCompound(i).copy()); }
            CompoundTag createTag() { var root = new CompoundTag(); var entries = new ListTag(); forEach(item -> entries.add(item.copy())); root.put("Recipes", entries); return root; }
        }
        static class HeroRewards {
            static class Reward {
                final int id;
                final int requiredTrust;
                List<ItemStack> items;
                Reward(int id, int trust, ItemStack... items) { this.id = id; requiredTrust = trust; this.items = Arrays.asList(items); }
            }
            static Reward copy(Reward reward) { return new Reward(reward.id, reward.requiredTrust, reward.items.stream().map(ItemStack::copy).toArray(ItemStack[]::new)); }
        }
        """;
    private static final String TESTS = """
        static int checks;
        static void check(boolean value) { if (!value) throw new AssertionError("check " + checks); checks++; }
        static void rejected(byte[] input) { try { HeroContentSnapshot.decode(input); throw new AssertionError("Malformed packet accepted"); } catch (IllegalArgumentException expected) { checks++; } }
        static byte[] bytes(CompoundTag tag) { var buffer = new FriendlyByteBuf(Unpooled.buffer()); try { buffer.writeNbt(tag); byte[] data = new byte[buffer.readableBytes()]; buffer.readBytes(data); return data; } finally { buffer.release(); } }
        static CompoundTag root(byte[] data) { var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data)); try { return buffer.readNbt(); } finally { buffer.release(); } }
        public static int run() {
            var item = new ItemStack(3, "Pack Diamond");
            var trades = new MerchantOffers();
            var offer = new CompoundTag(); offer.putInt("Uses", 2); offer.putInt("MaxUses", 12); offer.put("Result", item.save(new CompoundTag())); trades.add(offer);
            byte[] packet = HeroContentSnapshot.encode(List.of(new HeroRewards.Reward(100, 25, item)), trades);
            var decoded = HeroContentSnapshot.decode(packet);
            check(decoded.rewards().get(0).id == 100);
            check(decoded.rewards().get(0).requiredTrust == 25);
            check(decoded.rewards().get(0).items.get(0).count == 3);
            check(decoded.rewards().get(0).items.get(0).metadata.getString("Name").equals("Pack Diamond"));
            check(decoded.trades().get(0).getInt("MaxUses") == 12);
            decoded.rewards().get(0).items.get(0).metadata.putString("Name", "mutated");
            check(item.metadata.getString("Name").equals("Pack Diamond"));
            check(HeroContentSnapshot.decode(HeroContentSnapshot.encode(List.of(), new MerchantOffers())).rewards().isEmpty());
            rejected(Arrays.copyOf(packet, packet.length + 1));
            rejected(new byte[HeroContentLimits.MAX_PAYLOAD_BYTES + 1]);
            var duplicate = root(packet); var rewards = duplicate.getList("Rewards", Tag.TAG_COMPOUND); rewards.add(rewards.getCompound(0).copy()); rejected(bytes(duplicate));
            var invalid = root(packet); invalid.getList("Rewards", Tag.TAG_COMPOUND).getCompound(0).putInt("Trust", -1); rejected(bytes(invalid));
            var excessive = root(packet); var list = excessive.getList("Rewards", Tag.TAG_COMPOUND); while (list.size() <= HeroContentLimits.MAX_REWARDS) list.add(list.getCompound(0).copy()); rejected(bytes(excessive));
            rejected(bytes(new CompoundTag()));
            return checks;
        }
        """;
}
