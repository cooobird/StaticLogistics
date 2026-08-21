package com.coobird.staticlogistics.content.registry;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 注册连接配置器及其过滤器子界面使用的菜单类型。
 */
public class SLMenuTypes {
    /**
     * 菜单类型延迟注册器
     */
    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.MENU, StaticLogistics.MODID);

    /**
     * 连接配置器菜单：统一承载网络预览、连接面配置与容器升级槽位。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<LinkConfiguratorMenu>> LINK_CONFIGURATOR_MENU =
        TYPES.register("link_configurator_menu", () -> IMenuTypeExtension.create(LinkConfiguratorMenu::new));

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
            stack.set(SLDataComponents.FILTER_DATA.get(), FilterData.STREAM_CODEC.decode(buf));
            return new HandFilterMenu(id, inv, stack, inventorySlot);
        }));
}
