package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import com.coobird.staticlogistics.gui.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public record C2SConfigureFacePayload(BlockPos pos, Direction face, ResourceLocation typeId,
                                      CompoundTag data) implements CustomPacketPayload {

    public static final Type<C2SConfigureFacePayload> TYPE = new Type<>(Staticlogistics.asResource("configure_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfigureFacePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, C2SConfigureFacePayload::pos,
        Direction.STREAM_CODEC, C2SConfigureFacePayload::face,
        ResourceLocation.STREAM_CODEC, C2SConfigureFacePayload::typeId,
        ByteBufCodecs.COMPOUND_TAG, C2SConfigureFacePayload::data,
        C2SConfigureFacePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SConfigureFacePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

            CompoundTag tag = payload.data();

            if (tag.contains("open_filter")) {
                if (player.containerMenu instanceof FaceConfiguratorMenu faceMenu) {
                    BlockPos pos = faceMenu.getPos();
                    Direction face = faceMenu.getFace();
                    TransferType currentType = faceMenu.getTransferType();
                    FaceConfigComposite config = faceMenu.getFaceConfig();
                    boolean isInput = tag.getBoolean("is_input");
                    int slotIndex = isInput ? 0 : 1;
                    ItemStack upgradeStack = faceMenu.getSlot(slotIndex).getItem();
                    player.openMenu(
                        new SimpleMenuProvider((id, inv, p) ->
                            new FilterConfiguratorMenu(id, inv, pos, face, currentType, config, isInput, upgradeStack),
                            Component.translatable("gui.staticlogistics.filter.title")),
                        buf -> {
                            buf.writeBlockPos(pos);
                            buf.writeEnum(face);
                            buf.writeResourceLocation(currentType.id());
                            CompoundTag configTag = config.serializeNBT(player.registryAccess());
                            buf.writeNbt(configTag);
                            buf.writeBoolean(isInput);
                            ItemStack.STREAM_CODEC.encode(buf, upgradeStack);
                        }
                    );
                }
                return;
            }

            if (tag.contains("open_face_config")) {
                if (player.containerMenu instanceof FilterConfiguratorMenu filterMenu) {
                    BlockPos pos = filterMenu.getPos();
                    Direction face = filterMenu.getFace();
                    TransferType currentType = filterMenu.getTransferType();
                    player.openMenu(
                        new SimpleMenuProvider((id, inv, p) ->
                            new FaceConfiguratorMenu(id, inv, pos, face, currentType),
                            Component.translatable("gui.staticlogistics.face_config")),
                        buf -> {
                            buf.writeBlockPos(pos);
                            buf.writeEnum(face);
                            buf.writeResourceLocation(currentType.id());
                        }
                    );
                }
                return;
            }

            LinkManager manager = LinkManager.get(serverLevel);
            FaceConfigComposite config = manager.getFaceConfig(LinkManager.posToKey(payload.pos(), payload.face()));
            if (config == null) return;
            if (!GroupService.canModify(config.faceConfig.getOwner(), player)) return;

            TransferType type = TransferRegistries.get(payload.typeId());
            if (type == null) return;

            LinkConfig.SideData sideData = config.linkConfig.getSettings(type);
            boolean[] changed = {false};

            if (player.containerMenu instanceof FaceConfiguratorMenu menu) {
                if (tag.contains("globalInput")) {
                    boolean newGlobalInput = tag.getBoolean("globalInput");
                    if (newGlobalInput != menu.isGlobalInputEnabled()) {
                        menu.setGlobalInputEnabled(newGlobalInput);
                        changed[0] = true;
                    }
                }
                if (tag.contains("globalOutput")) {
                    boolean newGlobalOutput = tag.getBoolean("globalOutput");
                    if (newGlobalOutput != menu.isGlobalOutputEnabled()) {
                        menu.setGlobalOutputEnabled(newGlobalOutput);
                        changed[0] = true;
                    }
                }
                update(tag, "inputChannel", t -> t.getInt("inputChannel"), sideData.inputChannel, (v, d) -> d.inputChannel = v, sideData, changed);
                update(tag, "outputChannel", t -> t.getInt("outputChannel"), sideData.outputChannel, (v, d) -> d.outputChannel = v, sideData, changed);
                update(tag, "priority", t -> t.getInt("priority"), sideData.priority, (v, d) -> d.priority = v, sideData, changed);
                update(tag, "strategy", t -> DistributionStrategy.byName(t.getString("strategy"), DistributionStrategy.SEQUENTIAL),
                    sideData.strategy, (v, d) -> d.strategy = v, sideData, changed);

                menu.syncToSlots();
                menu.broadcastChanges();

                if (tag.contains("switch_type")) {
                    String typeStr = tag.getString("switch_type");
                    ResourceLocation res = ResourceLocation.tryParse(typeStr);
                    if (res != null) {
                        TransferType newType = TransferRegistries.get(res);
                        if (newType != null) menu.switchTransferType(newType, player);
                    }
                }
                if (tag.contains("selected_types_mask")) {
                    int mask = tag.getInt("selected_types_mask");
                    if (menu.getSelectedTypesMask() != mask) {
                        menu.setSelectedTypesMask(mask);
                        changed[0] = true;
                    }
                }
                menu.broadcastChanges();
            }

            if (changed[0]) {
                config.markDirty();
                LogisticsNode selfNode = new LogisticsNode(GlobalPos.of(serverLevel.dimension(), payload.pos()), payload.face());
                GlobalLogisticsManager.get(serverLevel.getServer()).syncGroupLinks(serverLevel, config.faceConfig.getGroupId(), selfNode);
                long key = LinkManager.posToKey(payload.pos(), payload.face());
                manager.activateNode(key, payload.pos(), payload.face(), config);
            }
        });
    }

    private static <T> void update(CompoundTag tag, String key, Function<CompoundTag, T> getter, T currentVal,
                                   BiConsumer<T, LinkConfig.SideData> setter, LinkConfig.SideData data, boolean[] changed) {
        if (tag.contains(key)) {
            T newVal = getter.apply(tag);
            if (!Objects.equals(newVal, currentVal)) {
                setter.accept(newVal, data);
                changed[0] = true;
            }
        }
    }
}