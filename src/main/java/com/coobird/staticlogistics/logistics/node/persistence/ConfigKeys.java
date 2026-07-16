package com.coobird.staticlogistics.logistics.node.persistence;

/**
 * 物流配置持久化与网络编辑共用的 NBT 字段名。
 */
public final class ConfigKeys {
    public static final String GROUP_ID = "group_id";
    public static final String GROUP_IDS = "group_ids";
    public static final String GROUPS = "groups_v2";
    public static final String SCHEMA_VERSION = "schema_version";
    public static final String OWNER = "owner";
    public static final String OWNER_NAME = "owner_name";
    public static final String OWNER_PROFILE = "owner_profile";
    public static final String INPUT_CHANNEL = "input_channel";
    public static final String OUTPUT_CHANNEL = "output_channel";
    public static final String STRATEGY = "strategy";
    public static final String EXTRACTION_MODE = "extraction_mode";
    public static final String PRIORITY = "priority";
    public static final String KEEP_STOCK = "keep_stock";
    public static final String FILTER_UPGRADES = "filter_upgrades";
    public static final String SELECTED_TYPES = "selected_types";
    public static final String SELECTED_TYPES_MASK = "selected_types_mask";
    public static final String GLOBAL_INPUT = "global_input";
    public static final String GLOBAL_OUTPUT = "global_output";

    private ConfigKeys() {
    }
}
