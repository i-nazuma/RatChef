package com.ratchef.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline guesses for diet (vegan / vegetarian / meat & fish) and effort (quick / everyday / weekend).
 * Both can be overridden per recipe when a guess is wrong.
 */
public final class RecipeTags {

    public enum Diet { VEGAN, VEGETARIAN, MEAT }

    public enum Effort { QUICK, EVERYDAY, WEEKEND }

    private RecipeTags() {}

    // ------------------------------------------------------------------ diet

    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Explicitly plant-based versions: "vegan cheese", "Haferdrink", "pflanzliche Butter". */
    private static final Pattern PLANT = Pattern.compile(
            "vegan|plant[- ]?based|pflanzlich|vegetal|dairy[- ]free|milchfrei|egg[- ]free|ohne ei|"
                    + "(?:oat|soy|soya|almond|coconut|rice|hafer|soja|mandel|kokos|reis)[- ]?(?:milk|milch|drink|cream|sahne|joghurt|yogh?urt)|"
                    + "peanut butter|erdnussbutter|almond butter|mandelmus|nut butter|kokosmilch|coconut milk|"
                    + "beyond|like meat|seitan|tofu|tempeh|jackfruit", F);

    private static final Pattern MEAT = Pattern.compile(
            "chicken|beef|pork|bacon|ham\\b|salami|sausage|chorizo|prosciutto|pancetta|lamb|turkey|duck|veal|steak|"
                    + "\\bmince\\b|minced (?:beef|meat|pork|lamb)|meatball|hähnchen|huhn|hendl|hühner|rind\\b|rinder|rindfleisch|rinds|"
                    + "schwein|speck|schinken|wurst|würst|hackfleisch|\\bhack\\b|faschiert|"
                    + "lamm|\\bpute\\b|putenbrust|truthahn|\\bente\\b|entenbrust|kalb|leberkäse|fleisch|meat|"
                    + "fish|fisch|salmon|lachs|tuna|thunfisch|\\bcod\\b|kabeljau|shrimp|prawn|garnele|crevette|anchov|sardell|"
                    + "sardin|mussel|muschel|squid|calamar|tintenfisch|scallop|crab|krabbe|lobster|hummer|forelle|trout|"
                    + "fish sauce|fischsauce|oyster sauce|austernsauce|gelatin|gelatine|lard|schmalz|"
                    + "pollo|carne|cerdo|ternera|jamón|jamon|atún|atun|gambas|pescado|bacalao|pancetta|manzo|maiale|"
                    + "poulet|boeuf|bœuf|\\bporc\\b|jambon|lardons|poisson|saumon", F);

    private static final Pattern ANIMAL = Pattern.compile(
            "cheese|käse|kaese|parmesan|parmigiano|pecorino|mozzarella|feta|ricotta|mascarpone|burrata|halloumi|"
                    + "cheddar|gouda|emmentaler|brie|milk|milch|butter|ghee|cream|sahne|obers|rahm|schmand|crème|creme|"
                    + "joghurt|jogurt|yogh?urt|quark|topfen|egg|ei\\b|eier|eigelb|eiweiß|eiweiss|dotter|mayo|honey|honig|"
                    + "queso|leche|huevo|nata|mantequilla|formaggio|latte|uovo|uova|fromage|lait|oeuf|œuf|beurre", F);

    public static Diet diet(List<Ingredient> ingredients) {
        boolean animal = false;
        for (Ingredient i : ingredients) {
            String n = (i.name + " " + i.note).toLowerCase(Locale.ROOT);
            Canon.Match m = Canon.match(i.name, i.unit);
            boolean plant = PLANT.matcher(n).find()
                    || (m != null && (m.entry.key.equals("plant_milk") || m.entry.key.equals("tofu")
                    || m.entry.key.equals("coconut_milk") || m.entry.key.equals("peanut_butter")));
            if (plant) continue;
            if (m != null) {
                String k = m.entry.key;
                if (m.entry.aisle == Canon.Aisle.MEAT || k.equals("tuna") || k.equals("chicken_stock")
                        || k.equals("beef_stock")) return Diet.MEAT;
                if (m.entry.aisle == Canon.Aisle.DAIRY || k.equals("honey")) { animal = true; continue; }
            }
            if (MEAT.matcher(n).find()) {
                // "Gemüsebrühe", "veggie broth" are fine; "Hühnerbrühe" isn't
                if (n.contains("gemüse") || n.contains("vegetable") || n.contains("veggie")) continue;
                return Diet.MEAT;
            }
            if (ANIMAL.matcher(n).find()) animal = true;
        }
        return animal ? Diet.VEGETARIAN : Diet.VEGAN;
    }

    // ------------------------------------------------------------------ effort

    private static final String NUM = "(\\d+(?:[.,]\\d+)?)";
    private static final Pattern DURATION = Pattern.compile(
            NUM + "\\s*(?:-|–|to|bis|a)?\\s*" + "(\\d+(?:[.,]\\d+)?)?\\s*"
                    + "(minutes|minute|minuten|minutos|mins|min|stunden|stunde|std|hours|hour|hrs|hr|horas|hora|ore|heures|h)\\b", F);
    private static final Pattern READY = Pattern.compile(
            "\\b(?:ready in|done in|in (?:just|only|nur|unter|under)|fertig in|in nur|total(?: time)?|gesamt(?:zeit)?|"
                    + "zubereitungszeit|listo en|en solo)\\s*:?\\s*" + NUM + "\\s*(?:-|–)?\\s*(\\d+)?\\s*"
                    + "(minutes|minuten|min|minutos|stunden|std|hours|hour|h)\\b", F);
    private static final Pattern OVERNIGHT = Pattern.compile(
            "overnight|über nacht|ueber nacht|toda la noche|24 ?h|12 ?h|next day|am nächsten tag|day ahead|vortag", F);
    private static final Pattern SLOW = Pattern.compile(
            "slow[- ]cook|schmor|braise|brais|confit|smok|räucher|sourdough|sauerteig|proof|gehen lassen|"
                    + "rise until|ruhen lassen|marinate|marinier|laminat|blätterteig selbst", F);

    /** Rough total time in minutes from the text, or -1 if no times are mentioned. */
    public static int minutes(List<String> steps, String caption) {
        String all = String.join("\n", steps) + "\n" + (caption == null ? "" : caption);
        Matcher r = READY.matcher(all);
        if (r.find()) return toMinutes(r.group(1), r.group(2), r.group(3));

        int sum = 0;
        boolean any = false;
        for (String s : steps) {
            Matcher m = DURATION.matcher(s);
            while (m.find()) {
                sum += toMinutes(m.group(1), m.group(2), m.group(3));
                any = true;
            }
        }
        if (!any) {
            // Prep/cook lines in the caption ("Prep: 10 min, Cook: 25 min")
            Matcher m = DURATION.matcher(caption == null ? "" : caption);
            while (m.find()) {
                sum += toMinutes(m.group(1), m.group(2), m.group(3));
                any = true;
            }
        }
        return any ? sum : -1;
    }

    private static int toMinutes(String a, String b, String unit) {
        double v = Double.parseDouble((b != null ? b : a).replace(',', '.'));
        String u = unit.toLowerCase(Locale.ROOT);
        boolean hours = u.startsWith("h") || u.startsWith("std") || u.startsWith("stun") || u.startsWith("ore")
                || u.startsWith("heure");
        return (int) Math.round(hours ? v * 60 : v);
    }

    public static Effort effort(List<Ingredient> ingredients, List<String> steps, String caption) {
        String all = (String.join("\n", steps) + "\n" + (caption == null ? "" : caption));
        int ing = 0;
        for (Ingredient i : ingredients) {
            Canon.Match m = Canon.match(i.name, i.unit);
            if (m != null && (PantryMatcher.BASICS.contains(m.entry.key) || m.entry.aisle == Canon.Aisle.SPICES)) continue;
            if (ShoppingMerger.shouldSkip(i)) continue;
            ing++;
        }
        int nSteps = steps.size();
        int min = minutes(steps, caption);

        if (OVERNIGHT.matcher(all).find()) return Effort.WEEKEND;
        if (min >= 90) return Effort.WEEKEND;
        if (ing >= 15 || nSteps >= 12) return Effort.WEEKEND;
        boolean slow = SLOW.matcher(all).find();

        if (min >= 0) {
            if (min <= 30 && !slow && ing <= 10 && nSteps <= 8) return Effort.QUICK;
            return Effort.EVERYDAY;
        }
        if (!slow && ing <= 7 && nSteps <= 5) return Effort.QUICK;
        return Effort.EVERYDAY;
    }
}
