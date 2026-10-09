package dev.px.karatsuba.client.setting;

import dev.px.karatsuba.adapter.game.item.ItemCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What an item picker lists for a search, in order. Shared by every picker, so
 * "oak" finds the same items in the same order wherever it is typed.
 */
public final class ItemSearch {

    private ItemSearch() {
    }

    /** Items for an item picker; blocks, by block id, for a block picker. */
    public static List<ItemCatalog.Entry> catalogue(ItemSetting setting) {
        return setting.getScope() == ItemSetting.Scope.BLOCKS ? ItemCatalog.blocks() : ItemCatalog.all();
    }

    /**
     * @return the catalogue's entries matching {@code query}, best first
     *
     * <p>With no search, ticked items come first so a player sees their list at a
     * glance, then the rest in catalogue order.
     */
    public static List<ItemCatalog.Entry> filter(ItemSetting setting, List<ItemCatalog.Entry> catalogue,
                                                 String query, boolean selectedOnly) {
        String[] terms = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        boolean searching = !query.trim().isEmpty();

        List<Ranked> matches = new ArrayList<>();
        for (int i = 0; i < catalogue.size(); i++) {
            ItemCatalog.Entry entry = catalogue.get(i);
            boolean selected = setting.contains(entry.getId());
            if (selectedOnly && !selected) {
                continue;
            }
            int rank = searching ? rank(entry, terms) : (selected ? 0 : 1);
            if (rank >= 0) {
                matches.add(new Ranked(entry, rank, i));
            }
        }
        matches.sort(Comparator.comparingInt((Ranked r) -> r.rank).thenComparingInt(r -> r.order));

        List<ItemCatalog.Entry> result = new ArrayList<>(matches.size());
        for (Ranked ranked : matches) {
            result.add(ranked.entry);
        }
        return result;
    }

    /**
     * @return how well an item matches every term, lower is better, or -1 for no match
     *
     * <p>Each term has to appear in the name or the id. Then: the name starting with
     * the first term beats a word in it starting with it, which beats the term
     * turning up anywhere. So "oak" lists Oak Planks, then Dark Oak Planks, then
     * anything with "oak" mid-word.
     */
    private static int rank(ItemCatalog.Entry entry, String[] terms) {
        String name = entry.getName().toLowerCase(Locale.ROOT);
        String id = entry.getId();
        for (String term : terms) {
            if (!name.contains(term) && !id.contains(term)) {
                return -1;
            }
        }
        String first = terms[0];
        if (name.startsWith(first)) {
            return 0;
        }
        if (name.contains(" " + first)) {
            return 1;
        }
        return 2;
    }

    private record Ranked(ItemCatalog.Entry entry, int rank, int order) {
    }
}
