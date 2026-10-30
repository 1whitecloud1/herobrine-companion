package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/**
 * 交付产物给玩家（交接阶段 F 最后一步"拿给我"）。
 *
 * <p><b>真实交付</b>：把 Hero 手上真实存在的产物丢到玩家身边（与原版"给予"一致），
 * 并从 Hero 手持扣除相同数量。没有产物就不交付，不凭空生成。</p>
 *
 * <p><b>单一职责</b>：只做"把已在手上的东西交出去并核对数量"，不负责制作。</p>
 */
public final class DeliverToOwnerTask extends PlannedTask {

    public static final String ID = "deliver_to_owner";

    /** 交付距离平方（3 格²）：太远就交不到，如实失败而不是隔空给物品。 */
    private static final double DELIVER_RANGE_SQR = 9.0D;

    private final int expectedCount;

    public DeliverToOwnerTask(Map<String, String> args) {
        super(ID, args);
        this.expectedCount = Math.max(1, getInt("count", 1));
    }

    @Override
    public TaskState tick(AgentTaskContext context) {
        HeroEntity hero = context.hero();
        UUID ownerUuid = hero.getOwnerUUID();
        if (ownerUuid == null || hero.getServer() == null) {
            return TaskState.FAILED;
        }
        ServerPlayer owner = hero.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) {
            return TaskState.FAILED;
        }
        if (owner.level() != hero.level()) {
            // 跨维度：交付要走到玩家身边，不能隔空。
            return TaskState.FAILED;
        }
        if (hero.distanceToSqr(owner) > DELIVER_RANGE_SQR) {
            return TaskState.FAILED;
        }
        int delivered = 0;
        delivered += deliverSlot(hero, owner, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        delivered += deliverSlot(hero, owner, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if (delivered <= 0) {
            return TaskState.FAILED;
        }
        ReportTask.send(context, "已把 " + delivered + " 份成品交给你", false);
        return TaskState.DONE;
    }

    /** 从一只手交付可食用产物；返回实际交付数量。 */
    private static int deliverSlot(HeroEntity hero, ServerPlayer owner, net.minecraft.world.entity.EquipmentSlot slot) {
        ItemStack held = hero.getItemBySlot(slot);
        if (held.isEmpty() || !held.isEdible()) {
            return 0;
        }
        ItemStack toGive = held.copy();
        hero.setItemSlot(slot, ItemStack.EMPTY);
        Vec3 motion = owner.position().subtract(hero.position());
        Vec3 push = motion.lengthSqr() > 1.0E-4D ? motion.normalize().scale(0.25D) : Vec3.ZERO;
        ItemEntity drop = new ItemEntity(hero.level(),
                hero.getX(), hero.getY() + 1.0D, hero.getZ(), toGive);
        drop.setDeltaMovement(push.x, 0.2D, push.z);
        drop.setDefaultPickUpDelay();
        hero.level().addFreshEntity(drop);
        return toGive.getCount();
    }

    @Override
    public String progressText() {
        return "交付 " + expectedCount + " 份成品";
    }

    @Override
    public int maxAttempts() {
        return 1;
    }

    @Override
    public long timeoutTicks() {
        return 20L * 10L;
    }

    @Override
    public long noProgressTicks() {
        return 0L;
    }

    /** 便捷构造。 */
    public static DeliverToOwnerTask of(int count, String goalId) {
        DeliverToOwnerTask task = new DeliverToOwnerTask(Map.of("count", String.valueOf(count)));
        task.setGoalId(goalId);
        return task;
    }
}
