package com.coobird.staticlogistics.logic;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.TransferProvider;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.transfer.handler.impl.EnergyTransferHandler;
import com.coobird.staticlogistics.transfer.handler.impl.FluidTransferHandler;
import com.coobird.staticlogistics.transfer.handler.impl.item.ItemTransferHandler;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class TransferRegistries {
    private static final Map<ResourceLocation, TransferType> TYPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ITransferHandler> HANDLERS = new LinkedHashMap<>();

    public static TransferType ITEM;
    public static TransferType FLUID;
    public static TransferType ENERGY;

    public static void init() {
        ITEM = registerInternal("item", 0xFFFFFFFF, 0, Capabilities.ItemHandler.BLOCK,
            SLConfig::getItemStack, () -> new ItemStack(Items.IRON_INGOT));

        FLUID = registerInternal("fluid", 0xFF3366FF, 1, Capabilities.FluidHandler.BLOCK,
            SLConfig::getFluidStack, () -> new ItemStack(Items.WATER_BUCKET));

        ENERGY = registerInternal("energy", 0xFFFFFF00, 2, Capabilities.EnergyStorage.BLOCK,
            SLConfig::getEnergyStack, () -> new ItemStack(Items.REDSTONE), false, false);

        registerHandler(ITEM, ItemTransferHandler.INSTANCE);
        registerHandler(FLUID, FluidTransferHandler.INSTANCE);
        registerHandler(ENERGY, EnergyTransferHandler.INSTANCE);
    }

    private static TransferType registerInternal(String name, int color, int offset,
                                                 BlockCapability<?, Direction> cap,
                                                 IntSupplier sizeSupplier, Supplier<ItemStack> icon) {
        return registerInternal(name, color, offset, cap, sizeSupplier, icon, true, true);
    }

    private static TransferType registerInternal(String name, int color, int offset,
                                                 BlockCapability<?, Direction> cap,
                                                 IntSupplier sizeSupplier, Supplier<ItemStack> icon,
                                                 boolean requiresCooldown, boolean requiresValidLinks) {
        ResourceLocation id = StaticLogistics.asResource(name);
        TransferType type = new TransferType(id, color, offset, "transfer_type.staticlogistics." + name,
            cap, sizeSupplier, icon, null, requiresCooldown, requiresValidLinks);
        TYPES.put(id, type);
        return type;
    }

    @Nullable
    public static TransferType get(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static void registerHandler(TransferType type, ITransferHandler handler) {
        HANDLERS.put(type.id(), handler);
    }

    public static void registerExternal(TransferType type, ITransferHandler handler) {
        TYPES.put(type.id(), type);
        HANDLERS.put(type.id(), handler);
    }

    /**
     * 通过 TransferProvider 注册传输类型 —— 最简集成方式。
     * <p>
     * 第三方模组只需实现 {@link TransferProvider} 接口，不需要创建 TransferType 和 ITransferHandler。
     *
     * @param id                    唯一标识（如 "mymod:my_resource"）
     * @param color                 显示颜色（ARGB）
     * @param bitOffset             位掩码偏移（用于 selectedTypesMask）
     * @param translationKey        翻译键
     * @param provider              传输提供者
     * @param baseStackSizeSupplier 基础传输量
     */
    public static <C> void registerProvider(ResourceLocation id, int color, int bitOffset,
                                            String translationKey, TransferProvider<C> provider,
                                            IntSupplier baseStackSizeSupplier) {
        TransferType type = new TransferType(id, color, bitOffset, translationKey,
            null, baseStackSizeSupplier, () -> new ItemStack(Items.PAPER),
            (level, pos) -> provider.isAvailable((ServerLevel) level, pos, Direction.UP),
            true, true);
        TYPES.put(id, type);

        ITransferHandler handler = (context, targets) -> {
            boolean movedAny = false;
            int remaining = context.limit();
            var srcCap = provider.resolve(context.level(), context.sourceNode().gPos().pos(), context.sourceNode().face());
            if (srcCap == null || provider.isEmpty(srcCap)) return false;

            for (var target : targets) {
                if (remaining <= 0) break;
                var tgtCap = provider.resolve(
                    context.level().getServer().getLevel(target.gPos().dimension()),
                    target.gPos().pos(), target.face());
                if (tgtCap == null) continue;

                int extracted = provider.extract(srcCap, remaining);
                if (extracted <= 0) break;
                int inserted = provider.insert(tgtCap, extracted);
                if (inserted > 0) {
                    remaining -= inserted;
                    movedAny = true;
                }
            }
            return movedAny;
        };
        HANDLERS.put(id, handler);
    }

    public static Collection<TransferType> getAllActive() {
        return TYPES.values();
    }

    @Nullable
    public static ITransferHandler getHandler(TransferType type) {
        return HANDLERS.get(type.id());
    }
}