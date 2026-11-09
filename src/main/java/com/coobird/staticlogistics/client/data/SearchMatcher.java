package com.coobird.staticlogistics.client.data;

import com.ibm.icu.text.Transliterator;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/// GUI 搜索同时支持原文、无声调全拼和拼音首字母。
public final class SearchMatcher {
    private static final int CACHE_LIMIT = 4096;
    private static final ThreadLocal<Transliterator> PINYIN = ThreadLocal.withInitial(() -> Transliterator.getInstance("Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC"));
    private static final Map<String, Spellings> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Spellings> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private record Spellings(String full, String initials) {
    }

    private SearchMatcher() {
    }

    public static boolean matches(String text, String query) {
        String source = text.toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        if (source.contains(needle)) return true;
        if (needle.isEmpty() || source.codePoints().noneMatch(SearchMatcher::isHan)) return false;
        String compactQuery = needle.replaceAll("\\s+", "");
        Spellings spellings;
        synchronized (CACHE) {
            spellings = CACHE.computeIfAbsent(source, SearchMatcher::spellings);
        }
        return spellings.full().contains(compactQuery) || spellings.initials().contains(compactQuery);
    }

    private static Spellings spellings(String source) {
        String full = PINYIN.get().transliterate(source).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        StringBuilder initials = new StringBuilder();
        source.codePoints().forEach(codePoint -> {
            if (isHan(codePoint)) {
                String pinyin = PINYIN.get().transliterate(new String(Character.toChars(codePoint))).toLowerCase(Locale.ROOT).trim();
                if (!pinyin.isEmpty()) initials.appendCodePoint(pinyin.codePointAt(0));
            } else if (!Character.isWhitespace(codePoint)) {
                initials.appendCodePoint(codePoint);
            }
        });
        return new Spellings(full, initials.toString());
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
