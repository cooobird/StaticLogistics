package com.coobird.staticlogistics.api.transfer;

/**
 * 向内容注册与可选集成层提供的资源适配注册端口。
 */
@FunctionalInterface
public interface ResourceAdapterRegistrar {
    <C, V> void register(ResourceAdapter<C, V> adapter, int stableBitOffset);
}
