package com.coobird.staticlogistics.datagen;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.content.registry.SLCreativeTabs;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import com.coobird.staticlogistics.transfer.TransferTypeBootstrap;
import com.coobird.staticlogistics.transfer.UpgradeTier;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.registries.RegistryObject;

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

        add("config.jade.plugin_staticlogistics.jade", "Static Logistics Jade Plugin", "静态物流 Jade 适配");

        add("gui.staticlogistics.linker_settings", "Linker Configuration", "连接配置器");
        add("gui.staticlogistics.search_hint", "Search...", "搜索分组...");
        add("gui.staticlogistics.add_group", "Add Group", "添加分组");
        add("gui.staticlogistics.search_types", "Search...", "搜索类型");
        add("gui.staticlogistics.tooltip.toggle_type", "Click to enable/disable types", "点击以启用/禁用类型");
        add("gui.staticlogistics.tooltip.group_id", "Group #%s", "分组 #%s");
        add("gui.staticlogistics.tooltip.shift_export", "%s + Click to export coordinates", "%s + 点击导出坐标");
        add("gui.staticlogistics.tooltip.shift_more", "Hold %1$s to show %2$s more...", "按住 %1$s 显示剩余 %2$s 项...");
        add("gui.staticlogistics.tooltip.rename_hint", "Double-click to rename group", "双击以重命名分组");
        add("gui.staticlogistics.tooltip.select_hint", "Click to select this group", "点击以选择该分组");
        add("gui.staticlogistics.tooltip.right_click_delete", "Right-click to delete the group and all connections it has", "右键点击以删除该分组及其拥有的所有连接");

        add("gui.mode.staticlogistics.input", "Insert", "存入");
        add("gui.mode.staticlogistics.output", "Extract", "提取");
        add("gui.staticlogistics.label.priority", "Priority", "优先级");
        add("gui.staticlogistics.label.keep_stock", "Keep Stock", "存量维持");
        add("gui.staticlogistics.keep_stock.tooltip", "0=Disabled. Set N to keep at least N of each item at the target.", "0=禁用。设为N会在目标维持至少N个每种物品。");
        add("gui.staticlogistics.node.details", "[%1$s: %2$s  %3$s: %4$s  %5$s: %6$s]", "[%1$s：%2$s  %3$s：%4$s  %5$s：%6$s]");
        add("gui.staticlogistics.node.dimension", "Dimension", "维度");
        add("gui.staticlogistics.node.block_position", "Block Position", "方块坐标");
        add("gui.staticlogistics.node.connection_face", "Connection Face", "连接面朝向");
        add("gui.staticlogistics.node.distance_from_player", "[Distance from Player: %s blocks]", "[与玩家距离：%s 格]");
        add("gui.staticlogistics.direction.down", "Down", "下");
        add("gui.staticlogistics.direction.up", "Up", "上");
        add("gui.staticlogistics.direction.north", "North", "北");
        add("gui.staticlogistics.direction.south", "South", "南");
        add("gui.staticlogistics.direction.west", "West", "西");
        add("gui.staticlogistics.direction.east", "East", "东");
        add("gui.staticlogistics.face_config", "Node Configuration", "节点配置");
        add("gui.staticlogistics.priority.tooltip", "Hold %1$s: ×10, hold %2$s: ×5, hold both: ×64", "按住 %1$s：×10，按住 %2$s：×5，同时按住：×64");
        add("gui.staticlogistics.strategy", "Distribution Strategy", "分发策略");
        add("gui.staticlogistics.extraction_mode", "Extraction Mode", "提取模式");
        add("gui.staticlogistics.hint.speed", "Speed Upgrade", "速度升级");
        add("gui.staticlogistics.hint.range", "Range/Dim Upgrade", "范围/维度升级");
        add("gui.staticlogistics.hint.stack", "Stack Upgrade", "堆叠升级");
        add("gui.staticlogistics.hint.input_filter", "Input Filter", "输入过滤器");
        add("gui.staticlogistics.hint.output_filter", "Output Filter", "输出过滤器");
        add("gui.staticlogistics.stat.transfer", "Transfer:", "传输量:");
        add("gui.staticlogistics.stat.range", "Range:", "范围:");
        add("gui.staticlogistics.stat.speed", "Speed:", "速度:");
        add("gui.staticlogistics.stat.stack", "Stack:", "堆叠:");
        add("gui.staticlogistics.stat.dimension", "Cross-dimensional:", "跨维度:");
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
        add("gui.staticlogistics.clear_tags", "Clear the label for this line", "清除这一行的标签");
        add("gui.staticlogistics.tag_dropdown.help", "Scroll to select tags", "滚动选择标签");
        add("gui.staticlogistics.tag_dropdown.help2", "Click the tab to select it, and then tap the selected tab again to uncheck it.", "点击标签选中，再次点击已选中的标签即可取消选中。");
        add("gui.staticlogistics.tag_dropdown.help3", "Right-click to toggle tag condition", "右键对标签条件取反");
        add("gui.staticlogistics.part_match_button", "Part Match", "部分匹配");
        add("gui.staticlogistics.full_match_button", "Full Match", "完全匹配");
        add("gui.staticlogistics.ignore_durability", "Ignore Durability", "忽略耐久");
        add("gui.staticlogistics.filter.full", "Filter is full", "过滤器已满");
        add("gui.staticlogistics.filter.no_tags", "This item has no tags, cannot be added to the filter.", "此物品无标签，无法添加到过滤器");
        add("tooltip.staticlogistics.shift_right_mark", "%s+Right-click: quick mark item to filter", "%s+右键：快速标记物品到过滤器");
        add("gui.staticlogistics.filter.left_click_item", "Left-click: mark %s", "左键：标记 %s");
        add("gui.staticlogistics.filter.right_click_fluid", "Right-click: mark %s", "右键：标记 %s");

        add("tag_type.staticlogistics.item", "Item Tag:", "物品标签:");
        add("tag_type.staticlogistics.block", "Block Tag:", "方块标签:");
        add("tag_type.staticlogistics.fluid", "Fluid Tag:", "流体标签:");
        add("gui.staticlogistics.tag.active", "Whitelist", "白名单");
        add("gui.staticlogistics.tag.excluded", "Blacklist", "黑名单");

        add("msg.staticlogistics.export.header", "--- Coordinates for Group #%s ---", "--- 分组 #%s 的坐标列表 ---");
        add("msg.staticlogistics.export.tp_hover", "Click to suggest teleport command", "点击以补全传送指令");
        add("msg.staticlogistics.owner_display", "Owner: %s", "所有者：%s");
        add("msg.staticlogistics.node_added", "Node recorded. Total: %s", "节点已记录，当前共计：%s");
        add("msg.staticlogistics.no_capability", "This block has no logistics capability.", "此方块无物流能力。");
        add("msg.staticlogistics.node_removed", "Node unrecorded. Remaining: %s", "节点记录已移除，剩余：%s");
        add("msg.staticlogistics.stored_nodes_full", "The configurator can store at most %s nodes.", "连接配置器最多可记录 %s 个节点。");
        add("msg.staticlogistics.selection_cleared", "Selection Cleared", "已清空已记录节点");
        add("msg.staticlogistics.batch_linked_to_group", "Successfully linked %s nodes to Group: %s!", "成功将 %s 条链路连接至分组：%s！");
        add("msg.staticlogistics.no_nodes_stored", "No nodes are stored in the configurator!", "配置器中未存储任何节点！");
        add("msg.staticlogistics.out_of_range", "Out of Range", "超出范围");
        add("msg.staticlogistics.mode_switched", "Mode: %s", "当前模式：%s");
        add("msg.staticlogistics.mode_switched_with_nodes", "Mode: %s (%s nodes stored)", "当前模式：%s（已存储 %s 个节点）");
        add("msg.staticlogistics.no_links_on_face", "No removable links found on face: %s", "在 %s 面上未发现属于你或你团队的链路");
        add("msg.staticlogistics.no_permission", "Access Denied: Insufficient permissions.", "访问拒绝：权限不足。");
        add("msg.staticlogistics.no_permission_to_remove", "Cannot remove: You must be the owner or a Team Officer.", "无法移除：你必须是所有者 or 具备团队管理员权限。");
        add("msg.staticlogistics.self_link_error", "Cannot link a node to itself.", "无法将节点连接到自身。");
        add("msg.staticlogistics.group_removed_from_face", "Removed group %s from this face.", "已从该面移除组 %s。");
        add("msg.staticlogistics.group_not_on_face", "Group %s is not linked to this face.", "分组 %s 不属于该节点面。");
        add("msg.staticlogistics.select_group_to_remove", "Select a group first to remove.", "请先选取组再移除。");
        add("msg.staticlogistics.no_dimension_upgrade", "No dimension upgrade installed!", "未安装跨维度升级！");
        add("msg.staticlogistics.unknown_owner", "Unknown", "未知");
        add("msg.staticlogistics.tool_nodes_cleaned", "Removed %s invalid node(s) from configurator", "已从配置器中移除了 %s 个无效节点");
        add("msg.staticlogistics.wrench.no_permission", "No permission to remove this machine.", "没有权限移除此机器。");

        add("msg.staticlogistics.blueprint.anchor_set", "Anchor set at %s. Click opposite corner to copy region.", "锚点已设在 %s。点击对角位置复制区域。");
        add("msg.staticlogistics.blueprint.too_large", "Region too large (%s blocks). Maximum is 4096.", "区域过大（%s 方块）。最大 4096。");
        add("msg.staticlogistics.blueprint.empty", "No logistics configurations found in this area.", "该区域内未找到物流配置。");
        add("msg.staticlogistics.blueprint.missing_block", "Target block at %s is missing or cannot be modified.", "目标位置 %s 的方块缺失或不可修改。");
        add("msg.staticlogistics.blueprint.copied", "Copied %s face(s) from anchor %s.", "已从锚点 %2$s 复制了 %1$s 个面。");
        add("msg.staticlogistics.blueprint.pasted", "Pasted %s face(s) at anchor %s.", "已在锚点 %2$s 粘贴了 %1$s 个面。");
        add("msg.staticlogistics.blueprint.paste_failed", "Blueprint paste failed and all changes were rolled back.", "蓝图粘贴失败，所有修改均已回滚。");
        add("tooltip.staticlogistics.blueprint.info", "--- Blueprint ---", "--- 蓝图信息 ---");
        add("tooltip.staticlogistics.blueprint.face_count", " Faces: %s", "  面：%s 个");
        add("tooltip.staticlogistics.blueprint.container", " Container Upgrades: %s", "  容器升级：%s");
        add("tooltip.staticlogistics.blueprint.upgrades", " Upgrades：", "  升级卡：");
        add("tooltip.staticlogistics.blueprint.group", "Group: %s", "组：%s");
        add("tooltip.staticlogistics.blueprint.region", " Region: %s ~ %s", "  区域：%s 至 %s");
        add("tooltip.staticlogistics.blueprint.preview", "Preview at %s, rotation: %s°", "预览位置：%s，旋转：%s°");
        add("tooltip.staticlogistics.blueprint.anchor", "Selected point: %s", "已选起点：%s");
        add("tooltip.staticlogistics.blueprint.use", "%s+Right-click block: anchor / copy / preview / confirm", "%s+右键方块：设锚点 / 复制区域 / 预览 / 确认粘贴");
        add("tooltip.staticlogistics.blueprint.clear", "%s+Right-click air: clear all", "%s+右键空气：清空全部");
        add("tooltip.staticlogistics.blueprint.scroll", "%s+Scroll: move / %s+Scroll: rotate / %s+Scroll: Y-axis", "%s+滚轮：平移 / %s+滚轮：旋转 / %s+滚轮：升降");
        add("tooltip.staticlogistics.blueprint.undo", "Press %s to undo last paste", "按 %s 撤销上次粘贴");
        add("msg.staticlogistics.blueprint.missing_upgrades", "Missing %s upgrade(s) — check your inventory.", "缺少 %s 张升级卡 — 请检查背包。");
        add("msg.staticlogistics.blueprint.preview_enter", "Preview at %s — right-click to confirm, scroll to adjust.", "预览贴在 %s — 右键确认粘贴，滚轮调整位置。");
        add("msg.staticlogistics.blueprint.preview_moved", "Preview moved to %s.", "预览已移至 %s。");
        add("msg.staticlogistics.blueprint.select_group", "Select a group first before pasting.", "请先在配置器中选取组再粘贴。");
        add("msg.staticlogistics.blueprint.cleared", "Blueprint cleared.", "蓝图已清空。");
        add("msg.staticlogistics.blueprint.undone", "Blueprint paste undone. Restored %s face(s).", "蓝图粘贴已撤销。恢复了 %s 个面。");
        add("msg.staticlogistics.blueprint.no_undo", "Nothing to undo.", "没有可撤销的操作。");
        add("msg.staticlogistics.blueprint.undo_failed", "Blueprint undo failed and the pasted state was restored.", "蓝图撤销失败，已恢复撤销前的粘贴状态。");

        add("mode.staticlogistics.wrench", "Wrench", "扳手");
        add("mode.staticlogistics.wrench.desc",
            "%s + Right-click a machine to remove it. With Mekanism Additions installed, plastic blocks can also be dismantled.",
            "%s+右键拆卸机器。安装 Mekanism：Additions 后可拆卸塑料方块。");
        add("mode.staticlogistics.link_as_input", "Select point as Insert", "选取点为存入端");
        add("mode.staticlogistics.link_as_input.desc",
            "%s + Right-click a node to store it as an insert target. Resources will be inserted into this node.",
            "%s+右键点击节点，将其标记为存入目标（资源将传入此节点）。");
        add("mode.staticlogistics.link_as_output", "Select point as Extract", "选取点为提取端");
        add("mode.staticlogistics.link_as_output.desc",
            "%s + Right-click a node to store it as an extract source. Resources will be extracted from this node.",
            "%s+右键点击节点，将其标记为提取源（从此节点向外传输资源）。");
        add("mode.staticlogistics.remove", "Remove Links", "移除现有链路");
        add("mode.staticlogistics.remove.desc",
            "%s + Right-click a node face to delete all links connected to it.",
            "%s+右键点击节点面，删除该面上现有的所有物流链路。");
        add("mode.staticlogistics.node_config", "Configure Node", "配置节点");
        add("mode.staticlogistics.node_config.desc", "%s + Right-click to configure face and container.", "%s+右键配置节点面和容器。");

        add("key.categories.staticlogistics", "Static Logistics", "静态物流");
        add("key.staticlogistics.blueprint_preview_move", "Blueprint Preview Move", "蓝图预览移动");
        add("key.staticlogistics.blueprint_preview_rotate", "Blueprint Preview Rotate", "蓝图预览旋转");
        add("key.staticlogistics.blueprint_preview_move_y", "Blueprint Preview Move Y", "蓝图预览升降");
        add("key.staticlogistics.tool_mode_scroll", "Switch Tool Mode with Scroll", "滚轮切换工具模式");
        add("key.staticlogistics.clear_stored_nodes", "Clear Stored Nodes", "清除已存储节点");
        add("key.staticlogistics.blueprint_undo", "Undo Blueprint Paste", "撤销蓝图粘贴");
        add("key.staticlogistics.quick_filter_mark", "Quickly Add Filter Entry", "快速添加过滤项");
        add("key.staticlogistics.priority_x10", "Adjust Value ×10", "数值调整 ×10");
        add("key.staticlogistics.priority_x5", "Adjust Value ×5", "数值调整 ×5");
        add("key.staticlogistics.group_details_and_export", "Show Group Details / Export Coordinates", "显示分组详情 / 导出坐标");

        add("jade.staticlogistics.input", "[Input]", "[输入]");
        add("jade.staticlogistics.output", "[Output]", "[输出]");
        add("jade.staticlogistics.both", "[Both]", "[双向]");
        add("jade.staticlogistics.group_label", "Group: %s", "组: %s");
        add("jade.staticlogistics.linked", " | %s nodes", " | %s节点");
        add("jade.staticlogistics.receive_types", "Accepted Types: %s", "接收类型：%s");
        add("jade.staticlogistics.transfer_types", "Transfer Types: %s", "传输类型：%s");
        add("jade.staticlogistics.no_resource_types", "None selected", "未选择");
        add("jade.staticlogistics.transfer_stats", "  Sent:%s Rcv:%s", "  发送:%s 接收:%s");
        add("jade.staticlogistics.face_stats", "Rate: %s/min | Last: %s ago", "速率: %s/分钟 | 上次: %s前");
        add("jade.staticlogistics.section_input", "Input:", "输入:");
        add("jade.staticlogistics.section_output", "Output:", "输出:");
        add("jade.staticlogistics.channel", "Channel: %s", "频道: %s");
        add("jade.staticlogistics.priority", "Priority: %s", "优先级: %s");
        add("jade.staticlogistics.keep_stock", "Keep >= %s", "存量维持 >= %s");
        add("jade.staticlogistics.strategy_label", "Distribution: %s", "分发策略: %s");
        add("jade.staticlogistics.extraction_label", "Extraction: %s", "提取策略: %s");
        add("jade.staticlogistics.owner", "Owner: %s", "所有者: %s");

        add("tooltip.staticlogistics.mode", "Mode: %s", "工具模式：%s");
        add("tooltip.staticlogistics.type", "Transfer Type: %s", "传输类型：%s");
        add("tooltip.staticlogistics.group", "Group Id: %s", "分组 ID：%s");
        add("tooltip.staticlogistics.none", "None", "无");
        add("tooltip.staticlogistics.saved_list", "List of stored nodes：", "已存储的节点列表：");
        add("tooltip.staticlogistics.stored_mode", "Mode: %s", "模式：%s");
        add("tooltip.staticlogistics.scroll_hint", "%s+Scroll: switch mode", "%s+滚轮：切换工具模式");
        add("tooltip.staticlogistics.clear_stored_hint", "Press %s to clear stored nodes", "按 %s 清除已存储节点");
        add("tooltip.staticlogistics.auto_clean_info", "Auto-clears stored nodes after each link (configurable)", "每次建立连接后自动清空存储节点（配置文件可关闭）");
        add("tooltip.staticlogistics.upgrade.tier_display", "Tier: %s", "等级：%s");
        add("tooltip.staticlogistics.upgrade.value", "Multiplier: %s", "效果倍率：%s");
        add("tooltip.staticlogistics.upgrade.dimension_feature", "Enables interdimensional logistics.", "解锁跨维度物流传输。");
        add("tooltip.staticlogistics.upgrade.install_hint", "Install into nodes to enhance capabilities.", "安装至节点以增强其传输属性。");

        add("commands.staticlogistics.info.no_links", "No active source links on this block face.", "该方块表面没有活动的源链路。");
        add("commands.staticlogistics.transfer.success", "Successfully transferred %s link(s) from %s to %s", "成功将 %s 条链路从玩家 %s 转移给 %s");
        add("commands.staticlogistics.transfer.group_success", "Successfully transferred Group '%2$s' (%3$s links) from %1$s to %4$s", "已成功将玩家 %1$s 的分组“%2$s”（共 %3$s 条链路）转移给 %4$s");
        add("commands.staticlogistics.rename.success", "Group '%s' renamed to '%s' for player %s", "已为玩家 %3$s 将分组“%1$s”重命名为“%2$s”");
        add("commands.staticlogistics.cleanup.success", "Deleted %s link(s) owned by %s", "已清理属于玩家 %2$s 的 %1$s 条链路");
        add("commands.staticlogistics.info.group", "Group: %s", "分组：%s");
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
        add("commands.staticlogistics.info.input_channel", "Input Channel: %d", "输入频道：%d");
        add("commands.staticlogistics.info.output_channel", "Output Channel: %d", "输出频道：%d");
        add("commands.staticlogistics.info.strategy", "Strategy: %s", "分发策略：%s");
        add("commands.staticlogistics.info.priority", "Priority: %d", "优先级：%d");
        add("commands.staticlogistics.info.role_version", "Role: %s, Version: %s", "角色：%s，版本：%s");
        add("commands.staticlogistics.info.selected_types", "Selected Types: %s", "已选传输类型：%s");
        add("commands.staticlogistics.info.present_capabilities", "Present Capabilities: %s", "当前可用能力：%s");
        add("commands.staticlogistics.info.linked_nodes_detail", "Linked Nodes: %s", "已连接节点：%s");
        add("commands.staticlogistics.info.linked_nodes", "Linked Nodes: %d", "已连接节点数：%d");
        add("commands.staticlogistics.info.enabled", "Enabled", "启用");
        add("commands.staticlogistics.info.disabled", "Disabled", "禁用");

        add("commands.staticlogistics.list.header", "=== Active Logistics Nodes ===", "=== 当前活跃物流节点 ===");
        add("commands.staticlogistics.list.no_groups", "No active logistics groups found.", "未找到活跃的物流分组。");
        add("commands.staticlogistics.list.group_entry", "Group: %s (%d nodes)", "分组：%s（共 %d 个节点）");
        add("commands.staticlogistics.list.node_entry", "  - %s %s (%s)", "  - %s %s（角色：%s）");
        add("commands.staticlogistics.debug.header", "StaticLogistics Debug", "StaticLogistics 调试信息");
        add("commands.staticlogistics.debug.transfer_types", "Transfer Types: %s, Generation: %s", "传输类型：%s，版本代：%s");
        add("commands.staticlogistics.debug.cache", "Capability Cache: dimensions=%s, entries=%s, live=%s, stale=%s", "能力缓存：维度=%s，条目=%s，有效=%s，失效=%s");
        add("commands.staticlogistics.debug.scheduler", "Scheduler: last=%sμs, peak=%sμs, avg/dim=%sμs, candidates=%s, attempts=%s, budget stops=%s", "调度器：上次=%sμs，峰值=%sμs，维度平均=%sμs，候选=%s，尝试=%s，预算停止=%s 次");
        add("commands.staticlogistics.debug.help", "Use /sl debug types or /sl debug cache for details.", "使用 /sl debug types 或 /sl debug cache 查看详情。");
        add("commands.staticlogistics.debug.cache_header", "Capability Cache", "能力缓存");
        add("commands.staticlogistics.debug.cache_stats", "Dimensions: %s, Entries: %s, Live: %s, Stale: %s", "维度：%s，条目：%s，有效：%s，失效：%s");
        add("commands.staticlogistics.debug.types_header", "Transfer Types", "传输类型");
        add("commands.staticlogistics.debug.type_line", "%s bitOffset=%s legacyMask=%s handler=%s", "%s bitOffset=%s 旧掩码=%s 处理器=%s");

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

        add("failure.staticlogistics.no_dim", "No Dimension Upgrade", "无维度升级");
        add("failure.staticlogistics.out_of_range", "Out of Range", "超出范围");
        add("failure.staticlogistics.chunk_unloaded", "Chunk Unloaded", "区块未加载");
        add("failure.staticlogistics.no_capability", "No Capability", "无能力");
        add("failure.staticlogistics.target_rejected", "Target Rejected Resource", "目标拒绝接收资源");
        add("failure.staticlogistics.event_cancelled", "Event Cancelled", "事件取消");
        add("failure.staticlogistics.source_commit_failed", "Source Commit Failed", "源端提交失败");
        add("failure.staticlogistics.rollback_failed", "Rollback Failed", "回滚失败");

        add("match_strategy.staticlogistics.exact", "EXACT", "精确");
        add("match_strategy.staticlogistics.contains", "CONTAINS", "包含");
        add("match_strategy.staticlogistics.smart_contains", "SMART_CONTAINS (default)", "智能包含（默认）");
        add("match_strategy.staticlogistics.ignore", "IGNORE", "忽略");

        add("staticlogistics.configuration.general", "General Settings", "基础设置");
        add("staticlogistics.configuration.performance", "Performance Settings", "性能设置");
        add("staticlogistics.configuration.upgrades", "Upgrade Settings", "插件参数");
        add("staticlogistics.configuration.filter", "Filter Settings", "过滤插件参数");

        add("config.staticlogistics.default_radius", "Default Link Radius", "默认连接半径");
        add("config.staticlogistics.default_tick_interval", "Base Tick Interval (Ticks)", "基础传输间隔(Tick)");
        add("config.staticlogistics.max_transfer_limit", "Max Transfer per Tick", "单次传输最大数量");
        add("config.staticlogistics.max_transfer_limit.tooltip",
            """
                Maximum amount of various types transferred per tick.
                Large values may cause performance issues.""",
            """
                每刻传输的各个类型最大数量。
                数值过大可能导致性能问题。""");
        add("config.staticlogistics.auto_clean_stored_nodes", "Auto Clean Stored Nodes", "自动清理存储节点");
        add("config.staticlogistics.auto_clean_stored_nodes.tooltip", "If true, stored node references will be automatically cleaned after batch linking or when a node is removed.", "开启后，批量链接完成或节点被移除时，配置器中存储的节点引用将自动清理。");

        add("config.staticlogistics.cache.provider_size", "Expected Provider Count", "预期提供者数量");
        add("config.staticlogistics.cache.provider_size.tooltip",
            """
                Initial capacity of the active provider index.
                This value does not limit the actual number of active nodes.
                Default: 1000, Range: 100-10000.""",
            """
                活跃提供者索引的初始容量。
                此值不会限制实际活跃节点数量。
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
                Base node/type candidates scanned per tick. Lower = less lag, Higher = faster.
                Default: 50, Range: 10-200.""",
            """
                每刻扫描的节点/类型候选基数。越小越流畅，越大响应越快。
                默认：50，范围：10-200。""");

        add("config.staticlogistics.performance.ticker_time_budget_us",
            "Ticker Time Budget (μs)", "定时器时间预算（微秒）");
        add("config.staticlogistics.performance.ticker_time_budget_us.tooltip",
            """
                Soft time limit for logistics scheduling in each dimension tick.
                Default: 1500, Range: 100-10000.""",
            """
                每个维度每刻用于物流调度的软时间上限。
                默认：1500，范围：100-10000。""");

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

        add("config.staticlogistics.mek_gas_stack_size", "Base Mek-Gas Amount", "基础 Mek 气体传输量");
        add("config.staticlogistics.mek_infusion_stack_size", "Base Mek-Infusion Amount", "基础 Mek 灌注传输量");
        add("config.staticlogistics.mek_pigment_stack_size", "Base Mek-Pigment Amount", "基础 Mek 颜料传输量");
        add("config.staticlogistics.mek_slurry_stack_size", "Base Mek-Slurry Amount", "基础 Mek 浆液传输量");

        add("config.staticlogistics.mek_heat_stack_size", "Base Mek Heat Amount", "基础热量传输量");
        add("config.staticlogistics.ars_source_stack_size", "Base Ars Source Amount", "基础魔源传输量");
        add("config.staticlogistics.botania_mana_stack_size", "Base Mana Amount", "基础魔力传输量");
        add("config.staticlogistics.gtceu_stack_size", "Base GT EU Amount", "基础 GT EU 传输量");

        add("config.staticlogistics.iron_multiplier", "Iron Tier Multiplier", "铁等级倍率");
        add("config.staticlogistics.gold_multiplier", "Gold Tier Multiplier", "金等级倍率");
        add("config.staticlogistics.diamond_multiplier", "Diamond Tier Multiplier", "钻石等级倍率");
        add("config.staticlogistics.netherite_multiplier", "Netherite Tier Multiplier", "下界合金等级倍率");
        add("config.staticlogistics.nether_star_multiplier", "Nether Star Tier Multiplier", "下界之星等级倍率");

        add("transfer_type.staticlogistics.item", "Item", "物品");
        add("transfer_type.staticlogistics.item.desc", "Transport Items", "传输物品。");
        add("transfer_type.staticlogistics.fluid", "Fluid", "流体");
        add("transfer_type.staticlogistics.fluid.desc", "Transport Fluids", "传输流体。");
        add("transfer_type.staticlogistics.energy", "Energy", "能量");
        add("transfer_type.staticlogistics.energy.desc", "Transport Energy", "传输能量。");

        add("transfer_type.staticlogistics.mek_gas", "Mek Gas", "Mek 气体");
        add("transfer_type.staticlogistics.mek_gas.desc", "Transport Mek Gas", "传输 Mek 气体。");
        add("transfer_type.staticlogistics.mek_infusion", "Mek Infusion", "Mek 灌注类型");
        add("transfer_type.staticlogistics.mek_infusion.desc", "Transport Mek Infusion", "传输 Mek 灌注类型。");
        add("transfer_type.staticlogistics.mek_pigment", "Mek Pigment", "Mek 颜料");
        add("transfer_type.staticlogistics.mek_pigment.desc", "Transport Mek Pigment", "传输 Mek 颜料。");
        add("transfer_type.staticlogistics.mek_slurry", "Mek Slurry", "Mek 浆液");
        add("transfer_type.staticlogistics.mek_slurry.desc", "Transport Mek Slurry", "传输 Mek 浆液。");
        add("transfer_type.staticlogistics.mek_heat", "Mek Heat", "热量");
        add("transfer_type.staticlogistics.mek_heat.desc", "Transport Mekanism Heat", "传输热量。");
        add("transfer_type.staticlogistics.ars_source", "Ars Source", "魔源");
        add("transfer_type.staticlogistics.ars_source.desc", "Transport Ars Source", "传输魔源。");
        add("transfer_type.staticlogistics.botania_mana", "Mana", "魔力");
        add("transfer_type.staticlogistics.botania_mana.desc", "Transport Mana", "传输魔力。");
        add("transfer_type.staticlogistics.gtceu_energy", "GT EU", "GT EU 能量");
        add("transfer_type.staticlogistics.gtceu_energy.desc", "Transport GregTech EU Energy", "传输 GregTech EU 能量。");

        for (UpgradeType type : UpgradeType.values()) {
            String key = "tooltip.staticlogistics.upgrade." + type.getName() + "_desc";
            String enDesc = switch (type) {
                case SPEED -> "Decreases the time interval between transfers.";
                case RANGE -> "Increases the maximum distance for wireless links.";
                case STACK -> "Increases the maximum amount of resources moved per tick.";
                case DIMENSION -> "Enables logistics across different dimensions.";
                case BASIC_FILTER -> "Enables basic filtering of resources.";
                case TAG_FILTER -> "Enables filtering of resources based on tags.";
                case NBT_FILTER -> "Enables filtering of resources based on NBT data.";
            };
            String zhDesc = switch (type) {
                case SPEED -> "缩短传输间隔时间。";
                case RANGE -> "增加链路连接的最大距离。";
                case STACK -> "增加单次传输的数量限制。";
                case DIMENSION -> "无视维度进行传输。";
                case BASIC_FILTER -> "基础过滤器。";
                case TAG_FILTER -> "支持基于标签过滤资源。";
                case NBT_FILTER -> "支持基于NBT数据过滤资源。";
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

    public void addItem(RegistryObject<? extends Item> key, String zh) {
        this.add(key.get().getDescriptionId(), toTitleCase(key.get().getDescriptionId()), zh);
    }

    public void addCreativeTab(Supplier<CreativeModeTab> tab, String en, String zh) {
        Component title = tab.get().getDisplayName();
        if (title.getContents() instanceof TranslatableContents translatable) {
            this.add(translatable.getKey(), en, zh);
        }
    }
}
