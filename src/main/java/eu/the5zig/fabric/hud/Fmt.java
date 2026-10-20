package eu.the5zig.fabric.hud;

import eu.the5zig.fabric.Config;

/** 5zig-style text formatting: "§7§6Label§7> §fvalue" with the colours/brackets from the config. */
public final class Fmt {
    private Fmt() {}

    public static final char S = '§';

    public static String code(char c) {
        return "" + S + c;
    }

    public static String main() {
        return code(Config.get().colorMain);
    }

    public static String prefixColor() {
        return code(Config.get().colorPrefix);
    }

    public static String prefix(String label) {
        Config c = Config.get();
        String b = code(c.colorBrackets);
        return b + c.formattingBrackets.first + code(c.colorPrefix) + label + b + c.formattingBrackets.last + " ";
    }

    public static String line(String label, boolean showPrefix, String value) {
        return (showPrefix && label != null && !label.isEmpty() ? prefix(label) : "") + main() + value;
    }

    /** "[Z+]" style hint used by the direction item; always round brackets like the original. */
    public static String bracketed(String inner) {
        Config c = Config.get();
        String b = code(c.colorBrackets);
        return b + "(" + code(c.colorPrefix) + inner + b + ")";
    }

    /** Original durability colour ramp (dark green ≥95%, green ≥80%, yellow ≥50%, red ≥10%, dark red). */
    public static String durabilityColor(float frac) {
        if (!Config.get().coloredEquipmentDurability) return main();
        if (frac >= 0.95f) return code('2');
        if (frac >= 0.8f) return code('a');
        if (frac >= 0.5f) return code('e');
        if (frac >= 0.1f) return code('c');
        return code('4');
    }

    /** Original potion colour ramp by remaining ticks. */
    public static String potionColor(int ticks) {
        if (ticks >= 20 * 60) return code('2');
        if (ticks >= 20 * 30) return code('a');
        if (ticks >= 20 * 10) return code('e');
        if (ticks >= 20 * 5) return code('c');
        return code('4');
    }

    public static final char[] COLORS = "0123456789abcdef".toCharArray();

    public static char nextColor(char c) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == c) return COLORS[(i + 1) % COLORS.length];
        return 'f';
    }

    private static final String[] COLOR_NAMES = {"Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red", "Dark Purple",
            "Gold", "Gray", "Dark Gray", "Blue", "Green", "Aqua", "Red", "Light Purple", "Yellow", "White"};

    public static String colorName(char c) {
        int i = Character.digit(c, 16);
        return i < 0 ? "?" : code(c) + COLOR_NAMES[i];
    }
}
