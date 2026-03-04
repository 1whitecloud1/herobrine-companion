package com.whitecloud233.modid.herobrine_companion.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

public class AddItemModifier extends LootModifier {

    // 修复 1 & 2: 移除强制的 chance 字段，将 nbt 改为可选的字符串 (String) 解析
    public static final Codec<AddItemModifier> CODEC = RecordCodecBuilder.create(inst ->
            codecStart(inst).and(
                    inst.group(
                            ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item),
                            Codec.STRING.optionalFieldOf("nbt").forGetter(m -> m.nbtString)
                    )
            ).apply(inst, AddItemModifier::new));

    private final Item item;
    private final Optional<String> nbtString;

    public AddItemModifier(LootItemCondition[] conditionsIn, Item item, Optional<String> nbtString) {
        super(conditionsIn);
        this.item = item;
        this.nbtString = nbtString;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // 父类已经处理了 conditions (包含你的 random_chance)，能进到这里说明条件已满足
        ItemStack stack = new ItemStack(item);

        // 如果 JSON 中写了 nbt 字符串，将其转换为 CompoundTag 并注入物品
        this.nbtString.ifPresent(nbtStr -> {
            try {
                CompoundTag tag = TagParser.parseTag(nbtStr);
                stack.setTag(tag);
            } catch (Exception e) {
                // 如果 NBT 字符串格式写错了，会在控制台报错，方便排查
                System.err.println("解析 Herobrine 物品 NBT 失败: " + nbtStr);
                e.printStackTrace();
            }
        });

        generatedLoot.add(stack);
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}