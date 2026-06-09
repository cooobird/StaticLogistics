package com.coobird.staticlogistics.api;

/**
 * 节点在物流网络中的角色类型。
 */
public enum NodeRole {
    /**
     * 发送者 / 供应者 (抽取模式)
     */
    SENDER,

    /**
     * 接收者 / 消耗者 (存入模式)
     */
    RECEIVER,

    /**
     * 双向
     */
    BOTH,

    /**
     * 无角色 (默认)
     */
    NONE;

    /**
     * 辅助方法：判断是否可以发送资源
     */
    public boolean canSend() {
        return this == SENDER || this == BOTH;
    }

    /**
     * 辅助方法：判断是否可以接收资源
     */
    public boolean canReceive() {
        return this == RECEIVER || this == BOTH;
    }
}