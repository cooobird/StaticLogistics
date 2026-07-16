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

/** 节点写请求的验证、命令执行和提交入口。 */
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
                for (var group : node.config().faceConfig.getGroups()) {
                    GlobalLogisticsManager.get(node.level().getServer()).syncGroupLinks(group.key());
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
            if (edit instanceof FaceConfigurationEdit.BooleanEdit booleanEdit) {
                if (booleanEdit.field() == FaceConfigurationEdit.BooleanField.GLOBAL_INPUT) {
                    changed = updateBoolean(booleanEdit.enabled(), config.isGlobalInputEnabled(),
                        config::setGlobalInputEnabled);
                } else {
                    changed = updateBoolean(booleanEdit.enabled(), config.isGlobalOutputEnabled(),
                        config::setGlobalOutputEnabled);
                }
            } else if (edit instanceof FaceConfigurationEdit.ChannelEdit channelEdit) {
                if (channelEdit.field() == FaceConfigurationEdit.ChannelField.INPUT) {
                    changed = updateInt(channelEdit.channel(), config.linkConfig.getInputChannel(),
                        config::setInputChannel);
                } else {
                    changed = updateInt(channelEdit.channel(), config.linkConfig.getOutputChannel(),
                        config::setOutputChannel);
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
        LogisticsNode node() {
            return manager.createNodeFromKey(key);
        }
    }
}
