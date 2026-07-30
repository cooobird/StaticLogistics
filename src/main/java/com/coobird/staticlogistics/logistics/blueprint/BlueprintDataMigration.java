package com.coobird.staticlogistics.logistics.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图数据从旧方块级链接到精确面链接的显式版本链。
 */
public final class BlueprintDataMigration {
    private BlueprintDataMigration() {
    }

    public static BlueprintData migrate(BlueprintData source) {
        if (source.schemaVersion() < 1
            || source.schemaVersion() > BlueprintData.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "Unsupported blueprint schema version: " + source.schemaVersion());
        }
        BlueprintData migrated = source;
        while (migrated.schemaVersion() < BlueprintData.CURRENT_SCHEMA_VERSION) {
            migrated = switch (migrated.schemaVersion()) {
                case 1 -> migrateV1ToV2(migrated);
                default -> throw new IllegalStateException(
                    "Missing blueprint migration from version: " + migrated.schemaVersion());
            };
        }
        return migrated;
    }

    private static BlueprintData migrateV1ToV2(BlueprintData source) {
        Map<BlockPos, BlueprintData.BlockEntry> entriesByPosition = new LinkedHashMap<>();
        source.blocks().forEach(entry -> entriesByPosition.putIfAbsent(entry.relativePos(), entry));

        List<BlueprintData.BlockEntry> migratedBlocks = source.blocks().stream().map(entry -> {
            Map<Direction, BlueprintData.FaceEntry> migratedFaces = new LinkedHashMap<>();
            entry.faces().forEach((direction, face) -> migratedFaces.put(direction,
                new BlueprintData.FaceEntry(
                    face.faceConfig(),
                    face.linkConfig(),
                    face.filterUpgrades(),
                    BlueprintGeometry.resolveLinks(entry, face, entriesByPosition))));
            return new BlueprintData.BlockEntry(
                entry.relativePos(), migratedFaces, entry.containerUpgrades(), List.of());
        }).toList();

        return new BlueprintData(BlueprintData.CURRENT_SCHEMA_VERSION,
            source.anchor(), source.corner2(), source.groupId(), migratedBlocks);
    }
}
