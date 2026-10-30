package com.whitecloud233.herobrine_companion.structure.template;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.network.TemplatePlacementReply;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Loads on the template worker, then places within a shared per-tick budget, with asynchronous chunk loading. */
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class TemplatePlacementService {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("herobrine_template", Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<MinecraftServer, Map<UUID, Job>> JOBS = new IdentityHashMap<>();
    private TemplatePlacementService() { }

    public static void start(ServerPlayer player, UUID request, String id, int x, int y, int z) {
        if (player == null) return;
        Map<UUID, Job> jobs = JOBS.computeIfAbsent(player.server, key -> new LinkedHashMap<>());
        int limit = TemplateLimits.MAX_PLACEMENT_OFFSET;
        // 偏移上限从 256 放宽到 4096：大型建筑（如 463 格宽）需要能把原点放在更远处。
        // 越界仍然拒绝，但会给出发送者可见的原因，而不是静默失败。
        if (!player.hasPermissions(2) || Math.abs(x) > limit || Math.abs(y) > limit || Math.abs(z) > limit
                || jobs.containsKey(player.getUUID()) || jobs.size() >= 2) {
            player.sendSystemMessage(Component.translatable("gui.herobrine_companion.templates.offset_out_of_range", limit));
            reply(player, request, false, true);
            return;
        }
        Job job = new Job(player, request, player.blockPosition().offset(x, y, z), id);
        jobs.put(player.getUUID(), job); reply(player, request, true, false);
        var server = player.server;
        var repository = new TemplateRepository(server.getWorldPath(LevelResource.ROOT), player.getUUID());
        CompletableFuture.supplyAsync(() -> {
            try {
                CompoundTag root = repository.read(id);
                int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
                int version = root.contains("DataVersion") ? root.getInt("DataVersion") : 500;
                if (version > current) throw new IOException("Building data version is newer than this server");
                if (version < current) root = DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), root, version);
                TemplateStateValidator.validate(root);
                int[] size = TemplateNbtBuilder.position(root.getList("size", Tag.TAG_INT));
                ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
                BlockState[] states = new BlockState[palette.size()];
                for (int i = 0; i < states.length; i++) states[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i));
                return new Loaded(new TemplatePlacementPump(new TemplateBlockCursor(root, true, job.origin.getX(), job.origin.getZ())), states, size);
            } catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }, ServerTemplateLibrary.worker(player)).whenComplete((loaded, error) -> {
            if (server.isStopped()) return;
            server.execute(() -> {
                if (jobs.get(player.getUUID()) != job) return;
                if (error != null) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    player.sendSystemMessage(Component.translatable("gui.herobrine_companion.templates.error",
                            java.util.Objects.toString(cause.getMessage(), cause.getClass().getSimpleName())));
                    finish(jobs, job, false); return;
                }
                long endX = (long) job.origin.getX() + loaded.size()[0] - 1, endY = (long) job.origin.getY() + loaded.size()[1] - 1, endZ = (long) job.origin.getZ() + loaded.size()[2] - 1;
                if (endX > Integer.MAX_VALUE || endZ > Integer.MAX_VALUE || job.origin.getY() < job.level.getMinBuildHeight() || endY >= job.level.getMaxBuildHeight()
                        || !job.level.getWorldBorder().isWithinBounds(job.origin)
                        || !job.level.getWorldBorder().isWithinBounds(new BlockPos((int) endX, (int) endY, (int) endZ))) {
                    player.sendSystemMessage(Component.translatable("gui.herobrine_companion.templates.placement_bounds"));
                    finish(jobs, job, false); return;
                }
                // 大建筑真正的瓶颈是**要加载的区块数**，不是方块数：每个区块都要申请票据并等 FULL。
                // 超过上限时明确拒绝并说明规模，而不是让服务器长时间卡在加载上。
                long chunks = TemplateLimits.placementChunks(loaded.size()[0], loaded.size()[2]);
                if (chunks > TemplateLimits.MAX_PLACEMENT_CHUNKS) {
                    player.sendSystemMessage(Component.translatable(
                            "gui.herobrine_companion.templates.too_many_chunks",
                            chunks, TemplateLimits.MAX_PLACEMENT_CHUNKS));
                    finish(jobs, job, false); return;
                }
                job.loaded = loaded;
                job.totalBlocks = loaded.pump().totalBlocks();
                // 先推线框预览（固定 28 字节），玩家能立刻看到范围，而不是只收到一句"正在建造"。
                BlockPos min = job.origin;
                BlockPos max = new BlockPos((int) endX, (int) endY, (int) endZ);
                PacketDistributor.sendToPlayer(player, new com.whitecloud233.herobrine_companion.network
                        .TemplatePreviewPacket(min.getX(), min.getY(), min.getZ(),
                        max.getX(), max.getY(), max.getZ(), job.templateLabel()));
                player.sendSystemMessage(Component.translatable("gui.herobrine_companion.templates.placing"));
                player.sendSystemMessage(Component.translatable(
                        "gui.herobrine_companion.templates.scale",
                        loaded.size()[0], loaded.size()[1], loaded.size()[2], chunks, job.totalBlocks));
            });
        });
    }

    private record Loaded(TemplatePlacementPump pump, BlockState[] states, int[] size) { }
    private static final class Job {
        final ServerPlayer player;
        final UUID request;
        final ServerLevel level;
        final BlockPos origin;
        final String templateId;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Loaded loaded;
        /** 本次放置的总方块数（0 表示尚未读取完成）。 */
        long totalBlocks;
        /** 上次向玩家播报进度时的 visited 值。 */
        long lastReported;
        ChunkPos chunk;
        CompletableFuture<ChunkResult<ChunkAccess>> chunkFuture;
        Job(ServerPlayer player, UUID request, BlockPos origin, String templateId) {
            this.player = player; this.request = request; this.origin = origin; this.templateId = templateId;
            level = player.serverLevel();
        }
        /** 预览线框上显示的名字：只取模板 id 的最后一段，避免长路径撑爆 80 字符上限。 */
        String templateLabel() {
            if (templateId == null || templateId.isBlank()) {
                return "";
            }
            int slash = templateId.lastIndexOf('/');
            String tail = slash >= 0 && slash + 1 < templateId.length() ? templateId.substring(slash + 1) : templateId;
            return tail.length() <= 80 ? tail : tail.substring(0, 80);
        }
        void release() {
            if (chunk != null) level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk);
            chunk = null; chunkFuture = null;
        }
        boolean apply(TemplateBlockCursor block) throws IOException {
            pos.set(origin.getX() + block.x(), origin.getY() + block.y(), origin.getZ() + block.z());
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            if (chunk == null || chunk.x != cx || chunk.z != cz) {
                release(); chunk = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
                chunkFuture = level.getChunkSource().getChunkFuture(cx, cz, ChunkStatus.FULL, true);
                return false;
            }
            if (!chunkFuture.isDone()) return false;
            if (!chunkFuture.join().isSuccess()) throw new IOException("Could not load a building chunk");
            LevelChunk ready = level.getChunkSource().getChunkNow(cx, cz);
            if (ready == null) return false;
            BlockState state = loaded.states()[block.state()];
            if (state.is(Blocks.STRUCTURE_VOID)) return true;
            CompoundTag data = state.hasBlockEntity() ? block.data() : null;
            BlockState previous = ready.getBlockState(pos);
            if (previous == state && data == null) return true;
            if (previous.hasBlockEntity()) Clearable.tryClear(level.getBlockEntity(pos));
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            if (data != null && level.getBlockEntity(pos) != null) {
                CompoundTag tile = data.copy(); tile.putInt("x", pos.getX()); tile.putInt("y", pos.getY()); tile.putInt("z", pos.getZ());
                var entity = level.getBlockEntity(pos); entity.loadWithComponents(tile, level.registryAccess()); entity.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
            return true;
        }
    }

    private static void reply(ServerPlayer player, UUID request, boolean ok, boolean complete) {
        if (player.connection.getConnection().isConnected()) PacketDistributor.sendToPlayer(player, new TemplatePlacementReply(request, ok, complete));
    }
    private static void finish(Map<UUID, Job> jobs, Job job, boolean ok) {
        jobs.remove(job.player.getUUID()); job.release(); reply(job.player, job.request, ok, true);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Map<UUID, Job> jobs = JOBS.get(event.getServer());
        if (jobs == null || jobs.isEmpty()) return;
        Job[] active = jobs.values().toArray(Job[]::new);
        for (Job job : active) if (event.getServer().getTickCount() % 20 == 0) reply(job.player, job.request, true, false);
        long deadline = System.nanoTime() + 3_000_000L;
        int first = event.getServer().getTickCount() % active.length;
        for (int i = 0; i < active.length; i++) {
            Job job = active[(first + i) % active.length];
            if (!job.player.connection.getConnection().isConnected() || !job.player.hasPermissions(2) || !job.player.serverLevel().equals(job.level)) { finish(jobs, job, false); continue; }
            if (job.loaded == null || System.nanoTime() >= deadline) continue;
            try {
                boolean done = job.loaded.pump().tick(4096, deadline, job::apply);
                reportProgress(job, event.getServer().getTickCount());
                if (done) finish(jobs, job, true);
            }
            catch (Exception error) {
                job.player.sendSystemMessage(Component.translatable("gui.herobrine_companion.templates.error",
                        java.util.Objects.toString(error.getMessage(), error.getClass().getSimpleName())));
                finish(jobs, job, false);
            }
        }
    }

    /**
     * 放置进度播报：每 5 秒一次，只在"走过了一整段"时才发。
     *
     * <p>大建筑要跑十几秒到几分钟，没有进度反馈的话玩家会以为卡死（这正是"建了但没回音"的成因）。
     * 播报频率固定为 5 秒而不是按方块数算，避免大建筑刷屏、小建筑不吭声。</p>
     */
    private static void reportProgress(Job job, int tickCount) {
        if (job.totalBlocks <= 0 || tickCount % 100 != 0) return;
        long visited = job.loaded.pump().visited();
        if (visited <= job.lastReported) return;
        job.lastReported = visited;
        int percent = (int) Math.min(100L, visited * 100L / job.totalBlocks);
        job.player.sendSystemMessage(Component.translatable(
                "gui.herobrine_companion.templates.progress", percent, visited, job.totalBlocks));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Map<UUID, Job> jobs = JOBS.get(player.server);
        if (jobs != null && jobs.containsKey(player.getUUID())) finish(jobs, jobs.get(player.getUUID()), false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { JOBS.remove(event.getServer()); }
}
