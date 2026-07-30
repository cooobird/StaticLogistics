package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 配置器与蓝图共用的分组/连接树数据模型。
 *
 * <p>本类只负责过滤、稳定排序和展开后的行投影，不包含重命名、删除等界面动作。
 */
public final class GroupConnectionTreeModel {
    public static final Comparator<GroupRef> GROUP_ORDER = (first, second) -> {
        String firstName = first.displayName();
        String secondName = second.displayName();
        boolean firstNumber = isDecimal(firstName);
        boolean secondNumber = isDecimal(secondName);
        if (firstNumber && secondNumber) {
            try {
                int byNumber = Integer.compare(
                    Integer.parseInt(firstName),
                    Integer.parseInt(secondName));
                if (byNumber != 0) return byNumber;
            } catch (NumberFormatException ignored) {
                // 超长数字按普通名称排序。
            }
        } else if (firstNumber != secondNumber) {
            return firstNumber ? -1 : 1;
        }
        int byName = firstName.compareToIgnoreCase(secondName);
        return byName != 0
            ? byName
            : first.key().ownerId().compareTo(second.key().ownerId());
    };

    private GroupConnectionTreeModel() {
    }

    public static List<GroupRef> filterAndSort(
        Collection<GroupRef> groups,
        String searchTerm
    ) {
        String filter = searchTerm.trim().toLowerCase(Locale.ROOT);
        return groups.stream()
            .filter(group -> !group.displayName().isEmpty())
            .filter(group -> group.displayName()
                .toLowerCase(Locale.ROOT).contains(filter))
            .sorted(GROUP_ORDER)
            .toList();
    }

    public static List<Row> buildRows(
        List<GroupRef> groups,
        Set<GroupKey> expandedGroups
    ) {
        List<Row> rows = new ArrayList<>();
        for (GroupRef group : groups) {
            rows.add(new GroupRow(group));
            if (!expandedGroups.contains(group.key())) continue;
            int displayIndex = 1;
            for (ClientConnection connection :
                ClientLinkData.INSTANCE.getConnectionsForGroup(group.key())) {
                rows.add(new ConnectionRow(
                    group, connection, displayIndex++));
            }
        }
        return List.copyOf(rows);
    }

    private static boolean isDecimal(String value) {
        if (value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    public sealed interface Row permits GroupRow, ConnectionRow {
        GroupRef group();

        @Nullable
        ClientConnection connection();

        int connectionIndex();
    }

    public record GroupRow(GroupRef group) implements Row {
        @Override
        public ClientConnection connection() {
            return null;
        }

        @Override
        public int connectionIndex() {
            return 0;
        }
    }

    public record ConnectionRow(
        GroupRef group,
        ClientConnection connection,
        int connectionIndex
    ) implements Row {
    }
}
