package com.coobird.staticlogistics.common.data.gen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLCreativeTabs;
import com.coobird.staticlogistics.core.ConnectionMode;
import com.coobird.staticlogistics.core.DistributionStrategy;
import com.coobird.staticlogistics.transfer.TransferType;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
        if (raw == null || raw.isEmpty()) return "";
        String name = raw.substring(raw.lastIndexOf('.') + 1);
        return Arrays.stream(name.split("_"))
            .filter(word -> !word.isEmpty())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    @Override
    protected void addTranslations() {
        addCreativeTab(SLCreativeTabs.TAB_STATIC_LOGISTICS, "Static Logistics", "静态物流");

        add("gui.staticlogistics.linker_settings", "Linker Configuration", "链路配置工具");
        add("gui.staticlogistics.save", "Save Settings", "保存设置");
        add("gui.staticlogistics.label.priority", "Priority:", "优先级:");
        add("gui.staticlogistics.label.group", "Group ID:", "分组:");
        add("gui.staticlogistics.label.strategy", "Strategy:", "分配策略:");
        add("gui.staticlogistics.label.bulk", "Bulk Size:", "传输步长:");

        add("mode.staticlogistics.connect", "Connect", "连接模式");
        add("mode.staticlogistics.remove", "Remove", "移除模式");
        add("mode.staticlogistics.configure", "Configure", "配置模式");

        add("tooltip.staticlogistics.mode", "Mode: %s", "当前模式: %s");
        add("tooltip.staticlogistics.type", "Type: %s", "传输类型: %s");
        add("tooltip.staticlogistics.group", "Group: %s", "分组: %s");

        add("tooltip.staticlogistics.linked_from", "Source Locked: %s", "当前起点 (锁定): %s");
        add("tooltip.staticlogistics.no_source", "No Source Selected", "尚未选择起点方块");
        add("tooltip.staticlogistics.use_hint", "Right-click: Open Config GUI", "右键: 打开设置界面");
        add("tooltip.staticlogistics.shift_use_hint", "Sneak + Right-click: Set Source / Link Multiple", "潜行+右键: 设定起点 / 连续连接终点");
        add("tooltip.staticlogistics.reset_hint", "Sneak + Right-click Air or Source: Clear selection", "潜行+右键空气或起点: 手动清除选点");

        add("tooltip.staticlogistics.upgrade.install_hint", "Install in nodes to enhance capabilities.", "安装至节点以增强功能。");
        add("tooltip.staticlogistics.upgrade.value", "Multiplier: %s", "数值倍率: %s");
        add("tooltip.staticlogistics.upgrade.tier_display", "Tier: %s", "等级: %s");
        add("tooltip.staticlogistics.upgrade.dimension_feature", "Enables cross-dimensional transport.", "解锁跨维度传输功能。");
        add("tooltip.staticlogistics.upgrade.speed_desc", "Increases transfer frequency.", "提升传输频率。");
        add("tooltip.staticlogistics.upgrade.range_desc", "Extends the maximum link distance.", "延伸最大连接距离。");
        add("tooltip.staticlogistics.upgrade.stack_desc", "Increases items moved per operation.", "增加单次传输的堆叠数量。");
        add("tooltip.staticlogistics.upgrade.dimension_desc", "Allows linking across different worlds.", "允许跨越不同世界进行连接。");

        add("tier.staticlogistics.iron", "Iron", "铁");
        add("tier.staticlogistics.gold", "Gold", "金");
        add("tier.staticlogistics.diamond", "Diamond", "钻石");
        add("tier.staticlogistics.netherite", "Netherite", "下界合金");
        add("tier.staticlogistics.creative", "Creative", "创造");

        add("msg.staticlogistics.source_set", "Source Set: %s (%s) [Group: %s]", "已锁定起点: %s (%s) [分组: %s]");
        add("msg.staticlogistics.source_reset", "Selection Cleared", "选点已手动清除");
        add("msg.staticlogistics.link_created", "Linked to: %s (Source Kept)", "连接成功 -> %s (起点已保留)");
        add("msg.staticlogistics.out_of_range", "Target out of range! (Max: %s)", "距离过远！(最大距离: %s)");
        add("msg.staticlogistics.owner_updated", "Ownership updated to yours.", "链路所有权已更新。");
        add("msg.staticlogistics.links_cleared", "Cleared %s link(s).", "已成功拆除 %s 条链路。");
        add("msg.staticlogistics.no_link_found", "No link detected here.", "此位置没有任何链路。");
        add("msg.staticlogistics.no_permission", "You don't have permission to modify this link!", "你没有权限修改此链路！");
        add("msg.staticlogistics.no_dimension_upgrade", "Cross-dimension upgrade required!", "需要跨维度升级插件！");
        add("msg.staticlogistics.cannot_link_self", "Cannot link a face to itself!", "不能连接到自身！");

        add("config.staticlogistics.default_radius", "Default Link Radius", "默认连接半径");
        add("config.staticlogistics.default_tick_interval", "Default Tick Interval", "默认传输间隔(Tick)");
        add("config.staticlogistics.item_stack_size", "Base Item Stack Size", "基础物品传输量");
        add("config.staticlogistics.fluid_stack_size", "Base Fluid Amount (mB)", "基础流体传输量");
        add("config.staticlogistics.energy_stack_size", "Base Energy Amount (FE)", "基础能量传输量");
        add("config.staticlogistics.mek_chemical_stack_size", "Base Chemical Amount (mB)", "基础化学品传输量");
        add("config.staticlogistics.ars_source_stack_size", "Base Source Amount", "基础魔源传输量");
        add("config.staticlogistics.iron_multiplier", "Iron Tier Multiplier", "铁等级倍率");
        add("config.staticlogistics.gold_multiplier", "Gold Tier Multiplier", "金等级倍率");
        add("config.staticlogistics.diamond_multiplier", "Diamond Tier Multiplier", "钻石等级倍率");
        add("config.staticlogistics.netherite_multiplier", "Netherite Tier Multiplier", "下界合金等级倍率");

        for (TransferType type : TransferType.values()) {
            String cn = switch (type) {
                case ITEM -> "物品";
                case FLUID -> "流体";
                case ENERGY -> "能量";
                case MEK_CHEMICALS -> "化学品";
                case ARS_SOURCE -> "魔源";
            };
            add("type.staticlogistics." + type.getSerializedName(), toTitleCase(type.getSerializedName()), cn);
        }

        for (DistributionStrategy strategy : DistributionStrategy.values()) {
            String zh = switch (strategy) {
                case SEQUENTIAL -> "顺序优先";
                case ROUND_ROBIN -> "轮询分发";
                case NEAREST -> "最近优先";
                case FURTHEST -> "最远优先";
                case RANDOM -> "随机分发";
            };
            add(strategy.getDescriptionId(), toTitleCase(strategy.getSerializedName()), zh);
        }

        for (ConnectionMode mode : ConnectionMode.values()) {
            String zh = switch (mode) {
                case DISABLED -> "禁用";
                case INPUT -> "仅输入";
                case OUTPUT -> "仅输出";
                case BOTH -> "双向";
            };
            add("connection.staticlogistics." + mode.getSerializedName(), toTitleCase(mode.getSerializedName()), zh);
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

    public void addCreativeTab(Supplier<CreativeModeTab> tab, String en, String zh) {
        Component title = tab.get().getDisplayName();
        if (title.getContents() instanceof TranslatableContents translatable) {
            this.add(translatable.getKey(), en, zh);
        }
    }
}