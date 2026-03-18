package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.event.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeroBrain {
    public static final Set<HeroEntity> ACTIVE_HEROES = ConcurrentHashMap.newKeySet();

    private final HeroEntity hero;
    private final Map<UUID, SimpleNeuralNetwork> networks = new HashMap<>();
    private final SimpleNeuralNetwork defaultNetwork = new SimpleNeuralNetwork();

    public record BrokenBlockRecord(BlockPos pos, BlockState state, long timestamp) {}

    private final PriorityQueue<BrokenBlockRecord> brokenBlocksMemory = new PriorityQueue<>(Comparator.comparingInt(r -> r.pos().getY()));
    private final Map<BlockPos, Long> recentBreaks = new ConcurrentHashMap<>();

    public HeroBrain(HeroEntity hero) {
        this.hero = hero;
    }

    public void rememberBrokenBlock(BlockPos pos, BlockState state) {
        if (!com.whitecloud233.modid.herobrine_companion.config.Config.heroBlockRestoration) return;

        long now = hero.level().getGameTime();
        Long lastBreak = recentBreaks.get(pos);
        if (lastBreak != null && now - lastBreak < 100) return;

        recentBreaks.put(pos, now);
        if (brokenBlocksMemory.size() >= 8192) return;

        if (hero.level() instanceof ServerLevel serverLevel && isInUnstableZone(serverLevel, pos)) return;

        brokenBlocksMemory.offer(new BrokenBlockRecord(pos, state, now));
        if (hero.getOwnerUUID() != null) getNetwork(hero.getOwnerUUID()).input("ENTROPY", 0.05f);
    }

    private boolean isInUnstableZone(ServerLevel level, BlockPos pos) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ModStructures.UNSTABLE_ZONE_KEY);
        if (structure == null) return false;
        StructureStart start = level.structureManager().getStructureAt(pos, structure);
        return start.isValid();
    }

    private SimpleNeuralNetwork getNetwork(UUID playerUUID) {
        if (playerUUID == null) return defaultNetwork;
        return networks.computeIfAbsent(playerUUID, k -> {
            SimpleNeuralNetwork net = new SimpleNeuralNetwork();
            if (!hero.level().isClientSide) {
                CompoundTag memory = HeroWorldData.get((ServerLevel) hero.level()).getBrainMemory(playerUUID);
                if (!memory.isEmpty()) net.load(memory);
            }
            return net;
        });
    }

    private SimpleNeuralNetwork getCurrentNetwork() {
        return getNetwork(hero.getOwnerUUID());
    }

    public void tick() {
        if (hero.level().isClientSide) return;

        if (hero.isRemoved() || !hero.isAlive()) {
            ACTIVE_HEROES.remove(hero);
            return;
        } else {
            ACTIVE_HEROES.add(hero);
        }

        List<ServerPlayer> nearbyPlayers = hero.level().getEntitiesOfClass(ServerPlayer.class, hero.getBoundingBox().inflate(64));
        for (ServerPlayer p : nearbyPlayers) getNetwork(p.getUUID()).tick(hero.level().getGameTime());
        defaultNetwork.tick(hero.level().getGameTime());

        // 方块修复本能（最高级优先，忽略状态限制，不再写进 Goal 里）
        processBlockRepair();

        if (hero.getTags().contains("brain_debug")) {
            hero.setCustomName(Component.literal(getCurrentNetwork().getDebugInfo()));
            hero.setCustomNameVisible(true);
        }

        if (hero.tickCount % 1200 == 0) {
            saveMemories();
            recentBreaks.clear();
        }
    }

    private void processBlockRepair() {
        if (brokenBlocksMemory.isEmpty()) return;

        ServerLevel level = (ServerLevel) hero.level();
        int repairSpeed = Math.max(5, Math.min(64, brokenBlocksMemory.size() / 10));
        int blocksRepairedThisTick = 0;

        while (blocksRepairedThisTick < repairSpeed && !brokenBlocksMemory.isEmpty()) {
            BrokenBlockRecord record = brokenBlocksMemory.poll();
            if (record == null) break;

            if (level.getBlockState(record.pos()).canBeReplaced()) {
                int flags = Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE;
                level.setBlock(record.pos(), record.state(), flags);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, record.pos().getX() + 0.5, record.pos().getY() + 0.5, record.pos().getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);

                if (hero.getRandom().nextFloat() < 0.005f && hero.getOwnerUUID() != null) {
                    ServerPlayer owner = (ServerPlayer) level.getPlayerByUUID(hero.getOwnerUUID());
                    if (owner != null) HeroDialogueHandler.onFixAnomaly(hero, owner);
                }

                if (hero.getOwnerUUID() != null) getNetwork(hero.getOwnerUUID()).input("ENTROPY", -0.01f);
                blocksRepairedThisTick++;
            }
        }
    }

    private void saveMemories() {
        if (hero.level().isClientSide) return;
        HeroWorldData data = HeroWorldData.get((ServerLevel) hero.level());
        for (Map.Entry<UUID, SimpleNeuralNetwork> entry : networks.entrySet()) {
            CompoundTag tag = new CompoundTag();
            entry.getValue().save(tag);
            data.setBrainMemory(entry.getKey(), tag);
        }
    }

    // 数据输入与状态获取接口
    public void inputViolence(UUID playerUUID, float amount) { getNetwork(playerUUID).input("VIOLENCE", amount); }
    public void input(UUID playerUUID, String type, float amount) { getNetwork(playerUUID).input(type, amount); }
    public void inputCreativity(UUID playerUUID, float amount) { getNetwork(playerUUID).input("CREATIVITY", amount); }
    public void inputExploration(UUID playerUUID, float amount) { getNetwork(playerUUID).input("EXPLORATION", amount); }
    public void inputFailure(UUID playerUUID, float amount) { getNetwork(playerUUID).input("FAILURE", amount); }
    public void inputEntropy(UUID playerUUID, float amount) { getNetwork(playerUUID).input("ENTROPY", amount); }
    public void inputMeta(UUID playerUUID, float amount) { getNetwork(playerUUID).input("META", amount); }
    public void inputNostalgia(UUID playerUUID, float amount) { getNetwork(playerUUID).input("NOSTALGIA", amount); }
    public void inputMonsterEmpathy(UUID playerUUID, float amount) { getNetwork(playerUUID).input("MONSTER_INTEREST", amount); }
    public void inputLoreFragment(UUID playerUUID, String fragmentId) { getNetwork(playerUUID).inputLoreFragment(fragmentId); }

    public SimpleNeuralNetwork.MindState getState() { return getCurrentNetwork().getCurrentState(); }
    public SimpleNeuralNetwork.MindState getCurrentState() { return getState(); }
    public String getDebugInfo() { return getCurrentNetwork().getDebugInfo(); }

    public void save(CompoundTag tag) {
        ListTag networksTag = new ListTag();
        for (Map.Entry<UUID, SimpleNeuralNetwork> entry : networks.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            netTag.putUUID("UUID", entry.getKey());
            entry.getValue().save(netTag);
            networksTag.add(netTag);
        }
        tag.put("Networks", networksTag);

        CompoundTag defaultTag = new CompoundTag();
        defaultNetwork.save(defaultTag);
        tag.put("DefaultNetwork", defaultTag);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("Networks", Tag.TAG_LIST)) {
            ListTag networksTag = tag.getList("Networks", Tag.TAG_COMPOUND);
            for (int i = 0; i < networksTag.size(); i++) {
                CompoundTag netTag = networksTag.getCompound(i);
                UUID uuid = netTag.getUUID("UUID");
                getNetwork(uuid).load(netTag);
            }
        }
        if (tag.contains("DefaultNetwork")) defaultNetwork.load(tag.getCompound("DefaultNetwork"));
    }
}