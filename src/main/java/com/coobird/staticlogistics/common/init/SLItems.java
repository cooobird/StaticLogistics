package com.coobird.staticlogistics.common.init;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.common.item.UpgradeItem;
import com.coobird.staticlogistics.common.item.UpgradeItem.UpgradeTier;
import com.coobird.staticlogistics.common.item.UpgradeItem.UpgradeType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.coobird.staticlogistics.Staticlogistics.chineseProviders;

public class SLItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Staticlogistics.MODID);

    public static final DeferredItem<LinkConfiguratorItem> LINK_CONFIGURATOR = register("link_configurator", "连接配置器", LinkConfiguratorItem::new);

    public static final DeferredItem<UpgradeItem> DIMENSION_UPGRADE = register("dimension_upgrade", "维度升级插件",
        () -> new UpgradeItem(UpgradeType.DIMENSION));

    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_IRON = register("speed_upgrade_iron", "铁速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.IRON));
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_GOLD = register("speed_upgrade_gold", "金速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.GOLD));
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_DIAMOND = register("speed_upgrade_diamond", "钻石速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.DIAMOND));
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_NETHERITE = register("speed_upgrade_netherite", "下界合金速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.NETHERITE));
    public static final DeferredItem<UpgradeItem> SPEED_UPGRADE_NETHER_STAR = register("speed_upgrade_nether_star", "下界之星速率升级插件",
        () -> new UpgradeItem(UpgradeType.SPEED, UpgradeTier.NETHER_STAR));

    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_IRON = register("range_upgrade_iron", "铁范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.IRON));
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_GOLD = register("range_upgrade_gold", "金范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.GOLD));
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_DIAMOND = register("range_upgrade_diamond", "钻石范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.DIAMOND));
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_NETHERITE = register("range_upgrade_netherite", "下界合金范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.NETHERITE));
    public static final DeferredItem<UpgradeItem> RANGE_UPGRADE_NETHER_STAR = register("range_upgrade_nether_star", "下界之星范围升级插件",
        () -> new UpgradeItem(UpgradeType.RANGE, UpgradeTier.NETHER_STAR));

    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_IRON = register("stack_upgrade_iron", "铁堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.IRON));
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_GOLD = register("stack_upgrade_gold", "金堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.GOLD));
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_DIAMOND = register("stack_upgrade_diamond", "钻石堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.DIAMOND));
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_NETHERITE = register("stack_upgrade_netherite", "下界合金堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.NETHERITE));
    public static final DeferredItem<UpgradeItem> STACK_UPGRADE_NETHER_STAR = register("stack_upgrade_nether_star", "下界之星堆叠升级插件",
        () -> new UpgradeItem(UpgradeType.STACK, UpgradeTier.NETHER_STAR));

    public static <I extends Item> DeferredItem<I> register(final String en, final String zh, Supplier<I> it) {
        DeferredItem<I> item = ITEMS.register(en, it);
        chineseProviders.add(l -> l.addItem(item, zh));
        return item;
    }
}