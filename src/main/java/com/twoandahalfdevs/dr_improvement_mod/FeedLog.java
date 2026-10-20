package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WoW-style Combat Log that replaces the right-hand system chat: a framed panel with tabs, timestamps and
 * category icons. DR's raw lines are rewritten into short entries; heals are grouped per second; crit/proc
 * marker lines are folded into the hit they belong to; separators and blank lines are dropped.
 */
public final class FeedLog {
    private FeedLog() {}

    public enum Cat {
        HIT("⚔", 0xFFFFD100), TAKEN("✖", 0xFFFF4D4D), HEAL("✚", 0xFF4DFF6A),
        EXP("✦", 0xFFC27DFF), GEMS("◆", 0xFF55FF55), LOOT("✪", 0xFFFFAA00), SYSTEM("•", 0xFF9AA4B1);

        final String icon;
        final int color;

        Cat(String icon, int color) {
            this.icon = icon;
            this.color = color;
        }
    }

    public enum Tab {
        ALL("All"), COMBAT("Combat"), LOOT("Loot"), SYSTEM("System");
        final String label;

        Tab(String label) {
            this.label = label;
        }

        boolean shows(Cat c) {
            return switch (this) {
                case ALL -> true;
                case COMBAT -> c == Cat.HIT || c == Cat.TAKEN || c == Cat.HEAL;
                case LOOT -> c == Cat.LOOT || c == Cat.GEMS || c == Cat.EXP;
                case SYSTEM -> c == Cat.SYSTEM;
            };
        }
    }

    static final class Entry {
        final Cat cat;
        long time;
        Component main;
        String dim;
        int count = 1;
        String key;                 // grouping key (heals per second, repeated system lines)
        final Map<String, Integer> parts = new LinkedHashMap<>(); // heal sources
        int total;

        Entry(Cat cat, long time, Component main, String dim) {
            this.cat = cat;
            this.time = time;
            this.main = main;
            this.dim = dim;
        }
    }

    private static final List<Entry> entries = new ArrayList<>(); // newest last
    private static final int MAX = 400;
    private static Tab tab = Tab.ALL;
    private static int scroll; // rows scrolled up from the bottom (chat open)

    private static final Pattern OUT = Pattern.compile("^(\\d+) \\S*DMG(?: \\(([^)]*)\\))? -> (.+?) \\[(\\d+) HP]$");
    private static final Pattern IN = Pattern.compile("^-(\\d+) \\S*HP \\((.+?)\\)(?: \\[(\\d+) HP])?$");
    private static final Pattern HEAL = Pattern.compile("^\\+(\\d+) HP \\((.+?)\\)(?: \\[(\\d+) Overhealth])?$");
    private static final Pattern EXP = Pattern.compile("^\\+ ?([\\d,]+) EXP(?: \\((.+?)\\))?(?: \\[(\\d+)/(\\d+)])?$");
    private static final Pattern GEMS = Pattern.compile("^([\\d,]+)G$");
    private static final Pattern MARKER = Pattern.compile("^\\*\\s*(.+?)\\s*\\*$");
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    // marker lines ("* CRIT *", "* SHATTERED *") arrive a few ms before their hit
    private static final List<String> pendingTags = new ArrayList<>();
    private static long pendingAt;

    public static boolean enabled() {
        return "FEED".equals(DrConfig.data.rightChatStyle);
    }

    // ------------------------------------------------------------------ input

    /** Everything routed to the right-hand box comes through here. */
    public static void add(Component msg) {
        long now = System.currentTimeMillis();
        String txt = SplitChat.strip(msg.getString()).trim();
        if (txt.isEmpty() || isDecoration(txt)) return;

        Matcher mk = MARKER.matcher(txt);
        if (mk.find()) {
            if (now - pendingAt > 300) pendingTags.clear();
            pendingTags.add(title(mk.group(1)));
            pendingAt = now;
            return;
        }
        Matcher m = OUT.matcher(txt);
        if (m.find()) {
            boolean crit = false;
            List<String> tags = new ArrayList<>();
            if (now - pendingAt < 300) {
                for (String t : pendingTags) {
                    if (t.equals("Crit")) crit = true;
                    else tags.add(t.startsWith("Target ") ? t.substring(7) : t);
                }
            }
            pendingTags.clear();
            if (m.group(2) != null) tags.add(m.group(2));
            MutableComponent c = Component.literal(NUM.format(Long.parseLong(m.group(1))))
                .withStyle(crit ? Style.EMPTY.withColor(0xFFC02A).withBold(true) : Style.EMPTY.withColor(0xFFFFFF));
            if (crit) c.append(Component.literal(" CRIT").withStyle(Style.EMPTY.withColor(0xFF8A1F).withBold(true)));
            c.append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal(m.group(3)).withStyle(ChatFormatting.YELLOW));
            for (String t : tags) c.append(Component.literal(" " + t).withStyle(Style.EMPTY.withColor(0xFFE9A0).withItalic(true)));
            push(new Entry(Cat.HIT, now, c, NUM.format(Long.parseLong(m.group(4))) + " HP"));
            return;
        }
        pendingTags.clear();
        m = IN.matcher(txt);
        if (m.find()) {
            MutableComponent c = Component.literal("-" + NUM.format(Long.parseLong(m.group(1)))).withStyle(Style.EMPTY.withColor(0xFF5A5A));
            c.append(Component.literal(" from ").withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal(m.group(2)).withStyle(ChatFormatting.GRAY));
            push(new Entry(Cat.TAKEN, now, c, m.group(3) == null ? null : NUM.format(Long.parseLong(m.group(3))) + " HP"));
            return;
        }
        m = HEAL.matcher(txt);
        if (m.find()) {
            heal(now, m.group(2), Integer.parseInt(m.group(1)));
            return;
        }
        m = EXP.matcher(txt);
        if (m.find()) {
            MutableComponent c = Component.literal("+" + m.group(1) + " EXP").withStyle(Style.EMPTY.withColor(0xC27DFF));
            if (m.group(2) != null) c.append(Component.literal(" " + title(m.group(2))).withStyle(Style.EMPTY.withColor(0x8F6BBF).withItalic(true)));
            String dim = m.group(3) == null ? null : NUM.format(Long.parseLong(m.group(3))) + " / " + NUM.format(Long.parseLong(m.group(4)));
            push(new Entry(Cat.EXP, now, c, dim));
            return;
        }
        m = GEMS.matcher(txt);
        if (m.find()) {
            push(new Entry(Cat.GEMS, now, Component.literal("+" + m.group(1) + " gems").withStyle(ChatFormatting.GREEN), null));
            return;
        }
        if (txt.contains(" dropped: ") || txt.contains("portal shards") || txt.contains("Loot Buff resulted")) {
            push(new Entry(Cat.LOOT, now, msg.copy(), null)); // keeps DR's hover (e.g. the item behind "SHOW")
            return;
        }
        // anything else: system line, folded when repeated
        Entry last = lastOf(Cat.SYSTEM);
        if (last != null && txt.equals(last.key) && now - last.time < 10_000) {
            last.count++;
            last.time = now;
            moveToEnd(last);
            return;
        }
        Entry e = new Entry(Cat.SYSTEM, now, msg.copy(), null);
        e.key = txt;
        push(e);
    }

    private static void heal(long now, String source, int amount) {
        if (DrConfig.data.feedGroupHeals) {
            Entry last = lastOf(Cat.HEAL);
            if (last != null && now - last.time < 1000) {
                last.total += amount;
                last.parts.merge(source, amount, Integer::sum);
                last.time = now;
                rebuildHeal(last);
                moveToEnd(last);
                return;
            }
        }
        Entry e = new Entry(Cat.HEAL, now, null, null);
        e.total = amount;
        e.parts.put(source, amount);
        rebuildHeal(e);
        push(e);
    }

    private static void rebuildHeal(Entry e) {
        e.main = Component.literal("+" + NUM.format(e.total)).withStyle(Style.EMPTY.withColor(0x4DFF6A))
            .append(Component.literal(" healed").withStyle(ChatFormatting.DARK_GREEN));
        StringBuilder b = new StringBuilder();
        for (var p : e.parts.entrySet()) {
            if (b.length() > 0) b.append(" · ");
            b.append(p.getKey()).append(' ').append(NUM.format(p.getValue()));
        }
        e.dim = b.toString();
    }

    private static boolean isDecoration(String t) {
        // DR's fancy separators: no letters or digits at all
        return t.chars().noneMatch(Character::isLetterOrDigit);
    }

    private static String title(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private static Entry lastOf(Cat c) {
        for (int i = entries.size() - 1; i >= Math.max(0, entries.size() - 6); i--) {
            if (entries.get(i).cat == c) return entries.get(i);
        }
        return null;
    }

    private static void moveToEnd(Entry e) {
        entries.remove(e);
        entries.add(e);
    }

    private static void push(Entry e) {
        entries.add(e);
        if (entries.size() > MAX) entries.remove(0);
        if (scroll > 0) scroll++; // keep the reader's place while scrolled up
    }

    public static void clear() {
        entries.clear();
        scroll = 0;
    }

    // ------------------------------------------------------------------ layout + drawing

    private static final int HEADER = 13;
    private static final int GOLD = 0xFFC8A040;
    private static final int GOLD_DIM = 0xFF6E5420;

    /** Panel rectangle in GUI pixels: {x0, y0, x1, y1}. */
    static int[] rect(int guiW, int guiH, boolean focused) {
        var c = SplitChat.right();
        DrConfig.ChatBox b = DrConfig.data.rightChat;
        int w = c.getWidth() + 8;
        int h = (int) Math.round((focused ? b.heightFocused : b.heightUnfocused) * b.scale) + HEADER + 4;
        int x1 = guiW - 2 - b.x;
        int x0 = Math.max(0, x1 - w);
        int y1 = guiH - 38 - b.y;
        int y0 = Math.max(0, y1 - h);
        return new int[]{x0, y0, x1, y1};
    }

    private static int lineH(double scale) {
        return Math.max(6, (int) Math.round(10 * scale));
    }

    /** One drawn row; dim = right-aligned secondary text when it fits on the same row. */
    private record Line(FormattedCharSequence seq, FormattedCharSequence dim, Entry e, float alpha, boolean first) {}

    public static void render(GuiGraphics g, boolean focused, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        DrConfig.ChatBox box = DrConfig.data.rightChat;
        double scale = box.scale;
        long now = System.currentTimeMillis();
        int[] r = rect(g.guiWidth(), g.guiHeight(), focused);
        int x0 = r[0], y0 = r[1], x1 = r[2], y1 = r[3];

        // how "present" the window is: always when open, else fades with the newest entry
        long newest = 0;
        for (Entry e : entries) if (tab.shows(e.cat)) newest = Math.max(newest, e.time);
        float presence = focused ? 1f : (float) Mth.clamp(1.0 - (now - newest - 8000) / 2000.0, 0, 1);
        if (presence <= 0.02f) return;

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        float bg = (float) box.background;
        int a = (int) (presence * 255);
        // frame: dark body, gold hairline, header strip
        g.fill(x0, y0, x1, y1, argb((int) (a * Math.max(0.62f, bg)), 0x0B0D12));
        g.fill(x0, y0, x1, y0 + HEADER, argb((int) (a * 0.9f), 0x1A1712));
        g.fill(x0, y0 + HEADER, x1, y0 + HEADER + 1, argb(a, GOLD_DIM));
        outline(g, x0, y0, x1, y1, argb(a, GOLD));
        // title + tabs
        g.drawString(font, "Combat Log", x0 + 5, y0 + 3, argb(a, 0xFFD100), true);
        int tx = x1 - 4;
        Tab[] tabs = Tab.values();
        for (int i = tabs.length - 1; i >= 0; i--) {
            Tab t = tabs[i];
            int w = font.width(t.label);
            tx -= w;
            boolean active = t == tab;
            boolean hover = focused && mouseX >= tx - 2 && mouseX <= tx + w + 2 && mouseY >= y0 && mouseY <= y0 + HEADER;
            int col = active ? 0xFFFFFF : hover ? 0xE0D0A0 : 0x8A8070;
            g.drawString(font, t.label, tx, y0 + 3, argb(a, col), false);
            if (active) g.fill(tx, y0 + HEADER - 2, tx + w, y0 + HEADER - 1, argb(a, GOLD));
            tabX[i] = tx;
            tabW[i] = w;
            tx -= 8;
        }
        tabY = y0;

        // rows, newest at the bottom
        int lh = lineH(scale);
        int innerW = (int) ((x1 - x0 - 8) / scale);
        List<Line> lines = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (!tab.shows(e.cat)) continue;
            float alpha = focused ? 1f : (float) Mth.clamp(1.0 - (now - e.time - 8000) / 2000.0, 0, 1);
            if (alpha <= 0.02f && !focused) continue;
            Component left = compose(e, false);
            Component dim = e.dim == null || e.dim.isEmpty() ? null
                : Component.literal(e.dim).withStyle(Style.EMPTY.withColor(0x6B7280));
            if (dim != null && font.width(left) + 8 + font.width(dim) <= innerW) {
                lines.add(new Line(left.getVisualOrderText(), dim.getVisualOrderText(), e, alpha, true));
            } else {
                List<FormattedCharSequence> wrapped = font.split(compose(e, true), innerW);
                for (int k = wrapped.size() - 1; k >= 0; k--) lines.add(new Line(wrapped.get(k), null, e, alpha, k == 0));
            }
        }
        int visible = Math.max(1, (y1 - (y0 + HEADER + 3) - 2) / lh);
        scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - visible));
        int start = focused ? scroll : 0;
        int y = y1 - 3 - lh;
        hoverStyle = null;
        for (int i = start; i < lines.size() && i < start + visible; i++) {
            Line ln = lines.get(i);
            int la = (int) (ln.alpha * presence * 255);
            if (la < 8) {
                y -= lh;
                continue;
            }
            if (ln.first && ln.e.cat == Cat.HIT && ln.e.main.getString().contains("CRIT")) { // crit rows glow
                g.fill(x0 + 2, y - 1, x1 - 2, y + lh - 1, argb((int) (la * 0.20f), 0xFF8A1F));
            } else if (((i - start) & 1) == 1) { // subtle striping
                g.fill(x0 + 1, y - 1, x1 - 1, y + lh - 1, argb((int) (la * 0.05f), 0xFFFFFF));
            }
            g.pose().pushPose();
            g.pose().translate(x0 + 4, y, 0);
            g.pose().scale((float) scale, (float) scale, 1f);
            g.drawString(font, ln.seq, 0, 0, argb(la, 0xFFFFFF), true);
            if (ln.dim != null) g.drawString(font, ln.dim, innerW - font.width(ln.dim), 0, argb(la, 0xFFFFFF), true);
            g.pose().popPose();
            if (focused && mouseX >= x0 && mouseX <= x1 && mouseY >= y && mouseY < y + lh) {
                hoverStyle = font.getSplitter().componentStyleAtWidth(ln.seq, (int) ((mouseX - x0 - 4) / scale));
            }
            y -= lh;
        }
        // scrollbar when there's more history
        if (focused && lines.size() > visible) {
            int trackTop = y0 + HEADER + 3, trackBot = y1 - 3;
            int th = Math.max(8, (trackBot - trackTop) * visible / lines.size());
            int maxScroll = lines.size() - visible;
            int ty = trackBot - th - (int) ((trackBot - trackTop - th) * (scroll / (double) maxScroll));
            g.fill(x1 - 3, trackTop, x1 - 2, trackBot, argb(60, 0xFFFFFF));
            g.fill(x1 - 3, ty, x1 - 2, ty + th, argb(200, GOLD));
        }
        g.pose().popPose();
    }

    /** "16:45 ⚔ 1,105 → Fiendish Skeleton   6,238 HP" */
    private static Component compose(Entry e) {
        return compose(e, true);
    }

    private static Component compose(Entry e, boolean withDim) {
        MutableComponent c = Component.empty();
        if (DrConfig.data.feedTimestamps) {
            c.append(Component.literal(LocalTime.ofInstant(java.time.Instant.ofEpochMilli(e.time), java.time.ZoneId.systemDefault())
                .format(TIME) + " ").withStyle(Style.EMPTY.withColor(0x5C6370)));
        }
        c.append(Component.literal(e.cat.icon + " ").withStyle(Style.EMPTY.withColor(e.cat.color & 0xFFFFFF)));
        c.append(e.main);
        if (e.count > 1) c.append(Component.literal(" ×" + e.count).withStyle(Style.EMPTY.withColor(0xC8A040)));
        if (withDim && e.dim != null && !e.dim.isEmpty()) c.append(Component.literal("  " + e.dim).withStyle(Style.EMPTY.withColor(0x6B7280)));
        return c;
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col);
        g.fill(x1 - 1, y0, x1, y1, col);
    }

    private static int argb(int a, int rgb) {
        return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ interaction (chat open)

    private static final int[] tabX = new int[4], tabW = new int[4];
    private static int tabY;
    private static Style hoverStyle;

    public static boolean contains(int guiW, int guiH, double mx, double my) {
        int[] r = rect(guiW, guiH, true);
        return mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
    }

    public static boolean click(double mx, double my) {
        if (my < tabY || my > tabY + HEADER) return false;
        for (int i = 0; i < 4; i++) {
            if (mx >= tabX[i] - 3 && mx <= tabX[i] + tabW[i] + 3) {
                tab = Tab.values()[i];
                scroll = 0;
                return true;
            }
        }
        return false;
    }

    public static void scroll(double amount) {
        scroll += amount > 0 ? 3 : -3;
        if (scroll < 0) scroll = 0;
    }

    public static Style styleAtMouse() {
        return hoverStyle;
    }

    static int size() {
        return entries.size();
    }

    static long count(Cat c) {
        return entries.stream().filter(e -> e.cat == c).count();
    }

    static String lastText(Cat c) {
        Entry e = null;
        for (Entry x : entries) if (x.cat == c) e = x;
        return e == null ? null : compose(e).getString();
    }
}
