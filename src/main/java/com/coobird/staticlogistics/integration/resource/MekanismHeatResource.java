package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logic.TransferRegistries;
import com.mojang.logging.LogUtils;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Mekanism 热量资源适配器。
 *
 * <p>通过 {@link LogisticsResource} 接口接入物流管线。
 * 热量使用 double，多电容按比例分配提取。
 */
public class MekanismHeatResource implements LogisticsResource<IHeatHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("mek_heat");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFFFF6600;
    }

    @Override
    public int bitOffset() {
        return 5;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.mek_heat";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(MekanismBlocks.RESISTIVE_HEATER.get());
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getMekHeatStack;
    }

    @Override
    public boolean isSimpleResource() {
        return true;
    }

    @Override
    public @Nullable IHeatHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(mekanism.common.capabilities.Capabilities.HEAT, pos, face);
    }

    @Override
    public long extract(IHeatHandler handle, long amount, boolean simulate) {
        try {
            int capacitorCount = handle.getHeatCapacitorCount();
            if (capacitorCount <= 0) return 0;

            // 计算总热量
            double totalHeat = 0;
            for (int i = 0; i < capacitorCount; i++) {
                totalHeat += handle.getTemperature(i) * handle.getHeatCapacity(i);
            }

            // 限制提取量为实际可用热量
            long actualAmount = Math.min(amount, (long) totalHeat);
            if (actualAmount <= 0) return 0;

            if (simulate) {
                return actualAmount;
            } else {
                double totalCapacity = handle.getTotalHeatCapacity();
                if (totalCapacity <= 0) return 0;
                for (int i = 0; i < capacitorCount; i++) {
                    double ratio = handle.getHeatCapacity(i) / totalCapacity;
                    double toExtract = actualAmount * ratio;
                    handle.handleHeat(i, -toExtract);
                }
                return actualAmount;
            }
        } catch (Exception e) {
            LOGGER.error("Mekanism heat extract failed", e);
            return 0;
        }
    }

    @Override
    public long insert(IHeatHandler handle, long amount, boolean simulate) {
        try {
            if (!simulate) {
                handle.handleHeat(amount);
            }
            return amount;
        } catch (Exception e) {
            LOGGER.error("Mekanism heat insert failed", e);
            return 0;
        }
    }

    public static void register() {
        TransferRegistries.registerAdapter(new MekanismHeatResource());
        LOGGER.info("Registered Mekanism heat transfer support");
    }
}
