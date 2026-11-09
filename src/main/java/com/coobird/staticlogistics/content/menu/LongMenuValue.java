package com.coobird.staticlogistics.content.menu;

import net.minecraft.world.inventory.DataSlot;

import java.util.function.Consumer;

/// 四段无符号 16 位同步，避免菜单数据包截断 64 位设置。
final class LongMenuValue {
    private long value;

    long get() {
        return value;
    }

    void set(long value) {
        this.value = value;
    }

    void register(Consumer<DataSlot> register) {
        for (int part = 0; part < 4; part++) {
            int shift = part * 16;
            register.accept(new DataSlot() {
                @Override
                public int get() {
                    return (int) (value >>> shift) & 0xffff;
                }

                @Override
                public void set(int chunk) {
                    value = (value & ~(0xffffL << shift)) | ((chunk & 0xffffL) << shift);
                }
            });
        }
    }
}
