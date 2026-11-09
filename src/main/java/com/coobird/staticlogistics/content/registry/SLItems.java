package com.coobird.staticlogistics.content.registry;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.transfer.UpgradeTier;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

import static com.coobird.staticlogistics.StaticLogistics.chineseProviders;

/// 注册连接配置器、综合升级卡和通道过滤器。
public class SLItems {
    /// 物品延迟注册器
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, StaticLogistics.MODID);

    /// 连接配置器：核心工具，用于配置物流节点之间的连接
    public static final RegistryObject<LinkConfiguratorItem> LINK_CONFIGURATOR = register("link_configurator", "连接配置器", LinkConfiguratorItem::new);

    public static final RegistryObject<UpgradeItem> COMBINED_UPGRADE_IRON = register("combined_upgrade_iron", "铁综合升级卡",
        () -> new UpgradeItem(UpgradeType.COMBINED, UpgradeTier.IRON));
    public static final RegistryObject<UpgradeItem> COMBINED_UPGRADE_GOLDEN = register("combined_upgrade_golden", "金综合升级卡",
        () -> new UpgradeItem(UpgradeType.COMBINED, UpgradeTier.GOLD));
    public static final RegistryObject<UpgradeItem> COMBINED_UPGRADE_DIAMOND = register("combined_upgrade_diamond", "钻石综合升级卡",
        () -> new UpgradeItem(UpgradeType.COMBINED, UpgradeTier.DIAMOND));
    public static final RegistryObject<UpgradeItem> COMBINED_UPGRADE_NETHERITE = register("combined_upgrade_netherite", "下界合金综合升级卡",
        () -> new UpgradeItem(UpgradeType.COMBINED, UpgradeTier.NETHERITE));
    public static final RegistryObject<UpgradeItem> COMBINED_UPGRADE_NETHER_STAR = register("combined_upgrade_nether_star", "下界之星综合升级卡",
        () -> new UpgradeItem(UpgradeType.COMBINED, UpgradeTier.NETHER_STAR));

    public static final RegistryObject<UpgradeItem> COMBINED_FILTER_UPGRADE = register("combined_filter_upgrade", "综合过滤器",
        () -> new UpgradeItem(UpgradeType.FILTER));


    public static <I extends Item> RegistryObject<I> register(final String en, final String zh, Supplier<I> it) {
        RegistryObject<I> item = ITEMS.register(en, it);
        chineseProviders.add(l -> l.addItem(item, zh));
        return item;
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
