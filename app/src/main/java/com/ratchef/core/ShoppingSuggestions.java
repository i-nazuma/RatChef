package com.ratchef.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds shopping-list items that are probably the same thing to buy and proposes one merged item:
 * different kinds (½ yellow onion + 1 red onion), different units, lemon juice next to lemons,
 * or names that look alike. Nothing is merged until you accept a suggestion.
 */
public final class ShoppingSuggestions {

    public static final class Suggestion {
        public final int[] items;        // indices into the list passed to compute()
        public final Ingredient merged;
        public final String reason;      // "kinds", "units", "fruit", "similar", "same"

        Suggestion(int[] items, Ingredient merged, String reason) {
            this.items = items;
            this.merged = merged;
            this.reason = reason;
        }
    }

    private ShoppingSuggestions() {}

    public static List<Suggestion> compute(List<Ingredient> list, boolean german) {
        List<Suggestion> out = new ArrayList<>();
        Map<String, List<Integer>> byEntry = new LinkedHashMap<>();
        List<Integer> unknown = new ArrayList<>();
        Canon.Match[] matches = new Canon.Match[list.size()];

        for (int i = 0; i < list.size(); i++) {
            Ingredient ing = Canon.normalize(list.get(i));
            Canon.Match m = Canon.match(ing.name, ing.unit);
            matches[i] = m;
            if (m == null) unknown.add(i);
            else byEntry.computeIfAbsent(m.entry.key, k -> new ArrayList<>()).add(i);
        }

        for (List<Integer> group : byEntry.values()) {
            Canon.Entry e = matches[group.get(0)].entry;
            if (group.size() >= 2) {
                Merge mg = mergeKnown(list, matches, group, e, german);
                out.add(new Suggestion(toArray(group), mg.result, mg.reason));
            } else {
                // A lone "2 squeezes of lemon juice": suggest buying the fruit instead.
                int idx = group.get(0);
                Canon.Match m = matches[idx];
                if (e.fruitForms && !m.form.isEmpty() && !e.key.equals("orange")) {
                    double f = Canon.fruitsFor(Canon.normalize(list.get(idx)), m);
                    if (!Double.isNaN(f) && f <= 4) {
                        out.add(new Suggestion(new int[]{idx}, fruit(e, f, german, m.form), "fruit"));
                    }
                }
            }
        }

        for (List<Integer> group : similarGroups(list, unknown)) {
            out.add(new Suggestion(toArray(group), mergeLoose(list, group), "similar"));
        }
        out.sort((a, b) -> a.items[0] - b.items[0]);
        return out;
    }

    // ------------------------------------------------------------------ merging known items

    private static final class Merge {
        final Ingredient result;
        final String reason;

        Merge(Ingredient result, String reason) {
            this.result = result;
            this.reason = reason;
        }
    }

    private static Merge mergeKnown(List<Ingredient> list, Canon.Match[] matches, List<Integer> group,
                                   Canon.Entry e, boolean german) {
        Set<String> variants = new LinkedHashSet<>();
        boolean anyForm = false;
        for (int i : group) {
            if (!matches[i].variant.isEmpty()) variants.add(matches[i].variant);
            if (!matches[i].form.isEmpty()) anyForm = true;
        }
        String variant = variants.size() == 1 && allHaveVariant(matches, group) ? variants.iterator().next() : "";
        String variantNote = "";
        if (variants.size() > 1 || (variants.size() == 1 && variant.isEmpty())) {
            List<String> w = new ArrayList<>();
            for (String v : variants) w.add(Canon.variantWord(v, german));
            variantNote = String.join(", ", w);
        }
        String reason = variants.size() > 1 ? "kinds" : "units";

        // Juice / zest next to whole fruit: count everything in fruits.
        if (anyForm) {
            double total = 0;
            boolean ok = true;
            for (int i : group) {
                Ingredient ing = Canon.normalize(list.get(i));
                Canon.Match m = matches[i];
                double f = m.form.isEmpty() ? pieces(ing, e) : Canon.fruitsFor(ing, m);
                if (Double.isNaN(f)) { ok = false; break; }
                total += f;
            }
            if (ok) return new Merge(fruit(e, total, german, ""), "fruit");
        }

        List<Ingredient> withQty = new ArrayList<>();
        for (int i : group) {
            Ingredient ing = Canon.normalize(list.get(i));
            if (ing.hasQty()) withQty.add(ing);
        }
        String name = Canon.name(new Canon.Match(e, variant, ""), false, german);
        if (withQty.isEmpty()) return new Merge(new Ingredient(Double.NaN, Double.NaN, "", name, variantNote), reason);

        // Same kind of amount: just add up.
        Ingredient summed = sumSameFamily(withQty);
        if (summed != null) {
            return new Merge(new Ingredient(summed.qty, summed.qtyMax, summed.unit, name, variantNote), reason);
        }
        // Whole things (onions, tomatoes): convert grams to pieces.
        if (e.countable()) {
            double total = 0;
            boolean ok = true;
            for (Ingredient ing : withQty) {
                double p = pieces(ing, e);
                if (Double.isNaN(p)) { ok = false; break; }
                total += p;
            }
            if (ok) return new Merge(new Ingredient(total, Double.NaN, "", name, variantNote), "units");
        }
        // Can't add these up: one line listing the amounts.
        List<String> amounts = new ArrayList<>();
        for (Ingredient ing : withQty) amounts.add(Quantities.formatAmount(ing.qty, ing.qtyMax, ing.unit, german));
        String note = ShoppingFormat.join(String.join(" + ", amounts), variantNote);
        return new Merge(new Ingredient(Double.NaN, Double.NaN, "", name, note), "units");
    }

    private static boolean allHaveVariant(Canon.Match[] matches, List<Integer> group) {
        for (int i : group) if (matches[i].variant.isEmpty()) return false;
        return true;
    }

    /** Pieces of a countable thing: "2" -> 2, "300 g" -> 2 onions. NaN if not convertible. */
    private static double pieces(Ingredient ing, Canon.Entry e) {
        if (!ing.hasQty()) return 0;
        Units.Unit u = Units.byKey(ing.unit);
        if (u == null || u.key.equals("piece")) return ing.qty;
        if (u.family == Units.Family.MASS && e.gramsEach > 0) return ing.qty * u.toBase / e.gramsEach;
        return Double.NaN;
    }

    private static Ingredient fruit(Canon.Entry e, double count, boolean german, String fromForm) {
        String note = "";
        if (!fromForm.isEmpty()) note = german ? (fromForm.equals("juice") ? "für den Saft" : "für den Abrieb")
                : (fromForm.equals("juice") ? "for the juice" : "for the zest");
        return new Ingredient(count, Double.NaN, "", german ? e.deSg : e.enSg, note);
    }

    /** Adds amounts if they're all the same kind (all counts, all weights, all volumes). */
    private static Ingredient sumSameFamily(List<Ingredient> items) {
        String fam = ShoppingMerger.famKey(items.get(0));
        for (Ingredient i : items) if (!ShoppingMerger.famKey(i).equals(fam)) return null;
        Ingredient acc = items.get(0);
        for (int k = 1; k < items.size(); k++) {
            Ingredient b = items.get(k);
            // combine() needs the same name; the result name is set by the caller.
            acc = ShoppingMerger.combine(acc, new Ingredient(b.qty, b.qtyMax, b.unit, acc.name, acc.note));
        }
        return acc;
    }

    // ------------------------------------------------------------------ unknown items: similar names

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "of", "the", "and", "for", "fresh", "freshly", "chopped", "diced", "sliced", "minced", "grated",
            "large", "small", "medium", "ground", "dried", "optional", "taste", "frisch", "frische", "frischer",
            "gehackt", "gehackte", "gerieben", "geriebener", "gewürfelt", "klein", "kleine", "groß", "große",
            "getrocknet", "getrocknete", "und", "oder", "von", "für", "etwas"));

    private static Set<String> tokens(String name) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : name.toLowerCase(Locale.ROOT).replaceAll("\\([^)]*\\)", " ").split("[^\\p{L}]+")) {
            if (t.length() < 3 || STOP.contains(t)) continue;
            if (t.length() > 4) t = t.replaceAll("(es|en|e|s|n)$", "");
            out.add(t);
        }
        return out;
    }

    private static boolean similar(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        boolean strong = false;
        for (String t : common) if (t.length() >= 4) strong = true;
        if (!strong) return false;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return common.size() * 2 >= union.size() || a.containsAll(b) || b.containsAll(a);
    }

    private static List<List<Integer>> similarGroups(List<Ingredient> list, List<Integer> idx) {
        int n = idx.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        List<Set<String>> tok = new ArrayList<>();
        for (int i : idx) tok.add(tokens(list.get(i).name));
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                if (similar(tok.get(a), tok.get(b))) parent[find(parent, a)] = find(parent, b);
            }
        }
        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(idx.get(i));
        List<List<Integer>> out = new ArrayList<>();
        for (List<Integer> g : groups.values()) if (g.size() >= 2) out.add(g);
        return out;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) i = p[i] = p[p[i]];
        return i;
    }

    private static Ingredient mergeLoose(List<Ingredient> list, List<Integer> group) {
        String name = null;
        List<Ingredient> withQty = new ArrayList<>();
        for (int i : group) {
            Ingredient ing = list.get(i);
            if (name == null || ing.name.length() < name.length()) name = ing.name;
            if (ing.hasQty()) withQty.add(ing);
        }
        if (withQty.isEmpty()) return new Ingredient(Double.NaN, Double.NaN, "", name, "");
        Ingredient summed = sumSameFamily(withQty);
        if (summed != null) return new Ingredient(summed.qty, summed.qtyMax, summed.unit, name, "");
        List<String> amounts = new ArrayList<>();
        for (Ingredient ing : withQty) amounts.add(Quantities.formatAmount(ing.qty, ing.qtyMax, ing.unit));
        return new Ingredient(Double.NaN, Double.NaN, "", name, String.join(" + ", amounts));
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }
}
