package com.example.pinspire.data;

/** Static catalog of browse categories and Daily Spark themes. */
public final class Discovery {
    public static final class Category {
        public final String label, query, pixabayCategory;
        Category(String label, String query, String pixabayCategory) {
            this.label = label; this.query = query; this.pixabayCategory = pixabayCategory;
        }
    }

    public static final class Spark {
        public final String title, query, color, prompt;
        Spark(String title, String query, String color, String prompt) {
            this.title = title; this.query = query; this.color = color; this.prompt = prompt;
        }
    }

    public static final Category[] CATEGORIES = {
            new Category("Architecture", "architecture", "buildings"),
            new Category("Interiors", "interior design", ""),
            new Category("Fashion", "fashion", "fashion"),
            new Category("Art", "art", ""),
            new Category("Photography", "photography", ""),
            new Category("Nature", "nature landscape", "nature"),
            new Category("Food", "food", "food"),
            new Category("Design", "graphic design", ""),
            new Category("Travel", "travel", "travel"),
            new Category("Minimal", "minimalism", ""),
    };

    /** One theme per day; everyone gets the same spark on the same date. */
    public static final Spark[] SPARKS = {
            new Spark("Terracotta & linen", "terracotta interior", "orange", "Warm clay tones and soft textures"),
            new Spark("Midnight neon", "neon city night", "blue", "Glowing signs after dark"),
            new Spark("Matcha morning", "matcha tea", "green", "Slow green rituals"),
            new Spark("Brutalist calm", "brutalist architecture", "gray", "Raw concrete, quiet light"),
            new Spark("Golden hour", "golden hour", "yellow", "Everything bathed in late sun"),
            new Spark("Soft lilac", "lavender field", "lilac", "Dreamy purple haze"),
            new Spark("Ocean glass", "ocean waves", "turquoise", "Clear water, cool light"),
            new Spark("Cherry blossom", "cherry blossom", "pink", "Fleeting spring petals"),
            new Spark("Monochrome street", "street photography", "black", "Shadows, contrast, motion"),
            new Spark("Rustic bread", "sourdough bread", "brown", "Crusts, flour and wood"),
            new Spark("Desert modern", "desert house", "orange", "Sun-baked minimal homes"),
            new Spark("Paper & ink", "calligraphy", "black", "Handmade marks"),
            new Spark("Snow quiet", "snow forest", "white", "Hush of winter"),
            new Spark("Retro diner", "retro diner", "red", "Chrome, vinyl and neon"),
            new Spark("Jungle room", "tropical plants interior", "green", "Bring the outdoors in"),
            new Spark("Coastal blue", "coastal house", "blue", "Breezy seaside living"),
            new Spark("Autumn layers", "autumn fashion", "brown", "Knits, wool and rust"),
            new Spark("Pastel bakery", "pastel dessert", "pink", "Sugar-coated colors"),
            new Spark("Industrial loft", "industrial loft", "gray", "Steel, brick and big windows"),
            new Spark("Citrus pop", "citrus fruit", "yellow", "Zesty brights"),
            new Spark("Night sky", "milky way", "black", "Stars over dark horizons"),
    };

    private Discovery() {}

    public static Spark todaysSpark() {
        return SPARKS[(int) (PinRepository.today() % SPARKS.length)];
    }

    public static Category category(String label) {
        for (Category c : CATEGORIES) if (c.label.equals(label)) return c;
        return null;
    }
}
