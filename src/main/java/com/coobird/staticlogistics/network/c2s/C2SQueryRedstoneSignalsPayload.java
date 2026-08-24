package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CRedstoneSignalsPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 请求分组内所有红石控制方案的最新电平，不重复传输绑定结构。
 */
public record C2SQueryRedstoneSignalsPayload(GroupKey groupKey)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("query_redstone_signals");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SQueryRedstoneSignalsPayload> STREAM_CODEC = PortStreamCodec.composite(
        GroupKey.STREAM_CODEC, C2SQueryRedstoneSignalsPayload::groupKey,
        C2SQueryRedstoneSignalsPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!NodeInteractionValidator.holdsConfigurator(player)
            || !ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.REDSTONE_SIGNAL_QUERY)
            || !PermissionService.getInstance().canModify(groupKey().ownerId(), player)
            || PlayerGroupStore.get(player.server).findGroup(groupKey()) == null) return;
        S2CRedstoneSignalsPayload.sendGroup(player, groupKey());
    }
}
