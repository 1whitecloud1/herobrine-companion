package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
    
    // [修改] 存储每个玩家的神经网络
    private final Map<UUID, SimpleNeuralNetwork> networks = new HashMap<>();
    
    // 默认网络 (用于无主状态或对非玩家实体的反应)
    private final SimpleNeuralNetwork defaultNetwork = new SimpleNeuralNetwork();

    public HeroBrain(HeroEntity hero) {
        this.hero = hero;
    }
    
    // 获取特定玩家的神经网络
    public SimpleNeuralNetwork getNetwork(UUID playerUUID) {
        if (playerUUID == null) return defaultNetwork;
        return networks.computeIfAbsent(playerUUID, k -> new SimpleNeuralNetwork());
    }
    
    // 获取当前主要关注对象的网络 (通常是 Owner)
    public SimpleNeuralNetwork getCurrentNetwork() {
        return getNetwork(hero.getOwnerUUID());
    }

    public void tick() {
        if (hero.level().isClientSide) return;
        
        // 1. 思考 (更新所有活跃玩家的神经网络)
        // 只有当玩家在线且距离较近时才更新，节省性能
        ServerLevel level = (ServerLevel) hero.level();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(hero) < 64 * 64) {
                getNetwork(player.getUUID()).tick(level.getGameTime());
            }
        }
        defaultNetwork.tick(level.getGameTime());
        
        // 2. 执行基于状态的行为 (基于 Owner 的状态)
        executeStateBehavior();

        // 3. 调试可视化
        if (hero.getTags().contains("brain_debug")) {
            // [修复] 如果当前名字显示的是 Idle 动作，不要覆盖它
            Component currentName = hero.getCustomName();
            if (currentName == null || !currentName.getString().startsWith("Idle:")) {
                hero.setCustomName(Component.literal(getCurrentNetwork().getDebugInfo()));
                hero.setCustomNameVisible(true);
            }
        }
        
        // [新增] 定期保存数据到 HeroWorldData (每 5 分钟)
        if (hero.tickCount % 6000 == 0) {
            saveToWorldData();
        }
    }

    private void executeStateBehavior() {
        // 行为主要基于 Owner 的状态，因为 Herobrine 只能表现出一种物理行为
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
                    // 只给 Owner Buff
                    if (hero.getOwnerUUID() != null) {
                        ServerPlayer p = (ServerPlayer) level.getPlayerByUUID(hero.getOwnerUUID());
                        if (p != null && p.distanceToSqr(hero) < 64 * 64) {
                            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0, false, false));
                            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 0, false, false));
                            level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2, p.getZ(), 3, 0.5, 0.5, 0.5, 0.1);
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
    
    public String getDebugInfo() {
        return getCurrentNetwork().getDebugInfo();
    }

    // --- 持久化 ---
    // [修改] 从 HeroWorldData 加载/保存，而不是实体 NBT

    public void save(CompoundTag tag) {
        // 这里的 tag 是实体 NBT，我们只保存 defaultNetwork
        // 玩家特定的网络保存到 HeroWorldData
        defaultNetwork.save(tag);
        saveToWorldData();
    }

    public void load(CompoundTag tag) {
        defaultNetwork.load(tag);
        loadFromWorldData();
    }
    
    private void saveToWorldData() {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            for (Map.Entry<UUID, SimpleNeuralNetwork> entry : networks.entrySet()) {
                CompoundTag brainTag = new CompoundTag();
                entry.getValue().save(brainTag);
                data.setBrainMemory(entry.getKey(), brainTag);
            }
        }
    }
    
    private void loadFromWorldData() {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            // 我们无法遍历所有玩家，只能在需要时加载 (Lazy Load)
            // 或者在初始化时加载所有在线玩家的数据
            for (ServerPlayer player : serverLevel.players()) {
                CompoundTag brainTag = data.getBrainMemory(player.getUUID());
                if (brainTag != null && !brainTag.isEmpty()) {
                    getNetwork(player.getUUID()).load(brainTag);
                }
            }
        }
    }
}
