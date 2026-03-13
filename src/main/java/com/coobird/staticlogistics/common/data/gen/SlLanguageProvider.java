package com.coobird.staticlogistics.common.data.gen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLCreativeTabs;
import com.coobird.staticlogistics.transfer.TransferType;
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

public class SlLanguageProvider extends LanguageProvider {
    private final String locale;

    public SlLanguageProvider(PackOutput output, String locale) {
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
        addCreativeTab(SLCreativeTabs.TAB_STATIC_LOGISTICS, "Static Logistics", "静态物流");

        add("config.staticlogistics.default_radius", "Base Radius", "基础半径");
        add("config.staticlogistics.default_radius.comment", "Base connection range without upgrades.", "无升级时的基础连接距离(方块)。");
        add("config.staticlogistics.default_tick_interval", "Tick Interval", "传输频率");
        add("config.staticlogistics.default_tick_interval.comment", "Ticks between operations (20=1s).", "两次操作间的Ticks间隔。");
        add("config.staticlogistics.item_stack_size", "Item Limit", "物品传输上限");
        add("config.staticlogistics.item_stack_size.comment", "Max items per transfer.", "单次搬运的物品上限。");
        add("config.staticlogistics.fluid_stack_size", "Fluid Limit", "流体传输上限");
        add("config.staticlogistics.fluid_stack_size.comment", "Max mB per transfer.", "单次搬运的流体上限(mB)。");
        add("config.staticlogistics.energy_stack_size", "Energy Limit", "能量传输上限");
        add("config.staticlogistics.energy_stack_size.comment", "Max FE per transfer.", "单次搬运的能量上限(FE)。");

        add("config.staticlogistics.iron_multiplier", "Iron Multiplier", "铁级倍率");
        add("config.staticlogistics.gold_multiplier", "Gold Multiplier", "金级倍率");
        add("config.staticlogistics.diamond_multiplier", "Diamond Multiplier", "钻石级倍率");
        add("config.staticlogistics.netherite_multiplier", "Netherite Multiplier", "下界合金级倍率");

        add("mode.staticlogistics.connect", "Connect", "连接模式");
        add("mode.staticlogistics.remove", "Remove", "移除模式");
        add("mode.staticlogistics.configure", "Configure", "配置模式");

        add("msg.staticlogistics.source_set", "Source Set: %s (%s)", "已设置起点: %s (%s)");
        add("msg.staticlogistics.source_reset", "Source Cleared", "选点已清除");
        add("msg.staticlogistics.link_created", "Linked to: %s", "已连接至: %s");
        add("msg.staticlogistics.links_cleared", "Cleared links at %s", "已清除 %s 的所有链路");
        add("msg.staticlogistics.no_permission", "Access Denied!", "权限不足！");
        add("msg.staticlogistics.no_dimension_upgrade", "Cross-dimension upgrade missing!", "缺失跨维度升级！");
        add("msg.staticlogistics.too_far", "Target out of range! (Max: %s)", "超出范围！（当前上限：%s）");

        add("tooltip.staticlogistics.linker.mode", "Mode: %s", "当前模式: %s");
        add("tooltip.staticlogistics.linker.source", "Source: %s (%s)", "起点: %s (%s)");
        add("tooltip.staticlogistics.linker.type", "Type: %s", "类型: %s");

        add("gui.staticlogistics.linker_settings", "Linker Settings", "配置器设置");
        add("gui.staticlogistics.save", "Save", "保存");
        add("gui.staticlogistics.label.priority", "Priority:", "优先级:");
        add("gui.staticlogistics.label.group", "Group ID:", "组 ID:");

        String[][] tiers = {{"iron", "铁"}, {"gold", "金"}, {"diamond", "钻石"}, {"netherite", "下界合金"}, {"creative", "创造"}};
        for (String[] t : tiers) add("tier.staticlogistics." + t[0], toTitleCase(t[0]), t[1]);

        for (TransferType type : TransferType.values()) {
            String cn = switch (type) {
                case ITEM -> "物品";
                case FLUID -> "流体";
                case ENERGY -> "能量";
                case CHEMICALS -> "化学品";
            };
            add("type.staticlogistics." + type.getSerializedName(), toTitleCase(type.getSerializedName()), cn);
        }

        Staticlogistics.chineseProviders.forEach(action -> action.accept(this));
    }

    public void add(String key, String en, String zh) {
        super.add(key, this.locale.equals("zh_cn") ? zh : en);
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