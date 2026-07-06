package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.config.SLConfig;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Ars Nouveau 魔源资源适配�?(Forge 1.20.1)�? * <p>
 * 物流系统直接检�?BlockEntity 是否实现 ISourceTile，然后调用其方法�?
 */
public class ArsSourceResource implements LogisticsResource<ISourceTile> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("ars_source");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFF8000FF;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.ars_source";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(ItemsRegistry.SOURCE_GEM.get());
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getArsSourceStack;
    }

    @Override
    public boolean isSimpleResource() {
        return true;
    }

    @Override
    public @Nullable ISourceTile resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ISourceTile source) {
            return source;
        }
        return null;
    }

    @Override
    public long extract(ISourceTile handle, long amount, boolean simulate) {
        try {
            int available = handle.getSource();
            if (available <= 0) return 0;
            int actual = (int) Math.min(available, amount);
            if (!simulate) {
                handle.removeSource(actual);
            }
            return actual;
        } catch (Exception e) {
            LOGGER.error("Ars source extract failed", e);
            return 0;
        }
    }

    @Override
    public long insert(ISourceTile handle, long amount, boolean simulate) {
        try {
            int space = handle.getMaxSource() - handle.getSource();
            if (space <= 0) return 0;
            int actual = (int) Math.min(space, amount);
            if (!simulate) {
                handle.addSource(actual);
            }
            return actual;
        } catch (Exception e) {
            LOGGER.error("Ars source insert failed", e);
            return 0;
        }
    }
}
