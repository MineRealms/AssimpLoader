package cn.minerealms.assimploader.lookdev.preview;

import cn.minerealms.assimploader.model.AssimpScene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LoadedModelCatalog {
    public record Entry(String name, AssimpScene scene) {}

    private LoadedModelCatalog() {}

    /** A scene can have canonical, legacy and mode aliases; show it only once. */
    public static List<Entry> snapshot(Map<String, AssimpScene> cache) {
        Map<AssimpScene, String> names = new IdentityHashMap<>();
        cache.forEach((name, scene) -> {
            if (scene != null) names.merge(scene, name, LoadedModelCatalog::preferredName);
        });
        List<Entry> entries = new ArrayList<>();
        names.forEach((scene, name) -> entries.add(new Entry(name, scene)));
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.scene().getImportMode().name()));
        return List.copyOf(entries);
    }

    private static String preferredName(String a, String b) {
        int comparison = Integer.compare(score(a), score(b));
        if (comparison != 0) return comparison > 0 ? a : b;
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static int score(String value) {
        int score = value.contains("/") ? 4 : 0;
        if (value.contains(":")) score += 2;
        if (value.endsWith(":STATIC") || value.endsWith(":DYNAMIC")) score -= 8;
        return score;
    }

    public static List<Entry> filter(List<Entry> entries, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return needle.isEmpty() ? entries : entries.stream()
                .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
}
