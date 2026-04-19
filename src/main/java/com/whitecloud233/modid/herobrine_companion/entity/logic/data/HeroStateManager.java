package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class HeroStateManager {

    // ================== [1. 全局数据备份与恢复 (HeroWorldData)] ==================

    /**
     * 将当前 Hero 的关键数据（皮肤、信任度、装备）备份到全局存档
     */
    public static void backupToGlobal(HeroEntity hero) {
        if (!(hero.level() instanceof ServerLevel serverLevel)) return;

        // 【修复】：必须先获取并判断 ownerUUID，再执行后续依赖 UUID 的操作
        UUID ownerUUID = hero.getOwnerUUID();
        if (ownerUUID == null) return;

        HeroWorldData data = HeroWorldData.get(serverLevel);

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

        // 同步姿势数据到全局存档
        CompoundTag poseTag = new CompoundTag();
        poseTag.putBoolean("IsPoseEditing", hero.isPoseEditing);
        if (hero.isPoseEditing) {
            net.minecraft.nbt.ListTag poseList = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 3; j++) {
                    poseList.add(net.minecraft.nbt.FloatTag.valueOf(hero.customPoseAngles[i][j]));
                }
            }
            poseTag.put("CustomPoseAngles", poseList);
        }
        data.setPoseData(ownerUUID, poseTag);
    }

    /**
     * 从全局存档恢复 Hero 的关键数据（作为安全托底机制）
     */
    public static void restoreFromGlobal(HeroEntity hero, Player owner) {
        if (!(hero.level() instanceof ServerLevel serverLevel) || owner == null) return;
        HeroWorldData data = HeroWorldData.get(serverLevel);
        UUID ownerUUID = owner.getUUID();

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

        // 3. 恢复原生装备 (仅在实体全裸时恢复，防覆盖)
        boolean isNaked = true;
        for (ItemStack stack : hero.getArmorSlots()) if (!stack.isEmpty()) isNaked = false;
        for (ItemStack stack : hero.getHandSlots()) if (!stack.isEmpty()) isNaked = false;

        if (isNaked) {
            hero.loadEquipmentFromTag(data.getArmorItems(ownerUUID), data.getHandItems(ownerUUID));
        }

        // 4. 恢复 Curios 背部饰品
        CompoundTag savedCurios = data.getCuriosBackItem(ownerUUID);
        if (savedCurios != null && !savedCurios.isEmpty() && hero.isCuriosBackSlotEmpty()) {
            hero.setCuriosBackItemFromTag(savedCurios);
        }

        // 👇 [新增] 5. 恢复姿势数据，并立即同步给客户端
        CompoundTag poseTag = data.getPoseData(ownerUUID);
        if (poseTag != null && poseTag.contains("IsPoseEditing")) {
            hero.isPoseEditing = poseTag.getBoolean("IsPoseEditing");
            if (hero.isPoseEditing && poseTag.contains("CustomPoseAngles", 9)) {
                net.minecraft.nbt.ListTag poseList = poseTag.getList("CustomPoseAngles", 5);
                if (poseList.size() == 30) {
                    int index = 0;
                    for (int i = 0; i < 10; i++) {
                        for (int j = 0; j < 3; j++) {
                            hero.customPoseAngles[i][j] = poseList.getFloat(index++);
                        }
                    }
                } else {
                    hero.isPoseEditing = false;
                }
            } else if (!hero.isPoseEditing) {
                hero.customPoseAngles = new float[10][3];
            }
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

    /**
     * 将 Hero 完整数据挂载到玩家身上，以便在传送或复活后重建实体
     */
    public static void backupToPlayerNBT(HeroEntity hero, ServerPlayer player, String tagKey) {
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

        player.getPersistentData().put(tagKey, heroData);

        // 挂载的同时强制备份一次全局数据，双保险
        backupToGlobal(hero);
    }

    /**
     * 从玩家身上读取被挂起的 Hero 数据并恢复给实体
     * @return 是否成功读取了临时数据
     */
    public static boolean restoreFromPlayerNBT(HeroEntity hero, ServerPlayer player, String tagKey) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(tagKey)) return false;

        CompoundTag heroData = data.getCompound(tagKey);

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

        data.remove(tagKey); // 阅后即焚
        return true;
    }

    // ================== [3. 实体生命周期同步] ==================

    /**
     * 实体去重时，将旧实体的关键数据无缝转移给新保留的实体
     */
    public static void syncEntityToEntity(HeroEntity source, HeroEntity target) {
        // 转移皮肤
        if (source.getSkinVariant() == HeroEntity.SKIN_CUSTOM && target.getSkinVariant() != HeroEntity.SKIN_CUSTOM) {
            target.setSkinVariant(HeroEntity.SKIN_CUSTOM);
            target.setCustomSkinName(source.getCustomSkinName());
        }

        // 转移信任度（取最高值）
        if (source.getTrustLevel() > target.getTrustLevel()) {
            target.setTrustLevel(source.getTrustLevel());
        }

        // 转移装备
        target.loadEquipmentFromTag(source.getArmorItemsTag(), source.getHandItemsTag());
        target.setCuriosBackItemFromTag(source.getCuriosBackItemTag());

        // 👇 [新增] 转移姿势数据
        target.isPoseEditing = source.isPoseEditing;
        for (int i = 0; i < 10; i++) {
            System.arraycopy(source.customPoseAngles[i], 0, target.customPoseAngles[i], 0, 3);
        }

        // 转移完成后立即备份到全局存档
        backupToGlobal(target);
    }
}