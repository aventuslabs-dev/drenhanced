package com.twoandahalfdevs.dr_improvement_mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WoW-style nameplate for DungeonRealms mobs. DR shows "Name [Lv 90]" until a mob is hit, then swaps the
 * name for a "|||||" bar. We remember the name, keep showing it, and draw a real health bar under it.
 * Exact numbers come from DR's damage debug lines ("120 DMG -> Spooky Skeleton [2280 HP]"); max HP is
 * remaining + damage on the first hit against a full-health mob, otherwise estimated from the bar.
 */
public final class MobHp {
    private MobHp() {}

    /** Duck interface added to EntityRenderState by a mixin. */
    public interface Holder {
        Display drim$hp();

        void drim$setHp(Display d);
    }

    /** label = mob name without the level; showBar = in combat (hit recently). */
    public record Display(Component label, String level, boolean elite, boolean showBar, String numbers, Track track,
                          Component sub, int barColor) {
        Display(Component label, String level, boolean elite, boolean showBar, String numbers, Track track) {
            this(label, level, elite, showBar, numbers, track, null, 0);
        }
    }

    /** Entity ids treated as real players regardless of the tab-list check (self-test only). */
    static final java.util.Set<Integer> testPlayers = new java.util.HashSet<>();

    /** A DR mob name split into its parts: "Spooky Skeleton" (with DR's colours), "90", bold = elite. */
    record Parts(Component name, String level, boolean elite) {}

    public static final class Track {
        Component name;          // last plain (un-hit) name, with DR's colours
        Component label;         // that name without "[Lv 90]"
        boolean elite;
        String level = "";
        float frac = 1f;         // drawn fraction: exact hp/max when known, else the |||| bar
        float pipe = 1f;         // last |||| bar fraction
        long fracChanged;
        long plainAt;            // last time the plain name was shown (NOT proof of full HP: DR reverts after combat)
        Boolean startedFull;     // first time we saw it, was it showing its plain name (never damaged)?
        boolean confirmed;       // at least one damage line has been matched to this mob
        long firstDropAt;        // when its |||| bar first dropped below full
        int swing = -1;          // swing that last updated it (one cleave swing hits several mobs)
        int hp = -1;             // exact, from damage messages
        long hpAt;
        int max = -1;
        boolean maxExact;
        double maxLo = -1, maxHi = -1; // max HP window learned from exact HP vs DR's |||| bar (HP varies per mob)
        long hitAt;
        Vec3 pos = Vec3.ZERO;
        float height = 2f;
        long seen;
        // animation
        float shown = 1f, trail = 1f;
        long lastFrame;
    }

    private static final Map<Integer, Track> tracks = new HashMap<>();
    /** Max HP last seen for "name|level", used as an estimate for other mobs of the same kind. */
    /** "523 DMG -> Fiendish Skeleton [7319 HP]", also "36 DMG (Thorns) -> ..." and "(Reflect)". */
    private static final Pattern DMG = Pattern.compile("(\\d+) \\S*DMG(?: \\(([^)]*)\\))? -> (.+?) \\[(\\d+) HP]");
    private static final Pattern LEVEL = Pattern.compile("\\[Lv\\.? ?(\\d+)]");
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);

    private static int lastAttacked = -1;
    private static long lastAttackAt;

    public static boolean enabled() {
        return DrConfig.enemyHpStyle() == EnemyHp.Style.MODERN;
    }

    // ------------------------------------------------------------------ tracking

    /** Called while extracting the render state: returns what to draw, or null for a normal nametag. */
    public static Display update(Entity e, Component nameTag) {
        if (!enabled() || nameTag == null) return null;
        if (e instanceof net.minecraft.client.player.AbstractClientPlayer pl) return updatePlayer(pl);
        long now = System.currentTimeMillis();
        EnemyHp.Parsed p = EnemyHp.parse(nameTag);
        Parts parts = p == null ? split(nameTag) : null;
        if (p == null && parts == null) return null; // not a DR mob nameplate (NPC, sign text, ...)

        Track t = tracks.computeIfAbsent(e.getId(), k -> new Track());
        t.seen = now;
        t.pos = e.position();
        t.height = e.getBbHeight();
        if (t.startedFull == null) t.startedFull = p == null;
        if (p == null) { // plain "Name [Lv 90]": out of combat (DR also shows this after combat, while still hurt)
            t.name = nameTag;
            t.label = parts.name();
            t.level = parts.level();
            t.elite = parts.elite();
            // just hit (DR hasn't swapped in its bar yet), or DR reverted the name mid-fight while it's still hurt
            if (t.hp >= 0 && (now - t.hpAt < 3000 || (t.confirmed && t.hp > 0 && t.max > 0 && t.hp < t.max && now - t.hitAt < 15_000))) {
                if (t.max > 0) t.frac = Math.min(1f, t.hp / (float) t.max);
                return new Display(t.label, t.level, t.elite, true, numbers(t, now), t);
            }
            t.plainAt = now;
            // Keep what we know about its HP: DR swaps back to the name ~8 s after the last hit even when the
            // mob is still hurt. Only a mob we've never seen damaged counts as full.
            if (!t.confirmed && t.firstDropAt == 0) {
                t.frac = t.pipe = 1f;
                t.shown = t.trail = 1f;
            }
            return new Display(t.label, t.level, t.elite, false, "", t);
        }
        float f = (float) p.fraction();
        // max HP for a mob we didn't see at full: pair the exact HP from the last damage line with the bar
        // (once DR has had time to update it) and narrow the window hit by hit
        if (!t.maxExact && t.hp > 0 && p.total() > 0 && now - t.hpAt > 250 && now - t.hpAt < 2000) {
            learnMax(t, t.hp, p.filled(), p.total());
        }
        if (f < 0.999f && t.firstDropAt == 0) t.firstDropAt = now;
        if (Math.abs(f - t.pipe) > 1e-4) {
            if (f < t.pipe) t.hitAt = now;
            t.pipe = f;
            t.fracChanged = now;
        }
        boolean exact = t.hp >= 0 && t.max > 0 && (t.hpAt >= t.fracChanged - 1500
            || (now - t.hpAt < 30_000 && p.total() > 0 && Math.abs(t.hp / (float) t.max - f) <= 1.5f / p.total() + 0.01f));
        t.frac = exact ? Math.min(1f, t.hp / (float) t.max) : f;
        Matcher m = LEVEL.matcher(p.after());
        if (m.find()) t.level = m.group(1);

        Component label = t.label;
        if (label == null) { // came into view already damaged: fall back to the mob type
            label = Component.literal(e.getType().getDescription().getString());
        }
        return new Display(label, t.level, t.elite, true, numbers(t, now), t);
    }

    /**
     * Real players (in the tab list with a genuine v4 account UUID; DR's NPCs fail this): name in their team
     * colour, guild tag as "<TAG>" underneath, and an HP bar with DR's real HP while they're hurt or in combat.
     */
    private static Display updatePlayer(net.minecraft.client.player.AbstractClientPlayer pl) {
        Minecraft mc = Minecraft.getInstance();
        if (!DrConfig.data.playerPlates || pl == mc.player) return null;
        if (pl.getGameProfile().getName().startsWith("NPC@")) return null; // DR's NPCs
        boolean real = testPlayers.contains(pl.getId()) || (pl.getUUID().version() == 4
            && mc.getConnection() != null && mc.getConnection().getPlayerInfo(pl.getUUID()) != null);
        if (!real) return null;

        long now = System.currentTimeMillis();
        Track t = tracks.computeIfAbsent(pl.getId(), k -> new Track());
        t.seen = now;
        t.pos = pl.position();
        t.height = pl.getBbHeight();
        float f = pl.getMaxHealth() > 0 ? Math.max(0f, Math.min(1f, pl.getHealth() / pl.getMaxHealth())) : 1f;
        if (f < t.frac - 0.001f || pl.hurtTime > 0) t.hitAt = now;
        t.frac = f;
        boolean showBar = true; // players: always show the bar (WoW style)

        var team = pl.getTeam();
        net.minecraft.ChatFormatting col = team == null ? null : team.getColor();
        Style nameStyle = Style.EMPTY.withColor(col == null || col == net.minecraft.ChatFormatting.RESET ? net.minecraft.ChatFormatting.WHITE : col);
        Component label = Component.literal(pl.getGameProfile().getName()).withStyle(nameStyle);
        Component sub = null;
        if (team != null) {
            // PlayerTeamMixin may have recoloured guild-mates' tags, so take the formatted prefix from the team.
            Component prefix = team.getFormattedName(Component.empty());
            String tag = SplitChat.strip(team.getPlayerPrefix().getString()).trim();
            if (tag.startsWith("[") && tag.endsWith("]") && tag.length() > 2) {
                Style ts = prefix.getSiblings().isEmpty() ? prefix.getStyle() : prefix.getSiblings().get(0).getStyle();
                if (ts.getColor() == null) ts = ts.withColor(net.minecraft.ChatFormatting.GRAY);
                sub = Component.literal("<" + tag.substring(1, tag.length() - 1) + ">").withStyle(ts);
            }
        }

        String name = pl.getScoreboardName();
        Integer max = HealthSync.maxOf(name);
        int pct = Math.round(f * 100);
        String numbers;
        if (max != null && max > 0) {
            int cur = Math.round(max * f);
            numbers = switch (DrConfig.data.enemyHpText) {
                case "CURRENT" -> NUM.format(cur);
                case "PERCENT" -> pct + "%";
                case "CURRENT_PERCENT" -> NUM.format(cur) + " (" + pct + "%)";
                default -> NUM.format(cur) + " / " + NUM.format(max);
            };
        } else {
            numbers = pct + "%";
        }
        // DR's team suffix carries level and class: " [Lv 103 ROG]"
        String level = "";
        String cls = null;
        // usually in the team suffix; fall back to the full display name
        for (String src : new String[]{team == null ? "" : team.getPlayerSuffix().getString(), pl.getDisplayName().getString()}) {
            Matcher lm = PLAYER_SUFFIX.matcher(SplitChat.strip(src));
            if (lm.find()) {
                level = lm.group(1);
                cls = lm.group(2);
                break;
            }
        }
        if (cls != null) {
            int cc = classColor(cls);
            Component clsTag = Component.literal(className(cls)).withStyle(Style.EMPTY.withColor(cc != 0 ? cc & 0xFFFFFF : 0xD8D8D8));
            sub = sub == null ? clsTag : Component.empty().append(sub).append(Component.literal("  ").append(clsTag));
        }
        int bar = PLAYER_GREEN; // one health-green bar for every class (the class shows in the line above)
        return new Display(label, level, false, showBar, numbers, t, sub, bar);
    }

    static final int PLAYER_GREEN = 0xFF2FB34A;

    private static final Pattern PLAYER_SUFFIX = Pattern.compile("\\[Lv\\.? ?(\\d+)(?: ([A-Za-z]{2,4}))?]");

    /** WoW class colours for DR's class tags. */
    static int classColor(String tag) {
        return switch (tag.toUpperCase(Locale.ROOT)) {
            case "ROG" -> 0xFFFFF468;
            case "WAR" -> 0xFFC69B6D;
            case "MAG", "MGE", "WIZ" -> 0xFF3FC7EB;
            case "PAL" -> 0xFFF48CBA;
            case "ARC", "RAN", "HUN", "BOW" -> 0xFFAAD372;
            case "PRI", "CLE" -> 0xFFF0F0F0;
            case "NEC", "WLK" -> 0xFF8788EE;
            default -> 0; // a class tag we don't know yet (e.g. "REV"): no class colour
        };
    }

    static String className(String tag) {
        return switch (tag.toUpperCase(Locale.ROOT)) {
            case "ROG" -> "Rogue";
            case "WAR" -> "Warrior";
            case "MAG", "MGE", "WIZ" -> "Mage";
            case "PAL" -> "Paladin";
            case "ARC", "RAN", "HUN", "BOW" -> "Ranger";
            case "PRI", "CLE" -> "Priest";
            case "NEC", "WLK" -> "Necromancer";
            default -> tag;
        };
    }

    /**
     * Splits "Spooky Skeleton [Lv 90]" into its name (keeping DR's colours and bold) and level.
     * Returns null when there's no "[Lv N]", i.e. it isn't a DR mob nameplate.
     */
    static Parts split(Component full) {
        List<Object[]> runs = new ArrayList<>(); // {String, Style}
        full.visit((style, text) -> {
            Style st = style;
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\u00a7' && i + 1 < text.length()) {
                    net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(text.charAt(++i));
                    if (f == null) continue;
                    if (!cur.isEmpty()) runs.add(new Object[]{cur.toString(), st});
                    cur.setLength(0);
                    st = f == net.minecraft.ChatFormatting.RESET ? Style.EMPTY : f.isColor() ? Style.EMPTY.withColor(f) : st.applyLegacyFormat(f);
                    continue;
                }
                cur.append(c);
            }
            if (!cur.isEmpty()) runs.add(new Object[]{cur.toString(), st});
            return java.util.Optional.empty();
        }, Style.EMPTY);
        StringBuilder plain = new StringBuilder();
        for (Object[] r : runs) plain.append((String) r[0]);
        Matcher m = Pattern.compile("\\s*\\[Lv\\.? ?(\\d+)]").matcher(plain);
        if (!m.find()) return null;
        int from = m.start(), to = m.end();
        MutableComponent name = Component.empty();
        boolean elite = false;
        int pos = 0;
        for (Object[] r : runs) {
            String txt = (String) r[0];
            Style st = (Style) r[1];
            StringBuilder keep = new StringBuilder();
            for (int i = 0; i < txt.length(); i++, pos++) {
                if (pos >= from && pos < to) continue;
                keep.append(txt.charAt(i));
            }
            if (keep.isEmpty()) continue;
            // Elite = bold name letters. DR's affix icons ("❄ ", "♨ ") are bold too, so they don't count.
            if (st.isBold() && keep.chars().anyMatch(Character::isLetter)) elite = true;
            name.append(Component.literal(keep.toString()).withStyle(st.withBold(false)));
        }
        String nameText = name.getString().trim();
        if (nameText.isEmpty()) return null;
        // drop leading/trailing spaces left around the removed level
        MutableComponent trimmed = Component.empty();
        String flat = name.getString();
        int lead = flat.length() - flat.stripLeading().length();
        int end = flat.stripTrailing().length();
        int idx = 0;
        for (Component part : name.getSiblings()) {
            String txt = part.getString();
            int a = Math.max(0, lead - idx), b = Math.min(txt.length(), end - idx);
            if (a < b) trimmed.append(Component.literal(txt.substring(a, b)).withStyle(part.getStyle()));
            idx += txt.length();
        }
        return new Parts(trimmed, m.group(1), elite);
    }

    private static int swingCounter;

    public static void onAttack(Entity e) {
        lastAttacked = e.getId();
        lastAttackAt = System.currentTimeMillis();
        swingCounter++;
    }

    public static void onChat(String raw) {
        if (raw == null) return;
        Matcher m = DMG.matcher(SplitChat.strip(raw));
        if (!m.find()) return;
        int dmg = Integer.parseInt(m.group(1));
        String source = m.group(2); // null for a normal hit, "Thorns" / "Reflect" otherwise
        String target = m.group(3).trim();
        int left = Integer.parseInt(m.group(4));
        long now = System.currentTimeMillis();

        // Every line (your hit, a cleave/AoE hit on a neighbour, Thorns/Reflect) goes to the mob whose name and
        // HP history fit it best. The mob you just swung at only gets a head start: on an axe cleave DR prints
        // the lines in any order, and "♨ Fire Imp" must never land on the plain "Fire Imp" you clicked.
        Track clicked = source == null && now - lastAttackAt < 1500 ? tracks.get(lastAttacked) : null;
        Track t = bestMatch(target, dmg, left, now, clicked);
        if (t == null) return;
        if (t == clicked) t.swing = swingCounter;
        FloatingText.fromChat(dmg, t.pos, t.height, source);
        // nothing tracked fits (the real mob is out of view): show the number, but don't corrupt another mob's HP
        if (t != clicked && fitPenalty(t, dmg, left, now) >= 200) return;

        // exact max = remaining + damage, but only on the first hit against a mob we first saw undamaged
        boolean wasFull = Boolean.TRUE.equals(t.startedFull) && !t.confirmed
            && (t.firstDropAt == 0 || now - t.firstDropAt < 2000);
        t.confirmed = true;
        t.hp = left;
        t.hpAt = now;
        t.hitAt = now;
        if (wasFull && (!t.maxExact || t.max < left + dmg)) {
            t.max = left + dmg;
            t.maxExact = true;
        } else if (t.max >= 0 && left > t.max) {
            t.max = left; // regenerated or estimate was low
            t.maxExact = false;
        }
    }

    /**
     * DR draws round(hp / max * total) pipes, give or take its rounding, so max lies in
     * (hp * total / (filled + 1), hp * total / (filled - 1)). Each pairing narrows it; a contradiction
     * (wrong pairing, the mob healed a lot) starts over.
     */
    static void learnMax(Track t, int hp, int filled, int total) {
        double lo = Math.max(hp, hp * (double) total / (filled + 1));
        double hi = filled > 1 ? hp * (double) total / (filled - 1) : Double.MAX_VALUE;
        if (t.maxLo < 0 || lo > t.maxHi || hi < t.maxLo) {
            t.maxLo = lo;
            t.maxHi = hi;
        } else {
            t.maxLo = Math.max(t.maxLo, lo);
            t.maxHi = Math.min(t.maxHi, hi);
        }
        double est = t.maxHi < Double.MAX_VALUE / 2 ? (t.maxLo + t.maxHi) / 2 : t.maxLo * 1.5;
        t.max = (int) Math.round(Math.max(est, hp));
    }

    private static String labelText(Track t) {
        return SplitChat.strip((t.label != null ? t.label : t.name).getString()).trim();
    }

    private static boolean matches(Track t, String target) {
        if (t.name == null) return true; // no name yet (came into view damaged): can't rule it out
        String l = labelText(t);
        return l.equals(target) || l.endsWith(" " + target) || target.endsWith(" " + l) || l.contains(target);
    }

    /** Best mob for a damage line: exact name first, then HP continuity (hp - damage ≈ remaining, allowing regen). */
    private static Track bestMatch(String target, int dmg, int left, long now, Track clicked) {
        Vec3 me = Minecraft.getInstance().player == null ? Vec3.ZERO : Minecraft.getInstance().player.position();
        List<Track> exact = new ArrayList<>(), loose = new ArrayList<>();
        for (Track t : tracks.values()) {
            if (now - t.seen > 5000 || t.pos.distanceToSqr(me) > 40 * 40) continue;
            if (t.name != null && labelText(t).equals(target)) exact.add(t);
            else if (matches(t, target)) loose.add(t);
        }
        if (clicked != null && clicked.name == null && !exact.contains(clicked)) exact.add(clicked);
        List<Track> pool = exact.isEmpty() ? loose : exact;
        Track best = null;
        double bestScore = Double.MAX_VALUE;
        for (Track t : pool) {
            double score = Math.min(40, Math.sqrt(t.pos.distanceToSqr(me)));
            score += fitPenalty(t, dmg, left, now);
            if (t == clicked && clicked.swing != swingCounter) score -= 40; // you just swung at it
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    /** How badly "dmg -> [left HP]" fits what we know about this mob's HP. Negative = fits exactly. */
    static double fitPenalty(Track t, int dmg, int left, long now) {
        if (t.max > 0 && t.maxExact && left > t.max) return 300;            // more HP than it can have
        if (t.hp < 0 || now - t.hpAt > 60_000) {
            if (t.hp == 0 && now - t.hpAt < 3000) return 0;                    // DR still prints hits on a dying mob
            return 30;                                                          // never matched: possible, unproven
        }
        long expect = (long) t.hp - dmg;
        double secs = Math.max(0, (now - t.hpAt) / 1000.0);
        int ref = t.max > 0 ? t.max : Math.max(left, t.hp);
        double regen = 60 + secs * Math.max(100, ref * 0.015);                // DR mobs regen ~1% max HP per second
        long diff = left - expect;
        if (diff >= -Math.max(3, ref / 200) && diff <= regen) return -100;     // continues its HP (plus regen)
        if (diff > regen) return 200;                                           // HP went up too much: other mob
        return Math.min(100, -diff / 50.0);                                     // lower than expected: unseen damage
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Track> it = tracks.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seen > 60_000) it.remove();
        }
    }

    public static void reset() {
        tracks.clear();
        lastAttacked = -1;
    }

    /** The number text for the bar, e.g. "1,840 / 2,400". "~" marks an estimate. */
    static String numbers(Track t, long now) {
        int pct = Math.round(t.frac * 100);
        boolean freshHp = t.hp >= 0 && (t.hpAt >= t.fracChanged - 1500
            || (t.max > 0 && Math.abs(t.frac - Math.min(1f, t.hp / (float) t.max)) < 1e-4f));
        int hp;
        boolean estimate;
        if (freshHp) {
            hp = t.hp;
            estimate = false;
        } else if (t.max > 0) {
            hp = Math.round(t.max * t.frac);
            estimate = true;
        } else {
            return pct + "%";
        }
        int max = t.max;
        boolean maxEst = !t.maxExact;
        if (max <= 0 && t.frac > 0.02f && t.frac < 0.999f) {
            max = Math.round(hp / t.frac);
            maxEst = true;
        }
        String cur = (estimate ? "~" : "") + NUM.format(hp);
        String mx = max > 0 ? (maxEst ? "~" : "") + NUM.format(max) : "?";
        return switch (DrConfig.data.enemyHpText) {
            case "CURRENT" -> cur;
            case "PERCENT" -> pct + "%";
            case "CURRENT_PERCENT" -> cur + " (" + pct + "%)";
            default -> cur + " / " + mx;
        };
    }

    // ------------------------------------------------------------------ drawing

    static final int BORDER = 0xF0000000;
    static final int BACK = 0xC0181818;
    static final int TRAIL = 0xFFF2C14E;

    public static int fillColor(float f) {
        if ("HEALTH".equals(DrConfig.data.enemyHpColor)) {
            if (f > 0.5f) return lerp(0xFFE0C020, 0xFF2EBF2E, (f - 0.5f) * 2f);
            return lerp(0xFFD01E1E, 0xFFE0C020, f * 2f);
        }
        if ("GOLD".equals(DrConfig.data.enemyHpColor)) return 0xFFD9A21E;
        return 0xFFC41F1F; // hostile red
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static final int GOLD = 0xFFE8B730;
    static final int GOLD_DARK = 0xFF7A5A10;

    /** WoW-style difficulty colour of a level against yours. */
    static int levelColor(String level) {
        int pl = DrState.playerLevel();
        int ml;
        try {
            ml = Integer.parseInt(level);
        } catch (Exception e) {
            return 0xFFFFD100;
        }
        if (pl <= 0) return 0xFFFFD100;
        int d = ml - pl;
        if (d >= 5) return 0xFFFF2B2B;
        if (d >= 3) return 0xFFFF8A1F;
        if (d >= -2) return 0xFFFFD100;
        if (d >= -8) return 0xFF40C040;
        return 0xFF9D9D9D;
    }

    /** Draws the plate in place of the vanilla nametag. Mirrors EntityRenderer#renderNameTag's transform. */
    public static void render(Display d, Vec3 attach, PoseStack pose, MultiBufferSource buffers, int light) {
        if (attach == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Track t = d.track();
        if (d.showBar()) animate(t);

        int w = DrConfig.data.enemyHpBarWidth;
        int h = DrConfig.data.enemyHpBarHeight;
        pose.pushPose();
        // players wear tall cosmetics (hats, horns): lift their plate clear of the head so it isn't hidden
        boolean player = d.barColor() != 0;
        pose.translate(attach.x, attach.y + (player ? 0.85 : 0.5), attach.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025f, -0.025f, 0.025f);
        Matrix4f m = pose.last().pose();
        int bright = LightTexture.FULL_BRIGHT;
        // Depth-tested plate pieces: text is drawn with polygon offset in front of them, so the level and HP
        // numbers stay on top no matter which order the renderer flushes the batches in (Sodium differs).
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());

        // ---- name row: [level badge] [skull] Name ----
        FormattedCharSequence name = d.label().getVisualOrderText();
        int nameW = font.width(name);
        String lv = d.level() == null ? "" : d.level();
        int badgeW = lv.isEmpty() ? 0 : font.width(lv) + 6;
        String skull = "\u2620";
        int skullW = d.elite() ? font.width(skull) + 2 : 0;
        int gap = 3;
        float total = badgeW + (badgeW > 0 ? gap : 0) + skullW + nameW;
        float x = -total / 2f;
        float rowY = d.sub() != null ? -21f : -13f; // name sits above the guild line and the bar
        int lvColor = levelColor(lv);

        if (badgeW > 0) {
            float bx0 = x, bx1 = x + badgeW, by0 = rowY - 1, by1 = rowY + 9;
            int edge = d.elite() ? GOLD : mix(lvColor, 0x000000, 0.45f);
            quad(vc, m, bx0 + 1, by0 + 1, bx1 - 1, by1 - 1, 0xD0101010, bright);
            quad(vc, m, bx0, by0, bx1, by0 + 1, edge, bright);
            quad(vc, m, bx0, by1 - 1, bx1, by1, edge, bright);
            quad(vc, m, bx0, by0 + 1, bx0 + 1, by1 - 1, edge, bright);
            quad(vc, m, bx1 - 1, by0 + 1, bx1, by1 - 1, edge, bright);
            text(font, Component.literal(lv).getVisualOrderText(), bx0 + 3, rowY, lvColor, buffers, m, light);
            x += badgeW + gap;
        }
        if (d.elite()) {
            text(font, Component.literal(skull).getVisualOrderText(), x, rowY, GOLD, buffers, m, light);
            x += skullW;
        }
        text(font, name, x, rowY, 0xFFFFFFFF, buffers, m, light);
        if (d.sub() != null) { // WoW-style <Guild> line under the name
            pose.pushPose();
            pose.translate(0, -11f, 0);
            pose.scale(0.8f, 0.8f, 1f);
            Matrix4f sm = pose.last().pose();
            var subSeq = d.sub().getVisualOrderText();
            text(font, subSeq, -font.width(subSeq) / 2f, 0, 0xFFAAAAAA, buffers, sm, light);
            pose.popPose();
        }

        // ---- health bar, only while in combat ----
        if (d.showBar()) {
            vc = buffers.getBuffer(RenderType.textBackground());
            float x0 = -w / 2f, x1 = w / 2f, y0 = 0, y1 = h;
            // Translucent quads are depth-sorted on upload, so overlapping layers would swap order with the
            // viewing angle. Draw non-overlapping pieces instead: fill | damage trail | empty, each in bands.
            float fillEnd = x0 + w * clamp(t.shown);
            float trailEnd = Math.max(fillEnd, x0 + w * clamp(t.trail));
            bands(vc, m, x0, fillEnd, y0, y1, h, readable(d.barColor() != 0 ? d.barColor() : fillColor(t.frac)), bright);
            bands(vc, m, fillEnd, trailEnd, y0, y1, h, TRAIL, bright);
            bands(vc, m, trailEnd, x1, y0, y1, h, BACK, bright);
            int edge = d.elite() ? GOLD : BORDER;
            quad(vc, m, x0 - 1, y0 - 1, x1 + 1, y0, edge, bright);
            quad(vc, m, x0 - 1, y1, x1 + 1, y1 + 1, edge, bright);
            quad(vc, m, x0 - 1, y0, x0, y1, edge, bright);
            quad(vc, m, x1, y0, x1 + 1, y1, edge, bright);
            if (d.elite()) { // little gold studs at the ends, WoW elite style
                quad(vc, m, x0 - 3, y0 + h / 2f - 1.5f, x0 - 1, y0 + h / 2f + 1.5f, GOLD, bright);
                quad(vc, m, x1 + 1, y0 + h / 2f - 1.5f, x1 + 3, y0 + h / 2f + 1.5f, GOLD, bright);
            }

            if (!"NONE".equals(DrConfig.data.enemyHpText)) {
                float s = Math.max(0.6f, Math.min(1.3f, (h + 1) / 8f));
                pose.pushPose();
                pose.translate(0, h / 2f, 0);
                pose.scale(s, s, 1f);
                Matrix4f tm = pose.last().pose();
                var seq = Component.literal(d.numbers()).getVisualOrderText();
                text(font, seq, -font.width(seq) / 2f, -4f, 0xFFFFFFFF, buffers, tm, bright);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** Outlined text plus a faint see-through copy so it stays readable behind blocks (like the bar). */
    private static void text(Font font, FormattedCharSequence seq, float x, float y, int color, MultiBufferSource buffers, Matrix4f m, int light) {
        font.drawInBatch(seq, x, y, (color & 0x00FFFFFF) | 0x90000000, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        font.drawInBatch8xOutline(seq, x, y, color, 0xFF000000, m, buffers, LightTexture.FULL_BRIGHT);
    }

    /**
     * Bright fills (Rogue yellow, Priest white, full-HP green) wash out the white HP numbers: darken them to a
     * mid tone that keeps the hue, so outlined white text always reads.
     */
    static int readable(int argb) {
        double r = ((argb >> 16) & 255) / 255.0, g = ((argb >> 8) & 255) / 255.0, b = (argb & 255) / 255.0;
        double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double target = 0.6;
        if (luma <= target) return argb;
        return mix(argb, 0x000000, (float) (1 - target / luma));
    }

    /** A bar segment with a light top band and a dark bottom band (the WoW gloss), no overlaps. */
    private static void bands(VertexConsumer vc, Matrix4f m, float xa, float xb, float y0, float y1, int h, int argb, int light) {
        if (xb - xa < 0.01f) return;
        float top = y0 + Math.max(1f, h * 0.45f);
        float bot = y1 - Math.max(1f, h * 0.3f);
        if (bot < top) bot = top;
        quad(vc, m, xa, y0, xb, top, mix(argb, 0xFFFFFF, 0.22f), light);
        if (bot > top) quad(vc, m, xa, top, xb, bot, argb, light);
        quad(vc, m, xa, bot, xb, y1, mix(argb, 0x000000, 0.25f), light);
    }

    /** Blend the RGB of argb towards rgb by t, keeping argb's alpha. */
    static int mix(int argb, int rgb, float t) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 255) * (1 - t) + ((rgb >> 16) & 255) * t);
        int g = (int) (((argb >> 8) & 255) * (1 - t) + ((rgb >> 8) & 255) * t);
        int b = (int) ((argb & 255) * (1 - t) + (rgb & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp(float f) {
        return Math.max(0f, Math.min(1f, f));
    }

    private static void animate(Track t) {
        long now = System.currentTimeMillis();
        float dt = t.lastFrame == 0 ? 0f : Math.min(0.1f, (now - t.lastFrame) / 1000f);
        t.lastFrame = now;
        t.shown += (t.frac - t.shown) * Math.min(1f, dt * 14f);
        if (t.frac > t.trail) t.trail = t.frac; // healing: no trail
        else if (now - t.hitAt > 450) t.trail += (t.shown - t.trail) * Math.min(1f, dt * 3.5f);
        if (t.trail < t.shown) t.trail = t.shown;
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb, int light) {
        vc.addVertex(m, x0, y1, 0f).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y1, 0f).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y0, 0f).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y0, 0f).setColor(argb).setLight(light);
    }

    /** Test/preview access. */
    static Track trackOf(Entity e) {
        return tracks.get(e.getId());
    }

    static int hpOf(Track t) {
        return t.hp;
    }

    static int maxOf(Track t) {
        return t.max;
    }

    static boolean maxExact(Track t) {
        return t.maxExact;
    }
}
