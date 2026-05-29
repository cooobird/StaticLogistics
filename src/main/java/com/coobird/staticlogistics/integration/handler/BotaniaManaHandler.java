package com.coobird.staticlogistics.integration.handler;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.mana.ManaCollector;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.block.BotaniaBlocks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Botania 魔力传输处理器 —— 通过 {@link ManaAccess} 桥接 ManaReceiver 和 GeneratingFlowerBlockEntity，
 * 走标准 {@link TransferUtils#doTransferNodes} 管线。
 */
public class BotaniaManaHandler implements ITransferHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    public static final TransferType TYPE = new TransferType(
        Staticlogistics.asResource("botania_mana"),
        0xFF55FFFF,
        6,
        "transfer_type.staticlogistics.botania_mana",
        null,
        SLConfig::getBotaniaManaStack,
        () -> new ItemStack(BotaniaBlocks.manaPool),
        (level, pos) -> {
            try {
                var be = level.getBlockEntity(pos);
                return be != null && (ManaReceiver.LOOKUP.find(be, null) != null
                    || be instanceof GeneratingFlowerBlockEntity);
            } catch (Throwable e) {
                return false;
            }
        }
    );

    private static final TransferUtils.CapGetter<ManaAccess> CAP_GETTER = (lvl, pos, face) -> {
        var be = lvl.getBlockEntity(pos);
        if (be == null) return null;
        if (be instanceof GeneratingFlowerBlockEntity flower) {
            return flower.getMana() > 0 ? new ManaAccess(null, flower) : null;
        }
        ManaReceiver r = ManaReceiver.LOOKUP.find(be, face);
        return r != null ? new ManaAccess(r, null) : null;
    };

    private static final TransferUtils.TransferProtocol<ManaAccess, Integer> PROTOCOL =
        new TransferUtils.SimpleProtocol<>(
            (src, max) -> src.getMana() > 0 ? Math.min(src.getMana(), max) : 0,
            (dst, amount) -> {
                if (dst.isFull()) return 0;
                int space = dst.getAvailableSpace();
                int actual = Math.min(amount, space);
                if (actual > 0) dst.addMana(actual);
                return actual;
            },
            (src, amount, actual) -> src.addMana(-actual),
            val -> val <= 0
        );

    public static void register() {
        TransferRegistries.registerExternal(TYPE, new BotaniaManaHandler());
        LOGGER.info("Registered Botania mana transfer support");
    }

    @Override
    public boolean performTransfer(TransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant mana transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newCtx = null;
        try {
            isInTransfer.set(true);
            newCtx = context.withIncrementedDepth();
            return TransferUtils.doTransferNodes(
                context.level(),
                context.sourceNode().gPos().pos(),
                context.sourceNode().face(),
                targets,
                CAP_GETTER,
                context.limit(),
                PROTOCOL,
                context.isPullMode(),
                newCtx
            );
        } finally {
            if (newCtx != null) newCtx.recycle();
            isInTransfer.set(false);
        }
    }

    /**
     * 桥接 ManaReceiver（魔力池/扩散器等）和 GeneratingFlowerBlockEntity（产能花），
     * 让两者都能通过统一的 CapGetter + TransferProtocol 走标准传输管线。
     */
    private record ManaAccess(@Nullable ManaReceiver receiver, @Nullable GeneratingFlowerBlockEntity flower) {
        ManaAccess {
            if ((receiver == null) == (flower == null))
                throw new IllegalArgumentException("Exactly one of receiver or flower must be non-null");
        }

        int getMana() {
            return receiver != null ? receiver.getCurrentMana() : flower.getMana();
        }

        boolean isFull() {
            return receiver != null && receiver.isFull();
        }

        void addMana(int amount) {
            if (receiver != null) receiver.receiveMana(amount);
            else if (flower != null) flower.addMana(amount);
        }

        /**
         * 可供写入的空间。花不能作为接收端。
         */
        int getAvailableSpace() {
            if (flower != null) return 0;
            if (receiver instanceof ManaPool pool)
                return Math.max(0, pool.getMaxMana() - receiver.getCurrentMana());
            if (receiver instanceof ManaCollector col)
                return Math.max(0, col.getMaxMana() - receiver.getCurrentMana());
            return Integer.MAX_VALUE;
        }
    }
}
