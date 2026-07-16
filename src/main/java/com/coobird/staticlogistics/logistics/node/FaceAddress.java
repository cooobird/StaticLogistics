package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * 单维度内无损的方块面地址。
 * 方块位置已经占满 64 位，因此方向不能继续安全塞入同一个 long。
 */
public record FaceAddress(long posLong, Direction face) {
    private static final char STORAGE_SEPARATOR = ':';

    public FaceAddress {
        Objects.requireNonNull(face, "Face address direction must not be null");
    }

    public static FaceAddress of(BlockPos pos, Direction face) {
        Objects.requireNonNull(pos, "Face address position must not be null");
        return new FaceAddress(pos.asLong(), face);
    }

    public static FaceAddress of(LogisticsNode node) {
        Objects.requireNonNull(node, "Face address node must not be null");
        return of(node.gPos().pos(), node.face());
    }

    public BlockPos pos() {
        return BlockPos.of(posLong);
    }

    public LogisticsNode toNode(ResourceKey<Level> dimension) {
        return new LogisticsNode(GlobalPos.of(dimension, pos()), face);
    }

    /**
     * 新存档键分别保存完整位置和方向编号。
     */
    public String storageKey() {
        return posLong + String.valueOf(STORAGE_SEPARATOR) + face.get3DDataValue();
    }

    /**
     * 读取无损复合键，同时兼容旧版有损 packed-long 键。
     */
    public static FaceAddress parseStorageKey(String value) {
        int separator = value.lastIndexOf(STORAGE_SEPARATOR);
        if (separator > 0 && separator < value.length() - 1) {
            long pos = Long.parseLong(value.substring(0, separator));
            int faceId = Integer.parseInt(value.substring(separator + 1));
            if (faceId < 0 || faceId >= Direction.values().length) {
                throw new IllegalArgumentException("Invalid face address direction: " + faceId);
            }
            return new FaceAddress(pos, Direction.from3DDataValue(faceId));
        }

        long legacyKey = Long.parseLong(value);
        int legacyFaceId = (int) (legacyKey & 0b111);
        if (legacyFaceId >= Direction.values().length) {
            throw new IllegalArgumentException("Invalid legacy face address direction: " + legacyFaceId);
        }
        return of(BlockPos.of(legacyKey >> 3), Direction.from3DDataValue(legacyFaceId));
    }
}
