package com.coobird.staticlogistics.client.gui.component;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 统一渲染玩家真实皮肤头像，并为不在玩家列表中的所有者异步补全档案纹理。
 */
public final class PlayerAvatarRenderer {
    private static final int MAX_CACHED_SKINS = 256;
    private static final Map<UUID, CompletableFuture<PlayerSkin>> SKIN_CACHE =
        new LinkedHashMap<>(32, 0.75F, true);

    private PlayerAvatarRenderer() {
    }

    public static void render(GuiGraphics graphics, UUID playerId, int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerInfo playerInfo = minecraft.getConnection() == null
            ? null : minecraft.getConnection().getPlayerInfo(playerId);
        PlayerSkin skin = playerInfo == null
            ? getOrLoadSkin(playerId)
            .getNow(DefaultPlayerSkin.get(playerId))
            : playerInfo.getSkin();
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    private static CompletableFuture<PlayerSkin> getOrLoadSkin(UUID playerId) {
        synchronized (SKIN_CACHE) {
            CompletableFuture<PlayerSkin> cached = SKIN_CACHE.get(playerId);
            if (cached != null) return cached;
            CompletableFuture<PlayerSkin> loaded = loadSkin(playerId);
            SKIN_CACHE.put(playerId, loaded);
            while (SKIN_CACHE.size() > MAX_CACHED_SKINS) {
                SKIN_CACHE.remove(SKIN_CACHE.keySet().iterator().next());
            }
            return loaded;
        }
    }

    private static CompletableFuture<PlayerSkin> loadSkin(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        return CompletableFuture.supplyAsync(
                () -> minecraft.getMinecraftSessionService()
                    .fetchProfile(playerId, true),
                Util.nonCriticalIoPool())
            .thenCompose(result -> loadProfileSkin(minecraft, playerId, result))
            .exceptionally(error -> DefaultPlayerSkin.get(playerId));
    }

    private static CompletableFuture<PlayerSkin> loadProfileSkin(
        Minecraft minecraft, UUID playerId, ProfileResult result
    ) {
        if (result == null) {
            return CompletableFuture.completedFuture(
                DefaultPlayerSkin.get(playerId));
        }
        return minecraft.getSkinManager().getOrLoad(result.profile());
    }
}
