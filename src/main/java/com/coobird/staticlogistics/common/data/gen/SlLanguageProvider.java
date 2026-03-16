package com.coobird.staticlogistics.common.data.gen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLCreativeTabs;
import com.coobird.staticlogistics.common.item.UpgradeItem;
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
        if (raw.isEmpty()) return "";
        String name = raw.substring(raw.lastIndexOf('.') + 1);
        return Arrays.stream(name.split("_"))
            .filter(word -> !word.isEmpty())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    @Override
    protected void addTranslations() {
        addCreativeTab(SLCreativeTabs.TAB_STATIC_LOGISTICS, "Static Logistics", "静态物流");

        add("gui.staticlogistics.linker_settings", "Linker Configuration", "连接配置工具");
        add("gui.staticlogistics.save", "Save Settings", "保存设置");
        add("gui.staticlogistics.label.group", "Group Id:", "分组 ID：");
        add("gui.staticlogistics.label.priority", "Priority: ", "优先级：");
        add("gui.staticlogistics.face_config.title", "Node Configuration", "节点面配置");
        add("gui.staticlogistics.label.distribution", "Distribution: ", "分发策略：");
        add("gui.staticlogistics.label.filter", "Filter: ", "过滤器：");
        add("gui.staticlogistics.label.blacklist", "Blacklist Mode", "黑名单模式");
        add("gui.staticlogistics.label.whitelist", "Whitelist Mode", "白名单模式");
        add("gui.staticlogistics.label.channel_color", "Channel Color: ", "通道染色：");
        add("gui.staticlogistics.upgrade_slots", "Upgrades", "升级插件槽");
        add("gui.staticlogistics.label.stats", "Current Stats: ", "当前属性：");
        add("gui.staticlogistics.label.range", "Range: x%s", "范围：x%s");
        add("gui.staticlogistics.label.speed", "Speed: x%s", "速度：x%s");
        add("gui.staticlogistics.label.stack", "Stack Size: x%s", "堆叠限制：x%s");

        add("gui.staticlogistics.group_management", "Group Management", "物流分组管理");
        add("gui.staticlogistics.rename_group", "Rename Group", "重命名组");
        add("gui.staticlogistics.delete_group", "Delete Group", "删除组");
        add("gui.staticlogistics.confirm_delete", "Are you sure you want to delete this group and all its links?", "确定要删除该组及其所有链路吗？");

        add("msg.staticlogistics.group_display", "Group Id: %s", "分组 ID：%s");
        add("msg.staticlogistics.target_count", "Targets: %s", "目的地：%s");
        add("msg.staticlogistics.owner_display", "Owner: %s", "所有者：%s");

        add("mode.staticlogistics.link_as_input", "Select the point as the input", "选取点为输入");
        add("mode.staticlogistics.link_as_output", "Select the point as the output", "选取点为输出");
        add("mode.staticlogistics.configure", "Configure Node Face", "配置节点面");
        add("mode.staticlogistics.remove", "Remove Links", "移除现有链路");

        add("tooltip.staticlogistics.mode", "Mode: %s", "工具模式：%s");
        add("tooltip.staticlogistics.type", "Transfer Type: %s", "传输类型：%s");
        add("tooltip.staticlogistics.group", "Group Id: %s", "组 ID：%s");
        add("tooltip.staticlogistics.stored_nodes", "Stored Nodes: %s (As %s)", "待连接节点：%s（设为 %s）");
        add("tooltip.staticlogistics.upgrade_type", "Type: %s", "升级类型：%s");
        add("tooltip.staticlogistics.upgrade.tier_display", "Tier: %s", "等级：%s");
        add("tooltip.staticlogistics.upgrade.value", "Multiplier: %s", "效果倍率：%s");
        add("tooltip.staticlogistics.upgrade.dimension_feature", "Enables interdimensional logistics.", "解锁跨维度物流传输。");
        add("tooltip.staticlogistics.upgrade.install_hint", "Install into nodes to enhance capabilities.", "安装至节点以增强其传输属性。");

        add("staticlogistics.configuration.general", "General Settings", "基础设置");
        add("staticlogistics.configuration.upgrades", "Upgrade Settings", "插件参数");
        add("config.staticlogistics.category.values", "Base Transfer Values", "基础传输数值");
        add("config.staticlogistics.category.multipliers", "Upgrade Multipliers", "升级插件倍率");
        add("config.staticlogistics.default_radius", "Default Link Radius", "默认连接半径");
        add("config.staticlogistics.default_tick_interval", "Base Tick Interval", "基础传输间隔(Tick)");
        add("config.staticlogistics.item_stack_size", "Base Item Stack Size", "基础物品堆叠量");
        add("config.staticlogistics.fluid_stack_size", "Base Fluid Amount (mB)", "基础流体传输量(mB)");
        add("config.staticlogistics.energy_stack_size", "Base Energy Amount (FE)", "基础能量传输量(FE)");
        add("config.staticlogistics.mek_chemical_stack_size", "Base Mek-Chemical Amount", "基础 Mek 化学品传输量");
        add("config.staticlogistics.ars_source_stack_size", "Base Ars-Source Amount", "基础魔源传输量");
        add("config.staticlogistics.iron_multiplier", "Iron Tier Multiplier", "铁倍率插件系数");
        add("config.staticlogistics.gold_multiplier", "Gold Tier Multiplier", "金倍率插件系数");
        add("config.staticlogistics.diamond_multiplier", "Diamond Tier Multiplier", "钻石倍率插件系数");
        add("config.staticlogistics.netherite_multiplier", "Netherite Tier Multiplier", "下界合金倍率插件系数");

        add("msg.staticlogistics.node_added", "Node recorded. Total: %s", "节点已记录，当前共计：%s");
        add("msg.staticlogistics.node_removed", "Node unrecorded. Remaining: %s", "节点记录已移除，剩余：%s");
        add("msg.staticlogistics.selection_cleared", "Selection Cleared", "已清空已记录节点");
        add("msg.staticlogistics.batch_linked", "Successfully linked %s nodes!", "成功建立了 %s 条链路！");
        add("msg.staticlogistics.batch_linked_to_group", "Successfully linked %s nodes to Group: %s!", "成功将 %s 条链路连接至分组：%s！");
        add("msg.staticlogistics.no_nodes_stored", "No nodes stored in linker!", "连接器中未存储任何节点！");
        add("msg.staticlogistics.link_failed", "Failed to create links. Check connection rules.", "建立连接失败。请检查连接规则。");
        add("msg.staticlogistics.links_created", "Created %s new link(s)", "建立了 %s 条新链路");
        add("msg.staticlogistics.links_merged", "Merged %s link(s) with %s", "合并了 %s 条链路的 %s 传输");
        add("msg.staticlogistics.no_valid_links", "No valid connections possible.", "无法建立有效的连接。");
        add("msg.staticlogistics.out_of_range", "Target out of range!", "目标超出范围！");
        add("msg.staticlogistics.mode_switched", "Mode: %s", "当前模式：%s");
        add("msg.staticlogistics.mode_switched_with_nodes", "Mode: %s (%s nodes stored)", "当前模式：%s（已存储 %s 个节点）");
        add("msg.staticlogistics.links_removed", "Successfully removed %s logistics links", "已成功移除 %s 条物流链接");
        add("msg.staticlogistics.no_links_found", "No active links found at this position", "该位置未发现活动的物流链接");
        add("msg.staticlogistics.links_cleaned_at", "Cleared all links at %s", "已清除位置 %s 处的所有链路");
        add("msg.staticlogistics.no_permission", "You do not have permission to modify this link.", "你没有权限修改此链路。");
        add("msg.staticlogistics.ftb_team_required", "This action requires an FTB Team with appropriate permissions.", "此操作需要具备相应权限的 FTB 团队。");

        add("commands.staticlogistics.info.header", "--- Logistics Info at %s ---", "--- 位置 %s 的物流信息 ---");
        add("commands.staticlogistics.info.no_links", "No active source links on this block face.", "该方块表面没有活动的源链路。");
        add("commands.staticlogistics.info.line_format", "  [%s] %s | %s | %s", "  [%s] %s | %s | %s");
        add("commands.staticlogistics.transfer.success", "Successfully transferred %s link(s) from %s to %s", "成功将 %s 条链路从玩家 %s 转移给 %s");
        add("commands.staticlogistics.transfer.group_not_found", "No matching groups found for that player.", "未找到该玩家匹配的分组。");
        add("commands.staticlogistics.transfer.group_success", "Successfully transferred Group '%2$s' (%3$s links) from %1$s to %4$s", "已成功将玩家 %1$s 的分组“%2$s”（共 %3$s 条链路）转移给 %4$s");
        add("commands.staticlogistics.rename.not_found", "No matching groups found to rename.", "未找到匹配的分组进行重命名。");
        add("commands.staticlogistics.rename.success", "Group '%s' renamed to '%s' for player %s", "已为玩家 %3$s 将分组“%1$s”重命名为“%2$s”");
        add("commands.staticlogistics.cleanup.success", "Deleted %s link(s) owned by %s", "已清理属于玩家 %2$s 的 %1$s 条链路");

        for (UpgradeItem.UpgradeType type : UpgradeItem.UpgradeType.values()) {
            String key = "tooltip.staticlogistics.upgrade." + type.name().toLowerCase() + "_desc";
            String enDesc = switch (type) {
                case SPEED -> "Decreases the time interval between transfers.";
                case RANGE -> "Increases the maximum distance for wireless links.";
                case STACK -> "Increases the maximum amount of resources moved per tick.";
                case DIMENSION -> "Enables logistics across different dimensions.";
            };
            String zhDesc = switch (type) {
                case SPEED -> "缩短传输间隔时间。";
                case RANGE -> "增加链路连接的最大距离。";
                case STACK -> "增加单次传输的数量限制。";
                case DIMENSION -> "无视维度进行传输。";
            };
            add(key, enDesc, zhDesc);
            add("upgrade_type.staticlogistics." + type.name().toLowerCase(), toTitleCase(type.name()) + " Upgrade", zhDesc.replaceAll("[。，]", "").replace("缩短", "").replace("增加", "").replace("无视", ""));
        }

        for (UpgradeItem.UpgradeTier tier : UpgradeItem.UpgradeTier.values()) {
            String zh = switch (tier) {
                case IRON -> "铁";
                case GOLD -> "金";
                case DIAMOND -> "钻石";
                case NETHERITE -> "下界合金";
                case CREATIVE -> "创造";
            };
            add("tier.staticlogistics." + tier.getSerializedName(), toTitleCase(tier.getSerializedName()), zh);
        }

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
                case INPUT -> "输入";
                case OUTPUT -> "输出";
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