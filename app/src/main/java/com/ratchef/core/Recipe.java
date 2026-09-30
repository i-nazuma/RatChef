package com.ratchef.core;

import java.util.ArrayList;
import java.util.List;

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

    /** Good enough to skip the AI fallback. */
    public boolean looksComplete() {
        return ingredients.size() >= 2 && !steps.isEmpty();
    }
}
