package com.ratchef.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/** Combines ingredients for the shopping list: 2 tbsp + 100 ml olive oil -> 130 ml olive oil. */
public final class ShoppingMerger {

    /** Things nobody needs to buy. */
    private static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "water", "wasser", "warm water", "cold water", "hot water", "ice", "eis", "ice cubes", "eiswürfel"));

    private static final String[] ADJECTIVES = {
            "fresh ", "freshly ", "frische ", "frischer ", "frisches ", "frisch ", "large ", "small ", "medium ",
            "big ", "große ", "großer ", "kleine ", "kleiner ", "mittelgroße ", "ripe ", "reife "};

    private ShoppingMerger() {}

    public static boolean shouldSkip(Ingredient i) {
        return SKIP.contains(i.name.toLowerCase(Locale.ROOT).trim());
    }

    /** Items with the same key can be added together. */
    public static String key(Ingredient i) {
        String fam;
        Units.Unit u = Units.byKey(i.unit);
        if (!i.hasQty()) fam = "?";
        else if (u == null) fam = "count";
        else if (u.family == Units.Family.COUNT) fam = u.key;
        else fam = u.family.name();
        return normalizeName(i.name) + "|" + fam;
    }

    public static String normalizeName(String name) {
        String n = name.toLowerCase(Locale.ROOT).trim();
        for (String a : ADJECTIVES) if (n.startsWith(a)) n = n.substring(a.length());
        n = n.replaceAll("[^\\p{L}\\p{N} ]", "").replaceAll("\\s+", " ").trim();
        // light English singularisation on the last word
        if (n.endsWith("oes")) n = n.substring(0, n.length() - 2);          // tomatoes -> tomato
        else if (n.endsWith("ies") && n.length() > 4) n = n.substring(0, n.length() - 3) + "y"; // berries
        else if (n.endsWith("s") && !n.endsWith("ss") && n.length() > 3) n = n.substring(0, n.length() - 1);
        return n;
    }

    /** Add b into a. Both must have the same key. */
    public static Ingredient combine(Ingredient a, Ingredient b) {
        if (!a.hasQty() || !b.hasQty()) return a.hasQty() ? a : b;
        String note = a.note.equals(b.note) ? a.note : "";
        boolean range = !Double.isNaN(a.qtyMax) || !Double.isNaN(b.qtyMax);
        double aMax = Double.isNaN(a.qtyMax) ? a.qty : a.qtyMax;
        double bMax = Double.isNaN(b.qtyMax) ? b.qty : b.qtyMax;

        if (a.unit.equals(b.unit)) {
            return new Ingredient(a.qty + b.qty, range ? aMax + bMax : Double.NaN, a.unit, a.name, note);
        }
        Units.Unit ua = Units.byKey(a.unit), ub = Units.byKey(b.unit);
        if (ua == null || ub == null || ua.family != ub.family || ua.family == Units.Family.COUNT) {
            return a; // shouldn't happen when keys match
        }
        double base = a.qty * ua.toBase + b.qty * ub.toBase;
        double baseMax = range ? aMax * ua.toBase + bMax * ub.toBase : Double.NaN;
        String unit = ua.family == Units.Family.MASS ? "g" : "ml";
        return new Ingredient(base, baseMax, unit, a.name, note);
    }

    /** Scale g >= 1000 to kg and ml >= 1000 to l for display. */
    public static Ingredient tidy(Ingredient i) {
        if (!i.hasQty()) return i;
        if (i.unit.equals("g") && i.qty >= 1000)
            return new Ingredient(i.qty / 1000, Double.isNaN(i.qtyMax) ? Double.NaN : i.qtyMax / 1000, "kg", i.name, i.note);
        if (i.unit.equals("ml") && i.qty >= 1000)
            return new Ingredient(i.qty / 1000, Double.isNaN(i.qtyMax) ? Double.NaN : i.qtyMax / 1000, "l", i.name, i.note);
        return i;
    }

    /** Merge a flat list; "salt (to taste)" is dropped when "1 tsp salt" is also present. */
    public static List<Ingredient> merge(List<Ingredient> items) {
        Map<String, Ingredient> byKey = new LinkedHashMap<>();
        for (Ingredient i : items) {
            if (shouldSkip(i)) continue;
            String k = key(i);
            Ingredient prev = byKey.get(k);
            byKey.put(k, prev == null ? i : combine(prev, i));
        }
        Set<String> namesWithQty = new HashSet<>();
        for (Ingredient i : byKey.values()) if (i.hasQty()) namesWithQty.add(normalizeName(i.name));
        List<Ingredient> out = new ArrayList<>();
        for (Ingredient i : byKey.values()) {
            if (!i.hasQty() && namesWithQty.contains(normalizeName(i.name))) continue;
            out.add(tidy(i));
        }
        return out;
    }
}
