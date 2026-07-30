package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.node.ScopedTopologyLink;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.network.TopologyPagePartitioner;
import com.coobird.staticlogistics.network.TopologyStreamCodecs;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页传输当前玩家有权读取的轻量拓扑和分组目录。
 *
 * <p>链接边独立分页，避免单个高连接数节点形成无法分片的超大嵌套结构。
 */
public record S2CAccessSnapshotPayload(
    long sequence,
    int pageIndex,
    int pageCount,
    List<FaceTopology> faces,
    List<ScopedTopologyLink> links,
    List<GroupRef> groups
) implements CustomPacketPayload {
    private static final int MAX_PAGE_COUNT = 16_384;
    public static final Type<S2CAccessSnapshotPayload> TYPE =
        new Type<>(StaticLogistics.asResource("access_snapshot"));

    public S2CAccessSnapshotPayload {
        faces = List.copyOf(faces);
        links = List.copyOf(links);
        groups = List.copyOf(groups);
        validatePage(pageIndex, pageCount, faces.size(), links.size(), groups.size(), false);
        validateWeight(faces, links, groups, false);
    }

    /**
     * 将权限快照按条目数和编码复杂度拆成有界页面。
     */
    public static List<S2CAccessSnapshotPayload> pages(
        List<FaceTopology> faces,
        List<ScopedTopologyLink> links,
        List<GroupRef> groups
    ) {
        List<List<FaceTopology>> facePages = TopologyPagePartitioner.faces(faces);
        List<List<ScopedTopologyLink>> linkPages = TopologyPagePartitioner.links(links);
        List<List<GroupRef>> groupPages = TopologyPagePartitioner.groups(groups);
        int pageCount = Math.max(1,
            Math.max(facePages.size(), Math.max(linkPages.size(), groupPages.size())));
        if (pageCount > MAX_PAGE_COUNT) {
            throw new IllegalArgumentException("Access snapshot exceeds maximum page count");
        }
        long sequence = TopologySequence.next();
        List<S2CAccessSnapshotPayload> pages = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++) {
            pages.add(new S2CAccessSnapshotPayload(sequence, page, pageCount,
                page(facePages, page), page(linkPages, page), page(groupPages, page)));
        }
        return List.copyOf(pages);
    }

    private static <T> List<T> page(List<List<T>> pages, int page) {
        return page < pages.size() ? pages.get(page) : List.of();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CAccessSnapshotPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public S2CAccessSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
                long sequence = buffer.readVarLong();
                int pageIndex = buffer.readVarInt();
                int pageCount = buffer.readVarInt();
                int faceCount = buffer.readVarInt();
                int linkCount = buffer.readVarInt();
                int groupCount = buffer.readVarInt();
                validatePage(pageIndex, pageCount, faceCount, linkCount, groupCount, true);

                List<FaceTopology> faces = new ArrayList<>(faceCount);
                for (int i = 0; i < faceCount; i++) faces.add(TopologyStreamCodecs.decodeFace(buffer));
                List<ScopedTopologyLink> links = new ArrayList<>(linkCount);
                for (int i = 0; i < linkCount; i++) links.add(TopologyStreamCodecs.decodeLink(buffer));
                List<GroupRef> groups = new ArrayList<>(groupCount);
                for (int i = 0; i < groupCount; i++) groups.add(TopologyStreamCodecs.decodeGroup(buffer));
                validateWeight(faces, links, groups, true);
                return new S2CAccessSnapshotPayload(
                    sequence, pageIndex, pageCount, faces, links, groups);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, S2CAccessSnapshotPayload payload) {
                validatePage(payload.pageIndex(), payload.pageCount(), payload.faces().size(),
                    payload.links().size(), payload.groups().size(), false);
                buffer.writeVarLong(payload.sequence());
                buffer.writeVarInt(payload.pageIndex());
                buffer.writeVarInt(payload.pageCount());
                buffer.writeVarInt(payload.faces().size());
                buffer.writeVarInt(payload.links().size());
                buffer.writeVarInt(payload.groups().size());
                payload.faces().forEach(face -> TopologyStreamCodecs.encodeFace(buffer, face));
                payload.links().forEach(link -> TopologyStreamCodecs.encodeLink(buffer, link));
                payload.groups().forEach(group -> TopologyStreamCodecs.encodeGroup(buffer, group));
            }
        };

    private static void validatePage(
        int pageIndex,
        int pageCount,
        int faceCount,
        int linkCount,
        int groupCount,
        boolean decoding
    ) {
        int maximum = LogisticsConstants.Network.getMaxBulkEntries();
        String message = null;
        if (pageCount < 1 || pageCount > MAX_PAGE_COUNT) {
            message = "Invalid access snapshot page count: " + pageCount;
        } else if (pageIndex < 0 || pageIndex >= pageCount) {
            message = "Invalid access snapshot page index: " + pageIndex;
        } else if (faceCount < 0 || faceCount > maximum) {
            message = "Invalid access snapshot face count: " + faceCount;
        } else if (linkCount < 0 || linkCount > maximum) {
            message = "Invalid access snapshot link count: " + linkCount;
        } else if (groupCount < 0 || groupCount > maximum) {
            message = "Invalid access snapshot group count: " + groupCount;
        }
        if (message == null) return;
        if (decoding) throw new DecoderException(message);
        throw new EncoderException(message);
    }

    private static void validateWeight(List<FaceTopology> faces,
                                       List<ScopedTopologyLink> links,
                                       List<GroupRef> groups,
                                       boolean decoding) {
        if (TopologyPagePartitioner.pageWeight(faces, links, groups)
            <= TopologyPagePartitioner.maximumCombinedPageWeight()) return;
        String message = "Access snapshot page exceeds maximum encoded weight";
        if (decoding) throw new DecoderException(message);
        throw new EncoderException(message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CAccessSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLinkData.INSTANCE.acceptAuthoritativeSnapshotPage(
            payload.sequence(), payload.pageIndex(), payload.pageCount(),
            payload.faces(), payload.links(), payload.groups()));
    }
}
