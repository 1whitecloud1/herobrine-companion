package com.whitecloud233.modid.herobrine_companion.entity.awakened;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 单维度（单世界）觉醒怪物总量注册表。
 *
 * <p>设计文档 5.2：单世界总觉醒怪物上限 20 ~ 40（宁少勿滥）；
 * 5.3：怪物一旦觉醒并接触过玩家，就应尽量持久化、不轻易重置。
 *
 * <p>计数以 UUID 集合持久化到维度存档：
 * <ul>
 *   <li>觉醒成功时登记（超过上限则拒绝本次觉醒）；</li>
 *   <li>死亡、消失（discard）、跨维度离开时注销；</li>
 *   <li>区块卸载不注销（觉醒怪仍存在于未加载区块中，仍占用世界名额）；</li>
 *   <li>实体从 NBT 加载/容器释放时强制登记（已觉醒的实体不允许被上限丢弃）。</li>
 * </ul>
 */
public final class AwakenedMobWorldData extends SavedData {
    public static final String DATA_NAME = "herobrine_companion_awakened_mobs";
    private static final String KEY_AWAKENED = "Awakened";

    private final Set<UUID> awakened = new HashSet<>();

    public static AwakenedMobWorldData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(AwakenedMobWorldData::load, AwakenedMobWorldData::new, DATA_NAME);
    }

    public static AwakenedMobWorldData load(CompoundTag tag) {
        AwakenedMobWorldData data = new AwakenedMobWorldData();
        ListTag list = tag.getList(KEY_AWAKENED, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.awakened.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : awakened) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(KEY_AWAKENED, list);
        return tag;
    }

    public int size() {
        return awakened.size();
    }

    public boolean contains(UUID id) {
        return awakened.contains(id);
    }

    /** 尝试登记一个新觉醒者。已登记返回 true（不占新名额）；达到世界上限返回 false。 */
    public boolean register(UUID id, int worldCap) {
        if (awakened.contains(id)) {
            return true;
        }
        if (awakened.size() >= worldCap) {
            return false;
        }
        awakened.add(id);
        setDirty();
        return true;
    }

    /** 强制登记（加载、容器释放、家族强制觉醒时使用）：不检查上限，已觉醒实体不允许被上限丢弃。 */
    public void forceRegister(UUID id) {
        if (awakened.add(id)) {
            setDirty();
        }
    }

    /** 注销（死亡、消失、跨维度离开、被收容时）。 */
    public void unregister(UUID id) {
        if (awakened.remove(id)) {
            setDirty();
        }
    }
}