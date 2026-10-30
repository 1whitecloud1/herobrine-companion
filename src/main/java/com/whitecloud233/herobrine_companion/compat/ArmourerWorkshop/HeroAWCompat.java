package com.whitecloud233.herobrine_companion.compat.ArmourerWorkshop;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;

/**
 * 时装工坊（Armourer's Workshop）兼容层 —— 1.21.1 NeoForge。
 *
 * <h2>Hero 的时装 Profile 由数据包声明，不写任何 Java 集成代码</h2>
 * AW 从 3.x 起把「哪些实体能穿时装、每个槽位容量多少」完全外置成数据包资源：
 * <pre>data/armourers_workshop/skin/profiles/&lt;name&gt;.json</pre>
 * 本模组对应的文件是
 * {@code data/armourers_workshop/skin/profiles/herobrine_companion.json}，
 * 内容镜像 AW 自带的 {@code player.json}，从而把 {@code herobrine_companion:hero}
 * 声明为「玩家骨架 + 玩家槽位」。
 *
 * <p>这正是 AW 官方接入所有人形 NPC 模组的方式。在 AW 的 jar 中，
 * {@code minecolonies} / {@code easy_npc} / {@code mca} / {@code recruits} / {@code taterzens} /
 * {@code guardvillagers} / {@code humancompanions} / {@code workers} / {@code modulargolems}
 * 等模组<b>只有 JSON、不存在任何 Java 引用</b>（AW 只额外内置了槽位组定义
 * {@code default_player_slots} / {@code default_mob_slots}）。
 * 因此这条路线不依赖 AW 的任何内部类。
 *
 * <h2>为什么弃用了先前的手写绑定</h2>
 * 旧实现在 {@code EntityRenderersEvent.AddLayers} 阶段取 Hero 与玩家的渲染器，
 * 直接调用 AW 内部的 {@code EntityRendererContext.of(...)} 并把<b>本地玩家</b>的
 * profile 强行套到 Hero 上。这有三个问题：
 * <ol>
 *   <li>{@code EntityRendererContext.of} 的参数类型在 AW 3.3+ 由 {@code EntityRenderer}
 *       改成了 {@code api.client.IEntityRenderer}，直接引用只能在单一版本上工作
 *       （换版本会 {@code NoSuchMethodError}，被 catch 吞掉后功能静默失效）。</li>
 *   <li>拿的是<b>本地玩家</b>的 profile，多人在场时其他玩家的 Hero 也会套用错的 profile。</li>
 *   <li>AW 自己已通过 {@code SkinRendererManager$ProfileLoader} +
 *       {@code ModEntityProfiles.addListener} 自动把 profile 装配到渲染上下文
 *       （实测两个版本都有这条链路），手写绑定属于重复且易错。</li>
 * </ol>
 *
 * <p>本类因此<b>不得</b>引用任何 {@code moe.plushie.armourers_workshop.*} 类型，
 * 只保留与 AW 版本无关的能力。
 */
public final class HeroAWCompat {

    private static final String AW_MOD_ID = "armourers_workshop";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String AW_OPEN_WARDROBE_PACKET =
            "moe.plushie.armourers_workshop.core.network.OpenWardrobePacket";
    private static final String AW_CUSTOM_PACKET =
            "moe.plushie.armourers_workshop.core.network.CustomPacket";
    private static final String AW_NETWORK_MANAGER =
            "moe.plushie.armourers_workshop.init.platform.NetworkManager";

    private HeroAWCompat() {
    }

    /**
     * AW 是否已加载。只依赖 NeoForge 的模组列表，不触碰 AW 的任何类型，
     * 因此在 AW 未安装、或安装了任意版本时都安全。
     */
    public static boolean isLoaded() {
        try {
            return ModList.get() != null && com.whitecloud233.herobrine_companion.compat.safety.CompatPreflight.available("armourers_workshop");
        } catch (Throwable t) {
            com.whitecloud233.herobrine_companion.compat.safety.CompatSafety.rethrowFatal(t);
            return false;
        }
    }

    /**
     * 判断物品是否来自时装工坊。只用原版注册表，不会触发 AW 的类加载。
     */
    public static boolean isAwItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return registryName != null && registryName.getNamespace().equals(AW_MOD_ID);
    }

    /**
     * 请求 Armourer's Workshop 为指定实体打开时装工坊。
     *
     * <p>不能模拟 AW 的「打开玩家时装栏」快捷键：那个快捷键始终把本地玩家的实体 ID
     * 发给服务端。这里直接构造 AW 的 OpenWardrobePacket 并传入 Hero 实体，服务端会按
     * 该实体的 profile 打开对应菜单。反射隔离 AW 的内部类型，保证 AW 未安装时安全，
     * 也兼容目前 3.2.7 与 3.4.x 仍共用的 packet/network API。
     *
     * @return 是否成功把打开请求发送给服务端
     */
    public static boolean openWardrobeFor(Entity entity) {
        if (!isLoaded() || entity == null) {
            return false;
        }

        try {
            ClassLoader classLoader = HeroAWCompat.class.getClassLoader();
            Class<?> packetClass = Class.forName(AW_OPEN_WARDROBE_PACKET, true, classLoader);
            Object packet = packetClass.getConstructor(Entity.class).newInstance(entity);
            Class<?> customPacketClass = Class.forName(AW_CUSTOM_PACKET, true, classLoader);
            Class<?> networkManagerClass = Class.forName(AW_NETWORK_MANAGER, true, classLoader);
            networkManagerClass.getMethod("sendToServer", customPacketClass).invoke(null, packet);
            return true;
        } catch (InvocationTargetException e) {
            LOGGER.warn("[Herobrine Companion] Armourer's Workshop rejected the wardrobe open request.",
                    e.getCause() == null ? e : e.getCause());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("[Herobrine Companion] Could not open the Armourer's Workshop wardrobe for entity {}.",
                    entity.getId(), e);
        }
        return false;
    }
}
