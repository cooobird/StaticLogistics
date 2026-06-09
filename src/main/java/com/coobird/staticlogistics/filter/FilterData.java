package com.coobird.staticlogistics.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

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
    Map<String, Set<TagKey<Item>>> tagSlots,
    Map<String, Set<TagKey<Item>>> excludedTagSlots,
    Map<String, Set<TagKey<Fluid>>> fluidFilterTags,
    Map<String, Set<TagKey<Fluid>>> excludedFluidTags,
    boolean ignoreDamage
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

    private static final Codec<Map<String, Set<TagKey<Fluid>>>> FLUID_TAG_SLOTS_CODEC =
        Codec.unboundedMap(Codec.STRING, FLUID_TAG_SET_CODEC);

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
        FLUID_TAG_SLOTS_CODEC.optionalFieldOf("fluid_filter_tags", new HashMap<>()).forGetter(FilterData::fluidFilterTags),
        FLUID_TAG_SLOTS_CODEC.optionalFieldOf("excluded_fluid_tags", new HashMap<>()).forGetter(FilterData::excludedFluidTags),
        Codec.BOOL.optionalFieldOf("ignore_damage", true).forGetter(FilterData::ignoreDamage)
    ).apply(inst, FilterData::new));

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, FilterData> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public FilterData decode(PortRegistryFriendlyByteBuf buf) {
            FriendlyByteBuf fbuf = buf;
            int itemSize = fbuf.readVarInt();
            Map<String, ItemStack> items = new HashMap<>();
            for (int i = 0; i < itemSize; i++) {
                int slot = fbuf.readVarInt();
                ItemStack stack = fbuf.readItem();
                items.put(String.valueOf(slot), stack);
            }

            int fluidSize = fbuf.readVarInt();
            Map<String, Fluid> fluids = new HashMap<>();
            var fluidCodec = PortByteBufCodecs.registry(BuiltInRegistries.FLUID.key());
            for (int i = 0; i < fluidSize; i++) {
                int slot = fbuf.readVarInt();
                Fluid fluid = fluidCodec.decode(buf);
                fluids.put(String.valueOf(slot), fluid);
            }

            boolean isBlacklist = fbuf.readBoolean();
            NbtMatchMode nbtMatchMode = fbuf.readEnum(NbtMatchMode.class);

            int tagSlotCount = fbuf.readVarInt();
            Map<String, Set<TagKey<Item>>> tagSlots = new HashMap<>();
            for (int i = 0; i < tagSlotCount; i++) {
                String key = fbuf.readUtf();
                int size = fbuf.readVarInt();
                Set<TagKey<Item>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    tags.add(TagKey.create(Registries.ITEM, fbuf.readResourceLocation()));
                }
                tagSlots.put(key, tags);
            }

            int excludedTagSlotCount = fbuf.readVarInt();
            Map<String, Set<TagKey<Item>>> excludedTagSlots = new HashMap<>();
            for (int i = 0; i < excludedTagSlotCount; i++) {
                String key = fbuf.readUtf();
                int size = fbuf.readVarInt();
                Set<TagKey<Item>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    tags.add(TagKey.create(Registries.ITEM, fbuf.readResourceLocation()));
                }
                excludedTagSlots.put(key, tags);
            }

            int fluidTagSlotCount = fbuf.readVarInt();
            Map<String, Set<TagKey<Fluid>>> fluidFilterTags = new HashMap<>();
            for (int i = 0; i < fluidTagSlotCount; i++) {
                String key = fbuf.readUtf();
                int size = fbuf.readVarInt();
                Set<TagKey<Fluid>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    tags.add(TagKey.create(Registries.FLUID, fbuf.readResourceLocation()));
                }
                fluidFilterTags.put(key, tags);
            }

            int excludedFluidTagSlotCount = fbuf.readVarInt();
            Map<String, Set<TagKey<Fluid>>> excludedFluidTags = new HashMap<>();
            for (int i = 0; i < excludedFluidTagSlotCount; i++) {
                String key = fbuf.readUtf();
                int size = fbuf.readVarInt();
                Set<TagKey<Fluid>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    tags.add(TagKey.create(Registries.FLUID, fbuf.readResourceLocation()));
                }
                excludedFluidTags.put(key, tags);
            }

            boolean ignoreDamage = fbuf.readBoolean();

            return new FilterData(items, fluids, isBlacklist, nbtMatchMode,
                tagSlots, excludedTagSlots, fluidFilterTags, excludedFluidTags, ignoreDamage);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buf, FilterData data) {
            FriendlyByteBuf fbuf = buf;
            fbuf.writeVarInt(data.items().size());
            data.items().forEach((slot, stack) -> {
                fbuf.writeVarInt(Integer.parseInt(slot));
                fbuf.writeItem(stack);
            });

            fbuf.writeVarInt(data.fluids().size());
            var fluidCodec = PortByteBufCodecs.registry(BuiltInRegistries.FLUID.key());
            data.fluids().forEach((slot, fluid) -> {
                fbuf.writeVarInt(Integer.parseInt(slot));
                fluidCodec.encode(buf, fluid);
            });

            fbuf.writeBoolean(data.isBlacklist());
            fbuf.writeEnum(data.nbtMatchMode());

            fbuf.writeVarInt(data.tagSlots().size());
            data.tagSlots().forEach((key, tags) -> {
                fbuf.writeUtf(key);
                fbuf.writeVarInt(tags.size());
                tags.forEach(tag -> fbuf.writeResourceLocation(tag.location()));
            });

            fbuf.writeVarInt(data.excludedTagSlots().size());
            data.excludedTagSlots().forEach((key, tags) -> {
                fbuf.writeUtf(key);
                fbuf.writeVarInt(tags.size());
                tags.forEach(tag -> fbuf.writeResourceLocation(tag.location()));
            });

            fbuf.writeVarInt(data.fluidFilterTags().size());
            data.fluidFilterTags().forEach((key, tags) -> {
                fbuf.writeUtf(key);
                fbuf.writeVarInt(tags.size());
                tags.forEach(tag -> fbuf.writeResourceLocation(tag.location()));
            });

            fbuf.writeVarInt(data.excludedFluidTags().size());
            data.excludedFluidTags().forEach((key, tags) -> {
                fbuf.writeUtf(key);
                fbuf.writeVarInt(tags.size());
                tags.forEach(tag -> fbuf.writeResourceLocation(tag.location()));
            });

            fbuf.writeBoolean(data.ignoreDamage());
        }
    };

    public static final FilterData EMPTY = new FilterData(
        new HashMap<>(), new HashMap<>(), false, NbtMatchMode.PARTIAL,
        new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), true
    );
}
