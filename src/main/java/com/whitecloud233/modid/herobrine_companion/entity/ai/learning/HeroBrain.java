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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HeroBrain {
    private final HeroEntity hero;
    
    // [重构] 多重人格切片：每个玩家对应一个神经网络
    private final Map<UUID, SimpleNeuralNetwork> networks = new HashMap<>();
    
    // 默认网络 (用于没有特定玩家交互时)
    private final SimpleNeuralNetwork defaultNetwork = new SimpleNeuralNetwork();

    public HeroBrain(HeroEntity hero) {
        this.hero = hero;
    }

    // 获取针对特定玩家的神经网络
    private SimpleNeuralNetwork getNetwork(UUID playerUUID) {
        if (playerUUID == null) return defaultNetwork;
        return networks.computeIfAbsent(playerUUID, k -> {
            SimpleNeuralNetwork net = new SimpleNeuralNetwork();
            // 尝试从 WorldData 加载记忆
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

    // 获取当前焦点目标的网络 (通常是 Owner)
    private SimpleNeuralNetwork getCurrentNetwork() {
        return getNetwork(hero.getOwnerUUID());
    }

    public void tick() {
        if (hero.level().isClientSide) return;

        // 1. 思考 (更新所有活跃的神经网络)
        // 我们只更新周围玩家的网络，或者最近交互过的
        List<ServerPlayer> nearbyPlayers = hero.level().getEntitiesOfClass(ServerPlayer.class, hero.getBoundingBox().inflate(64));
        for (ServerPlayer p : nearbyPlayers) {
            getNetwork(p.getUUID()).tick(hero.level().getGameTime());
        }
        defaultNetwork.tick(hero.level().getGameTime());

        // 2. 执行基于状态的行为 (基于当前的 Owner)
        executeStateBehavior();

        // 3. 调试可视化
        if (hero.getTags().contains("brain_debug")) {
            hero.setCustomName(Component.literal(getCurrentNetwork().getDebugInfo()));
            hero.setCustomNameVisible(true);
        }
        
        // 4. 定期保存记忆到 WorldData
        if (hero.tickCount % 1200 == 0) { // 每分钟保存一次
            saveMemories();
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

        if (hero.tickCount % 20 != 0) return;

        switch (state) {
            case OBSERVER -> {
                if (hero.getRandom().nextFloat() < 0.05f) {
                    hero.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
                }
            }
            case PROTECTOR -> {
                if (hero.tickCount % 100 == 0) {
                    List<ServerPlayer> players = level.players();
                    for (ServerPlayer p : players) {
                        // 检查该玩家对应的网络状态是否也是 PROTECTOR (或者友好)
                        if (getNetwork(p.getUUID()).getCurrentState() == SimpleNeuralNetwork.MindState.PROTECTOR) {
                            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0, false, false));
                            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0, false, false));
                            if (p.distanceToSqr(hero) < 64 * 64) {
                                level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2, p.getZ(), 3, 0.5, 0.5, 0.5, 0.1);
                            }
                        }
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
                    List<Entity> items = level.getEntities(hero, hero.getBoundingBox().inflate(16), e -> e instanceof net.minecraft.world.entity.item.ItemEntity);
                    if (items.size() > 20) {
                        for (Entity item : items) {
                            item.discard();
                            level.sendParticles(ParticleTypes.POOF, item.getX(), item.getY(), item.getZ(), 1, 0, 0, 0, 0);
                        }
                        if (hero.getOwnerUUID() != null) {
                            ServerPlayer owner = (ServerPlayer) level.getPlayerByUUID(hero.getOwnerUUID());
                            if (owner != null) HeroDialogueHandler.speak(hero, owner, "message.herobrine_companion.maintainer_clean");
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
                hero.getLookControl().setLookAt(hero.getX(), hero.getY() + 100, hero.getZ(), 10, 10);
                if (hero.getRandom().nextFloat() < 0.05f) {
                    level.playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.5f);
                }
            }
        }
    }

    // --- 输入接口 (Sensors) ---
    // [修改] 所有输入方法现在需要传入 playerUUID

    public void inputViolence(UUID playerUUID, float amount) {
        getNetwork(playerUUID).input("VIOLENCE", amount);
    }

    public void input(UUID playerUUID, String type, float amount) {
        getNetwork(playerUUID).input(type, amount);
    }
    
    public void inputCreativity(UUID playerUUID, float amount) {
        getNetwork(playerUUID).input("CREATIVITY", amount);
    }

    public void inputExploration(UUID playerUUID, float amount) {
        getNetwork(playerUUID).input("EXPLORATION", amount);
    }

    public void inputFailure(UUID playerUUID, float amount) {
        getNetwork(playerUUID).input("FAILURE", amount);
    }

    public void inputEntropy(UUID playerUUID, float amount) {
        getNetwork(playerUUID).input("ENTROPY", amount);
    }

    public void inputMeta(UUID playerUUID, float amount) {
        getNetwork(playerUUID).input("META", amount);
    }

    public void inputNostalgia(UUID playerUUID, float amount) {
        getNetwork(playerUUID).input("NOSTALGIA", amount);
    }

    public void inputMonsterEmpathy(UUID playerUUID, float amount) {
        getNetwork(playerUUID).input("MONSTER_INTEREST", amount);
    }
    
    public void inputLoreFragment(UUID playerUUID, String fragmentId) {
        getNetwork(playerUUID).inputLoreFragment(fragmentId);
    }

    // --- 状态获取 ---

    public SimpleNeuralNetwork.MindState getState() {
        return getCurrentNetwork().getCurrentState();
    }
    
    public SimpleNeuralNetwork.MindState getCurrentState() {
        return getState();
    }

    public String getDebugInfo() {
        return getCurrentNetwork().getDebugInfo();
    }

    // --- 持久化 ---

    public void save(CompoundTag tag) {
        // 保存所有网络状态
        ListTag networksTag = new ListTag();
        for (Map.Entry<UUID, SimpleNeuralNetwork> entry : networks.entrySet()) {
            CompoundTag netTag = new CompoundTag();
            netTag.putUUID("UUID", entry.getKey());
            entry.getValue().save(netTag);
            networksTag.add(netTag);
        }
        tag.put("Networks", networksTag);
        
        // 保存默认网络
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
