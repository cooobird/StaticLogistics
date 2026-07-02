package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.CapGetter;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logic.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * 传输工具类 —— 提供传输协议接口和 capability 查询工具。
 */
public class TransferUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <C, T> boolean doTransferNodes(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, CapGetter<C> capGetter,
        long limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        return TransferPipeline.execute(localLevel, localPos, localFace, destinations, capGetter, limit, protocol, isPullMode, context);
    }

    public static boolean hasLogisticsCapability(Level level, BlockPos pos, Direction face) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        for (var type : TransferRegistries.getAllActive()) {
            try {
                if (type.isPresent(serverLevel, pos, face)) return true;
            } catch (Exception e) {
                LOGGER.error("Capability check failed at {} face {} type {}: {}", pos, face, type.typeId(), e.getMessage());
            }
        }
        return false;
    }

    public static void clearDimCache(Level level) {
        CapabilityCache.clearDimension(level);
    }

    // ── 传输协议接口 ──

    public interface TransferProtocol<C, T> {
        ExtractionResult<T> simulateExtract(C source, long max);

        long executeInsert(C dest, T stack);

        void commitExtract(C source, ExtractionResult<T> result, long actual);

        boolean isEmpty(ExtractionResult<T> result);

        default boolean canInsert(C dest, T stack, LogisticsNode targetNode) {
            return true;
        }
    }

    public record SimpleProtocol<C, T>(
        BiFunction<C, Long, T> extractor,
        BiFunction<C, T, Long> inserter,
        TriConsumer<C, T, Long> committer,
        Predicate<T> emptyChecker,
        @javax.annotation.Nullable java.util.function.BiPredicate<T, LogisticsNode> targetFilter
    ) implements TransferProtocol<C, T> {
        public SimpleProtocol(BiFunction<C, Long, T> extractor, BiFunction<C, T, Long> inserter,
                              TriConsumer<C, T, Long> committer, Predicate<T> emptyChecker) {
            this(extractor, inserter, committer, emptyChecker, null);
        }

        @Override
        public ExtractionResult<T> simulateExtract(C source, long max) {
            T value = extractor.apply(source, max);
            return ExtractionResult.of(value);
        }

        @Override
        public long executeInsert(C dest, T stack) {
            return inserter.apply(dest, stack);
        }

        @Override
        public void commitExtract(C source, ExtractionResult<T> result, long actual) {
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
