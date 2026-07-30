package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.TeamAccessPolicy;
import com.coobird.staticlogistics.api.group.TeamMemberProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 所有节点读取与修改权限的唯一判定入口。
 */
public final class PermissionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PermissionService INSTANCE = new PermissionService();
    private volatile TeamAccessPolicy teamAccessPolicy = TeamAccessPolicy.ownerOnly();
    private volatile TeamMemberProvider teamMemberProvider = TeamMemberProvider.none();

    private PermissionService() {
    }

    public static PermissionService getInstance() {
        return INSTANCE;
    }

    /**
     * 由可选集成层安装团队权限实现。
     */
    public void installTeamAccessPolicy(TeamAccessPolicy policy) {
        teamAccessPolicy = Objects.requireNonNull(policy, "Team access policy must not be null");
    }

    /**
     * 由可选集成层安装团队成员目录实现。
     */
    public void installTeamMemberProvider(TeamMemberProvider provider) {
        teamMemberProvider = Objects.requireNonNull(provider, "Team member provider must not be null");
    }

    /**
     * 返回团队同步目标；集成异常时安全退化为空集合。
     */
    public Set<UUID> teamMembersOf(UUID playerId) {
        try {
            return Set.copyOf(teamMemberProvider.membersOf(playerId));
        } catch (RuntimeException exception) {
            LOGGER.warn("Team member provider failed", exception);
            return Set.of();
        }
    }

    /**
     * 返回目录同步应包含的所有者，始终包含玩家本人。
     */
    public Set<UUID> accessibleDirectoryOwners(Player actor) {
        if (actor == null) return Set.of();
        UUID playerId = actor.getUUID();
        LinkedHashSet<UUID> owners = new LinkedHashSet<>();
        owners.add(playerId);
        for (UUID candidate : teamMembersOf(playerId)) {
            if (canAccess(candidate, actor)) owners.add(candidate);
        }
        return Set.copyOf(owners);
    }

    /**
     * 所有者或同一 FTB 团队及盟友可以读取。
     */
    public boolean canAccess(UUID owner, Player actor) {
        if (actor == null) return false;
        return GroupPermissionPolicy.canAccess(owner, actor.getUUID(),
            teamAccessPolicy::canAccess);
    }

    /**
     * 所有者或其 FTB 团队管理员可以修改。
     */
    public boolean canModify(UUID owner, Player actor) {
        if (actor == null) return false;
        return GroupPermissionPolicy.canModify(owner, actor.getUUID(),
            teamAccessPolicy::canModify);
    }
}
