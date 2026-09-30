package com.ratchef.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RecipeParserTest {

    private static final String EN =
            "🍝 Creamy Garlic Pasta 🍝\n\nServes 2\n\nIngredients:\n"
                    + "🔸 200g spaghetti\n🔸 2 tbsp butter\n🔸 4 cloves garlic, minced\n🔸 1 cup heavy cream\n"
                    + "🔸 1/2 cup grated parmesan\n🔸 Salt & pepper to taste\n\nInstructions:\n"
                    + "1️⃣ Cook the spaghetti until al dente.\n2️⃣ Melt butter, add garlic.\n"
                    + "3️⃣ Add cream and parmesan.\n4️⃣ Toss and serve.\n\nSave this for later! 📌\n#pasta #dinner";

    private static final String DE =
            "Linsen-Curry 🌱\nZutaten (für 4 Portionen):\n- Rote Linsen: 250 g\n- 1 Dose Kokosmilch (400 ml)\n"
                    + "- 2 EL Currypaste\n- 1,5 TL Kurkuma\n- Salz nach Geschmack\nZubereitung:\n"
                    + "Zwiebel fein hacken und anschwitzen. Currypaste kurz mitrösten. Linsen und Kokosmilch dazugeben "
                    + "und 15 Minuten köcheln lassen. Mit Salz abschmecken und mit Reis servieren.\nKcal: 420 | Protein: 18g";

    private static final String NO_HEADERS =
            "The easiest protein oats\n80g oats\n1 scoop protein powder\n250ml milk\n½ banana, sliced\n"
                    + "Mix oats, protein powder and milk in a pot and cook on medium heat for about 5 minutes.\n"
                    + "Macros: 520 kcal, 45g protein";

    @Test
    public void englishWithHeaders() {
        Recipe r = RecipeParser.parse(EN);
        assertEquals("Creamy Garlic Pasta", r.title);
        assertEquals(2, r.servings);
        assertEquals(6, r.ingredients.size());
        assertEquals(4, r.steps.size());
        Ingredient garlic = r.ingredients.get(2);
        assertEquals(4.0, garlic.qty, 1e-9);
        assertEquals("clove", garlic.unit);
        assertEquals("Garlic", garlic.name);
        assertEquals("minced", garlic.note);
        assertEquals("½ cup Grated parmesan", r.ingredients.get(4).display());
        assertTrue(!r.ingredients.get(5).hasQty());
    }

    @Test
    public void germanTrailingAmountsAndProseSteps() {
        Recipe r = RecipeParser.parse(DE);
        assertEquals(4, r.servings);
        assertEquals("250 g Rote Linsen", r.ingredients.get(0).display());
        assertEquals("can", r.ingredients.get(1).unit);
        assertEquals(1.5, r.ingredients.get(3).qty, 1e-9);
        assertEquals("tsp", r.ingredients.get(3).unit);
        assertEquals(4, r.steps.size()); // paragraph split into sentences, macros line dropped
    }

    @Test
    public void noHeaders() {
        Recipe r = RecipeParser.parse(NO_HEADERS);
        assertEquals("The easiest protein oats", r.title);
        assertEquals(4, r.ingredients.size());
        assertEquals(1, r.steps.size());
    }

    @Test
    public void scalingAndFormatting() {
        Ingredient i = RecipeParser.parseIngredient("1 1/2 tbsp olive oil");
        assertEquals("4 ½ tbsp Olive oil", i.scaled(3).display());
        assertEquals("1–2 tsp Chili flakes", RecipeParser.parseIngredient("1-2 tsp chili flakes").display());
        assertEquals("1 pinch Salt", RecipeParser.parseIngredient("Pinch of salt").display());
    }

    @Test
    public void shoppingMerge() {
        List<Ingredient> l = new ArrayList<>();
        l.add(RecipeParser.parseIngredient("1 cup heavy cream"));
        l.add(RecipeParser.parseIngredient("100 ml heavy cream"));
        l.add(RecipeParser.parseIngredient("200 g flour"));
        l.add(RecipeParser.parseIngredient("800g flour"));
        l.add(RecipeParser.parseIngredient("2 tomatoes"));
        l.add(RecipeParser.parseIngredient("1 tomato"));
        l.add(RecipeParser.parseIngredient("water"));
        List<Ingredient> m = ShoppingMerger.merge(l);
        assertEquals(3, m.size());
        assertEquals("340 ml Heavy cream", m.get(0).display());
        assertEquals("1 kg Flour", m.get(1).display());
        assertEquals("3 Tomatoes", m.get(2).display());
    }
}
