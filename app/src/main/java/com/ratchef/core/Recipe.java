package com.ratchef.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A recipe in the app's homogeneous form. servings == 0 means "unknown". */
public final class Recipe {
    public String id = "";
    public String title = "";
    public int servings = 0;
    public final List<Ingredient> ingredients = new ArrayList<>();
    public final List<String> steps = new ArrayList<>();
    public String sourceUrl = "";
    public String caption = "";
    public boolean aiParsed = false;
    public long createdAt = 0L;
    /** Language of title/ingredients/steps above: "en", "de", "es", … or "" if unknown. */
    public String lang = "";
    /** Translations by language code; amounts and units always come from the original. */
    public final Map<String, Translation> translations = new LinkedHashMap<>();

    /** Title, ingredient names/notes and steps in another language. */
    public static final class Translation {
        public String title = "";
        public final List<Ingredient> ingredients = new ArrayList<>();
        public final List<String> steps = new ArrayList<>();
    }

    /** This recipe shown in another language: same amounts, servings and source, translated text. */
    public Recipe translatedCopy(Translation t) {
        Recipe r = new Recipe();
        r.id = id;
        r.title = t.title;
        r.servings = servings;
        r.ingredients.addAll(t.ingredients);
        r.steps.addAll(t.steps);
        r.sourceUrl = sourceUrl;
        r.caption = caption;
        r.aiParsed = aiParsed;
        r.createdAt = createdAt;
        r.lang = lang;
        r.translations.putAll(translations);
        return r;
    }

    /** Good enough to skip the AI fallback. */
    public boolean looksComplete() {
        return ingredients.size() >= 2 && !steps.isEmpty();
    }
}
