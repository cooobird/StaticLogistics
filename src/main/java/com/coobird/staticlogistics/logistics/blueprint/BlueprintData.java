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
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 物流蓝图数据，schema v2 使用精确到面的链接端点。
 */
public record BlueprintData(int schemaVersion, BlockPos anchor, BlockPos corner2,
                            String groupId, List<BlockEntry> blocks) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_LINKS_PER_FACE = 4096;

    private static final Codec<String> GROUP_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(GroupConstraints.normalizeName(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, value -> value);

    private static final Codec<List<BlockEntry>> BLOCKS_CODEC =
        Codec.list(BlockEntry.CODEC).flatXmap(
            BlueprintData::validateBlocks, BlueprintData::validateBlocks);

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
        if (blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Blueprint block count exceeds maximum " + MAX_BLOCKS);
        }
    }

    private static final Codec<BlueprintData> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema", 1).forGetter(BlueprintData::schemaVersion),
        BlockPos.CODEC.fieldOf("anchor").forGetter(BlueprintData::anchor),
        BlockPos.CODEC.fieldOf("corner2").forGetter(BlueprintData::corner2),
        GROUP_CODEC.fieldOf("group").forGetter(BlueprintData::groupId),
        BLOCKS_CODEC.fieldOf("blocks").forGetter(BlueprintData::blocks)
    ).apply(instance, BlueprintData::new));

    public static final Codec<BlueprintData> CODEC = RAW_CODEC.flatXmap(value -> {
        try {
            return DataResult.success(BlueprintDataMigration.migrate(value));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, DataResult::success);

    private static final PortStreamCodec<PortRegistryFriendlyByteBuf, BlueprintData> RAW_STREAM_CODEC =
        PortByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public BlueprintData decode(PortRegistryFriendlyByteBuf buffer) {
                try {
                    return BlueprintDataMigration.migrate(RAW_STREAM_CODEC.decode(buffer));
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    throw new DecoderException("Invalid blueprint data", exception);
                }
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, BlueprintData value) {
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

    private static DataResult<List<BlockEntry>> validateBlocks(List<BlockEntry> blocks) {
        return blocks.size() <= MAX_BLOCKS
            ? DataResult.success(List.copyOf(blocks))
            : DataResult.error(() -> "Blueprint block count exceeds maximum " + MAX_BLOCKS);
    }

    public record BlockEntry(BlockPos relativePos, Map<Direction, FaceEntry> faces,
                             CompoundTag containerUpgrades, List<BlockPos> linkedTo) {
        private static final Codec<List<BlockPos>> LEGACY_LINKS_CODEC =
            Codec.list(BlockPos.CODEC).flatXmap(
                BlockEntry::validateLegacyLinks, BlockEntry::validateLegacyLinks);

        public static final Codec<BlockEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(BlockEntry::relativePos),
            Codec.unboundedMap(Direction.CODEC, FaceEntry.CODEC).fieldOf("faces").forGetter(BlockEntry::faces),
            CompoundTag.CODEC.optionalFieldOf("container", new CompoundTag())
                .forGetter(BlockEntry::containerUpgrades),
            LEGACY_LINKS_CODEC.optionalFieldOf("links", List.of()).forGetter(BlockEntry::linkedTo)
        ).apply(instance, BlockEntry::new));

        public BlockEntry {
            Objects.requireNonNull(relativePos, "Blueprint relative position must not be null");
            faces = Map.copyOf(Objects.requireNonNull(faces, "Blueprint faces must not be null"));
            containerUpgrades = Objects.requireNonNull(
                containerUpgrades, "Blueprint container upgrades must not be null").copy();
            linkedTo = List.copyOf(Objects.requireNonNull(
                linkedTo, "Blueprint legacy links must not be null"));
            if (linkedTo.size() > MAX_LINKS_PER_FACE) {
                throw new IllegalArgumentException(
                    "Blueprint legacy link count exceeds maximum " + MAX_LINKS_PER_FACE);
            }
        }

        @Override
        public CompoundTag containerUpgrades() {
            return containerUpgrades.copy();
        }

        private static DataResult<List<BlockPos>> validateLegacyLinks(List<BlockPos> links) {
            return links.size() <= MAX_LINKS_PER_FACE
                ? DataResult.success(List.copyOf(links))
                : DataResult.error(() ->
                "Blueprint legacy link count exceeds maximum " + MAX_LINKS_PER_FACE);
        }
    }

    public record LinkEntry(BlockPos relativePos, Direction face) {
        public static final Codec<LinkEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(LinkEntry::relativePos),
            Direction.CODEC.fieldOf("face").forGetter(LinkEntry::face)
        ).apply(instance, LinkEntry::new));

        public LinkEntry {
            Objects.requireNonNull(relativePos, "Blueprint link position must not be null");
            Objects.requireNonNull(face, "Blueprint link face must not be null");
        }
    }

    public record FaceEntry(CompoundTag faceConfig, CompoundTag linkConfig, CompoundTag filterUpgrades,
                            List<LinkEntry> linkedTo) {
        private static final Codec<List<LinkEntry>> LINKS_CODEC =
            Codec.list(LinkEntry.CODEC).flatXmap(
                FaceEntry::validateLinks, FaceEntry::validateLinks);

        public static final Codec<FaceEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CompoundTag.CODEC.fieldOf("face").forGetter(FaceEntry::faceConfig),
            CompoundTag.CODEC.fieldOf("link").forGetter(FaceEntry::linkConfig),
            CompoundTag.CODEC.optionalFieldOf("filter", new CompoundTag())
                .forGetter(FaceEntry::filterUpgrades),
            LINKS_CODEC.optionalFieldOf("exact_links", List.of()).forGetter(FaceEntry::linkedTo)
        ).apply(instance, FaceEntry::new));

        public FaceEntry(CompoundTag faceConfig, CompoundTag linkConfig, CompoundTag filterUpgrades) {
            this(faceConfig, linkConfig, filterUpgrades, List.of());
        }

        public FaceEntry {
            faceConfig = Objects.requireNonNull(
                faceConfig, "Blueprint face configuration must not be null").copy();
            linkConfig = Objects.requireNonNull(
                linkConfig, "Blueprint link configuration must not be null").copy();
            filterUpgrades = Objects.requireNonNull(
                filterUpgrades, "Blueprint filter upgrades must not be null").copy();
            linkedTo = List.copyOf(Objects.requireNonNull(
                linkedTo, "Blueprint links must not be null"));
            if (linkedTo.size() > MAX_LINKS_PER_FACE) {
                throw new IllegalArgumentException(
                    "Blueprint link count exceeds maximum " + MAX_LINKS_PER_FACE);
            }
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

        private static DataResult<List<LinkEntry>> validateLinks(List<LinkEntry> links) {
            return links.size() <= MAX_LINKS_PER_FACE
                ? DataResult.success(List.copyOf(links))
                : DataResult.error(() ->
                "Blueprint link count exceeds maximum " + MAX_LINKS_PER_FACE);
        }
    }
}
