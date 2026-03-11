package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.core.TransferType;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public class LogisticsTicker {

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
            List<StaticLink> links = manager.getLinksByKey(sourceKey);
            if (links == null || links.isEmpty()) continue;

            // 核心：遍历所有可能的传输类型
            for (TransferType type : TransferType.values()) {
                // 获取该接口下，针对特定类型的配置（比如物品有物品的频率，流体有流体的频率）
                TransferSettings settings = manager.getSettings(sourceKey, type);

                // 检查该类型的 Tick 频率
                if (gameTime % Math.max(1, settings.interval()) == 0) {
                    // 执行搬运：这里只传入包含该 type 的 link 列表
                    TransferEngine.execute(level, links, type, settings);
                }
            }
        }
    }
}