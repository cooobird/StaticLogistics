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

/**
 * 注册模组的各种GUI菜单类型（面配置、容器配置、过滤器配置、手持过滤器）。
 */
public class SLMenuTypes {
    /**
     * 菜单类型延迟注册器
     */
    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.MENU, Staticlogistics.MODID);

    /**
     * 面配置器菜单：配置物流节点某一面的连接参数
     */
    public static final DeferredHolder<MenuType<?>, MenuType<FaceConfiguratorMenu>> FACE_CONFIGURATOR_MENU =
        TYPES.register("face_configurator_menu", () -> IMenuTypeExtension.create(FaceConfiguratorMenu::new));

    /**
     * 容器配置器菜单：配置容器级别的升级插件和参数
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ContainerConfiguratorMenu>> CONTAINER_CONFIGURATOR_MENU =
        TYPES.register("container_configurator_menu", () -> IMenuTypeExtension.create(ContainerConfiguratorMenu::new));

    /**
     * 过滤器配置菜单：配置物品过滤规则
     */
    public static final DeferredHolder<MenuType<?>, MenuType<FilterConfiguratorMenu>> FILTER_CONFIG =
        TYPES.register("filter_configurator_menu", () -> IMenuTypeExtension.create(FilterConfiguratorMenu::fromBuffer));

    /**
     * 手持过滤器菜单：直接拿着物品编辑过滤规则
     */
    public static final DeferredHolder<MenuType<?>, MenuType<HandFilterMenu>> HAND_FILTER =
        TYPES.register("hand_filter", () -> IMenuTypeExtension.create((id, inv, buf) -> {
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            return new HandFilterMenu(id, inv, stack);
        }));
}
