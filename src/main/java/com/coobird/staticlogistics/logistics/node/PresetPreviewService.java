package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 预设预览的只读服务端快照；不创建节点、加载区块或修改配置。
public final class PresetPreviewService {
    public record Field(int editIndex, ChannelConfigurationEdit value, boolean available) {
    }

    public record Snapshot(LogisticsNode node, GroupKey group, long version, List<Field> fields) {
        public Snapshot {
            fields = List.copyOf(fields);
        }
    }

    private PresetPreviewService() {
    }

    @Nullable
    public static Snapshot snapshot(ServerPlayer player, LogisticsNode node, GroupKey group,
                                    NodeRole expectedRole, List<ChannelConfigurationEdit> requested) {
        var validated = new NodeMutationService().resolveRemote(player, node, group);
        if (validated == null || validated.config().determineRole() != expectedRole) return null;
        EndpointConfig config = validated.config();
        List<Field> fields = new ArrayList<>(requested.size());
        for (int index = 0; index < requested.size(); index++) {
            ChannelConfigurationEdit edit = requested.get(index);
            ChannelConfigurationEdit current;
            boolean available = true;
            if (edit instanceof ChannelConfigurationEdit.EnabledEdit) {
                current = new ChannelConfigurationEdit.EnabledEdit(config.linkConfig.isEnabled());
            } else if (edit instanceof ChannelConfigurationEdit.RedstoneEdit) {
                current = new ChannelConfigurationEdit.RedstoneEdit(config.linkConfig.getRedstoneMode());
            } else if (edit instanceof ChannelConfigurationEdit.NumberEdit number) {
                long value = number.field() == ChannelConfigurationEdit.NumberField.PRIORITY
                    ? config.linkConfig.getPriority() : config.linkConfig.getKeepStock();
                current = new ChannelConfigurationEdit.NumberEdit(number.field(), value);
            } else if (edit instanceof ChannelConfigurationEdit.StrategyEdit) {
                current = new ChannelConfigurationEdit.StrategyEdit(config.linkConfig.getStrategy());
            } else if (edit instanceof ChannelConfigurationEdit.ExtractionEdit) {
                current = new ChannelConfigurationEdit.ExtractionEdit(config.linkConfig.getExtractionMode());
            } else if (edit instanceof ChannelConfigurationEdit.SelectedTypesEdit) {
                current = new ChannelConfigurationEdit.SelectedTypesEdit(config.getSelectedTypeIds());
            } else if (edit instanceof ChannelConfigurationEdit.FilterEdit) {
                var stack = config.filterConfig.getUpgrades().getStackInSlot(0);
                available = stack.getItem() instanceof LogisticsUpgrade upgrade && upgrade.isFilterUpgrade();
                current = new ChannelConfigurationEdit.FilterEdit(available
                    ? stack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY) : FilterData.EMPTY);
            } else {
                return null;
            }
            fields.add(new Field(index, current, available));
        }
        return new Snapshot(node, group, config.getVersion(), fields);
    }
}
