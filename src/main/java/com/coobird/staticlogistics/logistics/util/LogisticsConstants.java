package com.coobird.staticlogistics.logistics.util;

import com.coobird.staticlogistics.config.SLConfig;

/// 提供物流配置参数和界面常量。
public final class LogisticsConstants {

    private LogisticsConstants() {
    }

    /// 缓存相关常量
    /// 控制各种缓存的大小和行为，平衡内存使用和性能
    /// 这些参数可在配置文件中调整
    public static final class Cache {

        private Cache() {
        }

        /// 活跃提供者索引的预期容量，仅用于初始化哈希表，不限制实际节点数量。
        public static int getExpectedProviderCount() {
            return SLConfig.getCacheProviderSize();
        }

    }

    /// 网络同步相关常量
    /// 控制服务器与客户端之间的数据同步行为
    /// 这些参数可在配置文件中调整
    public static final class Network {

        private Network() {
        }

        /// 批量同步的最大条目数
        public static int getMaxBulkEntries() {
            return SLConfig.getNetworkMaxBulkEntries();
        }
    }

    /// 性能优化相关常量
    /// 控制物流系统的性能优化策略，平衡响应速度和资源消耗
    /// 游戏刻说明：
    /// Minecraft 每秒运行 20 tick，1 tick = 0.05 秒
    /// 这些参数可在配置文件中调整
    public static final class Performance {

        private Performance() {
        }

        /// 物流定时器每批处理的节点数量
        public static int getTickerBatchSize() {
            return SLConfig.getPerfTickerBatchSize();
        }

        /// 全服务器物流调度每 Tick 共享的软时间预算。
        public static long getTickerTimeBudgetNanos() {
            return SLConfig.getPerfTickerTimeBudgetNanos();
        }

        /// 默认冷却时间（游戏刻）
        public static int getDefaultCooldownTicks() {
            return SLConfig.getPerfDefaultCooldown();
        }

    }

}
