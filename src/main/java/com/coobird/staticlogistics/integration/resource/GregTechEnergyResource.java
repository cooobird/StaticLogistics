package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * GregTech EU 能量资源适配器 (Forge 1.20.1)。
 *
 * <p>通过 {@code GTCapabilityHelper.getEnergyContainer()} 获取 {@link IEnergyContainer}。
 * GregTech 的 EU 系统使用 long 值，电压/安培概念。
 *
 * <p>设计决策：
 * <ul>
 *   <li>baseStackSizeSupplier 返回配置的基础 EU/t，由升级倍率放大</li>
 *   <li>实际传输量取 min(升级后限制, 设备能力)</li>
 *   <li>电压/电流完全由接入端设备决定，不额外控制</li>
 * </ul>
 */
public class GregTechEnergyResource implements LogisticsResource<IEnergyContainer> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("gtceu_energy");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFFFF6600;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.gtceu_energy";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> {
            try {
                var itemSupplier = TagPrefix.cableGtQuadruple.getItemFromTable(GTMaterials.Tritanium);
                if (itemSupplier != null) {
                    return new ItemStack(itemSupplier.get().asItem());
                }
            } catch (Exception ignored) {
            }
            return new ItemStack(Items.REDSTONE);
        };
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getGTCEUStack;
    }

    @Override
    public boolean requiresCooldown() {
        return false;
    }

    @Override
    public boolean requiresValidLinks() {
        return false;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactSimulationOnly();
    }

    @Override
    public @Nullable IEnergyContainer resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        IEnergyContainer cap = com.coobird.staticlogistics.transfer.CapabilityCache.get(
            level, pos, face, GTCapability.CAPABILITY_ENERGY_CONTAINER);
        if (cap != null) return cap;
        if (be instanceof CableBlockEntity cable) {
            return cable.getEnergyContainer(face);
        }
        return null;
    }

    @Override
    public long extract(IEnergyContainer handle, long amount, boolean simulate) {
        try {
            long stored = handle.getEnergyStored();
            if (stored <= 0) return 0;
            long actual = Math.min(stored, amount);
            if (actual <= 0) return 0;
            if (!simulate) {
                handle.changeEnergy(-actual);
            }
            return actual;
        } catch (Exception e) {
            LOGGER.error("GregTech EU extract failed", e);
            return 0;
        }
    }

    @Override
    public long insert(IEnergyContainer handle, long amount, boolean simulate) {
        try {
            long canInsert = handle.getEnergyCanBeInserted();
            if (canInsert <= 0) return 0;
            long actual = Math.min(canInsert, amount);
            if (actual <= 0) return 0;
            if (!simulate) {
                handle.changeEnergy(actual);
            }
            return actual;
        } catch (Exception e) {
            LOGGER.error("GregTech EU insert failed", e);
            return 0;
        }
    }
}
