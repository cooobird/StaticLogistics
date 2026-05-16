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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 过滤器数据记录类，用于存储和管理物品与流体的过滤配置信息。
 * 该类包含物品过滤、流体过滤、标签过滤等多种过滤方式，支持黑白名单模式，
 * 并可以配置NBT匹配模式和耐久度忽略设置。
 *
 * @param items 物品过滤映射，键为槽位字符串，值为物品堆栈
 * @param fluids 流体过滤映射，键为槽位字符串，值为流体对象
 * @param isBlacklist 是否为黑名单模式，true表示黑名单，false表示白名单
 * @param nbtMatchMode NBT匹配模式，决定物品NBT数据的匹配方式
 * @param tagSlots 物品标签过滤映射，键为槽位字符串，值为物品标签集合
 * @param excludedTagSlots 排除的物品标签过滤映射，键为槽位字符串，值为要排除的物品标签集合
 * @param fluidFilterTags 流体标签过滤映射，键为槽位字符串，值为流体标签集合
 * @param excludedFluidTags 排除的流体标签过滤映射，键为槽位字符串，值为要排除的流体标签集合
 * @param ignoreDamage 是否忽略物品耐久度，true表示忽略耐久度差异
 */
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

            int fluidTagSlotCount = buf.readVarInt();
            Map<String, Set<TagKey<Fluid>>> fluidFilterTags = new HashMap<>();
            for (int i = 0; i < fluidTagSlotCount; i++) {
                String key = buf.readUtf();
                int size = buf.readVarInt();
                Set<TagKey<Fluid>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    ResourceLocation rl = buf.readResourceLocation();
                    tags.add(TagKey.create(Registries.FLUID, rl));
                }
                fluidFilterTags.put(key, tags);
            }

            int excludedFluidTagSlotCount = buf.readVarInt();
            Map<String, Set<TagKey<Fluid>>> excludedFluidTags = new HashMap<>();
            for (int i = 0; i < excludedFluidTagSlotCount; i++) {
                String key = buf.readUtf();
                int size = buf.readVarInt();
                Set<TagKey<Fluid>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    ResourceLocation rl = buf.readResourceLocation();
                    tags.add(TagKey.create(Registries.FLUID, rl));
                }
                excludedFluidTags.put(key, tags);
            }

            boolean ignoreDamage = buf.readBoolean();

            return new FilterData(items, fluids, isBlacklist, nbtMatchMode,
                tagSlots, excludedTagSlots, fluidFilterTags, excludedFluidTags, ignoreDamage);
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
            data.fluidFilterTags().forEach((key, tags) -> {
                buf.writeUtf(key);
                buf.writeVarInt(tags.size());
                tags.forEach(tag -> buf.writeResourceLocation(tag.location()));
            });

            buf.writeVarInt(data.excludedFluidTags().size());
            data.excludedFluidTags().forEach((key, tags) -> {
                buf.writeUtf(key);
                buf.writeVarInt(tags.size());
                tags.forEach(tag -> buf.writeResourceLocation(tag.location()));
            });

            buf.writeBoolean(data.ignoreDamage());
        }
    };

    public static final FilterData EMPTY = new FilterData(
        new HashMap<>(), new HashMap<>(), false, NbtMatchMode.PARTIAL,
        new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), true
    );
}