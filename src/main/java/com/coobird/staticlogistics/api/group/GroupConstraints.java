package com.coobird.staticlogistics.api.group;

/**
 * 分组名称与每位所有者的容量边界。
 */
public final class GroupConstraints {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_CONNECTION_NAME_LENGTH = 48;
    public static final int MAX_GROUPS_PER_OWNER = 128;

    private GroupConstraints() {
    }

    public static String normalizeName(String value) {
        if (value == null) throw new IllegalArgumentException("Group name is required");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH
            || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid group name");
        }
        return normalized;
    }

    /**
     * 规范化连接显示名。空名称表示清除自定义名称，回退到本地化默认名称。
     */
    public static String normalizeConnectionName(String value) {
        if (value == null) throw new IllegalArgumentException("Connection name is required");
        String normalized = value.trim();
        if (normalized.length() > MAX_CONNECTION_NAME_LENGTH
            || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid connection name");
        }
        return normalized;
    }
}
