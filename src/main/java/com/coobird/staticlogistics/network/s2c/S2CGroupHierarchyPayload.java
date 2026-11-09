package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.logistics.group.GroupHierarchy;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2CGroupHierarchyPayload(CompoundTag data) implements CustomPacketPayload {
    public S2CGroupHierarchyPayload {
        CompoundTag source = data == null ? new CompoundTag() : data;
        data = GroupHierarchy.sanitize(source);
        data.put("owner_permissions", source.getCompound("owner_permissions").copy());
        data.put("owner_names", source.getCompound("owner_names").copy());
        data.put("owner_team_permissions", source.getCompound("owner_team_permissions").copy());
        data.put("my_grants", source.getCompound("my_grants").copy());
        data.put("my_grant_names", source.getCompound("my_grant_names").copy());
        data.putBoolean("team_integration_active", source.getBoolean("team_integration_active"));
        data.putBoolean("my_team_access_enabled", source.getBoolean("my_team_access_enabled"));
    }

    public static final Type<S2CGroupHierarchyPayload> TYPE = new Type<>(StaticLogistics.asResource("group_hierarchy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CGroupHierarchyPayload> STREAM_CODEC = new StreamCodec<>() {
        public S2CGroupHierarchyPayload decode(RegistryFriendlyByteBuf buf) {
            return new S2CGroupHierarchyPayload(buf.readNbt());
        }

        public void encode(RegistryFriendlyByteBuf buf, S2CGroupHierarchyPayload value) {
            buf.writeNbt(value.data());
        }
    };

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CGroupHierarchyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen)
                screen.acceptHierarchy(payload.data());
        });
    }
}
