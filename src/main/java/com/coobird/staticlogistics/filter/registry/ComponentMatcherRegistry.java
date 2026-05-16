package com.coobird.staticlogistics.filter.registry;

import com.coobird.staticlogistics.api.type.NbtMatchMode;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.TriPredicate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用于注册特定 DataComponent 类的“包含匹配”逻辑。
 * 当 isValueContainedIn 遇到无法用通用 Map/Collection 处理的对象时，
 * 会查询此注册表，调用对应的匹配器。
 */
public class ComponentMatcherRegistry {

    private static final Map<Class<?>, TriPredicate<Object, Object, NbtMatchMode>> MATCHERS = new HashMap<>();

    /**
     * 注册一个针对特定组件类的包含匹配器。
     * 调用者保证传入的 matcher 的类型参数与 key 严格对应，
     * 否则在运行时可能导致 ClassCastException。
     */
    @SuppressWarnings("unchecked")
    public static <T> void register(Class<T> key, TriPredicate<? super T, ? super T, ? super NbtMatchMode> matcher) {
        MATCHERS.put(key, (TriPredicate<Object, Object, NbtMatchMode>) matcher);
    }

    /**
     * 按数据组件的注册 ID 注册匹配器（支持其他模组扩展）。
     * 需要额外提供组件的 Java Class，因为运行时很难从 DataComponentType 安全推断。
     */
    @SuppressWarnings("unchecked")
    public static <T> void registerById(ResourceLocation componentId, Class<T> typeClass,
                                        TriPredicate<? super T, ? super T, ? super NbtMatchMode> matcher) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
        if (type != null) {
            MATCHERS.put(typeClass, (TriPredicate<Object, Object, NbtMatchMode>) matcher);
        }
    }

    /**
     * 根据对象的实际类型获取对应的匹配器（如果有）。
     */
    public static Optional<TriPredicate<Object, Object, NbtMatchMode>> getMatcher(Class<?> key) {
        return Optional.ofNullable(MATCHERS.get(key));
    }
}