package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.logistics.group.GroupHierarchy;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record S2CGroupHierarchyPayload(CompoundTag data) implements IPortPacket.S2C {
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

    public static final ResourceLocation ID = StaticLogistics.asResource("group_hierarchy");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CGroupHierarchyPayload> STREAM_CODEC = new PortStreamCodec<>() {
        public S2CGroupHierarchyPayload decode(PortRegistryFriendlyByteBuf buf) {
            return new S2CGroupHierarchyPayload(buf.readNbt());
        }

        public void encode(PortRegistryFriendlyByteBuf buf, S2CGroupHierarchyPayload value) {
            buf.writeNbt(value.data());
        }
    };

    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player packetPlayer) {
        S2CGroupHierarchyPayload payload = this;
        Player context = packetPlayer;

        if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen)
            screen.acceptHierarchy(payload.data());

    }
}
