package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.List;

public record C2SUpdateToolSettingsPayload(
    String groupId,
    @Nullable GroupKey groupKey,
    int mode,
    List<ResourceLocation> selectedTypeIds,
    int legacySelectedTypesMask
) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_tool_settings");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateToolSettingsPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SUpdateToolSettingsPayload decode(PortRegistryFriendlyByteBuf buffer) {
                String groupId = BoundedNetworkCodecs.GROUP_NAME.decode(buffer);
                GroupKey groupKey = buffer.readBoolean() ? GroupKey.STREAM_CODEC.decode(buffer) : null;
                int mode = buffer.readVarInt();
                List<ResourceLocation> types = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
                int legacyMask = buffer.readVarInt();
                return new C2SUpdateToolSettingsPayload(
                    groupId, groupKey, mode, types, legacyMask);
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SUpdateToolSettingsPayload value) {
                BoundedNetworkCodecs.GROUP_NAME.encode(buffer, value.groupId());
                buffer.writeBoolean(value.groupKey() != null);
                if (value.groupKey() != null) GroupKey.STREAM_CODEC.encode(buffer, value.groupKey());
                buffer.writeVarInt(value.mode());
                BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, value.selectedTypeIds());
                buffer.writeVarInt(value.legacySelectedTypesMask());
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof LinkConfiguratorItem)
            && !(stack.getItem() instanceof BlueprintItem)) {
            stack = player.getItemInHand(InteractionHand.OFF_HAND);
            if (!(stack.getItem() instanceof LinkConfiguratorItem)
                && !(stack.getItem() instanceof BlueprintItem)) return;
        }

        GroupRef selectedGroup;
        String finalId;
        if (groupKey() != null) {
            if (player.getServer() == null) return;
            selectedGroup = PlayerGroupStore.get(player.getServer()).findGroup(groupKey());
            if (selectedGroup == null
                || !GroupService.canModify(selectedGroup.key().ownerId(), player)) return;
            finalId = selectedGroup.displayName();
        } else if (groupId().trim().isEmpty()) {
            selectedGroup = null;
            finalId = "";
        } else {
            if (player.getServer() == null) return;
            try {
                finalId = GroupConstraints.normalizeName(groupId());
            } catch (IllegalArgumentException exception) {
                return;
            }
            selectedGroup = PlayerGroupStore.get(player.getServer())
                .findGroup(player.getUUID(), finalId);
            if (selectedGroup == null) return;
            finalId = selectedGroup.displayName();
        }

        int activeMask = TransferTypeSelection.activeLegacyMask(TransferRegistries.getAllActive());
        List<ResourceLocation> submittedTypes = TransferTypeSelection.sanitize(selectedTypeIds());
        if (submittedTypes.size() != selectedTypeIds().size()
            || submittedTypes.stream().anyMatch(id -> TransferRegistries.get(id) == null)) return;
        List<ResourceLocation> finalTypes = TransferTypeSelection.mergeIdsWithMask(
            submittedTypes, legacySelectedTypesMask() & activeMask,
            TransferRegistries.getAllActive());

        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP.get(), finalId);
        if (selectedGroup == null) {
            PortItemStackExtension.removeData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        } else {
            PortItemStackExtension.setData(
                stack, SLDataComponents.SELECTED_GROUP_KEY.get(), selectedGroup.key());
        }
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_TYPES.get(), finalTypes);
        int storedMask = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        int unresolvedMask = storedMask & ~activeMask;
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_TYPES_MASK.get(),
            TransferTypeSelection.toMask(finalTypes, TransferRegistries.getAllActive())
                | unresolvedMask);

        int validMode = Mth.clamp(mode(), 0, ToolMode.values().length - 1);
        int currentMode = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.TOOL_MODE.get(), 0);
        if (currentMode == validMode) return;
        PortItemStackExtension.setData(stack, SLDataComponents.TOOL_MODE.get(), validMode);
        var nodes = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.STORED_NODES.get(), List.of());
        player.displayClientMessage(Component.translatable(
            nodes.isEmpty() ? "msg.staticlogistics.mode_switched"
                : "msg.staticlogistics.mode_switched_with_nodes",
            ToolMode.values()[validMode].getDisplayName(), nodes.size()), true);
    }
}
