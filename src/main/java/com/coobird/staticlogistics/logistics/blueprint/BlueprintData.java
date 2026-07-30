package com.coobird.staticlogistics.logistics.blueprint;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 物流蓝图数据 —— 存储一个区域内所有面配置和容器配置的相对位置快照。
 * anchor = 起点，corner2 = 终点（选区对角），用于渲染选区范围。
 * 粘贴时以新锚点为基准重建。
 */
public record BlueprintData(int schemaVersion, BlockPos anchor, BlockPos corner2,
                            String groupId, List<BlockEntry> blocks) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_LINKS_PER_FACE = 4096;

    private static final Codec<String> GROUP_CODEC = Codec.STRING.validate(value -> {
        try {
            return DataResult.success(GroupConstraints.normalizeName(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    });
    private static final Codec<List<BlockEntry>> BLOCKS_CODEC = Codec.list(BlockEntry.CODEC).validate(value ->
        value.size() <= MAX_BLOCKS ? DataResult.success(value)
            : DataResult.error(() -> "Blueprint block count exceeds maximum " + MAX_BLOCKS));

    public static final BlueprintData EMPTY = new BlueprintData(
        CURRENT_SCHEMA_VERSION, BlockPos.ZERO, BlockPos.ZERO, "", List.of());

    public BlueprintData(BlockPos anchor, BlockPos corner2, String groupId, List<BlockEntry> blocks) {
        this(CURRENT_SCHEMA_VERSION, anchor, corner2, groupId, blocks);
    }

    public BlueprintData {
        Objects.requireNonNull(anchor, "Blueprint anchor must not be null");
        Objects.requireNonNull(corner2, "Blueprint corner must not be null");
        Objects.requireNonNull(groupId, "Blueprint group must not be null");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "Blueprint blocks must not be null"));
    }

    private static final Codec<BlueprintData> RAW_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.INT.optionalFieldOf("schema", 1).forGetter(BlueprintData::schemaVersion),
        BlockPos.CODEC.fieldOf("anchor").forGetter(BlueprintData::anchor),
        BlockPos.CODEC.fieldOf("corner2").forGetter(BlueprintData::corner2),
        GROUP_CODEC.fieldOf("group").forGetter(BlueprintData::groupId),
        BLOCKS_CODEC.fieldOf("blocks").forGetter(BlueprintData::blocks)
    ).apply(inst, BlueprintData::new));

    public static final Codec<BlueprintData> CODEC = RAW_CODEC.flatXmap(value -> {
        try {
            return DataResult.success(BlueprintDataMigration.migrate(value));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, DataResult::success);

    private static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> RAW_STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, BlueprintData::schemaVersion,
        BlockPos.STREAM_CODEC, BlueprintData::anchor,
        BlockPos.STREAM_CODEC, BlueprintData::corner2,
        ByteBufCodecs.fromCodec(GROUP_CODEC), BlueprintData::groupId,
        ByteBufCodecs.fromCodecWithRegistries(BLOCKS_CODEC), BlueprintData::blocks,
        BlueprintData::new
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlueprintData decode(RegistryFriendlyByteBuf buffer) {
            try {
                return BlueprintDataMigration.migrate(RAW_STREAM_CODEC.decode(buffer));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new DecoderException("Invalid blueprint data", exception);
            }
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, BlueprintData value) {
            try {
                RAW_STREAM_CODEC.encode(buffer, BlueprintDataMigration.migrate(value));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new EncoderException("Invalid blueprint data", exception);
            }
        }
    };

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public record BlockEntry(BlockPos relativePos, Map<Direction, FaceEntry> faces,
                             CompoundTag containerUpgrades, List<BlockPos> linkedTo) {
        public BlockEntry {
            Objects.requireNonNull(relativePos, "Blueprint relative position must not be null");
            faces = Map.copyOf(Objects.requireNonNull(faces, "Blueprint faces must not be null"));
            containerUpgrades = Objects.requireNonNull(
                containerUpgrades, "Blueprint container upgrades must not be null").copy();
            linkedTo = List.copyOf(Objects.requireNonNull(
                linkedTo, "Blueprint legacy links must not be null"));
        }

        @Override
        public CompoundTag containerUpgrades() {
            return containerUpgrades.copy();
        }

        private static final Codec<List<BlockPos>> LEGACY_LINKS_CODEC = Codec.list(BlockPos.CODEC).validate(value ->
            value.size() <= MAX_LINKS_PER_FACE ? DataResult.success(value)
                : DataResult.error(() -> "Blueprint legacy link count exceeds maximum " + MAX_LINKS_PER_FACE));

        public static final Codec<BlockEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(BlockEntry::relativePos),
            Codec.unboundedMap(Direction.CODEC, FaceEntry.CODEC).fieldOf("faces").forGetter(BlockEntry::faces),
            CompoundTag.CODEC.optionalFieldOf("container", new CompoundTag()).forGetter(BlockEntry::containerUpgrades),
            LEGACY_LINKS_CODEC.optionalFieldOf("links", List.of()).forGetter(BlockEntry::linkedTo)
        ).apply(inst, BlockEntry::new));
    }

    public record LinkEntry(BlockPos relativePos, Direction face) {
        public LinkEntry {
            Objects.requireNonNull(relativePos, "Blueprint link position must not be null");
            Objects.requireNonNull(face, "Blueprint link face must not be null");
        }

        public static final Codec<LinkEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(LinkEntry::relativePos),
            Direction.CODEC.fieldOf("face").forGetter(LinkEntry::face)
        ).apply(inst, LinkEntry::new));
    }

    public record FaceEntry(CompoundTag faceConfig, CompoundTag linkConfig, CompoundTag filterUpgrades,
                            List<LinkEntry> linkedTo) {
        public FaceEntry {
            faceConfig = Objects.requireNonNull(
                faceConfig, "Blueprint face configuration must not be null").copy();
            linkConfig = Objects.requireNonNull(
                linkConfig, "Blueprint link configuration must not be null").copy();
            filterUpgrades = Objects.requireNonNull(
                filterUpgrades, "Blueprint filter upgrades must not be null").copy();
            linkedTo = List.copyOf(Objects.requireNonNull(
                linkedTo, "Blueprint links must not be null"));
        }

        @Override
        public CompoundTag faceConfig() {
            return faceConfig.copy();
        }

        @Override
        public CompoundTag linkConfig() {
            return linkConfig.copy();
        }

        @Override
        public CompoundTag filterUpgrades() {
            return filterUpgrades.copy();
        }

        private static final Codec<List<LinkEntry>> LINKS_CODEC = Codec.list(LinkEntry.CODEC).validate(value ->
            value.size() <= MAX_LINKS_PER_FACE ? DataResult.success(value)
                : DataResult.error(() -> "Blueprint link count exceeds maximum " + MAX_LINKS_PER_FACE));

        public static final Codec<FaceEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            CompoundTag.CODEC.fieldOf("face").forGetter(FaceEntry::faceConfig),
            CompoundTag.CODEC.fieldOf("link").forGetter(FaceEntry::linkConfig),
            CompoundTag.CODEC.optionalFieldOf("filter", new CompoundTag()).forGetter(FaceEntry::filterUpgrades),
            LINKS_CODEC.optionalFieldOf("exact_links", List.of()).forGetter(FaceEntry::linkedTo)
        ).apply(inst, FaceEntry::new));
    }
}
