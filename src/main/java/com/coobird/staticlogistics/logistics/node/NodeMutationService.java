package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/// 节点写请求的验证、命令执行和提交入口。
public final class NodeMutationService {
    /// 只读解析不授予写入权限，写操作仍必须使用 resolveRemote。
    @Nullable
    public ValidatedNode resolveRemoteView(ServerPlayer player, LogisticsNode node, GroupKey groupKey) {
        if (player == null || node == null || groupKey == null
            || !NodeInteractionValidator.holdsConfigurator(player)
            || !PermissionService.getInstance().canAccess(groupKey.ownerId(), player)) return null;
        ServerLevel level = player.server.getLevel(node.gPos().dimension());
        if (level == null) return null;
        BlockPos pos = node.gPos().pos();
        LinkManager manager = LinkManager.get(level);
        EndpointAddress key = EndpointAddress.of(node);
        EndpointConfig config = manager.getEndpointConfig(key);
        if (config == null || !config.faceConfig.containsGroup(groupKey)
            || !config.canPlayerAccess(player)) return null;
        return new ValidatedNode(player, level, manager, key, pos, node.face(), config);
    }

    /// 解析由连接配置器发起的远程节点修改。
    /// 远程编辑不要求玩家站在容器旁边，但必须手持配置器、节点仍属于请求分组，
    /// 并且玩家拥有该节点的修改权限。这里不创建节点，也不强制加载目标区块。
    @Nullable
    public ValidatedNode resolveRemote(ServerPlayer player, LogisticsNode node, GroupKey groupKey) {
        if (player == null || node == null || groupKey == null
            || !NodeInteractionValidator.holdsConfigurator(player)) return null;
        ServerLevel level = player.server.getLevel(node.gPos().dimension());
        if (level == null) return null;
        BlockPos pos = node.gPos().pos();
        if (!NodeInteractionValidator.canMutateRemote(player, level, pos)) return null;
        LinkManager manager = LinkManager.get(level);
        EndpointAddress key = EndpointAddress.of(node);
        EndpointConfig config = manager.getEndpointConfig(key);
        if (config == null
            || !config.faceConfig.containsGroup(groupKey)
            || !config.canPlayerModify(player)) return null;
        return new ValidatedNode(
            player, level, manager, key, pos, node.face(), config);
    }

    public boolean configure(ValidatedNode node, ChannelConfigurationEdit edit) {
        return !configureAll(List.of(node), edit).isEmpty();
    }

    /// 在一个服务端事务中修改多个已验证节点。目标列表会按完整节点身份去重，
    /// 任一配置修改失败时，恢复整批节点。
    public List<ValidatedNode> configureAll(Collection<ValidatedNode> requestedNodes,
                                            ChannelConfigurationEdit edit) {
        return configureAll(requestedNodes, List.of(edit));
    }

    public List<ValidatedNode> configureAll(Collection<ValidatedNode> requestedNodes,
                                            List<ChannelConfigurationEdit> edits) {
        if (edits.isEmpty() || requestedNodes == null || requestedNodes.isEmpty()) return List.of();
        List<ValidatedNode> nodes = distinctNodes(requestedNodes).stream().filter(node -> !node.node().isConnectionFace()).toList();
        if (nodes.isEmpty()) return List.of();
        var server = nodes.get(0).level().getServer();
        if (nodes.stream().anyMatch(node -> node.level().getServer() != server)) {
            throw new IllegalArgumentException("Batch nodes must belong to the same server");
        }

        List<ValidatedNode> changed = new ArrayList<>();
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            nodes.forEach(node -> transaction.capture(node.node()));
            for (ValidatedNode node : nodes) {
                boolean modified = false;
                try (var ignored = node.config().beginBulkEdit()) {
                    for (var edit : edits) modified |= applyConfiguration(node.config(), edit);
                }
                if (modified) changed.add(node);
            }
            Set<GroupKey> affectedGroups = new LinkedHashSet<>();
            changed.forEach(node -> node.config().faceConfig.getGroups()
                .forEach(group -> affectedGroups.add(group.key())));
            if (edits.stream().anyMatch(edit -> !(edit instanceof ChannelConfigurationEdit.NumberEdit)))
                affectedGroups.forEach(GlobalLogisticsManager.get(server)::syncGroupLinks);
            transaction.commit();
        }
        return List.copyOf(changed);
    }

    private static List<ValidatedNode> distinctNodes(Collection<ValidatedNode> nodes) {
        Set<LogisticsNode> identities = new LinkedHashSet<>();
        List<ValidatedNode> result = new ArrayList<>();
        for (ValidatedNode node : nodes) {
            if (node != null && identities.add(node.node())) result.add(node);
        }
        return result;
    }

    private static boolean applyConfiguration(EndpointConfig config,
                                              ChannelConfigurationEdit edit) {
        if (edit instanceof ChannelConfigurationEdit.FilterEdit filter) {
            ItemStack stack = config.filterConfig.getUpgrades().getStackInSlot(0);
            return config.filterConfig.applyFilterData(stack, filter.filter());
        }
        if (edit instanceof ChannelConfigurationEdit.SelectedTypesEdit selected
            && selected.typeIds().stream().anyMatch(id -> TransferRegistries.get(id) == null)) {
            return false;
        }
        boolean changed = false;
        if (edit instanceof ChannelConfigurationEdit.RoleEdit role) {
            if (config.determineRole() == role.role()) return false;
            try (var ignored = config.beginBulkEdit()) {
                config.setGlobalInputEnabled(role.role().canReceive());
                config.setGlobalOutputEnabled(role.role().canSend());
            }
            return true;
        }
        if (edit instanceof ChannelConfigurationEdit.EnabledEdit enabled) {
            if (config.linkConfig.isEnabled() == enabled.enabled()) return false;
            config.setChannelEnabled(enabled.enabled());
            return true;
        }
        if (edit instanceof ChannelConfigurationEdit.RedstoneEdit redstone) {
            if (config.linkConfig.getRedstoneMode() == redstone.mode()) return false;
            config.setRedstoneMode(redstone.mode());
            return true;
        }
        if (edit instanceof ChannelConfigurationEdit.TypePatchEdit patch) {
            if (patch.added().stream().anyMatch(id -> TransferRegistries.get(id) == null)
                || patch.removed().stream().anyMatch(id -> TransferRegistries.get(id) == null)) return false;
            List<ResourceLocation> updated = patch.apply(config.getSelectedTypeIds());
            if (updated.equals(config.getSelectedTypeIds())) return false;
            config.setSelectedTypeIds(updated);
            return true;
        }
        try (EndpointConfig.BulkEdit ignored = config.beginBulkEdit()) {
            if (edit instanceof ChannelConfigurationEdit.BooleanEdit booleanEdit) {
                var field2 = booleanEdit.field();
                boolean enabled = booleanEdit.enabled();
                if (field2 == ChannelConfigurationEdit.BooleanField.GLOBAL_INPUT) {
                    changed = updateBoolean(enabled, config.isGlobalInputEnabled(), config::setGlobalInputEnabled);
                } else {
                    changed = updateBoolean(enabled, config.isGlobalOutputEnabled(), config::setGlobalOutputEnabled);
                }
            } else if (edit instanceof ChannelConfigurationEdit.NumberEdit numberEdit) {
                var field = numberEdit.field();
                long value = numberEdit.value();
                if (field == ChannelConfigurationEdit.NumberField.PRIORITY) {
                    changed = updateLong(value, config.linkConfig.getPriority(), config::setPriority);
                } else {
                    changed = updateLong(value, config.linkConfig.getKeepStock(), config::setKeepStock);
                }
            } else if (edit instanceof ChannelConfigurationEdit.StrategyEdit strategyEdit) {
                var strategy = strategyEdit.strategy();
                if (!strategy.equals(config.linkConfig.getStrategy())) {
                    config.setDistributionStrategy(strategy);
                    changed = true;
                }
            } else if (edit instanceof ChannelConfigurationEdit.ExtractionEdit extraction
                && extraction.mode() != config.linkConfig.getExtractionMode()) {
                config.setExtractionMode(extraction.mode());
                changed = true;
            } else if (edit instanceof ChannelConfigurationEdit.SelectedTypesEdit selected
                && !config.getSelectedTypeIds().equals(selected.typeIds())) {
                config.setSelectedTypeIds(selected.typeIds());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean updateBoolean(boolean value, boolean current, Consumer<Boolean> setter) {
        if (value == current) return false;
        setter.accept(value);
        return true;
    }

    private static boolean updateLong(long value, long current, LongConsumer setter) {
        if (value == current) return false;
        setter.accept(value);
        return true;
    }

    public boolean updateFilter(
        ValidatedNode node, ResourceLocation typeId, boolean input, FilterData filter) {
        if (TransferRegistries.get(typeId) == null) return false;
        int slotIndex = 0;
        boolean changed;
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(node.level().getServer())) {
            transaction.capture(node.node());
            ItemStack upgradeStack = node.config().filterConfig.getUpgrades().getStackInSlot(slotIndex);
            changed = node.config().filterConfig.applyFilterData(upgradeStack, filter);
            transaction.commit();
        }
        return changed;
    }

    public record ValidatedNode(ServerPlayer player, ServerLevel level, LinkManager manager,
                                EndpointAddress key, BlockPos pos, Direction face,
                                EndpointConfig config) {
        public LogisticsNode node() {
            return manager.createNodeFromKey(key);
        }
    }
}
