# Static Logistics 完成功能

## 物品系统

### 物品注册
- **SLItems.java** - 物品注册入口
- **LinkConfiguratorItem.java** - 连接配置器物品
- **UpgradeItem.java** - 升级插件基类

### 具体物品
| 物品     | 类                                     | 说明      |
|--------|---------------------------------------|---------|
| 连接配置器  | LinkConfiguratorItem                  | 主要工具    |
| 维度升级   | UpgradeItem(UpgradeType.DIMENSION)    | 跨维度传输   |
| 速率升级×5 | UpgradeItem(UpgradeType.SPEED, 等级)    | 提升速度    |
| 范围升级×5 | UpgradeItem(UpgradeType.RANGE, 等级)    | 扩大范围    |
| 堆叠升级×5 | UpgradeItem(UpgradeType.STACK, 等级)    | 增加数量    |
| 基础过滤   | UpgradeItem(UpgradeType.BASIC_FILTER) | 物品/流体过滤 |
| 标签过滤   | UpgradeItem(UpgradeType.TAG_FILTER)   | 标签过滤    |
| NBT过滤  | UpgradeItem(UpgradeType.NBT_FILTER)   | NBT过滤   |

## 物流网络管理

### 核心管理
| 类                               | 功能                |
|---------------------------------|-------------------|
| **GlobalLogisticsManager.java** | 全局物流管理器，每个服务器一个实例 |
| **LinkManager.java**            | 链接管理器，管理方块间连接     |
| **NodeGroupService.java**       | 节点分组服务            |
| **GroupMemberService.java**     | 分组成员服务            |
| **GroupSyncScheduler.java**     | 分组同步调度器           |
| **IncomingLinkIndex.java**      | 传入链接索引            |
| **TransferCursorService.java**  | 传输游标服务            |

### API接口
| 类                           | 功能      |
|-----------------------------|---------|
| **LogisticsNode.java**      | 物流节点表示  |
| **ILogisticsManager.java**  | 物流管理器接口 |
| **ITransferHandler.java**   | 传输处理器接口 |
| **NodeRole.java**           | 节点角色枚举  |
| **LogisticsNodeEvent.java** | 节点事件    |

## 过滤系统

### 过滤器实现
| 类                                | 功能      |
|----------------------------------|---------|
| **AbstractLogisticsFilter.java** | 过滤器抽象基类 |
| **BasicLogisticsFilter.java**    | 基础过滤器   |
| **TagLogisticsFilter.java**      | 标签过滤器   |
| **NbtLogisticsFilter.java**      | NBT过滤器  |
| **FilterData.java**              | 过滤器数据   |
| **ILogisticsFilter.java**        | 过滤器接口   |
| **MatchStrategy.java**           | 匹配策略    |

### 过滤器注册
| 类                                       | 功能       |
|-----------------------------------------|----------|
| **ComponentMatcherRegistry.java**       | 组件匹配器注册表 |
| **ComponentMatchStrategyRegistry.java** | 匹配策略注册表  |
| **ComponentValueMatcher.java**          | 组件值匹配器   |

## 图形界面

### 菜单类
| 类                                  | 功能      |
|------------------------------------|---------|
| **AbstractFilterMenu.java**        | 过滤器菜单基类 |
| **FilterConfiguratorMenu.java**    | 过滤器配置菜单 |
| **FaceConfiguratorMenu.java**      | 面配置菜单   |
| **ContainerConfiguratorMenu.java** | 容器配置菜单  |
| **HandFilterMenu.java**            | 手持过滤器菜单 |

### 界面类
| 类                                    | 功能      |
|--------------------------------------|---------|
| **AbstractConfiguratorScreen.java**  | 配置界面基类  |
| **LinkConfiguratorScreen.java**      | 连接配置器界面 |
| **BaseFilterScreen.java**            | 基础过滤器界面 |
| **FilterConfiguratorScreen.java**    | 过滤器配置界面 |
| **FaceConfiguratorScreen.java**      | 面配置界面   |
| **ContainerConfiguratorScreen.java** | 容器配置界面  |
| **HandFilterScreen.java**            | 手持过滤器界面 |

### 资源
| 类                      | 功能      |
|------------------------|---------|
| **SLGuiTextures.java** | GUI纹理资源 |

## 网络通信

### C2S（客户端→服务端）
| 类                                     | 功能      |
|---------------------------------------|---------|
| **C2SConfigureFacePayload.java**      | 配置面     |
| **C2SGroupRenamePayload.java**        | 重命名分组   |
| **C2SRemoveLinkPayload.java**         | 移除链接    |
| **C2SUpdateFilterOnHandPayload.java** | 更新手持过滤器 |
| **C2SUpdateFilterOnItemPayload.java** | 更新物品过滤器 |
| **C2SUpdateToolSettingsPayload.java** | 更新工具设置  |
| **C2SOpenHandFilterPayload.java**     | 打开手持过滤器 |

### S2C（服务端→客户端）
| 类                                    | 功能     |
|--------------------------------------|--------|
| **S2CSyncFaceConfigPacket.java**     | 同步面配置  |
| **S2CSyncBulkFaceConfigPacket.java** | 批量同步配置 |

## 传输系统

### 传输核心
| 类                                    | 功能        |
|--------------------------------------|-----------|
| **TransferExecutor.java**            | 传输执行器     |
| **TargetSelector.java**              | 目标选择器     |
| **StrategyBasedTargetSelector.java** | 基于策略的目标选择 |
| **TransferContext.java**             | 传输上下文     |
| **CooldownManager.java**             | 冷却管理器     |
| **TransferUtils.java**               | 传输工具类     |
| **StandardTransferHandlers.java**    | 标准传输处理器   |

### 传输注册
| 类                           | 功能      |
|-----------------------------|---------|
| **TransferRegistries.java** | 传输类型注册表 |
| **TransferType.java**       | 传输类型定义  |

## 团队集成

| 类                          | 功能      |
|----------------------------|---------|
| **FTBEventHandlers.java**  | FTB事件处理 |
| **FTBTeamService.java**    | FTB团队服务 |
| **PermissionService.java** | 权限服务    |
| **TeamSyncService.java**   | 团队同步服务  |
| **CompatHandler.java**     | 兼容处理器   |
| **ModCompat.java**         | 模组兼容基类  |

## 数据存储

### 存储核心
| 类                            | 功能      |
|------------------------------|---------|
| **ConfigRepository.java**    | 配置仓库    |
| **ContainerRepository.java** | 容器仓库    |
| **CacheManager.java**        | 缓存管理器   |
| **NetworkSyncManager.java**  | 网络同步管理器 |
| **SyncManager.java**         | 同步管理器   |
| **DropHandler.java**         | 掉落处理    |

### 配置类
| 类                            | 功能    |
|------------------------------|-------|
| **FaceConfig.java**          | 面配置   |
| **FaceConfigComposite.java** | 面配置组合 |
| **ContainerConfig.java**     | 容器配置  |
| **LinkConfig.java**          | 链接配置  |
| **FilterConfig.java**        | 过滤器配置 |

### 服务类
| 类                               | 功能     |
|---------------------------------|--------|
| **FaceConfigService.java**      | 面配置服务  |
| **ContainerConfigService.java** | 容器配置服务 |

### 变更处理
| 类                          | 功能      |
|----------------------------|---------|
| **LinkChangeHandler.java** | 链接变更处理器 |

## 事件系统

### 服务端事件
| 类                      | 功能   |
|------------------------|------|
| **SLEvents.java**      | 主事件类 |
| **PlayerEvents.java**  | 玩家事件 |
| **SLLevelEvents.java** | 世界事件 |

### 客户端事件
| 类                     | 功能    |
|-----------------------|-------|
| **ClientEvents.java** | 客户端事件 |

## 渲染系统

| 类                          | 功能      |
|----------------------------|---------|
| **LinkWorldRenderer.java** | 链接世界渲染器 |
| **ClientLinkData.java**    | 客户端链接数据 |
| **SelectionContext.java**  | 选择上下文   |
| **RenderConstants.java**   | 渲染常量    |

## 数据生成

| 类                              | 功能      |
|--------------------------------|---------|
| **SLDataGenerator.java**       | 数据生成器入口 |
| **SlLanguageProvider.java**    | 语言文件生成  |
| **SLItemModelProvider.java**   | 物品模型生成  |
| **VanillaRecipeProvider.java** | 配方生成    |

## 注册系统

| 类                         | 功能      |
|---------------------------|---------|
| **SLItems.java**          | 物品注册    |
| **SLMenuTypes.java**      | 菜单类型注册  |
| **SLDataComponents.java** | 数据组件注册  |
| **SLCreativeTabs.java**   | 创意标签页注册 |
| **SLCommands.java**       | 命令注册    |

## 配置系统

| 类                            | 功能      |
|------------------------------|---------|
| **SLConfig.java**            | 模组配置    |
| **ConfigSerializer.java**    | 配置序列化器  |
| **ConfigFilterManager.java** | 配置过滤器管理 |

## 服务类

| 类                           | 功能      |
|-----------------------------|---------|
| **GroupService.java**       | 分组服务    |
| **GroupRenameService.java** | 分组重命名服务 |
| **LinkRemovalService.java** | 链接移除服务  |
| **LinkValidator.java**      | 链接验证    |

## 工具类

| 类                        | 功能     |
|--------------------------|--------|
| **LogisticsTicker.java** | 物流计时器  |
| **CapabilityCache.java** | 能力缓存   |
| **ToolMode.java**        | 工具模式枚举 |