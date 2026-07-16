package com.coobird.staticlogistics.content.registry;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.content.menu.NodeConfiguratorMenu;
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
    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.MENU, StaticLogistics.MODID);

    /**
     * 节点配置器菜单：统一管理某一面的连接参数和容器升级插件
     */
    public static final DeferredHolder<MenuType<?>, MenuType<NodeConfiguratorMenu>> NODE_CONFIGURATOR_MENU =
        TYPES.register("node_configurator_menu", () -> IMenuTypeExtension.create(NodeConfiguratorMenu::new));

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
            int inventorySlot = buf.readVarInt();
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            return new HandFilterMenu(id, inv, stack, inventorySlot);
        }));
}
