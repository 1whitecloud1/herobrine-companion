package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

/**
 * 建筑模板同步通道（可选）。
 *
 * <p>独立于主通道，保留主通道全部既有编号，并与旧服务端兼容。
 * 通道脚手架见 {@link OptionalChannel}。</p>
 */
public final class TemplateSyncChannel extends OptionalChannel {

    private static final String VERSION = "2";
    private static final TemplateSyncChannel INSTANCE = new TemplateSyncChannel();

    private TemplateSyncChannel() {
        super("templates", VERSION);
    }

    public static void register() {
        INSTANCE.registerMessage(0, TemplateLibraryRequest.class, TemplateLibraryRequest::encode,
                TemplateLibraryRequest::new, TemplateLibraryRequest::handle, NetworkDirection.PLAY_TO_SERVER);
        INSTANCE.registerMessage(1, TemplateUploadPacket.class, TemplateUploadPacket::encode,
                TemplateUploadPacket::new, TemplateUploadPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        INSTANCE.registerMessage(2, TemplateLibraryReply.class, TemplateLibraryReply::encode,
                TemplateLibraryReply::new, TemplateLibraryReply::handle, NetworkDirection.PLAY_TO_CLIENT);
        INSTANCE.registerMessage(3, TemplatePlacementRequest.class, TemplatePlacementRequest::encode,
                TemplatePlacementRequest::new, TemplatePlacementRequest::handle, NetworkDirection.PLAY_TO_SERVER);
        INSTANCE.registerMessage(4, TemplatePlacementReply.class, TemplatePlacementReply::encode,
                TemplatePlacementReply::new, TemplatePlacementReply::handle, NetworkDirection.PLAY_TO_CLIENT);
        // 建筑预览线框（固定 28 字节，与建筑体积无关）。
        INSTANCE.registerMessage(5, TemplatePreviewPacket.class, TemplatePreviewPacket::encode,
                TemplatePreviewPacket::new, TemplatePreviewPacket::handle, NetworkDirection.PLAY_TO_CLIENT);
    }

    /** 该连接是否声明支持本通道（脚手架见 {@link OptionalChannel#remoteSupports}）。 */
    public static boolean supported(net.minecraft.network.Connection connection) {
        return INSTANCE.remoteSupports(connection);
    }

    public static void send(Object packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void reply(ServerPlayer player, TemplateLibraryReply packet) {
        INSTANCE.sendToPlayer(player, packet);
    }

    public static void replyPlacement(ServerPlayer player, TemplatePlacementReply packet) {
        INSTANCE.sendToPlayer(player, packet);
    }

    /**
     * 推送建筑预览线框。
     *
     * <p><b>必须走本通道</b>：{@link TemplatePreviewPacket} 注册在模板通道上，
     * 用主通道 {@code PacketHandler.sendToPlayer} 发送会得到
     * {@code Invalid message ... on channel herobrine_companion:main}
     * ——注册表在哪个通道，就只能由哪个通道发。</p>
     */
    public static void replyPreview(ServerPlayer player, TemplatePreviewPacket packet) {
        INSTANCE.sendToPlayer(player, packet);
    }
}
