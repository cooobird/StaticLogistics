package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import com.coobird.staticlogistics.gui.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SConfigureFacePayload(BlockPos pos, Direction face, CompoundTag data) implements CustomPacketPayload {
    public static final Type<C2SConfigureFacePayload> TYPE = new Type<>(Staticlogistics.asResource("configure_face"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfigureFacePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, C2SConfigureFacePayload::pos,
        Direction.STREAM_CODEC, C2SConfigureFacePayload::face,
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

            LinkManager manager = LinkManager.get(serverLevel);
            long key = LinkManager.posToKey(payload.pos(), payload.face());
            FaceConfigComposite config = manager.getFaceConfig(key);
            if (config == null) return;
            if (!config.canPlayerModify(player)) return;

            CompoundTag tag = payload.data();

            if (tag.contains("open_filter")) {
                if (player.containerMenu instanceof FaceConfiguratorMenu faceMenu) {
                    BlockPos pos = faceMenu.getPos();
                    Direction face = faceMenu.getFace();
                    boolean isInput = tag.getBoolean("is_input");
                    int slotIndex = isInput ? 0 : 1;
                    ItemStack upgradeStack = faceMenu.getSlot(slotIndex).getItem();
                    player.openMenu(
                        new SimpleMenuProvider((id, inv, p) -> new FilterConfiguratorMenu(id, inv, pos, face, null, config, isInput, upgradeStack),
                            Component.translatable("gui.staticlogistics.filter.title")),
                        buf -> {
                            buf.writeBlockPos(pos);
                            buf.writeEnum(face);
                            buf.writeResourceLocation(Staticlogistics.asResource("item"));
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
                    player.openMenu(
                        new SimpleMenuProvider((id, inv, p) -> new FaceConfiguratorMenu(id, inv, pos, face),
                            Component.translatable("gui.staticlogistics.face_config")),
                        buf -> {
                            buf.writeBlockPos(pos);
                            buf.writeEnum(face);
                        }
                    );
                }
                return;
            }

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
                if (tag.contains("inputChannel")) {
                    int newVal = tag.getInt("inputChannel");
                    if (newVal < 1) newVal = 1;
                    if (newVal > 16) newVal = 16;
                    if (newVal != menu.getInputChannel()) {
                        menu.setInputChannel(newVal);
                        changed[0] = true;
                    }
                }
                if (tag.contains("outputChannel")) {
                    int newVal = tag.getInt("outputChannel");
                    if (newVal < 1) newVal = 1;
                    if (newVal > 16) newVal = 16;
                    if (newVal != menu.getOutputChannel()) {
                        menu.setOutputChannel(newVal);
                        changed[0] = true;
                    }
                }
                if (tag.contains("priority")) {
                    int newVal = tag.getInt("priority");
                    if (newVal != menu.getPriority()) {
                        menu.setPriority(newVal);
                        changed[0] = true;
                    }
                }
                if (tag.contains("strategy")) {
                    DistributionStrategy newVal = DistributionStrategy.byName(tag.getString("strategy"), DistributionStrategy.SEQUENTIAL);
                    if (newVal != menu.getStrategy()) {
                        menu.setStrategy(newVal);
                        changed[0] = true;
                    }
                }
                if (tag.contains("selected_types_mask")) {
                    int mask = tag.getInt("selected_types_mask");
                    if (menu.getSelectedTypesMask() != mask) {
                        menu.setSelectedTypesMask(mask);
                        changed[0] = true;
                    }
                }
                menu.syncToSlots();
                menu.broadcastChanges();
            }

            if (changed[0]) {
                config.markDirty();
                LogisticsNode selfNode = new LogisticsNode(GlobalPos.of(serverLevel.dimension(), payload.pos()), payload.face());
                GlobalLogisticsManager.get(serverLevel.getServer()).syncGroupLinks(serverLevel, config.faceConfig.getGroupId(), selfNode);
                manager.activateNode(key, payload.pos(), payload.face(), config);

                if (player instanceof ServerPlayer serverPlayer) {
                    S2CSyncFaceConfigPacket syncPacket = new S2CSyncFaceConfigPacket(GlobalPos.of(serverLevel.dimension(), payload.pos()), payload.face(), config);
                    GroupService.syncToTeamMembers(serverPlayer, syncPacket);
                }
            }
        });
    }
}