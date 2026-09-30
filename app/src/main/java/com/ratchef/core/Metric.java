package com.ratchef.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts US units to metric, offline. Applied at display time, so the saved recipe keeps its
 * original units and the setting can be switched back.
 *
 * cup  -> g for common dry ingredients (flour, sugar, rice, …), otherwise ml
 * oz   -> g (ml for liquids), lb -> g, stick of butter -> g
 * tsp / tbsp stay (TL / EL are normal in European recipes too)
 * Steps: 350°F -> 175 °C, 9x13 inch -> 23x33 cm
 */
public final class Metric {

    private Metric() {}

    /** grams per US cup; first match wins, so specific names come first. */
    private static final Object[][] CUP_GRAMS = {
            {new String[]{"powdered sugar", "icing sugar", "puderzucker", "confectioner"}, 120.0},
            {new String[]{"brown sugar", "brauner zucker", "rohrzucker"}, 220.0},
            {new String[]{"sugar", "zucker"}, 200.0},
            {new String[]{"almond flour", "mandelmehl", "ground almond"}, 100.0},
            {new String[]{"flour", "mehl"}, 125.0},
            {new String[]{"cornstarch", "corn starch", "maisstärke", "speisestärke"}, 120.0},
            {new String[]{"cocoa", "kakao"}, 85.0},
            {new String[]{"peanut butter", "erdnussbutter", "erdnussmus"}, 250.0},
            {new String[]{"butter"}, 227.0},
            {new String[]{"oat", "hafer"}, 90.0},
            {new String[]{"quinoa"}, 170.0},
            {new String[]{"rice", "reis"}, 185.0},
            {new String[]{"lentil", "linsen"}, 190.0},
            {new String[]{"breadcrumb", "bread crumb", "panko", "semmelbrösel", "paniermehl"}, 110.0},
            {new String[]{"cream cheese", "frischkäse"}, 230.0},
            {new String[]{"parmesan", "grated cheese", "geriebener käse"}, 100.0},
            {new String[]{"cheese", "käse", "cheddar", "mozzarella"}, 113.0},
            {new String[]{"chocolate chip", "schokotropfen", "chocolate", "schokolade"}, 170.0},
            {new String[]{"almond", "walnut", "pecan", "cashew", "hazelnut", "nuts", "mandeln", "nüsse", "walnüsse"}, 140.0},
            {new String[]{"honey", "honig", "maple syrup", "ahornsirup"}, 340.0},
            {new String[]{"berries", "beeren", "blueberr", "raspberr", "heidelbeer", "himbeer"}, 150.0},
            {new String[]{"spinach", "spinat"}, 30.0},
            {new String[]{"coconut flakes", "shredded coconut", "kokosraspel"}, 80.0},
    };

    private static final Pattern LIQUID = Pattern.compile(
            "\\b(milk|milch|water|wasser|cream|sahne|obers|broth|stock|brühe|fond|juice|saft|oil|öl|wine|wein|"
                    + "vinegar|essig|sauce|soße|sosse|beer|bier|coffee|kaffee|buttermilk|buttermilch)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static Ingredient convert(Ingredient i) {
        if (!i.hasQty()) return i;
        String name = (i.name + " " + i.note).toLowerCase(Locale.ROOT);
        switch (i.unit) {
            case "cup": {
                Double g = cupGrams(name);
                return g != null ? with(i, g, "g") : with(i, 240.0, "ml");
            }
            case "oz":
                return isLiquid(name) ? with(i, 29.57, "ml") : with(i, 28.35, "g");
            case "lb":
                return with(i, 453.6, "g");
            case "stick":
                return name.contains("butter") ? with(i, 113.0, "g") : i;
            default:
                return i;
        }
    }

    private static Double cupGrams(String name) {
        if (isLiquid(name)) return null;
        for (Object[] row : CUP_GRAMS) {
            for (String w : (String[]) row[0]) if (name.contains(w)) return (Double) row[1];
        }
        return null;
    }

    /** "heavy cream", "olive oil", "buttermilk" are liquids; "cream cheese" is not. */
    private static boolean isLiquid(String name) {
        boolean cheese = name.contains("cheese") || name.contains("käse");
        return LIQUID.matcher(name).find() && !cheese;
    }

    private static Ingredient with(Ingredient i, double factor, String unit) {
        double max = Double.isNaN(i.qtyMax) ? Double.NaN : i.qtyMax * factor;
        return new Ingredient(i.qty * factor, max, unit, i.name, i.note);
    }

    // ------------------------------------------------------------------ step text

    private static final Pattern FAHRENHEIT = Pattern.compile(
            "(\\d{2,3})\\s*(?:°\\s*F\\b|º\\s*F\\b|degrees?\\s*F(?:ahrenheit)?\\b|F\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAN_INCH = Pattern.compile(
            "(\\d{1,2})\\s*[x×]\\s*(\\d{1,2})(?:\\s*-?\\s*(?:inch(?:es)?|in\\.|\"|″))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INCH = Pattern.compile(
            "(\\d{1,2}(?:\\.\\d)?)\\s*-?\\s*(?:inch(?:es)?\\b|\"|″)",
            Pattern.CASE_INSENSITIVE);

    public static String convertText(String s) {
        Matcher m = FAHRENHEIT.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            double f = Double.parseDouble(m.group(1));
            if (f < 200 || f > 550) {           // not an oven temperature, leave it alone
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }
            long c = Math.round((f - 32) * 5 / 9 / 5) * 5;
            m.appendReplacement(sb, c + " °C");
        }
        m.appendTail(sb);
        s = sb.toString();

        m = PAN_INCH.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            long a = Math.round(Double.parseDouble(m.group(1)) * 2.54);
            long b = Math.round(Double.parseDouble(m.group(2)) * 2.54);
            m.appendReplacement(sb, a + "×" + b + " cm");
        }
        m.appendTail(sb);
        s = sb.toString();

        m = INCH.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            double cm = Double.parseDouble(m.group(1)) * 2.54;
            String v = cm < 5 ? Quantities.trim(Math.round(cm * 2) / 2.0) : Long.toString(Math.round(cm));
            m.appendReplacement(sb, v + " cm");
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
