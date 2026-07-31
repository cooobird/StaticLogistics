package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.node.ScopedTopologyLink;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;

import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * 轻量拓扑网络模型的共享编解码入口。
 */
public final class TopologyStreamCodecs {
    private static final int MAX_OWNER_NAME_LENGTH = 64;

    private TopologyStreamCodecs() {
    }

    public static FaceTopology decodeFace(PortRegistryFriendlyByteBuf buffer) {
        LogisticsNode node = LogisticsNode.STREAM_CODEC.decode(buffer);
        int groupCount = buffer.readVarInt();
        if (groupCount < 0 || groupCount > GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new DecoderException("Invalid topology face group count: " + groupCount);
        }
        LinkedHashSet<GroupRef> groups = new LinkedHashSet<>();
        for (int index = 0; index < groupCount; index++) {
            if (!groups.add(decodeGroup(buffer))) {
                throw new DecoderException("Duplicate group in topology face");
            }
        }
        int roleId = buffer.readUnsignedByte();
        NodeRole[] roles = NodeRole.values();
        if (roleId >= roles.length) {
            throw new DecoderException("Invalid topology node role: " + roleId);
        }
        int maxTransferBlocks = buffer.readVarInt();
        boolean dimensionEffective = buffer.readBoolean();
        long speedMultiplier = buffer.readVarLong();
        long rangeMultiplier = buffer.readVarLong();
        long stackMultiplier = buffer.readVarLong();
        boolean inputFilterPresent = buffer.readBoolean();
        boolean outputFilterPresent = buffer.readBoolean();
        var outputTypeIds = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
        var acceptedTypeIds = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
        UUID ownerId = buffer.readBoolean() ? buffer.readUUID() : null;
        String ownerName = buffer.readUtf(MAX_OWNER_NAME_LENGTH);
        long version = buffer.readLong();
        try {
            return new FaceTopology(node, groups, roles[roleId], maxTransferBlocks,
                dimensionEffective, speedMultiplier, rangeMultiplier, stackMultiplier,
                inputFilterPresent, outputFilterPresent, outputTypeIds, acceptedTypeIds,
                ownerId, ownerName, version);
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid topology face", exception);
        }
    }

    public static void encodeFace(PortRegistryFriendlyByteBuf buffer, FaceTopology face) {
        if (face.groups().size() > GroupConstraints.MAX_GROUPS_PER_OWNER) {
            throw new EncoderException("Topology face group count exceeds maximum");
        }
        LogisticsNode.STREAM_CODEC.encode(buffer, face.node());
        buffer.writeVarInt(face.groups().size());
        face.groups().forEach(group -> encodeGroup(buffer, group));
        buffer.writeByte(face.role().ordinal());
        buffer.writeVarInt(face.maxTransferBlocks());
        buffer.writeBoolean(face.dimensionEffective());
        buffer.writeVarLong(face.speedMultiplier());
        buffer.writeVarLong(face.rangeMultiplier());
        buffer.writeVarLong(face.stackMultiplier());
        buffer.writeBoolean(face.inputFilterPresent());
        buffer.writeBoolean(face.outputFilterPresent());
        BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, face.outputTypeIds());
        BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, face.acceptedTypeIds());
        buffer.writeBoolean(face.ownerId() != null);
        if (face.ownerId() != null) buffer.writeUUID(face.ownerId());
        buffer.writeUtf(face.ownerName(), MAX_OWNER_NAME_LENGTH);
        buffer.writeLong(face.version());
    }

    public static ScopedTopologyLink decodeLink(PortRegistryFriendlyByteBuf buffer) {
        return new ScopedTopologyLink(
            GroupKey.STREAM_CODEC.decode(buffer),
            LogisticsNode.STREAM_CODEC.decode(buffer),
            LogisticsNode.STREAM_CODEC.decode(buffer),
            buffer.readUtf(GroupConstraints.MAX_CONNECTION_NAME_LENGTH)
        );
    }

    public static void encodeLink(PortRegistryFriendlyByteBuf buffer, ScopedTopologyLink link) {
        GroupKey.STREAM_CODEC.encode(buffer, link.groupKey());
        LogisticsNode.STREAM_CODEC.encode(buffer, link.source());
        LogisticsNode.STREAM_CODEC.encode(buffer, link.target());
        buffer.writeUtf(link.displayName(), GroupConstraints.MAX_CONNECTION_NAME_LENGTH);
    }

    public static GroupRef decodeGroup(PortRegistryFriendlyByteBuf buffer) {
        return new GroupRef(GroupKey.STREAM_CODEC.decode(buffer),
            buffer.readUtf(GroupConstraints.MAX_NAME_LENGTH));
    }

    public static void encodeGroup(PortRegistryFriendlyByteBuf buffer, GroupRef group) {
        GroupKey.STREAM_CODEC.encode(buffer, group.key());
        buffer.writeUtf(group.displayName(), GroupConstraints.MAX_NAME_LENGTH);
    }
}
