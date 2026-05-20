package com.coobird.staticlogistics.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Map;

/**
 * 物流蓝图数据 —— 存储一个区域内所有面配置和容器配置的相对位置快照。
 * 粘贴时以新锚点为基准重建。
 */
public record BlueprintData(BlockPos anchor, String groupId, List<BlockEntry> blocks) {

    public static final BlueprintData EMPTY = new BlueprintData(BlockPos.ZERO, "", List.of());

    public static final Codec<BlueprintData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        BlockPos.CODEC.fieldOf("anchor").forGetter(BlueprintData::anchor),
        Codec.STRING.fieldOf("group").forGetter(BlueprintData::groupId),
        Codec.list(BlockEntry.CODEC).fieldOf("blocks").forGetter(BlueprintData::blocks)
    ).apply(inst, BlueprintData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, BlueprintData::anchor,
        ByteBufCodecs.STRING_UTF8, BlueprintData::groupId,
        ByteBufCodecs.fromCodecWithRegistries(Codec.list(BlockEntry.CODEC)), BlueprintData::blocks,
        BlueprintData::new
    );

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public record BlockEntry(BlockPos relativePos, Map<Direction, FaceEntry> faces,
                             CompoundTag containerUpgrades, List<BlockPos> linkedTo) {

        public static final Codec<BlockEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(BlockEntry::relativePos),
            Codec.unboundedMap(Direction.CODEC, FaceEntry.CODEC).fieldOf("faces").forGetter(BlockEntry::faces),
            CompoundTag.CODEC.optionalFieldOf("container", new CompoundTag()).forGetter(BlockEntry::containerUpgrades),
            Codec.list(BlockPos.CODEC).optionalFieldOf("links", List.of()).forGetter(BlockEntry::linkedTo)
        ).apply(inst, BlockEntry::new));
    }

    public record FaceEntry(CompoundTag faceConfig, CompoundTag linkConfig, CompoundTag filterUpgrades) {

        public static final Codec<FaceEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            CompoundTag.CODEC.fieldOf("face").forGetter(FaceEntry::faceConfig),
            CompoundTag.CODEC.fieldOf("link").forGetter(FaceEntry::linkConfig),
            CompoundTag.CODEC.optionalFieldOf("filter", new CompoundTag()).forGetter(FaceEntry::filterUpgrades)
        ).apply(inst, FaceEntry::new));
    }
}
