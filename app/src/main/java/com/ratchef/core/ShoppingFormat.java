package com.ratchef.core;

import java.util.Locale;

/** How one shopping-list item reads: one language, whole onions rounded up, German unit labels. */
public final class ShoppingFormat {

    private ShoppingFormat() {}

    public static String line(Ingredient raw, boolean german) {
        Ingredient i = ShoppingMerger.tidy(raw);
        Canon.Match m = Canon.match(i.name, i.unit);

        double qty = i.qty, max = i.qtyMax;
        String need = "";
        // You can't buy ½ an onion: show what to buy, and what the recipes need.
        if (m != null && m.entry.countable() && m.form.isEmpty() && i.unit.isEmpty() && i.hasQty()) {
            double top = Double.isNaN(max) ? qty : max;
            double buy = Math.ceil(top - 1e-9);
            if (Math.abs(buy - top) > 1e-9 || !Double.isNaN(max)) {
                need = Quantities.formatAmount(qty, max, "") + (german ? " benötigt" : " needed");
                qty = buy;
                max = Double.NaN;
            }
        }

        String name;
        if (m != null) {
            Units.Unit u = Units.byKey(i.unit);
            boolean countLike = u == null || u.family == Units.Family.COUNT;
            boolean plural = i.hasQty() && (countLike ? qty > 1.0001 : m.entry.countable());
            if (u != null && u.family == Units.Family.COUNT && !u.key.equals("piece")) plural = false; // "2 Zehen Knoblauch"
            name = Canon.name(m, plural, german);
        } else {
            name = i.name;
        }

        StringBuilder sb = new StringBuilder();
        String amount = Quantities.formatAmount(qty, max, i.unit, german);
        if (!amount.isEmpty()) sb.append(amount).append(' ');
        sb.append(name);
        String note = join(need, i.note);
        if (!note.isEmpty()) sb.append(" (").append(note).append(')');
        return sb.toString();
    }

    public static Canon.Aisle aisle(Ingredient i) {
        Canon.Match m = Canon.match(i.name, i.unit);
        return m == null ? Canon.Aisle.OTHER : m.entry.aisle;
    }

    /** Sort key within an aisle: the displayed name. */
    public static String sortName(Ingredient i, boolean german) {
        Canon.Match m = Canon.match(i.name, i.unit);
        return (m == null ? i.name : Canon.name(m, false, german)).toLowerCase(Locale.ROOT);
    }

    static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b == null ? "" : b;
        if (b == null || b.isEmpty()) return a;
        return a + "; " + b;
    }
}
