package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonObject;
import com.whitecloud233.herobrine_companion.client.network.ClientTemplateLibrary;
import com.whitecloud233.herobrine_companion.structure.template.TemplateEntry;
import java.lang.reflect.Field;
import java.util.List;

/** Exercises index -> exact ID -> typed command, including name resolution and rejection of unsynchronized IDs. */
public final class TemplateLlmChecks {
    private static int checks;
    private static final String ID = "herobrine_companion:uploaded/" + "1".repeat(32) + "/" + "a".repeat(64);
    private static final String OTHER = "herobrine_companion:uploaded/" + "2".repeat(32) + "/" + "b".repeat(64);

    public static void main(String[] args) throws Exception {
        Field snapshot = ClientTemplateLibrary.class.getDeclaredField("entries");
        snapshot.setAccessible(true);
        Object before = snapshot.get(null);
        boolean bootstrapped = bootstrapRegistries();
        try {
            snapshot.set(null, List.of(
                    new TemplateEntry(ID, "海边小屋", "litematic", 12, 8, 9, 500),
                    new TemplateEntry(OTHER, "epichouse10465049.schematic", "schematic", 78, 123, 81, 10838)));

            String result = TemplateLookupSupport.search("小屋", 8, true);
            check(result.contains(ID) && result.contains("海边小屋") && result.contains("\"x\":12"), "Lookup must expose the exact ID and metadata");
            check(TemplateLookupSupport.search("*", 8, true).contains(OTHER), "List imported templates");

            check(build("place_template", ID).equals("place template " + ID + " ~5 ~ ~"), "Typed place_template must accept confirmed uploaded IDs");
            check(build("place", ID, "place_type", "template", "offset_x", "-3").equals("place template " + ID + " ~-3 ~ ~"), "Typed place offsets");

            // 玩家通常只说建筑名字/文件名：模型不需要玩家提供模板 ID。
            check(build("place_template", "海边小屋").equals("place template " + ID + " ~5 ~ ~"), "Exact template name must resolve");
            check(build("place_template", "海边").equals("place template " + ID + " ~5 ~ ~"), "Unique name fragment must resolve");
            check(build("place_template", "epichouse10465049.schematic").equals("place template " + OTHER + " ~5 ~ ~"),
                    "A file-name template must resolve even when it has an extension in its label");
            check(build("place_template", "epichouse10465049").equals("place template " + OTHER + " ~5 ~ ~"), "Name fragment without extension must resolve");
            check(build("place_template", "小屋").equals("place template " + ID + " ~5 ~ ~"), "Unique Chinese name fragment must resolve");
            check(build("place_template", "海") == null, "Single-character fragments must stay rejected");
            check(build("place_template", "minecraft:igloo").equals("place template minecraft:igloo ~5 ~ ~"), "Built-in vanilla templates stay usable");
            // 模组自带模板不再放行：玩家要的是自己导入的那一栋，不能拿随包模板凑数。
            check(build("place_template", "herobrine_companion:live_house") == null,
                    "Mod-bundled templates must NOT be offered as a fallback");
            check(build("place_template", "live_house") == null, "Bare bundled names must be rejected too");
            check(build("place_template", "other:invented") == null, "Ids from uninstalled namespaces must be rejected");
            // 第二道闸门：AIGameCommandExecutor 在执行前会用
            // MinecraftJavaIdResolver.isRegisteredModResource 扫描命令里的每个 ns:path。
            // 建筑模板不是注册表内容，早先只查了注册表，已上传的模板因此会被判
            // "未注册资源 ID" 并在命令执行前拦下（日志里的 [System Block] ... 已阻止）。
            // 模板命中发生在读注册表之前，所以正例不需要启动注册表。
            check(MinecraftJavaIdResolver.hasOnlyRegisteredModResources(build("place_template", ID)),
                    "Confirmed uploaded template command must pass the pre-execution resource check");
            check(MinecraftJavaIdResolver.hasOnlyRegisteredModResources("place template minecraft:igloo ~5 ~ ~"),
                    "Vanilla template commands keep passing");
            check(!MinecraftJavaIdResolver.hasOnlyRegisteredModResources(
                            "place template herobrine_companion:live_house ~5 ~ ~"),
                    "Bundled template must be blocked: it is not in this save");
            if (bootstrapped) {
                // 负例要读注册表：确认闸门仍然拦住真正未注册的模组资源。
                check(!MinecraftJavaIdResolver.hasOnlyRegisteredModResources(
                                "place template herobrine_companion:uploaded/deadbeef ~5 ~ ~"),
                        "Unknown mod resource must still be blocked by the pre-execution check");
            }

            // 歧义/未知/注入一律拒绝，宁可让模型重新查询，也不猜错建筑或拼接命令。
            check(build("place_template", "herobrine_companion:uploaded/a") == null, "Shared id prefix must stay ambiguous");
            check(build("place_template", "herobrine_companion:uploaded/2") == null, "Partial id prefix must stay ambiguous");
            check(build("place_template", "invented house") == null, "Unknown names must be rejected");
            check(build("place_template", ID + "\nstop") == null, "Reject command injection");
            check(build("place_template", OTHER + " stop") == null, "Reject appended command text");
            check(build("place_template", "") == null, "Reject empty ids");

            snapshot.set(null, List.of());
            check(build("place_template", ID) == null && !ClientTemplateLibrary.has(ID), "Disconnected/other-world snapshots must not authorize old IDs");
            check(build("place_template", "海边小屋") == null, "Names must not survive a catalog reset");

            snapshot.set(null, List.of(
                    new TemplateEntry(ID, "海边小屋", "litematic", 12, 8, 9, 500),
                    new TemplateEntry(OTHER, "海边城堡", "schematic", 78, 123, 81, 10838)));
            check(build("place_template", "海边") == null, "Two templates matching one fragment must stay ambiguous");
        } finally {
            snapshot.set(null, before);
        }
        System.out.println("TemplateLlmChecks: " + checks + " checks passed");
    }

    /**
     * 注册表校验（物品/方块/结构…）需要 Minecraft 的 Bootstrap。
     * 独立 JVM 下尽量启动它；失败时只跳过依赖注册表的负例，正例照常执行。
     */
    private static boolean bootstrapRegistries() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }

    private static String build(String action, String templateId, String... extra) {
        JsonObject args = new JsonObject();
        args.addProperty("action", action);
        args.addProperty("template_id", templateId);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            args.addProperty(extra[i], extra[i + 1]);
        }
        return AICommandSkillSupport.buildMinecraftSkillCommand(args, "tp", "lightning", "hero");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
}
