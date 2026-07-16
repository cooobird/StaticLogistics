package com.coobird.staticlogistics.logistics.node;

/**
 * 链接集合底层变更许可。
 * 构造器仅对节点管理包开放，防止业务层绕过双端生命周期入口。
 */
public final class LinkMutationPermit {
    LinkMutationPermit() {
    }
}
