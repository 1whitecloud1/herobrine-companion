package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import com.whitecloud233.modid.herobrine_companion.client.agent.ClientAgentToolRequestStore;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolArgs;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolConfirmationStore;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 阶段 A 回归检查：显式请求队列、去重、容量、终态、确认语义、客户端 pending 释放。
 *
 * <p><b>跑的是生产类</b>：{@link AgentToolInbox}、{@link AgentToolRequest}、
 * {@link AgentToolConfirmationStore}、{@link ClientAgentToolRequestStore}。
 * 旧版"单槽覆盖 / pending 不释放 / 等待确认算失败"的行为会在这里被拦住。</p>
 */
public final class AgentReliabilityChecks {

    public static void main(String[] args) {
        int checks = 0;

        // ---- A1：连续两条请求都保留，顺序不变，同 ID 重放不再入队 ----
        AgentToolInbox inbox = new AgentToolInbox(3);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        checks += check(inbox.offer(request("hero_inspect", first)) == AgentToolInbox.Offer.ACCEPTED,
                "第一条请求必须被受理");
        checks += check(inbox.offer(request("hero_summon_to_player", second)) == AgentToolInbox.Offer.ACCEPTED,
                "第二条请求必须被受理（旧版单槽会覆盖第一条）");
        checks += check(inbox.size() == 2, "两条请求必须都在队列里");
        checks += check(inbox.offer(request("hero_inspect", first)) == AgentToolInbox.Offer.DUPLICATE,
                "同 requestId 重放必须判重，不产生第二次副作用");
        checks += check(inbox.size() == 2, "重复投递不得改变队列长度");
        AgentToolRequest head = inbox.poll();
        checks += check(head != null && first.equals(head.requestId()), "FIFO：先投递的先执行");
        AgentToolRequest tail = inbox.poll();
        checks += check(tail != null && second.equals(tail.requestId()), "后投递的仍在队列里，没有被丢掉");

        // ---- A1/A2：队满返回 FULL，且绝不覆盖已在等待的请求 ----
        AgentToolInbox small = new AgentToolInbox(2);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        small.offer(request("hero_inspect", a));
        small.offer(request("hero_inspect", b));
        checks += check(small.offer(request("hero_inspect", c)) == AgentToolInbox.Offer.FULL,
                "队满必须返回 FULL（调用方据此回 busy）");
        checks += check(small.size() == 2, "队满不得丢弃已有请求");
        checks += check(small.poll().requestId().equals(a), "队满后仍按原顺序出队，a 未被覆盖");

        // ---- A2：终态记录有界，且可用于重复确认判定 ----
        AgentToolInbox terminalInbox = new AgentToolInbox(2);
        UUID done = UUID.randomUUID();
        terminalInbox.rememberTerminal(done, true, "OK");
        checks += check(terminalInbox.knows(done), "已终结的 requestId 必须被记住");
        checks += check(terminalInbox.terminal(done).ok(), "终态记录保留真实结果");
        checks += check(terminalInbox.offer(request("hero_inspect", done)) == AgentToolInbox.Offer.DUPLICATE,
                "已终结的 requestId 再次投递必须判重（重复确认不重复执行）");
        for (int i = 0; i < AgentToolInbox.TERMINAL_HISTORY + 8; i++) {
            terminalInbox.rememberTerminal(UUID.randomUUID(), true, "OK");
        }
        checks += check(terminalInbox.recentTerminals(Integer.MAX_VALUE).size() <= AgentToolInbox.TERMINAL_HISTORY,
                "终态记录必须有界");

        // ---- A2：服务端请求期与审批期是两件事 ----
        AgentToolRequest stamped = request("hero_inspect", UUID.randomUUID(), 1_000L);
        checks += check(!stamped.isExpired(1_000L + AgentToolRequest.REQUEST_TTL_TICKS),
                "请求期边界内不算过期");
        checks += check(stamped.isExpired(1_000L + AgentToolRequest.REQUEST_TTL_TICKS + 1),
                "超过请求期必须判过期（不再执行副作用）");
        checks += check(AgentToolConfirmationStore.APPROVAL_TTL_TICKS > 30L * 20L,
                "审批期必须大于客户端 30 秒等待上限（否则正常确认会被判过期）");
        checks += check(AgentToolRequest.REQUEST_TTL_TICKS < 30L * 20L,
                "请求期必须短于客户端等待上限（否则客户端放弃后服务端还会执行）");

        // ---- A3：等待确认不是失败；软件失败与世界失败分开 ----
        AgentToolResult waiting = AgentToolResult.confirmationRequired("需要玩家确认", null);
        checks += check(waiting.confirmationRequired(), "等待确认必须可被上层识别");
        checks += check(!waiting.ok() && !waiting.failed(), "等待确认既不是成功也不是失败");
        checks += check(!waiting.softwareFailure() && !waiting.worldFailure(),
                "等待确认不得被记成失败经历");
        AgentToolResult invalid = AgentToolResult.denied("参数错误", null, AgentToolResult.Cause.INVALID_ARGS);
        checks += check(invalid.failed() && invalid.softwareFailure(), "参数错误是软件侧失败");
        checks += check(!invalid.worldFailure(), "参数错误不得写成世界经历");
        AgentToolResult world = AgentToolResult.fail("目标不可达");
        checks += check(world.failed() && world.worldFailure(), "游戏内动作失败是世界事实");
        checks += check(!world.softwareFailure(), "世界失败不应被当成软件错误吞掉");
        AgentToolResult rejected = AgentToolResult.rejected("玩家拒绝");
        checks += check(rejected.failed() && !rejected.worldFailure() && !rejected.softwareFailure(),
                "玩家拒绝既不写世界经历，也不算软件错误");

        // ---- A3：审批仓储的有效期、归属与一次性消费 ----
        AgentToolConfirmationStore.clear();
        UUID approval = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        AgentToolConfirmationStore.enqueue(new AgentToolConfirmationStore.ConfirmationEntry(
                approval, "hero_ascend", AgentToolArgs.empty(), owner, 500L, "需要确认", "minecraft:overworld"));
        checks += check(AgentToolConfirmationStore.isAwaiting(approval, 500L), "有效期内应处于等待状态");
        checks += check(AgentToolConfirmationStore.isAwaiting(approval, 500L + 30L * 20L),
                "第 30 秒时仍必须显示等待，不能记失败");
        checks += check(AgentToolConfirmationStore.isAwaiting(approval, 500L + 40L * 20L),
                "第 40 秒确认时服务端仍在有效期内（客户端 30 秒上限不该决定服务端期限）");
        checks += check(!AgentToolConfirmationStore.isAwaiting(approval,
                        500L + AgentToolConfirmationStore.APPROVAL_TTL_TICKS + 1),
                "超过审批期必须判过期");
        AgentToolConfirmationStore.ConfirmationEntry entry = AgentToolConfirmationStore.take(approval, 500L);
        checks += check(entry != null && owner.equals(entry.requesterUuid()), "审批条目必须绑定原请求者");
        checks += check(entry != null && "minecraft:overworld".equals(entry.dimensionId()),
                "审批条目必须绑定世界会话（维度）");
        checks += check(AgentToolConfirmationStore.take(approval, 500L) == null,
                "同一条审批只能取走一次（重复确认不重复执行）");

        // ---- A3 回归：等待确认期间该 requestId 不能被认为是"已终结" ----
        // 旧缺陷（本轮引入并被这条检查拦住）：等待确认时误记终态 →
        // ApproveToolPacket 按同一 requestId 重新投递时被判 DUPLICATE 而静默丢弃，
        // 表现是"弹了确认屏、点了确认、工具永远不执行"。
        // 服务层代码（AgentToolRequestService.apply）不得在等待分支写终态，
        // 这里同时用真实 inbox 语义锁定契约。
        AgentToolInbox waitingInbox = new AgentToolInbox(4);
        UUID pending = UUID.randomUUID();
        AgentToolRequest pendingRequest = new AgentToolRequest(
                "hero_ascend", AgentToolArgs.empty(), UUID.randomUUID(), false, pending, 1_000L);
        checks += check(waitingInbox.offer(pendingRequest) == AgentToolInbox.Offer.ACCEPTED,
                "首次请求应入队");
        checks += check(waitingInbox.poll() != null, "消费后队列应为空");
        checks += check(!waitingInbox.knows(pending),
                "等待确认中的 requestId 不得被当作已终结（否则确认后无法执行）");
        AgentToolRequest approved = new AgentToolRequest(
                "hero_ascend", AgentToolArgs.empty(), pendingRequest.requesterUuid(), true, pending, 2_000L);
        checks += check(waitingInbox.offer(approved) == AgentToolInbox.Offer.ACCEPTED,
                "审批通过后必须能重新投递执行（不能被判 DUPLICATE）");
        waitingInbox.poll();
        waitingInbox.rememberTerminal(pending, true, "OK");
        checks += check(waitingInbox.offer(new AgentToolRequest(
                        "hero_ascend", AgentToolArgs.empty(), UUID.randomUUID(), true, pending, 3_000L))
                        == AgentToolInbox.Offer.DUPLICATE,
                "已真正终结的 requestId 才判重（重复确认不重复执行）");
        checks += check(!waitingBranchWritesTerminal(),
                "服务层等待确认分支不得写终态（源码契约检查）");

        // ---- A2：客户端 pending 在超时 / 断线时释放，且回调不在锁内 ----
        checks += clientPendingChecks();

        System.out.println("AgentReliabilityChecks: " + checks + " checks passed");
    }

    private static int clientPendingChecks() {
        int checks = 0;
        ClientAgentToolRequestStore.clear();

        UUID noReply = UUID.randomUUID();
        CompletableFuture<ClientAgentToolRequestStore.AgentToolResultRecord> pending = new CompletableFuture<>();
        ClientAgentToolRequestStore.track(noReply, pending);
        checks += check(ClientAgentToolRequestStore.pendingCount() == 1, "登记后 pending 应有 1 条");
        checks += check(ClientAgentToolRequestStore.expire(noReply), "超时必须能从 pending 中释放该请求");
        checks += check(ClientAgentToolRequestStore.pendingCount() == 0, "释放后 pending 必须归零（旧版会残留）");
        checks += check(pending.isDone() && pending.join() == null, "超时的 future 必须以 null 结束");

        // 迟到回包：只归原 ID，不产生第二次执行；表里仍有终态可供查询。
        ClientAgentToolRequestStore.complete(noReply, "hero_inspect", true, "心智=calm");
        checks += check(ClientAgentToolRequestStore.pendingCount() == 0, "迟到回包不得复活 pending");
        checks += check(ClientAgentToolRequestStore.recentResult() != null
                        && "hero_inspect".equals(ClientAgentToolRequestStore.recentResult().toolId()),
                "迟到回包仍归原 requestId 的结果表");

        // 正常完成路径同样要移除 pending（不是只有超时才清）。
        UUID normal = UUID.randomUUID();
        CompletableFuture<ClientAgentToolRequestStore.AgentToolResultRecord> normalFuture = new CompletableFuture<>();
        ClientAgentToolRequestStore.track(normal, normalFuture);
        ClientAgentToolRequestStore.complete(normal, "hero_ascend", true, "已升空");
        checks += check(normalFuture.isDone() && normalFuture.join() != null, "正常回包必须完成 future");
        checks += check(ClientAgentToolRequestStore.pendingCount() == 0, "正常回包后 pending 必须释放");

        // 断线清理：批量完成所有在途请求并清空。
        for (int i = 0; i < 3; i++) {
            ClientAgentToolRequestStore.track(UUID.randomUUID(), new CompletableFuture<>());
        }
        ClientAgentToolRequestStore.clear();
        checks += check(ClientAgentToolRequestStore.pendingCount() == 0, "断线清理后 pending 必须为空");

        // 兜底：即使上游忘记 expire，pending 也不会无界增长。
        for (int i = 0; i < 200; i++) {
            ClientAgentToolRequestStore.track(UUID.randomUUID(), new CompletableFuture<>());
        }
        checks += check(ClientAgentToolRequestStore.pendingCount() <= 64, "pending 必须有上限，不能无限增长");
        ClientAgentToolRequestStore.clear();
        return checks;
    }

    private static AgentToolRequest request(String toolId, UUID requestId) {
        return request(toolId, requestId, 100L);
    }

    /**
     * 源码契约：服务层的"等待确认"分支<b>不应</b>调用 {@code rememberTerminal}。
     *
     * <p>这是本轮实际发生过的缺陷（等待确认误记终态 → 审批通过后被判 DUPLICATE →
     * 工具永远不执行）。行为级断言只能覆盖 inbox 自身语义，所以这里再读一次真实源文件，
     * 确认那段分支里没有终态写入——回归时会立刻失败。</p>
     *
     * @return true 表示等待分支里存在终态写入（即缺陷存在）
     */
    private static boolean waitingBranchWritesTerminal() {
        // 按当前类所在包推导路径，避免两版包名不同时硬编码失效。
        String packagePath = AgentReliabilityChecks.class.getPackageName().replace('.', '/');
        java.nio.file.Path path = java.nio.file.Path.of(
                "src/main/java/" + packagePath + "/tool/AgentToolRequestService.java");
        try {
            String source = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            int waiting = source.indexOf("if (result.confirmationRequired())");
            if (waiting < 0) {
                throw new AssertionError("找不到等待确认分支（源码结构变化，请更新本契约检查）");
            }
            int waitingReturn = source.indexOf("return;", waiting);
            if (waitingReturn < 0) {
                throw new AssertionError("等待确认分支缺少 return");
            }
            return source.substring(waiting, waitingReturn).contains("rememberTerminal");
        } catch (java.io.IOException error) {
            throw new AssertionError("无法读取 AgentToolRequestService 源码: " + path + " -> " + error);
        }
    }

    private static AgentToolRequest request(String toolId, UUID requestId, long receivedTick) {
        return new AgentToolRequest(toolId, AgentToolArgs.empty(), UUID.randomUUID(), false, requestId, receivedTick);
    }

    private static int check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
        return 1;
    }

    private AgentReliabilityChecks() {
    }
}
