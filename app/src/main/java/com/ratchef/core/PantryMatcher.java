package com.ratchef.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "What can I cook?" Ranks recipes by how much of them the pantry covers. Matching is loose on
 * purpose: same ingredient in any language (dictionary), close substitutes (shallot for onion,
 * crème fraîche for sour cream) for partial credit, and typo/compound-word tolerant names for
 * everything the dictionary doesn't know.
 */
public final class PantryMatcher {

    /** Assumed to be in every kitchen when "assume basics" is on. */
    public static final Set<String> BASICS = new HashSet<>(Arrays.asList(
            "salt", "pepper", "salt_pepper", "oil", "olive_oil", "sugar", "flour"));

    /** Close substitutes: same family = partial credit. */
    private static final Map<String, String> FAMILY = new HashMap<>();
    static {
        String[][] groups = {
                {"onion", "shallot", "spring_onion", "leek"},
                {"tomato", "cherry_tomato", "canned_tomatoes", "passata"},
                {"cream", "sour_cream", "creme_fraiche", "yogurt", "quark"},
                {"cheese", "parmesan", "mozzarella", "feta"},
                {"chicken", "chicken_breast", "chicken_thigh"},
                {"stock", "veg_stock", "chicken_stock", "beef_stock"},
                {"lemon", "lime"},
                {"milk", "plant_milk"},
                {"oil", "olive_oil"},
                {"ground_meat", "beef"},
                {"chickpeas", "kidney_beans", "lentils"},
                {"sugar", "brown_sugar", "powdered_sugar"},
                {"chili", "chili_flakes"},
                {"zucchini", "cucumber"},
                {"spinach", "kale", "arugula"},
        };
        for (String[] g : groups) for (String k : g) FAMILY.put(k, g[0]);
    }

    private static final double SUBSTITUTE_CREDIT = 0.6;

    public static final class Result {
        public final int index;          // position in the recipe list passed to rank()
        public final double score;       // 0..1, weighted share of the recipe you can cover
        public final int have;           // ingredients you have exactly
        public final int counted;        // ingredients that count (basics/water excluded)
        public final List<Integer> missing = new ArrayList<>();          // ingredient indices
        public final Map<Integer, String> swaps = new LinkedHashMap<>(); // ingredient index -> pantry item

        Result(int index, double score, int have, int counted) {
            this.index = index;
            this.score = score;
            this.have = have;
            this.counted = counted;
        }

        public boolean complete() {
            return missing.isEmpty() && swaps.isEmpty();
        }
    }

    private static final class Item {
        final String name;
        final Canon.Match match;
        final Set<String> tokens;

        Item(String name) {
            this.name = name;
            this.match = Canon.match(name, "");
            this.tokens = tokens(name);
        }
    }

    private PantryMatcher() {}

    /** Pantry text ("2 Zwiebeln", "feta") -> just the ingredient name. */
    public static String cleanName(String raw) {
        Ingredient i = RecipeParser.parseIngredient(raw);
        return i == null ? raw.trim() : i.name.trim();
    }

    public static List<Result> rank(List<String> pantry, List<List<Ingredient>> recipes, boolean assumeBasics) {
        List<Item> items = new ArrayList<>();
        for (String p : pantry) if (!p.trim().isEmpty()) items.add(new Item(cleanName(p)));

        List<Result> out = new ArrayList<>();
        for (int r = 0; r < recipes.size(); r++) {
            List<Ingredient> ings = recipes.get(r);
            double total = 0, got = 0;
            int have = 0, counted = 0;
            List<Integer> missing = new ArrayList<>();
            Map<Integer, String> swaps = new LinkedHashMap<>();

            for (int k = 0; k < ings.size(); k++) {
                Ingredient ing = ings.get(k);
                if (ShoppingMerger.shouldSkip(ing)) continue;
                Canon.Match m = Canon.match(ing.name, ing.unit);
                if (assumeBasics && m != null && BASICS.contains(m.entry.key)) continue;
                double w = (m != null && m.entry.aisle == Canon.Aisle.SPICES) ? 0.3 : 1.0;
                total += w;
                counted++;

                double best = 0;
                String bestItem = null;
                Set<String> ingTokens = null;
                for (Item p : items) {
                    double c;
                    if (m != null && p.match != null) {
                        String a = m.entry.key, b = p.match.entry.key;
                        if (a.equals(b)) c = 1;
                        else if (FAMILY.containsKey(a) && FAMILY.get(a).equals(FAMILY.get(b))) c = SUBSTITUTE_CREDIT;
                        else c = 0;
                    } else {
                        if (ingTokens == null) ingTokens = tokens(ing.name);
                        c = similar(ingTokens, p.tokens) ? 1 : 0;
                    }
                    if (c > best) {
                        best = c;
                        bestItem = p.name;
                    }
                    if (best >= 1) break;
                }
                got += best * w;
                if (best >= 1) have++;
                else if (best > 0) swaps.put(k, bestItem);
                else missing.add(k);
            }
            if (total <= 0 || got <= 0) continue;
            Result res = new Result(r, got / total, have, counted);
            res.missing.addAll(missing);
            res.swaps.putAll(swaps);
            out.add(res);
        }
        out.sort((a, b) -> {
            int c = Double.compare(b.score, a.score);
            return c != 0 ? c : Integer.compare(b.have, a.have);
        });
        return out;
    }

    // ------------------------------------------------------------------ fuzzy names

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "of", "the", "and", "for", "fresh", "chopped", "diced", "sliced", "minced", "grated", "large", "small",
            "medium", "dried", "frisch", "frische", "frischer", "gehackt", "gehackte", "gerieben", "geriebener",
            "klein", "kleine", "groß", "große", "getrocknet", "und", "oder", "von", "etwas"));

    static Set<String> tokens(String name) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : name.toLowerCase(Locale.ROOT).replaceAll("\\([^)]*\\)", " ").split("[^\\p{L}]+")) {
            if (t.length() < 3 || STOP.contains(t)) continue;
            if (t.length() > 4) t = t.replaceAll("(es|en|e|s|n)$", "");
            out.add(t);
        }
        return out;
    }

    static boolean similar(Set<String> a, Set<String> b) {
        for (String x : a) {
            for (String y : b) {
                if (x.equals(y)) return true;
                String shorter = x.length() <= y.length() ? x : y;
                String longer = shorter == x ? y : x;
                // German compounds: "Fetakäse" ~ "Feta", "Hühnerbrühe" ~ "Brühe"
                if (shorter.length() >= 4 && (longer.startsWith(shorter) || longer.endsWith(shorter))) return true;
                // Typos: "Mozarella" ~ "Mozzarella"
                if (shorter.length() >= 5 && levenshtein(x, y) <= (shorter.length() >= 8 ? 2 : 1)) return true;
            }
        }
        return false;
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
