package com.ratchef.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small bilingual ingredient dictionary. Recognises the same ingredient across English, German and
 * Austrian names ("yellow onion", "rote Zwiebel", "Zwiebeln"), so the shopping list can merge them,
 * name them in one language, sort them by aisle and pick the right vegetable icon.
 */
public final class Canon {

    /** Supermarket aisles, in the order the shopping list shows them. */
    public enum Aisle {
        PRODUCE("Fruit & vegetables", "Obst & Gemüse"),
        DAIRY("Dairy & eggs", "Kühlregal & Eier"),
        MEAT("Meat & fish", "Fleisch & Fisch"),
        BAKERY("Bread", "Brot & Gebäck"),
        PANTRY("Pantry", "Vorrat"),
        BAKING("Baking", "Backen"),
        SPICES("Spices & seasoning", "Gewürze"),
        DRINKS("Drinks", "Getränke"),
        OTHER("Other", "Sonstiges");

        public final String en, de;

        Aisle(String en, String de) {
            this.en = en;
            this.de = de;
        }

        public String label(boolean german) {
            return german ? de : en;
        }
    }

    public static final class Entry {
        public final String key;
        public final Aisle aisle;
        public final String deSg, dePl, enSg, enPl;
        final char gender;             // German gender for adjective endings: m, f, n
        public final double gramsEach; // > 0: usually bought as whole pieces
        public final String veggie;    // icon name or ""
        final boolean variants;        // red / yellow / white / green kinds
        final boolean fruitForms;      // juice / zest (lemon, lime, orange)

        Entry(String[] f) {
            key = f[0];
            aisle = aisleOf(f[1]);
            deSg = f[2];
            dePl = f[3].isEmpty() ? f[2] : f[3];
            gender = f[4].isEmpty() ? 'n' : f[4].charAt(0);
            enSg = f[5];
            enPl = f[6].isEmpty() ? f[5] : f[6];
            gramsEach = f[7].isEmpty() ? 0 : Double.parseDouble(f[7]);
            veggie = f[8];
            variants = f[9].contains("v");
            fruitForms = f[9].contains("f");
        }

        public boolean countable() {
            return gramsEach > 0;
        }
    }

    /** Result of recognising an ingredient name. */
    public static final class Match {
        public final Entry entry;
        public final String variant; // "red", "yellow", "white", "green" or ""
        public final String form;    // "juice", "zest" or ""

        Match(Entry entry, String variant, String form) {
            this.entry = entry;
            this.variant = variant;
            this.form = form;
        }

        /** Same thing to buy: entry + kind + form. */
        public String key() {
            return entry.key + "|" + variant + "|" + form;
        }
    }

    // key|aisle|de singular|de plural|gender|en singular|en plural|grams each|icon|flags|extra synonyms
    // Aisle: P produce, D dairy, M meat, B bakery, V pantry, K baking, S spices, G drinks.
    // Synonyms of 5+ letters also match inside words ("Knoblauchzehen"); shorter ones only as words.
    private static final String[] TABLE = {
            "onion|P|Zwiebel|Zwiebeln|f|onion|onions|150|ONION|v|onion",
            "spring_onion|P|Frühlingszwiebel|Frühlingszwiebeln|f|spring onion|spring onions|15|ONION||green onion,scallion,jungzwiebel,lauchzwiebel",
            "shallot|P|Schalotte|Schalotten|f|shallot|shallots|30|ONION||",
            "leek|P|Lauch|Lauch|m|leek|leeks|200|ONION||porree",
            "garlic|P|Knoblauch|Knoblauch|m|garlic|garlic||GARLIC||",
            "tomato|P|Tomate|Tomaten|f|tomato|tomatoes|120|TOMATO||paradeiser",
            "cherry_tomato|P|Cherrytomate|Cherrytomaten|f|cherry tomato|cherry tomatoes|15|TOMATO||grape tomato,kirschtomate,cocktailtomate,datteltomate,cherry-tomate,cocktail-tomate",
            "canned_tomatoes|V|Dosentomaten|Dosentomaten|f|canned tomatoes|canned tomatoes||TOMATO||canned tomato,chopped tomatoes,diced tomatoes,crushed tomatoes,tinned tomato,peeled tomatoes,gehackte tomaten,stückige tomaten,geschälte tomaten,pelati",
            "passata|V|Passata|Passata|f|passata|passata||TOMATO||passierte tomaten,tomato sauce,tomatensauce,tomatensoße",
            "tomato_paste|V|Tomatenmark|Tomatenmark|n|tomato paste|tomato paste||TOMATO||tomato purée,paradeismark",
            "potato|P|Kartoffel|Kartoffeln|f|potato|potatoes|150|POTATO||erdapfel,erdäpfel,erdaepfel",
            "sweet_potato|P|Süßkartoffel|Süßkartoffeln|f|sweet potato|sweet potatoes|300|POTATO||süsskartoffel,suesskartoffel",
            "carrot|P|Karotte|Karotten|f|carrot|carrots|80|CARROT||möhre,moehre,mohrrübe,rüebli",
            "bell_pepper|P|Paprika|Paprika|m|bell pepper|bell peppers|160|PEPPER|v|red pepper,green pepper,yellow pepper,capsicum,paprikaschote",
            "chili|P|Chili|Chilis|f|chili|chilies|10|PEPPER||chili pepper,chilli pepper,chilli,jalapeño,jalapeno,pfefferoni,peperoncino",
            "zucchini|P|Zucchini|Zucchini|f|zucchini|zucchini|250|ZUCCHINI||zucchetti,courgette",
            "cucumber|P|Gurke|Gurken|f|cucumber|cucumbers|400|ZUCCHINI||salatgurke",
            "eggplant|P|Melanzani|Melanzani|f|eggplant|eggplants|300|EGGPLANT||aubergine,melanzane",
            "pumpkin|P|Kürbis|Kürbisse|m|pumpkin|pumpkins|1000|||butternut,hokkaido",
            "mushroom|P|Champignon|Champignons|m|mushroom|mushrooms||MUSHROOM||pilze,schwammerl,egerling",
            "spinach|P|Spinat|Spinat|m|spinach|spinach||HERB||blattspinat",
            "lettuce|P|Salat|Salat|m|lettuce|lettuce|300|HERB||kopfsalat,blattsalat,eisbergsalat,romana,romaine,häuptelsalat",
            "arugula|P|Rucola|Rucola|m|arugula|arugula||HERB||rocket,rauke",
            "broccoli|P|Brokkoli|Brokkoli|m|broccoli|broccoli|400|HERB||",
            "cauliflower|P|Karfiol|Karfiol|m|cauliflower|cauliflower|800|||blumenkohl",
            "cabbage|P|Kraut|Kraut|n|cabbage|cabbage|1000||v|weißkraut,weisskraut,rotkraut,blaukraut,weißkohl,rotkohl,spitzkohl",
            "sauerkraut|V|Sauerkraut|Sauerkraut|n|sauerkraut|sauerkraut||||",
            "kale|P|Grünkohl|Grünkohl|m|kale|kale||HERB||",
            "celery|P|Sellerie|Sellerie|m|celery|celery||||stangensellerie,staudensellerie",
            "ginger|P|Ingwer|Ingwer|m|ginger|ginger||||",
            "lemongrass|P|Zitronengras|Zitronengras|n|lemongrass|lemongrass||||lemon grass",
            "lemon|P|Zitrone|Zitronen|f|lemon|lemons|100|LEMON|f|",
            "lime|P|Limette|Limetten|f|lime|limes|70|LEMON|f|",
            "orange|P|Orange|Orangen|f|orange|oranges|200||f|",
            "pineapple|P|Ananas|Ananas|f|pineapple|pineapples|1000||||",
            "apple|P|Apfel|Äpfel|m|apple|apples|180||||",
            "banana|P|Banane|Bananen|f|banana|bananas|120||||",
            "avocado|P|Avocado|Avocados|f|avocado|avocados|200||||",
            "blueberries|P|Heidelbeeren|Heidelbeeren|f|blueberries|blueberries||||blueberr,heidelbeer,blaubeer",
            "raspberries|P|Himbeeren|Himbeeren|f|raspberries|raspberries||||raspberr,himbeer",
            "strawberries|P|Erdbeeren|Erdbeeren|f|strawberries|strawberries||||strawberr,erdbeer",
            "parsley|P|Petersilie|Petersilie|f|parsley|parsley||HERB||",
            "basil|P|Basilikum|Basilikum|n|basil|basil||HERB||",
            "coriander|P|Koriander|Koriander|m|coriander|coriander||HERB||cilantro",
            "dill|P|Dill|Dill|m|dill|dill||HERB||",
            "chives|P|Schnittlauch|Schnittlauch|m|chives|chives||HERB||chive",
            "mint|P|Minze|Minze|f|mint|mint||HERB||",
            "thyme|P|Thymian|Thymian|m|thyme|thyme||HERB||",
            "rosemary|P|Rosmarin|Rosmarin|m|rosemary|rosemary||HERB||",
            "sage|P|Salbei|Salbei|m|sage|sage||HERB||",

            "milk|D|Milch|Milch|f|milk|milk||||vollmilch",
            "plant_milk|D|Haferdrink|Haferdrink|m|oat milk|oat milk||||almond milk,soy milk,hafermilch,mandelmilch,sojamilch,pflanzenmilch,mandeldrink,sojadrink",
            "buttermilk|D|Buttermilch|Buttermilch|f|buttermilk|buttermilk||||",
            "butter|D|Butter|Butter|f|butter|butter||||",
            "cream|D|Schlagobers|Schlagobers|n|cream|cream||||heavy cream,whipping cream,double cream,single cream,cooking cream,sahne,schlagsahne,obers,kochsahne",
            "sour_cream|D|Sauerrahm|Sauerrahm|m|sour cream|sour cream||||saure sahne,schmand",
            "creme_fraiche|D|Crème fraîche|Crème fraîche|f|crème fraîche|crème fraîche||||creme fraiche,crème fraiche,creme fraîche",
            "cream_cheese|D|Frischkäse|Frischkäse|m|cream cheese|cream cheese||||",
            "quark|D|Topfen|Topfen|m|quark|quark||||",
            "yogurt|D|Joghurt|Joghurt|n|yogurt|yogurt||||yoghurt,jogurt",
            "parmesan|D|Parmesan|Parmesan|m|parmesan|parmesan||||parmigiano,grana padano",
            "mozzarella|D|Mozzarella|Mozzarella|f|mozzarella|mozzarella|125|||",
            "feta|D|Feta|Feta|m|feta|feta||||feta cheese,schafskäse",
            "cheese|D|Käse|Käse|m|cheese|cheese||||cheddar,gouda,emmentaler,bergkäse",
            "eggs|D|Ei|Eier|n|egg|eggs|60|||egg yolk,eigelb,eidotter",
            "tofu|D|Tofu|Tofu|m|tofu|tofu||||",

            "chicken_breast|M|Hühnerbrust|Hühnerbrust|f|chicken breast|chicken breasts|180|||chicken fillet,hähnchenbrust,hühnerfilet,hähnchenfilet,hendlbrust",
            "chicken_thigh|M|Hühnerkeule|Hühnerkeulen|f|chicken thigh|chicken thighs|150|||chicken leg,hähnchenschenkel,hähnchenkeule,hühnerhaxe",
            "chicken|M|Huhn|Huhn|n|chicken|chicken||||hähnchen,hendl",
            "ground_meat|M|Faschiertes|Faschiertes|n|ground meat|ground meat||||ground beef,minced beef,beef mince,minced meat,mince,hackfleisch,rinderhack,rinderfaschiertes",
            "beef|M|Rindfleisch|Rindfleisch|n|beef|beef||||",
            "pork|M|Schweinefleisch|Schweinefleisch|n|pork|pork||||",
            "bacon|M|Speck|Speck|m|bacon|bacon||||pancetta,bauchspeck",
            "ham|M|Schinken|Schinken|m|ham|ham||||prosciutto",
            "salmon|M|Lachs|Lachs|m|salmon|salmon||||",
            "shrimp|M|Garnelen|Garnelen|f|shrimp|shrimp||||prawn,garnele,crevette",
            "tuna|V|Thunfisch|Thunfisch|m|tuna|tuna||||",

            "pasta|V|Nudeln|Nudeln|f|pasta|pasta||||noodles,egg noodles,rice noodles,reisnudeln",
            "rice|V|Reis|Reis|m|rice|rice||||basmati,jasmine rice,milchreis,risotto rice",
            "oats|V|Haferflocken|Haferflocken|f|oats|oats||||rolled oats,oatmeal",
            "breadcrumbs|V|Semmelbrösel|Semmelbrösel|n|breadcrumbs|breadcrumbs||||breadcrumb,bread crumb,panko,paniermehl",
            "chickpeas|V|Kichererbsen|Kichererbsen|f|chickpeas|chickpeas||||chickpea,garbanzo,kichererbse",
            "kidney_beans|V|Kidneybohnen|Kidneybohnen|f|kidney beans|kidney beans||||kidney bean,kidneybohne",
            "lentils|V|Linsen|Linsen|f|lentils|lentils|||v|lentil",
            "peas|V|Erbsen|Erbsen|f|peas|peas||||green peas",
            "corn|V|Mais|Mais|m|corn|corn||||sweetcorn",
            "coconut_milk|V|Kokosmilch|Kokosmilch|f|coconut milk|coconut milk||||coconut cream",
            "veg_stock|V|Gemüsebrühe|Gemüsebrühe|f|vegetable stock|vegetable stock||||vegetable broth,veggie stock,gemüsesuppe,gemüsefond",
            "chicken_stock|V|Hühnerbrühe|Hühnerbrühe|f|chicken stock|chicken stock||||chicken broth,hühnersuppe,hühnerfond",
            "beef_stock|V|Rinderbrühe|Rinderbrühe|f|beef stock|beef stock||||beef broth,rindsuppe,rinderfond",
            "stock|V|Brühe|Brühe|f|stock|stock||||broth,bouillon,suppenwürfel",
            "olive_oil|V|Olivenöl|Olivenöl|n|olive oil|olive oil||||",
            "sesame_oil|V|Sesamöl|Sesamöl|n|sesame oil|sesame oil||||",
            "oil|V|Öl|Öl|n|oil|oil||||vegetable oil,sunflower oil,rapeseed oil,canola oil,neutral oil,rapsöl,sonnenblumenöl,pflanzenöl",
            "balsamic|V|Balsamico|Balsamico|m|balsamic vinegar|balsamic vinegar||||balsamic,aceto balsamico",
            "vinegar|V|Essig|Essig|m|vinegar|vinegar||||apple cider vinegar,cider vinegar,apfelessig,rice vinegar,reisessig",
            "soy_sauce|V|Sojasauce|Sojasauce|f|soy sauce|soy sauce||||soya sauce,sojasoße,sojasosse,soja sauce",
            "honey|V|Honig|Honig|m|honey|honey||||",
            "maple_syrup|V|Ahornsirup|Ahornsirup|m|maple syrup|maple syrup||||",
            "mustard|V|Senf|Senf|m|mustard|mustard||||",
            "peanut_butter|V|Erdnussbutter|Erdnussbutter|f|peanut butter|peanut butter||||erdnussmus",
            "curry_paste|V|Currypaste|Currypaste|f|curry paste|curry paste||||",
            "capers|V|Kapern|Kapern|f|capers|capers||||caper",
            "olives|V|Oliven|Oliven|f|olives|olives||||olive",
            "pickles|V|Essiggurken|Essiggurken|f|pickles|pickles||||pickle,gherkin,essiggurke,gewürzgurke,cornichon",
            "almonds|V|Mandeln|Mandeln|f|almonds|almonds||||almond,mandel",
            "walnuts|V|Walnüsse|Walnüsse|f|walnuts|walnuts||||walnut,walnuss",
            "pine_nuts|V|Pinienkerne|Pinienkerne|m|pine nuts|pine nuts||||pine nut,pinienkern",
            "peanuts|V|Erdnüsse|Erdnüsse|f|peanuts|peanuts||||peanut,erdnuss",

            "bread|B|Brot|Brot|n|bread|bread||||baguette,sourdough",
            "tortilla|B|Tortilla|Tortillas|f|tortilla|tortillas||||wrap",

            "flour|K|Mehl|Mehl|n|flour|flour||||weizenmehl",
            "almond_flour|K|Mandelmehl|Mandelmehl|n|almond flour|almond flour||||ground almonds,gemahlene mandeln",
            "sugar|K|Zucker|Zucker|m|sugar|sugar||||kristallzucker",
            "brown_sugar|K|Brauner Zucker|Brauner Zucker|m|brown sugar|brown sugar||||rohrzucker",
            "powdered_sugar|K|Staubzucker|Staubzucker|m|powdered sugar|powdered sugar||||icing sugar,confectioners,puderzucker",
            "vanilla_sugar|K|Vanillezucker|Vanillezucker|m|vanilla sugar|vanilla sugar||||",
            "vanilla|K|Vanille|Vanille|f|vanilla|vanilla||||",
            "baking_powder|K|Backpulver|Backpulver|n|baking powder|baking powder||||",
            "baking_soda|K|Natron|Natron|n|baking soda|baking soda||||bicarbonate",
            "yeast|K|Germ|Germ|f|yeast|yeast||||hefe",
            "cornstarch|K|Maisstärke|Maisstärke|f|cornstarch|cornstarch||||corn starch,cornflour,speisestärke,maizena",
            "cocoa|K|Kakao|Kakao|m|cocoa|cocoa||||",
            "chocolate|K|Schokolade|Schokolade|f|chocolate|chocolate||||schoko",

            "salt_pepper|S|Salz & Pfeffer|Salz & Pfeffer|n|salt & pepper|salt & pepper||||salt and pepper,salt+pepper,salz und pfeffer,salz, pfeffer,salt, pepper",
            "salt|S|Salz|Salz|n|salt|salt||||meersalz",
            "pepper|S|Pfeffer|Pfeffer|m|pepper|pepper||||black pepper,peppercorn",
            "paprika_powder|S|Paprikapulver|Paprikapulver|n|paprika powder|paprika powder||||smoked paprika,sweet paprika,rosenpaprika,edelsüß,paprika edelsüß,geräuchertes paprika",
            "chili_flakes|S|Chiliflocken|Chiliflocken|f|chili flakes|chili flakes||||chilli flakes,red pepper flakes,crushed red pepper,chili powder,chilipulver,cayenne",
            "cumin|S|Kreuzkümmel|Kreuzkümmel|m|cumin|cumin||||",
            "cinnamon|S|Zimt|Zimt|m|cinnamon|cinnamon||||",
            "nutmeg|S|Muskatnuss|Muskatnuss|f|nutmeg|nutmeg||||muskat",
            "curry_powder|S|Currypulver|Currypulver|n|curry powder|curry powder||||curry",
            "turmeric|S|Kurkuma|Kurkuma|f|turmeric|turmeric||||",
            "garlic_powder|S|Knoblauchpulver|Knoblauchpulver|n|garlic powder|garlic powder||||granulated garlic,knoblauchgranulat",
            "onion_powder|S|Zwiebelpulver|Zwiebelpulver|n|onion powder|onion powder||||",
            "bay_leaf|S|Lorbeerblatt|Lorbeerblätter|n|bay leaf|bay leaves||||lorbeer",

            "wine|G|Wein|Wein|m|wine|wine|||v|white wine,red wine,weißwein,weisswein,rotwein",
    };

    private static final class Syn {
        final String text;
        final Entry entry;
        final Pattern word; // null: substring match

        Syn(String text, Entry entry) {
            this.text = text;
            this.entry = entry;
            this.word = text.length() >= 5 ? null
                    : Pattern.compile("(?:^|[^\\p{L}])" + Pattern.quote(text) + "(?:s|es|e|n|en|er)?(?:$|[^\\p{L}])");
        }

        int indexIn(String n) {
            if (word == null) return n.indexOf(text);
            Matcher m = word.matcher(n);
            return m.find() ? m.start() : -1;
        }
    }

    private static final List<Syn> SYNONYMS = new ArrayList<>();
    private static final Map<String, Entry> BY_KEY = new HashMap<>();

    static {
        for (String line : TABLE) {
            String[] f = Arrays.copyOf(line.split("\\|", -1), 11);
            for (int i = 0; i < f.length; i++) if (f[i] == null) f[i] = "";
            Entry e = new Entry(f);
            BY_KEY.put(e.key, e);
            Set<String> words = new LinkedHashSet<>();
            words.add(lower(e.enSg));
            words.add(lower(e.enPl));
            words.add(lower(e.deSg));
            words.add(lower(e.dePl));
            for (String s : f[10].split(",")) if (!s.trim().isEmpty()) words.add(lower(s.trim()));
            for (String w : words) SYNONYMS.add(new Syn(w, e));
        }
        // Longest synonym first, so "Knoblauchpulver" beats "Knoblauch" and "Frischkäse" beats "Käse".
        Collections.sort(SYNONYMS, (a, b) -> b.text.length() - a.text.length());
    }

    private Canon() {}

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static Aisle aisleOf(String c) {
        switch (c) {
            case "P": return Aisle.PRODUCE;
            case "D": return Aisle.DAIRY;
            case "M": return Aisle.MEAT;
            case "B": return Aisle.BAKERY;
            case "V": return Aisle.PANTRY;
            case "K": return Aisle.BAKING;
            case "S": return Aisle.SPICES;
            case "G": return Aisle.DRINKS;
            default: return Aisle.OTHER;
        }
    }

    public static Entry byKey(String key) {
        return BY_KEY.get(key);
    }

    // ------------------------------------------------------------------ matching

    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern JUICE = Pattern.compile("juice|saft|squeez|gepresst|ausgepresst");
    private static final Pattern ZEST = Pattern.compile("zest|abrieb|zeste|schale|grated rind");
    private static final String[][] COLORS = {
            {"red", "(?:^|[^\\p{L}])(?:red|rot(?:e|er|es|en)?|rotwein|rotkraut|rotkohl)"},
            {"yellow", "(?:^|[^\\p{L}])(?:yellow|gelb(?:e|er|es|en)?)"},
            {"white", "(?:^|[^\\p{L}])(?:white|wei(?:ß|ss)(?:e|er|es|en)?|weißwein|weisswein|weißkraut|weisskraut|weißkohl)"},
            {"green", "(?:^|[^\\p{L}])(?:green|grün(?:e|er|es|en)?)"},
    };
    private static final Pattern[] COLOR_PATTERNS = new Pattern[COLORS.length];
    static {
        for (int i = 0; i < COLORS.length; i++) COLOR_PATTERNS[i] = Pattern.compile(COLORS[i][1]);
    }

    /** Recognise an ingredient name (any language). [unit] helps tell paprika powder from bell peppers. */
    public static Match match(String name, String unit) {
        if (name == null) return null;
        String n = lower(PARENS.matcher(name).replaceAll(" ")).replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) return null;
        Syn hit = null;
        for (Syn s : SYNONYMS) {
            if (s.indexIn(n) >= 0) {
                hit = s;
                break;
            }
        }
        if (hit == null) return null;
        Entry e = hit.entry;

        // "1 tsp paprika" is the spice, "1 Paprika" the vegetable.
        if (e.key.equals("bell_pepper") && hit.text.equals("paprika") && unit != null
                && (unit.equals("tsp") || unit.equals("tbsp") || unit.equals("pinch") || unit.equals("g"))) {
            e = BY_KEY.get("paprika_powder");
        }

        String variant = "";
        if (e.variants) {
            for (int i = 0; i < COLORS.length; i++) {
                if (COLOR_PATTERNS[i].matcher(n).find()) {
                    variant = COLORS[i][0];
                    break;
                }
            }
        }
        String form = "";
        if (e.fruitForms) {
            if (JUICE.matcher(n).find()) form = "juice";
            else if (ZEST.matcher(n).find()) form = "zest";
        }
        return new Match(e, variant, form);
    }

    /** Vegetable icon for the earliest vegetable mentioned in a text (a recipe title), or "". */
    public static String veggieInText(String text) {
        if (text == null) return "";
        String n = lower(text);
        int bestPos = Integer.MAX_VALUE;
        String best = "";
        for (Syn s : SYNONYMS) {
            if (s.entry.veggie.isEmpty()) continue;
            int i = s.indexIn(n);
            if (i >= 0 && i < bestPos) { // SYNONYMS is longest-first, so ties keep the longer word
                bestPos = i;
                best = s.entry.veggie;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ naming

    private static final Map<String, String[]> FORM_NAMES = new HashMap<>();
    static {
        // key -> {en juice, de juice, en zest, de zest}
        FORM_NAMES.put("lemon", new String[]{"lemon juice", "Zitronensaft", "lemon zest", "Zitronenabrieb"});
        FORM_NAMES.put("lime", new String[]{"lime juice", "Limettensaft", "lime zest", "Limettenabrieb"});
        FORM_NAMES.put("orange", new String[]{"orange juice", "Orangensaft", "orange zest", "Orangenabrieb"});
    }

    private static final Map<String, String[]> COLOR_WORDS = new HashMap<>();
    static {
        COLOR_WORDS.put("red", new String[]{"red", "rot"});
        COLOR_WORDS.put("yellow", new String[]{"yellow", "gelb"});
        COLOR_WORDS.put("white", new String[]{"white", "weiß"});
        COLOR_WORDS.put("green", new String[]{"green", "grün"});
    }

    /** "rote Zwiebeln", "red onions", "Zitronensaft" … */
    public static String name(Match m, boolean plural, boolean german) {
        Entry e = m.entry;
        if (!m.form.isEmpty() && FORM_NAMES.containsKey(e.key)) {
            String[] f = FORM_NAMES.get(e.key);
            return cap(m.form.equals("juice") ? (german ? f[1] : f[0]) : (german ? f[3] : f[2]));
        }
        String base = german ? (plural ? e.dePl : e.deSg) : (plural ? e.enPl : e.enSg);
        if (m.variant.isEmpty()) return cap(base);
        String[] cw = COLOR_WORDS.get(m.variant);
        if (!german) return cap(cw[0] + " " + base);
        String ending = plural ? "e" : (e.gender == 'm' ? "er" : e.gender == 'n' ? "es" : "e");
        return cap(cw[1] + ending) + " " + base;
    }

    /** Colour words of a variant in the chosen language, for notes like "rot, gelb". */
    public static String variantWord(String variant, boolean german) {
        String[] cw = COLOR_WORDS.get(variant);
        return cw == null ? variant : cw[german ? 1 : 0];
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ normalising amounts

    private static final Pattern FRUIT_COUNT = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?|½|one|two|a|an|half|eine?r?|zwei|halben?)\\s+(?:\\p{L}+\\s+)?(?:lemon|lime|orange|zitrone|limette|orange)");

    /**
     * Brings an ingredient into the form the shopping list works with: "2 Knoblauchzehen" counts
     * cloves; "juice of 1 lemon" gets its amount from the text.
     */
    public static Ingredient normalize(Ingredient i) {
        Match m = match(i.name, i.unit);
        if (m == null) return i;
        String n = lower(i.name);
        if (m.entry.key.equals("garlic") && i.hasQty() && i.unit.isEmpty()
                && (n.contains("zehe") || n.contains("clove"))) {
            return new Ingredient(i.qty, i.qtyMax, "clove", i.name, i.note);
        }
        if (!m.form.isEmpty() && !i.hasQty()) {
            Matcher fm = FRUIT_COUNT.matcher(n);
            if (fm.find()) {
                double q = wordNumber(fm.group(1));
                if (!Double.isNaN(q)) return new Ingredient(q, Double.NaN, "", i.name, i.note);
            }
        }
        return i;
    }

    private static double wordNumber(String w) {
        switch (w) {
            case "a": case "an": case "one": case "ein": case "eine": case "einer": return 1;
            case "two": case "zwei": return 2;
            case "half": case "halbe": case "halben": case "½": return 0.5;
            default: return Quantities.parse(w);
        }
    }

    /**
     * How many whole fruits a juice/zest amount corresponds to (1 lemon ≈ 3 tbsp juice or 1 tbsp zest).
     * Returns NaN if the unit can't be converted.
     */
    public static double fruitsFor(Ingredient i, Match m) {
        boolean juice = m.form.equals("juice");
        if (!i.hasQty()) return juice ? 0.5 : 1;
        double perFruitMl = juice ? 45 : 15; // one fruit gives ~45 ml juice, ~15 ml (1 tbsp) zest
        switch (i.unit) {
            case "": return i.qty;               // "juice of 2 lemons" / "1 Zitrone, Saft"
            case "dash": case "pinch": return i.qty * (juice ? 0.25 : 0.1);
            default: {
                Units.Unit u = Units.byKey(i.unit);
                if (u == null || u.family != Units.Family.VOLUME) return Double.NaN;
                return i.qty * u.toBase / perFruitMl;
            }
        }
    }
}
