package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.mana.ManaCollector;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.block.BotaniaBlocks;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/// Botania 魔力资源适配器。
/// 产能花只提供魔力；魔力池与魔力收集器既可提供也可接收魔力；
/// 其他 ManaReceiver 仅作为接收端。
public class BotaniaManaResource implements LogisticsResource<BotaniaManaResource.ManaHandle> {
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("botania_mana");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFF55FFFF;
    }

    @Override
    public Component unit() {
        return Component.translatable("gui.staticlogistics.unit.mana");
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.botania_mana";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(BotaniaBlocks.MANA_POOL);
    }

    @Override
    public LongSupplier baseStackSizeSupplier() {
        return SLConfig::getBotaniaManaStack;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    /// 魔力句柄，区分产能花与 ManaReceiver。
    /// 每次操作读取实时存量，外部回调和同容器的其他连接不会留下旧快照。
    public static final class ManaHandle {
        private final @Nullable GeneratingFlowerBlockEntity flower;
        private final @Nullable ManaReceiver receiver;
        private boolean nonDepletingSource;

        private ManaHandle(@Nullable GeneratingFlowerBlockEntity flower,
                           @Nullable ManaReceiver receiver) {
            if ((flower == null) == (receiver == null))
                throw new IllegalArgumentException("Exactly one of flower or receiver must be non-null");
            this.flower = flower;
            this.receiver = receiver;
        }

        static ManaHandle source(GeneratingFlowerBlockEntity flower) {
            return new ManaHandle(flower, null);
        }

        static ManaHandle target(ManaReceiver receiver) {
            return new ManaHandle(null, receiver);
        }

        boolean canExtract() {
            return flower != null || receiver instanceof ManaPool || receiver instanceof ManaCollector;
        }

        boolean canInsert() {
            return receiver != null;
        }

        int currentMana() {
            return receiver != null ? receiver.getCurrentMana() : flower.getMana();
        }

        int changeMana(int amount) {
            int before = currentMana();
            if (receiver != null) {
                receiver.receiveMana(amount);
            } else if (flower != null) {
                flower.addMana(amount);
            }
            int changed = currentMana() - before;
            /// 创造型魔力池的读数不会下降，但应保持无限魔力源语义。
            if (amount < 0 && receiver instanceof ManaPool && before > 0 && changed == 0) {
                nonDepletingSource = true;
                return amount;
            }
            return changed;
        }

        int availableSpace() {
            if (flower != null) return 0;
            if (receiver instanceof ManaPool pool)
                return Math.max(0, pool.getMaxMana() - receiver.getCurrentMana());
            if (receiver instanceof ManaCollector col)
                return Math.max(0, col.getMaxMana() - receiver.getCurrentMana());
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public boolean isPresent(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        if (be instanceof GeneratingFlowerBlockEntity) return true;
        return ManaReceiver.LOOKUP.find(be, face) != null;
    }

    @Override
    public @Nullable ManaHandle resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        if (be instanceof GeneratingFlowerBlockEntity flower) {
            /// 即使魔力为0也返回handle，让extract方法处理
            return ManaHandle.source(flower);
        }

        ManaReceiver receiver = ManaReceiver.LOOKUP.find(be, face);
        if (receiver == null) return null;

        return ManaHandle.target(receiver);
    }

    @Override
    public long extract(ManaHandle handle, long amount, boolean simulate) {
        if (!handle.canExtract()) return 0;
        long actual = Math.min(handle.currentMana(), amount);
        if (actual <= 0) return 0;
        return simulate ? actual : Math.max(0L, -(long) handle.changeMana((int) -actual));
    }

    @Override
    public long insert(ManaHandle handle, long amount, boolean simulate) {
        if (!handle.canInsert()) return 0;
        long space = handle.availableSpace();
        if (space <= 0) return 0;
        long actual = Math.min(amount, space);
        return simulate ? actual : Math.max(0L, handle.changeMana((int) actual));
    }

    @Override
    public boolean rollback(ManaHandle source, Object value, long amount,
                            @Nullable EndpointConfig sourceConfig, boolean pullMode,
                            @Nullable TransferContext context) {
        if (!(value instanceof Number) || amount <= 0L || amount > Integer.MAX_VALUE) return amount <= 0L;
        int restored = source.changeMana((int) amount);
        return restored >= amount || source.nonDepletingSource;
    }

    @Override
    public long maxInsertToTarget(ManaHandle handle, Object value, EndpointConfig config) {
        long stock = config.linkConfig.getKeepStock();
        return stock <= 0 ? Long.MAX_VALUE : Math.max(0L, stock - handle.currentMana());
    }

}
