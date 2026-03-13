package com.coobird.staticlogistics.common.data.gen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLCreativeTabs;
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

        add("mode.staticlogistics.connect", "Connect", "连接模式");
        add("mode.staticlogistics.remove", "Remove", "移除模式");
        add("mode.staticlogistics.configure", "Configure", "配置模式");

        add("msg.staticlogistics.source_set", "Source Set: %s (%s)", "已设置起点: %s (%s)");
        add("msg.staticlogistics.source_reset", "Source Cleared", "选点已清除");
        add("msg.staticlogistics.link_created_multiple", "Linked to: %s", "已连接至: %s");
        add("msg.staticlogistics.links_cleared", "Cleared links at %s", "已清除 %s 的所有链路");
        add("msg.staticlogistics.todo.face_gui", "Face Config for %s (Coming Soon)", "%s 面配置界面 (开发中)");

        add("tooltip.staticlogistics.linker.mode", "Mode: %s", "当前模式: %s");
        add("tooltip.staticlogistics.linker.source", "Source: %s (%s)", "已选起点: %s (%s)");
        add("tooltip.staticlogistics.linker.type", "Type: %s", "传输类型: %s");
        add("tooltip.staticlogistics.linker.reset_hint", "Sneak + Right-click anywhere to reset source", "潜行 + 右键点击任意位置以清空选点");
        add("tooltip.staticlogistics.linker.gui_hint", "Right-click air to open tool settings", "直接右键空气打开配置界面");

        add("gui.staticlogistics.linker_settings", "Linker Settings", "配置器设置");
        add("gui.staticlogistics.save", "Save", "保存");
        add("gui.staticlogistics.label.priority", "Priority:", "优先级:");
        add("gui.staticlogistics.label.group", "Group ID:", "组 ID:");

        add("tooltip.staticlogistics.upgrade.speed_desc", "Increases transfer frequency.", "提高传输频率。");
        add("tooltip.staticlogistics.upgrade.range_desc", "Extends connection range.", "延长连接距离。");
        add("tooltip.staticlogistics.upgrade.stack_desc", "Increases transfer stack size and energy rate.", "提高单次搬运数量及能量传输速率。");
        add("tooltip.staticlogistics.upgrade.dimension_desc", "Enables cross-dim transfer.", "解锁跨维度传输。");
        add("tooltip.staticlogistics.upgrade.install_hint", "Place in Face Config", "放入面配置槽位以生效");

        add("tooltip.staticlogistics.upgrade.tier_label", "Tier", "级别");
        add("tooltip.staticlogistics.upgrade.value", "Multiplier: %s", "倍率: %s");
        add("tooltip.staticlogistics.upgrade.dimension_feature", "Bypasses distance and dimension limits", "无视距离与维度限制");
        add("tooltip.staticlogistics.upgrade.dimension_requirement", "Requires 64 Range Upgrades to function.", "需要插满 64 个范围卡方可生效。");

        String[][] tiers = {{"iron", "铁"}, {"gold", "金"}, {"diamond", "钻石"}, {"netherite", "下界合金"}, {"creative", "创造"}};
        for (String[] t : tiers) add("tier.staticlogistics." + t[0], toTitleCase(t[0]), t[1]);

        for (TransferType type : TransferType.values()) {
            String cn = switch (type) {
                case ITEM -> "物品";
                case FLUID -> "流体";
                case ENERGY -> "能量";
            };
            add("type.staticlogistics." + type.getSerializedName(), toTitleCase(type.getSerializedName()), cn);
        }

        Staticlogistics.chineseProviders.forEach(action -> action.accept(this));
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