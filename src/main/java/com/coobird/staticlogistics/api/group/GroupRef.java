package com.coobird.staticlogistics.api.group;

import java.util.Objects;
import java.util.UUID;

/** 稳定分组身份及其用户可见名称。 */
public record GroupRef(GroupKey key, String displayName) {
    public GroupRef {
        Objects.requireNonNull(key, "key");
        displayName = displayName == null ? "" : displayName;
    }

    public static GroupRef migrated(UUID ownerId, String legacyDisplayName) {
        return new GroupRef(GroupKey.migrated(ownerId, legacyDisplayName), legacyDisplayName);
    }
}
