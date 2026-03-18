package com.whitecloud233.modid.herobrine_companion.client.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

// 绑定到 FORGE 总线，且仅在客户端(CLIENT)生效
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ModrinthUpdateChecker {

    // 标记是否已经检查过，避免玩家切换维度时重复请求
    private static boolean hasChecked = false;

    // 【重要】替换为你在 Modrinth 上的项目 Slug (URL 里的名字) 或 ID
    // 例如你的网页是 https://modrinth.com/mod/herobrine_companion，这里就填 "herobrine_companion"
    private static final String PROJECT_SLUG = "herobrine_companion";

    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        // 确保只在客户端执行，且对象是本地玩家
        if (event.getLevel().isClientSide() && event.getEntity() instanceof LocalPlayer player) {

            // 【新增】检查配置开关，如果关闭了就直接退出
            if (!com.whitecloud233.modid.herobrine_companion.config.Config.enableUpdateChecker) {
                return;
            }

            if (hasChecked) return;
            hasChecked = true; // 记录已检查

            // 从 Forge 运行时获取当前加载的模组版本 (如 0.142)
            String currentVersion = ModList.get().getModContainerById(HerobrineCompanion.MODID)
                    .get().getModInfo().getVersion().toString();

            // 异步执行网络请求，防止卡住游戏主线程
            CompletableFuture.runAsync(() -> checkModrinth(player, currentVersion));
        }
    }

    private static void checkModrinth(LocalPlayer player, String currentVersion) {
        try {
            // 构造 Modrinth API URL。我们添加了过滤条件，只查询 Forge 和 1.20.1 的版本
            // 注意：["forge"] 和 ["1.20.1"] 必须进行 URL 编码
            String url = "https://api.modrinth.com/v2/project/" + PROJECT_SLUG +
                    "/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)) // 设置超时，防止网络不好卡住
                    .build();

            // Modrinth 官方要求必须提供自定义的 User-Agent，否则可能会被拒绝请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "WhiteCloud233/HerobrineCompanion/" + currentVersion)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 解析返回的 JSON 数组。Modrinth 默认将最新的版本放在数组的第 0 位
                JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
                if (!versions.isEmpty()) {
                    JsonObject latestVersionInfo = versions.get(0).getAsJsonObject();
                    String latestVersion = latestVersionInfo.get("version_number").getAsString();

                    // 比较版本号
                    if (isNewerVersion(currentVersion, latestVersion)) {

                        String projectUrl = "https://modrinth.com/mod/" + PROJECT_SLUG;

                        // 网络请求是在异步线程中完成的，发送聊天消息必须回到 Minecraft 主线程
                        Minecraft.getInstance().execute(() -> {
                            // 使用翻译键，并将最新版本号作为参数传入
                            MutableComponent message = Component.translatable("message.herobrine_companion.update_found", latestVersion)
                                    .append(Component.translatable("message.herobrine_companion.update_link")
                                            .setStyle(Style.EMPTY
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, projectUrl))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.link.open")))
                                            )
                                    );
                            player.sendSystemMessage(message);
                        });
                    }
                }
            }
        } catch (Exception e) {
            // 如果没网或请求失败，我们选择静默失败，不要在后台疯狂报错打扰玩家
            e.printStackTrace();
        }
    }

    /**
     * 简单的版本号比较器 (支持 0.142 和 0.143 的对比)
     * @return 如果 latest 大于 current，返回 true
     */
    private static boolean isNewerVersion(String current, String latest) {
        // 去除版本号里的字母(如 -beta, -alpha)，只保留数字和点进行比较
        String[] cParts = current.replaceAll("[^0-9.]", "").split("\\.");
        String[] lParts = latest.replaceAll("[^0-9.]", "").split("\\.");

        int length = Math.max(cParts.length, lParts.length);
        for (int i = 0; i < length; i++) {
            int c = i < cParts.length && !cParts[i].isEmpty() ? Integer.parseInt(cParts[i]) : 0;
            int l = i < lParts.length && !lParts[i].isEmpty() ? Integer.parseInt(lParts[i]) : 0;

            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }
}