package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置界面和世界覆盖层使用的红石控制运行态投影。
 */
@OnlyIn(Dist.CLIENT)
public enum ClientRedstoneControlData {
    INSTANCE;

    private final Map<ConnectionKey, State> states = new ConcurrentHashMap<>();
    private volatile SelectedControlGroup selectedControlGroup;

    public void accept(ConnectionKey connection, @Nullable RedstoneControlBinding binding,
                       boolean powered, boolean allowed) {
        if (binding != null) {
            states.replaceAll((key, state) -> binding.equals(state.binding())
                ? new State(binding, powered, allowed) : state);
        }
        states.put(connection, new State(binding, powered, allowed));
    }

    @Nullable
    public State get(ConnectionKey connection) {
        return connection == null ? null : states.get(connection);
    }

    public void invalidate() {
        states.clear();
        selectedControlGroup = null;
    }

    public void selectControlGroup(GroupKey groupKey, RedstoneControlBinding binding) {
        selectedControlGroup = groupKey == null || binding == null
            ? null : new SelectedControlGroup(groupKey, binding);
    }

    public boolean isControlGroupSelected(GroupKey groupKey,
                                          RedstoneControlBinding binding) {
        return selectedControlGroup != null
            && selectedControlGroup.groupKey().equals(groupKey)
            && selectedControlGroup.binding().equals(binding);
    }

    @Nullable
    public ControlGroup getSelectedControlGroup(GroupKey groupKey) {
        SelectedControlGroup selected = selectedControlGroup;
        if (selected == null || groupKey == null
            || !selected.groupKey().equals(groupKey)) return null;
        return getControlGroups(groupKey).stream()
            .filter(group -> group.binding().equals(selected.binding()))
            .findFirst().orElse(null);
    }

    public List<ControlGroup> getControlGroups(GroupKey groupKey) {
        if (groupKey == null) return List.of();
        Map<RedstoneControlBinding, List<Map.Entry<ConnectionKey, State>>> grouped =
            new LinkedHashMap<>();
        states.entrySet().stream()
            .filter(entry -> groupKey.equals(entry.getKey().groupKey())
                && entry.getValue().binding() != null)
            .forEach(entry -> grouped
                .computeIfAbsent(entry.getValue().binding(), ignored -> new ArrayList<>())
                .add(entry));
        List<ControlGroup> result = new ArrayList<>();
        grouped.forEach((binding, entries) -> {
            State state = entries.get(0).getValue();
            result.add(new ControlGroup(binding, state.powered(), state.allowed(),
                entries.stream().map(Map.Entry::getKey).toList()));
        });
        return List.copyOf(result);
    }

    public void acceptGroupPage(GroupKey groupKey, boolean reset,
                                List<GroupEntry> entries) {
        if (reset) states.keySet().removeIf(key -> key.groupKey().equals(groupKey));
        entries.forEach(entry -> states.put(entry.connection(), entry.state()));
    }

    public void acceptSignals(GroupKey groupKey, List<SignalUpdate> updates) {
        if (groupKey == null || updates.isEmpty()) return;
        Map<RedstoneControlBinding, SignalUpdate> byBinding = new LinkedHashMap<>();
        updates.forEach(update -> byBinding.put(update.binding(), update));
        states.replaceAll((connection, state) -> {
            if (!groupKey.equals(connection.groupKey()) || state.binding() == null) return state;
            SignalUpdate update = byBinding.get(state.binding());
            return update == null ? state : new State(
                state.binding(), update.powered(), update.allowed());
        });
    }

    public record GroupEntry(ConnectionKey connection, State state) {
    }

    public record SignalUpdate(RedstoneControlBinding binding,
                               boolean powered, boolean allowed) {
    }

    public record ControlGroup(RedstoneControlBinding binding, boolean powered,
                               boolean allowed, List<ConnectionKey> connections) {
        public ControlGroup {
            connections = List.copyOf(connections);
        }
    }

    private record SelectedControlGroup(GroupKey groupKey,
                                        RedstoneControlBinding binding) {
    }

    public record State(@Nullable RedstoneControlBinding binding,
                        boolean powered, boolean allowed) {
        public boolean bound() {
            return binding != null;
        }
    }
}
