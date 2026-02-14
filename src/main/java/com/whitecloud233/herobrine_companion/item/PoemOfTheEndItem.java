package com.whitecloud233.herobrine_companion.item;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.VoidRiftEntity;
import com.whitecloud233.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public class PoemOfTheEndItem extends DiggerItem {
    
    // 模式常量
    public static final int MODE_NORMAL = 0;        // 普通
    public static final int MODE_REALM_BREAKER = 1; // 破境
    public static final int MODE_THUNDER_CALL = 2;  // 鸣雷
    public static final int MODE_VOID_SHATTER = 3;  // 碎空
    private static final String TAG_MODE = "PoemMode";
    private final Random random = new Random();

    public PoemOfTheEndItem(Tier tier, float attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(tier, BlockTags.MINEABLE_WITH_PICKAXE, properties
                .attributes(createAttributes(tier, attackDamageModifier, attackSpeedModifier))
                .component(DataComponents.UNBREAKABLE, new Unbreakable(true)));
    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attackDamage + tier.getAttackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    // 获取当前模式
    public int getMode(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains(TAG_MODE)) {
            return customData.copyTag().getInt(TAG_MODE);
        }
        return MODE_NORMAL;
    }

    // 设置模式
    private void setMode(ItemStack stack, int mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_MODE, mode));
    }

    // 切换模式逻辑
    private void cycleMode(ItemStack stack, Player player) {
        int currentMode = getMode(stack);
        int nextMode = (currentMode + 1) % 4; // 0->1->2->3->0
        setMode(stack, nextMode);

        String modeKey = switch (nextMode) {
            case MODE_NORMAL -> "item.herobrine_companion.poem_of_the_end.mode.0";
            case MODE_REALM_BREAKER -> "item.herobrine_companion.poem_of_the_end.mode.1";
            case MODE_THUNDER_CALL -> "item.herobrine_companion.poem_of_the_end.mode.2";
            case MODE_VOID_SHATTER -> "item.herobrine_companion.poem_of_the_end.mode.3";
            default -> "";
        };
        
        player.displayClientMessage(Component.translatable(modeKey), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK, SoundSource.PLAYERS, 1.0F, 1.0F);
        
        // 立即更新属性，以便攻速变化生效
        if (!player.level().isClientSide) {
            updateAttributes(stack, (ServerLevel) player.level(), player);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        // 1. Shift + 右键：切换模式
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                cycleMode(stack, player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        // 2. 普通右键：执行技能
        if (!level.isClientSide) {
            int mode = getMode(stack);
            HeroWorldData data = HeroWorldData.get((ServerLevel) level);
            // [修复] 使用 player.getUUID() 获取信任度
            int trust = data.getTrust(player.getUUID());
            
            // 技能冷却检查
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }

            if (mode == MODE_REALM_BREAKER) {
                // --- 破境 (Realm Breaker) ---
                performRealmBreaker(level, player, trust, stack);
                player.getCooldowns().addCooldown(this, 40); // 2秒冷却
                player.swing(usedHand); // 播放挥手动画
            } else if (mode == MODE_THUNDER_CALL) {
                // --- 鸣雷 (Thunder Call) ---
                performThunderCall(level, player, trust, stack);
                player.getCooldowns().addCooldown(this, 30); // 1.5秒冷却
                player.swing(usedHand); // 播放挥手动画
            } else if (mode == MODE_VOID_SHATTER) {
                // --- 碎空 (Void Shatter) ---
                // 被动技能，右键无效果
                return InteractionResultHolder.pass(stack);
            } else if (mode == MODE_NORMAL) {
                // 普通模式无技能
                return InteractionResultHolder.pass(stack);
            }
        } else {
            // 客户端也需要播放动画，以便看起来流畅
            int mode = getMode(stack);
            if (mode == MODE_REALM_BREAKER || mode == MODE_THUNDER_CALL) {
                player.swing(usedHand);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // 技能：破境 (发射实体雷枪)
    private void performRealmBreaker(Level level, Player player, int trust, ItemStack stack) {
        float baseDamage = 25.0F + (trust / 4.0F);
        
        // 计算附魔加成 (主要计算锋利)
        // 由于是投射物，无法预知击中目标的类型，这里只计算通用的锋利附魔
        float enchantBonus = 0.0F;
        Holder<Enchantment> sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        int sharpnessLevel = EnchantmentHelper.getItemEnchantmentLevel(sharpness, stack);
        if (sharpnessLevel > 0) {
            // 锋利: 0.5 * level + 0.5
            enchantBonus = 0.5F * sharpnessLevel + 0.5F;
        }

        float totalDamage = baseDamage + enchantBonus;
        float explosionRadius = 4.0F + (trust / 40.0F);

        RealmBreakerLightningEntity projectile = new RealmBreakerLightningEntity(level, player, totalDamage, explosionRadius);
        
        // 设置射击方向和速度
        Vec3 look = player.getLookAngle();
        projectile.shoot(look.x, look.y, look.z, 3.0F, 0.0F); // 速度 3.0F，无偏差
        
        level.addFreshEntity(projectile);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // 技能：鸣雷
    private void performThunderCall(Level level, Player player, int trust, ItemStack stack) {
        AABB area = player.getBoundingBox().inflate(32.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e != null && !(e instanceof HeroEntity) && (e instanceof Monster || e instanceof Enemy));
        
        if (targets.isEmpty()) return;

        int maxTargets = 2 + (trust / 20);
        int count = 0;
        float baseDamage = 15.0F + (trust / 5.0F);

        for (LivingEntity target : targets) {
            if (count >= maxTargets) break;
            
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
            if (lightning != null) {
                lightning.moveTo(target.position());
                lightning.setVisualOnly(true);
                level.addFreshEntity(lightning);
            }
            
            // 计算针对该目标的附魔加成 (支持锋利、亡灵杀手、节肢杀手)
            // 使用魔法伤害以绕过护甲，并防止被免疫雷电的生物（如女巫）免疫
            DamageSource damageSource = level.damageSources().source(DamageTypes.MAGIC, player);
            float damage = EnchantmentHelper.modifyDamage((ServerLevel)level, stack, target, damageSource, baseDamage);
            
            target.hurt(damageSource, damage);
            count++;
        }
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!player.level().isClientSide && getMode(stack) == MODE_VOID_SHATTER) {
            entity.invulnerableTime = 0;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    // 技能：碎空 (左键攻击触发)
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (getMode(stack) == MODE_VOID_SHATTER && !attacker.level().isClientSide) {
            if (attacker instanceof Player player) {
                if (!player.getCooldowns().isOnCooldown(this)) {
                    // 在目标位置生成 VoidRiftEntity，并添加随机偏移
                    ServerLevel level = (ServerLevel) player.level();
                    double offsetX = (random.nextDouble() - 0.5) * 1.5; // +/- 0.75
                    double offsetY = (random.nextDouble() - 0.5) * 1.0; // +/- 0.5
                    double offsetZ = (random.nextDouble() - 0.5) * 1.5; // +/- 0.75
                    
                    VoidRiftEntity rift = new VoidRiftEntity(level, 
                            target.getX() + offsetX, 
                            target.getY() + target.getBbHeight() / 2.0 + offsetY, 
                            target.getZ() + offsetZ, 
                            player.getUUID());

                    level.addFreshEntity(rift);
                    player.getCooldowns().addCooldown(this, 10); // 0.5秒冷却 (10 ticks)
                }
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && entity instanceof Player player && isSelected && level.getGameTime() % 20 == 0) {
            if (level instanceof ServerLevel serverLevel) {
                updateAttributes(stack, serverLevel, player);
            }
        }
    }
    
    // [修改] 增加 Player 参数
    private void updateAttributes(ItemStack stack, ServerLevel level, Player player) {
        HeroWorldData data = HeroWorldData.get(level);
        // [修复] 使用 player.getUUID() 获取信任度
        int trust = data.getTrust(player.getUUID());
        
        int mode = getMode(stack);
        float attackSpeed = -2.4F; // 默认攻速 (类似剑 1.6)
        float damage = 8.0F + (trust / 5.0F); // 默认伤害
        
        if (mode == MODE_VOID_SHATTER) {
            attackSpeed = 100.0F; // 极快攻速，实现连击
            damage = 2.0F + (trust / 40.0F); // [平衡] 碎空模式普攻伤害极低，主要靠裂痕
        } else if (mode == MODE_REALM_BREAKER) {
            damage = 5.0F + (trust / 5.0F); // 破境模式普攻伤害适中
            attackSpeed = -3.0F; // 较慢攻速
        } else if (mode == MODE_THUNDER_CALL) {
            damage = 5.0F + (trust / 5.0F);
            attackSpeed = -3.0F;
        }

        ItemAttributeModifiers newModifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
        
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, newModifiers);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.herobrine_companion.poem_of_the_end.desc").withStyle(ChatFormatting.GOLD));
        
        int mode = getMode(stack);
        String modeKey = switch (mode) {
            case MODE_NORMAL -> "item.herobrine_companion.poem_of_the_end.mode.0";
            case MODE_REALM_BREAKER -> "item.herobrine_companion.poem_of_the_end.mode.1";
            case MODE_THUNDER_CALL -> "item.herobrine_companion.poem_of_the_end.mode.2";
            case MODE_VOID_SHATTER -> "item.herobrine_companion.poem_of_the_end.mode.3";
            default -> "";
        };
        tooltipComponents.add(Component.translatable(modeKey).withStyle(ChatFormatting.AQUA));
        
        // 添加具体模式的用法提示
        String usageKey = switch (mode) {
            case MODE_NORMAL -> "item.herobrine_companion.poem_of_the_end.mode.usage.0"; // 普通模式
            case MODE_REALM_BREAKER -> "item.herobrine_companion.poem_of_the_end.mode.usage.1"; // 右键发射雷枪
            case MODE_THUNDER_CALL -> "item.herobrine_companion.poem_of_the_end.mode.usage.2";  // 右键召唤雷电
            case MODE_VOID_SHATTER -> "item.herobrine_companion.poem_of_the_end.mode.usage.3";  // 长按左键极速连击
            default -> "";
        };
        tooltipComponents.add(Component.translatable(usageKey).withStyle(ChatFormatting.GRAY));
        
        tooltipComponents.add(Component.translatable("item.herobrine_companion.poem_of_the_end.usage").withStyle(ChatFormatting.DARK_GRAY));

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.GOLD);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_AXE) || 
               state.is(BlockTags.MINEABLE_WITH_HOE) || 
               state.is(BlockTags.MINEABLE_WITH_PICKAXE) || 
               state.is(BlockTags.MINEABLE_WITH_SHOVEL) ||
               state.is(Blocks.COBWEB) ||
               super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(Blocks.COBWEB)) {
            return 25.0F;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE) || 
            state.is(BlockTags.MINEABLE_WITH_HOE) || 
            state.is(BlockTags.MINEABLE_WITH_PICKAXE) || 
            state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return 30.0F;
        }
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(itemAbility) ||
               ItemAbilities.DEFAULT_HOE_ACTIONS.contains(itemAbility) ||
               ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(itemAbility) ||
               ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(itemAbility) ||
               ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(itemAbility) ||
               super.canPerformAction(stack, itemAbility);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(blockPos);
        Player player = context.getPlayer();
        ItemStack itemStack = context.getItemInHand();

        // Axe Stripping
        BlockState modifiedState = blockState.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false);
        if (modifiedState != null) {
            level.playSound(player, blockPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return applyToolAction(level, blockPos, player, itemStack, modifiedState, context);
        }

        // Shovel Flattening
        modifiedState = blockState.getToolModifiedState(context, ItemAbilities.SHOVEL_FLATTEN, false);
        if (modifiedState != null) {
            level.playSound(player, blockPos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            return applyToolAction(level, blockPos, player, itemStack, modifiedState, context);
        }

        // Hoe Tilling
        modifiedState = blockState.getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
        if (modifiedState != null) {
            level.playSound(player, blockPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return applyToolAction(level, blockPos, player, itemStack, modifiedState, context);
        }

        return super.useOn(context);
    }

    private InteractionResult applyToolAction(Level level, BlockPos pos, Player player, ItemStack stack, BlockState newState, UseOnContext context) {
        if (!level.isClientSide) {
            level.setBlock(pos, newState, 11);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 22; // 类似金工具的高附魔值
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true; // 强制允许附魔，即使是 Unbreakable
    }
}