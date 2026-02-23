package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.network.HeroWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeroBrain {
    // 在 HeroBrain 类的开头，添加一个全局的活跃 Herobrine 集合
    public static final Set<HeroEntity> ACTIVE_HEROES = ConcurrentHashMap.newKeySet();

    private final HeroEntity hero;
    
    private final Map<UUID, SimpleNeuralNetwork> networks = new HashMap<>();
    private final SimpleNeuralNetwork defaultNetwork = new SimpleNeuralNetwork();

    public record BrokenBlockRecord(BlockPos pos, BlockState state, long timestamp) {}
    
    // 【修改1】使用优先队列，按 Y 轴高度从小到大排序。确保他总是从坑底往上修！
    private final PriorityQueue<BrokenBlockRecord> brokenBlocksMemory = new PriorityQueue<>(Comparator.comparingInt(r -> r.pos().getY()));
    
    // 【修改2】防死循环追踪器，记录每个坐标最近一次损坏的时间
    private final Map<BlockPos, Long> recentBreaks = new ConcurrentHashMap<>();

    public HeroBrain(HeroEntity hero) {
        this.hero = hero;
    }

    public void rememberBrokenBlock(BlockPos pos, BlockState state) {
        long now = hero.level().getGameTime();
        
        // 【防无限掉落/卡顿核心】
        // 如果这个坐标在过去 100 tick (5秒) 内刚刚碎过，说明发生了物理崩塌死循环。
        // 神不屑于做无用功，直接抛弃这个方块的修复。
        Long lastBreak = recentBreaks.get(pos);
        if (lastBreak != null && now - lastBreak < 100) {
            return;
        }
        recentBreaks.put(pos, now);

        // 将记忆容量大幅提升到 8192，足以容纳核弹级爆炸
        if (brokenBlocksMemory.size() >= 8192) {
            return; 
        }
        
        brokenBlocksMemory.offer(new BrokenBlockRecord(pos, state, now));
        
        if (hero.getOwnerUUID() != null) {
            getNetwork(hero.getOwnerUUID()).input("ENTROPY", 0.05f);
        }
    }

    private SimpleNeuralNetwork getNetwork(UUID playerUUID) {
        if (playerUUID == null) return defaultNetwork;
        return networks.computeIfAbsent(playerUUID, k -> {
            SimpleNeuralNetwork net = new SimpleNeuralNetwork();
            if (!hero.level().isClientSide) {
                HeroWorldData data = HeroWorldData.get((ServerLevel) hero.level());
                CompoundTag memory = data.getBrainMemory(playerUUID);
                if (!memory.isEmpty()) {
                    net.load(memory);
                }
            }
            return net;
        });
    }

    private SimpleNeuralNetwork getCurrentNetwork() {
        return getNetwork(hero.getOwnerUUID());
    }

    public void tick() {
        if (hero.level().isClientSide) return;

        // 【新增】雷达注册：如果他死了或者被移除了，从全局雷达中注销
        if (hero.isRemoved() || !hero.isAlive()) {
            ACTIVE_HEROES.remove(hero);
            return;
        } else {
            ACTIVE_HEROES.add(hero); // 活着就保持在线
        }

        List<ServerPlayer> nearbyPlayers = hero.level().getEntitiesOfClass(ServerPlayer.class, hero.getBoundingBox().inflate(64));
        for (ServerPlayer p : nearbyPlayers) {
            getNetwork(p.getUUID()).tick(hero.level().getGameTime());
        }
        defaultNetwork.tick(hero.level().getGameTime());

        executeStateBehavior();

        if (hero.getTags().contains("brain_debug")) {
            hero.setCustomName(Component.literal(getCurrentNetwork().getDebugInfo()));
            hero.setCustomNameVisible(true);
        }
        
        if (hero.tickCount % 1200 == 0) { 
            saveMemories();
            recentBreaks.clear(); // 定期清理防卡顿缓存，防止内存泄漏
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

    private void executeStateBehavior() {
        SimpleNeuralNetwork.MindState state = getCurrentNetwork().getCurrentState();
        ServerLevel level = (ServerLevel) hero.level();
        
        // ==========================================
        // 【高频执行区】 神力重组
        // ==========================================
        // 【核心修复】删除了 state == MAINTAINER 的限制！
        // 只要有方块碎了，神之本能就会驱使他复原，哪怕他现在正处于其他状态。
        // 这保证了队列一定会被清空，地表绝对会被完美修复。
        if (!brokenBlocksMemory.isEmpty()) {
            
            // 【修改3】动态修复速度：积压的方块越多，修得越快。最快每 tick 修复 64 个方块！
            int repairSpeed = Math.max(5, Math.min(64, brokenBlocksMemory.size() / 10)); 
            int blocksRepairedThisTick = 0;

            while (blocksRepairedThisTick < repairSpeed && !brokenBlocksMemory.isEmpty()) {
                BrokenBlockRecord record = brokenBlocksMemory.poll();
                if (record == null) break;

                if (level.getBlockState(record.pos()).canBeReplaced()) {
                    int flags = Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE;
                    level.setBlock(record.pos(), record.state(), flags);
                    
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, 
                        record.pos().getX() + 0.5, record.pos().getY() + 0.5, record.pos().getZ() + 0.5, 
                        10, 0.3, 0.3, 0.3, 0.05);

                    if (hero.getRandom().nextFloat() < 0.005f && hero.getOwnerUUID() != null) {
                        ServerPlayer owner = (ServerPlayer) level.getPlayerByUUID(hero.getOwnerUUID());
                        if (owner != null) HeroDialogueHandler.onFixAnomaly(hero, owner);
                    }

                    if (hero.getOwnerUUID() != null) {
                        getNetwork(hero.getOwnerUUID()).input("ENTROPY", -0.01f);
                    }
                    
                    blocksRepairedThisTick++;
                }
            }
        }

        // ==========================================
        // 【低频执行区】
        // ==========================================
        if (hero.tickCount % 20 != 0) return;

        switch (state) {
            case OBSERVER -> {
                if (hero.getRandom().nextFloat() < 0.05f) {
                    hero.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
                }
            }
            case PROTECTOR -> {
                if (hero.tickCount % 100 == 0 && hero.getOwnerUUID() != null) {
                    ServerPlayer p = (ServerPlayer) level.getPlayerByUUID(hero.getOwnerUUID());
                    if (p != null && p.distanceToSqr(hero) < 64 * 64) {
                        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0, false, false));
                        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0, false, false));
                        level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2, p.getZ(), 3, 0.5, 0.5, 0.5, 0.1);
                    }
                }
            }
            case JUDGE -> {
                if (hero.getRandom().nextFloat() < 0.01f) {
                    int offsetX = hero.getRandom().nextInt(30) - 15;
                    int offsetZ = hero.getRandom().nextInt(30) - 15;
                    if (Math.abs(offsetX) < 5) offsetX = (offsetX < 0 ? -5 : 5);
                    if (Math.abs(offsetZ) < 5) offsetZ = (offsetZ < 0 ? -5 : 5);

                    BlockPos pos = hero.blockPosition().offset(offsetX, 0, offsetZ);
                    if (level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 3, false) == null) {
                        Entity lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                        if (lightning != null) {
                            lightning.moveTo(pos.getX(), pos.getY(), pos.getZ());
                            level.addFreshEntity(lightning);
                        }
                    }
                }
            }
            case PRANKSTER -> {
                if (hero.getRandom().nextFloat() < 0.2f) {
                    level.sendParticles(ParticleTypes.WITCH, hero.getX(), hero.getY() + 1, hero.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                }
            }
            case MAINTAINER -> {
                if (hero.tickCount % 100 == 0) {
                    List<Entity> items = level.getEntities(hero, hero.getBoundingBox().inflate(32), e -> e instanceof net.minecraft.world.entity.item.ItemEntity);
                    if (items.size() > 5) {
                        for (Entity item : items) {
                            level.sendParticles(ParticleTypes.PORTAL, item.getX(), item.getY() + 0.2, item.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                            item.discard(); 
                        }
                        if (hero.getOwnerUUID() != null) {
                            ServerPlayer owner = (ServerPlayer) level.getPlayerByUUID(hero.getOwnerUUID());
                            if (owner != null) {
                                HeroDialogueHandler.onCleanseArea(hero, owner);
                                getNetwork(owner.getUUID()).input("ENTROPY", -0.5f); 
                            }
                        }
                    }
                }
            }
            case GLITCH_LORD -> {
                if (hero.getRandom().nextFloat() < 0.3f) {
                    double x = hero.getX() + (hero.getRandom().nextDouble() - 0.5) * 10;
                    double y = hero.getY() + (hero.getRandom().nextDouble() - 0.5) * 5;
                    double z = hero.getZ() + (hero.getRandom().nextDouble() - 0.5) * 10;
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 5, 0.2, 0.2, 0.2, 0.5);
                }
            }
            case MONSTER_KING -> {
                if (hero.tickCount % 100 == 0) {
                    List<Monster> monsters = level.getEntitiesOfClass(Monster.class, hero.getBoundingBox().inflate(16));
                    for (Monster m : monsters) {
                        m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
                        m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
                        m.setGlowingTag(true);
                    }
                }
            }
            case REMINISCING -> {
                if (hero.getRandom().nextFloat() < 0.01f) {
                    level.playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
                }
            }
        }
    }

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
        if (tag.contains("DefaultNetwork")) {
            defaultNetwork.load(tag.getCompound("DefaultNetwork"));
        }
    }
}
