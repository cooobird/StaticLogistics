package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.type.UpgradeTier;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.item.BlueprintItem;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.item.UpgradeItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.coobird.staticlogistics.Staticlogistics.chineseProviders;

/**
 * 注册模组的所有物品，包括连接配置器、各种升级插件（速度/范围/堆叠/过滤）。
 */
public class SLItems {
    /**
     * 物品延迟注册器
     */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Staticlogistics.MODID);
    /**
     * 方块物品延迟注册器（方块对应的物品形态）
     */
    public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(Staticlogistics.MODID);

    /**
     * 连接配置器：核心工具，用于配置物流节点之间的连接
     */
    public static final DeferredItem<LinkConfiguratorItem> LINK_CONFIGURATOR = register("link_configurator", "连接配置器", LinkConfiguratorItem::new);

    /**
     * 物流蓝图：复制/粘贴一个区域内的所有物流配置
     */
    public static final DeferredItem<BlueprintItem> BLUEPRINT = register("blueprint", "物流蓝图", BlueprintItem::new);

    /**
     * 维度升级插件：允许跨维度传输
     */
    public static final DeferredItem<UpgradeItem> DIMENSION_UPGRADE = register("dimension_upgrade", "维度升级插件",
        () -> new UpgradeItem(UpgradeType.DIMENSION));

    /**
     * 铁速率升级插件
     */
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_IRON = register("speed_upgrade_iron", "铁速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.IRON));
    /**
     * 金速率升级插件
     */
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_GOLD = register("speed_upgrade_gold", "金速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.GOLD));
    /**
     * 钻石速率升级插件
     */
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_DIAMOND = register("speed_upgrade_diamond", "钻石速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.DIAMOND));
    /**
     * 下界合金速率升级插件
     */
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_NETHERITE = register("speed_upgrade_netherite", "下界合金速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.NETHERITE));
    /**
     * 下界之星速率升级插件
     */
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_NETHER_STAR = register("speed_upgrade_nether_star", "下界之星速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.NETHER_STAR));

    /**
     * 铁范围升级插件
     */
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_IRON = register("range_upgrade_iron", "铁范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.IRON));
    /**
     * 金范围升级插件
     */
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_GOLD = register("range_upgrade_gold", "金范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.GOLD));
    /**
     * 钻石范围升级插件
     */
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_DIAMOND = register("range_upgrade_diamond", "钻石范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.DIAMOND));
    /**
     * 下界合金范围升级插件
     */
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_NETHERITE = register("range_upgrade_netherite", "下界合金范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.NETHERITE));
    /**
     * 下界之星范围升级插件
     */
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_NETHER_STAR = register("range_upgrade_nether_star", "下界之星范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.NETHER_STAR));

    /**
     * 铁堆叠升级插件
     */
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_IRON = register("stack_upgrade_iron", "铁堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.IRON));
    /**
     * 金堆叠升级插件
     */
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_GOLD = register("stack_upgrade_gold", "金堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.GOLD));
    /**
     * 钻石堆叠升级插件
     */
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_DIAMOND = register("stack_upgrade_diamond", "钻石堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.DIAMOND));
    /**
     * 下界合金堆叠升级插件
     */
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_NETHERITE = register("stack_upgrade_netherite", "下界合金堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.NETHERITE));
    /**
     * 下界之星堆叠升级插件
     */
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_NETHER_STAR = register("stack_upgrade_nether_star", "下界之星堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.NETHER_STAR));

    /**
     * 基础过滤升级插件：允许按物品类型过滤
     */
    public static final DeferredItem<UpgradeItem> BASIC_FILTER_UPGRADE = register("basic_filter_upgrade", "基础过滤升级插件",
        () -> new UpgradeItem(UpgradeType.BASIC_FILTER, 64));
    /**
     * 标签过滤升级插件：允许按物品标签过滤
     */
    public static final DeferredItem<UpgradeItem> TAG_FILTER_UPGRADE = register("tag_filter_upgrade", "标签过滤升级插件",
        () -> new UpgradeItem(UpgradeType.TAG_FILTER, 64));
    /**
     * NBT过滤升级插件：允许按物品NBT数据过滤
     */
    public static final DeferredItem<UpgradeItem> NBT_FILTER_UPGRADE = register("nbt_filter_upgrade", "NBT过滤升级插件",
        () -> new UpgradeItem(UpgradeType.NBT_FILTER, 64));

    /**
     * 注册物品并同时注册其中文名到国际化提供器
     */
    public static <I extends Item> DeferredItem<I> register(final String en, final String zh, Supplier<I> it) {
        DeferredItem<I> item = ITEMS.register(en, it);
        chineseProviders.add(l -> l.addItem(item, zh));
        return item;
    }

    /**
     * 将所有物品和方块物品注册到事件总线
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        BLOCK_ITEMS.register(eventBus);
    }
}