package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.compat.accessories.HeroAccessoriesCompat;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroEquipment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class HeroStateManager {

    // ================== [1. 全局数据备份与恢复 (HeroWorldData)] ==================

    /**
     * 将当前 Hero 的关键数据（皮肤、信任度、装备）备份到全局存档
     */
    public static void backupToGlobal(HeroEntity hero) {
        if (hero.isRemoved() || !hero.isAlive() || !(hero.level() instanceof ServerLevel serverLevel)) return;

        // 【修复】：必须先获取并判断 ownerUUID，再执行后续依赖 UUID 的操作
        UUID ownerUUID = hero.getOwnerUUID();
        if (ownerUUID == null) return;

        HeroWorldData data = HeroWorldData.get(serverLevel);
        HeroEntity activeHero = HeroLifecycleHandler.findActiveHero(serverLevel, ownerUUID);
        if (activeHero != null && activeHero != hero) return;
        restoreEquipmentFromGlobal(hero);

        if (hero.getSkinVariant() == HeroEntity.SKIN_CUSTOM && !hero.getCustomSkinName().isEmpty()) {
            data.setSkinVariant(ownerUUID, HeroEntity.SKIN_CUSTOM);
            data.setCustomSkinName(ownerUUID, hero.getCustomSkinName());
        } else {
            data.setSkinVariant(ownerUUID, hero.getSkinVariant());
            data.setCustomSkinName(ownerUUID, "");
            data.setCustomSkinData(ownerUUID, new byte[0]);
        }

        // 同步信任度与装备
        if (hero.getTrustLevel() > 0) {
            data.setTrust(ownerUUID, hero.getTrustLevel());
        }
        data.setEquipment(ownerUUID, hero.getArmorItemsTag(), hero.getHandItemsTag());
        data.setCuriosBackItem(ownerUUID, hero.getCuriosBackItemTag());
        data.setAccessoriesData(ownerUUID, hero.getAccessoriesDataTag());

        // 同步姿势数据到全局存档
        CompoundTag poseTag = new CompoundTag();
        HeroDataHandler.savePoseData(hero, poseTag);
        data.setPoseData(ownerUUID, poseTag);
    }

    /**
     * 从全局存档恢复 Hero 的关键数据（作为安全托底机制）
     */
    public static void restoreFromGlobal(HeroEntity hero, Player owner) {
        if (hero.isRemoved() || !(hero.level() instanceof ServerLevel serverLevel) || owner == null) return;
        HeroWorldData data = HeroWorldData.get(serverLevel);
        // 契约物品可由其他玩家使用，恢复的必须是 HB 主人的档案。
        UUID ownerUUID = hero.getOwnerUUID() != null ? hero.getOwnerUUID() : owner.getUUID();
        if (hero.getOwnerUUID() == null) hero.setOwnerUUID(ownerUUID);

        // 以及在 restoreFromGlobal 中：
        hero.setSkinVariant(data.getSkinVariant(ownerUUID));
        if (data.getSkinVariant(ownerUUID) == HeroEntity.SKIN_CUSTOM) {
            hero.setCustomSkinName(data.getCustomSkinName(ownerUUID));
        } else {
            hero.setCustomSkinName("");
        }

        // 2. 恢复信任度 (如果实体信任度丢失则恢复)
        int trust = data.getTrust(ownerUUID);
        if (trust > 0 && hero.getTrustLevel() == 0) {
            hero.setTrustLevel(trust);
        }

        // 3. 新实体只恢复一次装备；手里已有物品也不能阻止护甲恢复。
        // 完整 NBT 已恢复的实体（包括玩家主动清空的槽位）不再被全局旧快照覆盖。
        restoreEquipmentFromGlobal(hero);

        // 👇 [新增] 5. 恢复姿势数据，并立即同步给客户端
        CompoundTag poseTag = data.getPoseData(ownerUUID);
        if (poseTag != null && poseTag.contains("IsPoseEditing")) {
            HeroDataHandler.loadPoseData(hero, poseTag);
            // 写回规范化后的备份，避免重新召唤/跨维度时再次恢复损坏的姿势状态。
            CompoundTag normalizedPose = new CompoundTag();
            HeroDataHandler.savePoseData(hero, normalizedPose);
            data.setPoseData(ownerUUID, normalizedPose);
            // 广播发包：确保服务端刚恢复的数据立刻被玩家看到
            com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToTracking(
                    new com.whitecloud233.modid.herobrine_companion.network.SavePosePacket(hero.getId(), hero.isPoseEditing, hero.customPoseAngles), hero
            );
        }

        com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToTracking(
                new com.whitecloud233.modid.herobrine_companion.network.SyncHeroCosmeticsPacket(hero), hero
        );
    }

    // ================== [2. 玩家 NBT 临时挂起与读取 (跨维度/死亡/战斗剔除)] ==================

    /** 首次备份前完成装备恢复，即使主人离线或不在当前维度也不能写空旧备份。 */
    public static void restoreEquipmentFromGlobal(HeroEntity hero) {
        UUID ownerUUID = hero.getOwnerUUID();
        if (hero.equipmentStateRestored || hero.isRemoved() || ownerUUID == null
                || !(hero.level() instanceof ServerLevel level)) return;
        HeroWorldData data = HeroWorldData.get(level);
        HeroEquipment.loadMissingEquipmentFromTag(hero, data.getArmorItems(ownerUUID), data.getHandItems(ownerUUID));
        CompoundTag savedCurios = data.getCuriosBackItem(ownerUUID);
        if (savedCurios != null && !savedCurios.isEmpty() && hero.isCuriosBackSlotEmpty()) {
            hero.setCuriosBackItemFromTag(savedCurios.copy());
        }
        HeroAccessoriesCompat.loadMissingItemsFromTag(hero, data.getAccessoriesData(ownerUUID));
        hero.equipmentStateRestored = true;
    }

    /**
     * 将 Hero 完整数据挂载到玩家身上，以便在传送或复活后重建实体
     */
    public static void backupToPlayerNBT(HeroEntity hero, ServerPlayer player, String tagKey) {
        // 先初始化并更新全局备份，再提取临时快照，避免新实体被挂起时装备尚未恢复。
        backupToGlobal(hero);
        CompoundTag heroData = new CompoundTag();

        // 保存所有原生 NBT (包括大脑、状态等)
        hero.saveWithoutId(heroData);

        // 强制提取并覆盖核心数据，防止 NBT 序列化遗漏
        heroData.putInt("TrustLevel", hero.getTrustLevel());
        heroData.putInt("SkinVariant", hero.getSkinVariant());
        if (hero.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
            heroData.putString("CustomSkinName", hero.getCustomSkinName());
            heroData.putByteArray("CustomSkinData", HeroWorldData.get((ServerLevel) hero.level()).getCustomSkinData(hero.getOwnerUUID()));
        }

        // 提取装备
        heroData.put("ArmorItems", hero.getArmorItemsTag());
        heroData.put("HandItems", hero.getHandItemsTag());
        heroData.put("CuriosBackItem", hero.getCuriosBackItemTag());
        heroData.put("AccessoriesData", hero.getAccessoriesDataTag());

        player.getPersistentData().put(tagKey, heroData);

    }

    /**
     * 从玩家身上读取被挂起的 Hero 数据并恢复给实体
     * @return 是否成功读取了临时数据
     */
    public static boolean restoreFromPlayerNBT(HeroEntity hero, ServerPlayer player, String tagKey) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(tagKey)) return false;

        CompoundTag heroData = data.getCompound(tagKey).copy();

        // 清洗 UUID，防止与旧实体冲突
        if (heroData.contains("UUID")) heroData.remove("UUID");
        if (heroData.contains("UUIDMost")) heroData.remove("UUIDMost");
        if (heroData.contains("UUIDLeast")) heroData.remove("UUIDLeast");

        // 【核心修复】：清洗旧坐标、旋转角和运动数据，防止把随机重生的实体强行拽回原地
        if (heroData.contains("Pos")) heroData.remove("Pos");
        if (heroData.contains("Rotation")) heroData.remove("Rotation");
        if (heroData.contains("Motion")) heroData.remove("Motion");
        if (heroData.contains("FallDistance")) heroData.remove("FallDistance");

        // 加载 NBT
        hero.load(heroData);

        // 强制恢复核心状态
        if (heroData.contains("TrustLevel")) hero.setTrustLevel(heroData.getInt("TrustLevel"));
        if (heroData.contains("SkinVariant")) {
            hero.setSkinVariant(heroData.getInt("SkinVariant"));
            if (hero.getSkinVariant() == HeroEntity.SKIN_CUSTOM && heroData.contains("CustomSkinName")) {
                hero.setCustomSkinName(heroData.getString("CustomSkinName"));
                if (hero.getOwnerUUID() != null && hero.level() instanceof ServerLevel serverLevel && heroData.contains("CustomSkinData", 7)) {
                    HeroWorldData.get(serverLevel).setCustomSkinData(hero.getOwnerUUID(), heroData.getByteArray("CustomSkinData"));
                }
            }
        }

        // 强制恢复装备
        if (heroData.contains("ArmorItems", 9) || heroData.contains("HandItems", 9)) {
            hero.loadEquipmentFromTag(heroData.getList("ArmorItems", 10), heroData.getList("HandItems", 10));
        }
        if (heroData.contains("CuriosBackItem", 10)) {
            hero.setCuriosBackItemFromTag(heroData.getCompound("CuriosBackItem"));
        }

        if (heroData.contains("AccessoriesData", 10)) {
            hero.setAccessoriesDataFromTag(heroData.getCompound("AccessoriesData"));
        }
        data.remove(tagKey); // 完整恢复成功后再消费临时快照
        return true;
    }

    // ================== [3. 实体生命周期同步] ==================

    /**
     * 实体去重时，将旧实体的关键数据无缝转移给新保留的实体
     */
    public static void syncEntityToEntity(HeroEntity source, HeroEntity target) {
        UUID ownerUUID = target.getOwnerUUID();
        if (source == target || source.isRemoved() || target.isRemoved() || ownerUUID == null
                || !ownerUUID.equals(source.getOwnerUUID())
                || !(target.level() instanceof ServerLevel serverLevel)) return;
        HeroEntity activeHero = HeroLifecycleHandler.findActiveHero(serverLevel, ownerUUID);
        // 两个旧克隆之间的清理不能抢走仍存活的第三个正统实体的身份。
        if (activeHero != null && activeHero != source && activeHero != target) return;
        // 转移皮肤
        if (source.getSkinVariant() == HeroEntity.SKIN_CUSTOM && target.getSkinVariant() != HeroEntity.SKIN_CUSTOM) {
            target.setSkinVariant(HeroEntity.SKIN_CUSTOM);
            target.setCustomSkinName(source.getCustomSkinName());
        }

        // 转移信任度（取最高值）
        if (source.getTrustLevel() > target.getTrustLevel()) {
            target.setTrustLevel(source.getTrustLevel());
        }

        // 正统实体的装备（空槽也有效）优先；旧克隆不能覆盖当前真身。
        if (activeHero == source) {
            target.loadEquipmentFromTag(source.getArmorItemsTag(), source.getHandItemsTag());
            target.setCuriosBackItemFromTag(source.getCuriosBackItemTag().copy());
            target.setAccessoriesDataFromTag(source.getAccessoriesDataTag().copy());
        } else if (activeHero != target && !target.equipmentStateRestored) {
            HeroEquipment.copyMissingEquipment(source, target);
        }
        target.equipmentStateRestored = true;

        // 👇 [新增] 转移姿势数据
        target.isPoseEditing = source.isPoseEditing;
        for (int i = 0; i < 10; i++) {
            System.arraycopy(source.customPoseAngles[i], 0, target.customPoseAngles[i], 0, 3);
        }

        // 先登记最终保留的实体，旧实体随后退出时就不能再写回装备备份。
        HeroWorldData.get(serverLevel).setActiveHeroUUID(ownerUUID, target.getUUID());
        backupToGlobal(target);
    }
}
