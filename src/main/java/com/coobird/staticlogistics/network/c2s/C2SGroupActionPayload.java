package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.storage.GroupService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SGroupActionPayload(
    String groupId,
    String newGroupId,
    Action action
) implements CustomPacketPayload {

    public enum Action {RENAME, DELETE}

    public static final Type<C2SGroupActionPayload> TYPE = new Type<>(Staticlogistics.asResource("group_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SGroupActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, C2SGroupActionPayload::groupId,
        ByteBufCodecs.STRING_UTF8, C2SGroupActionPayload::newGroupId,
        ByteBufCodecs.idMapper(id -> Action.values()[id], Action::ordinal), C2SGroupActionPayload::action,
        C2SGroupActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SGroupActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (payload.action() == Action.DELETE) {
                GroupService.deleteGroup(player.level(), player, payload.groupId());
            } else if (payload.action() == Action.RENAME) {
                GroupService.renameGroup(player.level(), player, payload.groupId(), payload.newGroupId());
            }
        });
    }
}