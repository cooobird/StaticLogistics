package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.util.List;

public class TransferUtils {

    /**
     * @param roundRobinCursor 用于轮询策略的索引缓存，如果不需要轮询可传 null
     */
    public static <C, T> boolean doTransfer(
        ServerLevel sourceLevel,
        List<StaticLink> links,
        BlockCapability<C, net.minecraft.core.Direction> cap,
        int limit,
        int[] roundRobinCursor,
        TransferProtocol<C, T> protocol
    ) {
        if (links.isEmpty()) return false;

        C source = sourceLevel.getCapability(cap, links.getFirst().sourcePos(), links.getFirst().sourceFace());
        if (source == null) return false;

        boolean movedAny = false;
        int remaining = limit;
        int size = links.size();

        int startIndex = (roundRobinCursor != null) ? roundRobinCursor[0] % size : 0;

        for (int i = 0; i < size; i++) {
            if (remaining <= 0) break;

            // 循环遍历：从 startIndex 开始，绕回到 0
            int currentIndex = (startIndex + i) % size;
            StaticLink link = links.get(currentIndex);

            T available = protocol.simulateExtract(source, remaining);
            if (protocol.isEmpty(available)) continue;

            ServerLevel destLevel = sourceLevel.getServer().getLevel(link.destDimension());
            if (destLevel == null) continue;

            C destination = destLevel.getCapability(cap, link.destPos(), link.destFace());
            if (destination == null) continue;

            int accepted = protocol.executeInsert(destination, available);
            if (accepted > 0) {
                protocol.commitExtract(source, available, accepted);
                remaining -= accepted;
                movedAny = true;

                if (roundRobinCursor != null) {
                    roundRobinCursor[0] = (currentIndex + 1) % size;
                    break;
                }
            }
        }
        return movedAny;
    }

    public interface TransferProtocol<C, T> {
        T simulateExtract(C source, int max);

        int executeInsert(C dest, T stack);

        void commitExtract(C source, T stack, int actual);

        boolean isEmpty(T stack);
    }
}