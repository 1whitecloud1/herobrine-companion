package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.init.ModEntities;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;

public record HeroContentSnapshot(List<HeroRewards.Reward> rewards, MerchantOffers trades) {
    public HeroContentSnapshot {
        if (rewards.size() > HeroContentLimits.MAX_REWARDS || trades.size() > HeroContentLimits.MAX_TRADES) throw new IllegalArgumentException("Oversized Herobrine content");
        rewards = rewards.stream().map(HeroRewards::copy).toList();
    }

    public static byte[] encode(MinecraftServer server) {
        HeroEntity preview = ModEntities.HERO.get().create(server.overworld());
        if (preview == null) throw new IllegalStateException("Cannot create Herobrine trade preview");
        preview.setTrustLevel(100);
        MerchantOffers offers = HeroTrades.getOffers(preview);
        if (offers.size() > HeroContentLimits.MAX_TRADES) throw new IllegalArgumentException("Too many preview trades");
        return encode(HeroRewards.snapshot(), offers, server.registryAccess());
    }

    public static byte[] encode(List<HeroRewards.Reward> definitions, MerchantOffers offers, HolderLookup.Provider registries) {
        if (definitions.size() > HeroContentLimits.MAX_REWARDS || offers.size() > HeroContentLimits.MAX_TRADES) throw new IllegalArgumentException("Oversized Herobrine content");
        CompoundTag root = new CompoundTag();
        ListTag rewards = new ListTag();
        for (HeroRewards.Reward reward : definitions) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", reward.id);
            entry.putInt("Trust", reward.requiredTrust);
            ListTag items = new ListTag();
            for (ItemStack item : reward.items) items.add(item.save(registries));
            entry.put("Items", items);
            rewards.add(entry);
        }
        root.put("Rewards", rewards);
        root.put("Trades", MerchantOffers.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), offers).getOrThrow());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(root);
            if (buffer.readableBytes() > HeroContentLimits.MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Herobrine content exceeds 1 MiB");
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } finally { buffer.release(); }
    }

    public static HeroContentSnapshot decode(byte[] data, HolderLookup.Provider registries) {
        if (data.length > HeroContentLimits.MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Oversized Herobrine content");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            CompoundTag root = buffer.readNbt();
            if (root == null || buffer.isReadable() || !root.contains("Rewards", Tag.TAG_LIST) || !root.contains("Trades", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Malformed Herobrine content");
            if (root.getCompound("Trades").getList("Recipes", Tag.TAG_COMPOUND).size() > HeroContentLimits.MAX_TRADES) throw new IllegalArgumentException("Too many trades");
            ListTag entries = root.getList("Rewards", Tag.TAG_COMPOUND);
            if (entries.size() > HeroContentLimits.MAX_REWARDS) throw new IllegalArgumentException("Too many rewards");
            List<HeroRewards.Reward> rewards = new ArrayList<>();
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                int id = HeroContentLimits.rewardId(entry.getInt("Id"));
                int trust = HeroContentLimits.trust(entry.getInt("Trust"));
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate reward ID");
                ListTag items = entry.getList("Items", Tag.TAG_COMPOUND);
                if (items.size() > HeroContentLimits.MAX_ITEMS_PER_REWARD) throw new IllegalArgumentException("Too many reward items");
                List<ItemStack> stacks = new ArrayList<>();
                for (int j = 0; j < items.size(); j++) stacks.add(HeroContentLimits.item(ItemStack.parse(registries, items.getCompound(j)).orElseThrow(() -> new IllegalArgumentException("Invalid reward item"))));
                rewards.add(new HeroRewards.Reward(id, trust, stacks.toArray(ItemStack[]::new)));
            }
            return new HeroContentSnapshot(rewards, MerchantOffers.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), root.getCompound("Trades")).getOrThrow());
        } finally { buffer.release(); }
    }
}
