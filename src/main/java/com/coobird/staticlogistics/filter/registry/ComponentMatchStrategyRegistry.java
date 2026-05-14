package com.coobird.staticlogistics.filter.registry;

import com.coobird.staticlogistics.api.filter.MatchStrategy;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ComponentMatchStrategyRegistry {
    private static final Map<DataComponentType<?>, MatchStrategy> STRATEGY_MAP = new HashMap<>();
    private static final MatchStrategy DEFAULT_STRATEGY = MatchStrategy.SMART_CONTAINS;

    static {
        /*
         * ==========================
         *  策略分配说明：
         *  - IGNORE：动态变化、纯视觉、物品固有、内部数据等对玩家主动过滤无意义的组件。
         *  - EXACT：需要完全相等才有意义的组件（如名称、颜色等）。
         *  - CONTAINS：存储集合/列表，部分匹配要求目标包含模板中所有元素。
         *  - 其余未列出的组件自动走 SMART_CONTAINS（智能递归包含判断）。
         * ==========================
         */

        // 动态变化 & 铁砧惩罚
        // 当前耐久损耗
        setStrategy(DataComponents.DAMAGE, MatchStrategy.IGNORE);
        // 铁砧累计惩罚
        setStrategy(DataComponents.REPAIR_COST, MatchStrategy.IGNORE);

        // 物品固有元数据（由物品类型决定，同物品必然一致）
        // 最大耐久
        setStrategy(DataComponents.MAX_DAMAGE, MatchStrategy.IGNORE);
        // 最大堆叠数
        setStrategy(DataComponents.MAX_STACK_SIZE, MatchStrategy.IGNORE);
        // 稀有度
        setStrategy(DataComponents.RARITY, MatchStrategy.IGNORE);
        // 是否防火
        setStrategy(DataComponents.FIRE_RESISTANT, MatchStrategy.IGNORE);
        // 定义工具挖掘属性（可挖方块、速度等）
        setStrategy(DataComponents.TOOL, MatchStrategy.IGNORE);
        // 食物属性
        setStrategy(DataComponents.FOOD, MatchStrategy.IGNORE);
        // 山羊角乐器
        setStrategy(DataComponents.INSTRUMENT, MatchStrategy.IGNORE);
        // 唱片机播放
        setStrategy(DataComponents.JUKEBOX_PLAYABLE, MatchStrategy.IGNORE);

        // --- 冒险模式限制 ---
        // 可放置在...
        setStrategy(DataComponents.CAN_PLACE_ON, MatchStrategy.IGNORE);
        // 可破坏...
        setStrategy(DataComponents.CAN_BREAK, MatchStrategy.IGNORE);

        // 内部数据/调试/容器战利品
        // 自定义 NBT 数据（存储任意数据，难以语义匹配）
        setStrategy(DataComponents.CUSTOM_DATA, MatchStrategy.IGNORE);
        // 实体数据
        setStrategy(DataComponents.ENTITY_DATA, MatchStrategy.IGNORE);
        // 桶装实体数据
        setStrategy(DataComponents.BUCKET_ENTITY_DATA, MatchStrategy.IGNORE);
        // 方块实体数据
        setStrategy(DataComponents.BLOCK_ENTITY_DATA, MatchStrategy.IGNORE);
        // 方块状态属性
        setStrategy(DataComponents.BLOCK_STATE, MatchStrategy.IGNORE);
        // 调试棒状态
        setStrategy(DataComponents.DEBUG_STICK_STATE, MatchStrategy.IGNORE);
        // 知识之书配方列表
        setStrategy(DataComponents.RECIPES, MatchStrategy.IGNORE);
        // 磁石追踪位置
        setStrategy(DataComponents.LODESTONE_TRACKER, MatchStrategy.IGNORE);
        // 蜂箱/蜂巢的蜜蜂数据
        setStrategy(DataComponents.BEES, MatchStrategy.IGNORE);
        // 容器战利品表引用（未生成内容时）
        setStrategy(DataComponents.CONTAINER_LOOT, MatchStrategy.IGNORE);

        // 视觉/客户端渲染
        // 附魔光效覆盖
        setStrategy(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, MatchStrategy.IGNORE);
        // 自定义模型数据
        setStrategy(DataComponents.CUSTOM_MODEL_DATA, MatchStrategy.IGNORE);
        // 无法被非创造玩家捡起的弹射物
        setStrategy(DataComponents.INTANGIBLE_PROJECTILE, MatchStrategy.IGNORE);
        // 隐藏附加提示框
        setStrategy(DataComponents.HIDE_ADDITIONAL_TOOLTIP, MatchStrategy.IGNORE);
        // 完全隐藏提示框
        setStrategy(DataComponents.HIDE_TOOLTIP, MatchStrategy.IGNORE);
        // 创造模式锁定格子
        setStrategy(DataComponents.CREATIVE_SLOT_LOCK, MatchStrategy.IGNORE);
        // 地图颜色
        setStrategy(DataComponents.MAP_COLOR, MatchStrategy.IGNORE);
        // 地图后处理（锁定）
        setStrategy(DataComponents.MAP_POST_PROCESSING, MatchStrategy.IGNORE);
        // 地图标记
        setStrategy(DataComponents.MAP_DECORATIONS, MatchStrategy.IGNORE);
        // 容器锁
        setStrategy(DataComponents.LOCK, MatchStrategy.IGNORE);

        // 精确匹配（名称、颜色、档案等）
        // 自定义名称
        setStrategy(DataComponents.CUSTOM_NAME, MatchStrategy.EXACT);
        // 物品名称（覆盖）
        setStrategy(DataComponents.ITEM_NAME, MatchStrategy.EXACT);
        // 提示描述
        setStrategy(DataComponents.LORE, MatchStrategy.EXACT);
        // 染色颜色
        setStrategy(DataComponents.DYED_COLOR, MatchStrategy.EXACT);
        // 地图 ID
        setStrategy(DataComponents.MAP_ID, MatchStrategy.EXACT);
        // 玩家档案（头颅）
        setStrategy(DataComponents.PROFILE, MatchStrategy.EXACT);
        // 音符盒音效
        setStrategy(DataComponents.NOTE_BLOCK_SOUND, MatchStrategy.EXACT);
        // 无法破坏
        setStrategy(DataComponents.UNBREAKABLE, MatchStrategy.EXACT);
        // 不祥之瓶效果等级
        setStrategy(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, MatchStrategy.EXACT);

        // 包含匹配（集合/映射语义）
        // 魔咒
        setStrategy(DataComponents.ENCHANTMENTS, MatchStrategy.CONTAINS);
        // 存储魔咒（附魔书）
        setStrategy(DataComponents.STORED_ENCHANTMENTS, MatchStrategy.CONTAINS);
        // 属性修饰符
        setStrategy(DataComponents.ATTRIBUTE_MODIFIERS, MatchStrategy.CONTAINS);
        // 药水效果
        setStrategy(DataComponents.POTION_CONTENTS, MatchStrategy.CONTAINS);
        // 迷之炖菜效果
        setStrategy(DataComponents.SUSPICIOUS_STEW_EFFECTS, MatchStrategy.CONTAINS);
        // 旗帜图案
        setStrategy(DataComponents.BANNER_PATTERNS, MatchStrategy.CONTAINS);
        // 弩装填弹射物
        setStrategy(DataComponents.CHARGED_PROJECTILES, MatchStrategy.CONTAINS);
        // 陶罐纹饰
        setStrategy(DataComponents.POT_DECORATIONS, MatchStrategy.CONTAINS);
        // 烟花火箭
        setStrategy(DataComponents.FIREWORKS, MatchStrategy.CONTAINS);
        // 烟火之星
        setStrategy(DataComponents.FIREWORK_EXPLOSION, MatchStrategy.CONTAINS);
        // 收纳袋内容
        setStrategy(DataComponents.BUNDLE_CONTENTS, MatchStrategy.CONTAINS);
        // 容器内容（潜影盒等）
        setStrategy(DataComponents.CONTAINER, MatchStrategy.CONTAINS);

        // 以下未注册的组件，包括 TRIM、WRITABLE_BOOK_CONTENT、WRITTEN_BOOK_CONTENT 等，
        // 将自动使用 SMART_CONTAINS 策略，智能递归比较其内部结构。
    }

    /**
     * 直接设置策略
     */
    public static void setStrategy(DataComponentType<?> type, MatchStrategy strategy) {
        STRATEGY_MAP.put(type, strategy);
    }

    /**
     * 获取组件的匹配策略，未注册则返回默认值 SMART_CONTAINS
     */
    public static MatchStrategy getStrategy(DataComponentType<?> type) {
        return STRATEGY_MAP.getOrDefault(type, DEFAULT_STRATEGY);
    }

    /**
     * 供外部模组按资源 ID 注册自定义策略
     */
    public static void registerStrategy(ResourceLocation componentId, MatchStrategy strategy) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
        if (type != null) {
            setStrategy(type, strategy);
        }
    }

    /**
     * 批量配置文件覆写（key: 组件 ID，value: 策略名称）
     */
    public static void loadConfigOverrides(Map<String, String> overrides) {
        for (var entry : overrides.entrySet()) {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(entry.getKey()));
            if (type != null) {
                MatchStrategy strategy = MatchStrategy.valueOf(entry.getValue().toUpperCase());
                setStrategy(type, strategy);
            }
        }
    }

    /**
     * 获取当前所有已注册数据组件策略的不可变快照（已应用配置文件覆盖）。
     * 按组件 ID 排序，方便分页展示。
     */
    public static Map<ResourceLocation, MatchStrategy> getAllStrategies() {
        Map<ResourceLocation, MatchStrategy> sorted = new java.util.TreeMap<>();
        for (var entry : BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            DataComponentType<?> type = entry.getValue();
            sorted.put(id, STRATEGY_MAP.getOrDefault(type, DEFAULT_STRATEGY));
        }
        return Collections.unmodifiableMap(sorted);
    }
}