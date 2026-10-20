package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WoW-style cooldown bar: square icons with a dark clockwise sweep over the time remaining, a countdown in
 * the middle and a gold flash when something comes off cooldown. Sources: DR's action bar (ultimate and
 * potion cooldowns), the combat / Rogue bonus timers, and vanilla item cooldowns on hotbar items.
 */
public final class CooldownBar {
    private CooldownBar() {}

    /** One icon to draw. remaining/max in seconds; max <= 0 means "no sweep". */
    record Slot(String key, ItemStack icon, String label, double remaining, double max, boolean active,
                boolean ready, String badge) {}

    /** Per-slot memory: total duration (for the sweep) and when it became ready (for the flash). */
    private static final class Mem {
        double prev = -1, max;
        boolean prevActive;
        long readyAt;
    }

    private static final Map<String, Mem> mem = new HashMap<>();
    private static final Map<Integer, double[]> itemCd = new HashMap<>(); // slot -> {startMillis, startFrac, estTotal}

    private static final int GOLD = 0xFFFFD100;
    private static final int GREEN = 0xFF3DDC3D;
    private static final int SWEEP = 0x99000000;

    public static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        List<Slot> slots = collect(mc, mc.player);
        if (slots.isEmpty()) return;
        drawRow(g, mc.font, slots, g.guiWidth(), g.guiHeight());
    }

    // ------------------------------------------------------------------ sources

    private static List<Slot> collect(Minecraft mc, LocalPlayer p) {
        List<Slot> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        DrConfig.Data c = DrConfig.data;

        // Abilities from DR's action bar ("The Fast: [7s]" green = active, "Dash [4s]" = cooldown).
        // Every ability seen this session keeps a slot, shown ready when it's no longer counting down.
        if (DrState.knownAbilities.isEmpty()) {
            out.add(track("ult", ultIcon(DrState.ultName), "Ultimate", 0, false, null, now));
        }
        for (String name : DrState.knownAbilities) {
            DrState.Ability a = DrState.abilities.get(name);
            double rem = a == null ? 0 : a.remaining();
            boolean active = a != null && a.active();
            out.add(track("ab:" + name, ultIcon(name), name, rem, active, null, now));
        }

        // Buffs ("MONSTER SLAYER [2m]"): only while running, green.
        for (DrState.Ability b : DrState.buffs.values()) {
            out.add(track("buff:" + b.name(), buffIcon(b.name()), pretty(b.name()), b.remaining(), true, null, now));
        }

        // Potions: count badge, cooldown sweep.
        int carried = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(Items.POTION) || s.is(Items.SPLASH_POTION)) carried += s.getCount();
        }
        double pot = DrState.potCd == null ? 0 : Math.max(0, DrState.potCd - (now - DrState.potCdStamp) / 1000.0);
        // "[4/5] Potions: [59s]": charges / max, sweep = time until the next charge
        if (carried > 0 || DrState.potCd != null) {
            String badge = DrState.potCd != null ? DrState.pots + "/" + DrState.potsMax : String.valueOf(carried);
            out.add(track("pot", new ItemStack(Items.POTION), "Potions", pot, false, badge, now));
        }

        if (c.cdShowCombat) {
            double combat = DrState.combatRemaining();
            if (combat > 0) out.add(track("combat", new ItemStack(Items.IRON_SWORD), "Combat", combat, false, null, now));
            if (DrState.isRogue()) {
                double bonus = DrState.bonusRemaining();
                if (bonus > 0) out.add(track("bonus", new ItemStack(Items.CLOCK), "Bonus", bonus, true, null, now));
            }
        }

        if (c.cdShowItems) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.getInventory().items.get(i);
                if (s.isEmpty()) {
                    itemCd.remove(i);
                    continue;
                }
                float f = p.getCooldowns().getCooldownPercent(s, 0f);
                if (f <= 0f) {
                    itemCd.remove(i);
                    continue;
                }
                // Vanilla only exposes a fraction; estimate the total from how fast it drains.
                double[] st = itemCd.computeIfAbsent(i, k -> new double[]{now, f, 0});
                double elapsed = (now - st[0]) / 1000.0;
                if (elapsed > 0.3 && st[1] - f > 0.01) st[2] = elapsed / (st[1] - f);
                double total = st[2];
                if (total > 0) {
                    out.add(new Slot("item" + i, s.copyWithCount(1), "Slot " + (i + 1), total * f, total, false, false, null));
                } else { // no estimate yet: sweep from the raw fraction, no number
                    out.add(new Slot("item" + i, s.copyWithCount(1), "Slot " + (i + 1), f, 1.0, false, false, "?"));
                }
            }
        }
        return out;
    }

    private static Slot track(String key, ItemStack icon, String label, double remaining, boolean active, String badge, long now) {
        Mem m = mem.computeIfAbsent(key, k -> new Mem());
        boolean started = remaining > 0 && (m.prev <= 0 || remaining > m.prev + 1.5 || active != m.prevActive);
        if (started) m.max = remaining;
        if (remaining > m.max) m.max = remaining;
        if (remaining <= 0 && m.prev > 0) m.readyAt = now;
        m.prev = remaining;
        m.prevActive = active;
        boolean ready = remaining <= 0;
        return new Slot(key, icon, label, remaining, m.max, active && remaining > 0, ready, badge);
    }

    private static ItemStack buffIcon(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        Item item = n.contains("SLAYER") || n.contains("MONSTER") ? Items.GOLDEN_SWORD
            : n.contains("LOOT") ? Items.CHEST : n.contains("EXP") || n.contains("XP") ? Items.EXPERIENCE_BOTTLE : Items.BEACON;
        return new ItemStack(item);
    }

    private static String pretty(String caps) {
        StringBuilder b = new StringBuilder();
        for (String w : caps.toLowerCase(Locale.ROOT).split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private static ItemStack ultIcon(String name) {
        Item item = Items.NETHER_STAR;
        if (name != null) {
            switch (name) {
                case "The Fast" -> item = Items.SUGAR;
                case "Dash" -> item = Items.FEATHER;
                case "Berserk" -> item = Items.BLAZE_POWDER;
                case "Divine Protection" -> item = Items.SHIELD;
                case "Deaths Grasp" -> item = Items.WITHER_SKELETON_SKULL;
                default -> { }
            }
        }
        return new ItemStack(item);
    }

    // ------------------------------------------------------------------ drawing

    static void drawRow(GuiGraphics g, Font font, List<Slot> slots, int sw, int sh) {
        DrConfig.Data c = DrConfig.data;
        int size = c.cdIconSize;
        int gap = Math.max(2, size / 8);
        int total = slots.size() * size + (slots.size() - 1) * gap;
        int x = sw / 2 - total / 2 + c.cdX;
        int y = sh - c.cdY - size;
        long now = System.currentTimeMillis();
        g.pose().pushPose();
        g.pose().translate(0, 0, 5000); // vanilla stacks HUD layers 200 apart in z; stay above chat and the rest
        for (Slot s : slots) {
            drawSlot(g, font, s, x, y, size, now);
            x += size + gap;
        }
        g.pose().popPose();
    }

    private static void drawSlot(GuiGraphics g, Font font, Slot s, int x, int y, int size, long now) {
        // frame + dark backdrop
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        g.fill(x, y, x + size, y + size, 0xC0202020);

        // icon, scaled to the slot
        float k = (size - 4) / 16f;
        g.pose().pushPose();
        g.pose().translate(x + 2, y + 2, 0);
        g.pose().scale(k, k, 1f);
        g.renderItem(s.icon(), 0, 0);
        g.pose().popPose();

        // the WoW sweep over the part still to go (clockwise from 12 o'clock)
        if (s.remaining() > 0 && s.max() > 0) {
            double frac = Math.min(1.0, s.remaining() / s.max());
            g.pose().pushPose();
            g.pose().translate(0, 0, 200); // above the item
            sweep(g, x, y, size, frac, s.active() ? 0x8020A020 : SWEEP);
            g.pose().popPose();
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        // border: green while active, gold flash when it comes off cooldown
        long since = now - mem.getOrDefault(s.key(), new Mem()).readyAt;
        if (s.active()) {
            border(g, x, y, size, GREEN);
        } else if (s.ready() && since < 700) {
            int a = (int) (255 * (1 - since / 700.0));
            border(g, x, y, size, (a << 24) | (GOLD & 0xFFFFFF));
            g.fill(x, y, x + size, y + size, ((a / 3) << 24) | 0xFFFFFF);
        }

        // countdown
        if (s.remaining() > 0 && !"?".equals(s.badge())) {
            String t = format(s.remaining());
            int col = s.active() ? 0xFF7CFF7C : s.remaining() < 3 ? 0xFFFF5050 : 0xFFFFFFFF;
            float ts = Math.max(0.6f, Math.min(1.6f, size / 22f));
            outlined(g, font, t, x + size / 2f, y + size / 2f, ts, col);
        }
        // badge (bottom-right), e.g. potions "3/5"
        if (s.badge() != null && !"?".equals(s.badge())) {
            float bs = Math.max(0.5f, Math.min(1f, size / 28f));
            int w = font.width(s.badge());
            g.pose().pushPose();
            g.pose().translate(x + size - 1 - w * bs, y + size - 1 - 8 * bs, 0);
            g.pose().scale(bs, bs, 1f);
            g.drawString(font, s.badge(), 0, 0, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
        // label under the icon
        if (DrConfig.data.cdShowLabels) {
            float ls = 0.5f;
            g.pose().pushPose();
            g.pose().translate(x + size / 2f - font.width(s.label()) * ls / 2f, y + size + 2, 0);
            g.pose().scale(ls, ls, 1f);
            g.drawString(font, s.label(), 0, 0, 0xFFCCCCCC, true);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    /**
     * Darkens the remaining fraction of the square, sweeping clockwise from the top like WoW. Drawn at 4x
     * sub-pixel resolution as horizontal runs, so the edge stays smooth at any GUI scale.
     */
    static void sweep(GuiGraphics g, int x, int y, int size, double remainingFrac, int color) {
        final int S = 4;
        int n = size * S;
        double start = (1.0 - remainingFrac) * 360.0; // angles >= start are still dark
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1f / S, 1f / S, 1f);
        double c = n / 2.0;
        for (int row = 0; row < n; row++) {
            double dy = row + 0.5 - c;
            int runStart = -1;
            for (int col = 0; col <= n; col++) {
                boolean dark = false;
                if (col < n) {
                    double dx = col + 0.5 - c;
                    double a = Math.toDegrees(Math.atan2(dx, -dy));
                    if (a < 0) a += 360.0;
                    dark = a >= start;
                }
                if (dark && runStart < 0) runStart = col;
                else if (!dark && runStart >= 0) {
                    g.fill(runStart, row, col, row + 1, color);
                    runStart = -1;
                }
            }
        }
        g.pose().popPose();
    }

    private static void border(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x - 1, y - 1, x + size + 1, y, color);
        g.fill(x - 1, y + size, x + size + 1, y + size + 1, color);
        g.fill(x - 1, y, x, y + size, color);
        g.fill(x + size, y, x + size + 1, y + size, color);
    }

    private static void outlined(GuiGraphics g, Font font, String t, float cx, float cy, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        int tx = -font.width(t) / 2;
        int ty = -4;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) g.drawString(font, t, tx + dx, ty + dy, 0xFF000000, false);
        g.drawString(font, t, tx, ty, color, false);
        g.pose().popPose();
    }

    /** WoW-like: "2m" above a minute, whole seconds above 3s, tenths below. */
    static String format(double s) {
        if (s >= 60) return (int) Math.ceil(s / 60.0) + "m";
        if (s >= 3) return String.valueOf((int) Math.ceil(s));
        return String.format(Locale.ROOT, "%.1f", s);
    }

    /** Example slots for the settings preview. */
    static List<Slot> demo() {
        long t = System.currentTimeMillis() % 6000;
        double ult = 40 - (t / 1000.0) * 4; // 40 -> 16
        return List.of(
                new Slot("demo-ult", new ItemStack(Items.FEATHER), "Ultimate", ult, 60, false, false, null),
                new Slot("demo-pot", new ItemStack(Items.POTION), "Potions", 6 - t / 1000.0, 6, false, false, "3/5"),
                new Slot("demo-combat", new ItemStack(Items.IRON_SWORD), "Combat", 8 - (t % 4000) / 500.0, 8, false, false, null),
                new Slot("demo-bonus", new ItemStack(Items.CLOCK), "Bonus", 4.5 - (t % 3000) / 1000.0, 4.5, true, false, null));
    }
}
