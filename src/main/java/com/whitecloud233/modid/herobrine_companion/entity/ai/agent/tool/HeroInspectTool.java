package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.compat.accessories.HeroAccessoriesCompat;
import com.whitecloud233.modid.herobrine_companion.compat.curios.HeroCuriosCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.AiItemNaming;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 只读内省工具：向 Agent 报告 Hero 自身状态（心智 / 生命 / 归属 / 位置 / 周围威胁 / 装备）。
 *
 * <p>M2 用它端到端打通"路由 → 注册表 → 校验 → 执行 → 审计"全链路，
 * 同时保持零副作用（不改变任何世界/实体状态）。</p>
 */
public final class HeroInspectTool implements AgentTool {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "mind", "health", "companion", "position", "threats", "flags", "equipment");

    /**
     * 玩家可见的通用回执。inspect 的详细报文（心智/生命/坐标…）只面向 LLM 上下文，
     * 玩家聊天只提示"已读取状态"，避免把技术报文直接抛给玩家。
     */
    private static final Component INSPECT_DISPLAY =
            Component.translatable("message.herobrine_companion.tool.inspect.success");

    @Override
    public String id() {
        return "hero_inspect";
    }

    @Override
    public String description() {
        return "只读查询 Herobrine 当前状态：心智、生命、结伴、位置、周围威胁、模式标记、装备。";
    }

    @Override
    public String category() {
        return "info";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public List<AgentToolParameter> parameters() {
        return List.of(AgentToolParameter.optional(
                "field", "string", "可选：mind/health/companion/position/threats/flags/equipment，缺省返回全部"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        String field = args.getString("field").trim().toLowerCase(java.util.Locale.ROOT);
        if (!field.isEmpty() && !ALLOWED_FIELDS.contains(field)) {
            return AgentToolResult.fail("field 只允许: " + ALLOWED_FIELDS,
                    Component.translatable("message.herobrine_companion.tool.inspect.invalid_field", ALLOWED_FIELDS));
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        var frame = context.frame();
        var hero = context.hero();
        String field = args.getString("field").trim().toLowerCase(java.util.Locale.ROOT);

        return switch (field) {
            case "mind" -> AgentToolResult.ok("心智状态: " + frame.mindState(), INSPECT_DISPLAY);
            case "health" -> AgentToolResult.ok(String.format("生命: %.0f/%.0f", hero.getHealth(), hero.getMaxHealth()), INSPECT_DISPLAY);
            case "companion" -> AgentToolResult.ok(frame.ownerUuid() == null ? "未结伴" : "同行者UUID: " + frame.ownerUuid(), INSPECT_DISPLAY);
            case "position" -> {
                var pos = frame.position();
                yield AgentToolResult.ok("坐标: " + pos.getX() + "," + pos.getY() + "," + pos.getZ(), INSPECT_DISPLAY);
            }
            case "threats" -> AgentToolResult.ok("周围敌对怪物: " + frame.nearbyThreats(), INSPECT_DISPLAY);
            case "equipment" -> AgentToolResult.ok("装备: " + describeEquipment(hero), INSPECT_DISPLAY);
            case "flags" -> AgentToolResult.ok("挑战=" + frame.isChallengeActive()
                    + " 战斗=" + frame.isBattleActive()
                    + " 陪伴=" + frame.isCompanionMode(), INSPECT_DISPLAY);
            default -> AgentToolResult.ok("心智=" + frame.mindState()
                    + " 生命=" + String.format("%.0f/%.0f", hero.getHealth(), hero.getMaxHealth())
                    + " 结伴=" + (frame.ownerUuid() == null ? "无" : "在")
                    + " 坐标=" + frame.position().getX() + "," + frame.position().getY() + "," + frame.position().getZ()
                    + " 威胁=" + frame.nearbyThreats()
                    + " 装备=" + describeEquipment(hero)
                    + " 挑战=" + frame.isChallengeActive()
                    + " 战斗=" + frame.isBattleActive(), INSPECT_DISPLAY);
        };
    }

    private static String describeEquipment(HeroEntity hero) {
        List<String> parts = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            ItemStack stack = hero.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                parts.add(slot.getName() + "=" + AiItemNaming.describe(stack));
            }
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
            return "(无装备)";
        }
        return String.join(", ", parts);
    }
}