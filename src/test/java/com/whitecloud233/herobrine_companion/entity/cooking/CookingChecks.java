package com.whitecloud233.herobrine_companion.entity.cooking;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CookingChecks {
    private static int checks;

    public static void main(String[] args) {
        int[] trusts = {-1, 0, 19, 20, 49, 50, 79, 80, 100};
        int[] ownerWeights = {0, 0, 0, 1, 1, 2, 2, 4, 4};
        for (int i = 0; i < trusts.length; i++) {
            for (boolean owner : new boolean[]{false, true}) {
                for (boolean monster : new boolean[]{false, true}) {
                    var weights = CookingRules.sharingWeights(trusts[i], owner, monster);
                    check(weights.hero() == 3 && weights.monster() == (monster ? 2 : 0)
                            && weights.owner() == (owner ? ownerWeights[i] : 0), "Sharing weights changed");
                    int[] actual = new int[3];
                    for (int roll = 0; roll < weights.total(); roll++) actual[weights.choose(roll).ordinal()]++;
                    check(actual[0] == weights.hero() && actual[1] == weights.monster() && actual[2] == weights.owner(), "Roll distribution changed");
                }
            }
        }
        for (int count : new int[]{Integer.MIN_VALUE, -1, 0, 1, 2, 64, 65, Integer.MAX_VALUE}) {
            check(CookingRules.clampRepeatCount(count) == Math.max(1, Math.min(64, count)), "Repeat clamp changed");
        }
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID stranger = UUID.fromString("22222222-2222-2222-2222-222222222222");
        check(CookingRules.canManage(owner, owner, false, true, true), "Owner rejected");
        check(!CookingRules.canManage(stranger, owner, false, true, true), "Stranger accepted");
        check(CookingRules.canManage(stranger, owner, true, true, true), "Bound-contract delegation lost");
        check(!CookingRules.canManage(stranger, owner, true, true, false), "Cross-dimension request accepted");
        check(!CookingRules.canManage(owner, owner, false, false, true), "Dead entity accepted");
        check(!CookingRules.canManage(null, owner, true, true, true), "Missing sender accepted");
        check(!CookingRules.canManage(owner, null, true, true, true), "Ownerless entity accepted");

        FakeBackend first = new FakeBackend("first");
        FakeBackend second = new FakeBackend("second");
        ArrayList<CookingBackend> input = new ArrayList<>(List.of(first, second));
        CookingBackendRegistry registry = new CookingBackendRegistry(input);
        input.clear();
        check(registry.all().size() == 2, "Registry aliases caller list");
        int[] seen = {0};
        check(registry.firstMatching(backend -> { seen[0]++; return true; }).orElseThrow() == first && seen[0] == 1,
                "First backend must win without evaluating later backend");
        check(registry.firstMatching(backend -> backend == second).orElseThrow() == second, "Fallback backend not selected");
        check(registry.firstMatching(backend -> false).isEmpty(), "Unsupported station must have no backend");
        check(registry.find(null, null).isEmpty(), "Null station must not query optional mod");
        check(registry.byId("second").orElseThrow() == second && registry.byId("removed").isEmpty(), "Snapshot backend lookup changed");
        try { registry.all().clear(); throw new AssertionError("Mutable registry list"); } catch (UnsupportedOperationException expected) { checks++; }
        try { new CookingBackendRegistry(List.of(first, new FakeBackend("first"))); throw new AssertionError("Duplicate ID accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        check(new CookingBackendRegistry(List.of()).firstMatching(backend -> true).isEmpty(), "Absent mods require empty route");

        CompoundTag source = new CompoundTag();
        CompoundTag nested = new CompoundTag();
        nested.putInt("value", 1);
        source.put("BlockEntityData", nested);
        CookingDisplaySnapshot snapshot = new CookingDisplaySnapshot("second", source);
        source.getCompound("BlockEntityData").putInt("value", 2);
        snapshot.data().getCompound("BlockEntityData").putInt("value", 3);
        check(snapshot.data().getCompound("BlockEntityData").getInt("value") == 1, "Display snapshot aliases input/getter");
        CompoundTag persisted = new CompoundTag();
        persisted.putString("Unrelated", "keep");
        snapshot.write(persisted);
        persisted.getCompound(CookingDisplaySnapshot.SNAPSHOT_KEY).getCompound("BlockEntityData").putInt("value", 4);
        check(snapshot.data().getCompound("BlockEntityData").getInt("value") == 1, "Display snapshot write aliases storage");
        CookingDisplaySnapshot loaded = CookingDisplaySnapshot.read(persisted);
        persisted.getCompound(CookingDisplaySnapshot.SNAPSHOT_KEY).getCompound("BlockEntityData").putInt("value", 5);
        check(loaded.backendId().equals("second") && loaded.data().getCompound("BlockEntityData").getInt("value") == 4,
                "Display snapshot load aliases storage");
        CookingDisplaySnapshot.clear(persisted);
        check(!persisted.contains(CookingDisplaySnapshot.BACKEND_KEY) && !persisted.contains(CookingDisplaySnapshot.SNAPSHOT_KEY)
                && persisted.getString("Unrelated").equals("keep"), "Clearing snapshot removed unrelated entity state");
        check(CookingDisplaySnapshot.read(new CompoundTag()).data().isEmpty(), "Legacy snapshot must not invent backend data");
        snapshot.write(persisted);
        new CookingDisplaySnapshot("first", null).write(persisted);
        check(persisted.getString(CookingDisplaySnapshot.BACKEND_KEY).equals("first") && !persisted.contains(CookingDisplaySnapshot.SNAPSHOT_KEY),
                "Empty display snapshot must remove previous backend data");
        System.out.println("CookingChecks: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }

    private record FakeBackend(String id) implements CookingBackend {
        @Override public boolean supports(Level level, BlockPos pos) { throw new AssertionError("Unexpected optional-mod lookup"); }
        @Override public boolean tick(HeroEntity hero, BlockPos pos) { return false; }
        @Override public ItemStack mainHandDisplay(HeroEntity hero) { return null; }
        @Override public ItemStack offHandDisplay(HeroEntity hero) { return null; }
        @Override public void resetSelection(HeroEntity hero, BlockPos pos) { }
        @Override public List<CookOption> options(HeroEntity hero, BlockPos pos) { return List.of(); }
        @Override public boolean select(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) { return false; }
        @Override public int maxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) { return 0; }
        @Override public Component cycleSelection(HeroEntity hero, BlockPos pos) { return null; }
        @Override public void setRepeatCount(HeroEntity hero, BlockPos pos, int count) { }
        @Override public void beginChunkTicket(HeroEntity hero, BlockPos pos) { }
        @Override public void endChunkTicket(HeroEntity hero) { }
        @Override public List<CookOption> autonomousOptions(Level level, BlockPos pos) { return List.of(); }
    }
}
