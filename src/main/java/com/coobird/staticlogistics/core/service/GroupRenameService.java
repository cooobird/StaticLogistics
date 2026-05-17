package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 组重命名服务——遍历所有维度中属于旧组 ID 的配置，批量改为新组 ID。
 */
public class GroupRenameService {
    private final PermissionService permissionService;
    private final GlobalLogisticsManager globalManager;

    public GroupRenameService(PermissionService permissionService, GlobalLogisticsManager globalManager) {
        this.permissionService = permissionService;
        this.globalManager = globalManager;
    }

    // 遍历所有维度的所有面配置，将匹配旧组 ID 的配置改为新组 ID
    public void renameGroup(Level level, Player player, String oldId, String newId) {
        if (oldId.equals(newId) || newId.isEmpty()) return;

        MinecraftServer server = level.getServer();
        if (server == null) return;

        for (ServerLevel serverLevel : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(serverLevel);

            boolean changedInLevel = false;
            for (long key : mgr.getAllConfigKeys()) {
                FaceConfigComposite config = mgr.getFaceConfig(key);

                if (config != null && config.faceConfig.getGroupId().equals(oldId) && permissionService.canModify(config.faceConfig.getOwner(), player)) {

                    config.faceConfig.setGroupId(newId);

                    BlockPos pos = BlockPos.of(key >> 3);
                    Direction face = Direction.from3DDataValue((int) (key & 0x7));
                    mgr.refreshLocalCache(key, pos, face, config);
                    mgr.syncConfigToClients(pos);

                    changedInLevel = true;
                }
            }

            if (changedInLevel) {
                mgr.markDirtyBatch(() -> {
                });
            }
        }

        if (level instanceof ServerLevel sl) {
            globalManager.syncGroupLinks(sl, oldId, null);
            globalManager.syncGroupLinks(sl, newId, null);
        }
    }
}