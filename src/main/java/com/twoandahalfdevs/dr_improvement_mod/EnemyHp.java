package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * DungeonRealms shows mob health as a row of coloured "|" characters in the mob's name, e.g.
 * "§a||||||||||||§7|||||||| §f[Lv 90]". This rewrites that name in the style picked in the settings.
 * Anything that doesn't look like a health bar is left untouched.
 */
public final class EnemyHp {
    private EnemyHp() {}

    public enum Style {
        MODERN("WoW style"), ORIGINAL("Original"), BAR("Bar"), BAR_PERCENT("Bar + %"), PERCENT("% only"), HEARTS("Hearts");

        public final String label;

        Style(String label) {
            this.label = label;
        }
    }

    /** One run of text with the colour it is drawn in. */
    private record Run(String text, TextColor color) {}

    /** Parsed bar: filled pipes, total pipes, text before and after the bar. */
    record Parsed(int filled, int total, String before, String after) {
        double fraction() {
            return total == 0 ? 0 : filled / (double) total;
        }
    }

    public static Component transform(Component name) {
        if (name == null) return null;
        Style style = DrConfig.enemyHpStyle();
        if (style == Style.ORIGINAL || style == Style.MODERN) return name; // MODERN is drawn by MobHp
        Parsed p = parse(name);
        if (p == null) return name;
        return render(p, style, DrConfig.data.enemyHpBarLength, DrConfig.data.enemyHpShowLevel);
    }

    static Parsed parse(Component name) {
        List<Run> runs = runs(name);
        StringBuilder before = new StringBuilder(), after = new StringBuilder();
        int filled = 0, total = 0;
        TextColor fillColor = null;
        boolean inBar = false, barDone = false, fillDone = false;
        for (Run r : runs) {
            for (int i = 0; i < r.text.length(); i++) {
                char c = r.text.charAt(i);
                if (c == '|' && !barDone) {
                    if (!inBar) {
                        inBar = true;
                        fillColor = r.color;
                    }
                    total++;
                    if (!fillDone && same(r.color, fillColor)) filled++;
                    else fillDone = true;
                } else {
                    if (inBar && c != '|') barDone = true;
                    (inBar ? after : before).append(c);
                }
            }
        }
        if (total < 8) return null; // not a health bar
        // DR caps the bar with "▌" on both sides
        String b = before.toString().replace("\u258c", "").trim(), a = after.toString().replace("\u258c", "").trim();
        return new Parsed(filled, total, b, a);
    }

    private static boolean same(TextColor a, TextColor b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Flatten a component into coloured runs, honouring legacy "§x" codes inside the text as well. */
    private static List<Run> runs(Component name) {
        List<Run> out = new ArrayList<>();
        name.visit((st, text) -> {
            TextColor color = st.getColor();
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '§' && i + 1 < text.length()) {
                    ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                    i++;
                    if (f != null && (f.isColor() || f == ChatFormatting.RESET)) {
                        if (!cur.isEmpty()) out.add(new Run(cur.toString(), color));
                        cur.setLength(0);
                        color = f == ChatFormatting.RESET ? null : TextColor.fromLegacyFormat(f);
                    }
                    continue;
                }
                cur.append(c);
            }
            if (!cur.isEmpty()) out.add(new Run(cur.toString(), color));
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    static ChatFormatting colorFor(double f) {
        if (f > 0.5) return ChatFormatting.GREEN;
        if (f > 0.25) return ChatFormatting.YELLOW;
        if (f > 0.1) return ChatFormatting.GOLD;
        return ChatFormatting.RED;
    }

    static Component render(Parsed p, Style style, int length, boolean showLevel) {
        double f = p.fraction();
        ChatFormatting col = colorFor(f);
        int pct = (int) Math.round(f * 100);
        MutableComponent out = Component.empty();
        if (!p.before().isEmpty()) out.append(Component.literal(p.before() + " "));
        switch (style) {
            case BAR, BAR_PERCENT -> {
                int n = Math.max(4, Math.min(40, length));
                int full = (int) Math.round(f * n);
                if (f > 0 && full == 0) full = 1;
                out.append(Component.literal("█".repeat(full)).withStyle(col));
                out.append(Component.literal("█".repeat(n - full)).withStyle(ChatFormatting.DARK_GRAY));
                if (style == Style.BAR_PERCENT) out.append(Component.literal(" " + pct + "%").withStyle(col));
            }
            case PERCENT -> out.append(Component.literal(pct + "%").withStyle(col));
            case HEARTS -> {
                int hearts = 10;
                int full = (int) Math.round(f * hearts);
                if (f > 0 && full == 0) full = 1;
                out.append(Component.literal("❤".repeat(full)).withStyle(ChatFormatting.RED));
                out.append(Component.literal("❤".repeat(hearts - full)).withStyle(ChatFormatting.DARK_GRAY));
            }
            default -> { }
        }
        if (showLevel && !p.after().isEmpty()) out.append(Component.literal(" " + p.after()).withStyle(ChatFormatting.WHITE));
        return out;
    }

    static String describe(Parsed p) {
        return String.format(Locale.ROOT, "%d/%d '%s' '%s'", p.filled(), p.total(), p.before(), p.after());
    }
}
