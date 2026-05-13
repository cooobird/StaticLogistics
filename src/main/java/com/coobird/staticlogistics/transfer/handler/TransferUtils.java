package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.util.CapabilityCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class TransferUtils {

    public static <C, T> boolean doTransferNodes(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, BlockCapability<C, Direction> cap,
        int limit, TransferProtocol<C, T> protocol, boolean isPullMode
    ) {
        if (destinations.isEmpty() || limit <= 0) return false;

        LinkManager localMgr = LinkManager.get(localLevel);
        var localContainer = localMgr.getContainerConfig(localPos);
        if (localContainer == null) return false;

        double baseRadius = SLConfig.getDefaultRadius();
        int rangeMult = localContainer.getRangeMultiplier();
        boolean isInfiniteRange = rangeMult >= 1000000;
        double maxDistSq = Math.pow(baseRadius * rangeMult, 2);
        boolean canCrossDim = localContainer.isDimensionEffective();

        C localCap = CapabilityCache.getOrCreateCache(localLevel, localPos, localFace, cap).getCapability();
        if (localCap == null) return false;

        boolean movedAny = false;
        int remaining = limit;

        for (LogisticsNode remoteNode : destinations) {
            boolean isSameDim = remoteNode.gPos().dimension().equals(localLevel.dimension());

            if (!isSameDim && !canCrossDim) continue;

            if (isSameDim && !isInfiniteRange) {
                if (remoteNode.gPos().pos().distSqr(localPos) > maxDistSq) continue;
            }
            ServerLevel remoteLevel = isSameDim ? localLevel :
                localLevel.getServer().getLevel(remoteNode.gPos().dimension());

            if (remoteLevel == null || !remoteLevel.getChunkSource().hasChunk(
                remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4))
                continue;

            C remoteCap = CapabilityCache.getOrCreateCache(remoteLevel, remoteNode.gPos().pos(), remoteNode.face(), cap).getCapability();
            if (remoteCap == null) continue;

            C from = isPullMode ? remoteCap : localCap;
            C to = isPullMode ? localCap : remoteCap;

            while (remaining > 0) {
                T available = protocol.simulateExtract(from, remaining);
                if (protocol.isEmpty(available)) break;

                int accepted = protocol.executeInsert(to, available);
                if (accepted <= 0) break;

                protocol.commitExtract(from, available, accepted);
                remaining -= accepted;
                movedAny = true;
            }
            if (remaining <= 0) break;
        }
        return movedAny;
    }

    public static boolean hasLogisticsCapability(Level level, BlockPos pos, Direction face) {
        return TransferRegistries.getAllActive().stream().anyMatch(type -> {
            var cap = type.capability();
            return cap != null && level.getCapability(cap, pos, face) != null;
        });
    }

    public interface TransferProtocol<C, T> {
        T simulateExtract(C source, int max);

        int executeInsert(C dest, T stack);

        void commitExtract(C source, T stack, int actual);

        boolean isEmpty(T stack);
    }

    public record SimpleProtocol<C, T>(BiFunction<C, Integer, T> extractor, BiFunction<C, T, Integer> inserter,
                                       TriConsumer<C, T, Integer> committer,
                                       Predicate<T> emptyChecker) implements TransferProtocol<C, T> {
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
}