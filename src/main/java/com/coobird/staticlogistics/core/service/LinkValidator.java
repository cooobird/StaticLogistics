package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class LinkValidator {

    public record Result(boolean success, @Nullable Component error) {
        public static Result ok() {
            return new Result(true, null);
        }

        public static Result fail(String translationKey, Object... args) {
            return new Result(false, Component.translatable(translationKey, args));
        }
    }

    public static Result canLink(LogisticsNode src, LogisticsNode dest, ContainerConfig config) {
        boolean sameDimension = src.gPos().dimension().equals(dest.gPos().dimension());
        if (!sameDimension && !config.isDimensionEffective()) {
            return Result.fail("msg.staticlogistics.no_dimension_upgrade");
        }

        boolean isInfinite = config.getRangeMultiplier() >= ContainerConfig.INFINITY_MARKER;

        if (sameDimension && !isInfinite) {
            double baseRadius = SLConfig.getDefaultRadius();
            double maxDistance = baseRadius * config.getRangeMultiplier();
            double distSq = src.gPos().pos().distSqr(dest.gPos().pos());

            if (distSq > maxDistance * maxDistance) {
                return Result.fail("msg.staticlogistics.out_of_range", (int) maxDistance);
            }
        }
        return Result.ok();
    }
}