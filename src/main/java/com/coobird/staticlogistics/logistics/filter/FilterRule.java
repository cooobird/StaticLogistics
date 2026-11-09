package com.coobird.staticlogistics.logistics.filter;

import PortLib.extensions.com.mojang.serialization.Codec.PortCodecExtension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record FilterRule(Kind kind, Mode mode, String key) {
    public enum Kind {NORMAL, COMPONENT, TAG, DURABILITY, NUMERIC}

    public enum Mode {MATCH, FUZZY, INVERT}

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(Enum.valueOf(type, value));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown filter rule: " + value);
            }
        }, Enum::name);
    }

    public static final Codec<FilterRule> CODEC = PortCodecExtension.validate(RecordCodecBuilder.create(instance -> instance.group(
        enumCodec(Kind.class).fieldOf("kind").forGetter(FilterRule::kind),
        enumCodec(Mode.class).fieldOf("mode").forGetter(FilterRule::mode),
        Codec.STRING.fieldOf("key").forGetter(FilterRule::key)
    ).apply(instance, FilterRule::new)), rule ->
        rule.kind == Kind.NORMAL ? (rule.key.isEmpty() ? DataResult.success(rule)
            : DataResult.error(() -> "Normal rule has a key"))
            : (rule.key.length() <= 256 && validKey(rule)
            ? DataResult.success(rule) : DataResult.error(() -> "Invalid rule key")));

    private static boolean validKey(FilterRule rule) {
        if (rule.kind == Kind.NUMERIC) {
            String[] parts = rule.key.split("\\|", -1);
            if (parts.length != 3 || !(parts[1].equals("gt") || parts[1].equals("lt") || parts[1].equals("eq"))
                || parts[2].length() > 24 || !parts[2].matches("-?[0-9]+(\\.[0-9]+)?")) return false;
            return NbtRuleValues.validPath(parts[0]);
        }
        if (rule.kind == Kind.DURABILITY) {
            String[] parts = rule.key.split("\\|", -1);
            if (parts.length != 3 || !(parts[0].equals("gt") || parts[0].equals("lt") || parts[0].equals("eq"))
                || !(parts[1].equals("points") || parts[1].equals("percent"))
                || parts[2].isEmpty() || parts[2].length() > 9
                || !parts[2].chars().allMatch(Character::isDigit)) return false;
            return !parts[1].equals("percent") || Integer.parseInt(parts[2]) <= 100;
        }
        if (rule.kind == Kind.COMPONENT && rule.mode == Mode.MATCH && rule.key.isEmpty()) return true;
        if (rule.kind == Kind.TAG) {
            int separator = rule.key.indexOf('|');
            if (separator < 0) return ResourceLocation.tryParse(rule.key) != null;
            String type = rule.key.substring(0, separator);
            return ResourceLocation.tryParse(type) != null
                && ResourceLocation.tryParse(rule.key.substring(separator + 1)) != null;
        }
        return rule.kind == Kind.COMPONENT && NbtRuleValues.validPath(rule.key);
    }
}
