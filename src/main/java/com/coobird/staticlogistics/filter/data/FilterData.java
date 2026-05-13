package com.coobird.staticlogistics.filter.data;

import com.coobird.staticlogistics.api.type.NbtMatchMode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record FilterData(
    Map<String, ItemStack> items,
    Map<String, Fluid> fluids,
    boolean isBlacklist,
    NbtMatchMode nbtMatchMode,
    Map<String, Set<TagKey<Item>>> tagSlots,          // 白名单标签
    Map<String, Set<TagKey<Item>>> excludedTagSlots,  // 黑名单标签
    Set<TagKey<Fluid>> fluidFilterTags,               // 流体白名单标签
    Set<TagKey<Fluid>> excludedFluidTags              // 流体黑名单标签
) {
    private static final Codec<Set<TagKey<Item>>> TAG_SET_CODEC =
        Codec.list(ResourceLocation.CODEC).xmap(
            list -> list.stream().map(rl -> TagKey.create(Registries.ITEM, rl)).collect(Collectors.toSet()),
            set -> set.stream().map(TagKey::location).toList()
        );

    private static final Codec<Map<String, Set<TagKey<Item>>>> TAG_SLOTS_CODEC =
        Codec.unboundedMap(Codec.STRING, TAG_SET_CODEC);

    private static final Codec<Set<TagKey<Fluid>>> FLUID_TAG_SET_CODEC =
        Codec.list(ResourceLocation.CODEC).xmap(
            list -> list.stream().map(rl -> TagKey.create(Registries.FLUID, rl)).collect(Collectors.toSet()),
            set -> set.stream().map(TagKey::location).toList()
        );

    public static final Codec<FilterData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(Codec.STRING, ItemStack.CODEC)
            .optionalFieldOf("items", new HashMap<>()).forGetter(FilterData::items),
        Codec.unboundedMap(Codec.STRING, BuiltInRegistries.FLUID.byNameCodec())
            .optionalFieldOf("fluids", new HashMap<>()).forGetter(FilterData::fluids),
        Codec.BOOL.optionalFieldOf("isBlacklist", false).forGetter(FilterData::isBlacklist),
        Codec.STRING.xmap(NbtMatchMode::valueOf, NbtMatchMode::name)
            .optionalFieldOf("nbt_mode", NbtMatchMode.PARTIAL).forGetter(FilterData::nbtMatchMode),
        TAG_SLOTS_CODEC.optionalFieldOf("tag_slots", new HashMap<>()).forGetter(FilterData::tagSlots),
        TAG_SLOTS_CODEC.optionalFieldOf("excluded_tag_slots", new HashMap<>()).forGetter(FilterData::excludedTagSlots),
        FLUID_TAG_SET_CODEC.optionalFieldOf("fluid_filter_tags", Set.of()).forGetter(FilterData::fluidFilterTags),
        FLUID_TAG_SET_CODEC.optionalFieldOf("excluded_fluid_tags", Set.of()).forGetter(FilterData::excludedFluidTags)
    ).apply(inst, FilterData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FilterData decode(RegistryFriendlyByteBuf buf) {
            int itemSize = buf.readVarInt();
            Map<String, ItemStack> items = new HashMap<>();
            for (int i = 0; i < itemSize; i++) {
                int slot = buf.readVarInt();
                ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
                items.put(String.valueOf(slot), stack);
            }

            int fluidSize = buf.readVarInt();
            Map<String, Fluid> fluids = new HashMap<>();
            var fluidCodec = ByteBufCodecs.registry(BuiltInRegistries.FLUID.key());
            for (int i = 0; i < fluidSize; i++) {
                int slot = buf.readVarInt();
                Fluid fluid = fluidCodec.decode(buf);
                fluids.put(String.valueOf(slot), fluid);
            }

            boolean isBlacklist = buf.readBoolean();
            NbtMatchMode nbtMatchMode = buf.readEnum(NbtMatchMode.class);

            int tagSlotCount = buf.readVarInt();
            Map<String, Set<TagKey<Item>>> tagSlots = new HashMap<>();
            for (int i = 0; i < tagSlotCount; i++) {
                String key = buf.readUtf();
                int size = buf.readVarInt();
                Set<TagKey<Item>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    ResourceLocation rl = buf.readResourceLocation();
                    tags.add(TagKey.create(Registries.ITEM, rl));
                }
                tagSlots.put(key, tags);
            }

            int excludedTagSlotCount = buf.readVarInt();
            Map<String, Set<TagKey<Item>>> excludedTagSlots = new HashMap<>();
            for (int i = 0; i < excludedTagSlotCount; i++) {
                String key = buf.readUtf();
                int size = buf.readVarInt();
                Set<TagKey<Item>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    ResourceLocation rl = buf.readResourceLocation();
                    tags.add(TagKey.create(Registries.ITEM, rl));
                }
                excludedTagSlots.put(key, tags);
            }

            int fluidTagSize = buf.readVarInt();
            Set<TagKey<Fluid>> fluidFilterTags = new HashSet<>();
            for (int i = 0; i < fluidTagSize; i++) {
                ResourceLocation rl = buf.readResourceLocation();
                fluidFilterTags.add(TagKey.create(Registries.FLUID, rl));
            }

            int excludedFluidTagSize = buf.readVarInt();
            Set<TagKey<Fluid>> excludedFluidTags = new HashSet<>();
            for (int i = 0; i < excludedFluidTagSize; i++) {
                ResourceLocation rl = buf.readResourceLocation();
                excludedFluidTags.add(TagKey.create(Registries.FLUID, rl));
            }

            return new FilterData(items, fluids, isBlacklist, nbtMatchMode,
                tagSlots, excludedTagSlots, fluidFilterTags, excludedFluidTags);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, FilterData data) {
            buf.writeVarInt(data.items().size());
            data.items().forEach((slot, stack) -> {
                buf.writeVarInt(Integer.parseInt(slot));
                ItemStack.STREAM_CODEC.encode(buf, stack);
            });

            buf.writeVarInt(data.fluids().size());
            var fluidCodec = ByteBufCodecs.registry(BuiltInRegistries.FLUID.key());
            data.fluids().forEach((slot, fluid) -> {
                buf.writeVarInt(Integer.parseInt(slot));
                fluidCodec.encode(buf, fluid);
            });

            buf.writeBoolean(data.isBlacklist());
            buf.writeEnum(data.nbtMatchMode());

            buf.writeVarInt(data.tagSlots().size());
            data.tagSlots().forEach((key, tags) -> {
                buf.writeUtf(key);
                buf.writeVarInt(tags.size());
                tags.forEach(tag -> buf.writeResourceLocation(tag.location()));
            });

            buf.writeVarInt(data.excludedTagSlots().size());
            data.excludedTagSlots().forEach((key, tags) -> {
                buf.writeUtf(key);
                buf.writeVarInt(tags.size());
                tags.forEach(tag -> buf.writeResourceLocation(tag.location()));
            });

            buf.writeVarInt(data.fluidFilterTags().size());
            data.fluidFilterTags().forEach(tag -> buf.writeResourceLocation(tag.location()));

            buf.writeVarInt(data.excludedFluidTags().size());
            data.excludedFluidTags().forEach(tag -> buf.writeResourceLocation(tag.location()));
        }
    };

    public static final FilterData EMPTY = new FilterData(
        new HashMap<>(), new HashMap<>(), false, NbtMatchMode.PARTIAL,
        new HashMap<>(), new HashMap<>(), Set.of(), Set.of()
    );
}