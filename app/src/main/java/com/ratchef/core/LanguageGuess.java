package com.ratchef.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Rough language guess from common words; good enough to decide whether a recipe needs translating. */
public final class LanguageGuess {

    private static final Map<String, String[]> WORDS = new HashMap<>();
    static {
        WORDS.put("en", new String[]{"the", "and", "with", "add", "until", "minutes", "cup", "tbsp", "tsp", "into",
                "salt", "pepper", "mix", "bake", "stir", "of", "for", "chopped", "sauce", "ingredients"});
        WORDS.put("de", new String[]{"und", "mit", "die", "der", "das", "den", "dem", "bis", "minuten", "zugeben",
                "etwas", "salz", "pfeffer", "geben", "zutaten", "zubereitung", "für", "ein", "eine", "einen", "backen",
                "ist", "auf", "aus", "dann", "nicht", "zu", "im", "noch", "kurz", "lassen", "anbraten", "köcheln",
                "hinzufügen", "dazugeben", "gehackt", "zwiebel", "knoblauch", "el", "tl", "prise", "portionen"});
        WORDS.put("es", new String[]{"y", "con", "los", "las", "del", "hasta", "minutos", "pimienta", "añadir",
                "agrega", "añade", "ingredientes", "preparación", "cucharada", "cucharadita", "para", "una", "horno",
                "cebolla", "ajo", "aceite", "sartén", "cocina", "mezcla", "taza", "pizca", "se", "que", "por"});
        WORDS.put("it", new String[]{"e", "con", "il", "la", "di", "del", "fino", "minuti", "sale", "pepe",
                "aggiungere", "ingredienti", "procedimento", "cucchiaio", "per", "una", "forno", "olio"});
        WORDS.put("fr", new String[]{"et", "avec", "le", "la", "les", "de", "du", "jusqu", "minutes", "sel",
                "poivre", "ajouter", "ingrédients", "préparation", "cuillère", "pour", "une", "four", "huile"});
    }

    private LanguageGuess() {}

    /** "en", "de", "es", "it", "fr" or "" when unsure. */
    public static String guess(String text) {
        if (text == null) return "";
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+");
        String best = "";
        int bestScore = 0, second = 0;
        for (Map.Entry<String, String[]> e : WORDS.entrySet()) {
            int score = 0;
            for (String t : tokens) for (String w : e.getValue()) if (t.equals(w)) score++;
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = e.getKey();
            } else if (score > second) {
                second = score;
            }
        }
        // Needs a few hits and a clear lead over the runner-up.
        return bestScore >= 3 && bestScore >= second * 1.3 ? best : "";
    }

    public static String displayName(String code) {
        switch (code) {
            case "en": return "English";
            case "de": return "German";
            case "es": return "Spanish";
            case "it": return "Italian";
            case "fr": return "French";
            case "pt": return "Portuguese";
            default: return code.isEmpty() ? "another language" : code;
        }
    }
}
