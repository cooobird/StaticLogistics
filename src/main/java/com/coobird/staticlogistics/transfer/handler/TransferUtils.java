package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.transfer.TransferLogManager;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class TransferUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <C, T> boolean doTransferNodes(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, BlockCapability<C, Direction> cap,
        int limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        if (context != null && context.isDepthExceeded()) {
            LOGGER.debug("Depth exceeded for transfer at {} (depth={})", localPos, context.depth());
            return false;
        }
        if (destinations.isEmpty() || limit <= 0) return false;

        int remaining = limit;

        LinkManager localMgr = LinkManager.get(localLevel);
        ContainerConfig localContainer = localMgr.getContainerConfig(localPos);
        if (localContainer == null && context != null && context.sourceConfig() != null) {
            localContainer = context.sourceConfig().sharedContainerConfig;
        }
        if (localContainer == null) return false;

        boolean canCrossDim = LogisticsCalculator.isDimensionEffective(localContainer);
        C localCap = localLevel.getCapability(cap, localPos, localFace);
        if (localCap == null) return false;

        boolean movedAny = false;

        for (LogisticsNode remoteNode : destinations) {
            boolean isSameDim = remoteNode.isInSameDimension(localLevel.dimension());

            if (!isSameDim && !canCrossDim) continue;

            if (isSameDim && !LogisticsCalculator.isWithinRange(localPos, remoteNode.gPos().pos(), localContainer)) {
                continue;
            }

            ServerLevel remoteLevel = isSameDim ? localLevel :
                localLevel.getServer().getLevel(remoteNode.gPos().dimension());

            if (remoteLevel == null || !remoteLevel.getChunkSource().hasChunk(
                remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4))
                continue;

            C remoteCap = remoteLevel.getCapability(cap, remoteNode.gPos().pos(), remoteNode.face());
            if (remoteCap == null) continue;

            C from = isPullMode ? remoteCap : localCap;
            C to = isPullMode ? localCap : remoteCap;

            while (remaining > 0) {
                ExtractionResult<T> result = protocol.simulateExtract(from, remaining);
                if (protocol.isEmpty(result)) break;

                if (!protocol.canInsert(to, result.value(), remoteNode)) break;

                int accepted = protocol.executeInsert(to, result.value());
                if (accepted <= 0) break;

                protocol.commitExtract(from, result, accepted);
                remaining -= accepted;
                movedAny = true;

                if (context != null) {
                    LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
                    LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
                    TransferLogManager.get().logTransfer(srcNode, dstNode, context.type(), accepted, true);
                }
            }
            if (remaining <= 0) break;
        }
        return movedAny;
    }

    public static boolean hasLogisticsCapability(Level level, BlockPos pos, Direction face) {
        return TransferRegistries.getAllActive().stream().anyMatch(type -> {
            var cap = type.capability();
            if (cap == null) return false;
            return level.getCapability(cap, pos, face) != null
                || level.getCapability(cap, pos, null) != null;
        });
    }

    public interface TransferProtocol<C, T> {
        ExtractionResult<T> simulateExtract(C source, int max);

        int executeInsert(C dest, T stack);

        void commitExtract(C source, ExtractionResult<T> result, int actual);

        boolean isEmpty(ExtractionResult<T> result);

        /**
         * 可选目标端过滤检查：false 则跳过插入
         */
        default boolean canInsert(C dest, T stack, LogisticsNode targetNode) {
            return true;
        }
    }

    public record SimpleProtocol<C, T>(
        BiFunction<C, Integer, T> extractor,
        BiFunction<C, T, Integer> inserter,
        TriConsumer<C, T, Integer> committer,
        Predicate<T> emptyChecker,
        @javax.annotation.Nullable java.util.function.BiPredicate<T, LogisticsNode> targetFilter
    ) implements TransferProtocol<C, T> {
        public SimpleProtocol(BiFunction<C, Integer, T> extractor, BiFunction<C, T, Integer> inserter,
                              TriConsumer<C, T, Integer> committer, Predicate<T> emptyChecker) {
            this(extractor, inserter, committer, emptyChecker, null);
        }

        @Override
        public ExtractionResult<T> simulateExtract(C source, int max) {
            T value = extractor.apply(source, max);
            return ExtractionResult.of(value);
        }

        @Override
        public int executeInsert(C dest, T stack) {
            return inserter.apply(dest, stack);
        }

        @Override
        public void commitExtract(C source, ExtractionResult<T> result, int actual) {
            committer.accept(source, result.value(), actual);
        }

        @Override
        public boolean isEmpty(ExtractionResult<T> result) {
            return emptyChecker.test(result.value());
        }

        @Override
        public boolean canInsert(C dest, T stack, LogisticsNode targetNode) {
            return targetFilter == null || targetFilter.test(stack, targetNode);
        }
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}