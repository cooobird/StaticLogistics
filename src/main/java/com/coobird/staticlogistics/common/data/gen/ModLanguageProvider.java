package com.coobird.staticlogistics.common.data.gen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.ModCreativeTabs;
import com.coobird.staticlogistics.core.TransferType;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.coobird.staticlogistics.Staticlogistics.chineseProviders;

public class ModLanguageProvider extends LanguageProvider {
    private final String locale;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, Staticlogistics.MODID, locale);
        this.locale = locale;
    }

    private static String toTitleCase(String raw) {
        String name = raw.substring(raw.lastIndexOf('.') + 1);
        return Arrays.stream(name.split("_"))
            .filter(word -> !word.isEmpty())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    @Override
    protected void addTranslations() {
        addCreativeTab(ModCreativeTabs.TAB_STATIC_LOGISTICS, "Static Logistics", "静态物流");

        add("msg.staticlogistics.no_capability",
            "This block has no valid container interface!",
            "该位置没有有效的容器接口！");
        add("msg.staticlogistics.invalid_target",
            "Invalid target for logistics link!",
            "无效的物流目标位置！");
        add("msg.staticlogistics.source_set",
            "Recorded source: %s (Face: %s)",
            "已记录起点: %s (面: %s)");
        add("msg.staticlogistics.link_created",
            "New link established to: %s",
            "已建立新的物流链路: %s");
        add("msg.staticlogistics.link_updated",
            "Link configuration updated at: %s",
            "已更新链路配置: %s");
        add("msg.staticlogistics.link_removed",
            "Link completely removed.",
            "链路已彻底移除。");
        add("msg.staticlogistics.type_removed",
            "Specific transfer type removed from link.",
            "已从链路中移除该传输类型。");
        add("msg.staticlogistics.record_cleared",
            "Configuration record cleared.",
            "临时坐标记录已清除。");

        for (TransferType type : TransferType.values()) {
            String key = "type.staticlogistics." + type.getSerializedName();
            String zhName = switch (type) {
                case ITEM -> "物品";
                case FLUID -> "流体";
            };
            add(key, toTitleCase(type.getSerializedName()), zhName);
        }

        add("tooltip.staticlogistics.linker.source", "Source: %s (%s)", "起点坐标: %s (%s)");
        add("tooltip.staticlogistics.linker.no_source", "No source set (Sneak + Right-click a block)", "未设置起点 (潜行+右键方块开始)");
        add("tooltip.staticlogistics.linker.group", "Target Group: %s", "目标分组: %s");
        add("tooltip.staticlogistics.linker.type", "Transfer Mode: %s", "传输模式: %s");
        add("tooltip.staticlogistics.linker.priority", "Priority: %s", "优先级: %s");

        add("tooltip.staticlogistics.linker.desc_link",
            "§e▶ Sneak + Right-click Block: §7Set source / Link destination",
            "§e▶ 潜行 + 右键方块: §7设置起点 / 建立连接");
        add("tooltip.staticlogistics.linker.desc_gui",
            "§7▶ Right-click Block: §fOpen face settings",
            "§7▶ 直接右键方块: §f打开该面详细配置");
        add("tooltip.staticlogistics.linker.desc_remove",
            "§c▶ Sneak + Right-click (Existing): §7Toggle/Remove link type",
            "§c▶ 潜行 + 右键(已有连接): §7切换或移除该类型链路");

        add("strategy.staticlogistics.sequential", "Sequential", "顺序分发");
        add("strategy.staticlogistics.round_robin", "Round Robin", "轮询分发");
        add("strategy.staticlogistics.nearest", "Nearest First", "最近优先");
        add("strategy.staticlogistics.furthest", "Furthest First", "最远优先");
        add("strategy.staticlogistics.random", "Random", "随机分发");

        if (this.locale.equals("zh_cn")) {
            chineseProviders.forEach(a -> a.accept(this));
        }
    }

    public void add(String key, String en, String zh) {
        if (this.locale.equals("zh_cn")) {
            super.add(key, zh);
        } else {
            super.add(key, en);
        }
    }

    public void addBlock(DeferredHolder<Block, ? extends Block> key, String zh) {
        this.add(key.get().getDescriptionId(), toTitleCase(key.get().getDescriptionId()), zh);
    }

    public void addItem(DeferredHolder<Item, ? extends Item> key, String zh) {
        this.add(key.get().getDescriptionId(), toTitleCase(key.get().getDescriptionId()), zh);
    }

    public void addEffect(DeferredHolder<MobEffect, ? extends MobEffect> key, String zh) {
        this.add(key.get().getDescriptionId(), toTitleCase(key.get().getDescriptionId()), zh);
    }

    public void addCreativeTab(Supplier<CreativeModeTab> tab, String en, String zh) {
        Component title = tab.get().getDisplayName();
        if (title.getContents() instanceof TranslatableContents translatable) {
            this.add(translatable.getKey(), en, zh);
        }
    }
}