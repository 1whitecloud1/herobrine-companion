package com.whitecloud233.herobrine_companion.world.inventory;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HerobrineCompanion.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<HeroContractMenu>> HERO_CONTRACT_MENU = MENUS.register("hero_contract",
            () -> IMenuTypeExtension.create((windowId, inv, data) -> new HeroContractMenu(windowId, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<HeroMerchantMenu>> HERO_TRADE_MENU = MENUS.register("hero_trade",
            () -> IMenuTypeExtension.create((windowId, inv, data) -> new HeroMerchantMenu(windowId, inv)));

}
