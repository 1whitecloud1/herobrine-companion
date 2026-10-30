package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import java.util.List;
import java.util.function.Supplier;

/**
 * 默认工具清单的<b>唯一定义处</b>（交接 A6）。
 *
 * <p>此前同一组九个工具被手列两遍——{@link AgentToolRegistry#defaultCatalog()}（客户端据此生成
 * LLM 工具 schema）与 {@code HeroAgent.createDefaultRegistry()}（服务端据此装配注册表）。
 * 两处一旦不同步，就会出现"模型能看到但服务端没注册"或反之的静默失配。
 * 现在两边都只读这一份清单。</p>
 *
 * <p><b>单一职责</b>：只声明"默认有哪些工具、怎么造"，不持有实例、不做校验、
 * 不执行任何世界动作。实例化时机由调用方决定（客户端只要描述符，服务端要能执行）。</p>
 */
public final class AgentToolFactory {

    /** 默认工具工厂：顺序即目录顺序。 */
    private static final List<Supplier<AgentTool>> DEFAULT_TOOLS = List.of(
            HeroInspectTool::new,
            HeroLocateCompanionTool::new,
            HeroLocateOwnerTool::new,
            HeroAscendTool::new,
            HeroDescendTool::new,
            HeroAcceptChallengeTool::new,
            HeroSetModeTool::new,
            HeroSummonTool::new,
            HeroUseSkillTool::new,
            // 阶段 D2：步行导航（与召唤/传送语义分开，不用传送伪装步行完成）。
            HeroNavigateTool::new,
            // 阶段 D3：只读方块 / 容器查询（不新增"扫描附近一切"）。
            BlockQueryTool::new,
            // 阶段 D4：配方查询与制作（复用 CookingService，不复制配方逻辑）。
            HeroCookTool::new,
            // 阶段 E1：真实物品转移（Hero 侧只有双手两个真实槽位）。
            ItemTransferTool::new,
            // 阶段 E2：明确目标交互（门 / 按钮 / 拉杆，白名单）。
            HeroInteractTool::new,
            // 阶段 E3：命名地点与区域偏好（有导航消费者）。
            HeroRememberTool::new,
            // 阶段 E4：建筑预览（只读，复用模板库与放置同源数据）。
            BuildPreviewTool::new,
            // 阶段 E5：有限区域拾取与收割（真实数量结算）。
            HeroGatherTool::new);

    private AgentToolFactory() {
    }

    /** 创建一份新的默认工具实例列表（每次调用都是新实例，避免跨 Hero 共享可变状态）。 */
    public static List<AgentTool> createDefaults() {
        return DEFAULT_TOOLS.stream().<AgentTool>map(Supplier::get).toList();
    }

    /** 默认工具目录（描述符快照），客户端 LLM schema 与服务端注册表共用。 */
    public static List<AgentToolDescriptor> defaultCatalog() {
        return createDefaults().stream().map(AgentTool::descriptor).toList();
    }
}
