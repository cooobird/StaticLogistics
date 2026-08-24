package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientRedstoneControlData;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 单条连接的红石控制状态。
 */
public record S2CRedstoneControlStatePayload(
    ConnectionKey connection, @Nullable RedstoneControlBinding binding,
    boolean powered, boolean allowed
) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("redstone_control_state");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        S2CRedstoneControlStatePayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public S2CRedstoneControlStatePayload decode(PortRegistryFriendlyByteBuf buffer) {
            ConnectionKey connection = ConnectionKey.STREAM_CODEC.decode(buffer);
            RedstoneControlBinding binding = buffer.readBoolean()
                ? RedstoneControlBinding.STREAM_CODEC.decode(buffer) : null;
            return new S2CRedstoneControlStatePayload(
                connection, binding, buffer.readBoolean(), buffer.readBoolean());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer,
                           S2CRedstoneControlStatePayload payload) {
            ConnectionKey.STREAM_CODEC.encode(buffer, payload.connection());
            buffer.writeBoolean(payload.binding() != null);
            if (payload.binding() != null) {
                RedstoneControlBinding.STREAM_CODEC.encode(buffer, payload.binding());
            }
            buffer.writeBoolean(payload.powered());
            buffer.writeBoolean(payload.allowed());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        Minecraft.getInstance().execute(() -> ClientRedstoneControlData.INSTANCE.accept(
            connection(), binding(), powered(), allowed()));
    }
}
