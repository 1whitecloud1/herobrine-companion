package com.whitecloud233.herobrine_companion.world.terrain;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 批量改写地形的公共骨架：区块队列 + 每 tick 预算 + 区块重发 + 天空光修补 + 高度图重建。
 *
 * <p>虚空领域（{@code ClearAreaPacket}）、荒芜（{@code DesolateAreaPacket}）、整平
 * （{@code FlattenAreaPacket}）三个任务原本各自抄了一份这段骨架，连同
 * {@code isTerrain} 的方块标签表、天空光 {@code DataLayer} 的位操作、实体清理的 AABB 都重复了
 * 三遍——复制之后必然走样：只有虚空领域记得 {@code recalcBlockCounts()}，另外两份漏了；
 * 只有整平的 {@code isTerrain} 少了水/岩浆/基岩。这里收成一份，三个任务只剩「一列怎么改」。</p>
 *
 * <p><b>为什么按区块串行 + 每 tick 预算</b>：整块地形改写是几十万个方块写操作，一次性做完会让
 * 服务端卡住好几秒。这里每 tick 只处理固定数量的区块，改过的区块先排队，下一次 tick 才把
 * 新区块数据重发给附近玩家（先改后发，避免客户端看到中间态）。</p>
 *
 * <p><b>为什么手动重发区块</b>：直接写 {@code LevelChunkSection} 绕过了 {@code LevelChunk#setBlockState}，
 * 原版的脏区块广播和光照更新都不会触发，因此必须自己 {@code ForgetLevelChunk + LevelChunkWithLight}。</p>
 *
 * <p>子类只需要覆写 {@link #editColumn}（或整个 {@link #editChunk}），并在结束时依赖
 * {@link #finishChunk} 收尾；不要自己再写 tick 循环。</p>
 */
public abstract class TerrainEditTask {

    /** 默认每 tick 处理 16 个区块（5×5 区域 1~2 tick 完成，同时把单 tick 峰值压在可控范围）。 */
    public static final int DEFAULT_CHUNKS_PER_TICK = 16;

    private static final Set<Heightmap.Types> HEIGHTMAPS = Set.of(
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.WORLD_SURFACE);

    protected final ServerLevel level;
    protected final ServerPlayer player;

    private final Queue<ChunkPos> queue;
    private final Queue<ChunkPos> pendingUpdates = new ArrayDeque<>();
    private final int chunksPerTick;
    private final Consumer<LevelTickEvent.Post> tickListener = this::onLevelTick;
    private boolean started;
    /** 全部区块改完后发给玩家的提示，{@code null} 表示静默完成。 */
    private final Component completeMessage;

    protected TerrainEditTask(ServerLevel level, Queue<ChunkPos> queue, ServerPlayer player,
                              int chunksPerTick, @Nullable Component completeMessage) {
        this.level = level;
        this.queue = queue;
        this.player = player;
        this.chunksPerTick = Math.max(1, chunksPerTick);
        this.completeMessage = completeMessage;
    }

    /** 以 {@code center} 为中心、边长 {@code 2*radius+1} 的区块队列（radius=2 → 5×5）。 */
    public static Queue<ChunkPos> chunkQueue(ChunkPos center, int radius) {
        Queue<ChunkPos> chunks = new ArrayDeque<>();
        int safeRadius = Math.max(0, radius);
        for (int x = -safeRadius; x <= safeRadius; x++) {
            for (int z = -safeRadius; z <= safeRadius; z++) {
                chunks.add(new ChunkPos(center.x + x, center.z + z));
            }
        }
        return chunks;
    }

    /** 显式注册回调，避免 NeoForge 拒绝子类继承的 @SubscribeEvent 方法。 */
    public final void start() {
        if (!this.started) {
            NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, this.tickListener);
            this.started = true;
        }
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() != this.level) {
            return;
        }

        processPendingUpdates();

        for (int i = 0; i < this.chunksPerTick; i++) {
            if (this.queue.isEmpty()) {
                if (this.pendingUpdates.isEmpty()) {
                    if (this.completeMessage != null && this.player != null) {
                        this.player.sendSystemMessage(this.completeMessage);
                    }
                    NeoForge.EVENT_BUS.unregister(this.tickListener);
                }
                return;
            }

            ChunkPos chunkPos = this.queue.poll();
            editChunk(this.level, chunkPos);
            this.pendingUpdates.add(chunkPos);
        }
    }

    /** 把上一次 tick 改过的区块重发给附近玩家（先 forget 再整块下发，含光照）。 */
    private void processPendingUpdates() {
        while (!this.pendingUpdates.isEmpty()) {
            ChunkPos chunkPos = this.pendingUpdates.poll();
            LevelChunk chunk = this.level.getChunk(chunkPos.x, chunkPos.z);

            this.level.getChunkSource().chunkMap.getPlayers(chunkPos, false).forEach(player -> {
                player.connection.send(new ClientboundForgetLevelChunkPacket(chunkPos));
                player.connection.send(new ClientboundLevelChunkWithLightPacket(
                        chunk, this.level.getLightEngine(), null, null));
            });

            if (this.pendingUpdates.size() < this.chunksPerTick) {
                break;
            }
        }
    }

    /**
     * 改一个区块。默认实现：清怪 → 逐列调用 {@link #editColumn} → {@link #finishChunk} 收尾。
     *
     * <p>整块处理（不看列）的任务可以整体覆写本方法，但请自己调用
     * {@link #evictEntities} 与 {@link #finishChunk}。</p>
     */
    protected void editChunk(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        LevelChunkSection[] sections = chunk.getSections();

        evictEntities(level, chunkPos);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                editColumn(level, chunk, chunkPos, sections, x, z);
            }
        }

        finishChunk(chunk);
    }

    /**
     * 改一列 —— 三个地形任务唯一的策略差异点。
     *
     * <p>默认抛错：整块处理的任务（如虚空领域）覆写 {@link #editChunk} 即可，不必实现本方法；
     * 逐列任务则必须覆写本方法，否则运行到第一个区块就会带着这条消息炸出来。</p>
     *
     * @param x 区块内 X（0..15）
     * @param z 区块内 Z（0..15）
     */
    protected void editColumn(ServerLevel level, LevelChunk chunk, ChunkPos chunkPos,
                              LevelChunkSection[] sections, int x, int z) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " 既没有覆写 editChunk，也没有覆写 editColumn");
    }

    // ------------------------------------------------------------------
    // 公共收尾 / 工具
    // ------------------------------------------------------------------

    /**
     * 清掉区块内的非玩家、非 Hero 实体（含掉落物、经验球）。
     *
     * <p>地形被整片抹掉后这些实体会悬在空中或掉进虚空，必须在改地形之前清掉，
     * 否则会在下一 tick 触发大量掉落/窒息判定。</p>
     */
    protected static void evictEntities(ServerLevel level, ChunkPos chunkPos) {
        AABB chunkBox = new AABB(
                chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunkPos.getMaxBlockZ() + 1);

        for (Entity entity : level.getEntities(null, chunkBox)) {
            if (entity instanceof Player || entity instanceof HeroEntity) {
                continue;
            }
            entity.discard();
        }
    }

    /**
     * 区块收尾：重算 section 计数 → 重建高度图 → 标记未保存。
     *
     * <p><b>{@code recalcBlockCounts()} 不能省</b>：直接写 {@code LevelChunkSection#setBlockState}
     * 只改调色板，{@code nonEmptyBlockCount} 不会跟着变，而 {@code hasOnlyAir()} 就读它。
     * 计数陈旧会让光照引擎把整段当作"本来就有方块"或"本来就是空的"，表现为改完地形后
     * 区块局部一片黑或者被当成空段跳过。</p>
     */
    public static void finishChunk(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            // 无条件重算：hasOnlyAir() 本身就读 nonEmptyBlockCount，拿它做前置判断等于用陈旧值
            // 决定要不要修正这个陈旧值，被清空的段会永远修不回来。
            section.recalcBlockCounts();
        }
        Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
        chunk.setUnsaved(true);
    }

    /**
     * 判断一个方块是否算「地表/地层」——即整平、抹平时应该被当作地面而不是空气/植物的方块。
     *
     * <p>优先用标签（自动覆盖模组方块），标签覆盖不到的再硬编码。原版五个任务各写一份且互相缺失，
     * 现在只有这一份：{@link BlockTags#DIRT} 已含草方块/灰化土/菌丝体/泥巴等，
     * 所以下面第三段不需要再逐个列。</p>
     */
    public static boolean isTerrain(BlockState state) {
        if (state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)) {
            return true;
        }

        // 平台通用标签（Forge/NeoForge 同名），对模组方块（各类石头/矿石）兼容性最好。
        if (state.is(Tags.Blocks.STONES)
                || state.is(Tags.Blocks.GRAVELS)
                || state.is(Tags.Blocks.SANDS)
                || state.is(Tags.Blocks.NETHERRACKS)
                || state.is(Tags.Blocks.END_STONES)
                || state.is(Tags.Blocks.ORES)) {
            return true;
        }

        // 标签管不到的原版方块（含液体与冰，供"从顶部往下找地表"时把水面也视作地面）。
        return state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.WATER)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.BEDROCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.POWDER_SNOW);
    }

    /** 一列的地表采样结果：世界高度 + 该处的方块状态。 */
    public record ColumnSurface(int y, BlockState state) {
    }

    /**
     * 从区块顶部往下找这一列最上面的「地表」方块。
     *
     * @param skipFluidSources 为 true 时跳过流体源（整平要采"真正的固体地表"）；
     *                         为 false 时把水面也算地表（荒芜要贴着水面切）
     * @return 找不到时返回 {@code null}（虚空列）
     */
    @Nullable
    protected static ColumnSurface findSurface(LevelChunk chunk, int x, int z,
                                               int minBuildHeight, int maxBuildHeight,
                                               boolean skipFluidSources) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int y = maxBuildHeight - 1; y >= minBuildHeight; y--) {
            LevelChunkSection section = sections[chunk.getSectionIndex(y)];
            if (section.hasOnlyAir()) {
                continue;
            }
            BlockState state = section.getBlockState(x, y & 15, z);
            if (state.isAir()) {
                continue;
            }
            if (skipFluidSources && state.getFluidState().isSource()) {
                continue;
            }
            if (isTerrain(state)) {
                return new ColumnSurface(y, state);
            }
        }
        return null;
    }

    /**
     * 清掉某一列中高于 {@code lowestWorldY} 的方块（含该行），并顺手删掉对应的方块实体。
     *
     * @param lowestWorldY 该 section 内的清理下界（世界坐标），传 {@code sectionMinY} 表示整段清空
     */
    protected static void clearColumnAbove(LevelChunk chunk, ChunkPos chunkPos, LevelChunkSection section,
                                           int sectionMinY, int x, int z, int lowestWorldY) {
        for (int y = 15; y >= 0; y--) {
            int worldY = sectionMinY + y;
            if (worldY < lowestWorldY) {
                break;
            }
            if (section.getBlockState(x, y, z).isAir()) {
                continue;
            }
            section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());
            chunk.removeBlockEntity(new BlockPos(
                    chunkPos.getMinBlockX() + x, worldY, chunkPos.getMinBlockZ() + z));
        }
    }

    /**
     * 把某一列中高于 {@code topSolidY} 的天空光置满 15。
     *
     * <p>地形被抬高/削平后原版光照不会重算被直接改写的方块，必须手动改 {@code DataLayer} 的 nibble，
     * 否则玩家会看到"头顶有方块但一片黑"或"头顶空了却还是暗的"。</p>
     */
    public static void lightColumnAbove(ServerLevel level, LevelChunk chunk, int x, int z, int topSolidY) {
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        LevelLightEngine lightEngine = level.getLightEngine();
        for (int i = sections.length - 1; i >= 0; i--) {
            int sectionY = chunk.getSectionYFromSectionIndex(i);
            int sectionMinY = sectionY * 16;
            if (sectionMinY + 15 <= topSolidY) {
                break;
            }
            DataLayer dataLayer = skyDataLayer(lightEngine, SectionPos.of(chunkPos, sectionY));
            if (dataLayer == null) {
                continue;
            }
            byte[] data = dataLayer.getData();
            for (int y = 15; y >= 0; y--) {
                if (sectionMinY + y <= topSolidY) {
                    continue;
                }
                int index = (y << 8) | (z << 4) | x;
                int byteIndex = index >> 1;
                int shift = (index & 1) * 4;
                data[byteIndex] = (byte) ((data[byteIndex] & ~(0xF << shift)) | (0xF << shift));
            }
        }
    }

    /** 把整块区块的天空光全部置满（虚空领域用：整块之后没有阻挡）。 */
    public static void lightWholeChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        LevelLightEngine lightEngine = level.getLightEngine();
        for (int i = 0; i < sections.length; i++) {
            int sectionY = chunk.getSectionYFromSectionIndex(i);
            DataLayer dataLayer = skyDataLayer(lightEngine, SectionPos.of(chunkPos, sectionY));
            if (dataLayer != null) {
                Arrays.fill(dataLayer.getData(), (byte) 0xFF);
            }
        }
    }

    /** 取某 section 的天空光数据层；缺失时触发一次重算再取。 */
    @Nullable
    private static DataLayer skyDataLayer(LevelLightEngine lightEngine, SectionPos sectionPos) {
        var listener = lightEngine.getLayerListener(LightLayer.SKY);
        if (listener == null) {
            return null;
        }
        DataLayer dataLayer = listener.getDataLayerData(sectionPos);
        if (dataLayer == null) {
            lightEngine.checkBlock(sectionPos.origin().offset(8, 8, 8));
            dataLayer = listener.getDataLayerData(sectionPos);
        }
        return dataLayer;
    }
}
