package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.filter.data.FilterData;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SUpdateFilterOnItemPayload(
    BlockPos pos,
    Direction face,
    ResourceLocation typeId,
    boolean isInput,
    FilterData filter
) implements CustomPacketPayload {

    public static final Type<C2SUpdateFilterOnItemPayload> TYPE =
        new Type<>(Staticlogistics.asResource("update_filter_on_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateFilterOnItemPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, C2SUpdateFilterOnItemPayload::pos,
            Direction.STREAM_CODEC, C2SUpdateFilterOnItemPayload::face,
            ResourceLocation.STREAM_CODEC, C2SUpdateFilterOnItemPayload::typeId,
            ByteBufCodecs.BOOL, C2SUpdateFilterOnItemPayload::isInput,
            FilterData.STREAM_CODEC, C2SUpdateFilterOnItemPayload::filter,
            C2SUpdateFilterOnItemPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SUpdateFilterOnItemPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            LinkManager manager = LinkManager.get(serverLevel);
            long key = LinkManager.posToKey(payload.pos(), payload.face());
            FaceConfigComposite config = manager.getFaceConfig(key);
            if (config == null) return;
            if (!config.canPlayerModify(player)) return;

            TransferType type = TransferRegistries.get(payload.typeId());
            if (type == null) return;

            int slotIndex = payload.isInput() ? 0 : 1;
            ItemStack upgradeStack = config.filterConfig.getUpgrades().getStackInSlot(slotIndex);
            if (upgradeStack.isEmpty()) return;

            upgradeStack.set(SLDataComponents.FILTER_DATA.get(), payload.filter());
            config.markDirty();
            manager.refreshLocalCache(key, payload.pos(), payload.face(), config);
            manager.syncConfigToClients(payload.pos());

            manager.activateNode(key, payload.pos(), payload.face(), config);
            if (player instanceof ServerPlayer serverPlayer) {
                S2CSyncFaceConfigPacket syncPacket = new S2CSyncFaceConfigPacket(GlobalPos.of(serverLevel.dimension(), payload.pos()), payload.face(), config);
                GroupService.syncToTeamMembers(serverPlayer, syncPacket);
            }
        });
    }
}