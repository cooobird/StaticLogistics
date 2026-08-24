package com.coobird.staticlogistics.logistics.redstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.Objects;

/**
 * 一条连接所属的红石控制方案。共享检测点和模式的连接会共同启停。
 */
public record RedstoneControlBinding(GlobalPos controller, RedstoneControlMode mode) {
    public static final Codec<RedstoneControlBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        GlobalPos.CODEC.fieldOf("controller").forGetter(RedstoneControlBinding::controller),
        RedstoneControlMode.CODEC.optionalFieldOf("mode", RedstoneControlMode.HIGH)
            .forGetter(RedstoneControlBinding::mode)
    ).apply(instance, RedstoneControlBinding::new));

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, RedstoneControlBinding>
        STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public RedstoneControlBinding decode(PortRegistryFriendlyByteBuf buffer) {
            ResourceLocation dimensionId = buffer.readResourceLocation();
            ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimensionId);
            BlockPos position = buffer.readBlockPos();
            int modeIndex = buffer.readVarInt();
            RedstoneControlMode[] modes = RedstoneControlMode.values();
            RedstoneControlMode mode = modeIndex >= 0 && modeIndex < modes.length
                ? modes[modeIndex] : RedstoneControlMode.HIGH;
            return new RedstoneControlBinding(GlobalPos.of(dimension, position), mode);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer,
                           RedstoneControlBinding binding) {
            buffer.writeResourceLocation(binding.controller().dimension().location());
            buffer.writeBlockPos(binding.controller().pos());
            buffer.writeVarInt(binding.mode().ordinal());
        }
    };

    public RedstoneControlBinding {
        Objects.requireNonNull(controller, "Redstone controller position is required");
        Objects.requireNonNull(mode, "Redstone control mode is required");
    }
}
