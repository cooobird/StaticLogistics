package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理传输游标：
 * - 顺序/一般轮询游标（每个节点每种类型一个 int[1]）
 * - 分组轮询游标（使用空传输类型对应的默认游标）
 *
 * <p>全服游标必须使用包含维度的完整节点身份；long 面键只允许用于维度级 LinkManager。
 * 所有状态均由服务器主线程访问。
 */
public class TransferCursorService {
    private static final ResourceLocation DEFAULT_KEY = StaticLogistics.asResource("default");

    private final Map<LogisticsNode, Map<ResourceLocation, int[]>> nodeCursors = new HashMap<>();

    /**
     * 获取指定节点和传输类型的游标数组（长度为1，可修改）。
     */
    public int[] getOrCreateCursor(LogisticsNode node, ResourceLocation cursorType) {
        if (node == null) throw new IllegalArgumentException("Cursor node must not be null");
        ResourceLocation key = cursorType != null ? cursorType : DEFAULT_KEY;
        return nodeCursors
            .computeIfAbsent(node, ignored -> new HashMap<>())
            .computeIfAbsent(key, t -> new int[]{0});
    }

    /**
     * 移除节点所有游标（节点注销时调用）
     */
    public void removeCursor(LogisticsNode node) {
        if (node == null) return;
        nodeCursors.remove(node);
    }
}

