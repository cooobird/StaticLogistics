package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.content.registry.SLCreativeTabs;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import com.coobird.staticlogistics.transfer.TransferTypeBootstrap;
import com.coobird.staticlogistics.transfer.UpgradeTier;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SLLanguageProvider extends LanguageProvider {
    private final String locale;

    public SLLanguageProvider(PackOutput output, String locale) {
        super(output, StaticLogistics.MODID, locale);
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
        TransferTypeBootstrap.init();

        addCreativeTab(SLCreativeTabs.TAB_STATIC_LOGISTICS, "Static Logistics", "静态物流");

        add("gui.staticlogistics.linker_settings", "Linker Configuration", "连接配置器");
        add("gui.staticlogistics.confirm", "Confirm", "确认");
        add("gui.staticlogistics.filter.rule.normal", "Item", "普通");
        add("gui.staticlogistics.filter.rule.component", "Component", "组件");
        add("gui.staticlogistics.filter.rule.selected_components", "Components: %s selected", "组件：已选 %s 项");
        add("gui.staticlogistics.filter.rule.tag", "Tag", "标签");
        add("gui.staticlogistics.filter.rule.durability", "Remaining durability", "剩余耐久");
        add("gui.staticlogistics.filter.rule.numeric", "Numeric component", "数值组件");
        add("gui.staticlogistics.filter.rule.points", "Points", "数值");
        add("gui.staticlogistics.filter.rule.durability_hint", "Click to edit the remaining durability condition", "点击编辑剩余耐久条件");
        add("gui.staticlogistics.filter.rule.durability_empty", "Enter a value", "请输入数值");
        add("gui.staticlogistics.filter.rule.durability_invalid", "Percent: 0–100", "百分比范围：0–100");
        add("gui.staticlogistics.filter.tag_type.item", "Item", "物品标签");
        add("gui.staticlogistics.filter.tag_type.block", "Block", "方块标签");
        add("gui.staticlogistics.filter.tag_type.fluid", "Fluid", "流体标签");
        add("gui.staticlogistics.filter.rule.match", "Exact", "匹配");
        add("gui.staticlogistics.filter.rule.fuzzy", "Partial", "模糊");
        add("gui.staticlogistics.filter.rule.invert", "Invert", "取反");
        add("gui.staticlogistics.filter.rule.match_hint", "Match mode: exact. Click to change.", "匹配方式：精确匹配，点击切换");
        add("gui.staticlogistics.filter.rule.fuzzy_hint", "Match mode: partial. Click to change.", "匹配方式：模糊匹配，点击切换");
        add("gui.staticlogistics.filter.rule.invert_hint", "Match mode: inverted. Click to change.", "匹配方式：取反匹配，点击切换");
        add("gui.staticlogistics.filter.rule.blacklist_hint", "Toggle allowlist / blocklist", "切换白名单 / 黑名单");
        add("gui.staticlogistics.filter.rule.selector_hint", "Click to choose a type, component or tag", "点击展开列表，选择类型、组件或标签");
        add("gui.staticlogistics.filter.rule.no_values", "No entries", "无可选项");
        add("gui.staticlogistics.filter.rule.add_hint", "Add a condition; all conditions must pass", "添加条件；所有条件必须同时满足");
        add("gui.staticlogistics.filter.rule.remove_hint", "Remove the selected condition", "删除当前条件");
        add("gui.staticlogistics.filter.rule.cancel_hint", "Discard changes", "取消本次修改");
        add("gui.staticlogistics.filter.rule.apply_hint", "Apply changes", "应用修改");
        add("gui.staticlogistics.cancel", "Cancel", "取消");
        add("gui.staticlogistics.confirm_delete", "Confirm Deletion", "确认删除");
        add("gui.staticlogistics.confirm_delete_connection", "Delete connection '%s'?", "删除链接“%s”？");
        add("gui.staticlogistics.blueprint.empty",
            "No matching groups", "没有匹配的分组");
        add("gui.staticlogistics.tooltip.select_hint", "Click to select this group", "点击以选择该分组");
        add("gui.staticlogistics.connection", "Connection", "连接");
        add("gui.staticlogistics.input", "Input", "输入");
        add("gui.staticlogistics.output", "Output", "输出");
        add("gui.staticlogistics.node_configuration", "Node Configuration", "节点配置");
        add("gui.staticlogistics.enabled", "Enabled", "已启用");
        add("gui.staticlogistics.disabled", "Disabled", "已禁用");
        add("gui.staticlogistics.filter", "Filter", "过滤器");
        add("gui.staticlogistics.upgrades", "Upgrades", "升级");
        add("gui.staticlogistics.receive_types", "Accepted Types: %s", "接收类型：%s");
        add("gui.staticlogistics.transfer_types", "Transfer Types: %s", "传输类型：%s");
        add("gui.staticlogistics.no_resource_types", "None selected", "未选择");
        add("gui.staticlogistics.blocks.select_face", "Select a block and its connected face", "选择方块及其连接面进行配置");
        add("gui.staticlogistics.unit.blocks", "blocks", "格");
        add("gui.staticlogistics.node.details", "[%1$s: %2$s  %3$s: %4$s  %5$s: %6$s  Channel %7$s]", "[%1$s：%2$s  %3$s：%4$s  %5$s：%6$s  通道 %7$s]");
        add("gui.staticlogistics.node.compact", "%1$s  %2$s  Channel %3$s", "%1$s  %2$s  通道 %3$s");
        add("gui.staticlogistics.node.face_channel", "%1$s  Channel %2$s", "%1$s  通道 %2$s");
        add("gui.staticlogistics.diagnostic.endpoint_a", "Endpoint A: %s", "端点 A：%s");
        add("gui.staticlogistics.diagnostic.endpoint_b", "Endpoint B: %s", "端点 B：%s");
        add("gui.staticlogistics.diagnostic.direction.forward", "Configured direction: A → B", "配置方向：A → B");
        add("gui.staticlogistics.diagnostic.direction.reverse", "Configured direction: B → A", "配置方向：B → A");
        add("gui.staticlogistics.diagnostic.direction.both", "Configured direction: A ↔ B", "配置方向：A ↔ B");
        add("gui.staticlogistics.diagnostic.direction.inactive", "No enabled transfer direction", "没有启用的传输方向");
        add("gui.staticlogistics.diagnostic.topology_only", "Topology only; live transfer status not checked", "仅显示连接配置，未检查实时传输状态");
        add("gui.staticlogistics.node.resource_context", "%1$s  %2$s", "%1$s  %2$s");
        add("gui.staticlogistics.node.configuration_target", "%1$s  %2$s  %3$s", "%1$s  %2$s  %3$s");
        add("gui.staticlogistics.node.dimension", "Dimension", "维度");
        add("gui.staticlogistics.node.block_position", "Block Position", "方块坐标");
        add("gui.staticlogistics.node.connection_face", "Connection Face", "连接面朝向");
        add("gui.staticlogistics.direction.down", "Down", "下");
        add("gui.staticlogistics.blocks.title", "Blocks", "方块目录");
        add("gui.staticlogistics.blocks.view_hint", "Click the block to configure it", "点击方块进行配置");
        add("gui.staticlogistics.blocks.current", "Current block", "当前方块");
        add("gui.staticlogistics.reference.resources", "Resources", "资源类型");
        add("gui.staticlogistics.reference.edit", "Edit", "修改");
        add("gui.staticlogistics.reference.view", "View", "查看");
        add("gui.staticlogistics.reference.interval", "Interval", "传输间隔");
        add("gui.staticlogistics.reference.disable", "Disable", "关闭");
        add("gui.staticlogistics.reference.enable", "Enable", "启用");
        add("gui.staticlogistics.blocks.column_name", "Name", "名称");
        add("gui.staticlogistics.batch.keep", "Keep", "保留");
        add("gui.staticlogistics.preset.save", "Save", "保存");
        add("gui.staticlogistics.diagnostic.no_record", "No recent transfer record", "暂无近期传输记录");
        add("gui.staticlogistics.diagnostic.loading", "Waiting for connection data", "正在等待连接数据");
        add("gui.staticlogistics.diagnostic.state.disabled", "This channel is disabled", "当前通道已关闭");
        add("gui.staticlogistics.diagnostic.state.no_types", "No extraction resource types selected", "提取端未选择资源类型");
        add("gui.staticlogistics.diagnostic.state.redstone", "This endpoint is paused by redstone", "当前端点被红石条件暂停");
        add("gui.staticlogistics.diagnostic.state.dimension", "Cross-dimensional upgrade required", "需要跨维度升级");
        add("gui.staticlogistics.diagnostic.state.range", "Other endpoint is out of transfer range", "另一端超出传输范围");
        add("gui.staticlogistics.diagnostic.state.unloaded", "Other endpoint is not loaded", "另一端所在区块未加载");
        add("gui.staticlogistics.diagnostic.state.no_candidate", "No extractable resource recently (empty or filtered)", "近期无可提取资源（可能为空或被过滤）");
        add("gui.staticlogistics.diagnostic.state.peer_disabled", "The other endpoint's channel is disabled", "另一端的通道已关闭");
        add("gui.staticlogistics.diagnostic.state.peer_no_types", "The other endpoint has no extraction types selected", "另一端未选择提取资源类型");
        add("gui.staticlogistics.diagnostic.state.peer_redstone", "The other endpoint is paused by redstone", "另一端被红石条件暂停");
        add("gui.staticlogistics.diagnostic.state.peer_dimension", "Extraction at the other endpoint needs a cross-dimensional upgrade", "另一端提取需要跨维度升级");
        add("gui.staticlogistics.diagnostic.state.peer_range", "Extraction at the other endpoint is out of range", "另一端提取超出传输范围");
        add("gui.staticlogistics.diagnostic.state.unknown", "Current state unavailable", "当前状态暂时无法确定");
        add("gui.staticlogistics.monitor.connections", "Connections: %s", "连接数量：%s");
        add("gui.staticlogistics.preset.review_batch_title", "%s faces (selected shown)", "%s 个面（显示所选面）");
        add("gui.staticlogistics.preset.target_title", "Preview target", "预览目标");
        add("gui.staticlogistics.preset.target_hint", "Choose a face to inspect", "选择要查看的面");
        add("gui.staticlogistics.preset.preview", "Preview", "预览");
        add("gui.staticlogistics.preset.preview_unavailable", "The target cannot be inspected or no longer has permission.", "无法查看目标，可能已失去权限。");
        add("gui.staticlogistics.monitor.configured", "Direction: %s configured, %s unavailable", "方向配置：%s 条有方向，%s 条无方向");
        add("gui.staticlogistics.monitor.configured_only", "Configuration only; not live transfer status", "仅反映方向配置，不代表实时传输状态");
        add("gui.staticlogistics.diagnostic.last_success", "Last recorded transfer: succeeded", "最近一条传输记录：成功");
        add("gui.staticlogistics.diagnostic.last_failure", "Last recorded transfer: %s", "最近一条传输记录：%s");
        add("gui.staticlogistics.diagnostic.unknown_failure", "failed (reason unavailable)", "失败（原因未知）");
        add("gui.staticlogistics.preset.apply", "Use", "应用");
        add("gui.staticlogistics.preset.saved", "Configuration preset saved", "配置预设已保存");
        add("gui.staticlogistics.preset.save_hint", "Save this configuration under a new name", "将当前配置保存为新预设");
        add("gui.staticlogistics.preset.duplicate", "A preset with this name already exists", "已存在同名预设");
        add("gui.staticlogistics.preset.changed", "Selection or configuration changed; review the preset again", "选择或配置已改变，请重新预览预设");
        add("gui.staticlogistics.preset.name_title", "Name the preset", "预设名称");
        add("gui.staticlogistics.preset.review_title", "Review preset changes", "预览预设变更");
        add("gui.staticlogistics.preset.choose_fields", "Toggle fields; values show the primary face", "勾选项目；当前值显示主选连接面");
        add("gui.staticlogistics.preset.channel_state", "Channel", "通道状态");
        add("gui.staticlogistics.preset.select_title", "Configuration presets", "配置预设");
        add("gui.staticlogistics.preset.choose", "Choose a preset", "选择一个预设");
        add("gui.staticlogistics.preset.legacy", "Previous preset", "之前的预设");
        add("gui.staticlogistics.preset.apply_hint", "Apply the saved preset to the selected faces", "将预设应用到所选连接面");
        add("gui.staticlogistics.preset.contents_hint", "Filter rules require installed filters; upgrades are not copied", "应用过滤规则需要已安装过滤器；不复制升级");
        add("gui.staticlogistics.preset.filter_missing", "No filter installed", "未安装过滤器");
        add("gui.staticlogistics.preset.filter_entry", "Slot %s: %s (%s; %s)", "槽位 %s：%s（%s；%s）");
        add("gui.staticlogistics.preset.block", "blocklist", "黑名单");
        add("gui.staticlogistics.preset.allow", "allowlist", "白名单");
        add("gui.staticlogistics.preset.error", "Cannot read or apply preset; check that its settings match the selected face", "无法读取或应用预设，请确认所选连接面的配置类型一致");
        add("gui.staticlogistics.batch.mixed", "Mixed", "多个值");
        add("gui.staticlogistics.batch.title", "Batch configuration  %s faces", "批量配置  %s 个连接面");
        add("gui.staticlogistics.batch.pending", "Apply to %s faces", "应用到 %s 个连接面");
        add("gui.staticlogistics.batch.reset", "Discard", "撤销");
        add("gui.staticlogistics.batch.stage", "Stage", "暂存");
        add("gui.staticlogistics.batch.hint", "Only edited fields are applied; choose a face on the left", "只应用填写的字段；左侧选择目标朝向");
        add("gui.staticlogistics.blocks.column_position", "Coordinates", "坐标");
        add("gui.staticlogistics.blocks.column_role", "Role", "输入／输出");
        add("gui.staticlogistics.blocks.column_direction", "Facing", "连接面朝向");
        add("gui.staticlogistics.blocks.back", "Back to blocks", "返回方块列表");
        add("gui.staticlogistics.blocks.connections", "%s connections  %s", "%s面连接的容器  %s个");
        add("gui.staticlogistics.blocks.empty", "No matching blocks", "没有对应方块");
        add("gui.staticlogistics.blocks.unloaded", "Unloaded", "未加载");
        add("gui.staticlogistics.blocks.rotate", "Drag to rotate; click a connected face to configure", "拖动旋转；点击有连接的面进行配置");
        add("gui.staticlogistics.blocks.extract", "Extract", "提取");
        add("gui.staticlogistics.blocks.insert", "Insert", "存入");
        add("gui.staticlogistics.blocks.both", "In / Out", "存入 / 提取");
        add("gui.staticlogistics.blocks.unknown", "Unknown", "未知");
        add("gui.staticlogistics.blocks.face_no_channel", "No channel", "未配置通道");
        add("gui.staticlogistics.blocks.disabled", "Disabled", "关闭");
        add("gui.staticlogistics.resources.rates", "Rates", "速率");
        add("gui.staticlogistics.directory.mine", "My connections", "我的连接");
        add("gui.staticlogistics.directory.title", "Links", "连接");
        add("gui.staticlogistics.directory.drag_count", "Moving %s connection(s)", "移动 %s 条连接");
        add("gui.staticlogistics.directory.all", "All", "全部");
        add("gui.staticlogistics.directory.own", "Mine", "仅自己");
        add("gui.staticlogistics.directory.search", "Search", "搜索");
        add("gui.staticlogistics.directory.scope_hint", "All: connections you can access. Mine: only connections you own.", "全部：可访问的所有连接；仅自己：由你拥有的连接。");
        add("gui.staticlogistics.directory.team", "Team connections", "团队连接");
        add("gui.staticlogistics.directory.root", "Ungrouped connections", "未分组连接");
        add("gui.staticlogistics.directory.new_collection", "New group", "新建分组");
        add("gui.staticlogistics.directory.new_collection_hint", "Group connections by purpose. Ungrouped connections remain at the root.", "按用途整理连接；未分组的连接直接显示在列表中。");
        add("gui.staticlogistics.directory.manage", "Manage directory", "管理");
        add("gui.staticlogistics.directory.create", "Create", "创建");
        add("gui.staticlogistics.directory.rename", "Rename", "重命名");
        add("gui.staticlogistics.compact.face", "Side", "朝向");
        add("gui.staticlogistics.compact.channel", "Channel", "通道");
        add("gui.staticlogistics.compact.face_channels", "Channels", "此面通道");
        add("gui.staticlogistics.compact.channel_local_hint", "Channels belong to this connection face; right-click a channel to manage it.", "通道属于当前连接面；右键通道可管理。");
        add("gui.staticlogistics.compact.channel_empty", "No channels", "无通道");
        add("gui.staticlogistics.compact.channel_empty_hint", "No channels on this face. Click + to add.", "此连接面没有通道，点击 + 新增");
        add("gui.staticlogistics.compact.channel_rename", "Rename channel", "重命名通道");
        add("gui.staticlogistics.compact.channel_delete", "Delete channel", "删除通道");
        add("gui.staticlogistics.compact.channel_add", "Add a paused channel to this face. No channel is created at the other end.", "只在当前连接面新增通道，初始关闭；不会在对端新增通道。");
        add("gui.staticlogistics.compact.channel_limit", "No free channels on this face", "此连接面没有空闲通道");
        add("gui.staticlogistics.compact.batch", "Selected connections", "批量连接");
        add("gui.staticlogistics.compact.mode", "Mode", "模式");
        add("gui.staticlogistics.compact.both", "Both", "双向");
        add("gui.staticlogistics.compact.redstone", "Redstone", "红石控制");
        add("gui.staticlogistics.compact.redstone_ignore", "Ignore redstone", "忽略红石信号");
        add("gui.staticlogistics.compact.redstone_high", "Run with signal", "有信号运行");
        add("gui.staticlogistics.compact.redstone_low", "Run without signal", "无信号运行");
        add("gui.staticlogistics.compact.upgrade", "Upgrade", "共享升级");
        add("gui.staticlogistics.compact.filter_help", "Independent channel filter; right-click an installed filter to configure.", "通道独立过滤；右键已安装的过滤器打开配置。");
        add("gui.staticlogistics.compact.number_invalid", "Invalid or out-of-range value", "数值无效或超出当前范围");
        add("gui.staticlogistics.directory.choose_destination", "Select a destination folder", "请选择目标目录");
        add("gui.staticlogistics.directory.no_destination", "No available destination", "暂无可用目标目录");
        add("gui.staticlogistics.directory.connection_name", "Connection %s", "连接 %s");
        add("gui.staticlogistics.directory.batch_rename", "Batch rename", "批量重命名");
        add("gui.staticlogistics.directory.batch_move", "Batch move", "批量移动");
        add("gui.staticlogistics.directory.batch_delete", "Batch delete", "批量删除");
        add("gui.staticlogistics.directory.batch_manage_limit", "Select up to 256 existing connections.", "请选择仍存在的连接，单次最多操作 256 条。");
        add("gui.staticlogistics.directory.manage_failed", "Operation failed. Check permissions, connection availability and destination.", "操作未完成，请确认连接仍存在、拥有权限且目标目录有效。");
        add("gui.staticlogistics.directory.delete_link", "Delete connection", "删除连接");
        add("gui.staticlogistics.tooltip.group_context", "Right-click to rename, move or delete", "右键：重命名、移动归类或删除");
        add("gui.staticlogistics.directory.move", "Move to", "移入");
        add("gui.staticlogistics.directory.delete_failed", "Deletion failed; directory retained. Check permissions and endpoint availability.", "删除失败，目录已保留。请确认权限与端点可用状态。");
        add("gui.staticlogistics.directory.delete", "Delete directory and all connections", "删除目录及全部连接");

        add("gui.staticlogistics.directory.delete_action", "Delete", "删除");
        add("gui.staticlogistics.directory.confirm_delete_channel", "Delete channel '%s' and its settings? The connection remains.", "删除通道“%s”及其配置？连接会保留。");
        add("gui.staticlogistics.directory.confirm_delete_links", "Delete all selected connections?", "删除全部选定连接？");
        add("gui.staticlogistics.directory.confirm_delete", "Delete this group and all its connections?", "删除此分组及其中的全部连接？");
        add("gui.staticlogistics.direction.up", "Up", "上");
        add("gui.staticlogistics.direction.north", "North", "北");
        add("gui.staticlogistics.direction.south", "South", "南");
        add("gui.staticlogistics.direction.west", "West", "西");
        add("gui.staticlogistics.direction.east", "East", "东");
        add("gui.staticlogistics.label.priority", "Priority", "优先级");
        add("gui.staticlogistics.label.target_priority", "Dst prio", "目标优先级");
        add("gui.staticlogistics.label.keep_stock", "Keep Stock", "存量维持");
        add("gui.staticlogistics.compact.distribution", "Distribute", "分配策略");
        add("gui.staticlogistics.compact.extraction", "Extract", "提取模式");
        add("gui.staticlogistics.request_timeout", "The server did not confirm this operation. Reopen the configurator to synchronize.", "服务器未确认本次操作，请重新打开配置器同步状态。");
        add("gui.staticlogistics.strategy", "Distribution Strategy", "分发策略");
        add("gui.staticlogistics.extraction_mode", "Extraction Mode", "提取模式");
        add("gui.staticlogistics.infinite", "infinite", "无限");
        add("gui.staticlogistics.unit.meters", "m", "米");
        add("gui.staticlogistics.unit.ticks", "t", "t");
        add("gui.staticlogistics.unit.multiplier", "x", "x");
        add("gui.staticlogistics.true", "true", "是");
        add("gui.staticlogistics.false", "false", "否");

        add("gui.staticlogistics.filter.title", "Filter Configuration", "详细过滤配置");
        add("gui.staticlogistics.hand_filter", "Hand Filter", "手持过滤器");
        add("gui.staticlogistics.filter.full", "Filter is full", "过滤器已满");
        add("tooltip.staticlogistics.shift_left_mark", "%s+Left-click: quick mark item to filter", "%s+左键：快速标记物品到过滤器");
        add("tooltip.staticlogistics.filter.damage", "Ignore durability: %s", "忽略耐久：%s");
        add("tooltip.staticlogistics.filter.items", "Marked items (%s):", "已标记物品（%s）：");
        add("tooltip.staticlogistics.filter.fluids", "Marked fluids (%s):", "已标记流体（%s）：");
        add("tooltip.staticlogistics.filter.tags", "Tag conditions (%s):", "标签条件（%s）：");
        add("tooltip.staticlogistics.filter.empty", "No entries marked", "未标记过滤项");
        add("gui.staticlogistics.filter.left_click_item", "Left-click: mark %s", "左键：标记 %s");
        add("gui.staticlogistics.filter.right_click_fluid", "Right-click: mark %s", "右键：标记 %s");

        add("tag_type.staticlogistics.item", "Item Tag:", "物品标签:");
        add("tag_type.staticlogistics.block", "Block Tag:", "方块标签:");
        add("tag_type.staticlogistics.fluid", "Fluid Tag:", "流体标签:");

        add("msg.staticlogistics.node_added", "Node recorded. Total: %s", "节点已记录，当前共计：%s");
        add("msg.staticlogistics.no_capability", "This block has no logistics capability.", "此方块无物流能力。");
        add("msg.staticlogistics.node_removed", "Node unrecorded. Remaining: %s", "节点记录已移除，剩余：%s");
        add("msg.staticlogistics.bulk_nodes_added", "Added %s of %s matching nodes.", "已添加 %s/%s 个匹配节点。");
        add("msg.staticlogistics.bulk_nodes_added_limited", "Added %s of %s matching nodes; the selection boundary was reached.", "已添加 %s/%s 个匹配节点；已到达选取边界。");
        add("msg.staticlogistics.selection_limit", "The configurator can store at most %s nodes.", "配置器最多可保存 %s 个节点。");
        add("msg.staticlogistics.bulk_cancelled", "Connected-block selection was cancelled because the tool state changed.", "工具状态已变化，连通方块选取已取消。");
        add("msg.staticlogistics.bulk_scanning", "Scanning connected blocks: %s matching nodes found.", "正在扫描连通方块：已找到 %s 个匹配节点。");
        add("msg.staticlogistics.bulk_failed", "The bulk logistics operation failed.", "批量物流操作失败。");
        add("msg.staticlogistics.bulk_link_result", "Created %s links; skipped %s entries. Group: %s.", "已创建 %s 条连接，跳过 %s 项；分组：%s。");
        add("msg.staticlogistics.selection_cleared", "Selection Cleared", "已清空已记录节点");
        add("msg.staticlogistics.select_group_to_link", "Select a group before linking.", "已有分组时，请先选择一个分组再建立链接。");
        add("msg.staticlogistics.mode_switched", "Mode: %s", "当前模式：%s");
        add("msg.staticlogistics.mode_switched_with_nodes", "Mode: %s (%s nodes stored)", "当前模式：%s（已存储 %s 个节点）");
        add("msg.staticlogistics.no_links_on_face", "No removable links found on face: %s", "在 %s 面上未发现属于你或你团队的链路");
        add("msg.staticlogistics.no_permission", "Access Denied: Insufficient permissions.", "访问拒绝：权限不足。");
        add("msg.staticlogistics.no_permission_to_remove", "Cannot remove: You must be the owner or a Team Officer.", "无法移除：你必须是所有者 or 具备团队管理员权限。");
        add("msg.staticlogistics.self_link_error", "Cannot link a node to itself.", "无法将节点连接到自身。");
        add("msg.staticlogistics.group_removed_from_face", "Removed group %s from this face.", "已从该面移除分组 %s。");
        add("msg.staticlogistics.group_not_on_face", "Group %s is not linked to this face.", "分组 %s 不属于该节点面。");
        add("msg.staticlogistics.select_group_to_remove", "Select a group first to remove.", "请先选取分组再移除。");
        add("msg.staticlogistics.unknown_owner", "Unknown", "未知");
        add("msg.staticlogistics.tool_nodes_cleaned", "Removed %s invalid node(s) from configurator", "已从配置器中移除了 %s 个无效节点");
        add("msg.staticlogistics.wrench.no_permission", "No permission to remove this machine.", "没有权限移除此机器。");

        add("msg.staticlogistics.blueprint.empty", "No logistics configurations found in this area.", "该区域内未找到物流配置。");
        add("msg.staticlogistics.blueprint.missing_block", "Target block at %s is missing or its chunk is not loaded.", "目标位置 %s 没有方块或区块未加载。");
        add("msg.staticlogistics.blueprint.target_too_far", "Blueprint anchor at %s is too far away. Move closer.", "蓝图锚点 %s 距离过远，请靠近后操作。");
        add("msg.staticlogistics.blueprint.copied", "Copied %s face(s) from anchor %s.", "已从锚点 %2$s 复制了 %1$s 个面。");
        add("msg.staticlogistics.blueprint.pasted", "Pasted %s face(s) at anchor %s.", "已在锚点 %2$s 粘贴了 %1$s 个面。");
        add("msg.staticlogistics.blueprint.paste_failed", "Blueprint paste failed and all changes were rolled back.", "蓝图粘贴失败，所有修改均已回滚。");
        add("tooltip.staticlogistics.blueprint.info", "--- Blueprint ---", "--- 蓝图信息 ---");
        add("tooltip.staticlogistics.blueprint.face_count", " Faces: %s", "  面：%s 个");
        add("tooltip.staticlogistics.blueprint.container", " Container Upgrades: %s", "  容器升级：%s");
        add("tooltip.staticlogistics.blueprint.upgrades", " Upgrades：", "  升级卡：");
        add("gui.staticlogistics.group_label", "Group: %s", "分组：%s");
        add("tooltip.staticlogistics.blueprint.region", " Region: %s ~ %s", "  区域：%s 至 %s");
        add("tooltip.staticlogistics.blueprint.preview", "Preview at %s, rotation: %s°", "预览位置：%s，旋转：%s°");
        add("tooltip.staticlogistics.blueprint.anchor", "Selected point: %s", "已选起点：%s");
        add("tooltip.staticlogistics.blueprint.use", "Right-click: confirm placement; %s+right-click: cancel preview", "右键确认粘贴；%s＋右键取消预览");
        add("tooltip.staticlogistics.blueprint.clear", "%s+Right-click: cancel preview, keep copied blueprint", "%s＋右键取消预览，保留已复制蓝图");
        add("tooltip.staticlogistics.blueprint.scroll", "%s+Scroll: move / %s+Scroll: rotate / %s+Scroll: Y-axis", "%s+滚轮：平移 / %s+滚轮：旋转 / %s+滚轮：升降");
        add("tooltip.staticlogistics.blueprint.undo", "Press %s to undo last paste", "按 %s 撤销上次粘贴");
        add("msg.staticlogistics.blueprint.missing_upgrades", "Missing %s upgrade(s) — check your inventory.", "缺少 %s 张升级卡 — 请检查背包。");
        add("msg.staticlogistics.blueprint.preview_enter", "Preview at %s — right-click to confirm, scroll to adjust.", "预览贴在 %s — 右键确认粘贴，滚轮调整位置。");
        add("msg.staticlogistics.blueprint.select_group", "Select a group first before pasting.", "请先在配置器中选取分组再粘贴。");
        add("msg.staticlogistics.blueprint.cleared", "Blueprint cleared.", "蓝图已清空。");
        add("msg.staticlogistics.blueprint.undone", "Blueprint paste undone. Restored %s face(s).", "蓝图粘贴已撤销。恢复了 %s 个面。");
        add("msg.staticlogistics.blueprint.no_undo", "Nothing to undo.", "没有可撤销的操作。");
        add("msg.staticlogistics.blueprint.undo_failed", "Blueprint undo failed and the pasted state was restored.", "蓝图撤销失败，已恢复撤销前的粘贴状态。");

        add("mode.staticlogistics.wrench", "Wrench", "扳手");
        add("mode.staticlogistics.wrench.desc",
            "%s + Right-click a machine to remove it. With Mekanism Additions installed, plastic blocks can also be dismantled.",
            "%s+右键拆卸机器。安装 Mekanism：拓展 后可拆卸塑料方块。");
        add("mode.staticlogistics.link_as_input", "Insert", "存入");
        add("mode.staticlogistics.link_as_input.desc",
            "Right-click a container face to select an insert target. Resources enter this container.",
            "直接右键容器的面，选择存入端：资源送入此容器。");
        add("mode.staticlogistics.link_as_output", "Extract", "提取");
        add("mode.staticlogistics.link_as_output.desc",
            "Right-click a container face to select an extract source. Resources leave this container.",
            "直接右键容器的面，选择提取端：资源从此容器取出。");
        add("gui.staticlogistics.blueprint.controls_select", "Left click / drag: select; Ctrl or Shift: add", "左键点选／框选；Ctrl 或 Shift 增选");
        add("gui.staticlogistics.blueprint.controls_camera", "Right drag: rotate; middle drag: pan; wheel: zoom", "右键拖动旋转；中键平移；滚轮缩放");
        add("gui.staticlogistics.blueprint.controls_anchor", "Click a selected block to set the anchor (gold outline)", "点击已选方块设为基准（金色描边）");
        add("gui.staticlogistics.blueprint.controls_copy", "Ctrl+A: select all; Ctrl+C or Enter: copy", "Ctrl+A 全选；Ctrl+C 或回车复制");
        add("gui.staticlogistics.blueprint.controls_links", "Blue faces: input; orange faces: output; split lines: bidirectional", "蓝色面为存入，橙色面为提取；双向连接分线显示");
        add("gui.staticlogistics.blueprint.choose_group", "Select group", "选择分组");
        add("gui.staticlogistics.blueprint.create", "Create blueprint", "创建蓝图");
        add("gui.staticlogistics.blueprint.select_all", "Select all", "全选");
        add("gui.staticlogistics.blueprint.reset_view", "Reset view", "重置视角");
        add("gui.staticlogistics.blueprint.no_loaded_blocks", "No loaded blocks in this dimension", "此组在当前维度没有已加载的方块");
        add("gui.staticlogistics.blueprint.copy_failed", "Copy failed: check loaded blocks and access", "复制未完成，请确认方块已加载且有访问权限");
        add("gui.staticlogistics.blueprint.selection_limit", "Selection exceeds blueprint capacity", "所选方块超出蓝图容量");
        add("gui.staticlogistics.blueprint.mixed_groups", "This directory contains multiple groups; select a connection to choose one", "该目录包含多个分组，请先选择一条连接确定分组");
        add("mode.staticlogistics.blueprint", "Blueprint", "蓝图");
        add("mode.staticlogistics.blueprint.desc", "Copy, preview and paste logistics layouts", "复制、预览和粘贴物流布局");
        add("mode.staticlogistics.remove", "Remove", "移除");
        add("mode.staticlogistics.remove.desc",
            "Right-click a node face to delete all links connected to it.",
            "直接右键节点面，删除该面上现有的所有物流链路。");

        add("key.categories.staticlogistics", "Static Logistics", "静态物流");
        add("key.staticlogistics.blueprint_preview_move", "Blueprint Preview Move", "蓝图预览移动");
        add("key.staticlogistics.blueprint_preview_rotate", "Blueprint Preview Rotate", "蓝图预览旋转");
        add("key.staticlogistics.blueprint_preview_move_y", "Blueprint Preview Move Y", "蓝图预览升降");
        add("key.staticlogistics.priority_x10", "Adjust Value ×10", "数值调整 ×10");
        add("key.staticlogistics.priority_x5", "Adjust Value ×5", "数值调整 ×5");
        add("gui.staticlogistics.compact.number_steps", "Click: ±1; %s: ±10; %s: ±5; both: ±64", "点击 ±1；%s ±10；%s ±5；同时按住 ±64");
        add("gui.staticlogistics.compact.number_steps_up", "Click: +1; %s: +10; %s: +5; both: +64", "点击 +1；%s +10；%s +5；同时按住 +64");
        add("gui.staticlogistics.compact.number_steps_down", "Click: -1; %s: -10; %s: -5; both: -64", "点击 -1；%s -10；%s -5；同时按住 -64");
        add("gui.staticlogistics.unit.items", "items", "个");
        add("gui.staticlogistics.unit.fluid", "mB", "mB");
        add("gui.staticlogistics.unit.energy", "FE", "FE");
        add("gui.staticlogistics.unit.heat", "J", "J");
        add("gui.staticlogistics.unit.source", "Source", "Source");
        add("gui.staticlogistics.unit.mana", "mana", "mana");
        add("gui.staticlogistics.unit.eu", "EU", "EU");
        add("gui.staticlogistics.configuration_rejected", "Configuration was not applied: the target, permission or request limit changed.", "配置未生效：目标、权限或请求频率限制发生变化。");
        add("msg.staticlogistics.blueprint.choose_target", "Right-click a target block to position the blueprint; sneak-right-click to cancel", "右键目标方块设置蓝图位置；蹲下右键取消");
        add("gui.staticlogistics.tabs.types", "Extraction types", "提取类型");
        add("key.staticlogistics.blueprint_paste", "Place copied blueprint", "放置已复制的蓝图");
        add("tooltip.staticlogistics.mode_scroll", "%s + scroll: cycle wrench, extract, insert and remove; blueprint preview controls take priority", "%s＋滚轮切换扳手、提取、存入、移除；有蓝图预览时优先操作预览");
        add("gui.staticlogistics.blueprint.controls_paste", "Ctrl+V: place the copied blueprint again", "Ctrl+V：再次放置已复制的蓝图");
        add("gui.staticlogistics.blueprint.placing", "Blueprint preview: right-click to paste; sneak-right-click to cancel", "蓝图预览：右键粘贴，蹲下右键取消");
        add("gui.staticlogistics.resources.values_title", "Resource Transfer Settings", "资源传输数值");
        add("gui.staticlogistics.resources.connection_rates", "Connection Rates", "连接速率");
        add("gui.staticlogistics.resources.no_observed_types", "No transfers observed yet", "尚未观察到传输");
        add("gui.staticlogistics.resources.total", "Total", "总速率");
        add("gui.staticlogistics.resources.partial_total", "Partial total", "部分总计");
        add("gui.staticlogistics.resources.unloaded", "Unavailable", "不可用");
        add("gui.staticlogistics.resources.connection_rate_hint", "Rates cover connections in the selected group. Unavailable endpoints are excluded; zero does not imply a fault.", "速率仅统计当前分组的连接；不可用端点不计入总计，零速率不代表故障。");
        add("gui.staticlogistics.resources.partial_limit_hint", "Only the first 256 endpoints are tracked. Unavailable endpoints are excluded.", "仅追踪前 256 个端点，不可用端点不计入总计。");
        add("gui.staticlogistics.resources.sent_rate", "Sent / sec", "实时发送");
        add("gui.staticlogistics.resources.received_rate", "Received / sec", "实时接收");
        add("gui.staticlogistics.resources.rate_hint", "Total for all connections on the selected face over the last second. Sampling starts when opened and refreshes every 5 ticks.", "统计所选连接面所有连接最近一秒的实际传输总量。打开后开始采样，每 5 tick 刷新。");
        add("gui.staticlogistics.resources.rate_reference", "Rates belong to this reference node, not the batch total.", "速率仅对应当前参照节点，不是批量选中连接的总和。");
        add("gui.staticlogistics.resources.type_column", "Type", "类型");
        add("gui.staticlogistics.resources.limit_column", "Per-operation limit", "单次上限");
        add("gui.staticlogistics.resources.limit_hint", "Per-operation limit: %1$s  Interval: %2$s", "单次上限：%1$s  间隔：%2$s");
        add("gui.staticlogistics.resources.interval_column", "Interval", "间隔");
        add("gui.staticlogistics.resources.source_limit", "Transfer limit", "传输上限");
        add("gui.staticlogistics.resources.source_decides", "Source-defined", "由提取端决定");
        add("gui.staticlogistics.resources.source_hint", "Select the source endpoint to view its transfer limit and interval.", "选择对应提取端，可查看其传输上限与间隔。");
        add("gui.staticlogistics.resources.title", "Resource Types", "资源类型");
        add("gui.staticlogistics.resources.edit", "Resource types: edit", "资源类型：修改");
        add("gui.staticlogistics.resources.view", "Accepted types: view", "可接收类型：查看");
        add("gui.staticlogistics.resources.apply", "Apply", "应用");
        add("gui.staticlogistics.resources.multi_hint", "Select one or more types", "可同时选择多种资源");
        add("gui.staticlogistics.resources.batch_hint", "Reference face shown; applying sets all selected faces", "显示当前连接面，应用后统一配置");
        add("gui.staticlogistics.resources.receivable", "Face types", "面能力");
        add("gui.staticlogistics.permissions.player_not_found", "Player not found: %s", "未找到玩家：%s");
        add("gui.staticlogistics.permissions.title", "Access management", "授权管理");
        add("gui.staticlogistics.permissions.entry", "Access", "授权管理");
        add("gui.staticlogistics.permissions.already_added", "This player already has an access entry.", "该玩家已在授权列表中。");
        add("gui.staticlogistics.permissions.short", "P", "权");
        add("gui.staticlogistics.permissions.player_hint", "Player name", "玩家名称");
        add("gui.staticlogistics.permissions.add", "Add", "添加");
        add("gui.staticlogistics.permissions.manage", "Can manage", "可管理");
        add("gui.staticlogistics.permissions.view", "View only", "仅查看");
        add("gui.staticlogistics.permissions.read_only", "Read only", "只读");
        add("gui.staticlogistics.permissions.none", "Remove grant", "移除手动授权");
        add("gui.staticlogistics.permissions.no_access", "No access", "无权限");
        add("gui.staticlogistics.permissions.manual_grants", "Manual grants", "以下为手动授权");
        add("gui.staticlogistics.permissions.team_hint", "FTB team access may still apply", "FTB团队权限可能仍生效");
        add("gui.staticlogistics.permissions.team_on", "FTB access: on", "FTB团队访问：开");
        add("gui.staticlogistics.permissions.team_off", "FTB access: off", "FTB团队访问：关");
        add("gui.staticlogistics.permissions.team_source", "%s: FTB team permissions also apply", "%s：同时具有FTB团队权限");
        add("gui.staticlogistics.preset.delete_title", "Delete preset", "删除预设");
        add("gui.staticlogistics.preset.confirm_delete", "Delete preset %s? This cannot be undone.", "确定删除预设 %s？此操作无法撤销。");
        add("gui.staticlogistics.preset.deleted", "Preset deleted", "预设已删除");
        add("gui.staticlogistics.resources.batch_context", "Editing %s connection faces", "正在编辑 %s 个连接面");
        add("gui.staticlogistics.resources.derived", "Detected from the container and input filter", "根据容器能力与输入过滤器计算，不直接修改");
        add("key.staticlogistics.clear_stored_nodes", "Clear Stored Nodes", "清除已存储节点");
        add("key.staticlogistics.blueprint_undo", "Undo Blueprint Paste", "撤销蓝图粘贴");
        add("key.staticlogistics.quick_filter_mark", "Quickly Add Filter Entry", "快速添加过滤项");
        add("key.staticlogistics.group_details_and_export", "Show Group Details / Export Coordinates", "显示分组详情 / 导出坐标");
        add("key.staticlogistics.bulk_node_selection", "Select Connected Blocks", "选取相连同类方块");
        add("tooltip.staticlogistics.bulk_select_hint", "Hold %s + right-click: select connected blocks of the same type", "按住 %s 右键，批量选取相连同类方块");
        add("tooltip.staticlogistics.stored_faces", "%s connection faces selected", "已选 %s 个连接面");

        add("msg.staticlogistics.blueprint.copy_first", "Copy a blueprint in the blueprint page first.", "请先在蓝图页复制蓝图。");
        add("msg.staticlogistics.wrench.cannot_rotate", "This block cannot be rotated.", "此方块不支持旋转。");
        add("gui.staticlogistics.hud.blueprint_open", "Open blueprint", "打开蓝图");
        add("gui.staticlogistics.hud.position_block", "Choose placement", "选择放置位置");
        add("gui.staticlogistics.hud.rotate_block", "Rotate block", "旋转方块");
        add("gui.staticlogistics.hud.dismantle", "Dismantle block", "拆卸方块");
        add("gui.staticlogistics.hud.remove", "Remove connections", "移除连接");
        add("gui.staticlogistics.hud.select_extract", "Block face: mark as source", "方块面：设为提取端");
        add("gui.staticlogistics.hud.select_insert", "Block face: mark as destination", "方块面：设为存入端");
        add("gui.staticlogistics.hud.connect_extract", "Block face: connect source", "方块面：连接提取端");
        add("gui.staticlogistics.hud.connect_insert", "Block face: connect destination", "方块面：连接存入端");
        add("gui.staticlogistics.hud.bulk", "Select faces in bulk", "批量选择连接面");
        add("gui.staticlogistics.hud.clear", "Clear selected faces", "清空已选连接面");
        add("gui.staticlogistics.hud.switch", "Switch mode", "切换模式");
        add("gui.staticlogistics.hud.paste", "Place blueprint", "放置蓝图");
        add("gui.staticlogistics.hud.undo", "Undo", "撤销");
        add("gui.staticlogistics.hud.scroll", "Scroll", "滚轮");
        add("gui.staticlogistics.hud.mouse_left", "LMB", "左键");
        add("gui.staticlogistics.hud.mouse_right", "RMB", "右键");
        add("gui.staticlogistics.hud.mouse_middle", "MMB", "中键");
        add("gui.staticlogistics.hud.channel", "Channel %s", "通道 %s");
        add("gui.staticlogistics.hud.target", "Blueprint  choose target", "蓝图  等待选址");
        add("gui.staticlogistics.hud.preview", "Blueprint  placement preview", "蓝图  放置预览");
        add("gui.staticlogistics.hud.position", "Choose target", "选址");
        add("gui.staticlogistics.hud.confirm", "Paste", "粘贴");
        add("gui.staticlogistics.hud.move", "Move", "平移");
        add("gui.staticlogistics.hud.rotate", "Rotate", "旋转");
        add("gui.staticlogistics.hud.height", "Raise / lower", "升降");
        add("gui.staticlogistics.hud.open", "Open settings", "打开配置");
        add("gui.staticlogistics.hud.sources", "Selected sources: %s", "已选来源：%s");
        add("gui.staticlogistics.hud.targets", "Selected targets: %s", "已选目标：%s");
        add("gui.staticlogistics.hud.selection", "Selected faces: %s", "已选连接面：%s");

        add("jade.staticlogistics.input", "[Input]", "[输入]");
        add("jade.staticlogistics.output", "[Output]", "[输出]");
        add("jade.staticlogistics.both", "[Both]", "[双向]");
        add("jade.staticlogistics.linked", " | %s nodes", " | %s节点");
        add("jade.staticlogistics.transfer_stats", "  Sent:%s Rcv:%s", "  发送:%s 接收:%s");
        add("jade.staticlogistics.face_stats", "Rate: %s/min | Last: %s ago", "速率: %s/分钟 | 上次: %s前");
        add("jade.staticlogistics.section_input", "Input:", "输入:");
        add("jade.staticlogistics.section_output", "Output:", "输出:");
        add("jade.staticlogistics.priority", "Priority: %s", "优先级: %s");
        add("jade.staticlogistics.keep_stock", "Keep >= %s", "存量维持 >= %s");
        add("jade.staticlogistics.strategy_label", "Distribution: %s", "分发策略: %s");
        add("jade.staticlogistics.extraction_label", "Extraction: %s", "提取策略: %s");
        add("jade.staticlogistics.owner", "Owner: %s", "所有者: %s");
        add("config.jade.plugin_staticlogistics.jade", "Static Logistics", "静态物流");

        add("tooltip.staticlogistics.mode", "Mode: %s", "工具模式：%s");
        add("tooltip.staticlogistics.type", "Transfer Type: %s", "传输类型：%s");
        add("tooltip.staticlogistics.group", "Group Id: %s", "分组 ID：%s");
        add("tooltip.staticlogistics.none", "None", "无");
        add("msg.staticlogistics.mode_pending", "Waiting for mode confirmation; try again shortly", "正在确认模式，请稍后再操作");
        add("tooltip.staticlogistics.configurator_hint", "Right-click without targeting a block to open settings; use side tabs to select tools", "未指向方块时右键打开配置界面，使用左侧页签选择操作");
        add("tooltip.staticlogistics.direct_use_hint", "Right-click a block to use the tool; right-click without targeting a block to open settings", "右键方块使用工具；未指向方块时右键打开配置界面");
        add("tooltip.staticlogistics.clear_stored_hint", "Press %s to clear stored nodes", "按 %s 清除已存储节点");
        add("tooltip.staticlogistics.upgrade.tier_display", "Tier: %s", "等级：%s");
        add("tooltip.staticlogistics.upgrade.value", "Multiplier: %s", "效果倍率：%s");
        add("tooltip.staticlogistics.upgrade.dimension_feature", "Enables interdimensional logistics.", "解锁跨维度物流传输。");
        add("tooltip.staticlogistics.upgrade.install_hint", "Install into nodes to enhance capabilities.", "安装至节点以增强其传输属性。");

        add("commands.staticlogistics.info.no_links", "No active source links on this block face.", "该方块表面没有活动的源链路。");
        add("commands.staticlogistics.transfer.success", "Successfully transferred %s link(s) from %s to %s", "成功将 %s 条链路从玩家 %s 转移给 %s");
        add("commands.staticlogistics.transfer.group_success", "Successfully transferred %s link(s) in group '%s' to %s", "已成功将分组“%2$s”的 %1$s 条链路转移给 %3$s");
        add("commands.staticlogistics.rename.success", "Group '%s' renamed to '%s' for player %s", "已为玩家 %3$s 将分组“%1$s”重命名为“%2$s”");
        add("commands.staticlogistics.rename.failed", "Could not rename group '%s' for player %s", "无法为玩家 %2$s 重命名分组“%1$s”");
        add("commands.staticlogistics.cleanup.success", "Deleted %s link(s) owned by %s", "已清理属于玩家 %2$s 的 %1$s 条链路");
        add("commands.staticlogistics.info.owner", "Owner: %s", "所有者：%s");
        add("commands.staticlogistics.info.container", "=== Container Upgrade Info ===", "=== 容器升级信息 ===");
        add("commands.staticlogistics.info.speed", "Speed Multiplier: x%s", "速度倍率：x%s");
        add("commands.staticlogistics.info.range", "Range Multiplier: %s", "范围倍率：%s");
        add("commands.staticlogistics.info.stack", "Stack Multiplier: %s", "堆叠倍率：%s");
        add("commands.staticlogistics.info.dimension", "X-Dim: %s", "跨维度：%s");
        add("commands.staticlogistics.info.upgrades_title", "Upgrades:", "升级插件：");
        add("commands.staticlogistics.info.slot_format", "  Slot %s: %s x%s", "  槽位 %s：%s x%s");
        add("commands.staticlogistics.info.no_container_config", "No container config found.", "未找到容器配置。");
        add("commands.staticlogistics.info.face_configs_title", "=== Face Configs ===", "=== 面配置 ===");
        add("commands.staticlogistics.info.face_direction", "[%s]", "[%s]");
        add("commands.staticlogistics.info.global_input", "Global Input: %s", "全局输入：%s");
        add("commands.staticlogistics.info.global_output", "Global Output: %s", "全局输出：%s");
        add("commands.staticlogistics.info.strategy", "Strategy: %s", "分发策略：%s");
        add("commands.staticlogistics.info.priority", "Priority: %d", "优先级：%d");
        add("commands.staticlogistics.info.role_version", "Role: %s, Version: %s", "角色：%s，版本：%s");
        add("commands.staticlogistics.info.selected_types", "Selected Types: %s", "已选传输类型：%s");
        add("commands.staticlogistics.info.present_capabilities", "Present Capabilities: %s", "当前可用能力：%s");
        add("commands.staticlogistics.info.linked_nodes_detail", "Linked Nodes: %s", "已连接节点：%s");
        add("commands.staticlogistics.info.linked_nodes", "Linked Nodes: %d", "已连接节点数：%d");
        add("commands.staticlogistics.info.enabled", "Enabled", "启用");
        add("commands.staticlogistics.info.disabled", "Disabled", "禁用");

        add("commands.staticlogistics.list.no_groups", "No active logistics groups found.", "未找到活跃的物流分组。");
        add("commands.staticlogistics.list.groups_header", "=== Logistics Groups (%d/%d) ===", "=== 物流分组（第 %d/%d 页）===");
        add("commands.staticlogistics.list.group_header", "=== Group %s (%d/%d) ===", "=== 分组 %s（第 %d/%d 页）===");
        add("commands.staticlogistics.list.group_entry", "Group: %s | Owner: %s | %d nodes", "分组：%s｜所有者：%s｜%d 个节点");
        add("commands.staticlogistics.list.node_entry", "  - %s | %s | %s | %s", "  - %s｜%s｜%s｜%s");
        add("commands.staticlogistics.list.groups_help", "Details: /sl list group <group> [page]", "查看详情：/sl list group <分组名> [页码]");
        add("commands.staticlogistics.list.group_not_found", "Logistics group '%s' was not found.", "未找到物流分组“%s”。");
        add("commands.staticlogistics.list.group_ambiguous", "Multiple owners have a group named '%s'. Rename one before querying details.", "多个所有者均有名为“%s”的分组，请先重命名其中一个再查询详情。");
        add("commands.staticlogistics.list.invalid_page", "Page %d does not exist. Last page: %d.", "第 %d 页不存在，最后一页为第 %d 页。");


        add("commands.staticlogistics.stats.header", "═════ StaticLogistics Stats ═════", "═════ StaticLogistics 传输统计 ═════");
        add("commands.staticlogistics.stats.total", "Total Transfers: %s", "总传输次数：%s");
        add("commands.staticlogistics.stats.amount", "Total Amount: %s", "总传输量：%s");
        add("commands.staticlogistics.stats.failed", "Failed: %s", "失败次数：%s");
        add("commands.staticlogistics.stats.log_size", "Log Entries: %s/200", "日志条目：%s/200");
        add("commands.staticlogistics.stats.by_type", "── By Type ──", "── 按类型 ──");
        add("commands.staticlogistics.stats.type_line", "  %s: %s times, %s total", "  %s：%s次，%s总量");
        add("commands.staticlogistics.stats.sub_help", "Sub: /sl stats recent | top | reset", "子命令：/sl stats recent | top | reset");

        add("commands.staticlogistics.stats.recent_header", "── Recent %s Transfers ──", "── 最近 %s 条传输 ──");
        add("commands.staticlogistics.stats.recent_line", "[%s] %s(%s) → %s(%s) %sx%s %s", "[%s] %s(%s) → %s(%s) %sx%s %s");

        add("commands.staticlogistics.stats.top_send", "── Top Senders ──", "── Top发送节点 ──");
        add("commands.staticlogistics.stats.top_recv", "── Top Receivers ──", "── Top接收节点 ──");
        add("commands.staticlogistics.stats.top_line", "  #%s [%s %s] sent %s / %s total", "  #%s [%s %s] 发送%s次 / %s总量");
        add("commands.staticlogistics.stats.top_recv_line", "  #%s [%s %s] received %s / %s total", "  #%s [%s %s] 接收%s次 / %s总量");

        add("commands.staticlogistics.stats.reset", "Stats reset.", "传输统计已重置");

        add("staticlogistics.configuration.general", "General Settings", "基础设置");
        add("staticlogistics.configuration.performance", "Performance Settings", "性能设置");
        add("staticlogistics.configuration.upgrades", "Upgrade Settings", "插件参数");
        add("staticlogistics.configuration.title", "Static Logistics Configuration", "静态物流配置");
        add("staticlogistics.configuration.section.staticlogistics.toml", "Static Logistics", "静态物流");
        add("staticlogistics.configuration.section.staticlogistics.toml.title", "Static Logistics", "静态物流");
        add("staticlogistics.configuration.general.button", "General Settings", "基础设置");
        add("staticlogistics.configuration.general.tooltip", "Configure general link and transfer defaults.", "配置链接与传输的基础默认值。");
        add("staticlogistics.configuration.performance.button", "Performance Settings", "性能设置");
        add("staticlogistics.configuration.performance.tooltip", "Configure batching, caching, and cleanup behavior.", "配置批处理、缓存与清理行为。");
        add("staticlogistics.configuration.upgrades.button", "Upgrade Settings", "升级设置");
        add("staticlogistics.configuration.upgrades.tooltip", "Configure transfer amounts and upgrade multipliers.", "配置传输量与升级倍率。");

        add("config.staticlogistics.default_radius", "Default Link Radius", "默认连接半径");
        add("config.staticlogistics.gameplay_mode", "Gameplay Mode", "玩法模式");
        add("config.staticlogistics.gameplay_mode.tooltip",
            "SIMPLE uses a 1-tick interval, maximum long transfer allowance and unlimited distance. Both modes require a nether-star combined upgrade for cross-dimensional transfer. Actual transfer is bounded by container APIs and the shared work budget. ADVANCED uses configured values and upgrades.",
            "SIMPLE（简易）间隔为 1 Tick，传输额度为 long 最大值，无视距离。两种模式跨维度均需下界之星综合升级卡。实际传输受容器接口与共享工作预算约束；ADVANCED（进阶）使用配置值和升级。");
        add("config.staticlogistics.default_radius.tooltip", "Maximum default link distance before combined upgrades are applied.", "应用综合升级前的默认最大连接距离。");
        add("config.staticlogistics.default_tick_interval", "Base Tick Interval (Ticks)", "基础传输间隔(Tick)");
        add("config.staticlogistics.default_tick_interval.tooltip", "Base number of ticks between transfer attempts.", "两次传输尝试之间的基础 Tick 数。");
        add("config.staticlogistics.auto_clean_stored_nodes", "Auto Clean Stored Nodes", "自动清理存储节点");
        add("config.staticlogistics.auto_clean_stored_nodes.tooltip", "If true, stored node references will be automatically cleaned after batch linking or when a node is removed.", "开启后，批量链接完成或节点被移除时，配置器中存储的节点引用将自动清理。");

        add("config.staticlogistics.cache.provider_size", "Expected Provider Count", "预期提供者数量");
        add("config.staticlogistics.cache.provider_size.tooltip",
            """
                Initial sizing hint for the active provider index.
                This value does not limit the number of active nodes.
                Default: 1000, Range: 100-10000.""",
            """
                活跃提供者索引的初始容量提示。
                此值不会限制实际活跃节点数量。
                默认：1000，范围：100-10000。""");

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

        add("config.staticlogistics.performance.ticker_batch_size", "Ticker Scan Budget", "定时器扫描预算");
        add("config.staticlogistics.performance.ticker_batch_size.tooltip",
            """
                Base node/type candidates shared across all dimensions per server tick. Lower = less lag, Higher = faster.
                Default: 50, Range: 10-200.""",
            """
                所有维度在每个服务器 Tick 中共享的节点/类型候选基数。越小越流畅，越大响应越快。
                默认：50，范围：10-200。""");

        add("config.staticlogistics.performance.ticker_time_budget_us",
            "Server Tick Time Budget (μs)", "服务器每刻时间预算（微秒）");
        add("config.staticlogistics.performance.ticker_time_budget_us.tooltip",
            """
                Shared soft time limit for logistics scheduling in each server tick.
                Default: 1500, Range: 100-10000.""",
            """
                所有维度在每个服务器 Tick 中共享的物流调度软时间上限。
                默认：1500，范围：100-10000。""");

        add("config.staticlogistics.performance.default_cooldown", "Default Cooldown (Ticks)", "默认冷却时间(Tick)");
        add("config.staticlogistics.performance.default_cooldown.tooltip",
            """
                Wait time after failed transfer (in ticks).
                Default: 10 (0.5 seconds), Range: 1-100.""",
            """
                传输失败后的等待时间（游戏刻）。
                默认：10（0.5秒），范围：1-100。""");


        add("config.staticlogistics.item_stack_size", "Base Item Stack Size", "基础物品堆叠量");
        add("config.staticlogistics.item_stack_size.tooltip", "Base number of items moved by one transfer.", "单次传输移动的基础物品数量。");
        add("config.staticlogistics.fluid_stack_size", "Base Fluid Amount (mB)", "基础流体传输量(mB)");
        add("config.staticlogistics.fluid_stack_size.tooltip", "Base fluid amount moved by one transfer, in mB.", "单次传输移动的基础流体量，单位为 mB。");
        add("config.staticlogistics.energy_stack_size", "Base Energy Amount (FE)", "基础能量传输量(FE)");
        add("config.staticlogistics.energy_stack_size.tooltip", "Base energy amount moved by one transfer, in FE.", "单次传输移动的基础能量，单位为 FE。");

        add("config.staticlogistics.mek_chemical_stack_size", "Base Mek-Chemical Amount", "基础 Mek 化学品传输量");
        add("config.staticlogistics.mek_chemical_stack_size.tooltip", "Base Mekanism chemical amount moved by one transfer.", "单次传输移动的基础 Mekanism 化学品数量。");
        add("config.staticlogistics.mek_heat_stack_size", "Base Mek Heat Amount", "基础热量传输量");
        add("config.staticlogistics.mek_heat_stack_size.tooltip", "Base Mekanism heat amount moved by one transfer.", "单次传输移动的基础 Mekanism 热量。");
        add("config.staticlogistics.ars_source_stack_size", "Base Ars Source Amount", "基础魔源传输量");
        add("config.staticlogistics.ars_source_stack_size.tooltip", "Base Ars Nouveau source amount moved by one transfer.", "单次传输移动的基础新生魔艺魔源数量。");
        add("config.staticlogistics.botania_mana_stack_size", "Base Mana Amount", "基础魔力传输量");
        add("config.staticlogistics.botania_mana_stack_size.tooltip", "Base Botania mana amount moved by one transfer.", "单次传输移动的基础植物魔法魔力数量。");

        add("config.staticlogistics.iron_multiplier", "Iron Tier Multiplier", "铁等级倍率");
        add("config.staticlogistics.iron_multiplier.tooltip", "Transfer multiplier provided by an iron-tier upgrade.", "铁等级升级提供的传输倍率。");
        add("config.staticlogistics.gold_multiplier", "Gold Tier Multiplier", "金等级倍率");
        add("config.staticlogistics.gold_multiplier.tooltip", "Transfer multiplier provided by a gold-tier upgrade.", "金等级升级提供的传输倍率。");
        add("config.staticlogistics.diamond_multiplier", "Diamond Tier Multiplier", "钻石等级倍率");
        add("config.staticlogistics.diamond_multiplier.tooltip", "Transfer multiplier provided by a diamond-tier upgrade.", "钻石等级升级提供的传输倍率。");
        add("config.staticlogistics.netherite_multiplier", "Netherite Tier Multiplier", "下界合金等级倍率");
        add("config.staticlogistics.netherite_multiplier.tooltip", "Transfer multiplier provided by a netherite-tier upgrade.", "下界合金等级升级提供的传输倍率。");
        add("config.staticlogistics.nether_star_multiplier", "Nether Star Tier Multiplier", "下界之星等级倍率");
        add("config.staticlogistics.nether_star_multiplier.tooltip", "Transfer multiplier provided by a nether-star-tier upgrade.", "下界之星等级升级提供的传输倍率。");

        add("tooltip.staticlogistics.upgrade.combined_desc", "Improves speed, range and transfer amount for the entire container.", "同时提升整个容器的速度、范围和单次传输量。");
        add("tooltip.staticlogistics.upgrade.filter_desc", "Resource filter.", "过滤器。");

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

        add("transfer_type.staticlogistics.item", "Item", "物品");
        add("transfer_type.staticlogistics.item.desc", "Transport Items", "传输物品。");
        add("transfer_type.staticlogistics.fluid", "Fluid", "流体");
        add("transfer_type.staticlogistics.fluid.desc", "Transport Fluids", "传输流体。");
        add("transfer_type.staticlogistics.energy", "Energy", "能量");
        add("transfer_type.staticlogistics.energy.desc", "Transport Energy", "传输能量。");

        add("transfer_type.staticlogistics.mek_chemicals", "Mek Chemicals", "化学品");
        add("transfer_type.staticlogistics.mek_chemicals.desc", "Transport Mek Chemicals", "传输化学品。");
        add("transfer_type.staticlogistics.mek_heat", "Mek Heat", "热量");
        add("transfer_type.staticlogistics.mek_heat.desc", "Transport Mekanism Heat", "传输热量。");
        add("transfer_type.staticlogistics.ars_source", "Ars Source", "魔源");
        add("transfer_type.staticlogistics.ars_source.desc", "Transport Ars Source", "传输魔源。");
        add("transfer_type.staticlogistics.botania_mana", "Mana", "魔力");
        add("transfer_type.staticlogistics.botania_mana.desc", "Transport Mana", "传输魔力。");

        add("failure.staticlogistics.no_dim", "Nether Star Upgrade Required", "需要下界之星综合升级卡");
        add("failure.staticlogistics.out_of_range", "Out of Range", "超出范围");
        add("failure.staticlogistics.chunk_unloaded", "Chunk Unloaded", "区块未加载");
        add("failure.staticlogistics.no_capability", "No Capability", "无能力");
        add("failure.staticlogistics.target_rejected", "Target Rejected Resource", "目标拒绝接收资源");
        add("failure.staticlogistics.target_policy_rejected", "Target policy rejected resource", "目标策略拒绝该资源");
        add("failure.staticlogistics.event_cancelled", "Event Cancelled", "事件取消");
        add("failure.staticlogistics.source_commit_failed", "Source Commit Failed", "源端提交失败");
        add("failure.staticlogistics.simulation_failed", "Transfer Simulation Failed", "传输模拟失败");
        add("failure.staticlogistics.rollback_failed", "Rollback Failed", "回滚失败");
        add("failure.staticlogistics.commit_state_unknown", "Commit State Unknown", "提交状态未知");

        for (DistributionStrategy strategy : DistributionStrategyRegistry.getValues()) {
            String zh = switch (strategy.id().getPath()) {
                case "sequential" -> "顺序优先";
                case "round_robin" -> "轮询分发";
                case "nearest" -> "最近优先";
                case "furthest" -> "最远优先";
                case "random" -> "随机分发";
                default -> toTitleCase(strategy.id().getPath());
            };
            add(strategy.getDescriptionId(), toTitleCase(strategy.id().getPath()), zh);
        }

        for (ExtractionMode mode : ExtractionMode.values()) {
            String zh = switch (mode) {
                case SEQUENTIAL -> "顺序提取";
                case SLOT_ROUND_ROBIN -> "插槽轮询提取";
            };
            add(mode.getDescriptionId(), toTitleCase(mode.getSerializedName()), zh);
        }

        StaticLogistics.chineseProviders.forEach(action -> action.accept(this));
    }

    public void add(String key, String en, String zh) {
        super.add(key, this.locale.equals("zh_cn") ? zh : en);
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
