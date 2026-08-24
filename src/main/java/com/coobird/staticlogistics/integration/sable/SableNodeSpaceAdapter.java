package com.coobird.staticlogistics.integration.sable;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Sable Companion 适配实现；仅在 ModList 检测到 Sable 后直接加载。
 */
final class SableNodeSpaceAdapter implements DynamicNodeSpace.Adapter {
    @Override
    public Vec3 toWorld(Level level, Vec3 position, float partialTick) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, position);
        return subLevel == null ? position : pose(subLevel, partialTick).transformPosition(position);
    }

    @Override
    public Vec3 normalToWorld(Level level, BlockPos position, Vec3 normal, float partialTick) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, position);
        return subLevel == null ? normal : pose(subLevel, partialTick).transformNormal(normal).normalize();
    }

    @Override
    public boolean isDynamic(Level level, BlockPos position) {
        return SableCompanion.INSTANCE.getContaining(level, position) != null;
    }

    @Override
    public double distanceSquared(Level level, Vec3 first, Vec3 second) {
        return SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level, first, second);
    }

    @Override
    public BlockPos findDynamicStoragePosition(
        Level level, BlockPos worldPosition, Predicate<BlockPos> predicate
    ) {
        return SableCompanion.INSTANCE.runIncludingSubLevels(
            level, (Position) Vec3.atCenterOf(worldPosition), false, null,
            (subLevel, candidate) -> subLevel != null && predicate.test(candidate)
                ? candidate : null);
    }

    private static Pose3dc pose(SubLevelAccess subLevel, float partialTick) {
        return subLevel instanceof ClientSubLevelAccess client
            ? client.renderPose(partialTick) : subLevel.logicalPose();
    }
}
