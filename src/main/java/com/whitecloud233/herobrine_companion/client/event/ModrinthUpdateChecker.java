package com.whitecloud233.herobrine_companion.client.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

// 1.21.1 NeoForge: 绑定到 GAME 总线 (等同于旧版的 FORGE 总线)，仅客户端生效
@SuppressWarnings("ALL")
@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ModrinthUpdateChecker {

    private static boolean hasChecked = false;
    private static final String PROJECT_SLUG = "herobrine-companion"; // 请确保使用的是连字符而不是下划线

    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof LocalPlayer player) {

            // 检查配置开关
            if (!Config.enableUpdateChecker) return;
            if (hasChecked) return;
            hasChecked = true;

            // NeoForge 获取版本号的方式与 Forge 一致
            String currentVersion = ModList.get().getModContainerById(HerobrineCompanion.MODID)
                    .get().getModInfo().getVersion().toString();

            CompletableFuture.runAsync(() -> checkModrinth(player, currentVersion));
        }
    }

    private static void checkModrinth(LocalPlayer player, String currentVersion) {
        try {
            // 注意这里改成了 neoforge 作为 loader 过滤条件
            String url = "https://api.modrinth.com/v2/project/" + PROJECT_SLUG +
                    "/version?loaders=%5B%22neoforge%22%5D&game_versions=%5B%221.21.1%22%5D";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "WhiteCloud233/HerobrineCompanion/" + currentVersion)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
                if (!versions.isEmpty()) {
                    JsonObject latestVersionInfo = versions.get(0).getAsJsonObject();
                    String latestVersion = latestVersionInfo.get("version_number").getAsString();

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        String projectUrl = "https://modrinth.com/mod/" + PROJECT_SLUG;

                        Minecraft.getInstance().execute(() -> {
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
            // 静默失败
        }
    }

    private static boolean isNewerVersion(String current, String latest) {
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