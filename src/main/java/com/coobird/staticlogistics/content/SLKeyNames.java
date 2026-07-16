package com.coobird.staticlogistics.content;

/**
 * 键位映射的稳定标识。
 * <p>
 * 公共代码只引用这些标识来构造动态键位文本，不直接依赖客户端键位类。
 */
public final class SLKeyNames {
    public static final String CATEGORY = "key.categories.staticlogistics";

    public static final String BLUEPRINT_PREVIEW_MOVE = "key.staticlogistics.blueprint_preview_move";
    public static final String BLUEPRINT_PREVIEW_ROTATE = "key.staticlogistics.blueprint_preview_rotate";
    public static final String BLUEPRINT_PREVIEW_MOVE_Y = "key.staticlogistics.blueprint_preview_move_y";
    public static final String TOOL_MODE_SCROLL = "key.staticlogistics.tool_mode_scroll";
    public static final String CLEAR_STORED_NODES = "key.staticlogistics.clear_stored_nodes";
    public static final String BLUEPRINT_UNDO = "key.staticlogistics.blueprint_undo";
    public static final String QUICK_FILTER_MARK = "key.staticlogistics.quick_filter_mark";
    public static final String PRIORITY_X10 = "key.staticlogistics.priority_x10";
    public static final String PRIORITY_X5 = "key.staticlogistics.priority_x5";
    public static final String GROUP_DETAILS_AND_EXPORT = "key.staticlogistics.group_details_and_export";

    private SLKeyNames() {
    }
}
