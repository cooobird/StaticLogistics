package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.node.ScopedTopologyLink;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.network.TopologyPagePartitioner;
import com.coobird.staticlogistics.network.TopologyStreamCodecs;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.*;
import java.util.function.Function;

/**
 * 分页、原子地替换一批面的轻量拓扑及其分组作用域出边。
 */
public record S2CTopologyUpdatePayload(
    long sequence,
    int pageIndex,
    int pageCount,
    List<FaceTopology> faces,
    List<ScopedTopologyLink> links
) implements IPortPacket.S2C {
    private static final int MAX_PAGE_COUNT = 16_384;
    public static final ResourceLocation ID = StaticLogistics.asResource("topology_update");

    /**
     * 服务端生成更新时使用的冻结输入。
     */
    public record FaceUpdate(FaceTopology topology, Map<GroupKey, Set<LogisticsNode>> linksByGroup) {
        public FaceUpdate {
            if (topology == null || linksByGroup == null) {
                throw new IllegalArgumentException("Topology update fields must not be null");
            }
            Map<GroupKey, Set<LogisticsNode>> copy = new LinkedHashMap<>();
            linksByGroup.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
            linksByGroup = Map.copyOf(copy);
        }

        public static FaceUpdate from(ServerLevel level, LogisticsNode node, FaceConfigComposite config) {
            return new FaceUpdate(FaceTopology.from(level, node, config), config.getLinkedNodesByGroup());
        }
    }

    public S2CTopologyUpdatePayload {
        faces = List.copyOf(faces);
        links = List.copyOf(links);
        validatePage(pageIndex, pageCount, faces.size(), links.size(), false);
        validateWeight(faces, links, false);
    }

    /**
     * 将任意数量的面和链接边拆成有界页面；客户端收齐后一次性提交。
     */
    public static List<S2CTopologyUpdatePayload> pages(
        Collection<FaceUpdate> updates,
        Function<ConnectionKey, String> connectionNameResolver
    ) {
        if (updates == null || updates.isEmpty()) return List.of();
        if (connectionNameResolver == null) {
            throw new IllegalArgumentException("Connection name resolver must not be null");
        }
        Map<LogisticsNode, FaceTopology> facesByNode = new LinkedHashMap<>();
        Set<ScopedTopologyLink> linkSet = new LinkedHashSet<>();
        for (FaceUpdate update : updates) {
            FaceTopology previous = facesByNode.putIfAbsent(update.topology().node(), update.topology());
            if (previous != null) throw new IllegalArgumentException("Duplicate face in topology update");
            update.linksByGroup().forEach((groupKey, targets) -> targets.forEach(target -> {
                ConnectionKey key = new ConnectionKey(groupKey, update.topology().node(), target);
                linkSet.add(new ScopedTopologyLink(groupKey, update.topology().node(), target,
                    connectionNameResolver.apply(key)));
            }));
        }

        List<FaceTopology> faces = List.copyOf(facesByNode.values());
        List<ScopedTopologyLink> links = List.copyOf(linkSet);
        List<List<FaceTopology>> facePages = TopologyPagePartitioner.faces(faces);
        List<List<ScopedTopologyLink>> linkPages = TopologyPagePartitioner.links(links);
        int pageCount = Math.max(1, Math.max(facePages.size(), linkPages.size()));
        if (pageCount > MAX_PAGE_COUNT) {
            throw new IllegalArgumentException("Topology update exceeds maximum page count");
        }
        long sequence = TopologySequence.next();
        List<S2CTopologyUpdatePayload> pages = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++) {
            pages.add(new S2CTopologyUpdatePayload(sequence, page, pageCount,
                page(facePages, page), page(linkPages, page)));
        }
        return List.copyOf(pages);
    }

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CTopologyUpdatePayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public S2CTopologyUpdatePayload decode(PortRegistryFriendlyByteBuf buffer) {
                long sequence = buffer.readVarLong();
                int pageIndex = buffer.readVarInt();
                int pageCount = buffer.readVarInt();
                int faceCount = buffer.readVarInt();
                int linkCount = buffer.readVarInt();
                validatePage(pageIndex, pageCount, faceCount, linkCount, true);
                List<FaceTopology> faces = new ArrayList<>(faceCount);
                for (int i = 0; i < faceCount; i++) faces.add(TopologyStreamCodecs.decodeFace(buffer));
                List<ScopedTopologyLink> links = new ArrayList<>(linkCount);
                for (int i = 0; i < linkCount; i++) links.add(TopologyStreamCodecs.decodeLink(buffer));
                validateWeight(faces, links, true);
                return new S2CTopologyUpdatePayload(
                    sequence, pageIndex, pageCount, faces, links);
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, S2CTopologyUpdatePayload payload) {
                validatePage(payload.pageIndex(), payload.pageCount(), payload.faces().size(),
                    payload.links().size(), false);
                buffer.writeVarLong(payload.sequence());
                buffer.writeVarInt(payload.pageIndex());
                buffer.writeVarInt(payload.pageCount());
                buffer.writeVarInt(payload.faces().size());
                buffer.writeVarInt(payload.links().size());
                payload.faces().forEach(face -> TopologyStreamCodecs.encodeFace(buffer, face));
                payload.links().forEach(link -> TopologyStreamCodecs.encodeLink(buffer, link));
            }
        };

    private static void validatePage(
        int pageIndex,
        int pageCount,
        int faceCount,
        int linkCount,
        boolean decoding
    ) {
        int maximum = LogisticsConstants.Network.getMaxBulkEntries();
        String message = null;
        if (pageCount < 1 || pageCount > MAX_PAGE_COUNT) {
            message = "Invalid topology update page count: " + pageCount;
        } else if (pageIndex < 0 || pageIndex >= pageCount) {
            message = "Invalid topology update page index: " + pageIndex;
        } else if (faceCount < 0 || faceCount > maximum) {
            message = "Invalid topology update face count: " + faceCount;
        } else if (linkCount < 0 || linkCount > maximum) {
            message = "Invalid topology update link count: " + linkCount;
        }
        if (message == null) return;
        if (decoding) throw new DecoderException(message);
        throw new EncoderException(message);
    }

    private static <T> List<T> page(List<List<T>> pages, int page) {
        return page < pages.size() ? pages.get(page) : List.of();
    }

    private static void validateWeight(List<FaceTopology> faces,
                                       List<ScopedTopologyLink> links,
                                       boolean decoding) {
        if (TopologyPagePartitioner.pageWeight(faces, links, List.of())
            <= TopologyPagePartitioner.maximumCombinedPageWeight()) return;
        String message = "Topology update page exceeds maximum encoded weight";
        if (decoding) throw new DecoderException(message);
        throw new EncoderException(message);
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        Minecraft.getInstance().execute(() ->
            ClientLinkData.INSTANCE.acceptTopologyUpdatePage(
                sequence(), pageIndex(), pageCount(), faces(), links()));
    }
}
