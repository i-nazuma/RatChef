package com.ratchef.core;

/** One ingredient line in normalized form. qty is NaN when the line has no amount ("salt to taste"). */
public final class Ingredient {
    public final double qty;
    public final double qtyMax; // NaN unless the line was a range ("1-2 cloves")
    public final String unit;   // canonical Units key, or "" for plain counts / no unit
    public final String name;
    public final String note;   // "finely chopped", "or butter", ... ("" if none)

    public Ingredient(double qty, double qtyMax, String unit, String name, String note) {
        this.qty = qty;
        this.qtyMax = qtyMax;
        this.unit = unit == null ? "" : unit;
        this.name = name == null ? "" : name;
        this.note = note == null ? "" : note;
    }

    public boolean hasQty() {
        return !Double.isNaN(qty);
    }

    public Ingredient scaled(double factor) {
        if (!hasQty() || factor == 1.0) return this;
        double max = Double.isNaN(qtyMax) ? Double.NaN : qtyMax * factor;
        return new Ingredient(qty * factor, max, unit, name, note);
    }

    /** "200 g flour (sifted)" */
    public String display() {
        StringBuilder sb = new StringBuilder();
        String amount = Quantities.formatAmount(qty, qtyMax, unit);
        if (!amount.isEmpty()) sb.append(amount).append(' ');
        sb.append(name);
        if (!note.isEmpty()) sb.append(" (").append(note).append(')');
        return sb.toString();
    }

    @Override
    public String toString() {
        return display();
    }
}
