package com.coobird.staticlogistics.logistics.node;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 分组作用域双向邻接图的纯领域内核。
 */
@SuppressWarnings("try")
public final class ReciprocalLinkGraph<N, G> {
    private final Function<N, Endpoint<N, G>> resolver;

    public ReciprocalLinkGraph(Function<N, Endpoint<N, G>> resolver) {
        this.resolver = resolver;
    }

    public void addEdge(G group, N first, N second) {
        if (group == null || invalidPair(first, second)) return;
        Endpoint<N, G> firstEndpoint = resolver.apply(first);
        Endpoint<N, G> secondEndpoint = resolver.apply(second);
        if (firstEndpoint == null || secondEndpoint == null
            || !firstEndpoint.belongsTo(group) || !secondEndpoint.belongsTo(group)) return;

        boolean addFirst = !firstEndpoint.linked(group).contains(second);
        boolean addSecond = !secondEndpoint.linked(group).contains(first);
        if (!addFirst && !addSecond) return;
        Edit firstEdit = addFirst ? firstEndpoint.beginEdit() : null;
        Edit secondEdit = addSecond ? secondEndpoint.beginEdit() : null;
        try {
            if (addFirst) firstEndpoint.add(group, second);
            if (addSecond) secondEndpoint.add(group, first);
        } finally {
            close(secondEdit);
            close(firstEdit);
        }
    }

    public void removeEdge(N first, N second) {
        removeEdge(null, first, second, Set.of());
    }

    public void removeEdge(G group, N first, N second) {
        if (group != null) removeEdge(group, first, second, Set.of());
    }

    public void removeNodeFromGroup(G group, N node) {
        if (group == null || node == null) return;
        Endpoint<N, G> endpoint = resolver.apply(node);
        if (endpoint == null || !endpoint.belongsTo(group)) return;
        try (Edit ignored = endpoint.beginEdit()) {
            for (N target : List.copyOf(endpoint.linked(group))) {
                removeEdge(group, node, target, Set.of(node));
            }
            if (endpoint.belongsTo(group)) endpoint.removeGroup(group);
            normalize(endpoint, group);
        }
        endpoint.cleanup();
    }

    public void cascadeRemove(N node, Collection<N> counterparts) {
        if (node == null || counterparts == null) return;
        Endpoint<N, G> endpoint = resolver.apply(node);
        if (endpoint == null) return;
        try (Edit ignored = endpoint.beginEdit()) {
            for (N counterpart : List.copyOf(counterparts)) {
                removeEdge(null, node, counterpart, Set.of(node));
            }
        }
    }

    public void repairReciprocalEdges(N node) {
        if (node == null) return;
        Endpoint<N, G> endpoint = resolver.apply(node);
        if (endpoint == null) return;
        boolean prunedInvalidEdge = false;
        for (G group : List.copyOf(endpoint.groups())) {
            for (N remote : List.copyOf(endpoint.linked(group))) {
                Endpoint<N, G> remoteEndpoint = resolver.apply(remote);
                if (remoteEndpoint == null || !remoteEndpoint.belongsTo(group)) {
                    prunedInvalidEdge |= removeEdge(group, node, remote, Set.of(node));
                } else if (!remoteEndpoint.linked(group).contains(node)) {
                    addEdge(group, node, remote);
                }
            }
        }
        if (prunedInvalidEdge) endpoint.cleanup();
    }

    private boolean removeEdge(G group, N first, N second, Set<N> deferredCleanup) {
        if (invalidPair(first, second)) return false;
        Endpoint<N, G> firstEndpoint = resolver.apply(first);
        Endpoint<N, G> secondEndpoint = resolver.apply(second);
        boolean removeFirst = contains(firstEndpoint, group, second);
        boolean removeSecond = contains(secondEndpoint, group, first);
        if (!removeFirst && !removeSecond) return false;

        Edit firstEdit = removeFirst ? firstEndpoint.beginEdit() : null;
        Edit secondEdit = removeSecond ? secondEndpoint.beginEdit() : null;
        try {
            if (removeFirst) {
                removeReference(firstEndpoint, group, second);
                normalize(firstEndpoint, group);
            }
            if (removeSecond) {
                removeReference(secondEndpoint, group, first);
                normalize(secondEndpoint, group);
            }
        } finally {
            close(secondEdit);
            close(firstEdit);
        }
        if (removeFirst && !deferredCleanup.contains(first)) firstEndpoint.cleanup();
        if (removeSecond && !deferredCleanup.contains(second)) secondEndpoint.cleanup();
        return true;
    }

    private static <N, G> boolean contains(Endpoint<N, G> endpoint, G group, N counterpart) {
        if (endpoint == null) return false;
        return group == null
            ? endpoint.linked().contains(counterpart)
            : endpoint.linked(group).contains(counterpart);
    }

    private static <N, G> void removeReference(Endpoint<N, G> endpoint, G group, N counterpart) {
        if (group == null) endpoint.remove(counterpart);
        else endpoint.remove(group, counterpart);
    }

    private static <N, G> void normalize(Endpoint<N, G> endpoint, G group) {
        if (group != null) {
            if (endpoint.linked(group).isEmpty() && endpoint.belongsTo(group)) endpoint.removeGroup(group);
        } else {
            for (G existingGroup : List.copyOf(endpoint.groups())) {
                if (endpoint.linked(existingGroup).isEmpty()) endpoint.removeGroup(existingGroup);
            }
        }
        if (endpoint.linked().isEmpty()) endpoint.disableRoles();
    }

    private static boolean invalidPair(Object first, Object second) {
        return first == null || second == null || first.equals(second);
    }

    private static void close(Edit edit) {
        if (edit != null) edit.close();
    }

    public interface Endpoint<N, G> {
        boolean belongsTo(G group);

        Set<G> groups();

        Set<N> linked();

        Set<N> linked(G group);

        Edit beginEdit();

        void add(G group, N counterpart);

        void remove(G group, N counterpart);

        void remove(N counterpart);

        void removeGroup(G group);

        void disableRoles();

        void cleanup();
    }

    @FunctionalInterface
    public interface Edit extends AutoCloseable {
        @Override
        void close();
    }
}
