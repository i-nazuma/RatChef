package com.ratchef.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based caption -> Recipe parser. Handles English and German captions with or without
 * "Ingredients:" / "Instructions:" headers, emoji bullets, keycap step numbers, inline comma
 * lists, trailing amounts ("Mehl: 200 g"), macros/time lines and hashtag trailers.
 */
public final class RecipeParser {

    private enum Section { PRE, INGREDIENTS, STEPS }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern INGR_HEADER = Pattern.compile(
            "^(?:the\\s+)?(?:ingredients?(?:\\s+list)?|zutaten(?:liste)?|you(?:'ll|’ll| will) need|"
                    + "what you(?:'ll|’ll)? need|ingredienti|ingrédients|ingredientes|necesitas|shopping list|"
                    + "einkaufsliste|was du brauchst)"
                    + "\\s*(\\([^)]*\\))?\\s*(?:$|[:：\\-–—]\\s*(.*)$)", FLAGS);

    private static final Pattern STEP_HEADER = Pattern.compile(
            "^(?:the\\s+)?(?:instructions?|method|directions?|steps|preparation|how to make(?: it)?|"
                    + "recipe steps|zubereitung|anleitung|so geht'?s|so geht’s|procedimento|préparation|"
                    + "preparación|preparacion|elaboración|elaboracion|modo de preparación|pasos|instrucciones|"
                    + "procedimiento|preparazione|how to)"
                    + "\\s*(\\([^)]*\\))?\\s*(?:$|[:：\\-–—]\\s*(.*)$)", FLAGS);

    private static final Pattern NUMBERED = Pattern.compile(
            "^(?:(?:step|schritt|paso|étape)\\s*(\\d{1,2})\\s*[:.)\\-–]?\\s*|(\\d{1,2})\\s*[.)]\\s+)(.+)$", FLAGS);

    private static final Pattern SUBHEADER = Pattern.compile("^[^\\d:]{2,40}:$");

    private static final Pattern NOISE = Pattern.compile(
            "^(?:follow|save (?:this|it|for)|like (?:and|&)|comment|share (?:this|with)|tag (?:a|your|someone)|"
                    + "link in (?:my )?bio|full recipe|recipe (?:by|credit|from)|credit|folg|speicher|"
                    + "teile? (?:das|dieses|mit)|kommentier|enjoy|guten appetit|bon app[ée]tit|"
                    + "let me know|would you try|did you|who else)\\b.*", FLAGS);

    private static final Pattern MACROS = Pattern.compile(
            "(?:\\b(?:kcal|calories|cals|kalorien|macros|makros|nährwerte|nutrition(?:al)?(?: info)?)\\b"
                    + "|^(?:protein|carbs?|fat|fett|kh|eiweiß|eiweiss|kohlenhydrate|ballaststoffe|fiber|fibre)\\s*[:\\-]?\\s*\\d"
                    + "|\\d+\\s*g\\s*(?:protein|carbs?|fat|fett|eiweiß|kh)\\s*(?:[,/|]|$)"
                    + "|^[pcf]\\s*:\\s*\\d)", FLAGS);

    private static final Pattern TIMES = Pattern.compile(
            "^(?:prep(?:aration)?|cook(?:ing)?|total|bake|baking|active|zubereitungs|koch|back|gesamt|"
                    + "arbeits|ruhe)(?:\\s*time|zeit)?\\s*[:\\-]?\\s*\\d.*", FLAGS);

    private static final Pattern SERVINGS_LINE = Pattern.compile(
            "^(?:serves|servings?|portions?|portionen|yields?|makes|für|for)\\s*:?\\s*\\d{1,2}"
                    + "(?:\\s*[-–]\\s*\\d{1,2})?\\s*(?:servings?|portions?|portionen|personen|people|persons|pers\\.?)?\\s*$"
                    + "|^\\d{1,2}\\s*(?:servings?|portions?|portionen|personen|people|persons)\\s*$", FLAGS);

    private static final Pattern SERVINGS_A = Pattern.compile(
            "\\b(?:serves|servings?|portions?|portionen|yields?|makes)\\s*:?\\s*(\\d{1,2})\\b", FLAGS);
    private static final Pattern SERVINGS_B = Pattern.compile(
            "\\b(\\d{1,2})\\s*(?:servings|portions|portionen|personen|people|persons|pers\\.)", FLAGS);

    private static final Pattern HASHTAG = Pattern.compile("(?:^|\\s)#[\\p{L}\\p{N}_]+");
    private static final Pattern ONLY_MENTIONS = Pattern.compile("^(?:@[\\w.]+\\s*)+$");
    private static final Pattern LEADING_BULLETS = Pattern.compile("^[\\s\\-–—•*·▪▫◦►▶>+~|=_]+");
    private static final Pattern PARENS = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern WORD = Pattern.compile("^([\\p{L}.]+)(.*)$", Pattern.DOTALL);
    private static final Pattern TRAILING_AMOUNT = Pattern.compile(
            "^(.*?[\\p{L})])\\s*[:\\-–—]?\\s*(" + Quantities.NUM + ")(?:\\s*[-–]\\s*(" + Quantities.NUM + "))?"
                    + "\\s*([\\p{L}.]+)?\\s*$", FLAGS);
    private static final Pattern TO_TASTE = Pattern.compile(
            "\\s*,?\\s*\\b(to taste|nach geschmack|nach belieben|optional|as needed|for garnish|zum garnieren)\\b\\s*",
            FLAGS);

    private RecipeParser() {}

    // ------------------------------------------------------------------ public API

    public static Recipe parse(String caption) {
        Recipe r = new Recipe();
        r.caption = caption == null ? "" : caption;
        r.servings = findServings(r.caption);

        List<String> lines = cleanLines(r.caption);
        boolean hasHeaders = false;
        for (String l : lines) {
            if (INGR_HEADER.matcher(l).matches() || STEP_HEADER.matcher(l).matches()) {
                hasHeaders = true;
                break;
            }
        }

        Section section = Section.PRE;
        boolean seenIngredient = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            Matcher ih = INGR_HEADER.matcher(line);
            if (ih.matches()) {
                section = Section.INGREDIENTS;
                String rest = ih.group(2);
                if (rest != null && !rest.trim().isEmpty()) addIngredientsFrom(rest, r);
                continue;
            }
            Matcher sh = STEP_HEADER.matcher(line);
            if (sh.matches()) {
                section = Section.STEPS;
                String rest = sh.group(2);
                if (rest != null && !rest.trim().isEmpty()) addStep(rest, r);
                continue;
            }
            if (isNoise(line)) continue;

            if (hasHeaders) {
                switch (section) {
                    case PRE:
                        if (r.title.isEmpty()) r.title = makeTitle(line);
                        break;
                    case INGREDIENTS:
                        if (SUBHEADER.matcher(line).matches()) break;
                        if (looksLikeStep(line)) {
                            section = Section.STEPS;
                            addStep(line, r);
                        } else {
                            addIngredientsFrom(line, r);
                        }
                        break;
                    case STEPS:
                        if (SUBHEADER.matcher(line).matches()) break;
                        addStep(line, r);
                        break;
                }
            } else {
                // No headers: first plain line is the title, then classify line by line.
                if (i == 0 && r.title.isEmpty() && !looksLikeIngredient(line) && !isNumbered(line)) {
                    r.title = makeTitle(line);
                    continue;
                }
                if (SUBHEADER.matcher(line).matches()) continue;
                if (looksLikeStep(line)) {
                    addStep(line, r);
                } else if (looksLikeIngredient(line)) {
                    addIngredientsFrom(line, r);
                    seenIngredient = true;
                } else if (seenIngredient && line.length() > 25) {
                    addStep(line, r); // prose instructions after the ingredient list
                }
                // otherwise: description text before the recipe -> ignore
            }
        }

        if (r.title.isEmpty()) r.title = "Reel recipe";
        splitSingleParagraphSteps(r);
        return r;
    }

    public static int findServings(String text) {
        if (text == null) return 0;
        Matcher m = SERVINGS_A.matcher(text);
        if (m.find()) return safeInt(m.group(1));
        m = SERVINGS_B.matcher(text);
        if (m.find()) return safeInt(m.group(1));
        return 0;
    }

    /** Parse a single ingredient line. Returns null if nothing usable is left. */
    public static Ingredient parseIngredient(String line) {
        String s = line.trim();
        while (s.endsWith(".") || s.endsWith(",") || s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();

        List<String> notes = new ArrayList<>();
        Matcher pm = PARENS.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (pm.find()) {
            String inner = pm.group(1).trim();
            if (!inner.isEmpty()) notes.add(inner);
            pm.appendReplacement(sb, " ");
        }
        pm.appendTail(sb);
        s = sb.toString().replaceAll("\\s+", " ").trim();

        Matcher tt = TO_TASTE.matcher(s);
        if (tt.find()) {
            notes.add(tt.group(1).toLowerCase(Locale.ROOT));
            s = (s.substring(0, tt.start()) + " " + s.substring(tt.end())).trim();
        }

        double qty = Double.NaN, qtyMax = Double.NaN;
        String unit = "";
        String name;

        Quantities.Leading lead = Quantities.readLeading(s);
        if (lead != null) {
            qty = lead.qty;
            qtyMax = lead.qtyMax;
            String rest = lead.rest.trim();
            Matcher w = WORD.matcher(rest);
            if (w.matches()) {
                Units.Unit u = Units.lookup(w.group(1));
                if (u != null) {
                    unit = u.key;
                    rest = w.group(2).trim();
                } else if (w.group(1).equalsIgnoreCase("x")) {
                    rest = w.group(2).trim();
                }
            } else if (rest.startsWith("×")) {
                rest = rest.substring(1).trim();
            }
            rest = rest.replaceFirst("(?i)^(of|von|vom|de)\\s+", "");
            name = rest;
        } else if (leadingCountUnit(s) != null) {
            Matcher w = WORD.matcher(s);
            w.matches();
            qty = 1;
            unit = leadingCountUnit(s).key;
            name = w.group(2).trim().replaceFirst("(?i)^(of|von|vom|de)\\s+", "");
        } else {
            Matcher ta = TRAILING_AMOUNT.matcher(s);
            Units.Unit u = null;
            boolean ok = false;
            if (ta.matches()) {
                String unitTok = ta.group(4);
                u = unitTok == null ? null : Units.lookup(unitTok);
                ok = unitTok == null || u != null;
            }
            if (ok) {
                name = ta.group(1).trim();
                qty = Quantities.parse(ta.group(2));
                qtyMax = ta.group(3) != null ? Quantities.parse(ta.group(3)) : Double.NaN;
                unit = u == null ? "" : u.key;
            } else {
                name = s;
            }
        }

        // "onion, finely chopped" / "garlic - minced"
        int comma = name.indexOf(", ");
        if (comma > 0) {
            notes.add(0, name.substring(comma + 2).trim());
            name = name.substring(0, comma);
        } else {
            Matcher dash = Pattern.compile("\\s[-–—]\\s").matcher(name);
            if (dash.find() && dash.start() > 0) {
                notes.add(0, name.substring(dash.end()).trim());
                name = name.substring(0, dash.start());
            }
        }
        name = name.replaceAll("^[:\\-–—\\s]+|[:\\-–—,\\s]+$", "").replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) return null;
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        StringBuilder note = new StringBuilder();
        for (String n : notes) {
            if (n.isEmpty()) continue;
            if (note.length() > 0) note.append("; ");
            note.append(n);
        }
        return new Ingredient(qty, qtyMax, unit, name, note.toString());
    }

    // ------------------------------------------------------------------ helpers

    static List<String> cleanLines(String caption) {
        String t = caption.replace("\r", "").replace('⁄', '/');
        t = t.replace("🔟", "10. ");                     // 🔟
        t = t.replaceAll("(\\d)\\uFE0F?\\u20E3", "$1. ");          // 1️⃣ -> "1. "
        List<String> out = new ArrayList<>();
        for (String raw : t.split("\n")) {
            String l = stripEmoji(raw);
            l = HASHTAG.matcher(l).replaceAll(" ");
            l = LEADING_BULLETS.matcher(l).replaceFirst("");
            l = l.replaceAll("[\\t\\u00A0 ]+", " ").trim();
            if (l.isEmpty() || l.matches("^[.\\-–—_=*·•]+$")) continue;
            if (ONLY_MENTIONS.matcher(l).matches()) continue;
            out.add(l);
        }
        return out;
    }

    static String stripEmoji(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            boolean emoji = cp >= 0x1F000
                    || (cp >= 0x2600 && cp <= 0x27BF)   // misc symbols, dingbats (✅ ✔ ❤)
                    || (cp >= 0x2B00 && cp <= 0x2BFF)   // ⭐ ⬇
                    || (cp >= 0x2190 && cp <= 0x21FF)   // arrows
                    || (cp >= 0x25A0 && cp <= 0x25FF)   // geometric shapes
                    || (cp >= 0x2300 && cp <= 0x23FF)   // ⏰ ⌛
                    || cp == 0xFE0F || cp == 0x200D || cp == 0x20E3;
            if (!emoji) sb.appendCodePoint(cp);
        });
        return sb.toString();
    }

    private static Units.Unit leadingCountUnit(String s) {
        Matcher w = WORD.matcher(s);
        if (!w.matches() || w.group(2).trim().isEmpty()) return null;
        Units.Unit u = Units.lookup(w.group(1));
        return u != null && u.family == Units.Family.COUNT && !u.key.equals("piece") ? u : null;
    }

    private static boolean isNoise(String l) {
        return NOISE.matcher(l).matches()
                || MACROS.matcher(l).find()
                || TIMES.matcher(l).matches()
                || SERVINGS_LINE.matcher(l).matches();
    }

    private static boolean isNumbered(String l) {
        return NUMBERED.matcher(l).matches();
    }

    private static boolean looksLikeIngredient(String l) {
        if (l.length() > 90) return false;
        Quantities.Leading lead = Quantities.readLeading(l);
        if (lead != null) {
            // "2 minutes later..." is not an ingredient
            String rest = lead.rest.trim().toLowerCase(Locale.ROOT);
            return !rest.matches("^(min|minute|minuten|mins|sec|seconds|sekunden|hours?|stunden?|std|°|degrees|grad).*");
        }
        Matcher ta = TRAILING_AMOUNT.matcher(l);
        if (ta.matches() && l.length() < 60) {
            String unitTok = ta.group(4);
            return unitTok == null || Units.lookup(unitTok) != null;
        }
        return l.length() <= 40 && TO_TASTE.matcher(l).find();
    }

    private static boolean looksLikeStep(String l) {
        Matcher m = NUMBERED.matcher(l);
        if (m.matches()) {
            String body = m.group(3).trim();
            // numbered ingredient list: "1. 200 g flour"
            return !(body.length() < 50 && Quantities.readLeading(body) != null && looksLikeIngredient(body));
        }
        return l.length() > 80 && Quantities.readLeading(l) == null;
    }

    private static void addIngredientsFrom(String text, Recipe r) {
        List<String> parts = splitTopLevel(text);
        int withQty = 0;
        for (String p : parts) if (Quantities.readLeading(p.trim()) != null) withQty++;
        if (parts.size() >= 2 && withQty >= 2) {
            for (String p : parts) {
                Ingredient ing = parseIngredient(p);
                if (ing != null) r.ingredients.add(ing);
            }
        } else {
            Matcher m = NUMBERED.matcher(text);
            String body = m.matches() ? m.group(3) : text;
            Ingredient ing = parseIngredient(body);
            if (ing != null) r.ingredients.add(ing);
        }
    }

    /** Split on , and ; that are not inside parentheses and not decimal commas ("1,5"). */
    static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            else if ((c == ',' || c == ';') && depth == 0) {
                boolean decimal = c == ',' && i > 0 && i + 1 < s.length()
                        && Character.isDigit(s.charAt(i - 1)) && Character.isDigit(s.charAt(i + 1));
                if (!decimal) {
                    out.add(s.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        out.add(s.substring(start).trim());
        out.removeIf(String::isEmpty);
        return out;
    }

    private static void addStep(String line, Recipe r) {
        Matcher m = NUMBERED.matcher(line);
        String body = m.matches() ? m.group(3) : line;
        body = body.replaceAll("^[:\\-–—\\s]+", "").trim();
        if (body.length() < 4) return;
        body = Character.toUpperCase(body.charAt(0)) + body.substring(1);
        r.steps.add(body);
    }

    /** One long prose paragraph -> sentences, so the step view stays readable. */
    private static void splitSingleParagraphSteps(Recipe r) {
        if (r.steps.size() > 2) return;
        List<String> out = new ArrayList<>();
        for (String s : r.steps) {
            if (s.length() < 160) {
                out.add(s);
                continue;
            }
            for (String part : s.split("(?<=[.!?])\\s+(?=[\\p{Lu}])")) {
                String p = part.trim();
                if (p.length() >= 4) out.add(p);
            }
        }
        r.steps.clear();
        r.steps.addAll(out);
    }

    private static String makeTitle(String line) {
        String t = line.replaceAll("(?i)\\s*[-–|,(]?\\s*\\b(?:serves|makes|for|für|yields?)\\s+\\d{1,2}\\b[^)]*\\)?\\s*$", "");
        t = t.replaceAll("[\\s:!.,\\-–|]+$", "").trim();
        if (t.isEmpty()) t = line.trim();
        if (t.length() > 70) {
            Matcher m = Pattern.compile("^(.{15,70}?)[.!?|]").matcher(t);
            t = m.find() ? m.group(1).trim() : t.substring(0, 67).trim() + "…";
        }
        return t;
    }

    private static int safeInt(String s) {
        try {
            int v = Integer.parseInt(s);
            return v > 0 && v <= 50 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
