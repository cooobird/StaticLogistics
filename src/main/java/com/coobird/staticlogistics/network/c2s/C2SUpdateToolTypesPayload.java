package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/// 仅更新连接配置器的默认传输类型。
public record C2SUpdateToolTypesPayload(List<ResourceLocation> selectedTypeIds) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolTypesPayload> TYPE = new Type<>(StaticLogistics.asResource("update_tool_types"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolTypesPayload> STREAM_CODEC =
        BoundedNetworkCodecs.TRANSFER_TYPE_IDS.map(C2SUpdateToolTypesPayload::new,
            C2SUpdateToolTypesPayload::selectedTypeIds);

    public C2SUpdateToolTypesPayload {
        selectedTypeIds = List.copyOf(selectedTypeIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SUpdateToolTypesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = ToolSettingsTarget.findConfigurator(context.player());
            if (stack.isEmpty()) return;

            List<ResourceLocation> submittedTypes =
                TransferTypeSelection.sanitize(payload.selectedTypeIds());
            if (submittedTypes.size() != payload.selectedTypeIds().size()
                || submittedTypes.stream().anyMatch(id -> TransferRegistries.get(id) == null)) {
                return;
            }

            stack.set(SLDataComponents.SELECTED_TYPES.get(), submittedTypes);
        });
    }
}
