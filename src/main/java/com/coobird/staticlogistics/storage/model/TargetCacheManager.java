package com.coobird.staticlogistics.storage.model;

import com.coobird.staticlogistics.api.LogisticsNode;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 目标缓存管理器 —— 缓存排序后的目标列表，版本匹配时命中。
 *
 * <p>职责：
 * <ul>
 *   <li>缓存排序后的目标列表</li>
 *   <li>版本匹配检查</li>
 *   <li>缓存失效</li>
 * </ul>
 *
 * <p>线程安全：主线程单线程访问。
 */
public class TargetCacheManager {
    private List<LogisticsNode> cachedTargets = null;
    private long cacheVersion = -1;

    /**
     * 从缓存获取目标列表（版本匹配时命中）。
     * 返回内部引用——调用方只读遍历，不做修改。
     */
    @Nullable
    public List<LogisticsNode> getCachedTargets(long currentVersion) {
        if (cachedTargets != null && cacheVersion == currentVersion) {
            return cachedTargets;
        }
        return null;
    }

    /**
     * 设置目标缓存。直接缓存调用方提供的列表引用（调用方已创建新列表）。
     */
    public void setCachedTargets(List<LogisticsNode> targets, long currentVersion) {
        if (targets != null && !targets.isEmpty()) {
            this.cachedTargets = targets;
            this.cacheVersion = currentVersion;
        } else {
            clear();
        }
    }

    /**
     * 清空缓存。
     */
    public void clear() {
        this.cachedTargets = null;
        this.cacheVersion = -1;
    }
}
