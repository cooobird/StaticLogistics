package com.coobird.staticlogistics.content.menu;

/// 服务端菜单槽位与客户端背景共同遵循的布局尺寸。
public final class MenuLayout {
    public static final int BACKGROUND_WIDTH = 176;
    public static final int BACKGROUND_HEIGHT = 119;
    /// 物品栏整块素材为 166×82；格子边框距左上角两像素，物品再内缩一像素。
    public static final int INVENTORY_WIDTH = 166;
    public static final int INVENTORY_HEIGHT = 82;
    public static final int LINK_INVENTORY_X = 288;
    public static final int LINK_INVENTORY_Y = 189;
    public static final int INVENTORY_SLOT_X = 3;
    public static final int INVENTORY_SLOT_Y = 3;
    public static final int HOTBAR_SLOT_Y = 63;

    /// 节点配置区内的真实槽位坐标，与客户端绘制共用。
    public static final int NODE_FILTER_X = 66;
    public static final int LINK_NODE_Y = 118;
    public static final int NODE_SLOT_Y = LINK_NODE_Y + 144;
    public static final int NODE_UPGRADE_X = 174;

    private MenuLayout() {
    }
}
