package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side state scraped from DR's packets: energy interpolation, class, action-bar cooldowns
 * and the combat timer. Everything here is touched on the client main thread only.
 */
public final class DrState {
    private DrState() {}

    // ---- Energy (server's XP bar progress) ----
    private static float latestExp;
    private static float prevExp;
    private static long expUpdateNanos;

    public static void onEnergy(float progress) {
        prevExp = interpolatedEnergy();
        AutoClicker.onEnergy(latestExp, progress);
        latestExp = progress;
        expUpdateNanos = System.nanoTime();
    }

    /** Latest energy from the server, 0..1. */
    public static float energy() {
        return latestExp;
    }

    /** On DungeonRealms (its class / HP boss bar has been read). */
    public static boolean onDr() {
        return !"???".equals(playerClass);
    }

    /** Fills up over ~50 ms, drains over ~10 ms. */
    public static float interpolatedEnergy() {
        float delta = latestExp - prevExp;
        float ms = (System.nanoTime() - expUpdateNanos) / 1_000_000f;
        float window = delta > 0f ? 50f : 10f;
        return prevExp + delta * Mth.clamp(ms / window, 0f, 1f);
    }

    // ---- Class (from the "<Class> Lv X - HP" boss bar) ----
    private static String playerClass = "???";

    private static final Pattern CLASS_NAME = Pattern.compile("Lv\\.? ?\\d+ ([A-Za-z][A-Za-z ]*)");
    private static String classKey = "";

    /** "Lv 101 Warrior": on a class switch, swap the cooldown bar to that class's abilities. */
    public static void setPlayerClass(String c) {
        playerClass = c;
        Matcher m = CLASS_NAME.matcher(c);
        String key = m.find() ? m.group(1).trim() : "";
        if (key.isEmpty() || key.equals(classKey)) return;
        classKey = key;
        abilities.clear();
        knownAbilities.clear();
        ultName = null;
        ultCd = null;
        if (DrConfig.data.classAbilities == null) DrConfig.data.classAbilities = new java.util.LinkedHashMap<>();
        java.util.List<String> saved = DrConfig.data.classAbilities.get(key);
        if (saved != null) knownAbilities.addAll(saved);
    }

    public static String classKey() { return classKey; }

    /** Remember a newly seen ability for the current class (saved, so it survives restarts). */
    private static void learn(String name) {
        if (!knownAbilities.add(name) || classKey.isEmpty()) return;
        if (DrConfig.data.classAbilities == null) DrConfig.data.classAbilities = new java.util.LinkedHashMap<>();
        DrConfig.data.classAbilities.put(classKey, new java.util.ArrayList<>(knownAbilities));
        DrConfig.save();
    }

    private static int playerLevel = -1;
    private static final Pattern PLAYER_LV = Pattern.compile("Lv\\.? ?(\\d+)");
    /** Your level, from the same boss bar (-1 until seen). Used for WoW-style difficulty colours. */
    public static int playerLevel() { return playerLevel; }
    public static void setPlayerLevel(int lv) { playerLevel = lv; }
    public static void onClassBar(String plain) {
        Matcher m = PLAYER_LV.matcher(plain);
        if (m.find()) playerLevel = parse(m.group(1));
    }
    public static boolean isRogue() { return playerClass.contains("Rogue"); }

    // ---- Action bar: ultimate + potion cooldowns ----
    private static final Pattern ULT_CD = Pattern.compile(
        "§(.)(?:The Fast|Berserk|Divine Protection|Deaths Grasp): \\[(?:(\\d+)m)? ?(?:(\\d+)s)?]");
    /** "[4/5] Potions: [59s]" = charges / max, next charge in; also "[1m]". */
    private static final Pattern POTS = Pattern.compile("\\[(\\d+)/(\\d+)] Potions: \\[(?:(\\d+)m)? ?(?:(\\d+)s)?]");
    public static int potsMax = 5;

    /** Action-bar messages show for 60 ticks; the remaining time tells us how stale the text is. */
    private static final int ACTION_BAR_TICKS = 60;

    private static final Pattern ULT_NAME = Pattern.compile("(The Fast|Berserk|Divine Protection|Deaths Grasp): \\[");
    /** Last ultimate seen in the action bar (picks the cooldown bar icon). */
    public static String ultName;
    public static Integer ultCd;         // seconds, null = not shown
    public static boolean ultActive;
    public static long ultCdStamp = System.currentTimeMillis();
    public static int pots = 5;
    public static Integer potCd;         // seconds, null = none
    /** Seconds until the next potion charge, sub-second accurate (0 when none). */
    public static double potRemaining() {
        if (potCd == null) return 0;
        double r = Countdown.remaining("pots", System.currentTimeMillis());
        return r >= 0 ? r : Math.max(0, potCd - (System.currentTimeMillis() - potCdStamp) / 1000.0);
    }
    public static long potCdStamp = System.currentTimeMillis();

    /** One ability shown in DR's action bar, e.g. "The Fast: [7s]" (green = active, red = cooldown) or "Dash [4s]". */
    public record Ability(String name, int seconds, boolean active, long stamp, String key) {
        public Ability(String name, int seconds, boolean active, long stamp) {
            this(name, seconds, active, stamp, null);
        }

        public double remaining() {
            long now = System.currentTimeMillis();
            double r = key == null ? -1 : Countdown.remaining(key, now);
            return r >= 0 ? r : Math.max(0, seconds - (now - stamp) / 1000.0);
        }
    }

    /** "[1m 59s]" counts in seconds, a bare "[2m]" only in minutes. */
    private static int granularity(String secondsGroup) {
        return secondsGroup == null || secondsGroup.isEmpty() ? 60 : 1;
    }

    private static final java.util.List<String> ULTS = java.util.List.of("The Fast", "Berserk", "Divine Protection", "Deaths Grasp");
    private static final Pattern ABILITY_CD = Pattern.compile("^(.+?):? \\[(?:(\\d+)m)? ?(?:(\\d+)s)?]$");
    private static final Pattern READY = Pattern.compile("^(.+?) is ready");
    private static final Pattern COMBAT_AB = Pattern.compile("^(PvM|PvE|PvP) Combat: \\[(\\d+)s]$");
    /** Abilities currently counting down in the action bar, by name (insertion order = as DR lists them). */
    public static final java.util.Map<String, Ability> abilities = new java.util.LinkedHashMap<>();
    /** Every ability name seen this session, so the cooldown bar can keep showing them as ready. */
    public static final java.util.Set<String> knownAbilities = new java.util.LinkedHashSet<>();
    /** Timed buffs DR lists in capitals, e.g. "MONSTER SLAYER [1m 59s]"; shown only while running. */
    public static final java.util.Map<String, Ability> buffs = new java.util.LinkedHashMap<>();
    private static int serverCombat = -1;   // DR's own "PvM Combat: [3s]"
    private static long serverCombatStamp;
    private static boolean serverCombatSeen;

    /**
     * DR's action bar is a component like "The Fast: [7s] ▪ Dash [4s] ▪ PvM Combat: [3s]", coloured per part
     * (the old § codes are gone). Split on ▪ and read each part with its colour.
     */
    public static void onActionBar(net.minecraft.network.chat.Component msg, int remainingTicks) {
        abilities.clear();
        buffs.clear();
        serverCombat = -1;
        if (msg == null || remainingTicks <= 0) {
            ultCd = null;
            pots = 5;
            potCd = null;
            return;
        }
        long shownAt = System.currentTimeMillis() - (long) (ACTION_BAR_TICKS - remainingTicks) * 50L;
        ultCd = null;
        pots = 5;
        potCd = null;

        for (String[] seg : segments(msg)) {
            String text = seg[0].trim();
            String color = seg[1];
            if (text.isEmpty()) continue;
            Matcher c = COMBAT_AB.matcher(text);
            if (c.find()) {
                serverCombat = parse(c.group(2));
                serverCombatStamp = shownAt;
                Countdown.observe("combat", serverCombat, 1, shownAt);
                serverCombatSeen = true;
                continue;
            }
            Matcher p = POTS.matcher(text);
            if (p.find()) {
                pots = parse(p.group(1));
                potsMax = Math.max(1, parse(p.group(2)));
                potCd = parse(p.group(3)) * 60 + parse(p.group(4));
                potCdStamp = shownAt;
                Countdown.observe("pots", potCd, granularity(p.group(4)), shownAt);
                continue;
            }
            Matcher r = READY.matcher(text);
            if (r.find()) {
                learn(r.group(1).trim());
                continue;
            }
            Matcher a = ABILITY_CD.matcher(text);
            if (a.find()) {
                String name = a.group(1).trim();
                if (name.length() > 24 || name.contains("Potions")) continue;
                int secs = parse(a.group(2)) * 60 + parse(a.group(3));
                if (name.equals(name.toUpperCase(java.util.Locale.ROOT)) && name.chars().anyMatch(Character::isLetter)) {
                    Countdown.observe("buff:" + name, secs, granularity(a.group(3)), shownAt);
                    buffs.put(name, new Ability(name, secs, true, shownAt, "buff:" + name)); // "MONSTER SLAYER [2m]": a buff, not an ability
                    continue;
                }
                boolean active = "green".equals(color) || "a".equals(color);
                String key = "ab:" + name + (active ? ":on" : ":cd");
                Countdown.observe(key, secs, granularity(a.group(3)), shownAt);
                abilities.put(name, new Ability(name, secs, active, shownAt, key));
                learn(name);
                if (ULTS.contains(name)) {
                    ultName = name;
                    ultCd = secs;
                    ultActive = active;
                    ultCdStamp = shownAt;
                }
            }
        }
    }

    /** Splits a component on "▪" into {text, colour} parts; also understands legacy § colour codes. */
    static java.util.List<String[]> segments(net.minecraft.network.chat.Component msg) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String[] color = {null};
        msg.visit((style, text) -> {
            String col = style.getColor() == null ? null : style.getColor().serialize();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '\u00a7' && i + 1 < text.length()) {
                    char code = text.charAt(++i);
                    if ("0123456789abcdef".indexOf(code) >= 0) col = String.valueOf(code);
                    continue;
                }
                if (ch == '\u25aa') {
                    out.add(new String[]{cur.toString(), color[0]});
                    cur.setLength(0);
                    color[0] = null;
                    continue;
                }
                if (color[0] == null && !Character.isWhitespace(ch)) color[0] = col;
                cur.append(ch);
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        out.add(new String[]{cur.toString(), color[0]});
        return out;
    }

    /** DR's own combat countdown when it's showing ("PvM Combat: [3s]"), else -1. */
    public static double serverCombatRemaining() {
        if (serverCombat < 0) return -1;
        double r = Countdown.remaining("combat", System.currentTimeMillis());
        return r >= 0 ? r : Math.max(0, serverCombat - (System.currentTimeMillis() - serverCombatStamp) / 1000.0);
    }

    private static int parse(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- Combat timer ----
    private static final double BASE_PVE = 8.0;
    private static final double BASE_PVP = 15.0;
    private static final Pattern ABILITY = Pattern.compile("(.*) has activated The Fast");
    private static final Pattern DEBUG_DMG = Pattern.compile("\\d+ \\S*DMG(?: \\([^)]*\\))? -> (.+) \\[\\d+ HP]|-\\d+ \\S*HP \\((.+?)\\)(?: \\[\\d+ HP])?");
    private static final Pattern REFLECT = Pattern.compile("\\*\\s+(OPPONENT\\s+)?REFLECTED.*\\[\\d+]");
    private static final Pattern GUILD_TAG = Pattern.compile("\\[.+]");
    private static final List<String> NOT_REAL_COMBAT = List.of("FALL");

    private record Timers(double combat, long combatStamp, double bonus, long bonusStamp) {}

    private static Timers timers = new Timers(0, System.currentTimeMillis(), 0, System.currentTimeMillis());
    private static Timers beforeLastHit = timers;

    private static double combatScale() {
        return isRogue() ? 1.0 - DrConfig.shadowmeldPoints() * 0.035 : 1.0;
    }

    private static double bonusWindow() {
        return 4.0 + 0.5 * DrConfig.justMyNaturePoints();
    }

    public static double combatRemaining() {
        // Prefer DR's own countdown; once DR has shown one, no countdown in the action bar means out of combat.
        double server = serverCombatRemaining();
        if (server >= 0) return server;
        if (serverCombatSeen) return 0;
        return estimatedCombatRemaining();
    }

    private static double estimatedCombatRemaining() {
        return Math.max(0.0, timers.combat - (System.currentTimeMillis() - timers.combatStamp) / 1000.0);
    }

    public static double bonusRemaining() {
        return Math.max(0.0, timers.bonus - (System.currentTimeMillis() - timers.bonusStamp) / 1000.0);
    }

    private static void enterCombat(boolean pvp) {
        long now = System.currentTimeMillis();
        double remaining = estimatedCombatRemaining();
        double bonus = timers.bonus;
        long bonusStamp = timers.bonusStamp;
        if (remaining <= 0.0) {
            // Fresh combat starts the "It's Just My Nature" window.
            bonus = bonusWindow();
            bonusStamp = now;
        }
        double length = (pvp ? BASE_PVP : BASE_PVE) * combatScale();
        beforeLastHit = timers;
        timers = new Timers(Math.max(length, remaining), now, bonus, bonusStamp);
    }

    public static void onChat(String msg) {
        MobHp.onChat(msg);
        FloatingText.onChatLine(msg);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || msg == null) return;

        // A reflected hit doesn't put you in combat: undo the hit that was just counted.
        if (REFLECT.matcher(msg).find()) {
            timers = beforeLastHit;
            return;
        }

        Matcher ab = ABILITY.matcher(msg);
        if (ab.find() && ab.group(1).endsWith(mc.player.getName().getString())) {
            // The Fast refreshes the bonus window without leaving combat.
            beforeLastHit = timers;
            timers = new Timers(timers.combat, timers.combatStamp, bonusWindow(), System.currentTimeMillis());
        }

        Matcher dmg = DEBUG_DMG.matcher(msg);
        if (!dmg.find()) return;
        String attacked = dmg.group(1);
        String attacker = dmg.group(2);
        List<AbstractClientPlayer> players = mc.level.players();

        if (attacked != null && !attacked.isEmpty()) {
            boolean isPlayer = GUILD_TAG.matcher(attacked).find()
                || players.stream().anyMatch(p -> attacked.endsWith(p.getName().getString()));
            enterCombat(isPlayer);
        } else if (attacker != null && !attacker.isEmpty() && !NOT_REAL_COMBAT.contains(attacker)) {
            boolean isPlayer = players.stream().anyMatch(p -> p.getName().getString().equals(attacker));
            enterCombat(isPlayer);
        }
    }

    /** Forget everything when leaving a server. */
    public static void reset() {
        latestExp = prevExp = 0f;
        playerClass = "???";
        classKey = "";
        Countdown.reset();
        abilities.clear();
        knownAbilities.clear();
        ultCd = null;
        pots = 5;
        potCd = null;
        timers = beforeLastHit = new Timers(0, System.currentTimeMillis(), 0, System.currentTimeMillis());
        HealthSync.reset();
    }
}
