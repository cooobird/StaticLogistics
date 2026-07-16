package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 传输日志门面，统一管理服务器会话内的最近日志、累计统计和速率。
 *
 * <p>所有入口都在服务器主线程调用。最近日志保留真实时间戳供命令显示，
 * 限频、速率及 Jade 新鲜度统一使用服务器游戏刻，不受系统时钟调整影响。
 */
public class TransferLogManager {
    private static final Map<MinecraftServer, TransferLogManager> INSTANCES = new HashMap<>();
    private static final long FAILURE_LOG_INTERVAL_TICKS = 20L;
    private static final int MAX_TRACKED_FAILURES = 1_024;

    public static TransferLogManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, TransferLogManager::new);
    }

    public static void release(MinecraftServer server) {
        TransferLogManager removed = INSTANCES.remove(server);
        if (removed != null) removed.reset();
    }

    private final MinecraftServer server;
    private final TransferRecentLog recentLog = new TransferRecentLog();
    private final TransferStats stats = new TransferStats();
    private final TransferRateCalculator rateCalculator = new TransferRateCalculator();
    private final Map<FailureLogKey, Long> failureLogTicks = new LinkedHashMap<>(16, 0.75F, true);
    private long lastTransferTick = -1;

    private TransferLogManager(MinecraftServer server) {
        this.server = server;
    }

    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            LogisticsResource<?> type, long amount, boolean success) {
        logTransfer(source, target, type, amount, success, null);
    }

    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            LogisticsResource<?> type, long amount, boolean success,
                            TransferFailureReason reason) {
        long gameTick = currentTick();
        if (!success) stats.incrementFailed();
        if (!success && reason != null
            && shouldSuppressFailure(source, target, type, reason, gameTick)) return;

        TransferEntry entry = TransferEntry.obtain(
            System.currentTimeMillis(),
            source.gPos().dimension().location().toString(),
            source.gPos().pos().getX(), source.gPos().pos().getY(), source.gPos().pos().getZ(),
            source.face().getName(),
            target.gPos().dimension().location().toString(),
            target.gPos().pos().getX(), target.gPos().pos().getY(), target.gPos().pos().getZ(),
            target.face().getName(),
            type.typeId().toString(), type.color(),
            amount, success, reason != null ? reason.id().toString() : null
        );
        recentLog.add(entry);

        if (!success) {
            return;
        }
        stats.incrementTotal(amount);
        stats.recordType(type.typeId().toString(), amount);
        stats.recordSource(source, amount, gameTick);
        stats.recordTarget(target, amount, gameTick);
        rateCalculator.record(amount, gameTick);
        lastTransferTick = gameTick;
    }

    /** 对同一链路、类型和原因的连续失败按游戏刻限频。 */
    private boolean shouldSuppressFailure(LogisticsNode source, LogisticsNode target,
                                          LogisticsResource<?> type, TransferFailureReason reason,
                                          long gameTick) {
        FailureLogKey key = new FailureLogKey(source, target, type.typeId(), reason.id());
        Long previous = failureLogTicks.get(key);
        if (previous != null && gameTick - previous < FAILURE_LOG_INTERVAL_TICKS) return true;
        if (previous == null && failureLogTicks.size() >= MAX_TRACKED_FAILURES) {
            var iterator = failureLogTicks.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        failureLogTicks.put(key, gameTick);
        return false;
    }

    private long currentTick() {
        return server.overworld().getGameTime();
    }

    public List<TransferEntry> getRecent(int count) {
        return recentLog.getRecent(count);
    }

    public int getLogSize() {
        return recentLog.size();
    }

    public long getTotalTransfers() {
        return stats.getTotalTransfers();
    }

    public long getTotalAmount() {
        return stats.getTotalAmount();
    }

    public long getFailedTransfers() {
        return stats.getFailedTransfers();
    }

    public Map<String, TypeStats> getPerTypeStats() {
        return stats.getPerTypeStats();
    }

    public NodeStats getPerNodeStats(LogisticsNode node) {
        return stats.getPerNodeStats(node, currentTick());
    }

    public List<Map.Entry<LogisticsNode, NodeStats>> getTopNodes(int n, boolean bySent) {
        return stats.getTopNodes(n, bySent);
    }

    public double getTransfersPerMinute() {
        return rateCalculator.getTransfersPerMinute(currentTick());
    }

    public double getAmountPerMinute() {
        return rateCalculator.getAmountPerMinute(currentTick());
    }

    /** 返回上次成功传输距今的毫秒数；无记录时返回 -1。 */
    public long getTimeSinceLastTransfer() {
        if (lastTransferTick < 0) return -1;
        return Math.max(0, currentTick() - lastTransferTick) * 50L;
    }

    public void reset() {
        recentLog.clear();
        stats.clear();
        rateCalculator.clear();
        failureLogTicks.clear();
        lastTransferTick = -1;
    }

    private record FailureLogKey(LogisticsNode source, LogisticsNode target, ResourceLocation typeId,
                                 ResourceLocation reasonId) {
    }
}
