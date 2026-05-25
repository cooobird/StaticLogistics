# 更新日志（1.1.5-SNAPSHOT）

## GUI 组件化拆分
- **LinkConfiguratorScreen 拆分**：从单文件 685 行拆为 11 个独立组件 + 精简主屏。
  - `LeftSidebar`：左侧模式标签栏（5 个标签 + 图标 + Tooltip）
  - `TransferTypeGrid`：中间类型按钮网格（8 列多选 toggle + 物品图标 + Tooltip）
  - `GroupPanel`：右侧分组面板（搜索框 + 分组列表 + 滚动条 + 所有者头像 + 行内重命名 + Tooltip + 导出）
  - `NewGroupWidget`：右侧最下方新建分组（编辑框 + 大号按钮底层 + 加号按钮）
  - `TitleBar`：9-slice 标题栏组件（消除 3 处重复渲染）
  - `ContainerStats`：容器升级统计面板（范围/速度/维度/堆叠）
  - `FaceControls`：Face 界面通用控件集（开关/颜色/操作/策略/过滤按钮）
- **BaseFilterScreen 拆分**：从 610 行拆为 4 个组件 + 精简基类。
  - `FilterGridWidget`：过滤槽位网格（9×4 或 1×4 模式）+ Tooltip + 点击处理
  - `TagBarWidget`：标签下拉栏（4 行标签选择 + 左键启用/右键排除）
  - `BlacklistButton`：黑/白名单切换按钮（9-slice 拼接）
  - `NbtModeControls`：NBT 匹配模式（全匹配/部分匹配/忽略耐久）
- 其余 Screen 同步精简：`FaceConfiguratorScreen`、`ContainerConfiguratorScreen`、`FilterConfiguratorScreen`、`HandFilterScreen`、`BlueprintGroupScreen`

## 新建分组交互
- 右键分组列表项立即删除分组及其所有链接节点（发送 `C2SDeleteGroupPayload`）
- NewGroupWidget 独立编辑框：点击输入名称，按 Enter 或点击加号提交创建
- `ClientLinkData.addKnownGroup/removeKnownGroup`：即使无链接节点的分组也能在列表中显示
- 分组 Tooltip 新增红色「右键删除」提示行
- 加号按钮命中判定优先于编辑框，避免误触发编辑模式

## 项目结构重组
- `ToolMode` 从 `item/util` 移至 `api/type`（被 7 个文件引用）
- `BlueprintData` 从 `item` 移至 `api`（跨层共享的数据记录）
- `TransferLogManager` 从 `util` 移至 `transfer`
- `PlayerEvents` 从 `server/event/game/entity` 提升至 `server/event`（消除 4 级嵌套）
- `SLLevelEvents` 从 `server/event/game` 提升至 `server/event`
- `SLEvents` 重命名为 `ServerEvents`（与 `ClientEvents` 对齐）
- `S2CConfigSyncPayload` 重命名为 `S2CConfigSyncPacket`（统一 S2C 命名）

## 死代码清理
- `ClientLinkData`：移除 7 个未使用方法（`getFaceConfig`×3、`getAllNodes`、`remove`、`getGroupsByOwner`、`getNodesInGroup`）
- `GroupPanel`：移除 `reposition()`、`positionRenameBox()`
- `LeftSidebar`：移除 `getHoveredMode()`
- `FilterGridWidget`：移除 `collectEnhancedTagsForFluid()`
- `LinkManager`：移除 `getFaceConfigCountAt()`

## 重复消除
- `playClickSound()`：创建 `SoundUtil`，5 处重复定义改为统一调用
- `renderTitle`：`TitleBar.render()` 替代 `LinkConfiguratorScreen` 中的重复代码
- `NewGroupWidget` 解耦 `GroupPanel` 常量引用（`LIST_OFFSET_X`/`SIDE_PANEL_X` 改为本地常量）

## 修复
- 加号按钮被编辑框命中区域覆盖导致无法提交（调整检测顺序）
- `g.fill` 参数顺序写反导致悬停出现巨大白色区域
- 搜索框重复渲染 EditBox 背景与侧面板纹理内置槽位冲突
- `NODE_CONFIG`/`FACE_CONFIG` 枚举引用同步修复
- **`LinkConfiguratorScreen` 输入处理代码被误删**：`mouseClicked`/`keyPressed`/`mouseScrolled` 等全部丢失，导致所有按钮和编辑框无法交互、E 键无法关闭 UI
- **`ServerEvents` 注册缺失**：`git checkout` 还原文件时丢失了 `C2SOpenContainerConfigPayload`、`C2SOpenFaceConfigPayload`、`C2SClearStoredNodesPayload`、`C2SDeleteGroupPayload` 的注册，导致面配置切换到容器配置时崩溃
- 分组 Tooltip 坐标列表过长：默认只显示 5 条，按住 Shift 显示全部
