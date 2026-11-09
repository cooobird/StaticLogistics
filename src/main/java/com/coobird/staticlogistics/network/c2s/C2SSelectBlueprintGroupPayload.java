package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 保存蓝图自己的来源组，使退出 UI 后仍能使用世界框选。
public record C2SSelectBlueprintGroupPayload(int toolSlot, GroupKey group) implements CustomPacketPayload {
    public static final Type<C2SSelectBlueprintGroupPayload> TYPE = new Type<>(StaticLogistics.asResource("select_blueprint_group"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSelectBlueprintGroupPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, C2SSelectBlueprintGroupPayload::toolSlot,
        GroupKey.STREAM_CODEC, C2SSelectBlueprintGroupPayload::group, C2SSelectBlueprintGroupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SSelectBlueprintGroupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.TOOL_SETTINGS))
                return;
            var stack = BlueprintModeHandler.tool(player, payload.toolSlot());
            var group = PlayerGroupStore.get(player.server).findGroup(payload.group());
            if (stack.isEmpty() || group == null || !PermissionService.getInstance().canAccess(group.key().ownerId(), player))
                return;
            if (!group.key().equals(stack.get(SLDataComponents.BLUEPRINT_GROUP_KEY.get())))
                stack.remove(SLDataComponents.BLUEPRINT_ANCHOR.get());
            stack.set(SLDataComponents.BLUEPRINT_GROUP.get(), group.displayName());
            stack.set(SLDataComponents.BLUEPRINT_GROUP_KEY.get(), group.key());
        });
    }
}
