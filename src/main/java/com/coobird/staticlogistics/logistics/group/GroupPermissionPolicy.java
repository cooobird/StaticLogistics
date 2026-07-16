package com.coobird.staticlogistics.logistics.group;

import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * 与游戏对象无关的分组权限规则。
 */
public final class GroupPermissionPolicy {
    private GroupPermissionPolicy() {
    }

    public static boolean canAccess(UUID owner, UUID actor,
                                    BiPredicate<UUID, UUID> allied) {
        if (owner == null || actor == null) return false;
        return owner.equals(actor) || allied.test(owner, actor);
    }

    public static boolean canModify(UUID owner, UUID actor,
                                    BiPredicate<UUID, UUID> administrator) {
        if (owner == null || actor == null) return false;
        return owner.equals(actor) || administrator.test(owner, actor);
    }
}
