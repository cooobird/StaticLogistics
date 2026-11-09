package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.LogisticsUpgrade;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.ChannelConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.ChannelRedstoneMode;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

// 本地预设只保存配置数值，不保存过滤器、升级或玩家物品。
public final class ConfigurationPreset {
    private ConfigurationPreset() {
    }

    private static Path path(String name) {
        Path config = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
        if (name.isEmpty()) return config.resolve("staticlogistics-preset.properties");
        if (name.isBlank() || name.length() > 32) throw new IllegalArgumentException("Invalid preset name");
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
        return config.resolve("staticlogistics-presets").resolve(encoded + ".properties");
    }

    public static void delete(String name) throws IOException {
        Files.delete(path(name));
    }

    public static List<String> names() throws IOException {
        List<String> result = new ArrayList<>();
        if (Files.isRegularFile(path(""))) result.add("");
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve("config/staticlogistics-presets");
        if (Files.isDirectory(directory)) try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile).map(file -> file.getFileName().toString())
                .filter(file -> file.endsWith(".properties"))
                .forEach(file -> {
                    try {
                        String name = new String(Base64.getUrlDecoder().decode(file.substring(0, file.length() - 11)), StandardCharsets.UTF_8);
                        if (!name.isBlank() && name.length() <= 32 && path(name).getFileName().toString().equals(file))
                            result.add(name);
                    } catch (IllegalArgumentException ignored) {
                    }
                });
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(result);
    }

    public static void save(String name, LinkConfiguratorMenu menu, long priority, long stock) throws IOException {
        Properties p = new Properties();
        p.setProperty("input", Boolean.toString(menu.showsInputSettings()));
        p.setProperty("output", Boolean.toString(menu.showsOutputSettings()));
        p.setProperty("enabled", Boolean.toString(menu.isChannelEnabled()));
        p.setProperty("redstone", menu.getRedstoneMode().name());
        var filterStack = menu.getSlot(0).getItem();
        if (filterStack.getItem() instanceof LogisticsUpgrade upgrade && upgrade.isFilterUpgrade()) {
            FilterData filter = filterStack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
            var ops = RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
            String encoded = FilterData.CODEC.encodeStart(ops, filter).result().orElseThrow(() -> new IllegalArgumentException("Cannot encode filter")).toString();
            if (encoded.length() > 262144) throw new IllegalArgumentException("Filter data too large");
            p.setProperty("filter", encoded);
        }
        if (menu.showsInputSettings()) {
            p.setProperty("priority", Long.toString(priority));
            p.setProperty("stock", Long.toString(stock));
        }
        if (menu.showsOutputSettings()) {
            p.setProperty("strategy", menu.getStrategy().id().toString());
            p.setProperty("extraction", menu.getExtractionMode().name());
            p.setProperty("types", String.join(",", menu.getSelectedTypeIds().stream().map(Object::toString).toList()));
        }
        Files.createDirectories(path(name).getParent());
        try (var output = Files.newOutputStream(path(name), StandardOpenOption.CREATE_NEW)) {
            p.store(output, "Static Logistics configuration preset");
        }
    }

    public static List<ChannelConfigurationEdit> load(String name, LinkConfiguratorMenu menu) throws IOException {
        Properties p = new Properties();
        try (var input = Files.newInputStream(path(name))) {
            p.load(input);
        }
        boolean hasInput = booleanValue(p, "input"), hasOutput = booleanValue(p, "output");
        if (hasInput != menu.showsInputSettings() || hasOutput != menu.showsOutputSettings())
            throw new IllegalArgumentException("Incompatible preset");
        List<ChannelConfigurationEdit> edits = new ArrayList<>();
        edits.add(new ChannelConfigurationEdit.EnabledEdit(booleanValue(p, "enabled")));
        edits.add(new ChannelConfigurationEdit.RedstoneEdit(ChannelRedstoneMode.valueOf(required(p, "redstone"))));
        String filter = p.getProperty("filter");
        if (filter != null) {
            if (filter.length() > 262144) throw new IllegalArgumentException("Filter data too large");
            var ops = RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
            FilterData data = FilterData.CODEC.parse(ops, JsonParser.parseString(filter)).result()
                .orElseThrow(() -> new IllegalArgumentException("Invalid filter"));
            edits.add(new ChannelConfigurationEdit.FilterEdit(data));
        }
        if (hasInput) {
            edits.add(new ChannelConfigurationEdit.NumberEdit(ChannelConfigurationEdit.NumberField.PRIORITY, Long.parseLong(required(p, "priority"))));
            edits.add(new ChannelConfigurationEdit.NumberEdit(ChannelConfigurationEdit.NumberField.KEEP_STOCK, Long.parseLong(required(p, "stock"))));
        }
        if (hasOutput) {
            ResourceLocation strategyId = ResourceLocation.tryParse(required(p, "strategy"));
            var strategy = strategyId == null ? null : DistributionStrategyRegistry.get(strategyId);
            if (strategy == null) throw new IllegalArgumentException("Unknown strategy");
            edits.add(new ChannelConfigurationEdit.StrategyEdit(strategy));
            edits.add(new ChannelConfigurationEdit.ExtractionEdit(ExtractionMode.valueOf(required(p, "extraction"))));
            String types = required(p, "types");
            List<ResourceLocation> ids = types.isEmpty() ? List.of() : Arrays.stream(types.split(",", -1)).map(ResourceLocation::tryParse).toList();
            if (ids.stream().anyMatch(id -> id == null || TransferRegistries.get(id) == null))
                throw new IllegalArgumentException("Unknown resource type");
            edits.add(new ChannelConfigurationEdit.SelectedTypesEdit(ids));
        }
        return List.copyOf(edits);
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing preset field");
        return value;
    }

    private static boolean booleanValue(Properties p, String key) {
        String value = required(p, key);
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean");
        return Boolean.parseBoolean(value);
    }
}
