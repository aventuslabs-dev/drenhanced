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
        latestExp = progress;
        expUpdateNanos = System.nanoTime();
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

    public static void setPlayerClass(String c) { playerClass = c; }

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
    private static final Pattern POTS = Pattern.compile("\\[(\\d+)/5] Potions: \\[(\\d+)s]");

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
    public static long potCdStamp = System.currentTimeMillis();

    public static void onActionBar(String msg, int remainingTicks) {
        if (msg == null || remainingTicks <= 0) {
            ultCd = null;
            pots = 5;
            potCd = null;
            return;
        }
        long shownAt = System.currentTimeMillis() - (long) (ACTION_BAR_TICKS - remainingTicks) * 50L;

        Matcher m = ULT_CD.matcher(msg);
        if (m.find()) {
            int minutes = parse(m.group(2));
            int seconds = parse(m.group(3));
            ultCd = minutes * 60 + seconds;
            ultActive = "a".equals(m.group(1));
            ultCdStamp = shownAt;
            Matcher n = ULT_NAME.matcher(msg);
            if (n.find()) ultName = n.group(1);
        } else {
            ultCd = null;
        }

        Matcher p = POTS.matcher(msg);
        if (p.find()) {
            pots = parse(p.group(1));
            potCd = parse(p.group(2));
            potCdStamp = shownAt;
        } else {
            pots = 5;
            potCd = null;
        }
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
    private static final Pattern DEBUG_DMG = Pattern.compile("\\d+ \\S*DMG -> (.+) \\[\\d+ HP]|-\\d+ \\S*HP \\((.+)\\)");
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
        return Math.max(0.0, timers.combat - (System.currentTimeMillis() - timers.combatStamp) / 1000.0);
    }

    public static double bonusRemaining() {
        return Math.max(0.0, timers.bonus - (System.currentTimeMillis() - timers.bonusStamp) / 1000.0);
    }

    private static void enterCombat(boolean pvp) {
        long now = System.currentTimeMillis();
        double remaining = combatRemaining();
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
        ultCd = null;
        pots = 5;
        potCd = null;
        timers = beforeLastHit = new Timers(0, System.currentTimeMillis(), 0, System.currentTimeMillis());
        HealthSync.reset();
    }
}
