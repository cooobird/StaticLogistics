package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.gui.menu.ContainerConfiguratorMenu;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import com.coobird.staticlogistics.gui.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.gui.menu.HandFilterMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SLMenuTypes {
    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.MENU, Staticlogistics.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<FaceConfiguratorMenu>> FACE_CONFIGURATOR_MENU =
        TYPES.register("face_configurator_menu", () -> IMenuTypeExtension.create(FaceConfiguratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ContainerConfiguratorMenu>> CONTAINER_CONFIGURATOR_MENU =
        TYPES.register("container_configurator_menu", () -> IMenuTypeExtension.create(ContainerConfiguratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FilterConfiguratorMenu>> FILTER_CONFIG =
        TYPES.register("filter_configurator_menu", () -> IMenuTypeExtension.create(FilterConfiguratorMenu::fromBuffer));

    public static final DeferredHolder<MenuType<?>, MenuType<HandFilterMenu>> HAND_FILTER =
        TYPES.register("hand_filter", () -> IMenuTypeExtension.create((id, inv, buf) -> {
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            return new HandFilterMenu(id, inv, stack);
        }));
}
