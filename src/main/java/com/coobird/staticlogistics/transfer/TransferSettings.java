package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.core.DistributionStrategy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record TransferSettings(
    int interval,
    int bulkSize,
    int priority,
    DistributionStrategy strategy,
    LogisticsFilter filter,
    StockControl stock
) {
    public static final Codec<TransferSettings> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("interval").forGetter(TransferSettings::interval),
            Codec.INT.fieldOf("bulk_size").forGetter(TransferSettings::bulkSize),
            Codec.INT.fieldOf("priority").forGetter(TransferSettings::priority),
            DistributionStrategy.CODEC.fieldOf("strategy").forGetter(TransferSettings::strategy),
            LogisticsFilter.CODEC.fieldOf("filter").forGetter(TransferSettings::filter),
            StockControl.CODEC.fieldOf("stock").forGetter(TransferSettings::stock)
        ).apply(instance, TransferSettings::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferSettings> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, TransferSettings::interval,
        ByteBufCodecs.VAR_INT, TransferSettings::bulkSize,
        ByteBufCodecs.VAR_INT, TransferSettings::priority,
        DistributionStrategy.STREAM_CODEC, TransferSettings::strategy,
        LogisticsFilter.STREAM_CODEC, TransferSettings::filter,
        StockControl.STREAM_CODEC, TransferSettings::stock,
        TransferSettings::new
    );

    public static final TransferSettings DEFAULT = new TransferSettings(
        10, 32, 0, DistributionStrategy.SEQUENTIAL,
        new LogisticsFilter(false, new ArrayList<>()),
        new StockControl(false, 0)
    );

    public record LogisticsFilter(boolean isBlacklist, List<Item> items) {
        public static final Codec<LogisticsFilter> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.fieldOf("is_blacklist").forGetter(LogisticsFilter::isBlacklist),
                BuiltInRegistries.ITEM.byNameCodec().listOf().fieldOf("items").forGetter(LogisticsFilter::items)
            ).apply(instance, LogisticsFilter::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsFilter> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LogisticsFilter::isBlacklist,
            ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list()), LogisticsFilter::items,
            LogisticsFilter::new
        );

        public boolean test(ItemStack stack) {
            if (items.isEmpty()) return !isBlacklist;
            boolean contains = items.contains(stack.getItem());
            return isBlacklist != contains;
        }
    }

    public record StockControl(boolean enabled, int maxAmount) {
        public static final Codec<StockControl> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.fieldOf("enabled").forGetter(StockControl::enabled),
                Codec.INT.fieldOf("max_amount").forGetter(StockControl::maxAmount)
            ).apply(instance, StockControl::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, StockControl> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, StockControl::enabled,
            ByteBufCodecs.VAR_INT, StockControl::maxAmount,
            StockControl::new
        );

        public boolean canInsert(int currentAmount) {
            if (!enabled) return true;
            return currentAmount < maxAmount;
        }
    }
}