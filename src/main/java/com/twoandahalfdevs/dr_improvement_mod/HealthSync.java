package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * DR sends real HP in the "health" scoreboard objective but entity health scaled to 20.
 * From one pair of (score, scaled health) we learn each player's max HP, then keep the
 * nameplate score in step with the smooth entity health every tick.
 */
public final class HealthSync {
    private HealthSync() {}

    private static final String OBJECTIVE = "health";

    /** Below 5% HP DR pins the synced health at exactly 1.0, which tells us nothing. */
    private static final float MEANINGLESS_HEALTH = 1.0f;

    private static final Map<String, Integer> pendingScore = new HashMap<>();
    private static final Map<String, Float> latestHealth = new HashMap<>();
    private static final Map<String, Integer> maxHealth = new HashMap<>();

    public static void onEntityHealth(String name, float health) {
        if (health != MEANINGLESS_HEALTH) {
            latestHealth.put(name, health);
        }
    }

    public static void onScore(String objective, String owner, int score) {
        if (!OBJECTIVE.equals(objective)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        pendingScore.put(owner, score);
        // Need a fresh entity-health sample paired with this score.
        latestHealth.remove(owner);

        // If they're under 5% the entity health is useless, so derive it from the score.
        Integer max = maxHealth.get(owner);
        if (max != null && max > 0) {
            AbstractClientPlayer p = find(mc, owner);
            if (p != null && p != mc.player) {
                p.setHealth(score * p.getMaxHealth() / max);
            }
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) return;

        Map<String, AbstractClientPlayer> byName = new HashMap<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            byName.put(p.getScoreboardName(), p);
        }

        Iterator<Map.Entry<String, Integer>> it = pendingScore.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> e = it.next();
            AbstractClientPlayer p = byName.get(e.getKey());
            Float health = latestHealth.get(e.getKey());
            if (p != null && health != null && health > 0f) {
                double max = e.getValue() * (p.getMaxHealth() / health);
                if (!Double.isNaN(max) && !Double.isInfinite(max)) {
                    maxHealth.put(e.getKey(), (int) Math.round(max));
                }
                it.remove();
            }
        }

        Scoreboard board = mc.level.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) return;

        for (PlayerScoreEntry entry : new ArrayList<>(board.listPlayerScores(objective))) {
            Integer max = maxHealth.get(entry.owner());
            AbstractClientPlayer p = byName.get(entry.owner());
            if (max == null || p == null || p.getMaxHealth() <= 0f) continue;
            int value = Math.round(max * (p.getHealth() / p.getMaxHealth()));
            if (value != entry.value()) {
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entry.owner()), objective).set(value);
            }
        }
    }

    private static AbstractClientPlayer find(Minecraft mc, String name) {
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.getScoreboardName().equals(name)) return p;
        }
        return null;
    }

    static void reset() {
        pendingScore.clear();
        latestHealth.clear();
        maxHealth.clear();
    }
}
