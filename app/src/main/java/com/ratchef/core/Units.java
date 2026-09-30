package com.ratchef.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Canonical cooking units, their aliases (EN + DE) and conversion to a base unit. */
public final class Units {

    public enum Family { MASS, VOLUME, COUNT }

    public static final class Unit {
        public final String key;      // canonical key, e.g. "g", "tbsp"
        public final String display;   // short label shown in the UI
        public final Family family;
        public final double toBase;    // factor to g (MASS) or ml (VOLUME); 1 for COUNT
        public final boolean fractional; // show as ½, ¼ instead of decimals
        public final String plural;

        Unit(String key, String display, Family family, double toBase, boolean fractional) {
            this.key = key;
            this.display = display;
            this.plural = PLURALS.containsKey(key) ? PLURALS.get(key) : display;
            this.family = family;
            this.toBase = toBase;
            this.fractional = fractional;
        }
    }

    private static final Map<String, String> PLURALS = new HashMap<>();
    static {
        String[] p = {"cup", "clove", "can", "pack", "bunch", "handful", "slice", "sprig", "dash",
                "stick", "cube", "jar", "scoop"};
        for (String k : p) PLURALS.put(k, k.endsWith("ch") || k.endsWith("sh") ? k + "es" : k + "s");
        PLURALS.put("pinch", "pinches");
        PLURALS.put("piece", "pcs");
    }

    private static final Map<String, Unit> BY_KEY = new HashMap<>();
    private static final Map<String, Unit> ALIASES = new HashMap<>();

    private static void add(String key, String display, Family f, double toBase, boolean frac, String... aliases) {
        Unit u = new Unit(key, display, f, toBase, frac);
        BY_KEY.put(key, u);
        ALIASES.put(key, u);
        for (String a : aliases) ALIASES.put(a, u);
    }

    static {
        add("g", "g", Family.MASS, 1, false, "gr", "gram", "grams", "gramm", "gramms", "grammes", "gramme");
        add("kg", "kg", Family.MASS, 1000, false, "kilo", "kilos", "kilogram", "kilograms", "kilogramm");
        add("oz", "oz", Family.MASS, 28.35, true, "ounce", "ounces");
        add("lb", "lb", Family.MASS, 453.6, true, "lbs", "pound", "pounds");

        add("ml", "ml", Family.VOLUME, 1, false, "milliliter", "milliliters", "millilitre", "millilitres");
        add("cl", "cl", Family.VOLUME, 10, false, "centiliter", "zentiliter");
        add("dl", "dl", Family.VOLUME, 100, false, "deciliter", "deziliter");
        add("l", "l", Family.VOLUME, 1000, false, "liter", "liters", "litre", "litres", "ltr");
        add("tsp", "tsp", Family.VOLUME, 5, true, "teaspoon", "teaspoons", "tl", "teelöffel", "teeloeffel", "tsps");
        add("tbsp", "tbsp", Family.VOLUME, 15, true, "tablespoon", "tablespoons", "tbs", "tbl", "tbsps",
                "el", "esslöffel", "essloeffel");
        add("cup", "cup", Family.VOLUME, 240, true, "cups", "tasse", "tassen");

        // Count-like units. Merged only with the same unit.
        add("pinch", "pinch", Family.COUNT, 1, true, "pinches", "prise", "prisen", "msp");
        add("clove", "clove", Family.COUNT, 1, true, "cloves", "zehe", "zehen");
        add("can", "can", Family.COUNT, 1, true, "cans", "tin", "tins", "dose", "dosen");
        add("pack", "pack", Family.COUNT, 1, true, "packs", "package", "packages", "packet", "packets", "pkg",
                "pck", "pkt", "packung", "packungen", "päckchen", "pckg");
        add("bunch", "bunch", Family.COUNT, 1, true, "bunches", "bund", "bünde");
        add("handful", "handful", Family.COUNT, 1, true, "handfuls", "handvoll");
        add("slice", "slice", Family.COUNT, 1, true, "slices", "scheibe", "scheiben");
        add("piece", "pc", Family.COUNT, 1, true, "pc", "pcs", "piece", "pieces", "stk", "stück", "stueck");
        add("sprig", "sprig", Family.COUNT, 1, true, "sprigs", "zweig", "zweige");
        add("dash", "dash", Family.COUNT, 1, true, "dashes", "schuss", "spritzer", "splash", "squeeze", "squeezes");
        add("stick", "stick", Family.COUNT, 1, true, "sticks", "stange", "stangen");
        add("cube", "cube", Family.COUNT, 1, true, "cubes", "würfel");
        add("scoop", "scoop", Family.COUNT, 1, true, "scoops", "messlöffel", "messbecher");
        add("jar", "jar", Family.COUNT, 1, true, "jars", "glas", "gläser");
    }

    private Units() {}

    /** Resolve a token like "EL", "tbsp.", "Gramm" to a unit, or null. */
    public static Unit lookup(String token) {
        if (token == null) return null;
        String t = token.toLowerCase(Locale.ROOT).trim();
        while (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return ALIASES.get(t);
    }

    /** Label for a given amount: "1 cup", "2 cups". */
    public static String label(Unit u, double qty) {
        return qty > 1.0001 ? u.plural : u.display;
    }

    /** German labels (singular, plural) where they differ from the English short form. */
    private static final Map<String, String[]> DE = new HashMap<>();
    static {
        String[][] d = {
                {"tsp", "TL", "TL"}, {"tbsp", "EL", "EL"}, {"cup", "Tasse", "Tassen"},
                {"pinch", "Prise", "Prisen"}, {"clove", "Zehe", "Zehen"}, {"can", "Dose", "Dosen"},
                {"pack", "Packung", "Packungen"}, {"bunch", "Bund", "Bund"}, {"handful", "Handvoll", "Handvoll"},
                {"slice", "Scheibe", "Scheiben"}, {"piece", "Stk.", "Stk."}, {"sprig", "Zweig", "Zweige"},
                {"dash", "Spritzer", "Spritzer"}, {"stick", "Stange", "Stangen"}, {"cube", "Würfel", "Würfel"},
                {"jar", "Glas", "Gläser"}, {"scoop", "Messlöffel", "Messlöffel"},
        };
        for (String[] r : d) DE.put(r[0], new String[]{r[1], r[2]});
    }

    public static String label(Unit u, double qty, boolean german) {
        if (german && DE.containsKey(u.key)) {
            String[] l = DE.get(u.key);
            return qty > 1.0001 ? l[1] : l[0];
        }
        return label(u, qty);
    }

    public static Unit byKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }
}
