package com.coobird.staticlogistics.logistics.redstone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * 一条连接所属的控制方案。共享检测点和模式的连接会作为同一方案共同启停。
 */
public record RedstoneControlBinding(GlobalPos controller, RedstoneControlMode mode) {
    public static final Codec<RedstoneControlBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        GlobalPos.CODEC.fieldOf("controller").forGetter(RedstoneControlBinding::controller),
        RedstoneControlMode.CODEC.optionalFieldOf("mode", RedstoneControlMode.HIGH).forGetter(RedstoneControlBinding::mode)
    ).apply(instance, RedstoneControlBinding::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RedstoneControlBinding> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public RedstoneControlBinding decode(RegistryFriendlyByteBuf buffer) {
                ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION, buffer.readResourceLocation());
                BlockPos position = buffer.readBlockPos();
                int modeIndex = buffer.readVarInt();
                RedstoneControlMode[] modes = RedstoneControlMode.values();
                RedstoneControlMode mode = modeIndex >= 0 && modeIndex < modes.length
                    ? modes[modeIndex] : RedstoneControlMode.HIGH;
                return new RedstoneControlBinding(GlobalPos.of(dimension, position), mode);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer,
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
