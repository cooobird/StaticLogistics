package com.coobird.staticlogistics.util;

import com.coobird.staticlogistics.config.SLConfig;

/**
 * 物流系统常量定义类
 * 包含缓存、网络、性能、存储、线程和UI等模块的配置常量
 * <p>
 * 注意：性能、缓存、网络相关的参数已迁移到配置文件，玩家可以在服务器配置中调整
 */
public final class LogisticsConstants {

    private LogisticsConstants() {
    }

    /**
     * 缓存相关常量
     * 控制各种缓存的大小和行为，平衡内存使用和性能
     * <p>
     * 这些参数可在配置文件中调整
     */
    public static final class Cache {

        private Cache() {
        }

        /**
         * 提供者缓存最大条目数
         */
        public static int getProviderCacheSize() {
            return SLConfig.getCacheProviderSize();
        }

        /**
         * 缓存加载因子
         */
        public static float getCacheLoadFactor() {
            return SLConfig.getCacheLoadFactor();
        }

        /**
         * 单个面的目标缓存最大条目数
         */
        public static int getTargetCacheSize() {
            return SLConfig.getCacheTargetSize();
        }

        /**
         * 全局目标缓存最大条目数
         */
        public static int getGlobalTargetCacheSize() {
            return SLConfig.getCacheGlobalTargetSize();
        }
    }

    /**
     * 网络同步相关常量
     * 控制服务器与客户端之间的数据同步行为
     * <p>
     * 这些参数可在配置文件中调整
     */
    public static final class Network {

        private Network() {
        }

        /**
         * 批量同步的最大条目数
         */
        public static int getMaxBulkEntries() {
            return SLConfig.getNetworkMaxBulkEntries();
        }
    }

    /**
     * 性能优化相关常量
     * 控制物流系统的性能优化策略，平衡响应速度和资源消耗
     * <p>
     * 游戏刻说明：
     * Minecraft 每秒运行 20 tick，1 tick = 0.05 秒
     * <p>
     * 这些参数可在配置文件中调整
     */
    public static final class Performance {

        private Performance() {
        }

        /**
         * 物流定时器每批处理的节点数量
         */
        public static int getTickerBatchSize() {
            return SLConfig.getPerfTickerBatchSize();
        }

        /**
         * 冷却管理器的清理间隔（游戏刻）
         */
        public static int getCleanIntervalTicks() {
            return SLConfig.getPerfCleanInterval();
        }

        /**
         * 默认冷却时间（游戏刻）
         */
        public static int getDefaultCooldownTicks() {
            return SLConfig.getPerfDefaultCooldown();
        }

        /**
         * 冷却管理器完整清理间隔（游戏刻）
         */
        public static int getFullCleanIntervalTicks() {
            return SLConfig.getPerfCleanInterval();
        }

        /**
         * 批量清理的阈值
         */
        public static int getBatchCleanThreshold() {
            return SLConfig.getPerfBatchCleanThreshold();
        }

        /**
         * 批量清理时每次清理的条目数
         */
        public static int getBatchCleanSize() {
            return SLConfig.getPerfBatchCleanSize();
        }

        /**
         * 传输上下文对象池的最大大小
         */
        public static int getTransferContextPoolSize() {
            return SLConfig.getPerfContextPoolSize();
        }
    }

    /**
     * 存储相关常量
     * 控制物流链接的存储和位运算操作
     */
    public static final class Storage {

        private Storage() {
        }

        /**
         * 方向位数（用于位运算）
         * Minecraft 有 6 个方向，需要 3 位表示
         */
        public static final int FACE_BITS = 3;

        /**
         * 方向掩码（0x7 = 二进制 111）
         * 用于提取编码后的方向部分
         */
        public static final int FACE_MASK = 0x7;
    }

    /**
     * 线程管理相关常量
     * 控制后台线程的关闭行为
     */
    public static final class Thread {

        private Thread() {
        }

        /**
         * 正常关闭超时时间（秒）
         * 等待任务完成的超时时间，超时后尝试强制关闭
         */
        public static final long SHUTDOWN_TIMEOUT_SECONDS = 5L;

        /**
         * 强制关闭超时时间（秒）
         * 强制关闭后等待线程终止的超时时间
         */
        public static final long FORCE_SHUTDOWN_TIMEOUT_SECONDS = 2L;
    }

    /**
     * 用户界面相关常量
     * 控制客户端 UI 的交互行为和显示效果
     */
    public static final class UI {

        private UI() {
        }

        /**
         * 双击判定阈值（毫秒）
         * 两次点击的时间间隔小于此值时，判定为双击
         */
        public static final long DOUBLE_CLICK_THRESHOLD_MS = 250L;

        /**
         * 每页显示的策略数量
         */
        public static final int STRATEGIES_PER_PAGE = 8;
    }
}