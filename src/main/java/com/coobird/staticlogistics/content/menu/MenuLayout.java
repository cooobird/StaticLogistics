package com.coobird.staticlogistics.content.menu;

/**
 * 服务端菜单槽位与客户端背景共同遵循的布局尺寸。
 */
public final class MenuLayout {
    public static final int BACKGROUND_WIDTH = 208;
    public static final int BACKGROUND_HEIGHT = 122;
    public static final int INVENTORY_WIDTH = 176;
    public static final int INVENTORY_HEIGHT = 105;
    public static final int LINK_INVENTORY_X = 300;
    public static final int LINK_INVENTORY_Y = 165;
    public static final int INVENTORY_SLOT_X = 4;
    public static final int INVENTORY_SLOT_Y = 13;
    public static final int HOTBAR_SLOT_Y = 73;

    /**
     * 节点配置区内的真实槽位坐标，与客户端绘制共用。
     */
    public static final int NODE_FILTER_X = 104;
    public static final int NODE_SLOT_Y = 188;
    public static final int NODE_SPEED_UPGRADE_X = 172;
    public static final int NODE_RANGE_UPGRADE_X = 192;
    public static final int NODE_STACK_UPGRADE_X = 212;

    private MenuLayout() {
    }
}
