package com.coobird.staticlogistics.transfer;


/**
 * 传输类型选择掩码工具。
 *
 * <p>当前掩码会持久化到世界数据、物品组件和蓝图 NBT 中。
 * 在存储格式仍为 int 的阶段，所有位运算都集中放在这里。
 */
public final class TransferTypeMask {
    private TransferTypeMask() {
    }

    public static boolean hasLegacyBit(LogisticsResource<?> type) {
        return type.bitOffset() >= 0 && type.bitOffset() < Integer.SIZE;
    }

    public static int flag(LogisticsResource<?> type) {
        if (!hasLegacyBit(type)) {
            return 0;
        }
        return 1 << type.bitOffset();
    }

    public static boolean isSelected(int mask, LogisticsResource<?> type) {
        return (mask & flag(type)) != 0;
    }

}
