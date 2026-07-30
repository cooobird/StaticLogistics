package com.coobird.staticlogistics.logistics.util;

/**
 * 更新与删除墓碑共用的单调版本顺序。
 */
public final class VersionOrder {
    private VersionOrder() {
    }

    public static boolean isNewer(long currentVersion, long candidateVersion) {
        return candidateVersion > currentVersion;
    }

    public static boolean preferCandidate(long currentVersion, boolean currentRemoval,
                                          long candidateVersion, boolean candidateRemoval) {
        return candidateVersion > currentVersion
            || candidateVersion == currentVersion && candidateRemoval && !currentRemoval;
    }
}
