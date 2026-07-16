package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.logistics.util.VersionOrder;
import java.util.HashMap;
import java.util.Map;

/** 记录会话内每个对象已接受的最高版本与删除墓碑。 */
public final class VersionGate<K> {
    private final Map<K, Long> highestVersions = new HashMap<>();

    public boolean acceptUpdate(K key, long version) {
        return accept(key, version);
    }

    public boolean acceptRemoval(K key, long version) {
        return accept(key, version);
    }

    private boolean accept(K key, long version) {
        Long current = highestVersions.get(key);
        if (current != null && !VersionOrder.isNewer(current, version)) return false;
        highestVersions.put(key, version);
        return true;
    }

    /** 用权威快照初始化当前对象版本，不执行“必须更新”判断。 */
    public void seed(K key, long version) {
        highestVersions.put(key, version);
    }

    public void clear() {
        highestVersions.clear();
    }
}
