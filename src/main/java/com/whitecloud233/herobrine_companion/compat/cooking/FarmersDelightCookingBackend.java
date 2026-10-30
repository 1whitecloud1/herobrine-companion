package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.compat.farmersdelight.HeroFarmersDelightCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.herobrine_companion.entity.cooking.CookingBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

final class FarmersDelightCookingBackend implements CookingBackend {
    @Override public String id() { return "farmersdelight"; }
    @Override public boolean supports(Level level, BlockPos pos) { return HeroFarmersDelightCompat.isCookwareStation(level, pos); }
    @Override public boolean tick(HeroEntity hero, BlockPos pos) { return HeroFarmersDelightCompat.tickCookware(hero, pos); }
    @Override public ItemStack mainHandDisplay(HeroEntity hero) { return HeroFarmersDelightCompat.getCookMainHandDisplay(hero); }
    @Override public ItemStack offHandDisplay(HeroEntity hero) { return HeroFarmersDelightCompat.getCookOffhandDisplay(hero); }
    @Override public void resetSelection(HeroEntity hero, BlockPos pos) { HeroFarmersDelightCompat.resetCookSelection(hero, pos); }
    @Override public List<CookOption> options(HeroEntity hero, BlockPos pos) {
        // 目标项目现有兼容层返回 CookOptionView；按访问器桥接到 entity.cooking.CookOption（兼容层更新后同样成立）。
        return HeroFarmersDelightCompat.getCookOptions(hero, pos).stream()
                .map(option -> new CookOption(option.recipeId(), option.result(), option.maxRepeatCount())).toList();
    }
    @Override public boolean select(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) { return HeroFarmersDelightCompat.selectCookOption(hero, pos, recipeId); }
    @Override public int maxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) { return HeroFarmersDelightCompat.getCookOptionMaxRepeat(hero, pos, recipeId); }
    @Override public Component cycleSelection(HeroEntity hero, BlockPos pos) { return HeroFarmersDelightCompat.cycleCookSelection(hero, pos); }
    @Override public void setRepeatCount(HeroEntity hero, BlockPos pos, int count) { HeroFarmersDelightCompat.setCookRepeatCount(hero, pos, count); }
    @Override public void beginChunkTicket(HeroEntity hero, BlockPos pos) { HeroFarmersDelightCompat.beginCookChunkTicket(hero, pos); }
    @Override public void endChunkTicket(HeroEntity hero) { HeroFarmersDelightCompat.endCookChunkTicket(hero); }
    @Override public List<CookOption> autonomousOptions(Level level, BlockPos pos) {
        return HeroFarmersDelightCompat.getAutonomousCookResults(level, pos).stream()
                .map(result -> new CookOption(null, result, 1)).toList();
    }
}
