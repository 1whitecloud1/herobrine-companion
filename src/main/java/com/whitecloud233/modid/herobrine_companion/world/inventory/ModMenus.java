package com.whitecloud233.modid.herobrine_companion.world.inventory;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, HerobrineCompanion.MODID);

    public static final RegistryObject<MenuType<HeroContractMenu>> HERO_CONTRACT_MENU = MENUS.register("hero_contract",
            () -> IForgeMenuType.create((windowId, inv, data) -> new HeroContractMenu(windowId, inv)));

    public static final RegistryObject<MenuType<HeroMerchantMenu>> HERO_TRADE_MENU = MENUS.register("hero_trade",
            () -> IForgeMenuType.create((windowId, inv, data) -> new HeroMerchantMenu(windowId, inv)));
}
