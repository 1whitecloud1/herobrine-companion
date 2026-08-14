package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 动作工具：让 Hero 立即施放一个夜幕（EFN）EpicFight 技能。
 *
 * <p>按当前主手武器解析夜幕 profile，用技能名（别名或 EFN 动画字段名，如
 * {@code blast_sword} / {@code judgement_cut} / {@code zandatsu} / {@code beast_roar} /
 * {@code SCYTHE_HARVEST}）定位技能系列并同步播放；特效由现有 ticker 自动触发。</p>
 *
 * <p><b>软依赖</b>：本工具<b>不 import 任何 EpicFight 类</b>（AgentToolRegistry 会急切实例化
 * 工具，硬引用会让未装 EpicFight 的存档崩溃）。只经 {@link HeroEpicFightCompat} 反射门面调用；
 * 桥未就绪时直接拒绝。</p>
 *
 * <p><b>权限模型</b>（P3）：施放技能会造成伤害等大副作用，
 * {@code requiresConfirmation=true} —— 需经确认屏玩家点头后才执行。</p>
 */
public final class HeroUseSkillTool implements AgentTool {

    public static final String ID = "hero_use_skill";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "让 Herobrine 立即施放一个夜幕技能（需持有 EFN 夜幕武器）。skill 填技能名，"
                + "可用常用别名（blast_sword/heavy_rain/damocles/judgement_cut/zandatsu/beast_roar/"
                + "blood_lust/blood_harvest/scarlet_end/mortal_blade 等）或具体动画字段名。";
    }

    @Override
    public String category() {
        return "action";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public List<AgentToolParameter> parameters() {
        return List.of(AgentToolParameter.required(
                "skill", "string", "要施放的夜幕技能名（别名或 EFN 动画字段名）"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        if (args.getString("skill").trim().isEmpty()) {
            return AgentToolResult.fail("skill 不能为空",
                    Component.translatable("message.herobrine_companion.tool.use_skill.empty"));
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        if (!HeroEpicFightCompat.isRuntimeBridgeReady()) {
            return AgentToolResult.fail("EpicFight 未加载，无法使用夜幕技能",
                    Component.translatable("message.herobrine_companion.tool.use_skill.epicfight_unavailable"));
        }
        String result = HeroEpicFightCompat.triggerSkill(context.hero(), args.getString("skill").trim());
        if (result == null || result.isBlank()) {
            return AgentToolResult.fail("技能触发失败",
                    Component.translatable("message.herobrine_companion.tool.use_skill.trigger_failed"));
        }
        if (result.startsWith("FAIL:")) {
            // EFN 动态文案，无法预定义翻译键，保留字面量。
            return AgentToolResult.fail(result.substring("FAIL:".length()));
        }
        return AgentToolResult.ok(result.startsWith("OK:") ? result.substring("OK:".length()) : result);
    }
}
