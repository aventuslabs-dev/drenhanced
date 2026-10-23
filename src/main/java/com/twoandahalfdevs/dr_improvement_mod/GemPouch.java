package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gem pouch fill shown on the slot itself (inventory, hotbar, bank, any container), no hovering: a bar along
 * the bottom that fills as the pouch does, and a gold frame + "FULL" once it's full, so you know to bank it.
 */
public final class GemPouch {
    private GemPouch() {}

    public record Fill(int gems, int max) {
        float frac() {
            return max > 0 ? Math.min(1f, gems / (float) max) : 0f;
        }

        boolean full() {
            return max > 0 && gems >= max;
        }
    }

    // "1,234/2,000", "1234 / 2000g", "Gems: 120 / 300"
    private static final Pattern PAIR = Pattern.compile("([\\d,]+)\\s*g?\\s*/\\s*([\\d,]+)\\s*g?");
    // capacity: "holds 300g", "Capacity: 300", "Max: 300", "can hold up to 300"
    private static final Pattern CAP = Pattern.compile("(?:HOLDS?|CAPACITY|MAX(?:IMUM)?|UP TO|LIMIT)\\D{0,12}?([\\d,]+)");
    // current: "125g", "125 gems", "Gems: 125", "Contains 125"
    private static final Pattern CUR = Pattern.compile("([\\d,]+)\\s*(?:G\\b|GEMS?\\b)|(?:GEMS?|CONTAINS|STORED|BALANCE)\\D{0,6}?([\\d,]+)");
    /** Fallback capacity by size word, if DR's text doesn't say. */
    private static final String[][] SIZES = {{"GIGANTIC", "300"}, {"INSANE", "300"}, {"HUGE", "300"}, {"LARGE", "200"},
        {"MEDIUM", "150"}, {"SMALL", "100"}};

    private static final Set<String> logged = new HashSet<>();

    public static boolean isPouch(ItemStack s) {
        return !s.isEmpty() && s.has(DataComponents.CUSTOM_NAME) && s.getHoverName().getString().contains("Gem Pouch");
    }

    /** Fill of a gem pouch, or null if it isn't one / the numbers can't be read. */
    public static Fill read(ItemStack s) {
        if (!isPouch(s)) return null;
        String name = SplitChat.strip(s.getHoverName().getString());
        StringBuilder lore = new StringBuilder();
        ItemLore l = s.get(DataComponents.LORE);
        if (l != null) for (Component c : l.lines()) lore.append(SplitChat.strip(c.getString())).append('\n');
        if (logged.add(name + "|" + lore)) { // one line per pouch text, so DR's exact wording can be checked in the log
            org.slf4j.LoggerFactory.getLogger("dr_improvement_mod").info("DRIM pouch: {} || {}", name, lore.toString().replace('\n', '|'));
        }
        return parse(name, lore.toString());
    }

    /** Pure, for the self-test. */
    static Fill parse(String name, String lore) {
        String all = (name + "\n" + lore).toUpperCase(Locale.ROOT);
        Matcher m = PAIR.matcher(all);
        if (m.find()) return new Fill(num(m.group(1)), num(m.group(2)));
        int max = -1;
        m = CAP.matcher(lore.toUpperCase(Locale.ROOT));
        if (m.find()) max = num(m.group(1));
        if (max <= 0) {
            String n = name.toUpperCase(Locale.ROOT);
            for (String[] sz : SIZES) if (n.contains(sz[0])) {
                max = Integer.parseInt(sz[1]);
                break;
            }
        }
        // the gem count: DR puts it in the name ("Small Gem Pouch 45g"); else the lore, skipping the capacity
        int cur = -1;
        String[] srcs = {name.toUpperCase(Locale.ROOT), lore.toUpperCase(Locale.ROOT)};
        for (int i = 0; i < srcs.length && cur < 0; i++) {
            m = CUR.matcher(srcs[i]);
            while (m.find()) {
                int v = num(m.group(1) != null ? m.group(1) : m.group(2));
                if (i == 1 && v == max) continue; // that's the capacity line
                cur = v;
                break;
            }
        }
        if (cur < 0) cur = 0; // no count shown: empty
        return max > 0 ? new Fill(cur, max) : null;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ drawing

    /** GuiGraphics#renderItemDecorations TAIL: draw over the 16x16 item at x, y. */
    public static void decorate(GuiGraphics g, ItemStack stack, int x, int y) {
        if (!DrConfig.data.pouchIndicator) return;
        Fill f = read(stack);
        if (f == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // above the item and its count
        float frac = f.frac();
        boolean full = f.full();
        // bar along the bottom, like durability: dark track, fill grows left to right
        int x0 = x + 2, x1 = x + 15, y0 = y + 13;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y0 + 2, 0xFF000000);
        int w = Math.round((x1 - x0) * frac);
        if (frac > 0 && w == 0) w = 1;
        int col = full ? 0xFFFFC21A : frac >= 0.75f ? 0xFFFFE066 : 0xFF3FD06B;
        if (w > 0) g.fill(x0, y0, x0 + w, y0 + 1, col);
        if (full) {
            // gold frame around the slot + "FULL"
            int gold = 0xFFFFC21A;
            g.fill(x - 1, y - 1, x + 17, y, gold);
            g.fill(x - 1, y + 16, x + 17, y + 17, gold);
            g.fill(x - 1, y, x, y + 16, gold);
            g.fill(x + 16, y, x + 17, y + 16, gold);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            g.pose().pushPose();
            g.pose().translate(x + 1, y + 1, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(font, "FULL", 0, 0, gold, true);
            g.pose().popPose();
        }
        g.pose().popPose();
    }
}
