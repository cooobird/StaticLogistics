package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 节点写请求的验证、命令执行和提交入口。
 */
public final class NodeMutationService {
    @Nullable
    public ValidatedNode resolve(ServerPlayer player, BlockPos pos, Direction face) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return null;
        LinkManager manager = LinkManager.get(level);
        FaceAddress key = FaceAddress.of(pos, face);
        FaceConfigComposite config = manager.getFaceConfig(key);
        if (!NodeInteractionValidator.holdsConfigurator(player)
            || !NodeInteractionValidator.canUseExisting(player, pos, face, config)) return null;
        return new ValidatedNode(player, level, manager, key, pos, face, config);
    }

    /**
     * 解析由连接配置器发起的远程节点修改。
     *
     * <p>远程编辑不要求玩家站在容器旁边，但必须手持配置器、节点仍属于请求分组，
     * 并且玩家拥有该节点的修改权限。这里不创建节点，也不强制加载目标区块。
     */
    @Nullable
    public ValidatedNode resolveRemote(ServerPlayer player, LogisticsNode node, GroupKey groupKey) {
        if (player == null || node == null || groupKey == null
            || !NodeInteractionValidator.holdsConfigurator(player)) return null;
        ServerLevel level = player.server.getLevel(node.gPos().dimension());
        if (level == null) return null;
        BlockPos pos = node.gPos().pos();
        if (!NodeInteractionValidator.canMutateRemote(player, level, pos)) return null;
        LinkManager manager = LinkManager.get(level);
        FaceAddress key = FaceAddress.of(node);
        FaceConfigComposite config = manager.getFaceConfig(key);
        if (config == null
            || !config.faceConfig.containsGroup(groupKey)
            || !config.canPlayerModify(player)) return null;
        return new ValidatedNode(
            player, level, manager, key, pos, node.face(), config);
    }

    public boolean configure(ValidatedNode node, FaceConfigurationEdit edit) {
        return !configureAll(List.of(node), edit).isEmpty();
    }

    /**
     * 在一个服务端事务中修改多个已验证节点。目标列表会按完整节点身份去重，
     * 任一准备或提交步骤失败时，所有节点、玩家背包和升级槽位一起回滚。
     */
    public List<ValidatedNode> configureAll(Collection<ValidatedNode> requestedNodes,
                                            FaceConfigurationEdit edit) {
        if (edit == null || requestedNodes == null || requestedNodes.isEmpty()) return List.of();
        List<ValidatedNode> nodes = distinctNodes(requestedNodes);
        if (nodes.isEmpty()) return List.of();
        var server = nodes.getFirst().level().getServer();
        if (nodes.stream().anyMatch(node -> node.level().getServer() != server)) {
            throw new IllegalArgumentException("Batch nodes must belong to the same server");
        }

        List<ValidatedNode> changed = new ArrayList<>();
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            nodes.forEach(node -> transaction.capture(node.node()));
            List<NodeLifecycleService.UpgradeSource> sources =
                collectDisabledRoleHandoffs(nodes, edit, transaction);
            try (NodeLifecycleService.HandoffReceipt receipt = sources.isEmpty() ? null
                : new PlayerUpgradeHandoff(nodes.getFirst().player()).begin(sources)) {
                for (ValidatedNode node : nodes) {
                    if (applyConfiguration(node.config(), edit)) changed.add(node);
                }
                Set<GroupKey> affectedGroups = new LinkedHashSet<>();
                changed.forEach(node -> node.config().faceConfig.getGroups()
                    .forEach(group -> affectedGroups.add(group.key())));
                affectedGroups.forEach(GlobalLogisticsManager.get(server)::syncGroupLinks);
                transaction.commit();
                if (receipt != null) receipt.commit();
            }
        }
        return List.copyOf(changed);
    }

    /**
     * 将来源节点的完整配置一次性应用到其余已验证节点。
     *
     * <p>面级设置包含输入输出、类型、过滤器、优先级与策略；容器级设置包含三类升级。
     * 物品只在目标槽位确实缺少时从玩家背包扣除，目标中被替换的物品优先返还背包。
     * 任一物品不足或提交失败时，节点、容器和玩家背包会整体回滚。
     */
    public List<ValidatedNode> applyTemplate(ValidatedNode source,
                                             Collection<ValidatedNode> requestedTargets) {
        if (source == null || requestedTargets == null || requestedTargets.isEmpty()) return List.of();
        List<ValidatedNode> targets = distinctNodes(requestedTargets).stream()
            .filter(target -> !target.node().equals(source.node()))
            .toList();
        if (targets.isEmpty()) return List.of();
        var server = source.level().getServer();
        if (targets.stream().anyMatch(target -> target.level().getServer() != server)) {
            throw new IllegalArgumentException("Batch nodes must belong to the same server");
        }

        List<ItemStack> inventorySnapshot = source.player().getInventory().items.stream()
            .map(ItemStack::copy).toList();
        List<SlotChange> slotChanges = collectTemplateSlotChanges(source, targets);
        Map<Item, Integer> requiredItems = requiredItems(slotChanges);
        if (!hasItems(source.player(), requiredItems)) return List.of();

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            targets.forEach(target -> transaction.capture(target.node()));
            captureTargetContainers(transaction, targets);
            transaction.onRollback(() -> restoreInventory(source.player(), inventorySnapshot));
            consumeItems(source.player(), requiredItems);

            List<NodeLifecycleService.UpgradeSource> returnedSources = returnedSources(slotChanges);
            try (NodeLifecycleService.HandoffReceipt receipt = returnedSources.isEmpty() ? null
                : new PlayerUpgradeHandoff(source.player()).begin(returnedSources)) {
                slotChanges.forEach(SlotChange::apply);
                targets.forEach(target -> copyFaceConfiguration(source.config(), target.config()));
                Set<GroupKey> affectedGroups = new LinkedHashSet<>();
                targets.forEach(target -> target.config().faceConfig.getGroups()
                    .forEach(group -> affectedGroups.add(group.key())));
                affectedGroups.forEach(GlobalLogisticsManager.get(server)::syncGroupLinks);
                transaction.commit();
                if (receipt != null) receipt.commit();
            }
        }
        return List.copyOf(targets);
    }

    private static List<SlotChange> collectTemplateSlotChanges(
        ValidatedNode source, List<ValidatedNode> targets) {
        List<SlotChange> changes = new ArrayList<>();
        IItemHandlerModifiable sourceFilters = source.config().filterConfig.getUpgrades();
        Set<ContainerIdentity> visitedContainers = new LinkedHashSet<>();
        ContainerConfig sourceContainer = source.config().getContainerConfig();
        for (ValidatedNode target : targets) {
            IItemHandlerModifiable targetFilters = target.config().filterConfig.getUpgrades();
            collectSlotChanges(sourceFilters, targetFilters, changes);
            ContainerIdentity identity = new ContainerIdentity(target.level(), target.pos());
            ContainerConfig targetContainer = target.config().getContainerConfig();
            if (sourceContainer != null && targetContainer != null && visitedContainers.add(identity)) {
                collectSlotChanges(sourceContainer.getUpgrades(), targetContainer.getUpgrades(), changes);
            }
        }
        return changes;
    }

    private static void collectSlotChanges(IItemHandlerModifiable source,
                                           IItemHandlerModifiable target,
                                           List<SlotChange> changes) {
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack current = target.getStackInSlot(slot);
            ItemStack desired = source.getStackInSlot(slot);
            if (!ItemStack.matches(current, desired)) {
                changes.add(new SlotChange(target, slot, current.copy(), desired.copy()));
            }
        }
    }

    private static Map<Item, Integer> requiredItems(List<SlotChange> changes) {
        Map<Item, Integer> required = new IdentityHashMap<>();
        for (SlotChange change : changes) {
            if (change.desired().isEmpty()) continue;
            int count = change.current().is(change.desired().getItem())
                ? Math.max(0, change.desired().getCount() - change.current().getCount())
                : change.desired().getCount();
            if (count > 0) required.merge(change.desired().getItem(), count, Integer::sum);
        }
        return required;
    }

    private static boolean hasItems(ServerPlayer player, Map<Item, Integer> required) {
        Map<Item, Integer> available = new IdentityHashMap<>();
        player.getInventory().items.forEach(stack -> {
            if (!stack.isEmpty()) available.merge(stack.getItem(), stack.getCount(), Integer::sum);
        });
        return required.entrySet().stream()
            .allMatch(entry -> available.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    private static void consumeItems(ServerPlayer player, Map<Item, Integer> required) {
        Map<Item, Integer> remaining = new IdentityHashMap<>(required);
        for (ItemStack stack : player.getInventory().items) {
            int count = remaining.getOrDefault(stack.getItem(), 0);
            if (count <= 0) continue;
            int removed = Math.min(count, stack.getCount());
            stack.shrink(removed);
            remaining.put(stack.getItem(), count - removed);
        }
        if (remaining.values().stream().anyMatch(count -> count > 0)) {
            throw new IllegalStateException("Required configuration items are unavailable");
        }
        player.getInventory().setChanged();
    }

    private static List<NodeLifecycleService.UpgradeSource> returnedSources(List<SlotChange> changes) {
        List<ItemStack> returned = new ArrayList<>();
        for (SlotChange change : changes) {
            if (change.current().isEmpty()) continue;
            int count = change.desired().is(change.current().getItem())
                ? Math.max(0, change.current().getCount() - change.desired().getCount())
                : change.current().getCount();
            if (count > 0) returned.add(change.current().copyWithCount(count));
        }
        if (returned.isEmpty()) return List.of();
        ItemStackHandler handler = new ItemStackHandler(returned.size());
        for (int slot = 0; slot < returned.size(); slot++) handler.setStackInSlot(slot, returned.get(slot));
        return List.of(new NodeLifecycleService.UpgradeSource(BlockPos.ZERO, handler));
    }

    private static void restoreInventory(ServerPlayer player, List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) {
            player.getInventory().items.set(slot, snapshot.get(slot).copy());
        }
        player.getInventory().setChanged();
    }

    private static void captureTargetContainers(NodeMutationTransaction transaction,
                                                List<ValidatedNode> targets) {
        Set<ContainerIdentity> captured = new LinkedHashSet<>();
        for (ValidatedNode target : targets) {
            ContainerIdentity identity = new ContainerIdentity(target.level(), target.pos());
            if (captured.add(identity)) transaction.captureContainer(target.level(), target.pos());
        }
    }

    private static void copyFaceConfiguration(FaceConfigComposite source,
                                              FaceConfigComposite target) {
        try (FaceConfigComposite.BulkEdit ignored = target.beginBulkEdit()) {
            target.setGlobalInputEnabled(source.isGlobalInputEnabled());
            target.setGlobalOutputEnabled(source.isGlobalOutputEnabled());
            target.setPriority(source.linkConfig.getPriority());
            target.setKeepStock(source.linkConfig.getKeepStock());
            target.setDistributionStrategy(source.linkConfig.getStrategy());
            target.setExtractionMode(source.linkConfig.getExtractionMode());
            target.setSelectedTypeIds(source.getSelectedTypeIds());
        }
    }

    private record SlotChange(IItemHandlerModifiable target, int slot,
                              ItemStack current, ItemStack desired) {
        private void apply() {
            target.setStackInSlot(slot, desired.copy());
        }
    }

    private record ContainerIdentity(ServerLevel level, BlockPos pos) {
        private ContainerIdentity {
            pos = pos.immutable();
        }
    }

    /**
     * 关闭输入或输出时，移交对应侧过滤器以及已不再被任何输出面使用的容器升级。
     *
     * <p>返还与面配置修改共享同一事务：优先进入玩家背包，背包已满时在玩家位置掉落；
     * 后续配置提交失败时，玩家背包、掉落实体和槽位会一起恢复。</p>
     */
    private static List<NodeLifecycleService.UpgradeSource> collectDisabledRoleHandoffs(
        List<ValidatedNode> nodes, FaceConfigurationEdit edit,
        NodeMutationTransaction transaction) {
        if (!(edit instanceof FaceConfigurationEdit.BooleanEdit(
            FaceConfigurationEdit.BooleanField field, boolean enabled
        ))
            || enabled) {
            return List.of();
        }
        Set<FaceAddress> disabledOutputs = new LinkedHashSet<>();
        if (field == FaceConfigurationEdit.BooleanField.GLOBAL_OUTPUT) {
            nodes.stream().filter(node -> node.config().isGlobalOutputEnabled())
                .map(ValidatedNode::key).forEach(disabledOutputs::add);
        }
        List<NodeLifecycleService.UpgradeSource> sources = new ArrayList<>();
        Set<ContainerConfig> capturedContainers =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ValidatedNode node : nodes) {
            FaceConfigComposite config = node.config();
            boolean active = field == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT
                ? config.isGlobalInputEnabled() : config.isGlobalOutputEnabled();
            if (!active) continue;
            int slot = field == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT ? 0 : 1;
            sources.add(new NodeLifecycleService.UpgradeSource(
                node.pos(), config.filterConfig.getUpgrades(), slot, slot + 1));
            ContainerConfig container = config.getContainerConfig();
            if (field == FaceConfigurationEdit.BooleanField.GLOBAL_OUTPUT
                && container != null
                && capturedContainers.add(container)
                && hasNoRemainingOutputFace(node, container, disabledOutputs)) {
                transaction.captureContainer(node.level(), node.pos());
                sources.add(new NodeLifecycleService.UpgradeSource(
                    node.pos(), container.getUpgrades()));
            }
        }
        return sources;
    }

    private static boolean hasNoRemainingOutputFace(
        ValidatedNode node, ContainerConfig container, Set<FaceAddress> disabledOutputs) {
        for (FaceAddress faceKey : container.getLinkedFaceKeys()) {
            if (disabledOutputs.contains(faceKey)) continue;
            FaceConfigComposite face = node.manager().getFaceConfig(faceKey);
            if (face != null && face.isGlobalOutputEnabled()) return false;
        }
        return true;
    }

    private static List<ValidatedNode> distinctNodes(Collection<ValidatedNode> nodes) {
        Set<LogisticsNode> identities = new LinkedHashSet<>();
        List<ValidatedNode> result = new ArrayList<>();
        for (ValidatedNode node : nodes) {
            if (node != null && identities.add(node.node())) result.add(node);
        }
        return result;
    }

    private static boolean applyConfiguration(FaceConfigComposite config,
                                              FaceConfigurationEdit edit) {
        if (edit instanceof FaceConfigurationEdit.SelectedTypesEdit(List<ResourceLocation> ids)
            && ids.stream().anyMatch(id -> TransferRegistries.get(id) == null)) {
            return false;
        }
        boolean changed = false;
        try (FaceConfigComposite.BulkEdit ignored = config.beginBulkEdit()) {
            if (edit instanceof FaceConfigurationEdit.BooleanEdit(
                FaceConfigurationEdit.BooleanField field2, boolean enabled
            )) {
                if (field2 == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT) {
                    changed = updateBoolean(enabled, config.isGlobalInputEnabled(),
                        config::setGlobalInputEnabled);
                } else {
                    changed = updateBoolean(enabled, config.isGlobalOutputEnabled(),
                        config::setGlobalOutputEnabled);
                }
            } else if (edit instanceof FaceConfigurationEdit.NumberEdit(
                FaceConfigurationEdit.NumberField field, int value
            )) {
                if (field == FaceConfigurationEdit.NumberField.PRIORITY) {
                    changed = updateInt(value, config.linkConfig.getPriority(), config::setPriority);
                } else {
                    changed = updateInt(value, config.linkConfig.getKeepStock(), config::setKeepStock);
                }
            } else if (edit instanceof FaceConfigurationEdit.StrategyEdit(DistributionStrategy strategy)) {
                if (!strategy.equals(config.linkConfig.getStrategy())) {
                    config.setDistributionStrategy(strategy);
                    changed = true;
                }
            } else if (edit instanceof FaceConfigurationEdit.ExtractionEdit(ExtractionMode mode)
                && mode != config.linkConfig.getExtractionMode()) {
                config.setExtractionMode(mode);
                changed = true;
            } else if (edit instanceof FaceConfigurationEdit.SelectedTypesEdit(List<ResourceLocation> typeIds)
                && !config.getSelectedTypeIds().equals(typeIds)) {
                config.setSelectedTypeIds(typeIds);
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

    private static boolean updateInt(int value, int current, IntConsumer setter) {
        if (value == current) return false;
        setter.accept(value);
        return true;
    }

    public boolean updateFilter(
        ValidatedNode node, ResourceLocation typeId, boolean input, FilterData filter) {
        if (TransferRegistries.get(typeId) == null) return false;
        int slotIndex = input ? 0 : 1;
        boolean changed;
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(node.level().getServer())) {
            transaction.capture(node.node());
            ItemStack upgradeStack = node.config().filterConfig.getUpgrades().getStackInSlot(slotIndex);
            changed = node.config().filterConfig.applyFilterData(upgradeStack, filter);
            transaction.commit();
        }
        return changed;
    }

    public boolean remove(ValidatedNode node) {
        node.manager().removeFaceConfig(node.key());
        return true;
    }

    public record ValidatedNode(ServerPlayer player, ServerLevel level, LinkManager manager,
                                FaceAddress key, BlockPos pos, Direction face,
                                FaceConfigComposite config) {
        public LogisticsNode node() {
            return manager.createNodeFromKey(key);
        }
    }
}
