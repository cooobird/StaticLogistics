package com.coobird.staticlogistics.api.group;

/** 分组名称与每位所有者的容量边界。 */
public final class GroupConstraints {
    public static final int MAX_NAME_LENGTH = 32;
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
}
