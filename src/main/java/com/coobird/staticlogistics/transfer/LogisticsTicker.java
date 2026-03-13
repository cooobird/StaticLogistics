package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.core.TransferType;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class LogisticsTicker {

    private static final TransferType[] TYPES = TransferType.values();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            tick(serverLevel);
        }
    }

    private static void tick(ServerLevel level) {
        LinkManager manager = LinkManager.get(level);
        long gameTime = level.getGameTime();

        for (long sourceKey : manager.getAllSourceKeys()) {
            List<StaticLink> allLinks = manager.getLinksByKey(sourceKey);
            if (allLinks.isEmpty()) continue;

            BlockPos pos = BlockPos.of(sourceKey >> 3);
            Direction face = Direction.from3DDataValue((int) (sourceKey & 0x7));
            FaceConfig faceConfig = manager.getOrCreateFaceConfig(pos, face);

            boolean dimEffective = faceConfig.isDimensionEffective();
            List<StaticLink> validLinks = allLinks.stream()
                .filter(l -> !l.isCrossDim(level.dimension()) || dimEffective)
                .collect(Collectors.toList());

            if (validLinks.isEmpty()) continue;

            int combinedFlags = 0;
            for (StaticLink link : validLinks) {
                combinedFlags |= link.transferFlags();
            }

            int speedMult = faceConfig.getSpeedMultiplier();
            int baseInterval = 20;
            int finalInterval = (speedMult >= baseInterval) ? 1 : Math.max(1, baseInterval / speedMult);

            for (TransferType type : TYPES) {
                if ((combinedFlags & (1 << type.ordinal())) == 0) continue;

                FaceConfig.SideData sideData = faceConfig.getSettings(type);
                if (!sideData.mode.allowsOutput()) continue;

                if (type == TransferType.ENERGY) {
                    TransferEngine.execute(level, validLinks, type, faceConfig, sideData.rrCursor);
                } else if ((gameTime + sourceKey) % finalInterval == 0) {
                    TransferEngine.execute(level, validLinks, type, faceConfig, sideData.rrCursor);
                }
            }
        }
    }
}