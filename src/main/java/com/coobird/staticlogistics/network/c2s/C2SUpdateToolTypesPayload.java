package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.List;

/**
 * 仅更新连接配置器的默认传输类型。
 */
public record C2SUpdateToolTypesPayload(
    List<ResourceLocation> selectedTypeIds,
    int legacySelectedTypesMask
) implements IPortPacket.C2S {
    public static final ResourceLocation ID =
        StaticLogistics.asResource("update_tool_types");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SUpdateToolTypesPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateToolTypesPayload decode(
            PortRegistryFriendlyByteBuf buffer
        ) {
            return new C2SUpdateToolTypesPayload(
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer),
                buffer.readVarInt());
        }

        @Override
        public void encode(
            PortRegistryFriendlyByteBuf buffer,
            C2SUpdateToolTypesPayload payload
        ) {
            BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(
                buffer, payload.selectedTypeIds());
            buffer.writeVarInt(payload.legacySelectedTypesMask());
        }
    };

    public C2SUpdateToolTypesPayload {
        selectedTypeIds = List.copyOf(selectedTypeIds);
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        ItemStack stack = ToolSettingsTarget.findConfigurator(player);
        if (stack.isEmpty()) return;

        List<ResourceLocation> submittedTypes =
            TransferTypeSelection.sanitize(selectedTypeIds);
        if (submittedTypes.size() != selectedTypeIds.size()
            || submittedTypes.stream().anyMatch(id -> TransferRegistries.get(id) == null)) {
            return;
        }

        int activeMask =
            TransferTypeSelection.activeLegacyMask(TransferRegistries.getAllActive());
        List<ResourceLocation> finalTypes = TransferTypeSelection.mergeIdsWithMask(
            submittedTypes, legacySelectedTypesMask & activeMask,
            TransferRegistries.getAllActive());
        int unresolvedMask = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.SELECTED_TYPES_MASK.get(), 0) & ~activeMask;

        PortItemStackExtension.setData(
            stack, SLDataComponents.SELECTED_TYPES.get(), finalTypes);
        PortItemStackExtension.setData(
            stack, SLDataComponents.SELECTED_TYPES_MASK.get(),
            TransferTypeSelection.toMask(finalTypes, TransferRegistries.getAllActive())
                | unresolvedMask);
    }
}
