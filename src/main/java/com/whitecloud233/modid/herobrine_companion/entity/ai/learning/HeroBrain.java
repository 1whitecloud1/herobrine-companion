package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.network.HeroWorldData;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
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
    // 全局的活跃 Herobrine 集合
    public static final Set<HeroEntity> ACTIVE_HEROES = ConcurrentHashMap.newKeySet();

    private final HeroEntity hero;

    private final Map<UUID, SimpleNeuralNetwork> networks = new HashMap<>();
    private final SimpleNeuralNetwork defaultNetwork = new SimpleNeuralNetwork();

    public record BrokenBlockRecord(BlockPos pos, BlockState state, long timestamp) {}

    // 使用优先队列，按 Y 轴高度从小到大排序。确保他总是从坑底往上修！
    private final PriorityQueue<BrokenBlockRecord> brokenBlocksMemory = new PriorityQueue<>(Comparator.comparingInt(r -> r.pos().getY()));

    // 防死循环追踪器，记录每个坐标最近一次损坏的时间
    private final Map<BlockPos, Long> recentBreaks = new ConcurrentHashMap<>();

    public HeroBrain(HeroEntity hero) {
        this.hero = hero;
    }

    // 在 HeroBrain.java 中找到这个方法
    public void rememberBrokenBlock(BlockPos pos, BlockState state) {
        // 【新增】如果玩家在配置中关闭了方块修复，直接返回，不再记录任何方块破坏
        if (!com.whitecloud233.modid.herobrine_companion.config.Config.heroBlockRestoration) {
            return;
        }

        long now = hero.level().getGameTime();

        // 防无限掉落/卡顿核心
        Long lastBreak = recentBreaks.get(pos);
        if (lastBreak != null && now - lastBreak < 100) {
            return;
        }
        recentBreaks.put(pos, now);

        // 将记忆容量大幅提升到 8192，足以容纳核弹级爆炸
        if (brokenBlocksMemory.size() >= 8192) {
            return;
        }

        // 如果在 Unstable Zone 内，不记录方块破坏
        if (hero.level() instanceof ServerLevel serverLevel && isInUnstableZone(serverLevel, pos)) {
            return;
        }

        brokenBlocksMemory.offer(new BrokenBlockRecord(pos, state, now));

        if (hero.getOwnerUUID() != null) {
            getNetwork(hero.getOwnerUUID()).input("ENTROPY", 0.05f);
        }
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

        // 雷达注册：如果他死了或者被移除了，从全局雷达中注销
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

    // 【新增】神之鉴定：判断地上的物品是否为贵重物品
    private boolean isValuableItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 保护层 1：玩家自定义命名的物品（倾注了心血，神不会轻易抹除）
        if (stack.hasCustomHoverName()) {
            return true;
        }

        // 保护层 2：带有任何附魔的物品
        if (stack.isEnchanted()) {
            return true;
        }

        // 保护层 3：高稀有度物品 (Rare 稀有, Epic 史诗)
        Rarity rarity = stack.getRarity();
        if (rarity == Rarity.EPIC || rarity == Rarity.RARE) {
            return true;
        }

        // 保护层 4：硬编码的绝对核心贵重物品白名单
        Item item = stack.getItem();
        if (item == Items.DIAMOND ||
                item == Items.DIAMOND_BLOCK ||
                item == Items.NETHERITE_INGOT ||
                item == Items.NETHERITE_BLOCK ||
                item == Items.NETHERITE_SCRAP ||
                item == Items.NETHER_STAR ||
                item == Items.TOTEM_OF_UNDYING ||
                item == Items.ENCHANTED_GOLDEN_APPLE ||
                item == Items.BEACON) {
            return true;
        }

        return false; // 不符合上述条件的，视为普通垃圾
    }

    private void executeStateBehavior() {
        SimpleNeuralNetwork.MindState state = getCurrentNetwork().getCurrentState();
        ServerLevel level = (ServerLevel) hero.level();

        // ==========================================
        // 【高频执行区】 神力重组
        // ==========================================
        // 只要有方块碎了，神之本能就会驱使他复原，哪怕他现在正处于其他状态。
        if (!brokenBlocksMemory.isEmpty()) {
            // 动态修复速度：积压的方块越多，修得越快。最快每 tick 修复 64 个方块
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

        // 如果正在交易，禁止执行任何状态行为 (除了上面的方块修复)
        if (hero.getTradingPlayer() != null) return;

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
                // 【新增】如果玩家在配置中关闭了掉落物清理，直接跳出该状态的执行
                if (!com.whitecloud233.modid.herobrine_companion.config.Config.heroCleanItems) {
                    break;
                }

                if (hero.tickCount % 100 == 0) {
                    // 只获取 ItemEntity 类型的实体
                    List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, hero.getBoundingBox().inflate(32));

                    if (items.size() > 5) {
                        int clearedCount = 0;
                        for (ItemEntity itemEntity : items) {
                            // 1. 时间保护：存在时间少于 1 分钟 (1200 tick) 的物品暂不清理
                            if (itemEntity.getAge() <1200) {
                                continue;
                            }

                            // 2. 价值保护：如果是钻石、下界合金或附魔物品等，不清理
                            if (isValuableItem(itemEntity.getItem())) {
                                continue;
                            }

                            // 确认为垃圾后清除
                            level.sendParticles(ParticleTypes.PORTAL, itemEntity.getX(), itemEntity.getY() + 0.2, itemEntity.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                            itemEntity.discard();
                            clearedCount++;
                        }

                        // 只有清理了真正的垃圾，才触发对话和熵减
                        if (clearedCount > 0 && hero.getOwnerUUID() != null) {
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
                if (hero.getRandom().nextFloat() < 0.001f) {
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