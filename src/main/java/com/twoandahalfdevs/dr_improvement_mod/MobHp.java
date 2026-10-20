package com.twoandahalfdevs.dr_improvement_mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
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

    public record Display(Component name, String numbers, Track track) {}

    public static final class Track {
        Component name;          // last plain (un-hit) name, with DR's colours
        String level = "";
        float frac = 1f;         // drawn fraction: exact hp/max when known, else the |||| bar
        float pipe = 1f;         // last |||| bar fraction
        long fracChanged;
        long plainAt;            // last time the plain name was shown (mob at full HP)
        int hp = -1;             // exact, from damage messages
        long hpAt;
        int max = -1;
        boolean maxExact;
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
    private static final Map<String, Integer> maxByKind = new HashMap<>();
    private static final Pattern DMG = Pattern.compile("(\\d+) \\S*DMG -> (.+?) \\[(\\d+) HP]");
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
        long now = System.currentTimeMillis();
        Track t = tracks.computeIfAbsent(e.getId(), k -> new Track());
        t.seen = now;
        t.pos = e.position();
        t.height = e.getBbHeight();
        EnemyHp.Parsed p = EnemyHp.parse(nameTag);
        if (p == null) { // plain "Name [Lv 90]": full health, not in combat
            t.name = nameTag;
            Matcher m = LEVEL.matcher(SplitChat.strip(nameTag.getString()));
            if (m.find()) t.level = m.group(1);
            if (t.hp >= 0 && now - t.hpAt < 3000) {
                // just hit, DR hasn't swapped the name for its bar yet: show ours straight away
                if (t.max > 0) t.frac = Math.min(1f, t.hp / (float) t.max);
                return new Display(nameTag, numbers(t, now), t);
            }
            t.plainAt = now;
            t.frac = t.pipe = 1f;
            t.hp = -1;
            t.shown = t.trail = 1f;
            return null;
        }
        float f = (float) p.fraction();
        if (Math.abs(f - t.pipe) > 1e-4) {
            if (f < t.pipe) t.hitAt = now;
            t.pipe = f;
            t.fracChanged = now;
        }
        boolean exact = t.hp >= 0 && t.max > 0 && t.hpAt >= t.fracChanged - 1500;
        t.frac = exact ? Math.min(1f, t.hp / (float) t.max) : f;
        Matcher m = LEVEL.matcher(p.after());
        if (m.find()) t.level = m.group(1);

        Component name = t.name;
        if (name == null) { // came into view already damaged: fall back to the mob type
            String lv = p.after().isEmpty() ? "" : " " + p.after();
            name = Component.literal(e.getType().getDescription().getString() + lv);
        }
        if (t.max < 0 && t.name != null) {
            Integer known = maxByKind.get(kind(t));
            if (known != null) t.max = known;
        }
        return new Display(name, numbers(t, now), t);
    }

    private static String kind(Track t) {
        return SplitChat.strip(t.name.getString()) + "|" + t.level;
    }

    public static void onAttack(Entity e) {
        lastAttacked = e.getId();
        lastAttackAt = System.currentTimeMillis();
    }

    public static void onChat(String raw) {
        if (raw == null) return;
        Matcher m = DMG.matcher(SplitChat.strip(raw));
        if (!m.find()) return;
        int dmg = Integer.parseInt(m.group(1));
        String target = m.group(2).trim();
        int left = Integer.parseInt(m.group(3));
        long now = System.currentTimeMillis();

        Track t = null;
        if (now - lastAttackAt < 1500) t = tracks.get(lastAttacked);
        if (t == null) t = byName(target, now); // staff / bow hits don't go through attack()
        if (t == null) return;
        FloatingText.fromChat(dmg, t.pos, t.height);

        boolean wasFull = t.hp < 0 && now - t.plainAt < 2000;
        t.hp = left;
        t.hpAt = now;
        t.hitAt = now;
        if (wasFull && (!t.maxExact || t.max < left + dmg)) {
            t.max = left + dmg;
            t.maxExact = true;
            if (t.name != null) maxByKind.put(kind(t), t.max);
        } else if (t.max >= 0 && left > t.max) {
            t.max = left; // regenerated or estimate was low
            t.maxExact = false;
        }
    }

    private static Track byName(String target, long now) {
        Vec3 me = Minecraft.getInstance().player == null ? Vec3.ZERO : Minecraft.getInstance().player.position();
        Track best = null;
        double bestD = 40 * 40;
        for (Track t : tracks.values()) {
            if (now - t.seen > 5000 || t.name == null) continue;
            String n = SplitChat.strip(t.name.getString());
            if (!n.contains(target) && !target.contains(n.replaceAll("\\s*\\[Lv.*", "").trim())) continue;
            double d = t.pos.distanceToSqr(me);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
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
        boolean freshHp = t.hp >= 0 && t.hpAt >= t.fracChanged - 1500;
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

    /** Draws the name + bar in place of the vanilla nametag. Mirrors EntityRenderer#renderNameTag's transform. */
    public static void render(Display d, Vec3 attach, PoseStack pose, MultiBufferSource buffers, int light) {
        if (attach == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Track t = d.track();
        animate(t);

        int w = DrConfig.data.enemyHpBarWidth;
        int h = DrConfig.data.enemyHpBarHeight;
        pose.pushPose();
        pose.translate(attach.x, attach.y + 0.5, attach.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025f, -0.025f, 0.025f);
        Matrix4f m = pose.last().pose();
        int bright = LightTexture.FULL_BRIGHT;

        // bar: border, background, damage trail, fill, shine/shade
        float x0 = -w / 2f, x1 = w / 2f, y0 = 0, y1 = h;
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
        // Translucent quads are depth-sorted on upload, so overlapping layers would swap order with the viewing
        // angle. Draw non-overlapping pieces instead: fill | damage trail | empty, each in three shaded bands.
        float fillEnd = x0 + w * clamp(t.shown);
        float trailEnd = Math.max(fillEnd, x0 + w * clamp(t.trail));
        bands(vc, m, x0, fillEnd, y0, y1, h, fillColor(t.frac), bright);
        bands(vc, m, fillEnd, trailEnd, y0, y1, h, TRAIL, bright);
        bands(vc, m, trailEnd, x1, y0, y1, h, BACK, bright);
        quad(vc, m, x0 - 1, y0 - 1, x1 + 1, y0, BORDER, bright);
        quad(vc, m, x0 - 1, y1, x1 + 1, y1 + 1, BORDER, bright);
        quad(vc, m, x0 - 1, y0, x0, y1, BORDER, bright);
        quad(vc, m, x1, y0, x1 + 1, y1, BORDER, bright);

        // name above the bar (DR's own colours), drawn like a vanilla nametag
        Component name = d.name();
        float nx = -font.width(name) / 2f;
        float ny = -10f;
        int bg = (int) (mc.options.getBackgroundOpacity(0.25f) * 255f) << 24;
        font.drawInBatch(name, nx, ny, 0x80FFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, bg, light);
        font.drawInBatch(name, nx, ny, 0xFFFFFFFF, false, m, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.lightCoordsWithEmission(light, 2));

        // numbers centred on the bar, outlined like WoW
        if (!"NONE".equals(DrConfig.data.enemyHpText)) {
            float s = Math.max(0.5f, Math.min(1.2f, (h - 1) / 8f));
            pose.pushPose();
            pose.translate(0, h / 2f, 0);
            pose.scale(s, s, 1f);
            Matrix4f tm = pose.last().pose();
            var seq = Component.literal(d.numbers()).getVisualOrderText();
            float tx = -font.width(seq) / 2f;
            // see-through copy keeps the numbers readable when the mob is behind blocks (like the bar itself)
            font.drawInBatch(seq, tx, -4f, 0xD0FFFFFF, false, tm, buffers, Font.DisplayMode.SEE_THROUGH, 0, bright);
            font.drawInBatch8xOutline(seq, tx, -4f, 0xFFFFFFFF, 0xFF000000, tm, buffers, bright);
            pose.popPose();
        }
        pose.popPose();
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
