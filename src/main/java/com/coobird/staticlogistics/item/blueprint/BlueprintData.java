package com.coobird.staticlogistics.item.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.List;
import java.util.Map;

/**
 * 物流蓝图数据
 */
public record BlueprintData(BlockPos anchor, BlockPos corner2, String groupId, List<BlockEntry> blocks) {

    public static final BlueprintData EMPTY = new BlueprintData(BlockPos.ZERO, BlockPos.ZERO, "", List.of());

    public static final Codec<BlueprintData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        BlockPos.CODEC.fieldOf("anchor").forGetter(BlueprintData::anchor),
        BlockPos.CODEC.fieldOf("corner2").forGetter(BlueprintData::corner2),
        Codec.STRING.fieldOf("group").forGetter(BlueprintData::groupId),
        Codec.list(BlockEntry.CODEC).fieldOf("blocks").forGetter(BlueprintData::blocks)
    ).apply(inst, BlueprintData::new));

    private static void writeBlockPos(BlockPos pos, PortRegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    private static BlockPos readBlockPos(PortRegistryFriendlyByteBuf buf) {
        return buf.readBlockPos();
    }

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, BlueprintData> STREAM_CODEC = PortStreamCodec.composite(
        PortStreamCodec.ofMember(BlueprintData::writeBlockPos, BlueprintData::readBlockPos), BlueprintData::anchor,
        PortStreamCodec.ofMember(BlueprintData::writeBlockPos, BlueprintData::readBlockPos), BlueprintData::corner2,
        PortByteBufCodecs.STRING_UTF8, BlueprintData::groupId,
        PortByteBufCodecs.fromCodecWithRegistries(Codec.list(BlockEntry.CODEC)), BlueprintData::blocks,
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
