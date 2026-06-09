package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.filter.FilterData;
import com.coobird.staticlogistics.logic.TransferRegistries;
import com.coobird.staticlogistics.logic.group.GroupService;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPayload;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SUpdateFilterOnItemPayload(BlockPos pos,
                                           Direction face,
                                           ResourceLocation typeId,
                                           boolean isInput,
                                           FilterData filter) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_filter_on_item");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateFilterOnItemPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateFilterOnItemPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            BlockPos pos = fbuf.readBlockPos();
            Direction face = fbuf.readEnum(Direction.class);
            ResourceLocation typeId = fbuf.readResourceLocation();
            boolean isInput = fbuf.readBoolean();
            FilterData filter = FilterData.STREAM_CODEC.decode(buffer);
            return new C2SUpdateFilterOnItemPayload(pos, face, typeId, isInput, filter);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SUpdateFilterOnItemPayload value) {
            FriendlyByteBuf fbuf = buffer;
            fbuf.writeBlockPos(value.pos());
            fbuf.writeEnum(value.face());
            fbuf.writeResourceLocation(value.typeId());
            fbuf.writeBoolean(value.isInput());
            FilterData.STREAM_CODEC.encode(buffer, value.filter());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        LinkManager manager = LinkManager.get(serverLevel);
        long key = LinkManager.posToKey(pos, face);
        FaceConfigComposite config = manager.getFaceConfig(key);
        if (config == null) return;
        if (!config.canPlayerModify(player)) return;
        LogisticsResource<?> type = TransferRegistries.get(typeId);
        if (type == null) return;
        int slotIndex = isInput ? 0 : 1;
        ItemStack upgradeStack = config.filterConfig.getUpgrades().getStackInSlot(slotIndex);
        if (upgradeStack.isEmpty()) return;
        PortItemStackExtension.setData(upgradeStack, SLDataComponents.FILTER_DATA, filter);
        config.markDirty();
        manager.refreshLocalCache(key, pos, face, config);
        manager.syncConfigToClients(pos);
        manager.activateNode(key, pos, face, config);
        S2CSyncFaceConfigPayload syncPacket = new S2CSyncFaceConfigPayload(GlobalPos.of(serverLevel.dimension(), pos), face, config);
        GroupService.syncToTeamMembers(player, syncPacket);
    }
}
