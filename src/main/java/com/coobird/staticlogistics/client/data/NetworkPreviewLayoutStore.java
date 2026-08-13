package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 客户端网络预览布局的唯一持久化仓库。
 *
 * <p>布局是玩家本地的显示偏好，不进入服务端物流数据和物品组件。仓库以稳定的
 * 分组身份和方块位置保存节点位置、缩放与平移；GUI 只读写内存状态，在界面
 * 关闭或退出存档时统一原子落盘，避免拖动过程中频繁访问磁盘。
 */
@OnlyIn(Dist.CLIENT)
public enum NetworkPreviewLayoutStore {
    INSTANCE;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_GROUPS = 4_096;
    private static final int MAX_NODES_PER_GROUP = 16_384;
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_VIEW_COORDINATE = 1_000_000;
    private static final double MIN_STORED_ZOOM = 0.1D;
    private static final double MAX_STORED_ZOOM = 4.0D;
    private static final Path FILE = FMLPaths.CONFIGDIR.get()
        .resolve("staticlogistics")
        .resolve("network-preview-layouts.json");

    private final Map<GroupKey, Layout> layouts = new LinkedHashMap<>();
    private boolean loaded;
    private boolean dirty;

    public Layout getOrCreate(GroupKey groupKey) {
        ensureLoaded();
        Objects.requireNonNull(groupKey, "groupKey");
        return layouts.computeIfAbsent(groupKey, ignored -> new Layout());
    }

    public void markDirty() {
        dirty = true;
    }

    public void remove(GroupKey groupKey) {
        ensureLoaded();
        if (layouts.remove(Objects.requireNonNull(groupKey, "groupKey")) != null) {
            dirty = true;
            flush();
        }
    }

    /**
     * 将当前内存快照写入磁盘。失败时保留 dirty 状态，允许下一次生命周期节点重试。
     */
    public void flush() {
        ensureLoaded();
        if (!dirty) return;

        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", FORMAT_VERSION);
            JsonArray groups = new JsonArray();
            layouts.forEach((groupKey, layout) ->
                groups.add(serializeGroup(groupKey, layout)));
            root.add("groups", groups);

            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            moveAtomically(temporary, FILE);
            dirty = false;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to save network preview layouts to {}", FILE, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupException) {
                LOGGER.warn("Failed to remove temporary network preview layout file {}",
                    temporary, cleanupException);
            }
        }
    }

    /**
     * 退出服务器或单人存档时先保存，再释放内存；下次进入时从磁盘重新加载。
     */
    public void closeSession() {
        flush();
        if (dirty) {
            /*
             * 写盘失败时保留内存与脏标记，允许后续关闭界面或会话再次尝试。
             * 直接清空会让一次瞬时 IO 故障永久丢失玩家布局。
             */
            return;
        }
        layouts.clear();
        loaded = false;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        layouts.clear();
        if (!Files.isRegularFile(FILE)) return;

        try {
            if (Files.size(FILE) > MAX_FILE_BYTES) {
                LOGGER.error("Network preview layout file is too large and will be ignored: {}",
                    FILE);
                return;
            }
            try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                int formatVersion = root.get("version").getAsInt();
                if (formatVersion < 1 || formatVersion > FORMAT_VERSION) {
                    LOGGER.warn("Unsupported network preview layout format in {}", FILE);
                    return;
                }
                JsonArray groups = root.getAsJsonArray("groups");
                int count = Math.min(groups.size(), MAX_GROUPS);
                for (int index = 0; index < count; index++) {
                    try {
                        readGroup(groups.get(index).getAsJsonObject());
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Skipped invalid network preview layout entry at index {}",
                            index, exception);
                    }
                }
                if (formatVersion < FORMAT_VERSION) dirty = true;
            }
        } catch (IOException | RuntimeException exception) {
            layouts.clear();
            LOGGER.error("Failed to load network preview layouts from {}", FILE, exception);
        }
    }

    private void readGroup(JsonObject json) {
        GroupKey groupKey = new GroupKey(UUID.fromString(json.get("owner").getAsString()),
            UUID.fromString(json.get("group").getAsString()));
        Layout layout = new Layout();
        layout.setView(
            finite(json.get("pan_x").getAsDouble(), 0.0D),
            finite(json.get("pan_y").getAsDouble(), 0.0D),
            clamp(finite(json.get("zoom").getAsDouble(), 1.0D),
                MIN_STORED_ZOOM, MAX_STORED_ZOOM));

        JsonArray nodes = json.getAsJsonArray("nodes");
        int count = Math.min(nodes.size(), MAX_NODES_PER_GROUP);
        for (int index = 0; index < count; index++) {
            JsonObject nodeJson = nodes.get(index).getAsJsonObject();
            ResourceLocation dimensionId = ResourceLocation.parse(nodeJson.get("dimension").getAsString());
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            GlobalPos node = GlobalPos.of(dimension, new BlockPos(
                    nodeJson.get("x").getAsInt(),
                    nodeJson.get("y").getAsInt(),
                nodeJson.get("z").getAsInt()));
            layout.nodePositions.putIfAbsent(node, new Position(
                clamp(finite(nodeJson.get("view_x").getAsDouble(), 0.0D),
                    -MAX_VIEW_COORDINATE, MAX_VIEW_COORDINATE),
                clamp(finite(nodeJson.get("view_y").getAsDouble(), 0.0D),
                    -MAX_VIEW_COORDINATE, MAX_VIEW_COORDINATE)));
        }
        layouts.put(groupKey, layout);
    }

    private static JsonObject serializeGroup(GroupKey groupKey, Layout layout) {
        JsonObject json = new JsonObject();
        json.addProperty("owner", groupKey.ownerId().toString());
        json.addProperty("group", groupKey.internalId().toString());
        json.addProperty("pan_x", layout.panX);
        json.addProperty("pan_y", layout.panY);
        json.addProperty("zoom", layout.zoom);
        JsonArray nodes = new JsonArray();
        layout.nodePositions.forEach((node, position) -> {
            JsonObject nodeJson = new JsonObject();
            nodeJson.addProperty("dimension",
                node.dimension().location().toString());
            nodeJson.addProperty("x", node.pos().getX());
            nodeJson.addProperty("y", node.pos().getY());
            nodeJson.addProperty("z", node.pos().getZ());
            nodeJson.addProperty("view_x", position.x());
            nodeJson.addProperty("view_y", position.y());
            nodes.add(nodeJson);
        });
        json.add("nodes", nodes);
        return json;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * 画布坐标保留双精度；只有最终绘制和命中检测才转换为屏幕像素。
     */
    public record Position(double x, double y) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException(
                    "Preview position must be finite");
            }
        }
    }

    /**
     * 单个分组的可变视图状态，仅由客户端渲染线程访问。
     */
    public static final class Layout {
        private final Map<GlobalPos, Position> nodePositions = new LinkedHashMap<>();
        private double panX;
        private double panY;
        private double zoom = 1.0D;

        public Map<GlobalPos, Position> nodePositions() {
            return nodePositions;
        }

        public double panX() {
            return panX;
        }

        public double panY() {
            return panY;
        }

        public double zoom() {
            return zoom;
        }

        public void setView(double panX, double panY, double zoom) {
            this.panX = panX;
            this.panY = panY;
            this.zoom = zoom;
        }
    }
}
