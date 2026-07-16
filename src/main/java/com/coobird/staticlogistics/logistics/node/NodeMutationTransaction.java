package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/**
 * 服务器级物流修改 Unit of Work。
 *
 * <p>嵌套图操作加入当前根事务；配置回调、成员事件和网络同步延迟到根提交阶段。
 * 未显式提交时按逆序恢复快照，再对回滚后的最终状态执行一次合并 reconcile。
 */
public final class NodeMutationTransaction implements AutoCloseable {
    private static final ThreadLocal<RootState> CURRENT = new ThreadLocal<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final RootState root;
    private final boolean ownerScope;
    private boolean scopeCommitted;
    private boolean closed;

    private NodeMutationTransaction(RootState root, boolean ownerScope) {
        this.root = root;
        this.ownerScope = ownerScope;
    }

    public static NodeMutationTransaction begin(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("Mutation server must not be null");
        RootState current = CURRENT.get();
        if (current == null) {
            RootState root = new RootState(server);
            CURRENT.set(root);
            return new NodeMutationTransaction(root, true);
        }
        if (current.server != server) {
            throw new IllegalStateException("Nested mutation belongs to a different server");
        }
        return new NodeMutationTransaction(current, false);
    }

    /** 在当前服务器事务提交后合并执行副作用；没有事务时返回 false。 */
    public static boolean defer(MinecraftServer server, Object key, Runnable action) {
        if (server == null || key == null || action == null) {
            throw new IllegalArgumentException("Deferred mutation server, key and action are required");
        }
        RootState current = CURRENT.get();
        if (current == null) return false;
        if (current.server != server) {
            throw new IllegalStateException("Deferred mutation belongs to a different server");
        }
        if (current.flushing) return false;
        current.afterCommit.put(key, action);
        return true;
    }

    /** 捕获一个当前必须存在的面；重复捕获同一节点不会生成重复快照。 */
    public void capture(LogisticsNode node) {
        capture(node, true);
    }

    /** 捕获一个面当前的存在性和内容，允许修改前尚不存在。 */
    public void captureState(LogisticsNode node) {
        capture(node, false);
    }

    private void capture(LogisticsNode node, boolean requireExisting) {
        if (node == null) throw new IllegalArgumentException("Mutation node must not be null");
        if (!root.capturedNodes.add(node)) return;

        ServerLevel level = root.server.getLevel(node.gPos().dimension());
        if (level == null) {
            throw new IllegalStateException(
                "Mutation dimension is unavailable: " + node.gPos().dimension().location());
        }
        LinkManager manager = LinkManager.get(level);
        FaceConfigComposite config = manager.getFaceConfig(FaceAddress.of(node));
        if (config == null) {
            if (requireExisting) {
                throw new IllegalStateException("Mutation face is unavailable: " + node);
            }
            root.rollbackActions.push(() -> manager.restoreFaceAbsence(node));
            return;
        }
        CompoundTag snapshot = config.serializeNBT(level.registryAccess()).copy();
        root.rollbackActions.push(() -> manager.restoreFaceSnapshot(node, snapshot));
    }

    public void captureAll(Iterable<LogisticsNode> nodes) {
        if (nodes == null) throw new IllegalArgumentException("Mutation nodes must not be null");
        for (LogisticsNode node : nodes) capture(node);
    }

    /** 注册节点快照之外的补偿动作，例如恢复分组目录。 */
    public void onRollback(Runnable action) {
        if (action == null) throw new IllegalArgumentException("Rollback action must not be null");
        root.rollbackActions.push(action);
    }

    public void commit() {
        if (closed) throw new IllegalStateException("Mutation scope is already closed");
        scopeCommitted = true;
        if (!ownerScope) return;
        if (root.rollbackOnly) {
            throw new IllegalStateException("Nested mutation scope requested rollback");
        }

        var actions = java.util.List.copyOf(root.afterCommit.values());
        root.afterCommit.clear();

        // 先确定领域状态，再发布缓存、事件和网络副作用；发布失败不得回滚已经对外可见的提交。
        root.committed = true;
        root.rollbackActions.clear();
        CURRENT.remove();
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (RuntimeException exception) {
                LOGGER.error("Post-commit node mutation action failed", exception);
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (!scopeCommitted) root.rollbackOnly = true;
        if (!ownerScope) return;

        RuntimeException failure = null;
        try {
            if (!root.committed) {
                // 丢弃修改阶段产生的提交副作用；回滚过程中重新收集最终状态的 reconcile 回调。
                root.afterCommit.clear();
                while (!root.rollbackActions.isEmpty()) {
                    try {
                        root.rollbackActions.pop().run();
                    } catch (RuntimeException exception) {
                        if (failure == null) failure = exception;
                        else failure.addSuppressed(exception);
                    }
                }
                var reconciliation = java.util.List.copyOf(root.afterCommit.values());
                root.afterCommit.clear();
                root.flushing = true;
                for (Runnable action : reconciliation) {
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        if (failure == null) failure = exception;
                        else failure.addSuppressed(exception);
                    }
                }
            }
        } finally {
            CURRENT.remove();
        }
        if (failure != null) {
            throw new IllegalStateException("Node mutation rollback failed", failure);
        }
    }

    private static final class RootState {
        final MinecraftServer server;
        final Set<LogisticsNode> capturedNodes = new LinkedHashSet<>();
        final Deque<Runnable> rollbackActions = new ArrayDeque<>();
        final Map<Object, Runnable> afterCommit = new LinkedHashMap<>();
        boolean rollbackOnly;
        boolean committed;
        boolean flushing;

        RootState(MinecraftServer server) {
            this.server = server;
        }
    }
}
