package com.coobird.staticlogistics.transfer;

/// 从运行时占用掩码中寻找第一个空闲位。
final class TypeBitAllocator {
    private TypeBitAllocator() {
    }

    static int firstFree(long occupied) {
        int offset = Long.numberOfTrailingZeros(~occupied);
        if (offset == Long.SIZE) throw new IllegalStateException("All 64 logistics resource slots are occupied");
        return offset;
    }
}
