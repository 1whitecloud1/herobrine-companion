package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.HeroKaleidoscopeCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingBackend;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.MillstoneOption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

final class KaleidoscopeCookingBackend implements CookingBackend {
    private static final String LEGACY_SNAPSHOT = "HeroAutoCookKaleidoscopeSnapshot";
    private static final String BLOCK_ID = "CookingSnapshotBlock";

    @Override public String id() { return "kaleidoscope_cookery"; }
    @Override public boolean supports(Level level, BlockPos pos) { return HeroKaleidoscopeCompat.isCookwareStation(level, pos); }
    @Override public boolean tick(HeroEntity hero, BlockPos pos) { return HeroKaleidoscopeCompat.tickCookware(hero, pos); }
    @Override public ItemStack mainHandDisplay(HeroEntity hero) { return HeroKaleidoscopeCompat.getCookMainHandDisplay(hero); }
    @Override public ItemStack offHandDisplay(HeroEntity hero) { return HeroKaleidoscopeCompat.getCookOffhandDisplay(hero); }
    @Override public void resetSelection(HeroEntity hero, BlockPos pos) { HeroKaleidoscopeCompat.resetCookSelection(hero, pos); }
    @Override public List<CookOption> options(HeroEntity hero, BlockPos pos) { return HeroKaleidoscopeCompat.getCookOptions(hero, pos); }
    @Override public boolean select(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) { return HeroKaleidoscopeCompat.selectCookOption(hero, pos, recipeId); }
    @Override public int maxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) { return HeroKaleidoscopeCompat.getCookOptionMaxRepeat(hero, pos, recipeId); }
    @Override public Component cycleSelection(HeroEntity hero, BlockPos pos) { return HeroKaleidoscopeCompat.cycleCookSelection(hero, pos); }
    @Override public void setRepeatCount(HeroEntity hero, BlockPos pos, int count) { HeroKaleidoscopeCompat.setCookRepeatCount(hero, pos, count); }
    @Override public void beginChunkTicket(HeroEntity hero, BlockPos pos) { HeroKaleidoscopeCompat.beginCookChunkTicket(hero, pos); }
    @Override public void endChunkTicket(HeroEntity hero) { HeroKaleidoscopeCompat.endCookChunkTicket(hero); }
    @Override public List<CookOption> autonomousOptions(Level level, BlockPos pos) {
        return HeroKaleidoscopeCompat.getAutonomousCookOptions(level, pos).stream()
                .map(option -> new CookOption(option.recipeId(), option.result(), 1)).toList();
    }

    // 石磨不走做菜路由：产物由石磨自己研磨，Hero 只负责推磨。

    @Override public boolean supportsMillstone(Level level, BlockPos pos) {
        return HeroKaleidoscopeCompat.isMillstoneStation(level, pos);
    }

    @Override public BlockPos millstoneCenter(Level level, BlockPos pos) {
        return HeroKaleidoscopeCompat.getMillstoneCenter(level, pos);
    }

    @Override public boolean tickMillstone(HeroEntity hero, BlockPos pos) {
        return HeroKaleidoscopeCompat.tickMillstone(hero, pos);
    }

    @Override public boolean isMillstoneBusy(HeroEntity hero, BlockPos pos) {
        return HeroKaleidoscopeCompat.isMillstoneBusy(hero, pos);
    }

    @Override public boolean canHostMillstone(HeroEntity hero, BlockPos pos) {
        return HeroKaleidoscopeCompat.canHostMillstone(hero, pos);
    }

    @Override public void releaseMillstone(HeroEntity hero, BlockPos pos) {
        HeroKaleidoscopeCompat.releaseMillstone(hero, pos);
    }

    @Override public List<MillstoneOption> millstoneOptions(HeroEntity hero, BlockPos pos) {
        return HeroKaleidoscopeCompat.getMillstoneOptions(hero, pos);
    }

    @Override public int placeMillstoneInput(HeroEntity hero, BlockPos pos, ResourceLocation recipeId, int count) {
        return HeroKaleidoscopeCompat.placeMillstoneInput(hero, pos, recipeId, count);
    }

    @Override public boolean isMillstoneWorking(HeroEntity hero, BlockPos pos) {
        return HeroKaleidoscopeCompat.isMillstoneWorking(hero, pos);
    }


    @Override
    public CompoundTag beginAutonomousDisplay(Level level, BlockPos pos, CookOption choice) {
        CompoundTag snapshot = HeroKaleidoscopeCompat.captureAutonomousCookwareSnapshot(level, pos);
        if (HeroKaleidoscopeCompat.applyAutonomousCookwareDisplay(level, pos, choice.recipeId(), choice.result())) {
            snapshot.putString(BLOCK_ID, net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()).toString());
            return snapshot;
        }
        return new CompoundTag();
    }

    @Override
    public void restoreAutonomousDisplay(Level level, BlockPos pos, CompoundTag snapshot) {
        if (snapshot == null || snapshot.isEmpty() || !supports(level, pos)) return;
        if (snapshot.contains(BLOCK_ID)) {
            String current = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()).toString();
            if (!snapshot.getString(BLOCK_ID).equals(current)) return;
        }
        HeroKaleidoscopeCompat.restoreAutonomousCookwareSnapshot(level, pos, snapshot);
    }

    @Override
    public boolean restoreLegacyDisplay(Level level, BlockPos pos, CompoundTag savedState) {
        if (!savedState.contains(LEGACY_SNAPSHOT)) return false;
        restoreAutonomousDisplay(level, pos, savedState.getCompound(LEGACY_SNAPSHOT).copy());
        savedState.remove(LEGACY_SNAPSHOT);
        return true;
    }
}
