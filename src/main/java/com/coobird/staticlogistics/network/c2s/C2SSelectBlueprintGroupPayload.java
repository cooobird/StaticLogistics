package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 保存蓝图自己的来源组，使退出 UI 后仍能使用世界框选。
public record C2SSelectBlueprintGroupPayload(int toolSlot, GroupKey group) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("select_blueprint_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SSelectBlueprintGroupPayload> STREAM_CODEC = PortStreamCodec.composite(PortByteBufCodecs.VAR_INT, C2SSelectBlueprintGroupPayload::toolSlot,
        GroupKey.STREAM_CODEC, C2SSelectBlueprintGroupPayload::group, C2SSelectBlueprintGroupPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SSelectBlueprintGroupPayload payload = this;
        Player context = packetPlayer;

        if (!(context instanceof ServerPlayer player) || !ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.TOOL_SETTINGS))
            return;
        var stack = BlueprintModeHandler.tool(player, payload.toolSlot());
        var group = PlayerGroupStore.get(player.server).findGroup(payload.group());
        if (stack.isEmpty() || group == null || !PermissionService.getInstance().canAccess(group.key().ownerId(), player))
            return;
        if (!group.key().equals(stack.get(SLDataComponents.BLUEPRINT_GROUP_KEY.get())))
            stack.remove(SLDataComponents.BLUEPRINT_ANCHOR.get());
        stack.set(SLDataComponents.BLUEPRINT_GROUP.get(), group.displayName());
        stack.set(SLDataComponents.BLUEPRINT_GROUP_KEY.get(), group.key());

    }
}
