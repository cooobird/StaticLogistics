package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
        LinkManager manager = LinkManager.get(level);
        FaceAddress key = FaceAddress.of(node);
        FaceConfigComposite config = manager.getFaceConfig(key);
        if (config == null
            || !config.faceConfig.getGroupKeys().contains(groupKey)
            || !config.canPlayerModify(player)) return null;
        return new ValidatedNode(
            player, level, manager, key, node.gPos().pos(), node.face(), config);
    }

    public boolean configure(ValidatedNode node, FaceConfigurationEdit edit) {
        if (edit == null) return false;
        boolean changed;
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(node.level().getServer())) {
            transaction.capture(node.node());
            try (NodeLifecycleService.HandoffReceipt receipt =
                     beginDisabledFilterHandoff(node, edit)) {
                changed = applyConfiguration(node.config(), edit);
                if (changed) {
                    for (var group : node.config().faceConfig.getGroups()) {
                        GlobalLogisticsManager.get(node.level().getServer()).syncGroupLinks(group.key());
                    }
                }
                transaction.commit();
                if (receipt != null) {
                    receipt.commit();
                }
            }
        }
        return changed;
    }

    /**
     * 关闭输入或输出时，只移交对应侧的过滤器物品。
     *
     * <p>返还与面配置修改共享同一事务：优先进入玩家背包，背包已满时在玩家位置掉落；
     * 后续配置提交失败时，玩家背包、掉落实体和过滤器槽位会一起恢复。</p>
     */
    @Nullable
    private static NodeLifecycleService.HandoffReceipt beginDisabledFilterHandoff(
        ValidatedNode node, FaceConfigurationEdit edit) {
        if (!(edit instanceof FaceConfigurationEdit.BooleanEdit booleanEdit)
            || booleanEdit.enabled()) return null;
        FaceConfigComposite config = node.config();
        int slot;
        if (booleanEdit.field() == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT) {
            if (!config.isGlobalInputEnabled()) return null;
            slot = 0;
        } else {
            if (!config.isGlobalOutputEnabled()) return null;
            slot = 1;
        }
        return new PlayerUpgradeHandoff(node.player()).begin(List.of(
            new NodeLifecycleService.UpgradeSource(
                node.pos(), config.filterConfig.getUpgrades(), slot, slot + 1)));
    }

    private static boolean applyConfiguration(FaceConfigComposite config,
                                              FaceConfigurationEdit edit) {
        if (edit instanceof FaceConfigurationEdit.SelectedTypesEdit typesEdit
            && typesEdit.typeIds().stream().anyMatch(id -> TransferRegistries.get(id) == null)) {
            return false;
        }
        boolean changed = false;
        try (FaceConfigComposite.BulkEdit ignored = config.beginBulkEdit()) {
            if (edit instanceof FaceConfigurationEdit.BooleanEdit booleanEdit) {
                if (booleanEdit.field() == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT) {
                    changed = updateBoolean(booleanEdit.enabled(), config.isGlobalInputEnabled(),
                        config::setGlobalInputEnabled);
                } else {
                    changed = updateBoolean(booleanEdit.enabled(), config.isGlobalOutputEnabled(),
                        config::setGlobalOutputEnabled);
                }
            } else if (edit instanceof FaceConfigurationEdit.NumberEdit numberEdit) {
                if (numberEdit.field() == FaceConfigurationEdit.NumberField.PRIORITY) {
                    changed = updateInt(numberEdit.value(), config.linkConfig.getPriority(), config::setPriority);
                } else {
                    changed = updateInt(numberEdit.value(), config.linkConfig.getKeepStock(), config::setKeepStock);
                }
            } else if (edit instanceof FaceConfigurationEdit.StrategyEdit strategyEdit) {
                if (!strategyEdit.strategy().equals(config.linkConfig.getStrategy())) {
                    config.setDistributionStrategy(strategyEdit.strategy());
                    changed = true;
                }
            } else if (edit instanceof FaceConfigurationEdit.ExtractionEdit extractionEdit
                && extractionEdit.mode() != config.linkConfig.getExtractionMode()) {
                config.setExtractionMode(extractionEdit.mode());
                changed = true;
            } else if (edit instanceof FaceConfigurationEdit.SelectedTypesEdit typesEdit
                && !config.getSelectedTypeIds().equals(typesEdit.typeIds())) {
                config.setSelectedTypeIds(typesEdit.typeIds());
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
