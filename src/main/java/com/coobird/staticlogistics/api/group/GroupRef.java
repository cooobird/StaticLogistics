package com.coobird.staticlogistics.api.group;

/// 稳定分组身份及其用户可见名称。
public record GroupRef(GroupKey key, String displayName) {
    public GroupRef {
        displayName = displayName == null ? "" : displayName;
    }

}
