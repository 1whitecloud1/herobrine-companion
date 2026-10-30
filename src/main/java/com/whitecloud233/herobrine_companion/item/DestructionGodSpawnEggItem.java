package com.whitecloud233.herobrine_companion.item;

import com.whitecloud233.herobrine_companion.util.GlitchText;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.util.function.Supplier;

/**
 * 毁灭之神生成蛋：多了一层「名字坏掉了」的表现。
 *
 * <p>名字仍取自语言文件的 {@code item.herobrine_companion.destruction_god_herobrine_spawn_egg}，
 * 只是客户端渲染时交给 {@link GlitchText} 拆成逐字符样式，于是工具提示、快捷栏上方的
 * 手持物品名等一切走 {@code getName()} 的地方都会同步抖动。生成蛋本身的行为
 * （右键生成、发射器、颜色分层）完全沿用 {@link DeferredSpawnEggItem}，没有改动。</p>
 *
 * <p>只影响显示：文本内容不变，因此存档里的物品、服务端逻辑、AI 物品描述都不受影响；
 * 专用服务端上 {@code dist} 不为 CLIENT，直接返回原始译名。</p>
 */
public class DestructionGodSpawnEggItem extends DeferredSpawnEggItem {

    public DestructionGodSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type,
                                      int backgroundColor, int highlightColor, Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = Component.translatable(this.getDescriptionId(stack));
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return name;
        }
        return GlitchText.animate(name.getString(), Util.getMillis());
    }
}
