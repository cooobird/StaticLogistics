package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.logistics.node.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.util.LinkedHashMap;
import java.util.Map;

/// 由维度管理器持有的 Forge 能力缓存，跟随 LazyOptional 失效。
public final class CapabilityCache {
    private static final int MAX_ENTRIES = 16_384;
    private final ServerLevel level;
    private final Map<Key, LazyOptional<?>> entries = new LinkedHashMap<>(256, 0.75F, true);
    private boolean active = true;

    private record Key(long pos, Direction face, Capability<?> capability) {
    }

    public CapabilityCache(ServerLevel level) {
        this.level = level;
    }

    public static <C> C get(ServerLevel level, BlockPos pos, Direction face, Capability<C> capability) {
        return LinkManager.get(level).getCapability(pos, face, capability);
    }

    @SuppressWarnings("unchecked")
    public <C> C get(BlockPos pos, Direction face, Capability<C> capability) {
        if (!active) throw new IllegalStateException("Capability cache is not active");
        Key key = new Key(pos.asLong(), face, capability);
        LazyOptional<?> cached = entries.get(key);
        if (cached != null) return ((LazyOptional<C>) cached).orElse(null);
        var entity = level.getBlockEntity(pos);
        if (entity == null) return null;
        LazyOptional<C> optional = entity.getCapability(capability, face);
        if (!optional.isPresent()) return null;
        if (entries.size() == MAX_ENTRIES) entries.remove(entries.keySet().iterator().next());
        entries.put(key, optional);
        optional.addListener(ignored -> {
            if (entries.remove(key, optional)) NodeQueryService.invalidateFace(level, pos, face);
        });
        return optional.orElse(null);
    }

    public void invalidateFace(BlockPos pos, Direction face) {
        entries.keySet().removeIf(key -> key.pos() == pos.asLong() && key.face() == face);
        NodeQueryService.invalidateFace(level, pos, face);
    }

    public void invalidateBlock(BlockPos pos) {
        entries.keySet().removeIf(key -> key.pos() == pos.asLong());
        NodeQueryService.invalidateBlock(level, pos);
    }

    public void shutdown() {
        active = false;
        entries.clear();
    }
}
