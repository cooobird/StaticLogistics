package com.coobird.staticlogistics.api.group;

import java.util.UUID;

/**
 * 可选团队模组向 core 提供的权限判定端口。
 */
public interface TeamAccessPolicy {
    boolean canAccess(UUID ownerId, UUID actorId);

    boolean canModify(UUID ownerId, UUID actorId);

    static TeamAccessPolicy ownerOnly() {
        return new TeamAccessPolicy() {
            @Override
            public boolean canAccess(UUID ownerId, UUID actorId) {
                return false;
            }

            @Override
            public boolean canModify(UUID ownerId, UUID actorId) {
                return false;
            }
        };
    }
}
