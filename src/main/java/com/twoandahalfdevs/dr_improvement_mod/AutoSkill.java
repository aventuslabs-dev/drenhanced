package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto skills: when you hit a mob and a damage artifact on your hotbar is ready (off cooldown, enough mana,
 * target in range), it's cast for you. DR casts an artifact when you switch to its slot, so this switches to
 * it for one tick and straight back; your weapon stays drawn in hand and highlighted on the hotbar meanwhile.
 */
public final class AutoSkill {
    private AutoSkill() {}

    /** One artifact as read from its tooltip. */
    record Artifact(String name, int cost, double cooldown, double range, boolean damage) {}

    private static final Pattern COST = Pattern.compile("COST:\\s*([\\d,]+)\\s*MANA");
    private static final Pattern COOLDOWN = Pattern.compile("COOLDOWN:\\s*([\\d.]+)\\s*S");
    private static final Pattern RANGE = Pattern.compile("RANGE:\\s*([\\d.]+)");
    private static final Pattern DAMAGE = Pattern.compile("^DMG:|DAMAGE MULTIPLIER|MAX TARGETS", Pattern.MULTILINE);

    private static final Map<String, Long> castAt = new HashMap<>();
    private static Entity target;
    private static long targetAt;
    // a cast in progress: the slot we came from, the artifact slot, when
    private static int backTo = -1, castSlot = -1;
    static int casts; // self-test

    public static boolean enabled() {
        return DrConfig.data.autoSkills;
    }

    /** You hit an entity (MultiPlayerGameMode#attack). */
    public static void onAttack(Entity e) {
        target = e;
        targetAt = System.currentTimeMillis();
    }

    /** Read an artifact from a hotbar item, or null if it isn't one. */
    static Artifact read(ItemStack s) {
        if (s.isEmpty() || !s.has(DataComponents.CUSTOM_NAME)) return null;
        ItemLore l = s.get(DataComponents.LORE);
        if (l == null) return null;
        StringBuilder lore = new StringBuilder();
        for (Component c : l.lines()) lore.append(SplitChat.strip(c.getString())).append('\n');
        return parse(SplitChat.strip(s.getHoverName().getString()), lore.toString());
    }

    /** Pure, for the self-test. */
    static Artifact parse(String name, String lore) {
        String u = lore.toUpperCase(Locale.ROOT);
        Matcher c = COST.matcher(u), cd = COOLDOWN.matcher(u);
        if (!c.find() || !cd.find()) return null; // not a castable artifact
        Matcher r = RANGE.matcher(u);
        String n = name.replaceFirst("(?i)^\\s*artifact:\\s*", "").trim();
        return new Artifact(n, Integer.parseInt(c.group(1).replace(",", "")), Double.parseDouble(cd.group(1)),
            r.find() ? Double.parseDouble(r.group(1)) : 4, DAMAGE.matcher(u).find());
    }

    /** Off cooldown by every sign we have: our own cast timer, DR's action bar, the item cooldown, and mana. */
    static boolean ready(Artifact a, ItemStack s, LocalPlayer p, long now) {
        Long last = castAt.get(a.name());
        if (last != null && now - last < a.cooldown() * 1000 + 200) return false;
        DrState.Ability ab = DrState.abilities.get(a.name());
        if (ab != null && !ab.active() && ab.remaining() > 0.05) return false;
        if (p.getCooldowns().isOnCooldown(s)) return false;
        // only with enough mana for it AND at least your threshold, so mana is left for skills you press yourself
        if (DrState.onDr() && DrState.manaKnown() && DrState.mana() < Math.max(a.cost(), DrConfig.data.autoSkillMinMana)) return false;
        return !DrConfig.data.autoSkillSkip.stream().anyMatch(x -> x.equalsIgnoreCase(a.name()));
    }

    /** Start of every client tick. */
    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) {
            backTo = -1;
            return;
        }
        var inv = p.getInventory();
        long now = System.currentTimeMillis();
        if (backTo >= 0) { // the switch went out last tick: come back (DR may already have moved us back)
            if (inv.selected == castSlot) inv.selected = backTo;
            backTo = castSlot = -1;
            return;
        }
        if (!enabled() || mc.screen != null || target == null || now - targetAt > 300) return;
        Entity t = target;
        target = null;
        if (!t.isAlive()) return;
        double dist = Math.sqrt(t.distanceToSqr(p));
        for (int i = 0; i < 9; i++) {
            if (i == inv.selected) continue;
            ItemStack s = inv.getItem(i);
            Artifact a = read(s);
            if (a == null || !a.damage() || dist > a.range() + 1.5 || !ready(a, s, p, now)) continue;
            backTo = inv.selected;
            castSlot = i;
            inv.selected = i; // sent to DR this tick; DR casts it on the switch
            castAt.put(a.name(), now);
            casts++;
            return;
        }
    }

    /** A cast's slot switch is out right now: no swings until we're back on the weapon. */
    public static boolean busy() {
        return backTo >= 0;
    }

    /** During the one-tick switch, what to draw as the selected slot / held item (your weapon, not the artifact). */
    public static int shownSlot(int real) {
        return backTo >= 0 && real == castSlot ? backTo : real;
    }

    public static ItemStack shownMainHand(LocalPlayer p, ItemStack real) {
        return backTo >= 0 && p.getInventory().selected == castSlot ? p.getInventory().getItem(backTo) : real;
    }

    public static Component list() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder b = new StringBuilder("§6[DR] §fAuto skills " + (enabled() ? "§aON" : "§cOFF") + "§f:");
        if (mc.player != null) {
            for (int i = 0; i < 9; i++) {
                Artifact a = read(mc.player.getInventory().getItem(i));
                if (a == null) continue;
                boolean skip = DrConfig.data.autoSkillSkip.stream().anyMatch(x -> x.equalsIgnoreCase(a.name()));
                b.append("\n §7slot ").append(i + 1).append(": §e").append(a.name()).append(" §7(")
                    .append(a.cost()).append(" mana, ").append(a.cooldown()).append("s) ")
                    .append(!a.damage() ? "§8not a damage skill" : skip ? "§cskipped" : "§aauto");
            }
        }
        b.append("\n§7/drskill skip NAME, /drskill use NAME");
        return Component.literal(b.toString());
    }
}
