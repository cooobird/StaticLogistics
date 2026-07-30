package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record C2SUpdateToolSettingsPayload(String groupId, @Nullable GroupKey groupKey, int mode,
                                           List<ResourceLocation> selectedTypeIds,
                                           int legacySelectedTypesMask,
                                           @Nullable ConnectionKey connectionKey) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolSettingsPayload> TYPE = new Type<>(StaticLogistics.asResource("update_tool_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SUpdateToolSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
            String groupId = BoundedNetworkCodecs.GROUP_NAME.decode(buffer);
            GroupKey groupKey = buffer.readBoolean() ? GroupKey.STREAM_CODEC.decode(buffer) : null;
            int mode = ByteBufCodecs.VAR_INT.decode(buffer);
            List<ResourceLocation> types = BoundedNetworkCodecs.TRANSFER_TYPE_IDS.decode(buffer);
            int legacyMask = ByteBufCodecs.VAR_INT.decode(buffer);
            ConnectionKey connectionKey =
                buffer.readBoolean() ? ConnectionKey.STREAM_CODEC.decode(buffer) : null;
            return new C2SUpdateToolSettingsPayload(
                groupId, groupKey, mode, types, legacyMask, connectionKey);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, C2SUpdateToolSettingsPayload payload) {
            BoundedNetworkCodecs.GROUP_NAME.encode(buffer, payload.groupId());
            buffer.writeBoolean(payload.groupKey() != null);
            if (payload.groupKey() != null) GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.mode());
            BoundedNetworkCodecs.TRANSFER_TYPE_IDS.encode(buffer, payload.selectedTypeIds());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.legacySelectedTypesMask());
            buffer.writeBoolean(payload.connectionKey() != null);
            if (payload.connectionKey() != null) {
                ConnectionKey.STREAM_CODEC.encode(buffer, payload.connectionKey());
            }
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
            ConnectionKey finalConnection = payload.connectionKey();
            if (finalConnection != null) {
                if (selectedGroup == null
                    || !selectedGroup.key().equals(finalConnection.groupKey())
                    || !(player instanceof ServerPlayer serverPlayer)) {
                    return;
                }
                if (!new ConnectionCommandService(serverPlayer.server)
                    .isSelectable(serverPlayer, finalConnection)) {
                    finalConnection = null;
                }
            }

            // 所有字段验证通过后再一次性提交，避免畸形请求产生部分写入。
            stack.set(SLDataComponents.SELECTED_GROUP.get(), finalId);
            if (selectedGroup == null) stack.remove(SLDataComponents.SELECTED_GROUP_KEY.get());
            else stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), selectedGroup.key());
            if (finalConnection == null) {
                stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
            } else {
                stack.set(SLDataComponents.SELECTED_CONNECTION_KEY.get(), finalConnection);
            }
            stack.set(SLDataComponents.SELECTED_TYPES.get(), finalTypes);
            int storedMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
            int unresolvedMask = storedMask & ~activeMask;
            stack.set(SLDataComponents.SELECTED_TYPES_MASK.get(),
                TransferTypeSelection.toMask(finalTypes, TransferRegistries.getAllActive())
                    | unresolvedMask);

            ToolMode validatedMode = ToolMode.fromId(payload.mode());
            int vMode = validatedMode.getId();
            stack.set(SLDataComponents.TOOL_MODE.get(), vMode);
        });
    }
}
