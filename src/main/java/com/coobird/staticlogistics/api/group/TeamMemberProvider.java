package com.coobird.staticlogistics.api.group;

import java.util.Set;
import java.util.UUID;

/** 可选团队集成为网络同步提供的成员解析端口。 */
@FunctionalInterface
public interface TeamMemberProvider {
    Set<UUID> membersOf(UUID playerId);

    static TeamMemberProvider none() {
        return ignored -> Set.of();
    }
}
