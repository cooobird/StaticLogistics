package com.coobird.staticlogistics.client.gui;

/// 客户端界面布局参数。坐标和尺寸以各组件的虚拟界面坐标为单位。
/// ConfiguratorScreen：配置器整体；GroupContents：左上方列表与搜索框。
/// NodeConfiguration：左下方节点配置；GroupDirectory：右侧连接目录。
/// Blueprint：蓝图页面；Tabs：左侧页签；Hud：手持工具提示。
/// ResourceDialog、HierarchyDialog、GrantDialog、FilterNumberDialog：对应弹窗。
/// FilterScreen：过滤器页面。贴图采样坐标仍在 SLGuiTextures，真实槽位坐标仍在 MenuLayout。
public final class ConfiguratorLayout {
    private ConfiguratorLayout() {
    }

    /// 所有 atlas 编辑框的底图和文字留白；修改会影响全部使用该组件的界面。
    public static final class EditBoxStyle {
        private EditBoxStyle() {
        }

        public static final int FRAME_OUTSET = 3;
        public static final int FRAME_BORDER = 2;
        public static final int TEXT_HORIZONTAL_PADDING = 4;
        public static final int TEXT_BASE_HEIGHT = 8;
    }

    /// 过滤器虚拟槽位网格的位置，相对过滤器界面左上角。
    /// 网格底图和槽距使用固定 atlas 素材；调整前需要同时确认素材尺寸。
    public static final class FilterGrid {
        private FilterGrid() {
        }

        public static final int START_X = 7;
        public static final int START_Y = 7;
    }

    /// 配置器和蓝图共用的整体尺寸及内容区。
    public static final class SharedScreen {
        private SharedScreen() {
        }

        public static final int GROUP_X = 288;
        public static final int WIDTH = 458, HEIGHT = 292, CONTENT_INSET = 1;
        public static final int CONTENT_WIDTH = 456, CONTENT_HEIGHT = 290;
        public static final int GROUP_CONTENT_X = 6, GROUP_CONTENT_Y = 6;
        public static final int GROUP_CONTENT_WIDTH = 274, GROUP_CONTENT_HEIGHT = 108;
        public static final int NODE_CONFIG_Y = com.coobird.staticlogistics.content.menu.MenuLayout.LINK_NODE_Y;
        public static final int NODE_CONFIG_HEIGHT = 172;
        public static final int INVENTORY_X = 288, INVENTORY_Y = 170;
    }

    /// 显示过长文字的提示框。
    public static final class Tooltip {
        private Tooltip() {
        }

        /// 过长文字提示框的最小宽度，以及距离屏幕两侧的留白总和。
        public static final int MIN_WIDTH = 120;
        public static final int SCREEN_WIDTH_INSET = 12;
        /// 提示框文字左右、上下的留白总和。
        public static final int TEXT_WIDTH_INSET = 10;
        public static final int TEXT_HEIGHT_INSET = 8;
        /// 提示框相对鼠标的水平偏移、鼠标上方留白和屏幕边缘留白。
        public static final int MOUSE_X_OFFSET = 8;
        public static final int MOUSE_Y_GAP = 4;
        public static final int SCREEN_MARGIN = 2;
        /// 提示文字相对提示框左上角的位置。
        public static final int TEXT_X = 5;
        public static final int TEXT_Y = 4;
    }

    /// 工具 HUD 的布局。
    public static final class Hud {
        private Hud() {
        }

        /// 操作提示中按键按钮的最小宽度、高度和文字两侧留白总和。
        public static final int KEY_MIN_WIDTH = 15;
        public static final int KEY_HEIGHT = 13;
        public static final int KEY_TEXT_WIDTH_INSET = 6;
        /// 相邻按键之间、按键区与操作说明之间的距离。
        public static final int KEY_GAP = 3;
        public static final int ACTION_GAP = 4;
        /// 按键文字和操作说明距离各自行顶部的距离。
        public static final int KEY_TEXT_Y = 2;
        public static final int ACTION_TEXT_Y = 2;
        /// 资源图标后的文字位置、每个资源末尾间距及换行高度。
        public static final int RESOURCE_TEXT_X = 19;
        public static final int RESOURCE_TEXT_Y = 4;
        public static final int RESOURCE_GAP = 7;
        public static final int RESOURCE_ROW_HEIGHT = 18;
        /// 资源区域换行参考宽度上限；实际还受屏幕宽度限制。
        public static final int RESOURCE_ROW_MAX_WIDTH = 180;
        /// 面板距离屏幕底部的距离。
        public static final int BOTTOM_MARGIN = 64;
        /// 面板距离屏幕右边缘的距离，为手持物品留出空间。
        public static final int RIGHT_MARGIN = 90;
        /// 面板距离屏幕边缘的最小距离。
        public static final int SCREEN_MARGIN = 8;
        /// 面板内容的水平内边距。
        public static final int PANEL_PADDING_X = 5;
        /// 面板内容的垂直内边距。
        public static final int PANEL_PADDING_Y = 4;
        /// 标题与右侧通道信息之间的最小距离。
        public static final int HEADER_GAP = 12;
        /// 标题下方的间距。
        public static final int HEADER_BOTTOM_GAP = 5;
        /// 已选数量与下一行之间的间距。
        public static final int SELECTED_BOTTOM_GAP = 3;
        /// 按键提示各行顶部之间的距离。
        public static final int ROW_HEIGHT = 15;
    }

    /// 授权管理弹窗的尺寸和列表布局。
    public static final class GrantDialog {
        private GrantDialog() {
        }

        /// 权限按钮和下拉选项中文字距离按钮左上角的位置。
        public static final int PERMISSION_TEXT_X = 4;
        public static final int PERMISSION_TEXT_Y = 4;
        /// 名称输入框的位置和尺寸，相对授权弹窗左上角。
        public static final int INPUT_X = 8, INPUT_Y = 30, INPUT_WIDTH = 143, INPUT_HEIGHT = 17;
        /// 添加按钮的位置和尺寸。
        public static final int ADD_X = 155, ADD_Y = 30, ADD_WIDTH = 57, ADD_HEIGHT = 17;
        /// 玩家列表位置、行宽、行高及文字位置。
        public static final int LIST_X = 8, LIST_Y = 52, LIST_WIDTH = 204, LIST_ROW_HEIGHT = 18;
        public static final int NAME_X = 12, NAME_Y = 5, NAME_WIDTH = 95;
        /// 每行权限按钮的位置、宽度和高度，Y 相对于该行。
        public static final int PERMISSION_X = 113, PERMISSION_Y = 1, PERMISSION_WIDTH = 95, PERMISSION_HEIGHT = 16;
        /// 权限下拉选项的位置和行高，Y 为相对选中行的偏移。
        public static final int MENU_ROW_OFFSET_Y = 17, MENU_BOTTOM_INSET = 49, MENU_ROW_HEIGHT = 16;
        /// 底部团队访问按钮及完成按钮的位置和尺寸。
        public static final int TEAM_X = 8, TEAM_Y = 153, TEAM_WIDTH = 130, TEAM_HEIGHT = 17;
        public static final int DONE_X = 145, DONE_Y = 153, DONE_WIDTH = 67, DONE_HEIGHT = 17;
        /// 弹窗标题及可拖动顶部区域。
        public static final int TITLE_X = 8, TITLE_Y = 8, SUBTITLE_Y = 19, DRAG_HEIGHT = 24;
        public static final int WIDTH = 220;
        public static final int HEIGHT = 177;
        public static final int ROWS = 5;
        public static final int ROW_HEIGHT = 19;
    }

    /// GroupContents 的布局，修改只影响对应界面或组件。
    public static final class GroupContents {
        private GroupContents() {
        }

        /// 左上列表表头的高度。
        public static final int HEADER_HEIGHT = 15;
        /// “坐标”表头距离列表左边的位置；只移动表头。
        public static final int HEADER_COORDINATE_X = 78;
        /// “输入／输出”表头距离列表左边的位置；只移动表头。
        public static final int HEADER_ROLE_X = 148;
        /// “连接面朝向”表头距离列表右边的距离；数值越大越靠左，只移动表头。
        public static final int HEADER_FACING_RIGHT = 54;
        /// 表头“名称”文字相对列表左边的位置。
        public static final int HEADER_NAME_X = 26;
        /// 表头文字相对表头顶部的位置。
        public static final int HEADER_TEXT_Y = 4;
        /// 分组行展开箭头相对列表左边的位置。
        public static final int GROUP_ARROW_X = 4;
        /// 分组名称相对列表左边的位置和文字最大宽度。
        public static final int GROUP_NAME_X = 18;
        public static final int GROUP_NAME_WIDTH = 120;
        /// 朝向文字最多占用的宽度。
        public static final int DIRECTION_TEXT_WIDTH = 43;
        /// 展开后节点名称相对普通节点名称向右缩进的距离。
        public static final int CHILD_NAME_INDENT = 8;
        /// 展开节点名称、普通节点名称和坐标文字的最大宽度。
        public static final int CHILD_NAME_WIDTH = 40;
        public static final int NODE_NAME_WIDTH = 48;
        public static final int COORDINATE_TEXT_WIDTH = 75;
        /// 模式文字与朝向列之间保留的距离。
        public static final int ROLE_TEXT_RIGHT_GAP = 5;
        /// 没有内容时提示文字距离列表顶部的距离。
        public static final int EMPTY_TEXT_Y = 20;
        /// 搜索框宽度微调：正数加宽，负数缩短；不会移动左边缘。
        public static final int SEARCH_WIDTH_ADJUST = 1;
        /// 左上列表左右位置：正数向右，负数向左；不移动搜索框。
        public static final int LIST_X_OFFSET = 0;
        /// 左上列表上下位置：正数向下，负数向上；不移动搜索框。
        public static final int LIST_Y_OFFSET = 0;
        /// 左上列表宽度：正数加宽，负数缩窄。
        public static final int LIST_WIDTH_ADJUST = 0;
        /// 左上列表高度：正数加高，负数缩短。
        public static final int LIST_HEIGHT_ADJUST = 0;
        /// 列表行宽度：正数加宽，负数缩窄；滚动条仅在需要时占用空间。
        public static final int LIST_ROW_WIDTH_ADJUST = 1;
        /// 滚动槽宽度，不改变里面滑块贴图的宽度。
        public static final int SCROLLBAR_WIDTH = 10;
        /// 滚动槽左右微调：正数向右，负数向左。
        public static final int SCROLLBAR_X_OFFSET = 2;
        /// 内部滑块距滚动槽左边的距离：数值越大，滑块越靠右。
        public static final int SCROLLBAR_THUMB_X_OFFSET = 1;
        /// 左上列表底部留白：数值越大，列表内容的下边缘越往上；初始也会缩短滚动槽。
        public static final int LIST_BOTTOM_INSET = 3;
        /// 节点行的步长。
        public static final int NODE_ROW_STEP = 19;
        /// 节点行的高度。
        public static final int NODE_ROW_HEIGHT = 18;
        /// 节点方块图标的横向位置。
        public static final int NODE_ICON_X = 6;
        /// 节点方块图标的纵向位置。
        public static final int NODE_ICON_Y = 1;
        /// 节点文字的横向位置。
        public static final int NODE_TEXT_X = 25;
        /// 节点文字的纵向位置。
        public static final int NODE_TEXT_Y = 5;
        /// 列表右侧的内边距。
        public static final int LIST_RIGHT_INSET = 11;
        /// 坐标列的横向位置。
        public static final int COORDINATE_COLUMN_X = 78;
        /// 模式列的横向位置。
        public static final int ROLE_COLUMN_X = 157;
        /// 朝向列的右侧。
        public static final int FACING_COLUMN_RIGHT = 54;
        /// 搜索框相对内容区的位置、宽度缩减量和高度；底图每侧额外延伸 3 像素。
        /// 搜索框左右位置：数值越大越靠右。
        public static final int SEARCH_X = 4;
        /// 搜索框上下位置：数值越大越靠下。
        public static final int SEARCH_Y = 5;
        /// 搜索框宽度扣减：数值越大，搜索框右边缘越往左、框越短。
        public static final int SEARCH_WIDTH_INSET = 8;
        /// 搜索框高度：数值越大，框越高；外面的底图每侧还会延伸 3 个单位。
        public static final int SEARCH_HEIGHT = 8;
        /// 滚动槽相对列表的右边距、Y 偏移及高度调整。
        /// 滚动槽左右位置：数值越小越靠右；每次可先调整 1 个单位。
        public static final int SCROLLBAR_RIGHT_INSET = 11;
        /// 滚动槽上下位置：数值越大越靠下；不改变列表行的位置。
        public static final int SCROLLBAR_Y_OFFSET = 0;
        /// 滚动槽高度：正数延长、负数缩短；改变后点击和拖动范围同步变化。
        public static final int SCROLLBAR_HEIGHT_ADJUST = 1;
        /// 滑块和槽两端的留白：数值越大，上下留白越多；不移动整个槽。
        public static final int SCROLLBAR_THUMB_VERTICAL_INSET = 1;
    }

    /// NodeConfiguration 的布局，修改只影响对应界面或组件。
    public static final class NodeConfiguration {
        private NodeConfiguration() {
        }

        /// 左列说明文字的横向位置；右列说明文字的位置单独设置。
        public static final int LABEL_X = 10;
        public static final int RIGHT_LABEL_X = 148;
        /// 标题、未选中提示和通道名称的上下位置。
        public static final int HEADER_TEXT_Y = 8;
        public static final int EMPTY_SELECTION_TEXT_Y = 38;
        public static final int CHANNEL_LABEL_Y = 25;
        public static final int EMPTY_CHANNEL_TEXT_Y = 88;
        /// 模式、资源和红石说明文字的上下位置。
        public static final int MODE_LABEL_Y = 45;
        public static final int RESOURCE_LABEL_Y = 65;
        public static final int REDSTONE_LABEL_Y = 85;
        /// 策略、输入参数、传输间隔的文字上下位置。
        public static final int STRATEGY_LABEL_Y = 105;
        public static final int INPUT_LABEL_Y = 124;
        public static final int INTERVAL_LABEL_Y = 126;
        public static final int INTERVAL_VALUE_X = 64;
        /// 过滤器、升级、批量状态和错误提示文字的位置。
        public static final int FILTER_LABEL_Y = 146;
        public static final int UPGRADE_LABEL_X = 112;
        public static final int UPGRADE_LABEL_Y = 146;
        public static final int BATCH_LABEL_Y = 147;
        public static final int ERROR_LABEL_Y = 160;
        /// 普通按钮及下拉按钮文字距离按钮顶部的距离。
        public static final int BUTTON_TEXT_Y = 5;
        public static final int DROPDOWN_TEXT_Y = 5;
        /// 下拉按钮文字右侧为箭头预留的宽度。
        public static final int DROPDOWN_ARROW_TEXT_SPACE = 12;
        /// 配置区横向分隔线距离左边的位置和长度。
        public static final int SEPARATOR_X = 10;
        public static final int SEPARATOR_WIDTH = 258;
        /// 数字输入框右侧上下箭头的宽度和每个按钮的高度。
        public static final int STEPPER_WIDTH = 14;
        public static final int STEPPER_BUTTON_HEIGHT = 9;
        /// 箭头图案相对按钮左上角的位置。
        public static final int STEPPER_ICON_X = 3;
        public static final int STEPPER_ICON_Y = 2;
        /// 分配策略和提取模式弹出菜单相对配置面板左边的位置。
        public static final int STRATEGY_MENU_X = 64;
        public static final int EXTRACTION_MENU_X = 176;
        /// 两种设置菜单的宽度、边框留白和底部参考位置。
        public static final int SETTING_MENU_WIDTH = 90;
        public static final int SETTING_MENU_INSET = 2;
        public static final int SETTING_MENU_BOTTOM_Y = 100;
        public static final int SETTING_MENU_BOTTOM_GAP = 4;
        /// 设置菜单文字相对菜单左边和选项行顶部的位置。
        public static final int SETTING_MENU_TEXT_X = 5;
        public static final int SETTING_MENU_TEXT_Y = 5;
        /// 设置菜单文字距菜单右侧保留的空间。
        public static final int SETTING_MENU_TEXT_RIGHT_INSET = 5;
        /// 资源类型行。位置相对于节点配置面板；该区域与其他节点布局常量一起调整。
        /// 资源类型行相对节点配置面板顶部的纵向偏移。
        public static final int RESOURCE_ROW_Y = 60;
        /// 资源类型按钮的横向位置。
        public static final int RESOURCE_BUTTON_X = 64;
        /// 资源类型按钮的宽度。
        public static final int RESOURCE_BUTTON_WIDTH = 176;
        /// 资源类型按钮的高度。
        public static final int RESOURCE_BUTTON_HEIGHT = 18;
        /// 新增通道按钮相对节点配置面板左边缘的横向偏移。
        public static final int ADD_CHANNEL_X = 180;
        /// 新增通道按钮相对节点配置面板顶部的纵向偏移。
        public static final int ADD_CHANNEL_Y = 21;
        /// 新增通道按钮的宽度。
        public static final int ADD_CHANNEL_WIDTH = 22;
        /// 新增通道按钮的高度。
        public static final int ADD_CHANNEL_HEIGHT = 18;
        /// 新增通道图标相对按钮左边缘的横向偏移。
        public static final int ADD_CHANNEL_ICON_X = 5;
        /// 新增通道图标相对按钮顶部的纵向偏移。
        public static final int ADD_CHANNEL_ICON_Y = 2;
        /// 存入端数值输入框。实际显示宽度由 *_FIELD_WIDTH 控制；INITIAL_WIDTH 只用于创建控件。
        /// 优先级输入框的横向位置。
        public static final int PRIORITY_FIELD_X = 64;
        /// 保留数量输入框的横向位置。
        public static final int STOCK_FIELD_X = 200;
        /// 数值输入框的纵向位置。
        public static final int NUMBER_FIELD_Y = 120;
        /// 优先级输入框初始的宽度。
        public static final int PRIORITY_INITIAL_WIDTH = 78;
        /// 保留数量输入框初始的宽度。
        public static final int STOCK_INITIAL_WIDTH = 58;
        /// 优先级输入框的宽度。
        public static final int PRIORITY_FIELD_WIDTH = 52;
        /// 保留数量输入框的宽度。
        public static final int STOCK_FIELD_WIDTH = 52;
        /// 数值输入框的高度。
        public static final int NUMBER_FIELD_HEIGHT = 18;
        /// 控件的高度。
        public static final int CONTROL_HEIGHT = 18;
        /// 提取、存入、双向和关闭按钮。
        /// 模式按钮的纵向位置。
        public static final int ROLE_Y = 40;
        /// 模式按钮的宽度。
        public static final int ROLE_WIDTH = 48;
        /// 提取按钮的横向位置。
        public static final int EXTRACT_X = 64;
        /// 存入按钮的横向位置。
        public static final int INSERT_X = 115;
        /// 双向按钮的横向位置。
        public static final int BOTH_X = 166;
        /// 启用按钮的横向位置。
        public static final int ENABLE_X = 217;
        /// 启用按钮的宽度。
        public static final int ENABLE_WIDTH = 49;
        /// 红石控制与提取端分配策略。
        /// 红石控制按钮的横向位置。
        public static final int REDSTONE_X = 64;
        /// 红石控制按钮的纵向位置。
        public static final int REDSTONE_Y = 80;
        /// 红石控制按钮的宽度。
        public static final int REDSTONE_WIDTH = 202;
        /// 分配策略按钮的横向位置。
        public static final int STRATEGY_X = 64;
        /// 分配策略按钮的纵向位置。
        public static final int STRATEGY_Y = 100;
        /// 分配策略按钮的宽度。
        public static final int STRATEGY_WIDTH = 78;
        /// 提取模式按钮的横向位置。
        public static final int EXTRACTION_X = 188;
        /// 提取模式按钮的宽度。
        public static final int EXTRACTION_WIDTH = 78;
        /// 批量操作与预设按钮。
        /// 批量操作按钮的纵向位置。
        public static final int BATCH_BUTTON_Y = 142;
        /// 批量操作按钮的宽度。
        public static final int BATCH_BUTTON_WIDTH = 56;
        /// 批量重置按钮的横向位置。
        public static final int BATCH_RESET_X = 150;
        /// 批量应用按钮的横向位置。
        public static final int BATCH_APPLY_X = 210;
        /// 保存预设按钮的横向位置。
        public static final int PRESET_SAVE_X = 210;
        /// 应用预设按钮的横向位置。
        public static final int PRESET_APPLY_X = 239;
        /// 预设按钮的纵向位置。
        public static final int PRESET_Y = 20;
        /// 预设按钮的宽度。
        public static final int PRESET_WIDTH = 27;
        /// 资源类型图标的横向内缩。
        public static final int RESOURCE_ICON_INSET_X = 2;
        /// 资源类型图标的纵向内缩。
        public static final int RESOURCE_ICON_INSET_Y = 1;
        /// 资源类型图标的尺寸。
        public static final int RESOURCE_ICON_SIZE = 16;
        /// 资源类型图标的步长。
        public static final int RESOURCE_ICON_STEP = 18;
        /// 资源类型文字的间距。
        public static final int RESOURCE_TEXT_GAP = 19;
        /// 资源类型文字的纵向位置。
        public static final int RESOURCE_TEXT_Y = 5;
        /// 资源类型箭头的右侧内缩。
        public static final int RESOURCE_ARROW_RIGHT_INSET = 11;
        /// 资源类型箭头的纵向位置。
        public static final int RESOURCE_ARROW_Y = 6;
        /// 资源类型溢出提示的右侧内缩。
        public static final int RESOURCE_OVERFLOW_RIGHT_INSET = 31;
        /// 资源类型溢出提示的阈值。
        public static final int RESOURCE_OVERFLOW_THRESHOLD = 8;
        /// 通道下拉列表。
        /// 通道的可见行数。
        public static final int CHANNEL_ROWS = 6;
        /// 通道选项行的高度。
        public static final int CHANNEL_ROW_HEIGHT = 18;
        /// 通道列表的横向位置。
        public static final int CHANNEL_LIST_X = 64;
        /// 通道列表的纵向位置。
        public static final int CHANNEL_LIST_Y = 39;
        /// 通道列表的宽度。
        public static final int CHANNEL_LIST_WIDTH = 118;
        /// 通道按钮的纵向位置。
        public static final int CHANNEL_BUTTON_Y = 20;
        /// 通道按钮的高度。
        public static final int CHANNEL_BUTTON_HEIGHT = 18;
        /// 通道列表的内边距。
        public static final int CHANNEL_LIST_INSET = 5;
        /// 通道列表滚动条的宽度。
        public static final int CHANNEL_LIST_SCROLL_WIDTH = 10;
        /// 通道列表文字的内边距。
        public static final int CHANNEL_LIST_TEXT_INSET = 5;
        /// 通道列表文字的纵向位置。
        public static final int CHANNEL_LIST_TEXT_Y = 5;
    }

    /// GroupDirectory 的布局，修改只影响对应界面或组件。
    public static final class GroupDirectory {
        private GroupDirectory() {
        }

        /// 拖动连接时，数量提示相对鼠标的位置。
        public static final int DRAG_LABEL_X_OFFSET = 8;
        public static final int DRAG_LABEL_Y_OFFSET = 8;
        /// 拖动提示文字四周的背景留白。
        public static final int DRAG_LABEL_PADDING = 2;
        /// 未选中行底部分隔线的厚度。
        public static final int ROW_DIVIDER_HEIGHT = 1;
        /// 树形连接线的粗细。
        public static final int TREE_LINE_WIDTH = 1;
        /// 所有者筛选下拉菜单的宽度以及底部分页按钮各自的宽度。
        public static final int SCOPE_MENU_WIDTH = 100;
        public static final int SCOPE_PREVIOUS_WIDTH = 30, SCOPE_PAGE_WIDTH = 40, SCOPE_NEXT_WIDTH = 30;
        /// 搜索栏的宽度。
        public static final int BAR_W = 149;
        /// 搜索栏的横向位置。
        public static final int BAR_X = 4;
        /// 搜索栏的纵向位置。
        public static final int BAR_Y = 29;
        /// 目录列表与滚动条。
        /// 列表相对右侧面板的横向偏移。
        public static final int LIST_OFFSET_X = 4;
        /// 列表相对界面顶部的纵向偏移。
        public static final int LIST_OFFSET_Y = 45;
        /// 滚动条的横向位置。
        public static final int SCROLLBAR_X = 154;
        /// 滚动条顶部相对界面顶部的纵向偏移，独立于列表位置。
        public static final int SCROLLBAR_Y = 44;
        /// 目录列表中选中行的宽度。
        public static final int SELECTION_WIDTH = 146;
        /// 新建分组按钮相对右侧面板左边缘的横向偏移。
        public static final int ADD_GROUP_BUTTON_X = 10;
        /// 新建分组按钮相对界面顶部的纵向偏移。
        public static final int ADD_GROUP_BUTTON_Y = 7;
        /// 新建分组按钮的宽度。
        public static final int ADD_GROUP_BUTTON_WIDTH = 18;
        /// 新建分组按钮的高度。
        public static final int ADD_GROUP_BUTTON_HEIGHT = 17;
        /// 范围选择按钮的横向位置。
        public static final int SCOPE_BUTTON_X = 52;
        /// 范围选择按钮的纵向位置。
        public static final int SCOPE_BUTTON_Y = 7;
        /// 范围选择按钮的宽度。
        public static final int SCOPE_BUTTON_WIDTH = 55;
        /// 范围选择按钮的高度。
        public static final int SCOPE_BUTTON_HEIGHT = 17;
        /// 授权管理按钮相对右侧面板的坐标和尺寸。
        public static final int GRANT_BUTTON_X = 111;
        public static final int GRANT_BUTTON_Y = 7;
        public static final int GRANT_BUTTON_WIDTH = 54;
        public static final int GRANT_BUTTON_HEIGHT = 17;
        /// 范围菜单的间距。
        public static final int SCOPE_MENU_GAP = 1;
        /// 范围菜单选项行的高度。
        public static final int SCOPE_MENU_ROW_HEIGHT = 18;
        /// 范围选择箭头的右侧内缩。
        public static final int SCOPE_ARROW_RIGHT_INSET = 11;
        /// 范围选择箭头的纵向偏移。
        public static final int SCOPE_ARROW_Y_OFFSET = 5;
        /// 标题的横向位置。
        public static final int TITLE_X = 6;
        /// 标题的纵向位置。
        public static final int TITLE_Y = 11;
        /// 标题文字与新建分组按钮之间的最小间距。
        public static final int TITLE_ADD_GAP = 3;
        /// 新建分组按钮与范围按钮之间的最小间距。
        public static final int ADD_SCOPE_GAP = 2;
        /// 搜索栏和搜索按钮。
        /// 搜索框文字的内边距。
        public static final int SEARCH_TEXT_INSET = 1;
        /// 搜索框文字的高度。
        public static final int SEARCH_TEXT_HEIGHT = 8;
        /// 搜索框右侧的内边距。
        public static final int SEARCH_RIGHT_INSET = 12;
        /// 放大镜相对搜索栏右边缘的横向内缩。
        public static final int SEARCH_ICON_RIGHT_INSET = 2;
        /// 放大镜相对搜索栏顶部的纵向偏移。
        public static final int SEARCH_ICON_Y_OFFSET = -1;
        /// 搜索栏文字的宽度。
        public static final int BAR_TEXT_WIDTH = 143;
        /// 滚动滑块相对轨道的纵向偏移；正数下移，负数上移。
        public static final int SCROLLBAR_THUMB_Y_OFFSET = 1;
        /// 列表的高度。
        public static final int LIST_HEIGHT = 117;
        /// 滚动条轨道的高度。
        public static final int SCROLLBAR_TRACK_HEIGHT = 117;
        /// 目录列表每行的高度。
        public static final int ROW_HEIGHT = 18;
        /// 文件夹和连接行内的图标、文字与树形连接线。
        /// 文件夹折叠箭头相对目录行起点的横向偏移。
        public static final int FOLDER_ARROW_X = 2;
        /// 文件夹折叠箭头相对目录行顶端的纵向偏移。
        public static final int FOLDER_ARROW_Y = 6;
        /// 文件夹图标相对目录行起点的横向偏移。
        public static final int FOLDER_ICON_X = 10;
        /// 文件夹图标相对目录行顶端的纵向偏移。
        public static final int FOLDER_ICON_Y = 1;
        /// 连接方块图标相对所在行内容起点的横向偏移。
        public static final int CONNECTION_ICON_X = 6;
        /// 连接方块图标相对所在行顶端的纵向偏移。
        public static final int CONNECTION_ICON_Y = 1;
        /// 树形竖向虚线相对列表左边缘的横向偏移。
        public static final int TREE_LINE_X = 18;
        /// 树形竖向虚线相对连接行顶端的纵向偏移。
        public static final int TREE_LINE_Y = 0;
        /// 树形横向分支的长度。
        public static final int TREE_BRANCH_LENGTH = 7;
        /// 树形虚线中每段实线的长度。
        public static final int TREE_DASH_LENGTH = 2;
        /// 树形虚线中相邻线段起点的距离。
        public static final int TREE_DASH_STEP = 3;
        /// 每层目录的横向缩进。
        public static final int DIRECTORY_INDENT_STEP = 20;
        /// 连接名称的横向位置。
        public static final int CONNECTION_TEXT_X = 26;
        /// 连接名称的宽度扣减。
        public static final int CONNECTION_TEXT_WIDTH_INSET = 31;
        /// 目录文字的横向位置。
        public static final int DIRECTORY_TEXT_X = 31;
        /// 目录文字的右侧内缩。
        public static final int DIRECTORY_TEXT_RIGHT_INSET = 3;
        /// 目录文字的纵向位置。
        public static final int DIRECTORY_TEXT_Y = 5;
        /// 右键菜单和弹窗。
        /// 右键菜单的横向边界。
        public static final int CONTEXT_MENU_MAX_X = 346;
        /// 右键菜单的纵向边界。
        public static final int CONTEXT_MENU_MAX_Y = 258;
        /// 右键菜单的宽度。
        public static final int CONTEXT_MENU_WIDTH = 110;
        /// 右键菜单选项的高度。
        public static final int CONTEXT_MENU_ROW_HEIGHT = 21;
        /// 弹窗的横向位置。
        public static final int DIALOG_X = 100;
        /// 弹窗的纵向位置。
        public static final int DIALOG_Y = 50;
    }

    /// ResourceDialog 的布局，修改只影响对应界面或组件。
    public static final class ResourceDialog {
        private ResourceDialog() {
        }

        /// 弹窗外框向左上扩出的距离，同时向右下扩出相同距离。
        public static final int PANEL_FRAME_OUTSET = 1;
        /// 标题文字和说明文字两侧预留的总宽度。
        public static final int TITLE_WIDTH_INSET = 35;
        public static final int CONTEXT_WIDTH_INSET = 20;
        /// 说明文字的悬停提示区域距离右侧的距离。
        public static final int CONTEXT_HIT_RIGHT_INSET = 10;
        /// 资源图标距离所在按钮左上角的位置。
        public static final int TYPE_ICON_X = 3;
        public static final int TYPE_ICON_Y = 4;
        /// 资源详情弹窗的底部预留高度。
        public static final int DETAIL_HEIGHT_EXTRA = 40;
        /// 类型按钮文字距离按钮左上角的位置和文字右侧预留宽度。
        public static final int DETAIL_TYPE_TEXT_X = 5;
        public static final int DETAIL_TYPE_TEXT_Y = 6;
        public static final int DETAIL_TYPE_TEXT_RIGHT_INSET = 25;
        /// 类型按钮箭头距离按钮右侧和顶部的距离。
        public static final int DETAIL_TYPE_ARROW_RIGHT_INSET = 15;
        public static final int DETAIL_TYPE_ARROW_Y = 7;
        /// 类型菜单上下边框的留白总和。
        public static final int DETAIL_MENU_HEIGHT_INSET = 4;
        /// 类型菜单选项文字距离选项左上角的位置和左右留白总和。
        public static final int DETAIL_OPTION_TEXT_X = 4;
        public static final int DETAIL_OPTION_TEXT_Y = 6;
        public static final int DETAIL_OPTION_TEXT_WIDTH_INSET = 10;
        /// 连接速率窗口标题和说明的左边位置。
        public static final int DETAIL_TEXT_X = 10;
        /// 连接速率窗口标题、说明、加载提示和方向标题的上下位置。
        public static final int DETAIL_TITLE_Y = 9;
        public static final int DETAIL_CONTEXT_Y = 24;
        public static final int DETAIL_LOADING_Y = 43;
        public static final int DETAIL_DIRECTION_Y = 63;
        /// 连接速率窗口说明文字两侧留白的总宽度。
        public static final int DETAIL_CONTEXT_WIDTH_INSET = 20;
        /// 总速率文字左边位置、顶部位置和右侧留白。
        public static final int DETAIL_TOTAL_X = 120;
        public static final int DETAIL_TOTAL_Y = 43;
        public static final int DETAIL_TOTAL_RIGHT_INSET = 5;
        /// 总速率提示响应区域相对窗口顶部的位置和高度。
        public static final int DETAIL_TOTAL_HIT_Y = 37;
        public static final int DETAIL_TOTAL_HIT_HEIGHT = 20;
        public static final int DETAIL_TOTAL_HIT_RIGHT_INSET = 10;
        /// 对端名称距离行左边和顶部的距离、名称右侧保留宽度。
        public static final int DETAIL_PEER_TEXT_X = 5;
        public static final int DETAIL_PEER_TEXT_Y = 7;
        public static final int DETAIL_PEER_NAME_WIDTH_INSET = 120;
        /// 对端速率文字距离窗口右边的距离。
        public static final int DETAIL_PEER_RATE_RIGHT_INSET = 105;
        /// 两种速率窗口的页码文字位置。
        public static final int PAGE_TEXT_X = 68;
        public static final int PAGE_TEXT_Y = 64;
        public static final int DETAIL_PAGE_TEXT_Y = 5;
        /// 资源速率行文字距离行顶部的距离。
        public static final int RATE_TEXT_Y = 8;
        /// 连接速率弹窗里的资源类型按钮：相对弹窗左上角。
        public static final int DETAIL_TYPE_X = 10, DETAIL_TYPE_Y = 37, DETAIL_TYPE_WIDTH = 105, DETAIL_TYPE_HEIGHT = 20;
        /// 类型下拉框及每个选项的位置和尺寸。
        public static final int DETAIL_MENU_X = 9, DETAIL_MENU_Y = 58, DETAIL_MENU_WIDTH = 108;
        public static final int DETAIL_OPTION_X = 11, DETAIL_OPTION_Y = 2, DETAIL_OPTION_WIDTH = 104, DETAIL_OPTION_HEIGHT = 20;
        /// 速率列表每行两侧的留白和行高；行之间的距离由 DETAIL_ROW_HEIGHT 控制。
        public static final int DETAIL_PEER_X = 8, DETAIL_PEER_RIGHT_INSET = 8, DETAIL_PEER_HEIGHT = 22;
        /// 速率弹窗底部按钮的位置和尺寸。
        public static final int DETAIL_FOOTER_GAP = 6, DETAIL_PREVIOUS_X = 10, DETAIL_NEXT_X = 38;
        public static final int DETAIL_PAGE_BUTTON_WIDTH = 24, DETAIL_FOOTER_BUTTON_HEIGHT = 20;
        public static final int DETAIL_DONE_RIGHT_INSET = 74, DETAIL_DONE_WIDTH = 64;
        public static final int DETAIL_CLOSE_RIGHT_INSET = 26, DETAIL_CLOSE_Y = 5, DETAIL_CLOSE_WIDTH = 20, DETAIL_CLOSE_HEIGHT = 18;
        public static final int DETAIL_ROWS = 6;
        public static final int DETAIL_ROW_HEIGHT = 24;
        public static final int DETAIL_GRID_Y = 76;
        /// 数值列的横向位置。
        public static final int VALUE_COLUMN_X = 94;
        /// 末列的宽度。
        public static final int LAST_COLUMN_WIDTH = 74;
        /// 面板最大的宽度。
        public static final int MAX_PANEL_WIDTH = 350;
        /// 面板最小的宽度。
        public static final int MIN_PANEL_WIDTH = 260;
        /// 面板两侧预留的总宽度。
        public static final int VIEWPORT_HORIZONTAL_MARGIN = 16;
        /// 网格单元格的最小宽度。
        public static final int CELL_MIN_WIDTH = 42;
        /// 网格文字的额外宽度。
        public static final int CELL_LABEL_EXTRA_WIDTH = 42;
        /// 网格的间距。
        public static final int GRID_GAP = 10;
        /// 网格左右两侧预留的总宽度。
        public static final int GRID_SIDE_INSETS = 20;
        /// 网格行的高度。
        public static final int GRID_ROW_HEIGHT = 28;
        /// 网格的顶部位置。
        public static final int GRID_TOP = 54;
        /// 网格按钮的高度。
        public static final int GRID_BUTTON_HEIGHT = 24;
        /// 资源类型网格的最大列数。
        public static final int MAX_COLUMNS = 4;
        /// 资源类型网格的最大行数。
        public static final int MAX_ROWS = 5;
        /// 计算网格行数时为标题、按钮等区域预留的高度。
        public static final int HEIGHT_RESERVE = 116;
        /// 面板的高度额外。
        public static final int PANEL_HEIGHT_EXTRA = 96;
        /// 底栏的顶部位置。
        public static final int FOOTER_TOP = 58;
        /// 上一页按钮的横向位置。
        public static final int PAGE_PREVIOUS_X = 10;
        /// 下一页按钮的横向位置。
        public static final int PAGE_NEXT_X = 38;
        /// 翻页按钮的宽度。
        public static final int PAGE_BUTTON_WIDTH = 24;
        /// 底部按钮的高度。
        public static final int FOOTER_BUTTON_HEIGHT = 20;
        /// 操作按钮的宽度。
        public static final int ACTION_BUTTON_WIDTH = 64;
        /// 取消按钮的右侧内缩。
        public static final int CANCEL_RIGHT_INSET = 146;
        /// 完成按钮的右侧内缩。
        public static final int DONE_RIGHT_INSET = 74;
        /// 应用按钮的右侧内缩。
        public static final int APPLY_RIGHT_INSET = 74;
        /// 关闭按钮的右侧内缩。
        public static final int CLOSE_RIGHT_INSET = 26;
        /// 关闭按钮的顶部位置。
        public static final int CLOSE_TOP = 5;
        /// 关闭按钮的宽度。
        public static final int CLOSE_WIDTH = 20;
        /// 关闭按钮的高度。
        public static final int CLOSE_HEIGHT = 18;
        /// 标题的横向位置。
        public static final int TITLE_X = 10;
        /// 标题的纵向位置。
        public static final int TITLE_Y = 9;
        /// 资源弹窗上下文说明相对顶部的纵向偏移。
        public static final int CONTEXT_Y = 24;
        /// 资源弹窗列标题相对顶部的纵向偏移。
        public static final int COLUMN_Y = 40;
        /// 资源弹窗提示文字相对顶部的纵向偏移。
        public static final int HINT_Y = 38;
        /// 资源类型列的横向位置。
        public static final int TYPE_COLUMN_X = 32;
    }

    /// HierarchyDialog 的布局，修改只影响对应界面或组件。
    public static final class HierarchyDialog {
        private HierarchyDialog() {
        }

        /// 名称输入框相对弹窗左边的位置、宽度和高度；上下位置见 NAME_Y。
        public static final int NAME_X = 10;
        public static final int NAME_WIDTH = 200;
        public static final int NAME_INPUT_HEIGHT = 18;
        /// 目标列表底框的宽度；位置和高度见 LIST_PANEL_X/Y/HEIGHT。
        public static final int LIST_PANEL_WIDTH = 204;
        /// 弹窗宽度。
        public static final int WIDTH = 220;
        /// 弹窗内容与边框的水平留白。
        public static final int PADDING = 10;
        /// 名称输入框的纵向位置。
        public static final int NAME_Y = 26;
        /// 控件的高度。
        public static final int CONTROL_HEIGHT = 18;
        /// 弹窗操作按钮的宽度。
        public static final int BUTTON_WIDTH = 96;
        /// 弹窗操作按钮之间的间距。
        public static final int BUTTON_GAP = 8;
        /// 命名操作按钮的纵向位置。
        public static final int NAME_BUTTON_Y = 52;
        /// 移动操作按钮的纵向位置。
        public static final int MOVE_BUTTON_Y = 178;
        /// 弹窗底部按钮与边框之间的留白。
        public static final int BOTTOM_PADDING = 10;
        /// 标题的纵向位置。
        public static final int TITLE_Y = 10;
        /// 删除警告文字的纵向位置。
        public static final int WARNING_Y = 28;
        /// 警告文字与按钮的间距。
        public static final int WARNING_BUTTON_GAP = 8;
        /// 拖动区域的内边距。
        public static final int DRAG_INSET = 4;
        /// 列表面板的横向位置。
        public static final int LIST_PANEL_X = 8;
        /// 列表面板的纵向位置。
        public static final int LIST_PANEL_Y = 40;
        /// 列表面板的高度。
        public static final int LIST_PANEL_HEIGHT = 130;
        /// 列表的横向位置。
        public static final int LIST_X = 12;
        /// 列表的纵向位置。
        public static final int LIST_Y = 44;
        /// 列表的宽度。
        public static final int LIST_WIDTH = 190;
        /// 列表行的高度。
        public static final int LIST_ROW_HEIGHT = 20;
        /// 列表的可见行数。
        public static final int LIST_VISIBLE_ROWS = 6;
        /// 目标选择提示的纵向位置。
        public static final int DESTINATION_HINT_Y = 25;
        /// 目标目录的缩进。
        public static final int DESTINATION_INDENT = 14;
        /// 目标文件夹图标的横向位置。
        public static final int DESTINATION_ICON_X = 26;
        /// 目标文件夹图标的纵向位置。
        public static final int DESTINATION_ICON_Y = 3;
        /// 目标名称的横向位置。
        public static final int DESTINATION_TEXT_X = 48;
        /// 目标名称的纵向位置。
        public static final int DESTINATION_TEXT_Y = 5;
        /// 目标名称的宽度。
        public static final int DESTINATION_TEXT_WIDTH = 136;
        /// 目标选项行的宽度。
        public static final int DESTINATION_ROW_WIDTH = 188;
        /// 目标选项行的高度。
        public static final int DESTINATION_ROW_HEIGHT = 19;
        /// 空列表提示的横向位置。
        public static final int EMPTY_MESSAGE_X = 16;
        /// 空列表提示的纵向位置。
        public static final int EMPTY_MESSAGE_Y = 62;
    }

    /// Tabs 的布局，修改只影响对应界面或组件。
    public static final class Tabs {
        private Tabs() {
        }

        /// 模式页签的最小宽度；文字较长时会自动加宽。
        public static final int MIN_TAB_WIDTH = 40;
        /// 资源展开面板的宽度。
        public static final int RESOURCE_FLYOUT_WIDTH = 98;
        /// 页签按钮底图比实际点击区域多出的宽度。
        public static final int TAB_DRAW_EXTRA_WIDTH = 1;
        /// 计算资源列表可见行数时，为窗口其他区域预留的高度。
        public static final int FLYOUT_HEIGHT_RESERVE = 32;
        /// 资源列表滚动条距离展开面板右边的距离。
        public static final int FLYOUT_SCROLL_RIGHT_INSET = 11;
        /// 页签的高度。
        public static final int TAB_HEIGHT = 19;
        /// 相邻页签顶部之间的垂直距离。
        public static final int TAB_STEP = 21;
        /// 页签的顶部位置。
        public static final int TAB_TOP = 5;
        /// 页签图标的横向位置。
        public static final int TAB_ICON_X = 1;
        /// 页签图标的纵向位置。
        public static final int TAB_ICON_Y = 2;
        /// 页签文字的横向位置。
        public static final int TAB_TEXT_X = 21;
        /// 页签文字的纵向位置。
        public static final int TAB_TEXT_Y = 5;
        /// 页签文字与右侧边框之间的最小间距。
        public static final int TAB_TEXT_RIGHT_PADDING = 2;
        /// 展开面板的间距。
        public static final int FLYOUT_GAP = 1;
        /// 展开列表的行间距。
        public static final int ROW = 23;
        /// 展开列表的纵向位置。
        public static final int FLYOUT_LIST_Y = 27;
        /// 最后一行下方到面板底部的额外间距。
        public static final int FLYOUT_BOTTOM_PADDING = 4;
        /// 标题相对面板左边缘的偏移。
        public static final int FLYOUT_TITLE_X = 8;
        /// 标题相对面板上边缘的偏移。
        public static final int FLYOUT_TITLE_Y = 10;
        /// 选项按钮相对面板左边缘的偏移。
        public static final int FLYOUT_ROW_X = 6;
        /// 选项按钮宽度相对面板宽度的缩减量。
        public static final int FLYOUT_ROW_WIDTH_INSET = 12;
        /// 选项按钮高度相对行间距的缩减量。
        public static final int FLYOUT_ROW_HEIGHT_INSET = 2;
        /// 选项图标相对面板左边缘的偏移。
        public static final int FLYOUT_ICON_X = 8;
        /// 选项图标相对行顶部的偏移。
        public static final int FLYOUT_ICON_Y = 2;
        /// 选项文字相对面板左边缘的偏移。
        public static final int FLYOUT_TEXT_X = 26;
        /// 选项文字允许显示的最大宽度。
        public static final int FLYOUT_TEXT_WIDTH = 43;
        /// 选项文字在垂直居中基础上的偏移。
        public static final int FLYOUT_TEXT_Y_OFFSET = 0;
        /// 勾选框左边缘到面板右边缘的距离。
        public static final int FLYOUT_CHECKBOX_RIGHT_INSET = 24;
        /// 勾选框在垂直居中基础上的偏移。
        public static final int FLYOUT_CHECKBOX_Y_OFFSET = 0;
    }

    /// FilterNumberDialog 的布局，修改只影响对应界面或组件。
    public static final class FilterNumberDialog {
        private FilterNumberDialog() {
        }

        /// 比较符、数值、单位控件各自的上下位置与高度。
        public static final int COMPARISON_Y = 20, COMPARISON_HEIGHT = 16;
        public static final int VALUE_Y = 20, VALUE_HEIGHT = 16;
        public static final int UNIT_Y = 20, UNIT_HEIGHT = 16;
        /// 取消和确认按钮各自的上下位置、宽度与高度。
        public static final int CANCEL_Y = 54, CANCEL_WIDTH = 40, CANCEL_HEIGHT = 16;
        public static final int CONFIRM_Y = 54, CONFIRM_WIDTH = 40, CONFIRM_HEIGHT = 16;
        /// 数值规则弹窗按钮文字距离按钮顶部的位置。
        public static final int BUTTON_TEXT_Y = 4;
        /// 数值输入无效时，提示文字相对弹窗左上角的位置。
        public static final int ERROR_X = 8;
        public static final int ERROR_Y = 43;
        /// 按钮文字左右留白总和。
        public static final int BUTTON_TEXT_WIDTH_INSET = 14;
        /// 弹窗宽度。
        public static final int WIDTH = 156;
        /// 弹窗高度。
        public static final int HEIGHT = 82;
        /// 标题的横向位置。
        public static final int TITLE_X = 8;
        /// 标题的纵向位置。
        public static final int TITLE_Y = 7;
        /// 标题的宽度。
        public static final int TITLE_WIDTH = 140;
        /// 拖动区域的高度。
        public static final int DRAG_HEIGHT = 19;
        /// 比较符按钮的横向位置。
        public static final int COMPARISON_X = 8;
        /// 比较符按钮的宽度。
        public static final int COMPARISON_WIDTH = 30;
        /// 数值输入框的横向位置。
        public static final int VALUE_X = 41;
        /// 数值输入框的宽度。
        public static final int VALUE_WIDTH = 64;
        /// 单位按钮的横向位置。
        public static final int UNIT_X = 108;
        /// 单位按钮的宽度。
        public static final int UNIT_WIDTH = 40;
        /// 单位文字的内边距。
        public static final int UNIT_TEXT_INSET = 4;
        /// 取消按钮的横向位置。
        public static final int CANCEL_X = 60;
        /// 确认按钮的横向位置。
        public static final int CONFIRM_X = 105;
    }

    /// Blueprint 的布局，修改只影响对应界面或组件。
    public static final class Blueprint {
        private Blueprint() {
        }

        /// 蓝图目录列表底框相对列表左上角的位置。
        public static final int DIRECTORY_FRAME_X_OFFSET = -1;
        public static final int DIRECTORY_FRAME_Y_OFFSET = -1;
        /// 列表底框相对列表宽度的增量和相对窗口高度的缩减量。
        public static final int DIRECTORY_FRAME_WIDTH_ADJUST = 2;
        public static final int DIRECTORY_FRAME_HEIGHT_INSET = 57;
        /// 列表行底部分隔线和树形连接线的厚度。
        public static final int DIRECTORY_DIVIDER_HEIGHT = 1;
        public static final int DIRECTORY_TREE_LINE_WIDTH = 1;
        /// 蓝图错误文字的左边位置、距离底边的位置和宽度缩减量。
        public static final int ERROR_X = 10;
        public static final int ERROR_BOTTOM = 43;
        public static final int ERROR_WIDTH_INSET = 20;
        /// 右侧目录：行图标、树形缩进和滚动条。
        /// 文件夹折叠箭头相对目录行起点的横向偏移。
        public static final int FOLDER_ARROW_X = 2;
        /// 文件夹折叠箭头相对目录行顶端的纵向偏移。
        public static final int FOLDER_ARROW_Y = 6;
        /// 文件夹图标相对目录行起点的横向偏移。
        public static final int FOLDER_ICON_X = 10;
        /// 文件夹图标相对目录行顶端的纵向偏移。
        public static final int FOLDER_ICON_Y = 1;
        /// 连接方块图标相对所在行内容起点的横向偏移。
        public static final int CONNECTION_ICON_X = 6;
        /// 连接方块图标相对所在行顶端的纵向偏移。
        public static final int CONNECTION_ICON_Y = 1;
        /// 目录的顶部位置。
        public static final int DIRECTORY_TOP = 52;
        /// 相邻目录行顶部之间的垂直距离。
        public static final int DIRECTORY_ROW_STEP = 18;
        /// 目录行的高度。
        public static final int DIRECTORY_ROW_HEIGHT = 18;
        /// 每级目录的横向缩进。
        public static final int DIRECTORY_DEPTH_INDENT = 20;
        /// 树形竖线相对列表左侧的偏移。
        public static final int DIRECTORY_TREE_X = 18;
        /// 树形横向分支的长度。
        public static final int DIRECTORY_TREE_BRANCH_LENGTH = 7;
        /// 树形虚线每段的长度。
        public static final int DIRECTORY_TREE_DASH_LENGTH = 2;
        /// 树形虚线各段起点之间的距离。
        public static final int DIRECTORY_TREE_DASH_STEP = 3;
        /// 目录滚动条顶部相对面板顶部的纵向偏移。
        public static final int DIRECTORY_SCROLL_Y = 53;
        /// 滚动滑块相对轨道的纵向偏移；正数下移，负数上移。
        public static final int DIRECTORY_SCROLL_THUMB_Y_OFFSET = 1;
        /// 目录滚动条轨道高度相对面板高度的扣减量。
        public static final int DIRECTORY_SCROLL_HEIGHT_INSET = 60;
        /// 目录的底部内缩。
        public static final int DIRECTORY_BOTTOM_INSET = 8;
        /// 目录列表的横向位置。
        public static final int DIRECTORY_LIST_X = 4;
        /// 目录行的横向位置。
        public static final int DIRECTORY_ROW_X = 4;
        /// 列表右边缘与滚动条之间的距离。
        public static final int DIRECTORY_SCROLL_GAP = 3;
        /// 目录滚动条的横向位置内边距。
        public static final int DIRECTORY_SCROLL_X_INSET = 18;
        /// 目录滚动条的右侧内缩。
        public static final int DIRECTORY_SCROLL_RIGHT_INSET = 3;
        /// 搜索框的横向位置。
        /// 右侧目录搜索框。
        public static final int SEARCH_X = 4;
        /// 搜索框的纵向位置。
        public static final int SEARCH_Y = 29;
        /// 搜索框的宽度扣减。
        public static final int SEARCH_WIDTH_INSET = 24;
        /// 搜索框的高度。
        public static final int SEARCH_HEIGHT = 16;
        /// 放大镜相对搜索框右边缘的横向内缩。
        public static final int SEARCH_ICON_RIGHT_INSET = 1;
        /// 放大镜相对搜索框顶部的纵向偏移。
        public static final int SEARCH_ICON_Y_OFFSET = 2;
        /// 视口边框的横向位置。
        /// 左侧蓝图视口及外框。
        public static final int VIEWPORT_FRAME_X = 6;
        /// 视口边框的纵向位置。
        public static final int VIEWPORT_FRAME_Y = 22;
        /// 视口边框的宽度扣减。
        public static final int VIEWPORT_FRAME_WIDTH_INSET = 11;
        /// 视口边框的高度扣减。
        public static final int VIEWPORT_FRAME_HEIGHT_INSET = 51;
        /// 视口的横向位置。
        public static final int VIEWPORT_X = 7;
        /// 视口的纵向位置。
        public static final int VIEWPORT_Y = 28;
        /// 视口的宽度扣减。
        public static final int VIEWPORT_WIDTH_INSET = 13;
        /// 视口的高度扣减。
        public static final int VIEWPORT_HEIGHT_INSET = 58;
        /// 底栏边框的横向位置。
        public static final int FOOTER_FRAME_X = 5;
        /// 底栏边框的底部位置。
        public static final int FOOTER_FRAME_BOTTOM = 29;
        /// 底栏边框的宽度扣减。
        public static final int FOOTER_FRAME_WIDTH_INSET = 10;
        /// 底栏边框的高度。
        public static final int FOOTER_FRAME_HEIGHT = 24;
        /// 标题的横向位置。
        public static final int TITLE_X = 10;
        /// 标题的纵向位置。
        public static final int TITLE_Y = 11;
        /// 右侧连接标题相对面板左边缘的横向偏移。
        public static final int DIRECTORY_TITLE_X = 8;
        /// 右侧连接标题相对面板顶部的纵向偏移。
        public static final int DIRECTORY_TITLE_Y = 10;
        /// 连接标题与范围按钮之间的最小间距。
        public static final int DIRECTORY_TITLE_GAP = 3;
        /// 目录文字的纵向位置。
        public static final int DIRECTORY_TEXT_Y = 5;
        /// 文件夹文字的横向位置。
        public static final int DIRECTORY_FOLDER_TEXT_X = 31;
        /// 连接文字的横向位置。
        public static final int DIRECTORY_CONNECTION_TEXT_X = 26;
        /// 目录文字的右侧内缩。
        public static final int DIRECTORY_TEXT_RIGHT_INSET = 3;
        /// 左右面板分界的横向位置。
        public static final int SPLIT_X = 286;
        /// 蓝图左侧面板距离整个界面左边和上边的距离
        public static final int LEFT_PANEL_X = 3;
        public static final int LEFT_PANEL_Y = 3;
        /// 蓝图左侧面板宽度相对分界线的缩减量，以及上下留白总和
        public static final int LEFT_PANEL_WIDTH_INSET = 5;
        public static final int LEFT_PANEL_HEIGHT_INSET = 6;
        /// 蓝图右侧面板距离整个界面顶部的距离、右侧留白及上下留白总和
        public static final int RIGHT_PANEL_Y = 3;
        public static final int RIGHT_PANEL_WIDTH_INSET = 3;
        public static final int RIGHT_PANEL_HEIGHT_INSET = 6;
        /// 范围选择按钮相对右侧面板的横向偏移。
        /// 右侧所有者筛选及下拉菜单。
        public static final int SCOPE_X = 88;
        /// 范围选择按钮相对界面顶部的纵向偏移。
        public static final int SCOPE_Y = 6;
        /// 范围选择按钮的宽度。
        public static final int SCOPE_WIDTH = 68;
        /// 按钮的高度。
        public static final int BUTTON_HEIGHT = 17;
        /// 范围菜单的间距。
        public static final int SCOPE_MENU_GAP = 1;
        /// 范围菜单选项行的高度。
        public static final int SCOPE_MENU_ROW_HEIGHT = 18;
        /// 蓝图范围菜单相对范围按钮向左移动的距离。
        public static final int SCOPE_MENU_LEFT_OFFSET = 32;
        /// 蓝图范围菜单每一行的宽度。
        public static final int SCOPE_MENU_WIDTH = 100;
        /// 菜单文字距离左侧的距离。
        public static final int SCOPE_MENU_TEXT_X = 4;
        /// 菜单文字距离顶部的距离。
        public static final int SCOPE_MENU_TEXT_Y = 4;
        /// 范围选择箭头的右侧内缩。
        public static final int SCOPE_ARROW_RIGHT_INSET = 11;
        /// 范围选择箭头的纵向偏移。
        public static final int SCOPE_ARROW_Y_OFFSET = 5;
        /// 底栏的底部位置。
        public static final int FOOTER_BOTTOM = 25;
        /// 全选按钮的横向位置。
        public static final int SELECT_ALL_X = 8;
        /// 全选按钮的宽度。
        public static final int SELECT_ALL_WIDTH = 52;
        /// 重置视角按钮的横向位置。
        public static final int RESET_VIEW_X = 64;
        /// 重置视角按钮的宽度。
        public static final int RESET_VIEW_WIDTH = 68;
        /// 创建按钮的右侧内缩。
        public static final int CREATE_RIGHT_INSET = 78;
        /// 创建按钮的宽度。
        public static final int CREATE_WIDTH = 70;
    }

    /// ConfiguratorScreen 的布局，修改只影响对应界面或组件。
    public static final class ConfiguratorScreen {
        private ConfiguratorScreen() {
        }

        /// 外框顶部相对界面原点的偏移。
        public static final int OUTER_TOP_OFFSET = 0;
        /// 标题的右侧留白。
        public static final int TITLE_RIGHT_PADDING = 8;
        /// 外层边框的内边距。
        public static final int OUTER_FRAME_INSET = 3;
        /// 左侧面板的横向位置。
        public static final int LEFT_PANEL_X = 1;
        /// 左侧面板的纵向位置。
        public static final int LEFT_PANEL_Y = 1;
        /// 左侧面板的宽度。
        public static final int LEFT_PANEL_WIDTH = 282;
        /// 上方面板的底部内缩。
        public static final int UPPER_PANEL_BOTTOM_INSET = 4;
        /// 左上方列表和搜索框整体相对内容区的右侧、底部内缩。
        public static final int GROUP_CONTENT_RIGHT_INSET = 3;
        public static final int GROUP_CONTENT_BOTTOM_INSET = 2;
        /// 节点配置面板的纵向偏移。
        public static final int NODE_PANEL_Y_OFFSET = -1;
        /// 节点配置面板的高度偏移量。
        public static final int NODE_PANEL_HEIGHT_OFFSET = 1;
        /// 右侧面板的横向位置。
        public static final int RIGHT_PANEL_X = 286;
        /// 右侧面板的纵向位置。
        public static final int RIGHT_PANEL_Y = 1;
        /// 右侧面板的宽度。
        public static final int RIGHT_PANEL_WIDTH = 170;
        /// 目录面板的高度。
        public static final int DIRECTORY_PANEL_HEIGHT = 164;
        /// 物品栏外框；物品栏素材和真实槽位的位置使用 MenuLayout。
        public static final int INVENTORY_PANEL_Y = 166;
        public static final int INVENTORY_PANEL_HEIGHT = 124;
        public static final int INVENTORY_FRAME_BORDER = 1;
        /// 资源速率按钮相对界面左边缘的横向偏移。
        public static final int RESOURCE_RATES_X = 244;
        /// 资源速率按钮的宽度。
        public static final int RESOURCE_RATES_WIDTH = 22;
        /// 资源速率按钮的高度。
        public static final int RESOURCE_RATES_HEIGHT = 18;
        /// 标题的纵向位置。
        public static final int TITLE_Y = 6;
        /// 原版物品栏标题隐藏位置的纵向位置。
        public static final int HIDDEN_VANILLA_INVENTORY_LABEL_Y = 1000;
        /// 物品栏标题相对物品栏顶部的纵向偏移。
        public static final int INVENTORY_TITLE_Y_OFFSET = 2;
    }

    /// FilterScreen 的布局，修改只影响对应界面或组件。
    public static final class FilterScreen {
        private FilterScreen() {
        }

        /// 过滤规则按钮上文字距离顶部的距离与左右预留宽度总和。
        public static final int RULE_BUTTON_TEXT_Y = 4;
        public static final int RULE_BUTTON_TEXT_WIDTH_INSET = 14;
        /// 过滤规则下拉列表边框留白；同时影响选项绘制和点击范围。
        public static final int DROPDOWN_INSET = 2;
        /// 下拉列表离屏幕边缘的最小距离。
        public static final int DROPDOWN_SCREEN_MARGIN = 2;
        /// 下拉选项文字距离列表左边、选项行顶部的距离。
        public static final int DROPDOWN_TEXT_X = 5;
        public static final int DROPDOWN_TEXT_Y = 3;
        /// 普通选项及带勾选框选项的文字右侧保留空间。
        public static final int DROPDOWN_TEXT_INSET = 10;
        public static final int DROPDOWN_CHECK_TEXT_INSET = 24;
        /// 勾选框距离列表右边和选项行顶部的距离，以及外框尺寸。
        public static final int DROPDOWN_CHECK_RIGHT_INSET = 16;
        public static final int DROPDOWN_CHECK_Y = 1;
        public static final int DROPDOWN_CHECK_SIZE = 12;
        /// 计算自适应列表宽度时，文字以外额外预留的空间。
        public static final int DROPDOWN_NORMAL_EXTRA_WIDTH = 12;
        public static final int DROPDOWN_CHECK_EXTRA_WIDTH = 26;
        /// 已选过滤样本的槽位位置，相对过滤器界面左上角。
        public static final int SAMPLE_X = 7, SAMPLE_Y = 84;
        /// 样本图标距槽位左上角的位置。
        public static final int SAMPLE_ICON_X = 1, SAMPLE_ICON_Y = 1;
        /// 未统一的黑白名单标记与批量选中数量文字位置。
        public static final int MIXED_STATE_X = 36, SELECTED_COUNT_X = 16, STATUS_TEXT_Y = 91;
        /// 数值规则弹窗初始位置，相对过滤器界面左上角。
        public static final int NUMBER_DIALOG_X = 10, NUMBER_DIALOG_Y = 45;
        /// 下拉列表的横向位置。
        public static final int DROPDOWN_X = 45;
        /// 下拉列表的纵向位置。
        public static final int DROPDOWN_Y = 100;
        /// 下拉列表的宽度。
        public static final int DROPDOWN_WIDTH = 124;
        /// 下拉选项行的高度。
        public static final int DROPDOWN_ROW_HEIGHT = 14;
        /// 下拉列表的可见行数。
        public static final int DROPDOWN_VISIBLE_ROWS = 6;
        /// 黑白名单按钮的横向位置。
        public static final int FILTER_BUTTON_X = 27;
        /// 黑白名单按钮的纵向位置。
        public static final int FILTER_BUTTON_Y = 84;
        /// 黑白名单按钮的尺寸。
        public static final int FILTER_BUTTON_SIZE = 16;
        /// 规则按钮的横向位置。
        public static final int RULE_BUTTON_X = 45;
        /// 规则按钮的纵向位置。
        public static final int RULE_BUTTON_Y = 84;
        /// 规则按钮的宽度。
        public static final int RULE_BUTTON_WIDTH = 124;
        /// 规则按钮的高度。
        public static final int RULE_BUTTON_HEIGHT = 16;
        /// 模式控件的横向位置。
        public static final int MODE_X = 7;
        /// 模式控件的纵向位置。
        public static final int MODE_Y = 104;
        /// 模式控件的宽度。
        public static final int MODE_WIDTH = 34;
        /// 模式控件的高度。
        public static final int MODE_HEIGHT = 10;
        /// 新增按钮的横向位置。
        public static final int ADD_X = 45;
        /// 移除按钮的横向位置。
        public static final int REDUCE_X = 59;
        /// 操作按钮的纵向位置。
        public static final int ACTION_Y = 103;
        /// 操作按钮的尺寸。
        public static final int ACTION_SIZE = 12;
        /// 取消按钮的横向位置。
        public static final int CANCEL_X = 141;
        /// 确认按钮的横向位置。
        public static final int CONFIRM_X = 157;
        /// 操作按钮的高度。
        public static final int ACTION_HEIGHT = 16;
        /// 操作按钮的图标纵向位置偏移量。
        public static final int ACTION_ICON_Y_OFFSET = 2;
        /// 黑白名单图标的横向位置偏移量。
        public static final int FILTER_ICON_X_OFFSET = 4;
        /// 黑白名单图标的纵向偏移。
        public static final int FILTER_ICON_Y_OFFSET = 2;
        /// 规则按钮箭头的右侧内缩。
        public static final int RULE_ARROW_RIGHT_INSET = 12;
        /// 规则按钮箭头的纵向偏移。
        public static final int RULE_ARROW_Y_OFFSET = 6;
    }
}
