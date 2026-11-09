package com.coobird.staticlogistics.logistics.blueprint;

/// 当前架构的格式边界；不进行旧蓝图迁移。
public final class BlueprintDataMigration {
    private BlueprintDataMigration() {
    }

    public static BlueprintData migrate(BlueprintData source) {
        if (source.schemaVersion() != BlueprintData.CURRENT_SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported blueprint schema: " + source.schemaVersion());
        return source;
    }
}
