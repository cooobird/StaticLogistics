package com.coobird.staticlogistics.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/** 按玩家限制高放大倍率的客户端配置请求；弱键保证离线玩家不会被静态状态留存。 */
public final class ServerPacketRateLimiter {
    public enum Action {
        FILTER_UPDATE(24),
        FACE_CONFIGURATION(80),
        BLUEPRINT_UNDO(2);

        private final int maximumPerSecond;

        Action(int maximumPerSecond) {
            this.maximumPerSecond = maximumPerSecond;
        }
    }

    private static final int WINDOW_TICKS = 20;
    private static final Map<ServerPlayer, EnumMap<Action, Window>> WINDOWS = new WeakHashMap<>();

    private ServerPacketRateLimiter() {
    }

    public static synchronized boolean allow(ServerPlayer player, Action action) {
        int now = player.getServer().getTickCount();
        EnumMap<Action, Window> playerWindows = WINDOWS.computeIfAbsent(
            player, ignored -> new EnumMap<>(Action.class));
        Window window = playerWindows.get(action);
        if (window == null || now < window.startedAt
            || (long) now - window.startedAt >= WINDOW_TICKS) {
            playerWindows.put(action, new Window(now, 1));
            return true;
        }
        if (window.requests >= action.maximumPerSecond) return false;
        window.requests++;
        return true;
    }

    private static final class Window {
        private final int startedAt;
        private int requests;

        private Window(int startedAt, int requests) {
            this.startedAt = startedAt;
            this.requests = requests;
        }
    }
}
