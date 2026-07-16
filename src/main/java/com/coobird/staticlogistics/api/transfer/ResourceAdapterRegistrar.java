package com.coobird.staticlogistics.api.transfer;

/**
 * 向内容和可选集成层提供的资源适配器注册端口。
 */
@FunctionalInterface
public interface ResourceAdapterRegistrar {
    <C, V> void register(ResourceAdapter<C, V> adapter, int stableBitOffset);
}
