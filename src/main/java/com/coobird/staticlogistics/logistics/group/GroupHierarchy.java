package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/// 玩家自定义分组及连接归属；分组只有一层。
public final class GroupHierarchy {
    private GroupHierarchy() {
    }

    public static String connectionId(ConnectionKey key) {
        return "connection:" + UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String groupId(GroupKey key) {
        return key.ownerId() + ":" + key.internalId();
    }

    public static String groupFolder(CompoundTag data, GroupKey key) {
        CompoundTag links = data.getCompound("links");
        String folder = links.getString(groupId(key));
        return data.getCompound("entries").contains(folder)
            ? folder : "";
    }

    public static String connectionFolder(CompoundTag data, ConnectionKey key) {
        CompoundTag links = data.getCompound("links");
        String individual = connectionId(key);
        String folder = links.contains(individual)
            ? links.getString(individual) : groupFolder(data, key.groupKey());
        return data.getCompound("entries").contains(folder) ? folder : "";
    }

    public static boolean initializeDefaults(CompoundTag data) {
        if (data.getBoolean("defaultsInitialized")) return false;
        data.putBoolean("defaultsInitialized", true);
        return true;
    }

    public static boolean edit(CompoundTag data, String action, String id, String parent, String name) {
        CompoundTag entries = data.getCompound("entries");
        CompoundTag links = data.getCompound("links");
        switch (action) {
            case "select" -> {
                if (!id.isEmpty() && !entries.contains(id)) return false;
                data.putString("selectedFolder", id);
            }
            case "create" -> {
                if (entries.size() >= GroupConstraints.MAX_HIERARCHY_ENTRIES) return false;
                if (!parent.isEmpty()) return false;
                CompoundTag entry = new CompoundTag();
                entry.putString("name", GroupConstraints.normalizeName(name));
                entry.putString("parent", parent);
                entries.put(UUID.randomUUID().toString(), entry);
            }
            case "rename" -> {
                if (!entries.contains(id)) return false;
                entries.getCompound(id).putString("name", GroupConstraints.normalizeName(name));
            }
            case "assign" -> {
                if (!parent.isEmpty() && !entries.contains(parent)) return false;
                if (!links.contains(id)
                    && links.size() >= GroupConstraints.MAX_HIERARCHY_LINKS) return false;
                links.putString(id, parent);
            }
            case "delete" -> {
                if (!entries.contains(id)) return false;
                entries.remove(id);
                if (!entries.contains(data.getString("selectedFolder"))) data.remove("selectedFolder");
                if (!entries.contains(data.getString("fallbackFolder"))) data.remove("fallbackFolder");
                for (String link : links.getAllKeys().toArray(String[]::new)) {
                    if (!entries.contains(links.getString(link))) links.remove(link);
                }
            }
            default -> {
                return false;
            }
        }
        data.put("entries", entries);
        data.put("links", links);
        return true;
    }

    /// 校验分组和连接归属，不接受嵌套分组。
    public static CompoundTag sanitize(CompoundTag source) {
        CompoundTag result = new CompoundTag();
        CompoundTag sourceEntries = source.getCompound("entries");
        CompoundTag entries = new CompoundTag();
        for (String id : sourceEntries.getAllKeys()) {
            if (entries.size() >= GroupConstraints.MAX_HIERARCHY_ENTRIES) break;
            if (!validId(id)) continue;
            if (!sourceEntries.contains(id, Tag.TAG_COMPOUND)) continue;
            CompoundTag entry = sourceEntries.getCompound(id);
            if (!entry.getString("parent").isEmpty()) continue;
            putValidEntry(entries, id, entry, "");
        }
        result.put("entries", entries);

        CompoundTag links = new CompoundTag();
        CompoundTag sourceLinks = source.getCompound("links");
        for (String id : sourceLinks.getAllKeys()) {
            if (links.size() >= GroupConstraints.MAX_HIERARCHY_LINKS) break;
            if (!validId(id)) continue;
            if (!sourceLinks.contains(id, Tag.TAG_STRING)) continue;
            String folder = sourceLinks.getString(id);
            if (folder.isEmpty() || isFolder(entries, folder)) links.putString(id, folder);
        }
        result.put("links", links);
        copyFolderReference(source, result, entries, "selectedFolder");
        if (source.getBoolean("defaultsInitialized")) {
            result.putBoolean("defaultsInitialized", true);
        }
        return result;
    }

    public static boolean remapAssignments(CompoundTag data, Map<String, String> replacements) {
        if (replacements.isEmpty()) return false;
        CompoundTag links = data.getCompound("links");
        boolean changed = false;
        for (var replacement : replacements.entrySet()) {
            if (replacement.getKey().equals(replacement.getValue())
                || !links.contains(replacement.getKey(), Tag.TAG_STRING)) continue;
            String folder = links.getString(replacement.getKey());
            links.remove(replacement.getKey());
            if (!links.contains(replacement.getValue())
                && links.size() < GroupConstraints.MAX_HIERARCHY_LINKS) {
                links.putString(replacement.getValue(), folder);
            }
            changed = true;
        }
        if (changed) data.put("links", links);
        return changed;
    }

    public static boolean removeAssignments(CompoundTag data, Collection<String> ids) {
        CompoundTag links = data.getCompound("links");
        boolean changed = false;
        for (String id : ids) {
            if (!links.contains(id)) continue;
            links.remove(id);
            changed = true;
        }
        if (changed) data.put("links", links);
        return changed;
    }

    private static void putValidEntry(CompoundTag target, String id, CompoundTag source, String parent) {
        try {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", GroupConstraints.normalizeName(source.getString("name")));
            entry.putString("parent", parent);
            target.put(id, entry);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void copyFolderReference(CompoundTag source, CompoundTag target,
                                            CompoundTag entries, String key) {
        String folder = source.getString(key);
        if (isFolder(entries, folder)) target.putString(key, folder);
    }

    private static boolean isFolder(CompoundTag entries, String id) {
        return !id.isEmpty() && entries.contains(id, Tag.TAG_COMPOUND)
            && entries.getCompound(id).getString("parent").isEmpty();
    }

    private static boolean validId(String id) {
        return id != null && !id.isEmpty()
            && id.length() <= GroupConstraints.MAX_HIERARCHY_ID_LENGTH
            && id.chars().noneMatch(Character::isISOControl);
    }

}
