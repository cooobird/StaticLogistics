package com.coobird.staticlogistics.network;

/**
 * 节点配置界面发往服务端的编辑字段名。
 *
 * <p>这些字段只属于网络编辑协议，不作为世界或蓝图的持久化字段。
 */
public final class ConfigEditKeys {
    public static final String GLOBAL_INPUT = "globalInput";
    public static final String GLOBAL_OUTPUT = "globalOutput";
    public static final String INPUT_CHANNEL = "inputChannel";
    public static final String OUTPUT_CHANNEL = "outputChannel";
    public static final String PRIORITY = "priority";
    public static final String KEEP_STOCK = "keepStock";
    public static final String STRATEGY = "strategy";
    public static final String EXTRACTION_MODE = "extractionMode";
    public static final String SELECTED_TYPES = "selectedTypes";
    public static final String OPEN_FILTER = "openFilter";
    public static final String OPEN_FACE_CONFIG = "openFaceConfig";
    public static final String IS_INPUT = "isInput";

    private ConfigEditKeys() {
    }
}
