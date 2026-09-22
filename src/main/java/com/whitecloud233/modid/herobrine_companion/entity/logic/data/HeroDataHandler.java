package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.SyncRewardsPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public class HeroDataHandler {
    // Older builds could persist an enabled editor with its untouched zero buffer.
    // Versioned saves distinguish a newly chosen neutral pose from that legacy state.
    private static final int POSE_DATA_VERSION = 1;

    public static void syncGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);

            // 【核心修复1】：绝对不要遍历周围玩家！Hero 只能同步它自己真正主人的数据！
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID != null) {
                int globalTrust = data.getTrust(ownerUUID);
                // 同步信任度
                if (hero.getTrustLevel() != globalTrust) {
                    hero.setTrustLevel(globalTrust);
                }

                // 同步奖励状态
                Set<Integer> rewards = data.getClaimedRewards(ownerUUID);
                for (int r : rewards) {
                    if (!hero.hasClaimedReward(r)) {
                        hero.claimReward(r);
                    }
                }

                // 👇【核心修复3】：将服务器的奖励数据强制同步给客户端，防止界面按钮错误点亮！
                Player player = serverLevel.getPlayerByUUID(ownerUUID);
                if (player instanceof ServerPlayer serverPlayer) {
                    PacketHandler.sendToPlayer(
                            new SyncRewardsPacket(hero.getId(), rewards),
                            serverPlayer
                    );
                }
            }
        }
    }

    public static void restoreTrustFromPlayer(HeroEntity hero) {
        if (hero.getTrustLevel() > 0) return;

        // 【核心修复2】：即使找不到 Owner，也坚决不能用 getNearestPlayer 去认别人当主人！
        // 否则会导致主权被劫持，进而触发唯一性检测被误杀。
        Player p = null;
        if (hero.getOwnerUUID() != null) {
            p = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        }

        if (p != null) {
            if (hero.level() instanceof ServerLevel serverLevel) {
                HeroWorldData data = HeroWorldData.get(serverLevel);
                int trust = data.getTrust(p.getUUID());
                if (trust > 0) {
                    hero.setTrustLevel(trust);
                }
            }
        }
    }

    public static void updateGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID != null) {
                int current = hero.getTrustLevel();
                if (current != data.getTrust(ownerUUID)) {
                    data.setTrust(ownerUUID, current);
                }
            }
        }
    }

    public static void savePoseData(HeroEntity hero, CompoundTag compound) {
        compound.putInt("PoseDataVersion", POSE_DATA_VERSION);
        compound.putBoolean("IsPoseEditing", hero.isPoseEditing);
        if (hero.isPoseEditing) {
            ListTag poseList = new ListTag();
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 3; j++) {
                    poseList.add(FloatTag.valueOf(hero.customPoseAngles[i][j]));
                }
            }
            compound.put("CustomPoseAngles", poseList);
        } else {
            compound.remove("CustomPoseAngles");
        }
    }

    public static void loadPoseData(HeroEntity hero, CompoundTag compound) {
        float[][] angles = readPoseAngles(compound);
        // 实体 NBT 和全局重生备份走同一迁移入口，避免重新召唤后恢复旧的木头人姿势。
        hero.isPoseEditing = angles != null;
        hero.customPoseAngles = angles != null ? angles : new float[10][3];
    }

    @Nullable
    static float[][] readPoseAngles(CompoundTag compound) {
        if (!compound.getBoolean("IsPoseEditing")) {
            return null;
        }
        ListTag poseList = compound.getList("CustomPoseAngles", Tag.TAG_FLOAT);
        if (poseList.size() != 30) {
            return null;
        }
        float[][] angles = new float[10][3];
        boolean hasRotation = false;
        for (int i = 0; i < 30; i++) {
            float angle = poseList.getFloat(i);
            if (!Float.isFinite(angle)) {
                return null;
            }
            angles[i / 3][i % 3] = angle;
            hasRotation |= angle != 0.0F;
        }
        // A complete zero list also occurs in affected saves, so validating only
        // the length/type leaves every animation overridden by a rigid stance.
        // Keep nonzero legacy poses, and allow intentional neutral poses saved
        // with the new format. This migration does not touch other entity data.
        if (!hasRotation && compound.getInt("PoseDataVersion") < POSE_DATA_VERSION) {
            return null;
        }
        return angles;
    }
}
