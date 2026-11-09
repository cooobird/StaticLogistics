package com.coobird.staticlogistics.logistics.filter;

import com.coobird.staticlogistics.transfer.UpgradeType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FilterData(Map<String, ItemStack> items, Map<String, Fluid> fluids,
                         Map<String, Boolean> blacklist, Map<String, List<FilterRule>> rules) {
    public static final int MAX_SLOT_COUNT = 36;
    public static final int MAX_RULES_PER_SLOT = 64;

    public FilterData {
        Map<String, ItemStack> itemCopy = new LinkedHashMap<>();
        items.forEach((slot, stack) -> itemCopy.put(slot, stack.copy()));
        items = Map.copyOf(itemCopy);
        fluids = Map.copyOf(fluids);
        blacklist = Map.copyOf(blacklist);
        Map<String, List<FilterRule>> ruleCopy = new LinkedHashMap<>();
        rules.forEach((slot, value) -> ruleCopy.put(slot, List.copyOf(value)));
        rules = Map.copyOf(ruleCopy);
    }

    @Override
    public Map<String, ItemStack> items() {
        Map<String, ItemStack> copy = new LinkedHashMap<>();
        items.forEach((slot, stack) -> copy.put(slot, stack.copy()));
        return Map.copyOf(copy);
    }

    public ItemStack item(int slot) {
        return items.getOrDefault(Integer.toString(slot), ItemStack.EMPTY).copy();
    }

    public FilterData normalizedFor(UpgradeType type) {
        if (type != UpgradeType.FILTER) return this;
        if (items.values().stream().allMatch(stack -> stack.getCount() == 1)) return this;
        Map<String, ItemStack> normalized = new LinkedHashMap<>();
        for (var entry : items.entrySet()) {
            ItemStack stack = entry.getValue();
            normalized.put(entry.getKey(), stack.copyWithCount(1));
        }
        return new FilterData(normalized, fluids, blacklist, rules);
    }

    private static final Codec<FilterData> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, ItemStack.CODEC).fieldOf("items").forGetter(FilterData::items),
        Codec.unboundedMap(Codec.STRING, BuiltInRegistries.FLUID.byNameCodec()).fieldOf("fluids").forGetter(FilterData::fluids),
        Codec.unboundedMap(Codec.STRING, Codec.BOOL).fieldOf("blacklist").forGetter(FilterData::blacklist),
        Codec.unboundedMap(Codec.STRING, FilterRule.CODEC.listOf()).fieldOf("rules").forGetter(FilterData::rules)
    ).apply(instance, FilterData::new));

    public static final Codec<FilterData> CODEC = RAW_CODEC.validate(data -> {
        if (data.items.size() > MAX_SLOT_COUNT || data.fluids.size() > MAX_SLOT_COUNT
            || data.rules.size() > MAX_SLOT_COUNT || data.blacklist.size() > MAX_SLOT_COUNT)
            return DataResult.error(() -> "Too many filter slots");
        for (String slot : data.items.keySet())
            if (!validSlot(slot)) return DataResult.error(() -> "Invalid filter slot");
        for (String slot : data.fluids.keySet())
            if (!validSlot(slot) || data.items.containsKey(slot))
                return DataResult.error(() -> "Invalid fluid slot");
        for (String slot : data.blacklist.keySet())
            if (!validSlot(slot)
                || (!data.items.containsKey(slot) && !data.fluids.containsKey(slot)))
                return DataResult.error(() -> "Invalid blacklist slot");
        for (var entry : data.rules.entrySet()) {
            if (!validSlot(entry.getKey()) || entry.getValue().size() > MAX_RULES_PER_SLOT
                || (!data.items.containsKey(entry.getKey()) && !data.fluids.containsKey(entry.getKey())))
                return DataResult.error(() -> "Invalid filter rules");
        }
        return DataResult.success(data);
    });

    private static boolean validSlot(String slot) {
        for (int i = 0; i < MAX_SLOT_COUNT; i++) if (Integer.toString(i).equals(slot)) return true;
        return false;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterData> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public static final FilterData EMPTY = new FilterData(Map.of(), Map.of(), Map.of(), Map.of());
}
