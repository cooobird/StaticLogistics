package com.coobird.staticlogistics.integration.sable;

import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.integration.create.CreateContraptionService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.function.Predicate;

/**
 * 将节点的存储坐标投影到真实世界坐标。Sable 未安装时退化为恒等变换。
 */
public final class DynamicNodeSpace {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Adapter ADAPTER = loadAdapter();

    private DynamicNodeSpace() {
    }

    public static Vec3 toWorld(Level level, Vec3 position, float partialTick) {
        Vec3 projected = ModCompat.isCreateLoaded()
            ? CreateContraptionService.toWorld(level, position, partialTick) : position;
        return ADAPTER.toWorld(level, projected, partialTick);
    }

    public static Vec3 blockCenter(Level level, BlockPos position, float partialTick) {
        return toWorld(level, Vec3.atCenterOf(position), partialTick);
    }

    public static Vec3 faceNormal(Level level, BlockPos position,
                                  net.minecraft.core.Direction face, float partialTick) {
        return vectorToWorld(level, position, Vec3.atLowerCornerOf(face.getNormal()), partialTick);
    }

    /**
     * 将节点局部方向旋转到世界空间，并保留动态结构完整的滚转角。
     */
    public static Vec3 vectorToWorld(Level level, BlockPos position, Vec3 vector, float partialTick) {
        Vec3 transformed = vector;
        Vec3 projectedCenter = Vec3.atCenterOf(position);
        if (ModCompat.isCreateLoaded()) {
            transformed = CreateContraptionService.rotateNormal(level, position, transformed, partialTick);
            projectedCenter = CreateContraptionService.toWorld(level, projectedCenter, partialTick);
        }
        return ADAPTER.normalToWorld(
            level, BlockPos.containing(projectedCenter), transformed, partialTick).normalize();
    }

    public static Vec3 faceAxis1(Level level, BlockPos position,
                                 net.minecraft.core.Direction face, float partialTick) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 seed = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 axis2 = normal.cross(seed).normalize();
        Vec3 axis1 = normal.cross(axis2).normalize();
        return vectorToWorld(level, position, axis1, partialTick);
    }

    public static Vec3 faceAxis2(Level level, BlockPos position,
                                 net.minecraft.core.Direction face, float partialTick) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 seed = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return vectorToWorld(level, position, normal.cross(seed).normalize(), partialTick);
    }

    public static boolean isDynamic(Level level, BlockPos position) {
        return (ModCompat.isCreateLoaded() && CreateContraptionService.isMounted(level, position))
            || ADAPTER.isDynamic(level, position);
    }

    public static boolean isCreateContraption(Level level, BlockPos position) {
        return ModCompat.isCreateLoaded() && CreateContraptionService.isMounted(level, position);
    }

    public static double distanceSquared(Level level, BlockPos first, BlockPos second) {
        return blockCenter(level, first, 1.0F).distanceToSqr(blockCenter(level, second, 1.0F));
    }

    public static BlockPos findDynamicStoragePosition(
        Level level, BlockPos worldPosition, Predicate<BlockPos> predicate
    ) {
        return ADAPTER.findDynamicStoragePosition(level, worldPosition, predicate);
    }

    private static Adapter loadAdapter() {
        if (!ModCompat.isSableLoaded()) return Adapter.VANILLA;
        try {
            Adapter adapter = new SableNodeSpaceAdapter();
            LOGGER.info("Static Logistics: Sable dynamic node-space integration enabled.");
            return adapter;
        } catch (LinkageError exception) {
            LOGGER.error("Static Logistics: Failed to initialize Sable integration; using vanilla coordinates.", exception);
            return Adapter.VANILLA;
        }
    }

    interface Adapter {
        Adapter VANILLA = new Adapter() {
        };

        default Vec3 toWorld(Level level, Vec3 position, float partialTick) {
            return position;
        }

        default Vec3 normalToWorld(Level level, BlockPos position, Vec3 normal, float partialTick) {
            return normal;
        }

        default boolean isDynamic(Level level, BlockPos position) {
            return false;
        }

        default double distanceSquared(Level level, Vec3 first, Vec3 second) {
            return first.distanceToSqr(second);
        }

        default BlockPos findDynamicStoragePosition(
            Level level, BlockPos worldPosition, Predicate<BlockPos> predicate
        ) {
            return null;
        }
    }
}
