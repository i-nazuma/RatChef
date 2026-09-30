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
    public void metricConversion() {
        assertEquals("250 g Flour", Metric.convert(RecipeParser.parseIngredient("2 cups flour")).display());
        assertEquals("360 ml Heavy cream", Metric.convert(RecipeParser.parseIngredient("1 1/2 cups heavy cream")).display());
        assertEquals("75 g Grated parmesan", Metric.convert(RecipeParser.parseIngredient("3/4 cup grated parmesan")).display());
        assertEquals("455 g Ground beef", Metric.convert(RecipeParser.parseIngredient("1 lb ground beef")).display());
        assertEquals("225 g Cream cheese", Metric.convert(RecipeParser.parseIngredient("8 oz cream cheese")).display());
        assertEquals("115 g Butter", Metric.convert(RecipeParser.parseIngredient("1 stick butter")).display());
        assertEquals("2 tbsp Olive oil", Metric.convert(RecipeParser.parseIngredient("2 tbsp olive oil")).display());
        assertEquals("Bake at 175 °C for 25 min.", Metric.convertText("Bake at 350°F for 25 min."));
        assertEquals("Grease a 23×33 cm pan.", Metric.convertText("Grease a 9x13 inch pan."));
        assertEquals("Cook for 10 minutes.", Metric.convertText("Cook for 10 minutes."));
    }

    private static String key(String name, String unit) {
        Canon.Match m = Canon.match(name, unit);
        return m == null ? "-" : m.key();
    }

    @Test
    public void dictionaryMatchesAcrossLanguages() {
        assertEquals("onion|yellow|", key("Yellow onion", ""));
        assertEquals("onion|red|", key("Rote Zwiebel", ""));
        assertEquals("onion||", key("Zwiebeln", ""));
        assertEquals("garlic||", key("Knoblauchzehen", ""));
        assertEquals("cream_cheese||", key("Frischkäse", ""));
        assertEquals("chickpeas||", key("Kichererbsen", ""));
        assertEquals("eggs||", key("Eier", ""));
        assertEquals("rice||", key("Basmati Reis", ""));
        assertEquals("wine|white|", key("Weißwein", ""));
        assertEquals("lemon||juice", key("Squeezes of lemon juice", "dash"));
        assertEquals("lemon||juice", key("Zitronensaft", ""));
        assertEquals("chicken_stock||", key("Chicken stock", ""));
        assertEquals("pineapple||", key("Pineapple", ""));
        assertEquals("paprika_powder||", key("Paprika", "tsp"));
        assertEquals("bell_pepper||", key("Paprika", ""));
        assertEquals("salt_pepper||", key("Salz und Pfeffer", ""));
        assertEquals("feta||", key("Feta cheese", ""));
        assertEquals("pumpkin||", key("Butternut squash", ""));
        assertEquals("-", key("Gochujang", ""));
        assertEquals("POTATO", Canon.veggieInText("Kartoffelsalat"));
        assertEquals("TOMATO", Canon.veggieInText("Tomato basil pasta"));
    }

    @Test
    public void shoppingLanguageAndAutoMerge() {
        Ingredient en = RecipeParser.parseIngredient("1 onion");
        Ingredient de = RecipeParser.parseIngredient("2 Zwiebeln");
        assertEquals(ShoppingMerger.key(en), ShoppingMerger.key(de));
        assertEquals("3 Zwiebeln", ShoppingFormat.line(ShoppingMerger.combine(en, de), true));
        assertEquals("3 Onions", ShoppingFormat.line(ShoppingMerger.combine(en, de), false));
        assertEquals("2 EL Olivenöl", ShoppingFormat.line(RecipeParser.parseIngredient("2 tbsp olive oil"), true));
        assertEquals("2 Zehen Knoblauch",
                ShoppingFormat.line(Canon.normalize(RecipeParser.parseIngredient("2 Knoblauchzehen")), true));
        assertEquals(Canon.Aisle.DAIRY, ShoppingFormat.aisle(RecipeParser.parseIngredient("200 ml Schlagobers")));
    }

    @Test
    public void mergeSuggestions() {
        List<Ingredient> l = new ArrayList<>();
        l.add(RecipeParser.parseIngredient("½ yellow onion"));
        l.add(RecipeParser.parseIngredient("eine rote Zwiebel"));
        l.add(RecipeParser.parseIngredient("2 squeezes of lemon juice"));
        l.add(RecipeParser.parseIngredient("1 lemon"));
        l.add(RecipeParser.parseIngredient("2 tbsp gochujang paste"));
        l.add(RecipeParser.parseIngredient("1 tbsp gochujang"));
        l.add(RecipeParser.parseIngredient("200 g spaghetti"));
        List<ShoppingSuggestions.Suggestion> s = ShoppingSuggestions.compute(l, true);
        assertEquals(3, s.size());
        assertEquals("2 Zwiebeln (1 ½ benötigt; gelb, rot)", ShoppingFormat.line(s.get(0).merged, true));
        assertEquals("kinds", s.get(0).reason);
        assertEquals("2 Zitronen (1 ½ benötigt)", ShoppingFormat.line(s.get(1).merged, true));
        assertEquals("3 tbsp Gochujang", ShoppingFormat.line(s.get(2).merged, false));

        List<Ingredient> lone = new ArrayList<>();
        lone.add(RecipeParser.parseIngredient("2 tbsp lemon juice"));
        List<ShoppingSuggestions.Suggestion> s2 = ShoppingSuggestions.compute(lone, false);
        assertEquals(1, s2.size());
        assertEquals("1 Lemon (⅔ needed; for the juice)", ShoppingFormat.line(s2.get(0).merged, false));
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
