package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 跨维度所有权迁移的唯一事务入口。
 */
public final class OwnershipTransferService {
    private static final OwnershipMutationPermit MUTATION_PERMIT = new OwnershipMutationPermit();

    private final MinecraftServer server;
    private final GlobalLogisticsManager globalManager;

    public OwnershipTransferService(MinecraftServer server, GlobalLogisticsManager globalManager) {
        this.server = server;
        this.globalManager = globalManager;
    }

    public int transfer(CommandSourceStack source, UUID previousOwner,
                        ServerPlayer newOwner, @Nullable String selectedGroupName) {
        if (source == null || !source.hasPermission(2) || previousOwner == null || newOwner == null) {
            return 0;
        }
        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef selectedGroup = selectedGroupName == null
            ? null : store.findGroup(previousOwner, selectedGroupName);
        if (selectedGroupName != null && selectedGroup == null) return 0;

        List<Target> targets = new ArrayList<>();
        Set<GroupRef> groups = new LinkedHashSet<>();
        if (selectedGroup == null) groups.addAll(store.getGroupRefs(previousOwner));
        else groups.add(selectedGroup);

        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (FaceAddress key : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(key);
                if (config == null || !previousOwner.equals(config.faceConfig.getOwner())) continue;
                if (selectedGroup != null
                    && !config.faceConfig.getGroupKeys().contains(selectedGroup.key())) continue;
                if (selectedGroup != null && config.faceConfig.getGroupKeys().size() != 1) {
                    source.sendFailure(Component.literal(
                        "Cannot transfer one group from a face that belongs to multiple groups"));
                    return 0;
                }
                config.validateOwnershipTransfer(previousOwner);
                groups.addAll(config.faceConfig.getGroups());
                targets.add(new Target(config));
            }
        }

        Map<Target, CompoundTag> snapshots = new LinkedHashMap<>();
        try {
            store.validateGroupTransfer(previousOwner, newOwner.getUUID(), groups);
            for (Target target : targets) {
                snapshots.put(target, target.config().serializeNBT(server.registryAccess()).copy());
            }
            for (Target target : targets) {
                target.config().transferOwnership(MUTATION_PERMIT, newOwner.getUUID(),
                    newOwner.getGameProfile().getName(), newOwner.getGameProfile());
            }
            store.transferGroups(previousOwner, newOwner.getUUID(), groups);
            groups.forEach(group -> globalManager.retireGroupIdentity(group.key()));
            return targets.size();
        } catch (RuntimeException exception) {
            RuntimeException rollbackFailure = null;
            for (Map.Entry<Target, CompoundTag> snapshot : snapshots.entrySet()) {
                try {
                    snapshot.getKey().config().restoreOwnershipSnapshot(
                        MUTATION_PERMIT, server.registryAccess(), snapshot.getValue());
                    snapshot.getKey().config().markDirty();
                } catch (RuntimeException restoreException) {
                    if (rollbackFailure == null) {
                        rollbackFailure = new IllegalStateException("Ownership rollback failed");
                    }
                    rollbackFailure.addSuppressed(restoreException);
                }
            }
            String message = "Ownership transfer failed: " + exception.getMessage();
            if (rollbackFailure != null) message += " (rollback incomplete)";
            source.sendFailure(Component.literal(message));
            return 0;
        }
    }

    private record Target(FaceConfigComposite config) {
    }
}
