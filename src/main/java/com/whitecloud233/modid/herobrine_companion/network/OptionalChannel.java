package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 「可选附加通道」的公共骨架。
 *
 * <p>项目除主通道 {@code PacketHandler} 外还有两条<b>有意独立</b>的通道：
 * 配置同步与建筑模板同步。它们独立的原因是正确的——主通道已发布 78 个固定编号，
 * 把新消息塞进去会破坏与旧服务端的兼容；而独立通道可以用
 * {@code acceptMissingOr} 协商，旧端不认就整条不启用。</p>
 *
 * <p>但两条通道外面的脚手架此前各写一遍且逐句同构：建 {@link SimpleChannel}、
 * 同一套 {@code VERSION} 常量的三个 lambda、{@code registerMessage} 的
 * {@code Optional.of(direction)} 包装、{@code isRemotePresent} 判定、
 * 乃至"回执不按 supported 过滤"那段解释群组服转发下握手表可能缺失的注释。</p>
 *
 * <p><b>为什么不直接并成一个类</b>：两条通道必须各自持有独立的
 * {@code ResourceLocation} 与 {@code VERSION}，合并会使版本协商互相牵连——
 * 配置报文加字段时不该把模板通道一起判死。因此这里只抽"怎么建、怎么发"，
 * 消息清单与回执内容留在子类。</p>
 */
public abstract class OptionalChannel {

    private final SimpleChannel channel;

    /**
     * @param channelName 通道名（会拼成 {@code herobrine_companion:<name>}）
     * @param version     协议版本；参与协商，改动需同步两端
     */
    protected OptionalChannel(String channelName, String version) {
        this.channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(HerobrineCompanion.MODID, channelName),
                () -> version,
                NetworkRegistry.acceptMissingOr(version),
                NetworkRegistry.acceptMissingOr(version));
    }

    /** 该连接是否声明支持本通道。 */
    public final boolean remoteSupports(Connection connection) {
        return connection != null && this.channel.isRemotePresent(connection);
    }

    /** 发送到服务端（C→S 方向）。 */
    public final void sendToServer(Object packet) {
        this.channel.sendToServer(packet);
    }

    /**
     * 发送给单个玩家（S→C 方向）。
     *
     * <p><b>刻意不按 {@link #remoteSupports} 过滤</b>：握手表（{@code S2CModList} 的
     * channels）在混合端、群组服转发下可能缺失，而发包本身不检查远端是否存在 ——
     * 缺通道的一方会静默丢弃。按表过滤会让"通道其实可用"的连接永远收不到数据。</p>
     */
    public final void sendToPlayer(ServerPlayer player, Object packet) {
        if (player == null) {
            return;
        }
        this.channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** 注册一条消息。 */
    protected final <MSG> void registerMessage(int id, Class<MSG> type,
                                               java.util.function.BiConsumer<MSG, net.minecraft.network.FriendlyByteBuf> encoder,
                                               java.util.function.Function<net.minecraft.network.FriendlyByteBuf, MSG> decoder,
                                               java.util.function.BiConsumer<MSG, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context>> handler,
                                               NetworkDirection direction) {
        this.channel.registerMessage(id, type, encoder, decoder, handler, Optional.of(direction));
    }
}
