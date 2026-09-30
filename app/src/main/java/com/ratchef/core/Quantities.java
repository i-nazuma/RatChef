package com.ratchef.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and pretty-printing of amounts: "1 1/2", "1,5", "½", "2-3", 0.333 -> "⅓". */
public final class Quantities {

    static final String FRACS = "½⅓⅔¼¾⅛⅜⅝⅞⅕⅙";
    private static final Map<Character, Double> UNICODE = new HashMap<>();
    static {
        UNICODE.put('½', 0.5);
        UNICODE.put('⅓', 1.0 / 3);
        UNICODE.put('⅔', 2.0 / 3);
        UNICODE.put('¼', 0.25);
        UNICODE.put('¾', 0.75);
        UNICODE.put('⅛', 0.125);
        UNICODE.put('⅜', 0.375);
        UNICODE.put('⅝', 0.625);
        UNICODE.put('⅞', 0.875);
        UNICODE.put('⅕', 0.2);
        UNICODE.put('⅙', 1.0 / 6);
    }

    /** One number: mixed fraction, fraction, decimal (dot or comma), unicode fraction. */
    public static final String NUM =
            "(?:\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?\\s?[" + FRACS + "]?|[" + FRACS + "])";

    /** Number or range at the start of a string. Group 1 = first number, group 2 = optional second. */
    public static final Pattern LEADING = Pattern.compile(
            "^(" + NUM + ")(?:\\s*(?:-|–|—|to|bis|or|oder)\\s*(" + NUM + "))?");

    private static final Map<String, Double> WORDS = new HashMap<>();
    static {
        String[][] w = {
                {"a", "1"}, {"an", "1"}, {"one", "1"}, {"two", "2"}, {"three", "3"}, {"four", "4"},
                {"five", "5"}, {"six", "6"}, {"half", "0.5"}, {"half a", "0.5"},
                {"ein", "1"}, {"eine", "1"}, {"einen", "1"}, {"zwei", "2"}, {"drei", "3"}, {"vier", "4"},
                {"fünf", "5"}, {"sechs", "6"}, {"halbe", "0.5"}, {"halber", "0.5"}, {"halb", "0.5"},
                {"eine halbe", "0.5"}, {"ein halber", "0.5"}, {"ein halbes", "0.5"},
                {"un", "1"}, {"una", "1"}, {"uno", "1"}, {"dos", "2"}, {"tres", "3"}, {"medio", "0.5"}, {"media", "0.5"}
        };
        for (String[] p : w) WORDS.put(p[0], Double.parseDouble(p[1]));
    }

    private static final Pattern LEADING_WORD = Pattern.compile(
            "^(half a|eine halbe|ein halber|ein halbes|a|an|one|two|three|four|five|six|half|"
                    + "ein|eine|einen|zwei|drei|vier|fünf|sechs|halbe|halber|halb|un|una|uno|dos|tres|medio|media)\\s+",
            Pattern.CASE_INSENSITIVE);

    private Quantities() {}

    /** Parse a single number token as produced by {@link #NUM}. Returns NaN if it can't. */
    public static double parse(String s) {
        if (s == null) return Double.NaN;
        s = s.trim();
        if (s.isEmpty()) return Double.NaN;
        try {
            // mixed "1 1/2"
            Matcher m = Pattern.compile("^(\\d+)\\s+(\\d+)/(\\d+)$").matcher(s);
            if (m.matches()) {
                double d = Double.parseDouble(m.group(3));
                return d == 0 ? Double.NaN : Double.parseDouble(m.group(1)) + Double.parseDouble(m.group(2)) / d;
            }
            m = Pattern.compile("^(\\d+)/(\\d+)$").matcher(s);
            if (m.matches()) {
                double d = Double.parseDouble(m.group(2));
                return d == 0 ? Double.NaN : Double.parseDouble(m.group(1)) / d;
            }
            char last = s.charAt(s.length() - 1);
            if (UNICODE.containsKey(last)) {
                String head = s.substring(0, s.length() - 1).trim();
                double whole = head.isEmpty() ? 0 : Double.parseDouble(head.replace(',', '.'));
                return whole + UNICODE.get(last);
            }
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Result of reading an amount off the front of a line. */
    public static final class Leading {
        public final double qty, qtyMax;
        public final String rest;
        Leading(double qty, double qtyMax, String rest) {
            this.qty = qty;
            this.qtyMax = qtyMax;
            this.rest = rest;
        }
    }

    /** Read "2", "1-2", "½", "a", "zwei" from the start of s. Returns null if there is no amount. */
    public static Leading readLeading(String s) {
        Matcher m = LEADING.matcher(s);
        if (m.find()) {
            double a = parse(m.group(1));
            if (!Double.isNaN(a)) {
                double b = m.group(2) != null ? parse(m.group(2)) : Double.NaN;
                return new Leading(a, b, s.substring(m.end()));
            }
        }
        Matcher w = LEADING_WORD.matcher(s);
        if (w.find()) {
            Double v = WORDS.get(w.group(1).toLowerCase(Locale.ROOT));
            if (v != null) return new Leading(v, Double.NaN, s.substring(w.end()));
        }
        return null;
    }

    // ---------------------------------------------------------------- formatting

    private static final double[] FRAC_VALUES = {0, 0.125, 0.25, 1.0 / 3, 0.5, 2.0 / 3, 0.75, 0.875, 1};
    private static final String[] FRAC_GLYPHS = {"", "⅛", "¼", "⅓", "½", "⅔", "¾", "⅞", ""};

    public static String formatAmount(double qty, double qtyMax, String unitKey) {
        return formatAmount(qty, qtyMax, unitKey, false);
    }

    public static String formatAmount(double qty, double qtyMax, String unitKey, boolean german) {
        if (Double.isNaN(qty)) return "";
        Units.Unit u = Units.byKey(unitKey);
        String n = formatNumber(qty, u);
        if (!Double.isNaN(qtyMax)) n = n + "–" + formatNumber(qtyMax, u);
        if (u == null) return n;
        // no space for metric units like "200 g" reads fine either way; keep the space for clarity
        double top = Double.isNaN(qtyMax) ? qty : qtyMax;
        return n + " " + Units.label(u, top, german);
    }

    public static String formatNumber(double v, Units.Unit u) {
        boolean fractional = u == null || u.fractional;
        if (fractional) return formatFraction(v);
        String k = u.key;
        if (k.equals("g") || k.equals("ml")) {
            double r;
            if (v < 10) r = Math.round(v * 2) / 2.0;
            else if (v < 100) r = Math.round(v);
            else if (v < 1000) r = Math.round(v / 5) * 5;
            else r = Math.round(v / 10) * 10;
            if (r == 0 && v > 0) r = Math.round(v * 10) / 10.0;
            return trim(r);
        }
        return trim(Math.round(v * 100) / 100.0);
    }

    static String formatFraction(double v) {
        if (v >= 10) return trim(Math.round(v * 2) / 2.0);
        int whole = (int) Math.floor(v);
        double frac = v - whole;
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < FRAC_VALUES.length; i++) {
            double d = Math.abs(frac - FRAC_VALUES[i]);
            if (d < bestDiff) {
                bestDiff = d;
                best = i;
            }
        }
        if (bestDiff > 0.07) return trim(Math.round(v * 10) / 10.0);
        if (best == FRAC_VALUES.length - 1) {
            whole += 1;
            best = 0;
        }
        String g = FRAC_GLYPHS[best];
        if (whole == 0 && g.isEmpty()) return "0";
        if (whole == 0) return g;
        if (g.isEmpty()) return Integer.toString(whole);
        return whole + " " + g;
    }

    static String trim(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
