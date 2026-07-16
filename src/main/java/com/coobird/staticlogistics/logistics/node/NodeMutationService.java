package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
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

/**
 * 节点写请求的验证、命令执行和提交入口。
 */
@SuppressWarnings("try")
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

    public boolean configure(ValidatedNode node, FaceConfigurationEdit edit) {
        if (edit == null) return false;
        boolean changed;
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(node.level().getServer())) {
            transaction.capture(node.node());
            changed = applyConfiguration(node.config(), edit);
            if (changed) {
                GlobalLogisticsManager global = GlobalLogisticsManager.get(node.level().getServer());
                for (var group : node.config().faceConfig.getGroups()) {
                    global.syncGroupLinks(node.level(), group.displayName(), node.node());
                }
            }
            transaction.commit();
        }
        return changed;
    }

    private static boolean applyConfiguration(FaceConfigComposite config,
                                              FaceConfigurationEdit edit) {
        if (edit instanceof FaceConfigurationEdit.SelectedTypesEdit typesEdit
            && typesEdit.typeIds().stream().anyMatch(id -> TransferRegistries.get(id) == null)) {
            return false;
        }
        boolean changed = false;
        try (FaceConfigComposite.BulkEdit ignored = config.beginBulkEdit()) {
            if (edit instanceof FaceConfigurationEdit.BooleanEdit value) {
                if (value.field() == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT) {
                    changed = updateBoolean(value.enabled(), config.isGlobalInputEnabled(),
                        config::setGlobalInputEnabled);
                } else {
                    changed = updateBoolean(value.enabled(), config.isGlobalOutputEnabled(),
                        config::setGlobalOutputEnabled);
                }
            } else if (edit instanceof FaceConfigurationEdit.ChannelEdit value) {
                if (value.field() == FaceConfigurationEdit.ChannelField.INPUT) {
                    changed = updateInt(value.channel(), config.linkConfig.getInputChannel(),
                        config.linkConfig::setInputChannel);
                } else {
                    changed = updateInt(value.channel(), config.linkConfig.getOutputChannel(),
                        config.linkConfig::setOutputChannel);
                }
            } else if (edit instanceof FaceConfigurationEdit.NumberEdit value) {
                if (value.field() == FaceConfigurationEdit.NumberField.PRIORITY) {
                    changed = updateInt(value.value(), config.linkConfig.getPriority(),
                        config.linkConfig::setPriority);
                } else {
                    changed = updateInt(value.value(), config.linkConfig.getKeepStock(),
                        config.linkConfig::setKeepStock);
                }
            } else if (edit instanceof FaceConfigurationEdit.StrategyEdit value) {
                if (!value.strategy().equals(config.linkConfig.getStrategy())) {
                    config.setDistributionStrategy(value.strategy());
                    changed = true;
                }
            } else if (edit instanceof FaceConfigurationEdit.ExtractionEdit value) {
                if (value.mode() != config.linkConfig.getExtractionMode()) {
                    config.setExtractionMode(value.mode());
                    changed = true;
                }
            } else if (edit instanceof FaceConfigurationEdit.SelectedTypesEdit value
                && !config.getSelectedTypeIds().equals(value.typeIds())) {
                config.setSelectedTypeIds(value.typeIds());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean updateBoolean(boolean value, boolean current,
                                         java.util.function.Consumer<Boolean> setter) {
        if (value == current) return false;
        setter.accept(value);
        return true;
    }

    private static boolean updateInt(int value, int current, java.util.function.IntConsumer setter) {
        if (value == current) return false;
        setter.accept(value);
        return true;
    }

    /**
     * 通过节点事务更新权威过滤器数据。
     */
    public boolean updateFilter(ValidatedNode node, ResourceLocation typeId,
                                boolean input, FilterData filter) {
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
            return key.toNode(level.dimension());
        }
    }
}
