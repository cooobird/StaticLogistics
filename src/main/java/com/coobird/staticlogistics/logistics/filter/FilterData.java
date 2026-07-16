package com.coobird.staticlogistics.logistics.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.*;
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
    public static final int MAX_SLOT_COUNT = 64;
    public static final int MAX_TAGS_PER_SLOT = 64;
    public static final int MAX_TOTAL_TAGS = 256;
    public static final int MAX_SLOT_KEY_LENGTH = 8;
    public static final int MAX_ITEM_BYTES = 16 * 1024;
    public static final int MAX_TOTAL_ITEM_BYTES = 128 * 1024;
    public static final int MAX_ITEM_TAG_TEXT = 32 * 1024;
    public static final int MAX_TOTAL_ITEM_TAG_TEXT = 256 * 1024;

    public FilterData {
        items = copyItems(items);
        fluids = Map.copyOf(Objects.requireNonNull(fluids, "Filter fluids must not be null"));
        nbtMatchMode = Objects.requireNonNull(nbtMatchMode, "NBT match mode must not be null");
        tagSlots = copyTagSlots(tagSlots, "Filter item tags must not be null");
        excludedTagSlots = copyTagSlots(excludedTagSlots, "Excluded item tags must not be null");
        fluidFilterTags = copyTagSlots(fluidFilterTags, "Filter fluid tags must not be null");
        excludedFluidTags = copyTagSlots(excludedFluidTags, "Excluded fluid tags must not be null");
    }

    /**
     * ItemStack 可变，因此每次访问都返回深复制快照。
     */
    @Override
    public Map<String, ItemStack> items() {
        return copyItems(items);
    }

    private static Map<String, ItemStack> copyItems(Map<String, ItemStack> source) {
        Objects.requireNonNull(source, "Filter items must not be null");
        Map<String, ItemStack> copy = new LinkedHashMap<>();
        source.forEach((slot, stack) -> copy.put(
            Objects.requireNonNull(slot, "Filter item slot must not be null"),
            Objects.requireNonNull(stack, "Filter item must not be null").copy()));
        return Map.copyOf(copy);
    }

    private static <T> Map<String, Set<TagKey<T>>> copyTagSlots(
        Map<String, Set<TagKey<T>>> source, String nullMessage
    ) {
        Objects.requireNonNull(source, nullMessage);
        Map<String, Set<TagKey<T>>> copy = new LinkedHashMap<>();
        source.forEach((slot, tags) -> copy.put(
            Objects.requireNonNull(slot, "Filter tag slot must not be null"),
            Set.copyOf(Objects.requireNonNull(tags, "Filter tag set must not be null"))));
        return Map.copyOf(copy);
    }

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

    private static final Codec<NbtMatchMode> NBT_MODE_CODEC = Codec.STRING.comapFlatMap(name -> {
        try {
            return DataResult.success(NbtMatchMode.valueOf(name));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Unknown NBT match mode: " + name);
        }
    }, NbtMatchMode::name);

    private static final Codec<FilterData> RAW_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.unboundedMap(Codec.STRING, ItemStack.CODEC)
            .optionalFieldOf("items", new HashMap<>()).forGetter(FilterData::items),
        Codec.unboundedMap(Codec.STRING, ForgeRegistries.FLUIDS.getCodec())
            .optionalFieldOf("fluids", new HashMap<>()).forGetter(FilterData::fluids),
        Codec.BOOL.optionalFieldOf("isBlacklist", false).forGetter(FilterData::isBlacklist),
        NBT_MODE_CODEC
            .optionalFieldOf("nbt_mode", NbtMatchMode.PARTIAL).forGetter(FilterData::nbtMatchMode),
        TAG_SLOTS_CODEC.optionalFieldOf("tag_slots", new HashMap<>()).forGetter(FilterData::tagSlots),
        TAG_SLOTS_CODEC.optionalFieldOf("excluded_tag_slots", new HashMap<>()).forGetter(FilterData::excludedTagSlots),
        FLUID_TAG_SLOTS_CODEC.optionalFieldOf("fluid_filter_tags", new HashMap<>()).forGetter(FilterData::fluidFilterTags),
        FLUID_TAG_SLOTS_CODEC.optionalFieldOf("excluded_fluid_tags", new HashMap<>()).forGetter(FilterData::excludedFluidTags),
        Codec.BOOL.optionalFieldOf("ignore_damage", true).forGetter(FilterData::ignoreDamage)
    ).apply(inst, FilterData::new));

    public static final Codec<FilterData> CODEC = RAW_CODEC.flatXmap(
        FilterData::validatePersistent, FilterData::validatePersistent);

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, FilterData> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public FilterData decode(PortRegistryFriendlyByteBuf buf) {
            FriendlyByteBuf fbuf = buf;
            int itemSize = readCount(fbuf, MAX_SLOT_COUNT, "item slot count");
            Map<String, ItemStack> items = new HashMap<>();
            int totalItemBytes = 0;
            for (int i = 0; i < itemSize; i++) {
                int slot = readSlot(fbuf, "item slot");
                int startIndex = fbuf.readerIndex();
                ItemStack stack = fbuf.readItem();
                int itemBytes = fbuf.readerIndex() - startIndex;
                if (itemBytes > MAX_ITEM_BYTES) {
                    throw new DecoderException("Filter item exceeds maximum encoded size " + MAX_ITEM_BYTES);
                }
                totalItemBytes += itemBytes;
                if (totalItemBytes > MAX_TOTAL_ITEM_BYTES) {
                    throw new DecoderException(
                        "Filter items exceed maximum total encoded size " + MAX_TOTAL_ITEM_BYTES);
                }
                validateItemStructure(stack, true);
                if (items.put(String.valueOf(slot), stack) != null) {
                    throw new DecoderException("Duplicate item slot: " + slot);
                }
            }

            int fluidSize = readCount(fbuf, MAX_SLOT_COUNT, "fluid slot count");
            Map<String, Fluid> fluids = new HashMap<>();
            for (int i = 0; i < fluidSize; i++) {
                int slot = readSlot(fbuf, "fluid slot");
                ResourceLocation fluidId = fbuf.readResourceLocation();
                if (!ForgeRegistries.FLUIDS.containsKey(fluidId)) {
                    throw new DecoderException("Unknown filter fluid: " + fluidId);
                }
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                if (fluids.put(String.valueOf(slot), fluid) != null) {
                    throw new DecoderException("Duplicate fluid slot: " + slot);
                }
            }

            boolean isBlacklist = fbuf.readBoolean();
            NbtMatchMode nbtMatchMode = fbuf.readEnum(NbtMatchMode.class);

            int totalTags = 0;
            int tagSlotCount = readCount(fbuf, MAX_SLOT_COUNT, "item tag slot count");
            Map<String, Set<TagKey<Item>>> tagSlots = new HashMap<>();
            for (int i = 0; i < tagSlotCount; i++) {
                String key = readSlotKey(fbuf);
                int size = readCount(fbuf, MAX_TAGS_PER_SLOT, "item tag count");
                totalTags = addTagCount(totalTags, size);
                Set<TagKey<Item>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    if (!tags.add(TagKey.create(Registries.ITEM, fbuf.readResourceLocation()))) {
                        throw new DecoderException("Duplicate item tag");
                    }
                }
                if (tagSlots.put(key, tags) != null) {
                    throw new DecoderException("Duplicate item tag slot: " + key);
                }
            }

            int excludedTagSlotCount = readCount(fbuf, MAX_SLOT_COUNT, "excluded item tag slot count");
            Map<String, Set<TagKey<Item>>> excludedTagSlots = new HashMap<>();
            for (int i = 0; i < excludedTagSlotCount; i++) {
                String key = readSlotKey(fbuf);
                int size = readCount(fbuf, MAX_TAGS_PER_SLOT, "excluded item tag count");
                totalTags = addTagCount(totalTags, size);
                Set<TagKey<Item>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    if (!tags.add(TagKey.create(Registries.ITEM, fbuf.readResourceLocation()))) {
                        throw new DecoderException("Duplicate excluded item tag");
                    }
                }
                if (excludedTagSlots.put(key, tags) != null) {
                    throw new DecoderException("Duplicate excluded item tag slot: " + key);
                }
            }

            int fluidTagSlotCount = readCount(fbuf, MAX_SLOT_COUNT, "fluid tag slot count");
            Map<String, Set<TagKey<Fluid>>> fluidFilterTags = new HashMap<>();
            for (int i = 0; i < fluidTagSlotCount; i++) {
                String key = readSlotKey(fbuf);
                int size = readCount(fbuf, MAX_TAGS_PER_SLOT, "fluid tag count");
                totalTags = addTagCount(totalTags, size);
                Set<TagKey<Fluid>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    if (!tags.add(TagKey.create(Registries.FLUID, fbuf.readResourceLocation()))) {
                        throw new DecoderException("Duplicate fluid tag");
                    }
                }
                if (fluidFilterTags.put(key, tags) != null) {
                    throw new DecoderException("Duplicate fluid tag slot: " + key);
                }
            }

            int excludedFluidTagSlotCount = readCount(fbuf, MAX_SLOT_COUNT, "excluded fluid tag slot count");
            Map<String, Set<TagKey<Fluid>>> excludedFluidTags = new HashMap<>();
            for (int i = 0; i < excludedFluidTagSlotCount; i++) {
                String key = readSlotKey(fbuf);
                int size = readCount(fbuf, MAX_TAGS_PER_SLOT, "excluded fluid tag count");
                totalTags = addTagCount(totalTags, size);
                Set<TagKey<Fluid>> tags = new HashSet<>();
                for (int j = 0; j < size; j++) {
                    if (!tags.add(TagKey.create(Registries.FLUID, fbuf.readResourceLocation()))) {
                        throw new DecoderException("Duplicate excluded fluid tag");
                    }
                }
                if (excludedFluidTags.put(key, tags) != null) {
                    throw new DecoderException("Duplicate excluded fluid tag slot: " + key);
                }
            }

            boolean ignoreDamage = fbuf.readBoolean();

            return new FilterData(items, fluids, isBlacklist, nbtMatchMode,
                tagSlots, excludedTagSlots, fluidFilterTags, excludedFluidTags, ignoreDamage);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buf, FilterData data) {
            FriendlyByteBuf fbuf = buf;
            validateForNetwork(data);
            fbuf.writeVarInt(data.items().size());
            int[] totalItemBytes = {0};
            data.items().forEach((slot, stack) -> {
                fbuf.writeVarInt(Integer.parseInt(slot));
                int startIndex = fbuf.writerIndex();
                fbuf.writeItem(stack);
                int itemBytes = fbuf.writerIndex() - startIndex;
                if (itemBytes > MAX_ITEM_BYTES) {
                    throw new EncoderException("Filter item exceeds maximum encoded size " + MAX_ITEM_BYTES);
                }
                totalItemBytes[0] += itemBytes;
                if (totalItemBytes[0] > MAX_TOTAL_ITEM_BYTES) {
                    throw new EncoderException(
                        "Filter items exceed maximum total encoded size " + MAX_TOTAL_ITEM_BYTES);
                }
            });

            fbuf.writeVarInt(data.fluids().size());
            data.fluids().forEach((slot, fluid) -> {
                fbuf.writeVarInt(Integer.parseInt(slot));
                fbuf.writeResourceLocation(ForgeRegistries.FLUIDS.getKey(fluid));
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

    private static int readCount(FriendlyByteBuf buf, int maximum, String field) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new DecoderException(field + " exceeds maximum " + maximum + ": " + count);
        }
        return count;
    }

    private static int readSlot(FriendlyByteBuf buf, String field) {
        int slot = buf.readVarInt();
        if (slot < 0 || slot >= MAX_SLOT_COUNT) {
            throw new DecoderException("Invalid " + field + ": " + slot);
        }
        return slot;
    }

    private static String readSlotKey(FriendlyByteBuf buf) {
        String key = buf.readUtf(MAX_SLOT_KEY_LENGTH);
        validateDecodedSlotKey(key);
        return key;
    }

    private static int addTagCount(int total, int count) {
        int result = total + count;
        if (result > MAX_TOTAL_TAGS) {
            throw new DecoderException("Filter tag total exceeds maximum " + MAX_TOTAL_TAGS);
        }
        return result;
    }

    private static void validateForNetwork(FilterData data) {
        if (data.items().size() > MAX_SLOT_COUNT || data.fluids().size() > MAX_SLOT_COUNT
            || data.tagSlots().size() > MAX_SLOT_COUNT || data.excludedTagSlots().size() > MAX_SLOT_COUNT
            || data.fluidFilterTags().size() > MAX_SLOT_COUNT || data.excludedFluidTags().size() > MAX_SLOT_COUNT) {
            throw new EncoderException("Filter slot count exceeds maximum " + MAX_SLOT_COUNT);
        }
        int totalTags = validateTagMap(data.tagSlots());
        totalTags += validateTagMap(data.excludedTagSlots());
        totalTags += validateTagMap(data.fluidFilterTags());
        totalTags += validateTagMap(data.excludedFluidTags());
        if (totalTags > MAX_TOTAL_TAGS) {
            throw new EncoderException("Filter tag total exceeds maximum " + MAX_TOTAL_TAGS);
        }
        data.items().keySet().forEach(FilterData::validateEncodedSlotKey);
        data.fluids().keySet().forEach(FilterData::validateEncodedSlotKey);
        int totalTagText = 0;
        for (ItemStack stack : data.items().values()) {
            totalTagText += validateItemStructure(stack, false);
            if (totalTagText > MAX_TOTAL_ITEM_TAG_TEXT) {
                throw new EncoderException(
                    "Filter items exceed maximum NBT size " + MAX_TOTAL_ITEM_TAG_TEXT);
            }
        }
    }

    private static DataResult<FilterData> validatePersistent(FilterData data) {
        try {
            validateForNetwork(data);
            return DataResult.success(data);
        } catch (RuntimeException exception) {
            return DataResult.error(() -> "Invalid filter data: " + exception.getMessage());
        }
    }

    private static int validateTagMap(Map<String, ? extends Set<?>> slots) {
        int total = 0;
        for (Map.Entry<String, ? extends Set<?>> entry : slots.entrySet()) {
            validateEncodedSlotKey(entry.getKey());
            if (entry.getValue().size() > MAX_TAGS_PER_SLOT) {
                throw new EncoderException("Tag count exceeds maximum " + MAX_TAGS_PER_SLOT);
            }
            total += entry.getValue().size();
        }
        return total;
    }

    private static void validateEncodedSlotKey(String key) {
        try {
            int slot = Integer.parseInt(key);
            if (slot < 0 || slot >= MAX_SLOT_COUNT || key.length() > MAX_SLOT_KEY_LENGTH) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new EncoderException("Invalid filter slot key: " + key, exception);
        }
    }

    private static void validateDecodedSlotKey(String key) {
        try {
            int slot = Integer.parseInt(key);
            if (slot < 0 || slot >= MAX_SLOT_COUNT || key.length() > MAX_SLOT_KEY_LENGTH) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new DecoderException("Invalid filter slot key: " + key, exception);
        }
    }

    private static int validateItemStructure(ItemStack stack, boolean decoding) {
        int tagTextLength = stack.hasTag() ? stack.getTag().toString().length() : 0;
        if (tagTextLength > MAX_ITEM_TAG_TEXT) {
            String message = "Filter item NBT exceeds maximum " + MAX_ITEM_TAG_TEXT;
            if (decoding) throw new DecoderException(message);
            throw new EncoderException(message);
        }
        return tagTextLength;
    }

    public static final FilterData EMPTY = new FilterData(
        Map.of(), Map.of(), false, NbtMatchMode.PARTIAL,
        Map.of(), Map.of(), Map.of(), Map.of(), true
    );
}
