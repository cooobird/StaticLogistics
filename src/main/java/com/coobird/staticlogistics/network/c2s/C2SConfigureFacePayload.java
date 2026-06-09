package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.gui.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.gui.menu.NodeConfiguratorMenu;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.logic.group.GroupService;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPayload;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SConfigureFacePayload(BlockPos pos, Direction face, CompoundTag data) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("configure_face");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SConfigureFacePayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SConfigureFacePayload decode(PortRegistryFriendlyByteBuf buffer) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            BlockPos pos = fbuf.readBlockPos();
            Direction face = fbuf.readEnum(Direction.class);
            CompoundTag data = fbuf.readNbt();
            return new C2SConfigureFacePayload(pos, face, data);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SConfigureFacePayload value) {
            net.minecraft.network.FriendlyByteBuf fbuf = buffer;
            fbuf.writeBlockPos(value.pos());
            fbuf.writeEnum(value.face());
            fbuf.writeNbt(value.data());
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
        long key = LinkManager.posToKey(pos(), face());
        FaceConfigComposite config = manager.getFaceConfig(key);
        if (config == null) return;
        if (!config.canPlayerModify(player)) return;
        CompoundTag tag = data();
        if (tag.contains("open_filter")) {
            if (player.containerMenu instanceof NodeConfiguratorMenu faceMenu) {
                BlockPos pos = faceMenu.getPos();
                Direction face = faceMenu.getFace();
                boolean isInput = tag.getBoolean("is_input");
                int slotIndex = isInput ? 0 : 1;
                ItemStack upgradeStack = faceMenu.getSlot(slotIndex).getItem();
                NetworkHooks.openScreen(player,
                    new SimpleMenuProvider((id, inv, p) -> new FilterConfiguratorMenu(id, inv, pos, face, null, config, isInput, upgradeStack),
                        Component.translatable("gui.staticlogistics.filter.title")),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeEnum(face);
                        buf.writeResourceLocation(com.coobird.staticlogistics.StaticLogistics.asResource("item"));
                        buf.writeNbt(config.serializeNBT(null));
                        buf.writeBoolean(isInput);
                        buf.writeItem(upgradeStack);
                    });
            }
            return;
        }
        if (tag.contains("open_face_config")) {
            if (player.containerMenu instanceof FilterConfiguratorMenu filterMenu) {
                BlockPos pos = filterMenu.getPos();
                Direction face = filterMenu.getFace();
                NetworkHooks.openScreen(player,
                    new SimpleMenuProvider((id, inv, p) -> new NodeConfiguratorMenu(id, inv, pos, face),
                        Component.translatable("gui.staticlogistics.face_config")),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeEnum(face);
                    });
            }
            return;
        }
        boolean[] changed = {false};
        if (player.containerMenu instanceof NodeConfiguratorMenu menu) {
            changed[0] = menu.applyFromTag(tag);
        }
        if (changed[0]) {
            config.markDirty();
            LogisticsNode selfNode = new LogisticsNode(GlobalPos.of(serverLevel.dimension(), pos()), face());
            for (String gid : config.faceConfig.getGroupIds()) {
                GlobalLogisticsManager.get(serverLevel.getServer()).syncGroupLinks(serverLevel, gid, selfNode);
            }
            manager.activateNode(key, pos(), face(), config);
            S2CSyncFaceConfigPayload syncPacket = new S2CSyncFaceConfigPayload(GlobalPos.of(serverLevel.dimension(), pos()), face(), config);
            GroupService.syncToTeamMembers(player, syncPacket);
        }
    }
}
