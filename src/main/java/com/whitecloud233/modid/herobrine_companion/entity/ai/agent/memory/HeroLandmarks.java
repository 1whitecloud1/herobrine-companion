package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 命名地点与区域偏好（交接 E3）：给"这里是我家，以后别动它"一个<b>结构化</b>的落点。
 *
 * <p><b>为什么不能只存字符串</b>：一个坐标不能代表整栋房子。地点必须带维度、
 * 位置/范围与来源；区域偏好必须有明确半径。只有结构化才可能被后续行为真正消费。</p>
 *
 * <p><b>单一职责</b>：只做"地点 / 区域偏好的增删查改 + 序列化"，不决定什么时候记、
 * 也不执行任何世界动作。写入侧见 {@code HeroRememberTool}，消费侧见
 * {@code HeroNavigateTool}（按名称前往）与 {@code HeroHomeGuard}
 * （不在此处 —— 由既有保护逻辑读取偏好）。</p>
 *
 * <p>存储：随 {@link HeroMemory} 的语义层一起落进玩家档案，按 owner 隔离。</p>
 */
public final class HeroLandmarks {

    /** 玩家可保存的命名地点数量上限。 */
    public static final int MAX_LANDMARKS = 32;

    /** 区域偏好半径上限（格）：再大就不该叫"我家"了。 */
    public static final int MAX_RADIUS = 128;

    private static final String KEY_NBT = "Landmarks";

    private final List<Landmark> landmarks = new ArrayList<>();

    /** 一条命名地点 / 区域偏好（不可变）。 */
    public record Landmark(
            String name,
            String dimension,
            int x,
            int y,
            int z,
            /** 0 表示单点；>0 表示以该点为中心的圆柱半径。 */
            int radius,
            /** 来源：player=玩家明确要求，event=已定义的可验证事件规则。 */
            String source) {

        public Landmark {
            name = name == null ? "" : name.trim();
            dimension = dimension == null ? "" : dimension.trim();
            radius = Math.max(0, Math.min(MAX_RADIUS, radius));
            source = source == null || source.isBlank() ? "player" : source.trim();
        }

        /** 是否是一条区域偏好（而不是单个坐标）。 */
        public boolean isArea() {
            return radius > 0;
        }

        /** 键：小写名称，用于大小写不敏感查找。 */
        public String key() {
            return name.toLowerCase(Locale.ROOT);
        }

        public String line() {
            return name + " @" + dimension + " " + x + "," + y + "," + z
                    + (isArea() ? " 半径 " + radius : "（单点）")
                    + " 来源=" + source;
        }

        public CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putString("dimension", dimension);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putInt("radius", radius);
            tag.putString("source", source);
            return tag;
        }

        public static Landmark read(CompoundTag tag) {
            return new Landmark(
                    tag.getString("name"),
                    tag.getString("dimension"),
                    tag.getInt("x"),
                    tag.getInt("y"),
                    tag.getInt("z"),
                    tag.getInt("radius"),
                    tag.getString("source"));
        }
    }

    /** 保存或覆盖同名地点（冲突更新：同名的旧记录被替换）。 */
    public boolean save(Landmark landmark) {
        if (landmark == null || landmark.name().isEmpty()) {
            return false;
        }
        remove(landmark.name());
        if (landmarks.size() >= MAX_LANDMARKS) {
            return false;
        }
        landmarks.add(landmark);
        return true;
    }

    /** 按名称查询（大小写不敏感）。 */
    public Optional<Landmark> find(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (Landmark landmark : landmarks) {
            if (landmark.key().equals(key)) {
                return Optional.of(landmark);
            }
        }
        return Optional.empty();
    }

    /** 遗忘一个地点；返回是否真的删掉了。 */
    public boolean remove(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return landmarks.removeIf(landmark -> landmark.key().equals(key));
    }

    /** 全部地点（只读快照）。 */
    public List<Landmark> all() {
        return List.copyOf(landmarks);
    }

    public int size() {
        return landmarks.size();
    }

    public boolean isEmpty() {
        return landmarks.isEmpty();
    }

    /** 该位置是否落在某条区域偏好内（用于"别动我家"这类判断）。 */
    public boolean isInsideArea(String dimension, int x, int y, int z) {
        for (Landmark landmark : landmarks) {
            if (!landmark.isArea() || !landmark.dimension().equals(dimension)) {
                continue;
            }
            double dx = x - landmark.x();
            double dz = z - landmark.z();
            if (dx * dx + dz * dz <= (double) landmark.radius() * landmark.radius()) {
                return true;
            }
        }
        return false;
    }

    /** 序列化到记忆 NBT（挂在语义层旁边，随玩家档案保存）。 */
    public void write(CompoundTag parent) {
        ListTag list = new ListTag();
        for (Landmark landmark : landmarks) {
            list.add(landmark.write());
        }
        parent.put(KEY_NBT, list);
    }

    /** 从记忆 NBT 读取；旧存档没有该键时保持为空。 */
    public void read(CompoundTag parent) {
        landmarks.clear();
        if (parent == null || !parent.contains(KEY_NBT, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = parent.getList(KEY_NBT, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && landmarks.size() < MAX_LANDMARKS; i++) {
            Landmark landmark = Landmark.read(list.getCompound(i));
            if (!landmark.name().isEmpty()) {
                landmarks.add(landmark);
            }
        }
    }

    public void clear() {
        landmarks.clear();
    }
}
