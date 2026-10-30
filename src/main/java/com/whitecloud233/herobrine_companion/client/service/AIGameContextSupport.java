package com.whitecloud233.herobrine_companion.client.service;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientTemplateLibrary;
import com.whitecloud233.herobrine_companion.api.compat.HeroAccessoriesCompat;
import com.whitecloud233.herobrine_companion.compat.curios.HeroCuriosCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.structure.template.TemplateEntry;
import com.whitecloud233.herobrine_companion.util.AiItemNaming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AIGameContextSupport {
    /** 提示词里最多列出的模板数；更多的交给 registry_lookup，避免长目录挤占上下文。 */
    private static final int MAX_TEMPLATES_IN_PROMPT = 12;
    private static final int MAX_SERVER_PLAYERS_IN_PROMPT = 12;
    private static final int MAX_NEARBY_PLAYERS_IN_PROMPT = 6;
    private static final double NEARBY_PLAYER_DETAIL_RADIUS = 96.0D;
    private static final double HERO_PERCEPTION_RADIUS = 256.0D;
    private static final List<EquipmentSlot> PLAYER_EQUIPMENT_SLOTS = List.of(
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final List<EquipmentSlot> HERO_ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private AIGameContextSupport() {}

    /** 使用已同步的实体心境，不查询模型、不写入历史；跨会话同样不借用其他玩家的 Hero。 */
    static String getHeroSpeechStyle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return "";
        }
        HeroEntity hero = findPerceivedHero(mc);
        return hero == null ? "" : HeroSpeechStyle.promptFor(hero.getMindState());
    }

    static String getDynamicGameData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return "";
        StringBuilder data = new StringBuilder("\n\n[System Inject: Current World Data]:\n");
        data.append("- Player Permission: ").append(mc.player.hasPermissions(2) ? "[Cheats Enabled] (Can use tools freely).\n" : "[Cheats Disabled] (CANNOT use physical alteration tools. Decline gently if asked).\n");
        data.append("- Available Dimensions: ");
        for (ResourceKey<Level> levelKey : mc.player.connection.levels()) data.append(levelKey.location()).append(", ");
        data.append("\n");
        try {
            var registryAccess = mc.player.connection.registryAccess();
            var structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            data.append("- Your Unstable Zone Structure ID: ");
            for (ResourceLocation loc : structureRegistry.keySet()) {
                if ((loc.getNamespace().equals(HerobrineCompanion.MODID) && loc.getPath().equals("unstable_zone")) || loc.getPath().contains("village")) data.append(loc).append(", ");
            }
            data.append("\n- Available Biomes: ");
            var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            for (ResourceLocation loc : biomeRegistry.keySet()) {
                if (loc.getNamespace().equals(HerobrineCompanion.MODID) || loc.getNamespace().equals("twilightforest") || loc.getPath().contains("cherry")) data.append(loc).append(", ");
            }
            data.append("\n");
        } catch (Exception ignored) {}
        appendBuildingTemplates(data);
        appendOtherPlayerAwareness(data, mc);
        data.append("\n- [Omniscient Eye] Current Environment:\n");
        appendPlayerEquipment(data, mc.player);
        appendHeroOutfit(data, mc);
        data.append("  * Entities within 20 blocks (You pity monsters): ");
        if (mc.level != null) {
            int entityCount = 0;
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                if (entity != mc.player && entity.distanceTo(mc.player) < 20) {
                    data.append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())).append(", ");
                    if (++entityCount > 15) { data.append("...and more"); break; }
                }
            }
            if (entityCount == 0) data.append("Peaceful, no entities.");
        }
        return data.append("\n").toString();
    }

    /** 玩家当前穿戴/持有的装备（主手+副手+护甲），只列非空槽位。 */
    private static void appendPlayerEquipment(StringBuilder data, Player player) {
        data.append("  * Player Equipment: ");
        List<String> parts = new ArrayList<>();
        for (EquipmentSlot slot : PLAYER_EQUIPMENT_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                parts.add(slot.getName() + "=" + AiItemNaming.describe(stack));
            }
        }
        if (parts.isEmpty()) {
            data.append("(none — barehanded, no armor)\n");
        } else {
            data.append(String.join(", ", parts)).append("\n");
        }
    }

    /** Herobrine 自己的装扮感知：护甲+双手+背部(Curios)+饰品(Accessories)。 */
    private static void appendHeroOutfit(StringBuilder data, Minecraft mc) {
        data.append("- [Omniscient Eye] Your current outfit:\n");
        if (mc.level == null) {
            return;
        }
        HeroEntity hero = findPerceivedHero(mc);
        if (hero == null) {
            data.append("  * (Your physical body is not loaded in the current area — you can only feel your attire when it is near.)\n");
            return;
        }

        List<String> parts = new ArrayList<>();
        for (EquipmentSlot slot : HERO_ARMOR_SLOTS) {
            ItemStack stack = hero.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                parts.add(slot.getName() + "=" + AiItemNaming.describe(stack));
            }
        }
        if (!hero.getMainHandItem().isEmpty()) {
            parts.add("mainhand=" + AiItemNaming.describe(hero.getMainHandItem()));
        }
        if (!hero.getOffhandItem().isEmpty()) {
            parts.add("offhand=" + AiItemNaming.describe(hero.getOffhandItem()));
        }
        if (ModList.get().isLoaded("curios")) {
            ItemStack back = HeroCuriosCompat.getBackSlotItem(hero);
            if (!back.isEmpty()) {
                parts.add("back=" + AiItemNaming.describe(back));
            }
        }
        String accessories = HeroAccessoriesCompat.describeEquippedItems(hero);
        if (!accessories.isEmpty()) {
            parts.add(accessories);
        }

        if (parts.isEmpty()) {
            data.append("  * (You are wearing nothing — bare and unadorned.)\n");
        } else {
            data.append("  * ").append(String.join(", ", parts)).append("\n");
        }
    }

    /** 优先返回本玩家结伴的 Hero，否则取最近的无主 Hero，避免借用其他玩家的状态。 */
    private static HeroEntity findPerceivedHero(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        HeroEntity owned = null;
        HeroEntity fallback = null;
        for (HeroEntity hero : mc.level.getEntitiesOfClass(HeroEntity.class, mc.player.getBoundingBox().inflate(HERO_PERCEPTION_RADIUS))) {
            if (hero.isRemoved() || !hero.isAlive()) {
                continue;
            }
            UUID owner = hero.getOwnerUUID();
            if (owner != null && owner.equals(mc.player.getUUID())) {
                if (owned == null || hero.distanceToSqr(mc.player) < owned.distanceToSqr(mc.player)) {
                    owned = hero;
                }
            } else if (owner == null && (fallback == null || hero.distanceToSqr(mc.player) < fallback.distanceToSqr(mc.player))) {
                fallback = hero;
            }
        }
        return owned != null ? owned : fallback;
    }

    /**
     * 玩家在当前世界导入、并由服务器确认的建筑模板（{@link ClientTemplateLibrary} 快照）。
     *
     * <p><b>为什么不用本地配置的 {@code nbtStructures}</b>：那份手写映射不会随导入更新，
     * 模型会一直使用过期 ID（如已被删除的旧模板），最终以 command lattice 拒绝失败。
     * 现在只呈现当前世界真实存在的模板，并把名称与精确 ID 一起给出，模型无需玩家提供模板 ID。</p>
     *
     * <p>列表为空时必须说清是「这个存档没有导入」，而不是拿随包模板或旧对话里的 ID 凑数：
     * 模板按存档存储，换存档后旧模板不会跟着过来。</p>
     */
    private static void appendBuildingTemplates(StringBuilder data) {
        data.append("- Your building templates (place with minecraft_command_skill action=place_template):\n");
        if (!ClientTemplateLibrary.supported()) {
            data.append("  * (Unavailable here: no world connection, or a server without the template channel.)\n");
            return;
        }
        if (ClientTemplateLibrary.busy()) {
            // 同步中是短暂状态：列出的仍是上一份快照，模型可以先用，但不应据此声称建筑已放置。
            data.append("  * (The template index is refreshing; entries below may change until the sync finishes.)\n");
        }
        List<TemplateEntry> templates = ClientTemplateLibrary.entries();
        if (templates.isEmpty()) {
            data.append("  * (NONE. This save has no imported building templates. Templates are stored per world, so a building imported in another save is not available here.)\n");
            data.append("  * Do not place anything and do not reuse a template id from another world or from memory. Tell the player to import the file in Herobrine > Building templates.\n");
            return;
        }
        int shown = 0;
        for (TemplateEntry template : templates) {
            if (shown++ >= MAX_TEMPLATES_IN_PROMPT) {
                data.append("  * ...and ").append(templates.size() - MAX_TEMPLATES_IN_PROMPT).append(" more templates (registry_lookup category=template, query='*').\n");
                break;
            }
            data.append("  * name=\"").append(oneLine(template.name())).append("\" id=").append(template.id())
                    .append(" size=").append(template.x()).append('x').append(template.y()).append('x').append(template.z())
                    .append(" blocks=").append(template.blocks()).append('\n');
        }
        data.append("  * Pick the template whose NAME and SIZE best fit what the player asked for; a vague wish for something grand means the largest listed building. ")
                .append("Place that exact id near the player, and never invent one.\n");
    }

    /** 模板名是玩家/文件名数据，压成单行并对引号做转义，避免它伪装成提示词结构。 */
    private static String oneLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ").replace("\"", "'").strip();
    }

    private static void appendOtherPlayerAwareness(StringBuilder data, Minecraft mc) {
        data.append("\n- Other Players On This Server:\n");

        List<PlayerInfo> otherPlayers = new ArrayList<>();
        try {
            for (PlayerInfo info : mc.player.connection.getOnlinePlayers()) {
                if (info == null || info.getProfile() == null || info.getProfile().getName() == null) {
                    continue;
                }
                UUID uuid = info.getProfile().getId();
                if (uuid != null && uuid.equals(mc.player.getUUID())) {
                    continue;
                }
                otherPlayers.add(info);
            }
        } catch (Exception ignored) {
        }

        if (otherPlayers.isEmpty()) {
            data.append("  * (No other online players detected)\n");
            return;
        }

        int serverLimit = Math.min(MAX_SERVER_PLAYERS_IN_PROMPT, otherPlayers.size());
        for (int i = 0; i < serverLimit; i++) {
            PlayerInfo info = otherPlayers.get(i);
            String playerName = info.getProfile().getName();
            net.minecraft.world.entity.player.Player levelPlayer = mc.level == null ? null : mc.level.getPlayerByUUID(info.getProfile().getId());
            if (levelPlayer == null) {
                data.append("  * ").append(playerName).append(" | location=unknown\n");
                continue;
            }

            double distance = mc.player.distanceTo(levelPlayer);
            data.append("  * ").append(playerName)
                    .append(" | dim=").append(levelPlayer.level().dimension().location())
                    .append(" | distance=").append(String.format(java.util.Locale.ROOT, "%.1f", distance))
                    .append(" | health=").append(String.format(java.util.Locale.ROOT, "%.1f", levelPlayer.getHealth()))
                    .append("/").append(String.format(java.util.Locale.ROOT, "%.1f", levelPlayer.getMaxHealth()));

            if (levelPlayer.isCrouching()) {
                data.append(" | crouching");
            }
            if (levelPlayer.isSprinting()) {
                data.append(" | sprinting");
            }
            if (!levelPlayer.getMainHandItem().isEmpty()) {
                data.append(" | mainhand=")
                        .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(levelPlayer.getMainHandItem().getItem()));
            }
            data.append("\n");
        }

        if (otherPlayers.size() > serverLimit) {
            data.append("  * ...and ").append(otherPlayers.size() - serverLimit).append(" more online players\n");
        }

        if (mc.level != null) {
            data.append("- Nearby Players (within ").append((int) NEARBY_PLAYER_DETAIL_RADIUS).append(" blocks):\n");
            List<net.minecraft.world.entity.player.Player> nearbyPlayers = new ArrayList<>();
            for (net.minecraft.world.entity.player.Player player : mc.level.players()) {
                if (player == mc.player) {
                    continue;
                }
                if (player.distanceTo(mc.player) <= NEARBY_PLAYER_DETAIL_RADIUS) {
                    nearbyPlayers.add(player);
                }
            }

            if (nearbyPlayers.isEmpty()) {
                data.append("  * (No nearby players)\n");
                return;
            }

            int nearbyLimit = Math.min(MAX_NEARBY_PLAYERS_IN_PROMPT, nearbyPlayers.size());
            for (int i = 0; i < nearbyLimit; i++) {
                net.minecraft.world.entity.player.Player player = nearbyPlayers.get(i);
                data.append("  * ").append(player.getName().getString())
                        .append(" | distance=").append(String.format(java.util.Locale.ROOT, "%.1f", player.distanceTo(mc.player)))
                        .append(" | health=").append(String.format(java.util.Locale.ROOT, "%.1f", player.getHealth()))
                        .append("/").append(String.format(java.util.Locale.ROOT, "%.1f", player.getMaxHealth()));

                if (player.isCrouching()) {
                    data.append(" | crouching");
                }
                if (player.isSprinting()) {
                    data.append(" | sprinting");
                }
                if (!player.getMainHandItem().isEmpty()) {
                    data.append(" | mainhand=")
                            .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()));
                }
                data.append("\n");
            }

            if (nearbyPlayers.size() > nearbyLimit) {
                data.append("  * ...and ").append(nearbyPlayers.size() - nearbyLimit).append(" more nearby players\n");
            }
        }
    }
}
