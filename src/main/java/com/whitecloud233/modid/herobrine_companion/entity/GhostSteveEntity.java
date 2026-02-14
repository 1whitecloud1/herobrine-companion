package com.whitecloud233.modid.herobrine_companion.entity;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public class GhostSteveEntity extends Monster {
    public GhostSteveEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

        // [修改] 随机给予一种花
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(getRandomFlower(level.getRandom())));

        return data;
    }

    private Item getRandomFlower(RandomSource random) {
        int r = random.nextInt(10);
        return switch (r) {
            case 0 -> Items.POPPY;
            case 1 -> Items.DANDELION;
            case 2 -> Items.BLUE_ORCHID;
            case 3 -> Items.ALLIUM;
            case 4 -> Items.AZURE_BLUET;
            case 5 -> Items.RED_TULIP;
            case 6 -> Items.ORANGE_TULIP;
            case 7 -> Items.WHITE_TULIP;
            case 8 -> Items.PINK_TULIP;
            default -> Items.OXEYE_DAISY;
        };
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        this.spawnAtLocation(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get());
    }

    public static boolean checkGhostSteveSpawnRules(EntityType<GhostSteveEntity> entityType, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        // Spawns day and night (ignore light level)
        // Chance reduced to 0.5% (from 5%)

        // [关键修复] 强制检查是否在地表
        // 获取当前位置的地表高度
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());

        // 如果生成位置的 Y 坐标远低于地表高度（例如低 5 格以上），则认为是在洞穴中，禁止生成
        if (pos.getY() < surfaceY - 5) {
            return false;
        }

        // [修改] 将生成概率调整为 100% (1.0F)
        return random.nextFloat() < 0.25F &&
                level.getBlockState(pos.below()).isSolid() &&
                level.getBlockState(pos).isAir();
    }

    // [新增] 覆盖 isDarkEnoughToSpawn，强制返回 true
    // Monster 类默认的 checkMonsterSpawnRules 会调用这个方法
    // 如果我们不覆盖它，即使我们在 checkGhostSteveSpawnRules 里不调用 checkMonsterSpawnRules，
    // 某些底层的生成逻辑（如 SpawnerBlock 或其他模组）可能还是会检查这个
    public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        return true;
    }
}
