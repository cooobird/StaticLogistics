package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public record C2SUpdateToolSettingsPayload(String groupId, @Nullable GroupKey groupKey, int mode,
                                           List<ResourceLocation> selectedTypeIds,
                                           int legacySelectedTypesMask) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolSettingsPayload> TYPE = new Type<>(StaticLogistics.asResource("update_tool_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SUpdateToolSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
            String groupId = BoundedNetworkCodecs.GROUP_NAME.decode(buffer);
            GroupKey groupKey = buffer.readBoolean() ? GroupKey.STREAM_CODEC.decode(buffer) : null;
            int mode = ByteBufCodecs.VAR_INT.decode(buffer);
            List<ResourceLocation> types = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
            int legacyMask = ByteBufCodecs.VAR_INT.decode(buffer);
            return new C2SUpdateToolSettingsPayload(groupId, groupKey, mode, types, legacyMask);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, C2SUpdateToolSettingsPayload payload) {
            BoundedNetworkCodecs.GROUP_NAME.encode(buffer, payload.groupId());
            buffer.writeBoolean(payload.groupKey() != null);
            if (payload.groupKey() != null) GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.mode());
            BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, payload.selectedTypeIds());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.legacySelectedTypesMask());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SUpdateToolSettingsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(stack.getItem() instanceof LinkConfiguratorItem) && !(stack.getItem() instanceof BlueprintItem)) {
                stack = player.getItemInHand(InteractionHand.OFF_HAND);
                if (!(stack.getItem() instanceof LinkConfiguratorItem) && !(stack.getItem() instanceof BlueprintItem))
                    return;
            }

            GroupRef selectedGroup = null;
            String finalId;
            if (payload.groupKey() != null) {
                if (player.getServer() == null) return;
                selectedGroup = PlayerGroupStore.get(player.getServer()).findGroup(payload.groupKey());
                if (selectedGroup == null
                    || !GroupService.canModify(selectedGroup.key().ownerId(), player)) return;
                // 稳定分组键存在时，以服务端目录名称为权威，避免重命名竞态和客户端名称改写。
                finalId = selectedGroup.displayName();
            } else if (payload.groupId().trim().isEmpty()) {
                selectedGroup = null;
                finalId = "";
            } else {
                // 旧版工具只有显示名；首次提交时仅允许迁移到玩家自己的稳定分组键。
                if (player.getServer() == null) return;
                try {
                    finalId = GroupConstraints.normalizeName(payload.groupId());
                } catch (IllegalArgumentException exception) {
                    return;
                }
                selectedGroup = PlayerGroupStore.get(player.getServer())
                    .findGroup(player.getUUID(), finalId);
                if (selectedGroup == null) return;
                finalId = selectedGroup.displayName();
            }

            int activeMask = TransferTypeSelection.activeLegacyMask(TransferRegistries.getAllActive());
            List<ResourceLocation> submittedTypes = TransferTypeSelection.sanitize(payload.selectedTypeIds());
            if (submittedTypes.size() != payload.selectedTypeIds().size()
                || submittedTypes.stream().anyMatch(id -> TransferRegistries.get(id) == null)) return;
            List<ResourceLocation> finalTypes = TransferTypeSelection.mergeIdsWithMask(
                submittedTypes, payload.legacySelectedTypesMask() & activeMask,
                TransferRegistries.getAllActive());

            // 所有字段验证通过后再一次性提交，避免畸形请求产生部分写入。
            stack.set(SLDataComponents.SELECTED_GROUP.get(), finalId);
            if (selectedGroup == null) stack.remove(SLDataComponents.SELECTED_GROUP_KEY.get());
            else stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), selectedGroup.key());
            stack.set(SLDataComponents.SELECTED_TYPES.get(), finalTypes);
            int storedMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
            int unresolvedMask = storedMask & ~activeMask;
            stack.set(SLDataComponents.SELECTED_TYPES_MASK.get(),
                TransferTypeSelection.toMask(finalTypes, TransferRegistries.getAllActive())
                    | unresolvedMask);

            int vMode = Mth.clamp(payload.mode(), 0, ToolMode.values().length - 1);
            int currentMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);

            if (currentMode != vMode) {
                stack.set(SLDataComponents.TOOL_MODE.get(), vMode);
                var nodes = stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of());
                player.displayClientMessage(Component.translatable(
                    nodes.isEmpty() ? "msg.staticlogistics.mode_switched" : "msg.staticlogistics.mode_switched_with_nodes",
                    ToolMode.values()[vMode].getDisplayName(),
                    nodes.size()
                ), true);
            }
        });
    }
}
