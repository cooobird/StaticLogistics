package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.api.type.UpgradeTier;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.registry.SLCreativeTabs;
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
        TransferRegistries.init();

        addCreativeTab(SLCreativeTabs.TAB_STATIC_LOGISTICS, "Static Logistics", "静态物流");

        add("gui.staticlogistics.linker_settings", "Linker Configuration", "连接配置器");
        add("gui.staticlogistics.search_hint", "Search groups...", "搜索分组...");
        add("gui.staticlogistics.search_types", "Search Types", "搜索类型");
        add("gui.staticlogistics.search_for_values_by_type", "Search for values by type", "按类型搜索值");
        add("gui.staticlogistics.tooltip.toggle_type", "Click to enable/disable types", "点击以启用/禁用类型");
        add("gui.staticlogistics.tooltip.group_id", "Group #%s", "分组 #%s");
        add("gui.staticlogistics.tooltip.shift_export", "Shift + Click to export coordinates", "Shift + 点击导出坐标");
        add("gui.staticlogistics.tooltip.rename_hint", "Double-click to rename group", "双击以重命名分组");
        add("gui.staticlogistics.tooltip.select_hint", "Click to select this group", "点击以选择该分组");

        add("gui.staticlogistics.label.status", "--- Current Status ---", "--- 当前状态 ---");
        add("gui.mode.staticlogistics.input", "Insert", "存入");
        add("gui.mode.staticlogistics.output", "Extract", "提取");
        add("gui.staticlogistics.label.priority", "Priority", "优先级");
        add("gui.staticlogistics.priority.tooltip", "Hold Shift: ×10, Hold Ctrl: ×5, Hold Shift+Ctrl: ×64", "按住shift点击: ×10, 按住ctrl点击: ×5, 同时按住shift+ctrl点击: ×64");
        add("gui.staticlogistics.strategy", "Distribution Strategy", "分发策略");
        add("gui.staticlogistics.hint.speed", "Speed Upgrade", "速度升级");
        add("gui.staticlogistics.hint.range", "Range/Dim Upgrade", "范围/维度升级");
        add("gui.staticlogistics.hint.stack", "Stack Upgrade", "堆叠升级");
        add("gui.staticlogistics.hint.input_filter", "Input Filter", "输入过滤器");
        add("gui.staticlogistics.hint.output_filter", "Output Filter", "输出过滤器");
        add("gui.staticlogistics.hint.filter", "Filter Item", "过滤器插件");
        add("gui.staticlogistics.stat.transfer", "Transfer:", "传输量:");
        add("gui.staticlogistics.stat.range", "Range:", "范围:");
        add("gui.staticlogistics.stat.speed", "Speed:", "速度:");
        add("gui.staticlogistics.stat.stack", "Stack:", "堆叠:");
        add("gui.staticlogistics.stat.interval", "Interval:", "间隔:");
        add("gui.staticlogistics.unit.kelvin", "K", "开尔文");
        add("gui.staticlogistics.stat.dimension", "Cross-Dim:", "跨维度:");
        add("gui.staticlogistics.infinite", "infinite", "无限");
        add("gui.staticlogistics.unit.meters", "m", "米");
        add("gui.staticlogistics.unit.ticks", "t", "t");
        add("gui.staticlogistics.unit.multiplier", "x", "x");
        add("gui.staticlogistics.true", "true", "是");
        add("gui.staticlogistics.false", "false", "否");

        add("gui.staticlogistics.filter.title", "Filter Configuration", "详细过滤配置");
        add("gui.staticlogistics.hand_filter", "Hand Filter", "手持过滤器");
        add("gui.staticlogistics.input_filter", "Input Filter", "输入过滤器");
        add("gui.staticlogistics.output_filter", "Output Filter", "输出过滤器");
        add("gui.staticlogistics.blacklist_button", "Blacklist", "黑名单");
        add("gui.staticlogistics.whitelist_button", "Whitelist", "白名单");
        add("gui.staticlogistics.open_filter", "Configure Filter", "配置过滤器");
        add("gui.staticlogistics.face_config", "Face Configuration", "面配置");
        add("gui.staticlogistics.clear_tags", "Clear the label for this line", "清除这一行的标签");
        add("gui.staticlogistics.tag_dropdown.help", "Scroll to select tags", "滚动选择标签");
        add("gui.staticlogistics.tag_dropdown.help2", "Click the tab to select it, and then tap the selected tab again to uncheck it.", "点击标签选中，再次点击已选中的标签即可取消选中。");
        add("gui.staticlogistics.tag_dropdown.help3", "Right-click to toggle tag condition", "右键对标签条件取反");
        add("gui.staticlogistics.part_match_button", "Part Match", "部分匹配");
        add("gui.staticlogistics.full_match_button", "Full Match", "完全匹配");
        add("gui.staticlogistics.ignore_durability", "Ignore Durability", "忽略耐久");
        add("gui.staticlogistics.filter.full", "Filter is full", "过滤器已满");
        add("gui.staticlogistics.filter.no_tags", "This item has no tags, cannot be added to the filter.", "此物品无标签，无法添加到过滤器");

        add("tag_type.staticlogistics.item", "Item Tag:", "物品标签:");
        add("tag_type.staticlogistics.block", "Block Tag:", "方块标签:");
        add("tag_type.staticlogistics.fluid", "Fluid Tag:", "流体标签:");
        add("gui.staticlogistics.tag.active", "Whitelist", "白名单");
        add("gui.staticlogistics.tag.excluded", "Blacklist", "黑名单");

        add("msg.staticlogistics.export.header", "--- Coordinates for Group #%s ---", "--- 分组 #%s 的坐标列表 ---");
        add("msg.staticlogistics.export.tp_hover", "Click to suggest teleport command", "点击以补全传送指令");
        add("msg.staticlogistics.group_display", "Group Id: %s", "分组 ID：%s");
        add("msg.staticlogistics.target_count", "Targets: %s", "目的地：%s");
        add("msg.staticlogistics.owner_display", "Owner: %s", "所有者：%s");
        add("msg.staticlogistics.node_added", "Node recorded. Total: %s", "节点已记录，当前共计：%s");
        add("msg.staticlogistics.node_removed", "Node unrecorded. Remaining: %s", "节点记录已移除，剩余：%s");
        add("msg.staticlogistics.selection_cleared", "Selection Cleared", "已清空已记录节点");
        add("msg.staticlogistics.batch_linked", "Successfully linked %s nodes!", "成功建立了 %s 条链路！");
        add("msg.staticlogistics.batch_linked_to_group", "Successfully linked %s nodes to Group: %s!", "成功将 %s 条链路连接至分组：%s！");
        add("msg.staticlogistics.no_nodes_stored", "No nodes stored in linker!", "连接器中未存储任何节点！");
        add("msg.staticlogistics.link_failed", "Failed to create links. Check connection rules.", "建立连接失败。请检查连接规则。");
        add("msg.staticlogistics.links_created", "Created %s new link(s)", "建立了 %s 条新链路");
        add("msg.staticlogistics.links_merged", "Merged %s link(s) with %s", "合并了 %s 条链路的 %s 传输");
        add("msg.staticlogistics.out_of_range", "Out of Range", "超出范围");
        add("msg.staticlogistics.mode_switched", "Mode: %s", "当前模式：%s");
        add("msg.staticlogistics.mode_switched_with_nodes", "Mode: %s (%s nodes stored)", "当前模式：%s（已存储 %s 个节点）");
        add("msg.staticlogistics.links_removed", "Successfully removed %s logistics links", "已成功移除 %s 条物流链接");
        add("msg.staticlogistics.no_links_found", "No links found or access denied", "未发现可操作的链路或访问被拒绝");
        add("msg.staticlogistics.links_cleaned_at", "Cleared all authorized links at %s", "已清除位置 %s 处所有你有权管理的链路");
        add("msg.staticlogistics.no_links_on_face", "No removable links found on face: %s", "在 %s 面上未发现属于你或你团队的链路");
        add("msg.staticlogistics.no_permission", "Access Denied: Insufficient permissions.", "访问拒绝：权限不足。");
        add("msg.staticlogistics.no_permission_to_remove", "Cannot remove: You must be the owner or a Team Officer.", "无法移除：你必须是所有者 or 具备团队管理员权限。");
        add("msg.staticlogistics.links_removed_partial", "Cleaned %s links; %s others were skipped (Protected).", "清理了 %s 条链路；另有 %s 条受保护无法移除。");
        add("msg.staticlogistics.no_face_config", "No face configuration found at this location.", "该位置未找到面配置。");
        add("msg.staticlogistics.no_capability", "This block does not have logistics capability.", "该方块不具备物流能力。");
        add("msg.staticlogistics.no_types_selected", "No transfer type selected. Cannot create link.", "§c未选择任何传输类型，无法建立链接");
        add("msg.staticlogistics.self_link_error", "Cannot link a node to itself.", "无法将节点连接到自身。");
        add("msg.staticlogistics.links_removed_smart", "Links removed successfully.", "链接已成功移除。");
        add("msg.staticlogistics.no_dimension_upgrade", "No dimension upgrade installed!", "未安装跨维度升级！");
        add("msg.staticlogistics.unknown_owner", "Unknown", "未知");
        add("msg.staticlogistics.tool_nodes_cleaned", "Removed %s invalid node(s) from configurator", "已从配置器中移除了 %s 个无效节点");
        add("msg.staticlogistics.wrench.sneak_required", "Sneak required to use the wrench.", "使用扳手需要潜行。");
        add("msg.staticlogistics.wrench.not_container", "This block is not a container.", "该方块不是容器。");
        add("msg.staticlogistics.wrench.no_permission", "No permission to remove this machine.", "没有权限移除此机器。");
        add("msg.staticlogistics.wrench.removed", "Successfully removed %s.", "已拆卸 %s。");
        add("msg.staticlogistics.wrench.failed", "Failed to remove block.", "拆卸方块失败。");

        add("mode.staticlogistics.wrench", "Wrench", "扳手");
        add("mode.staticlogistics.wrench.desc",
            "Shift + Right-click a machine to remove it.",
            "潜行+右键快速拆卸机器");
        add("mode.staticlogistics.link_as_input", "Select point as Insert", "选取点为存入端");
        add("mode.staticlogistics.link_as_input.desc",
            "Shift + Right-click a node to store it as an insert target. Resources will be inserted into this node.",
            "潜行+右键点击节点，将其标记为存入目标（资源将传入此节点）。");
        add("mode.staticlogistics.link_as_output", "Select point as Extract", "选取点为提取端");
        add("mode.staticlogistics.link_as_output.desc",
            "Shift + Right-click a node to store it as an extract source. Resources will be extracted from this node.",
            "潜行+右键点击节点，将其标记为提取源（从此节点向外传输资源）。");
        add("mode.staticlogistics.remove", "Remove Links", "移除现有链路");
        add("mode.staticlogistics.remove.desc",
            "Shift + Right-click a node face to delete all links connected to it.",
            "潜行+右键点击节点面，删除该面上现有的所有物流链路。");
        add("mode.staticlogistics.face_config", "Configure Node Face", "配置节点面");
        add("mode.staticlogistics.face_config.desc",
            "Shift + Right-click to open the detailed configuration GUI for the specific node face.",
            "潜行+右键点击节点面，打开该面的详细配置界面（过滤、策略等）。");
        add("mode.staticlogistics.container_config", "Configure Node Container", "配置节点容器");
        add("mode.staticlogistics.container_config.desc",
            "Shift + Right-click to open the detailed configuration GUI for the specific node container.",
            "潜行+右键点击节点容器，打开该容器的详细配置界面（速度、倍率、范围等）。");

        add("tooltip.staticlogistics.mode", "Mode: %s", "工具模式：%s");
        add("tooltip.staticlogistics.type", "Transfer Type: %s", "传输类型：%s");
        add("tooltip.staticlogistics.group", "Group Id: %s", "分组 ID：%s");
        add("tooltip.staticlogistics.none", "None", "无");
        add("tooltip.staticlogistics.stored_nodes", "Stored Nodes: %s (As %s)", "待连接节点：%s（设为 %s）");
        add("tooltip.staticlogistics.clear_stored_hint", "Sneak + Right-click to clear stored nodes", "潜行+右键清除已存储的节点");
        add("tooltip.staticlogistics.auto_clean_disabled", "Auto clean disabled", "自动清理已禁用");
        add("tooltip.staticlogistics.auto_clean_enable_hint", "Enable via config/staticlogistics-server.toml", "可在配置文件中启用");
        add("tooltip.staticlogistics.upgrade.tier_display", "Tier: %s", "等级：%s");
        add("tooltip.staticlogistics.upgrade.value", "Multiplier: %s", "效果倍率：%s");
        add("tooltip.staticlogistics.upgrade.dimension_feature", "Enables interdimensional logistics.", "解锁跨维度物流传输。");
        add("tooltip.staticlogistics.upgrade.install_hint", "Install into nodes to enhance capabilities.", "安装至节点以增强其传输属性。");
        add("tooltip.staticlogistics.upgrade.tag_filter_feature", "Enables filtering of resources based on tags.", "支持基于标签过滤资源。");
        add("tooltip.staticlogistics.upgrade.nbt_filter_feature", "Enables filtering of resources based on NBT data.", "支持基于NBT数据过滤资源。");

        add("commands.staticlogistics.info.header", "--- Logistics Info at %s ---", "--- 位置 %s 的物流信息 ---");
        add("commands.staticlogistics.info.no_links", "No active source links on this block face.", "该方块表面没有活动的源链路。");
        add("commands.staticlogistics.info.line_format", "  [%s] %s | %s | %s", "  [%s] %s | %s | %s");
        add("commands.staticlogistics.transfer.success", "Successfully transferred %s link(s) from %s to %s", "成功将 %s 条链路从玩家 %s 转移给 %s");
        add("commands.staticlogistics.transfer.group_not_found", "No matching groups found for that player.", "未找到该玩家匹配的分组。");
        add("commands.staticlogistics.transfer.group_success", "Successfully transferred Group '%2$s' (%3$s links) from %1$s to %4$s", "已成功将玩家 %1$s 的分组“%2$s”（共 %3$s 条链路）转移给 %4$s");
        add("commands.staticlogistics.rename.not_found", "No matching groups found to rename.", "未找到匹配的分组进行重命名。");
        add("commands.staticlogistics.rename.success", "Group '%s' renamed to '%s' for player %s", "已为玩家 %3$s 将分组“%1$s”重命名为“%2$s”");
        add("commands.staticlogistics.cleanup.success", "Deleted %s link(s) owned by %s", "已清理属于玩家 %2$s 的 %1$s 条链路");
        add("commands.staticlogistics.info.group", "Group: %s", "分组：%s");
        add("commands.staticlogistics.info.owner", "Owner: %s", "所有者：%s");
        add("commands.staticlogistics.info.container", "=== Container Upgrade Info ===", "=== 容器升级信息 ===");
        add("commands.staticlogistics.info.speed", "Speed Multiplier: x%s", "速度倍率：x%s");
        add("commands.staticlogistics.info.range", "Range Multiplier: %s", "范围倍率：%s");
        add("commands.staticlogistics.info.stack", "Stack Multiplier: %s", "堆叠倍率：%s");
        add("commands.staticlogistics.info.dimension", "Cross-Dimension: %s", "跨维度：%s");
        add("commands.staticlogistics.info.upgrades_title", "Upgrades:", "升级插件：");
        add("commands.staticlogistics.info.slot_format", "  Slot %s: %s x%s", "  槽位 %s：%s x%s");
        add("commands.staticlogistics.info.no_container_config", "No container config found.", "未找到容器配置。");
        add("commands.staticlogistics.info.face_configs_title", "=== Face Configs ===", "=== 面配置 ===");
        add("commands.staticlogistics.info.face_direction", "[%s]", "[%s]");
        add("commands.staticlogistics.info.types_mask", "Types mask: %s", "类型掩码：%s");
        add("commands.staticlogistics.info.global_input", "Global Input: %s", "全局输入：%s");
        add("commands.staticlogistics.info.global_output", "Global Output: %s", "全局输出：%s");
        add("commands.staticlogistics.info.input_channel", "Input Channel: %d", "输入频道：%d");
        add("commands.staticlogistics.info.output_channel", "Output Channel: %d", "输出频道：%d");
        add("commands.staticlogistics.info.strategy", "Strategy: %s", "分发策略：%s");
        add("commands.staticlogistics.info.priority", "Priority: %d", "优先级：%d");
        add("commands.staticlogistics.info.linked_nodes", "Linked Nodes: %d", "已连接节点数：%d");
        add("commands.staticlogistics.info.enabled", "Enabled", "启用");
        add("commands.staticlogistics.info.disabled", "Disabled", "禁用");

        add("commands.staticlogistics.list.header", "=== Active Logistics Nodes ===", "=== 当前活跃物流节点 ===");
        add("commands.staticlogistics.list.no_groups", "No active logistics groups found.", "未找到活跃的物流分组。");
        add("commands.staticlogistics.list.group_entry", "Group: %s (%d nodes)", "分组：%s（共 %d 个节点）");
        add("commands.staticlogistics.list.node_entry", "  - %s %s (%s)", "  - %s %s（角色：%s）");
        add("commands.staticlogistics.info.not_found", "No logistics data found at this position.", "此位置未找到物流数据。");
        add("commands.staticlogistics.strategies.header", "--- Data Component Strategies (Page %s/%s) ---", "--- 数据组件匹配策略 (第%s/%s页) ---");
        add("commands.staticlogistics.strategies.line", "%s -> %s", "%s -> %s");
        add("commands.staticlogistics.strategies.next_page", "Use /sl strategies %s for next page.", "输入 /sl strategies %s 查看下一页。");

        add("match_strategy.staticlogistics.exact", "EXACT", "精确");
        add("match_strategy.staticlogistics.contains", "CONTAINS", "包含");
        add("match_strategy.staticlogistics.smart_contains", "SMART_CONTAINS (default)", "智能包含（默认）");
        add("match_strategy.staticlogistics.ignore", "IGNORE", "忽略");

        add("staticlogistics.configuration.general", "General Settings", "基础设置");
        add("staticlogistics.configuration.core", "Core Settings", "核心设置");
        add("staticlogistics.configuration.integration", "Integration Settings", "集成设置");
        add("staticlogistics.configuration.upgrades", "Upgrade Settings", "插件参数");
        add("staticlogistics.configuration.filter", "Filter Settings", "过滤插件参数");

        add("config.staticlogistics.default_radius", "Default Link Radius", "默认连接半径");
        add("config.staticlogistics.default_tick_interval", "Base Tick Interval (Ticks)", "基础传输间隔(Tick)");
        add("config.staticlogistics.max_transfer_limit", "Max Transfer per Tick", "单次传输最大数量");
        add("config.staticlogistics.auto_clean_stored_nodes", "Auto Clean Stored Nodes", "自动清理存储节点");

        add("staticlogistics.configuration.cache", "Cache Settings", "缓存设置");
        add("config.staticlogistics.cache.provider_size", "Provider Cache Size", "提供者缓存大小");
        add("config.staticlogistics.cache.provider_size.tooltip",
            """
                Controls how many logistics nodes are cached.
                Higher values = better performance but more memory usage.
                Default: 1000, Range: 100-10000.""",
            """
                控制缓存的物流节点数量。
                值越大性能越好，但占用更多内存。
                默认：1000，范围：100-10000。""");

        add("config.staticlogistics.cache.load_factor", "Cache Load Factor", "缓存加载因子");
        add("config.staticlogistics.cache.load_factor.tooltip",
            """
                Affects cache performance. 0.75 is recommended.
                Don't change unless you know what you're doing.
                Default: 0.75, Range: 0.1-1.0.""",
            """
                影响缓存性能。推荐使用 0.75。
                除非你知道自己在做什么，否则不要修改。
                默认：0.75，范围：0.1-1.0。""");

        add("config.staticlogistics.cache.target_size", "Target Cache Size", "目标缓存大小");
        add("config.staticlogistics.cache.target_size.tooltip",
            """
                How many target nodes are cached per face.
                Default: 50, Range: 10-200.""",
            """
                每个面缓存的目标节点数量。
                默认：50，范围：10-200。""");

        add("config.staticlogistics.cache.global_target_size", "Global Target Cache Size", "全局目标缓存大小");
        add("config.staticlogistics.cache.global_target_size.tooltip",
            """
                Total cache size for all faces.
                Default: 500, Range: 100-5000.""",
            """
                所有面的总缓存大小。
                默认：500，范围：100-5000。""");

        add("staticlogistics.configuration.network", "Network Settings", "网络设置");
        add("config.staticlogistics.network.max_bulk_entries", "Max Bulk Entries", "最大批量条目数");
        add("config.staticlogistics.network.max_bulk_entries.tooltip",
            """
                Max config entries sent per packet.
                Lower if you have network issues.
                Default: 100, Range: 10-1000.""",
            """
                每个网络包发送的最大配置数。
                如果有网络问题可以调低。
                默认：100，范围：10-1000。""");

        add("staticlogistics.configuration.performance", "Performance Settings", "性能设置");
        add("config.staticlogistics.performance.ticker_batch_size", "Ticker Batch Size", "定时器批处理大小");
        add("config.staticlogistics.performance.ticker_batch_size.tooltip",
            """
                Nodes processed per tick. Lower = less lag, Higher = faster.
                Default: 50, Range: 10-200.""",
            """
                每刻处理的节点数。越小越流畅，越大越快。
                默认：50，范围：10-200。""");

        add("config.staticlogistics.performance.clean_interval", "Clean Interval (Ticks)", "清理间隔(Tick)");
        add("config.staticlogistics.performance.clean_interval.tooltip",
            """
                How often cooldowns are cleaned (in ticks).
                20 ticks = 1 second.
                Default: 200 (10 seconds), Range: 20-1200.""",
            """
                冷却清理间隔（游戏刻）。
                20 tick = 1 秒。
                默认：200（10秒），范围：20-1200。""");

        add("config.staticlogistics.performance.default_cooldown", "Default Cooldown (Ticks)", "默认冷却时间(Tick)");
        add("config.staticlogistics.performance.default_cooldown.tooltip",
            """
                Wait time after failed transfer (in ticks).
                Default: 10 (0.5 seconds), Range: 1-100.""",
            """
                传输失败后的等待时间（游戏刻）。
                默认：10（0.5秒），范围：1-100。""");

        add("config.staticlogistics.performance.batch_clean_threshold", "Batch Clean Threshold", "批量清理阈值");
        add("config.staticlogistics.performance.batch_clean_threshold.tooltip",
            """
                When to start batch cleanup.
                Default: 500, Range: 100-2000.""",
            """
                何时开始批量清理。
                默认：500，范围：100-2000。""");

        add("config.staticlogistics.performance.batch_clean_size", "Batch Clean Size", "批量清理大小");
        add("config.staticlogistics.performance.batch_clean_size.tooltip",
            """
                Entries cleaned per batch.
                Default: 200, Range: 50-1000.""",
            """
                每次清理的条目数。
                默认：200，范围：50-1000。""");

        add("config.staticlogistics.performance.context_pool_size", "Context Pool Size", "上下文池大小");
        add("config.staticlogistics.performance.context_pool_size.tooltip",
            """
                Object pool size for better performance.
                Default: 100, Range: 20-500.""",
            """
                对象池大小，用于提升性能。
                默认：100，范围：20-500。""");

        add("config.staticlogistics.item_stack_size", "Base Item Stack Size", "基础物品堆叠量");
        add("config.staticlogistics.fluid_stack_size", "Base Fluid Amount (mB)", "基础流体传输量(mB)");
        add("config.staticlogistics.energy_stack_size", "Base Energy Amount (FE)", "基础能量传输量(FE)");

        add("config.staticlogistics.mek_chemical_stack_size", "Base Mek-Chemical Amount", "基础 Mek 化学品传输量");
        add("config.staticlogistics.mek_heat_stack_size", "Base Mek Heat Amount", "基础热量传输量");
        add("config.staticlogistics.ars_source_stack_size", "Base Ars Source Amount", "基础魔源传输量");
        add("config.staticlogistics.pneumatic_pressure_stack_size", "Base Pneumatic Pressure Amount", "基础气压传输量");
        add("config.staticlogistics.pneumatic_heat_stack_size", "Base Pneumatic Heat Amount", "基础气动热量传输量");

        add("config.staticlogistics.iron_multiplier", "Iron Tier Multiplier", "铁等级倍率");
        add("config.staticlogistics.gold_multiplier", "Gold Tier Multiplier", "金等级倍率");
        add("config.staticlogistics.diamond_multiplier", "Diamond Tier Multiplier", "钻石等级倍率");
        add("config.staticlogistics.netherite_multiplier", "Netherite Tier Multiplier", "下界合金等级倍率");
        add("config.staticlogistics.nether_star_multiplier", "Nether Star Tier Multiplier", "下界之星等级倍率");

        add("config.staticlogistics.filter.component_strategy_overrides", "Component Strategy Overrides", "数据组件匹配策略覆写");
        add("config.staticlogistics.filter.component_strategy_overrides.tooltip",
            """
                Overrides the partial match strategy for specific data components.
                Default: Empty (Uses built-in strategies)
                Format: "namespace:component_id=STRATEGY"
                Valid Strategies:
                  · EXACT - Values must be identical
                  · CONTAINS - Map/Collection types must contain all elements
                  · SMART_CONTAINS - Smart recursive containment check
                  · IGNORE - Skip this component (e.g., 'minecraft:damage' is IGNORE by default)
                Example: "minecraft:damage=IGNORE" means ignore durability.
                These entries override the built-in defaults.""",
            """
                覆盖特定数据组件的部分匹配策略。
                默认：空（使用内置策略）
                格式："命名空间:组件ID=策略"
                可用的策略：
                  · EXACT - 要求完全相等
                  · CONTAINS - 集合/映射类型要求包含所有元素
                  · SMART_CONTAINS - 智能递归包含判断
                  · IGNORE - 跳过该组件（例如：minecraft:damage 默认跳过）
                例如："minecraft:damage=IGNORE" 表示忽略耐久。
                这些条目会覆盖内置默认值。"""
        );

        for (UpgradeType type : UpgradeType.values()) {
            String key = "tooltip.staticlogistics.upgrade." + type.getName() + "_desc";
            String enDesc = switch (type) {
                case SPEED -> "Decreases the time interval between transfers.";
                case RANGE -> "Increases the maximum distance for wireless links.";
                case STACK -> "Increases the maximum amount of resources moved per tick.";
                case DIMENSION -> "Enables logistics across different dimensions.";
                case BASIC_FILTER -> "Enables basic filtering of resources.";
                case TAG_FILTER -> "Enables filtering of resources based on tags.";
                case NBT_FILTER -> "Enables filtering of resources based on NBT tags.";
            };
            String zhDesc = switch (type) {
                case SPEED -> "缩短传输间隔时间。";
                case RANGE -> "增加链路连接的最大距离。";
                case STACK -> "增加单次传输的数量限制。";
                case DIMENSION -> "无视维度进行传输。";
                case BASIC_FILTER -> "基础过滤器。";
                case TAG_FILTER -> "标签过滤器。";
                case NBT_FILTER -> "NBT过滤器。";
            };
            add(key, enDesc, zhDesc);
            add("upgrade_type.staticlogistics." + type.getName(), toTitleCase(type.getName()) + " Upgrade", zhDesc.replaceAll("[。，]", "").replace("缩短", "").replace("增加", "").replace("无视", ""));
        }

        for (UpgradeTier tier : UpgradeTier.values()) {
            String zh = switch (tier) {
                case IRON -> "铁";
                case GOLD -> "金";
                case DIAMOND -> "钻石";
                case NETHERITE -> "下界合金";
                case NETHER_STAR -> "下界之星";
            };
            add("tier.staticlogistics." + tier.getSerializedName(), toTitleCase(tier.getSerializedName()), zh);
        }

        for (TransferType type : TransferRegistries.getAllActive()) {
            String path = type.id().getPath();
            String enName = toTitleCase(path);
            String cn = switch (path) {
                case "item" -> "物品";
                case "fluid" -> "流体";
                case "energy" -> "能量";
                default -> path;
            };
            String cnDesc = switch (path) {
                case "item" -> "传输物品。";
                case "fluid" -> "传输液体。";
                case "energy" -> "传输能量。";
                default -> "传输" + cn + "。";
            };
            add("transfer_type.staticlogistics." + path, enName, cn);
            add("transfer_type.staticlogistics." + path + ".desc", "Transport " + enName, cnDesc);
        }

        add("transfer_type.staticlogistics.mek_chemicals", "Mek Chemicals", "化学品");
        add("transfer_type.staticlogistics.mek_chemicals.desc", "Transport Mek Chemicals", "传输化学品。");
        add("transfer_type.staticlogistics.mek_heat", "Mek Heat", "热量");
        add("transfer_type.staticlogistics.mek_heat.desc", "Transport Mekanism Heat", "传输热量。");
        add("transfer_type.staticlogistics.ars_source", "Ars Source", "魔源");
        add("transfer_type.staticlogistics.ars_source.desc", "Transport Ars Source", "传输魔源。");
        add("transfer_type.staticlogistics.pnc_pressure", "Pneumatic Pressure", "气压");
        add("transfer_type.staticlogistics.pnc_pressure.desc", "Transport PneumaticCraft pressure", "传输气压。");
        add("transfer_type.staticlogistics.pnc_heat", "Pneumatic Heat", "气动热量");
        add("transfer_type.staticlogistics.pnc_heat.desc", "Transport PneumaticCraft heat", "传输气动热量。");

        for (DistributionStrategy strategy : DistributionStrategy.values()) {
            String zh = switch (strategy) {
                case SEQUENTIAL -> "顺序优先";
                case ROUND_ROBIN -> "轮询分发";
                case NEAREST -> "最近优先";
                case FURTHEST -> "最远优先";
                case RANDOM -> "随机分发";
                case SLOT_ROUND_ROBIN -> "插槽轮询";
            };
            add(strategy.getDescriptionId(), toTitleCase(strategy.getSerializedName()), zh);
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