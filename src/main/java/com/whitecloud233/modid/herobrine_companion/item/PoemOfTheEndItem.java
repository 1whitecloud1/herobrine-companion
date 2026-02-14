package com.whitecloud233.modid.herobrine_companion.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.VoidRiftEntity;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import com.whitecloud233.modid.herobrine_companion.network.HeroWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class PoemOfTheEndItem extends DiggerItem {

    // 模式常量
    public static final int MODE_NORMAL = 0;        // 普通
    public static final int MODE_REALM_BREAKER = 1; // 破境
    public static final int MODE_THUNDER_CALL = 2;  // 鸣雷
    public static final int MODE_VOID_SHATTER = 3;  // 碎空

    private static final String TAG_MODE = "PoemMode";
    private final Random random = new Random();
    
    // 使用固定的 UUID，确保属性修饰符的一致性
    private static final UUID BASE_ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID BASE_ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    public PoemOfTheEndItem(Tier tier, float attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, tier, BlockTags.MINEABLE_WITH_PICKAXE, properties.durability(-1));
    }

    // 获取当前模式
    public int getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_MODE)) {
            return tag.getInt(TAG_MODE);
        }
        return MODE_NORMAL;
    }

    // 设置模式
    private void setMode(ItemStack stack, int mode) {
        stack.getOrCreateTag().putInt(TAG_MODE, mode);
    }

    // 切换模式逻辑
    private void cycleMode(ItemStack stack, Player player) {
        int currentMode = getMode(stack);
        int nextMode = (currentMode + 1) % 4; // 4个模式
        setMode(stack, nextMode);

        String modeKey = switch (nextMode) {
            case MODE_NORMAL -> "item.herobrine_companion.poem_of_the_end.mode.0";
            case MODE_REALM_BREAKER -> "item.herobrine_companion.poem_of_the_end.mode.1";
            case MODE_THUNDER_CALL -> "item.herobrine_companion.poem_of_the_end.mode.2";
            case MODE_VOID_SHATTER -> "item.herobrine_companion.poem_of_the_end.mode.3";
            default -> "";
        };

        player.displayClientMessage(Component.translatable(modeKey), true);
        // 修复：使用 .get() 获取 SoundEvent
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
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
            int trust = data.getTrust(player.getUUID());

            // 技能冷却检查
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }

            if (mode == MODE_REALM_BREAKER) {
                // --- 破境 (Realm Breaker) ---
                performRealmBreaker(level, player, trust);
                player.getCooldowns().addCooldown(this, 40); // 2秒冷却
            } else if (mode == MODE_THUNDER_CALL) {
                // --- 鸣雷 (Thunder Call) ---
                performThunderCall(level, player, trust);
                player.getCooldowns().addCooldown(this, 30); // 1.5秒冷却
            } else if (mode == MODE_VOID_SHATTER) {
                // --- 碎空 (Void Shatter) ---
                // 被动技能，右键无效果
                return InteractionResultHolder.pass(stack);
            } else if (mode == MODE_NORMAL) {
                // 普通模式无技能
                return InteractionResultHolder.pass(stack);
            }
        }
        
        // 确保客户端也播放挥手动画
        player.swing(usedHand);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // 技能：破境 (发射实体雷枪)
    private void performRealmBreaker(Level level, Player player, int trust) {
        float damage = 25.0F + (trust / 4.0F);
        
        // 计算锋利附魔加成
        int sharpnessLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, player.getMainHandItem());
        if (sharpnessLevel > 0) {
            damage += 0.5F * sharpnessLevel;
        }

        float explosionRadius = 4.0F + (trust / 40.0F);

        RealmBreakerLightningEntity projectile = new RealmBreakerLightningEntity(level, player, damage, explosionRadius);

        // 设置射击方向和速度
        Vec3 look = player.getLookAngle();
        projectile.shoot(look.x, look.y, look.z, 3.0F, 0.0F); // 速度 3.0F，无偏差

        level.addFreshEntity(projectile);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // 技能：鸣雷
    private void performThunderCall(Level level, Player player, int trust) {
        AABB area = player.getBoundingBox().inflate(32.0);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e != null && !(e instanceof HeroEntity) && (e instanceof Monster || e instanceof Enemy));

        if (targets.isEmpty()) return;

        int maxTargets = 2 + (trust / 20);
        int count = 0;
        float baseDamage = 15.0F + (trust / 5.0F);

        for (LivingEntity target : targets) {
            if (count >= maxTargets) break;

            float damage = baseDamage;
            // 计算附魔加成 (锋利、亡灵杀手、节肢杀手)
            damage += EnchantmentHelper.getDamageBonus(player.getMainHandItem(), target.getMobType());

            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
            if (lightning != null) {
                lightning.moveTo(target.position());
                lightning.setVisualOnly(true); // 设置为仅视觉效果，避免造成额外伤害或副作用
                level.addFreshEntity(lightning);
            }
            // 修改：使用 magic() 伤害源，避免被雷电免疫
            target.hurt(level.damageSources().magic(), damage);
            count++;
        }
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
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!player.level().isClientSide && getMode(stack) == MODE_VOID_SHATTER) {
            entity.invulnerableTime = 0;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && entity instanceof Player player && level.getGameTime() % 20 == 0) {
            if (level instanceof ServerLevel serverLevel) {
                updateAttributes(stack, serverLevel, player);
            }
        }
    }

    private void updateAttributes(ItemStack stack, ServerLevel level, Player player) {
        HeroWorldData data = HeroWorldData.get(level);
        int trust = data.getTrust(player.getUUID());
        
        int mode = getMode(stack);
        float attackSpeed = -2.4F; // 默认攻速 (类似剑 1.6)
        float damage = 8.0F + (trust / 5.0F); // 默认伤害
        
        if (mode == MODE_VOID_SHATTER) {
            attackSpeed = 100.0F; // 极快攻速，实现连击
            damage = 2.0F + (trust / 20.0F); // [平衡] 碎空模式普攻伤害极低，主要靠裂痕
        } else if (mode == MODE_REALM_BREAKER) {
            damage = 5.0F + (trust / 5.0F); // 破境模式普攻伤害适中
            attackSpeed = -3.0F; // 较慢攻速
        } else if (mode == MODE_THUNDER_CALL) {
            damage = 5.0F + (trust / 5.0F);
            attackSpeed = -3.0F;
        }
        
        // 将计算出的属性存储在 NBT 中
        CompoundTag tag = stack.getOrCreateTag();
        tag.putFloat("TrustDamage", damage);
        tag.putFloat("TrustSpeed", attackSpeed);
    }
    
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            
            // 默认值
            float damage = this.getAttackDamage();
            float speed = -2.4F;
            
            // 从 NBT 读取额外伤害和速度
            if (stack.hasTag()) {
                CompoundTag tag = stack.getTag();
                if (tag.contains("TrustDamage")) {
                    damage = tag.getFloat("TrustDamage");
                }
                if (tag.contains("TrustSpeed")) {
                    speed = tag.getFloat("TrustSpeed");
                }
            }
            
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Tool modifier", damage, AttributeModifier.Operation.ADDITION));
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Tool modifier", speed, AttributeModifier.Operation.ADDITION));
            
            return builder.build();
        }
        return super.getAttributeModifiers(slot, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
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
        if (!usageKey.isEmpty()) {
            tooltipComponents.add(Component.translatable(usageKey).withStyle(ChatFormatting.GRAY));
        }

        tooltipComponents.add(Component.translatable("item.herobrine_companion.poem_of_the_end.usage").withStyle(ChatFormatting.DARK_GRAY));

        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
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
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_AXE_ACTIONS.contains(toolAction) ||
                ToolActions.DEFAULT_HOE_ACTIONS.contains(toolAction) ||
                ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(toolAction) ||
                ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(toolAction) ||
                ToolActions.DEFAULT_SWORD_ACTIONS.contains(toolAction) ||
                super.canPerformAction(stack, toolAction);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(blockPos);
        Player player = context.getPlayer();
        ItemStack itemStack = context.getItemInHand();

        // Axe Stripping
        BlockState modifiedState = blockState.getToolModifiedState(context, ToolActions.AXE_STRIP, false);
        if (modifiedState != null) {
            // 修复：移除 .get()
            level.playSound(player, blockPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return applyToolAction(level, blockPos, player, itemStack, modifiedState, context);
        }

        // Shovel Flattening
        modifiedState = blockState.getToolModifiedState(context, ToolActions.SHOVEL_FLATTEN, false);
        if (modifiedState != null) {
            // 修复：移除 .get()
            level.playSound(player, blockPos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            return applyToolAction(level, blockPos, player, itemStack, modifiedState, context);
        }

        // Hoe Tilling
        modifiedState = blockState.getToolModifiedState(context, ToolActions.HOE_TILL, false);
        if (modifiedState != null) {
            // 修复：移除 .get()
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
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        // 允许剑的附魔（锋利、亡灵杀手等）
        if (enchantment.category == net.minecraft.world.item.enchantment.EnchantmentCategory.WEAPON) {
            return true;
        }
        // 允许工具的附魔（效率、耐久等）
        if (enchantment.category == net.minecraft.world.item.enchantment.EnchantmentCategory.DIGGER) {
            return true;
        }
        // 允许耐久
        if (enchantment.category == net.minecraft.world.item.enchantment.EnchantmentCategory.BREAKABLE) {
            return true;
        }
        return super.canApplyAtEnchantingTable(stack, enchantment);
    }
    
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 22; // 设置附魔能力值，越高越容易获得好附魔 (金是22，钻石是10)
    }
}
