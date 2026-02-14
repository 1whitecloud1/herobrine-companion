package com.whitecloud233.herobrine_companion.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.Optional;

public class AddItemModifier extends LootModifier {

    public static final MapCodec<AddItemModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).and(
                    inst.group(
                            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(m -> m.item),
                            Codec.STRING.optionalFieldOf("nbt").forGetter(m -> m.nbtString)
                    )
            ).apply(inst, AddItemModifier::new)
    );

    private final Item item;
    private final Optional<String> nbtString;

    public AddItemModifier(LootItemCondition[] conditionsIn, Item item, Optional<String> nbtString) {
        super(conditionsIn);
        this.item = item;
        this.nbtString = nbtString;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // 启用调试日志
        System.out.println(">>> Loot Modifier Triggered! Table: " + context.getQueriedLootTableId());
        
        ItemStack stack = new ItemStack(item);
        
        if (nbtString.isPresent()) {
            try {
                // 尝试解析 NBT
                CompoundTag tag = TagParser.parseTag(nbtString.get());
                // 适配 1.20.5+ DataComponents
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                System.out.println(">>> Applied NBT: " + tag);
            } catch (Exception e) {
                System.err.println(">>> Failed to parse NBT for item " + item + ": " + nbtString.get());
                e.printStackTrace();
            }
        }
        
        generatedLoot.add(stack);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
