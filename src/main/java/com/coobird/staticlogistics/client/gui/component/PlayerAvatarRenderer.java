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

import java.util.*;

/**
 * 统一渲染玩家真实皮肤头像，并为不在玩家列表中的所有者异步补全档案纹理。
 */
public final class PlayerAvatarRenderer {
    private static final int MAX_CACHED_SKINS = 256;
    private static final Map<UUID, ResourceLocation> SKIN_CACHE =
        new LinkedHashMap<>(32, 0.75F, true);
    private static final Set<UUID> REQUESTED_SKINS = new LinkedHashSet<>();

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
            synchronized (SKIN_CACHE) {
                skin = SKIN_CACHE.getOrDefault(
                    playerId, DefaultPlayerSkin.getDefaultSkin(playerId));
            }
        }
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    private static void requestSkin(Minecraft minecraft, UUID playerId) {
        synchronized (REQUESTED_SKINS) {
            if (!REQUESTED_SKINS.add(playerId)) return;
            while (REQUESTED_SKINS.size() > MAX_CACHED_SKINS) {
                UUID evicted = REQUESTED_SKINS.iterator().next();
                REQUESTED_SKINS.remove(evicted);
                synchronized (SKIN_CACHE) {
                    SKIN_CACHE.remove(evicted);
                }
            }
        }
        minecraft.getSkinManager().registerSkins(
            new GameProfile(playerId,
                ClientLinkData.INSTANCE.getOwnerName(playerId)),
            (type, location, texture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    synchronized (REQUESTED_SKINS) {
                        if (!REQUESTED_SKINS.contains(playerId)) return;
                    }
                    synchronized (SKIN_CACHE) {
                        SKIN_CACHE.put(playerId, location);
                    }
                }
            }, false);
    }
}
