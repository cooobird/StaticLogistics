package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一渲染玩家真实皮肤头像，并为不在玩家列表中的所有者异步补全档案纹理。
 */
public final class PlayerAvatarRenderer {
    private static final Map<UUID, ResourceLocation> SKIN_CACHE =
        new ConcurrentHashMap<>();
    private static final Set<UUID> REQUESTED_SKINS = ConcurrentHashMap.newKeySet();

    private PlayerAvatarRenderer() {
    }

    public static void render(GuiGraphics graphics, UUID playerId,
                              int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerInfo playerInfo = minecraft.getConnection() == null
            ? null : minecraft.getConnection().getPlayerInfo(playerId);
        ResourceLocation skin;
        if (playerInfo != null) {
            skin = playerInfo.getSkinLocation();
        } else {
            requestSkin(minecraft, playerId);
            skin = SKIN_CACHE.getOrDefault(
                playerId, DefaultPlayerSkin.getDefaultSkin(playerId));
        }
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    private static void requestSkin(Minecraft minecraft, UUID playerId) {
        if (!REQUESTED_SKINS.add(playerId)) return;
        minecraft.getSkinManager().registerSkins(
            new GameProfile(playerId,
                ClientLinkData.INSTANCE.getOwnerName(playerId)),
            (type, location, texture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    SKIN_CACHE.put(playerId, location);
                }
            }, false);
    }
}
