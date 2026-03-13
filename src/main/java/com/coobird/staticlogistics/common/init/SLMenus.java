package com.coobird.staticlogistics.common.init;

import com.coobird.staticlogistics.Staticlogistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SLMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Staticlogistics.MODID);
}
