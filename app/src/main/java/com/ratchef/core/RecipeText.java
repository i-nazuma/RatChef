package com.ratchef.core;

import java.util.List;

/**
 * A recipe as plain text for sharing: readable in any messenger, and laid out so RatChef's own parser
 * reads it back exactly (title, servings, "Ingredients:" list, numbered steps).
 */
public final class RecipeText {

    private RecipeText() {}

    public static String format(String title, int servings, List<Ingredient> ingredients, List<String> steps,
                                String sourceUrl, boolean german) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append('\n');
        if (servings > 0) sb.append(german ? "Für " + servings + " Portionen" : "Serves " + servings).append('\n');
        sb.append('\n').append(german ? "Zutaten:" : "Ingredients:").append('\n');
        for (Ingredient i : ingredients) {
            sb.append("• ");
            String amount = Quantities.formatAmount(i.qty, i.qtyMax, i.unit, german);
            if (!amount.isEmpty()) sb.append(amount).append(' ');
            sb.append(i.name);
            if (!i.note.isEmpty()) sb.append(" (").append(i.note).append(')');
            sb.append('\n');
        }
        if (!steps.isEmpty()) {
            sb.append('\n').append(german ? "Zubereitung:" : "Steps:").append('\n');
            for (int k = 0; k < steps.size(); k++) sb.append(k + 1).append(". ").append(steps.get(k)).append('\n');
        }
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            sb.append('\n').append(german ? "Quelle: " : "Source: ").append(sourceUrl).append('\n');
        }
        sb.append(german ? "— Geteilt mit RatChef" : "— Shared with RatChef");
        return sb.toString();
    }
}
