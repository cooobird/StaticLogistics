package com.coobird.staticlogistics.content.registry;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.content.menu.NodeConfiguratorMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class SLMenuTypes {
    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, StaticLogistics.MODID);

    public static final Supplier<MenuType<NodeConfiguratorMenu>> NODE_CONFIGURATOR_MENU =
        TYPES.register("node_configurator_menu", () -> IForgeMenuType.create(NodeConfiguratorMenu::new));
    public static final Supplier<MenuType<FilterConfiguratorMenu>> FILTER_CONFIG =
        TYPES.register("filter_configurator_menu", () -> IForgeMenuType.create(FilterConfiguratorMenu::fromBuffer));
    public static final Supplier<MenuType<HandFilterMenu>> HAND_FILTER =
        TYPES.register("hand_filter", () -> IForgeMenuType.create((id, inv, buf) -> {
            int inventorySlot = buf.readVarInt();
            ItemStack stack = buf.readItem();
            return new HandFilterMenu(id, inv, stack, inventorySlot);
        }));
}
