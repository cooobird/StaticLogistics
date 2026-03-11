package com.coobird.staticlogistics.common.init;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.coobird.staticlogistics.Staticlogistics.chineseProviders;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Staticlogistics.MODID);

    public static final DeferredItem<LinkConfiguratorItem> LinkConfigurator = register("link_configurator", "链路配置器", LinkConfiguratorItem::new);

    public static <I extends Item> DeferredItem<I> register(final String en, final String zh, Supplier<I> it) {
        DeferredItem<I> item = ITEMS.register(en, it);
        chineseProviders.add(l -> l.addItem(item, zh));
        return item;
    }
}
