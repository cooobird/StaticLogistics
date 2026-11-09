package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.List;

/// 仅更新连接配置器的默认传输类型。
public record C2SUpdateToolTypesPayload(List<ResourceLocation> selectedTypeIds) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_tool_types");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateToolTypesPayload> STREAM_CODEC =
        BoundedNetworkCodecs.TRANSFER_TYPE_IDS.map(C2SUpdateToolTypesPayload::new,
            C2SUpdateToolTypesPayload::selectedTypeIds);

    public C2SUpdateToolTypesPayload {
        selectedTypeIds = List.copyOf(selectedTypeIds);
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SUpdateToolTypesPayload payload = this;
        Player context = packetPlayer;

        ItemStack stack = ToolSettingsTarget.findConfigurator(context);
        if (stack.isEmpty()) return;

        List<ResourceLocation> submittedTypes =
            TransferTypeSelection.sanitize(payload.selectedTypeIds());
        if (submittedTypes.size() != payload.selectedTypeIds().size()
            || submittedTypes.stream().anyMatch(id -> TransferRegistries.get(id) == null)) {
            return;
        }

        stack.set(SLDataComponents.SELECTED_TYPES.get(), submittedTypes);

    }
}
