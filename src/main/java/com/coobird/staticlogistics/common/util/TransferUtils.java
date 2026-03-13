package com.coobird.staticlogistics.common.util;

import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class TransferUtils {

    private static final Map<CacheKey, BlockCapabilityCache<?, Direction>> CACHE_POOL = new ConcurrentHashMap<>();


    public static <C, T> boolean doTransfer(ServerLevel sourceLevel, List<StaticLink> links, BlockCapability<C, Direction> cap, int limit, int[] roundRobinCursor, TransferProtocol<C, T> protocol) {
        if (links.isEmpty() || limit <= 0) return false;

        C source = getOrCreateCache(sourceLevel, links.getFirst().sourcePos(), links.getFirst().sourceFace(), cap).getCapability();
        if (source == null) return false;

        boolean movedAny = false;
        int remaining = limit;
        int size = links.size();
        int startIndex = (roundRobinCursor != null) ? (roundRobinCursor[0] % size) : 0;

        for (int i = 0; i < size; i++) {
            if (remaining <= 0) break;
            int currentIndex = (startIndex + i) % size;
            StaticLink link = links.get(currentIndex);

            ServerLevel destLevel = link.destDimension().equals(sourceLevel.dimension()) ? sourceLevel : sourceLevel.getServer().getLevel(link.destDimension());

            if (destLevel == null || !destLevel.getChunkSource().hasChunk(link.destPos().getX() >> 4, link.destPos().getZ() >> 4))
                continue;

            C destination = getOrCreateCache(destLevel, link.destPos(), link.destFace(), cap).getCapability();
            if (destination == null) continue;

            boolean linkMoved = false;

            while (remaining > 0) {
                T available = protocol.simulateExtract(source, remaining);
                if (protocol.isEmpty(available)) break;

                int accepted = protocol.executeInsert(destination, available);
                if (accepted <= 0) break;

                protocol.commitExtract(source, available, accepted);
                remaining -= accepted;
                movedAny = true;
                linkMoved = true;
            }

            if (linkMoved && roundRobinCursor != null) {
                roundRobinCursor[0] = (currentIndex + 1) % size;
                break;
            }
        }
        return movedAny;
    }

    public static boolean hasLogisticsCapability(Level level, BlockPos pos, Direction face) {

        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face) != null
            || level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face) != null
            || level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) != null) {
            return true;
        }

        if (ModList.get().isLoaded("mekanism")) {
            try {
                if (level.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(), pos, face) != null) {
                    return true;
                }
            } catch (Throwable ignored) {

            }
        }

        return false;
    }

    public interface TransferProtocol<C, T> {
        T simulateExtract(C source, int max);

        int executeInsert(C dest, T stack);

        void commitExtract(C source, T stack, int actual);

        boolean isEmpty(T stack);
    }

    public record SimpleProtocol<C, T>(
        BiFunction<C, Integer, T> extractor,
        BiFunction<C, T, Integer> inserter,
        TriConsumer<C, T, Integer> committer,
        Predicate<T> emptyChecker
    ) implements TransferProtocol<C, T> {
        @Override
        public T simulateExtract(C source, int max) {
            return extractor.apply(source, max);
        }

        @Override
        public int executeInsert(C dest, T stack) {
            return inserter.apply(dest, stack);
        }

        @Override
        public void commitExtract(C source, T stack, int act) {
            committer.accept(source, stack, act);
        }

        @Override
        public boolean isEmpty(T stack) {
            return emptyChecker.test(stack);
        }
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }


    public record CacheKey(ResourceKey<Level> dimension, BlockPos pos, Direction side,
                           BlockCapability<?, Direction> cap) {
    }

    @SuppressWarnings("unchecked")
    private static <C> BlockCapabilityCache<C, Direction> getOrCreateCache(ServerLevel level, BlockPos pos, Direction side, BlockCapability<C, Direction> cap) {
        CacheKey key = new CacheKey(level.dimension(), pos.immutable(), side, cap);
        return (BlockCapabilityCache<C, Direction>) CACHE_POOL.computeIfAbsent(key, k -> BlockCapabilityCache.create(cap, level, pos, side));
    }

    public static void clearCache() {
        CACHE_POOL.clear();
    }
}