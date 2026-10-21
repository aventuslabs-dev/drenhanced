package com.twoandahalfdevs.dr_improvement_mod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WoW-style floating combat text. Picks up DungeonRealms' own damage holograms (invisible armour stands named
 * "-1074 ❤" / "+125 ❤"), hides them, and draws animated numbers in their place. Hits that DR reports only
 * in chat ("120 DMG -> Name [HP]") are shown over that mob if no hologram turns up for them.
 */
public final class FloatingText {
    private FloatingText() {}

    public enum Kind {OUT, CRIT, IN, HEAL, THORNS, OTHER}

    private static final class Num {
        final String text;
        Kind kind;
        String label;      // proc above the number, e.g. "Shattered"
        final int amount;
        final Vec3 pos;
        final double driftX, driftZ;
        final long born;

        Num(String text, int amount, Kind kind, Vec3 pos, double driftX, double driftZ, long born) {
            this.text = text;
            this.amount = amount;
            this.kind = kind;
            this.pos = pos;
            this.driftX = driftX;
            this.driftZ = driftZ;
            this.born = born;
        }
    }

    /** A chat-reported hit waiting briefly in case DR's hologram for it shows up. */
    private record Pending(int amount, Vec3 pos, Kind kind, String label, long at) {}

    /**
     * A DR damage hologram away from you. DR shows everyone's hits, so it only becomes one of YOUR numbers
     * when one of your own "DMG ->" chat lines (chat is yours only) has the same amount.
     */
    private record Holo(int amount, Vec3 pos, long at) {}

    private static final List<Holo> holoWait = new ArrayList<>();
    /** How long a hologram and your chat line may be apart and still be the same hit. */
    private static final long PAIR_MS = 450;

    /** DR sends "* CRIT *" (and proc lines like "* SHATTERED *") a few ms before the damage line they belong to. */
    private static long critAt;
    private static String procLabel;
    private static long procAt;
    private static final Pattern MARKER = Pattern.compile("^\\*\\s*(.+?)\\s*\\*$");
    /** amount -> time of a damage line flagged crit (to upgrade a hologram number that arrived first). */
    private static final Map<Integer, Long> critAmounts = new HashMap<>();

    private static final List<Num> nums = new ArrayList<>();
    private static final List<Pending> pending = new ArrayList<>();
    private static final Set<Integer> seen = new HashSet<>();
    private static final Set<Integer> hidden = new HashSet<>();
    private static final Map<Integer, Long> holoValues = new HashMap<>(); // amount -> last time seen as hologram
    private static final Pattern HOLO = Pattern.compile("^([+-])\\s?([\\d,]+)\\s*(?:❤|HP)?\\s*$");
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);
    private static final Random RNG = new Random();

    public static boolean enabled() {
        return DrConfig.data.fctEnabled;
    }

    // ------------------------------------------------------------------ sources

    /** Every client tick: look for new DR damage holograms. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            seen.clear();
            hidden.clear();
            nums.clear();
            pending.clear();
            holoWait.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Set<Integer> alive = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            Component name;
            if (e instanceof ArmorStand) name = e.getCustomName();
            else if (e instanceof net.minecraft.world.entity.Display.TextDisplay td) name = textOf(td); // DR's current floating text
            else continue;
            int id = e.getId();
            alive.add(id);
            if (seen.contains(id)) continue;
            if (name == null) continue;
            Matcher m = HOLO.matcher(SplitChat.strip(name.getString()).trim());
            if (!m.find()) continue;
            seen.add(id);
            hidden.add(id);
            int amount = parse(m.group(2));
            boolean heal = "+".equals(m.group(1));
            Vec3 at = e instanceof ArmorStand as ? e.position().add(0, as.isMarker() ? 0.3 : e.getBbHeight() + 0.3, 0) : e.position();
            boolean nearMe = at.distanceToSqr(mc.player.getEyePosition()) < 2.5 * 2.5;
            holoValues.put(amount, now);
            if (nearMe) { // heals on you / damage you take
                if (enabled()) spawn(amount, heal ? Kind.HEAL : Kind.IN, null, at, now);
            } else if (heal) { // someone else being healed
                if (enabled() && DrConfig.data.fctOthers) spawn(amount, Kind.OTHER, null, at, now);
            } else {
                holoWait.add(new Holo(amount, at, now)); // yours only if your chat confirms it
            }
        }
        seen.retainAll(alive);
        hidden.retainAll(alive);
        holoValues.values().removeIf(t -> now - t > 3000);
        critAmounts.values().removeIf(t -> now - t > 3000);

        // your hits (chat) paired with DR's hologram for them: shown at the hologram's spot, with crit/proc info
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.kind == Kind.IN) { // "-35 HP (Attacker)": skip if the hologram above you already showed it
                Long h = holoValues.get(p.amount);
                if (h != null && Math.abs(h - p.at) < 800) it.remove();
                else if (now - p.at > 250) {
                    it.remove();
                    if (enabled()) spawn(p.amount, p.kind, p.label, p.pos, now);
                }
                continue;
            }
            Holo match = null;
            for (Holo h : holoWait) {
                if (h.amount == p.amount && Math.abs(h.at - p.at) < PAIR_MS) {
                    match = h;
                    break;
                }
            }
            if (match != null) {
                holoWait.remove(match);
                it.remove();
                if (enabled()) spawn(p.amount, p.kind, p.label, match.pos, now);
            } else if (now - p.at > PAIR_MS) { // no hologram (or it was culled): show it over the mob
                it.remove();
                if (enabled()) spawn(p.amount, p.kind, p.label, p.pos, now);
            }
        }
        // holograms nobody claimed: another player's hit
        for (Iterator<Holo> it = holoWait.iterator(); it.hasNext(); ) {
            Holo h = it.next();
            if (now - h.at > PAIR_MS) {
                it.remove();
                if (enabled() && DrConfig.data.fctOthers) spawn(h.amount, Kind.OTHER, null, h.pos, now);
            }
        }
        long life = lifeMillis();
        nums.removeIf(n -> now - n.born > life);
    }

    /** Called by MobHp when a "DMG -> Name [HP]" line was matched to a mob at {@code pos} (feet). */
    public static void fromChat(int amount, Vec3 pos, double height, String source) {
        if (pos == null) return;
        long now = System.currentTimeMillis();
        boolean crit = source == null && now - critAt < 150;
        if (crit) critAt = 0; // a crit marker belongs to one hit
        String label = source == null && now - procAt < 150 ? procLabel : source;
        if (source == null && label != null) procAt = 0;
        Kind kind = crit && DrConfig.data.fctBigHits ? Kind.CRIT : source != null ? Kind.THORNS : Kind.OUT;
        if (crit) {
            critAmounts.put(amount, now);
            // the hologram for this hit may already be on screen: upgrade it
            for (Num n : nums) {
                if (n.amount == amount && n.kind == Kind.OUT && now - n.born < 800) {
                    n.kind = Kind.CRIT;
                    n.label = label;
                }
            }
        }
        pending.add(new Pending(amount, pos.add(0, height + 0.4, 0), kind, label, now));
    }

    private static final Pattern IN_LINE = Pattern.compile("-(\\d+) \\S*HP \\((.+)\\)");

    /** DR chat lines that aren't tied to a tracked mob: "-35 HP (Attacker)" = damage you took. */
    public static void onChatLine(String raw) {
        if (raw == null) return;
        String txt = SplitChat.strip(raw).trim();
        Matcher mk = MARKER.matcher(txt);
        if (mk.find()) {
            String what = mk.group(1).toUpperCase(Locale.ROOT);
            long now = System.currentTimeMillis();
            if (what.equals("CRIT")) critAt = now;
            else if (what.startsWith("TARGET ") || what.equals("SHATTERED")) {
                String w = what.startsWith("TARGET ") ? what.substring(7) : what;
                procLabel = w.charAt(0) + w.substring(1).toLowerCase(Locale.ROOT);
                procAt = now;
            }
            return;
        }
        Matcher m = IN_LINE.matcher(txt);
        if (m.find()) incomingFromChat(parse(m.group(1)));
    }

    private static Component textOf(net.minecraft.world.entity.Display.TextDisplay td) {
        try {
            return td.getEntityData().get(com.twoandahalfdevs.dr_improvement_mod.mixin.TextDisplayAccessor.drim$textId());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Text of a DR text-display entity (also used by capture mode). */
    public static Component displayText(Entity e) {
        return e instanceof net.minecraft.world.entity.Display.TextDisplay td ? textOf(td) : null;
    }

    /** "-35 HP (Attacker)": damage you took. */
    public static void incomingFromChat(int amount) {
        var p = Minecraft.getInstance().player;
        if (p == null || !DrConfig.data.fctIncoming) return;
        pending.add(new Pending(amount, p.position().add(0, p.getBbHeight() + 0.5, 0), Kind.IN, null, System.currentTimeMillis()));
    }

    private static void spawn(int amount, Kind kind, String label, Vec3 at, long now) {
        if (kind == Kind.HEAL && !DrConfig.data.fctHeals) return;
        if (kind == Kind.IN && !DrConfig.data.fctIncoming) return;
        String text = switch (kind) {
            case HEAL -> "+" + NUM.format(amount);
            case IN -> "-" + NUM.format(amount);
            case CRIT -> NUM.format(amount) + "!";
            case THORNS -> NUM.format(amount);
            default -> NUM.format(amount);
        };
        double a = RNG.nextDouble() * Math.PI * 2;
        double r = 0.25 + RNG.nextDouble() * 0.35;
        Num num = new Num(text, amount, kind, at.add((RNG.nextDouble() - 0.5) * 0.4, (RNG.nextDouble() - 0.5) * 0.2, (RNG.nextDouble() - 0.5) * 0.4),
                Math.cos(a) * r, Math.sin(a) * r, now);
        num.label = label;
        nums.add(num);
        if (nums.size() > 60) nums.remove(0);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** DR's own hologram is replaced by ours, so don't draw it. */
    public static boolean isHidden(Entity e) {
        return enabled() && DrConfig.data.fctHideDr && hidden.contains(e.getId());
    }

    private static long lifeMillis() {
        return Math.round(DrConfig.data.fctDuration * 1000);
    }

    // ------------------------------------------------------------------ drawing

    public static void render(Camera camera) {
        if (!enabled() || nums.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Vec3 cam = camera.getPosition();
        long now = System.currentTimeMillis();
        long life = lifeMillis();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        float base = (float) DrConfig.data.fctSize;
        for (Num n : nums) {
            float t = Math.min(1f, (now - n.born) / (float) life);
            // ease-out rise and sideways drift, quick pop at the start, fade over the last 40%
            float ease = 1f - (1f - t) * (1f - t);
            double rise = ease * 1.1;
            Vec3 p = n.pos.add(n.driftX * ease, rise, n.driftZ * ease);
            float pop = n.kind == Kind.CRIT ? 1.6f : n.kind == Kind.OTHER ? 0.65f : 1.0f;
            float popT = Math.min(1f, (now - n.born) / 140f);
            float s = pop * (1f + (n.kind == Kind.CRIT ? 0.9f : 0.5f) * (1f - popT));
            float alpha = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
            if (n.kind == Kind.OTHER) alpha *= 0.75f;
            int a = Math.max(8, Math.round(alpha * 255)) << 24;
            int color = switch (n.kind) {
                case CRIT -> 0xFFC02A;
                case IN -> 0xFF3B3B;
                case HEAL -> 0x3DFF5A;
                case THORNS -> 0xC79BFF;
                case OTHER -> 0x9A9A9A;
                default -> 0xFFFFFF;
            };
            // keep a readable size at range, like WoW's screen-space text
            double dist = Math.sqrt(p.distanceToSqr(cam));
            float k = 0.025f * base * s * (float) Math.max(1.0, dist / 7.0);

            pose.pushPose();
            pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(k, -k, k);
            Matrix4f m = pose.last().pose();
            FormattedCharSequence seq = Component.literal(n.text).getVisualOrderText();
            float x = -font.width(seq) / 2f;
            if (DrConfig.data.fctThroughWalls) {
                font.drawInBatch(seq, x, 0, a | color, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            }
            font.drawInBatch8xOutline(seq, x, 0, a | color, a, m, buffers, LightTexture.FULL_BRIGHT);
            if (n.label != null && DrConfig.data.fctProcs) { // "Shattered", "Cleaved", "Thorns" above the number
                pose.pushPose();
                pose.translate(0, -8f, 0);
                pose.scale(0.6f, 0.6f, 1f);
                Matrix4f lm = pose.last().pose();
                FormattedCharSequence ls = Component.literal(n.label).getVisualOrderText();
                font.drawInBatch8xOutline(ls, -font.width(ls) / 2f, 0, a | 0xFFE9A0, a, lm, buffers, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** Sample numbers in front of the player for the settings screen's Test button. */
    public static void test() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        long now = System.currentTimeMillis();
        Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(3.5));
        spawn(523, Kind.OUT, null, front.add(-0.6, 0, 0), now);
        spawn(1105, Kind.CRIT, "Shattered", front.add(0, 0.2, 0), now);
        spawn(36, Kind.THORNS, "Thorns", front.add(0.6, -0.3, 0), now);
        spawn(125, Kind.HEAL, null, front.add(0.7, -0.2, 0), now);
        spawn(43, Kind.IN, null, p.position().add(0, p.getBbHeight() + 0.5, 0), now);
    }

    public static int countKind(Kind k) {
        int c = 0;
        for (Num n : nums) if (n.kind == k) c++;
        return c;
    }

    static int count() {
        return nums.size();
    }

    static boolean hiddenId(int id) {
        return hidden.contains(id);
    }
}
