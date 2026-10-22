package com.twoandahalfdevs.dr_improvement_mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mining popups: DR's mining debug lines ("+48 EXP [1205/9000]", "DOUBLE ORE DROP (2x)", "FOUND 3 GEM(S)",
 * ore respawn, durability, level up...) become animated cards that pop off the ore you just mined, stacking
 * upwards like floating combat text. Every line gets its own card.
 */
public final class OreText {
    private OreText() {}

    public enum Kind {
        EXP("✧", 0xB8F55A, false),           // ✧
        DOUBLE("◆", 0xFFC93C, false),        // ◆
        TRIPLE("◆", 0xFF8A3D, true),
        GEM("✦", 0x3CF08C, false),           // ✦
        TREASURE("★", 0xE36BFF, true),       // ★
        RESPAWN("⟳", 0x4FE6FF, false),       // ⟳
        DURABILITY("⚒", 0x7FB8FF, false),    // ⚒
        DURABILITY_LOW("⚠", 0xFF5A4F, false),// ⚠
        LEVEL("⬆", 0xFFE14D, true),          // ⬆
        TIER("★", 0xFFB14D, true),
        FAIL("✖", 0x9A9EA6, false),          // ✖
        OTHER("⚒", 0xD8DCE2, false);

        final String icon;
        final int color;
        final boolean rare;

        Kind(String icon, int color, boolean rare) {
            this.icon = icon;
            this.color = color;
            this.rare = rare;
        }
    }

    /** A parsed line. amount/cur/max are -1 when the line doesn't carry them. */
    public record Event(Kind kind, int amount, int cur, int max, String text) {}

    private static final class Card {
        final Kind kind;
        final long anchor;
        Vec3 pos;
        long born;
        int amount, cur = -1, max = -1;
        String text;
        float stackY = -1, barShown = -1;
        long lastFrame;

        Card(Event e, long anchor, Vec3 pos, long now) {
            this.kind = e.kind;
            this.anchor = anchor;
            this.pos = pos;
            this.born = now;
            this.amount = Math.max(0, e.amount);
            this.cur = e.cur;
            this.max = e.max;
            this.text = e.text;
        }

        float barFrac() {
            return max > 0 ? Math.max(0f, Math.min(1f, cur / (float) max)) : -1f;
        }
    }

    private record Queued(Event event, long at) {}

    private static final List<Card> cards = new ArrayList<>();
    private static final List<Queued> queue = new ArrayList<>();
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);

    // where you were last digging
    private static BlockPos digPos;
    private static Direction digFace = Direction.UP;
    private static long digAt;

    private static String lastOverlay = "";
    private static long lastOverlayAt;

    private static final int MAX_PER_STACK = 8;
    private static final long FREE_ANCHOR = Long.MIN_VALUE;

    public static boolean enabled() {
        return DrConfig.data.oreEnabled;
    }

    // ------------------------------------------------------------------ input

    /** MultiPlayerGameMode start/continueDestroyBlock: remember the block being mined. */
    public static void onDig(BlockPos pos, Direction face) {
        if (pos == null) return;
        digPos = pos.immutable();
        digFace = face == null ? Direction.UP : face;
        digAt = System.currentTimeMillis();
    }

    /**
     * A system chat / action-bar line from the server. Returns true when it became a popup, so the caller can
     * keep it out of chat ("Hide from chat").
     */
    public static boolean onMessage(String raw, boolean overlay) {
        if (raw == null || !enabled()) return false;
        long now = System.currentTimeMillis();
        if (overlay) { // the action bar is re-sent constantly: only react to a new text
            if (raw.equals(lastOverlay) && now - lastOverlayAt < 2500) return false;
            lastOverlay = raw;
            lastOverlayAt = now;
        }
        Event e = classify(raw);
        if (e == null || !allowed(e, now)) return false;
        if (e.kind == Kind.EXP && !DrConfig.data.oreShowExp) return false;
        if (e.kind == Kind.FAIL && !DrConfig.data.oreShowFails) return false;
        spawn(e, now);
        return e.kind != Kind.OTHER; // unrecognised mining text is shortened on the card: keep the full line in chat
    }

    public static boolean hideFromChat() {
        return enabled() && DrConfig.data.oreHideChat;
    }

    private static final Pattern MINING_WORD = Pattern.compile("\\bORES?\\b|PICK|MINING|MINED|\\bMINE\\b");

    /** DR shows the same kind of text for combat (EXP, gems from mobs): only count it right after you mined. */
    private static boolean allowed(Event e, long now) {
        long since = now - digAt;
        boolean mining = MINING_WORD.matcher(e.text.toUpperCase(Locale.ROOT)).find();
        return switch (e.kind) {
            case EXP, OTHER -> since <= 3500;
            case LEVEL, TIER -> since <= 6000 || mining;
            default -> since <= 3500 || mining;
        };
    }

    // ------------------------------------------------------------------ parsing

    private static final Pattern LEVEL_NUM = Pattern.compile("(?:LEVEL|LVL|LV)(?:L?ED)?\\s*(?:UP\\s*)?(?:TO\\s*)?(?:LEVEL\\s*|LV\\s*)?\\[?\\s*(\\d{1,3})");
    private static final Pattern LEVEL_UP = Pattern.compile("LEVEL(?:L?ED)?\\s*UP|REACHED\\s+LEVEL|\\bLVL\\s*UP");
    private static final Pattern TRIPLE = Pattern.compile("\\bTRIPLE\\b|(?:\\b3X\\b|\\bX3\\b).*(?:ORE|DROP)|(?:ORE|DROP).*(?:\\b3X\\b|\\bX3\\b)");
    private static final Pattern DOUBLE = Pattern.compile("\\bDOUBLE\\b|(?:\\b2X\\b|\\bX2\\b).*(?:ORE|DROP)|(?:ORE|DROP).*(?:\\b2X\\b|\\bX2\\b)");
    private static final Pattern GEM_N = Pattern.compile("([\\d,]+)\\s*GEMS?\\b");
    private static final Pattern GEM = Pattern.compile("\\bGEMS?\\s*(?:FIND|FOUND)|\\bFOUND\\b.*\\bGEMS?\\b");
    private static final Pattern RESPAWN = Pattern.compile("RESPAWN|REGENERAT|REPLENISH|REGROW|INSTANT(?:LY)?\\s+(?:RE)?SPAWN|REAPPEAR");
    private static final Pattern DURA_LOW = Pattern.compile("LOW|REPAIR|BROKE|BREAK|\\bNO\\b.*LEFT");
    private static final Pattern FAIL = Pattern.compile("FAIL|MISSED|\\bMISS\\b|UNABLE|COULD\\s?N.?T|NOTHING|NO ORE");
    private static final Pattern EXP = Pattern.compile("^\\+\\s?([\\d,]+)\\s*(?:EXP|XP)\\b(?:.*?\\[\\s*([\\d,]+)\\s*/\\s*([\\d,]+))?");
    private static final Pattern MULT = Pattern.compile("\\((?:X\\s*)?(\\d)\\s*X?\\)");

    /** Turns one DR line into an event, or null if it isn't a mining line. Pure, so the self-test can check it. */
    public static Event classify(String raw) {
        if (raw == null) return null;
        String plain = SplitChat.strip(raw).replaceAll("[*!»«]+", " ").replaceAll("\\s+", " ").trim();
        if (plain.isEmpty() || plain.length() > 90) return null;
        String u = plain.toUpperCase(Locale.ROOT);
        Matcher m;

        boolean leveled = LEVEL_UP.matcher(u).find() || (u.contains("PICK") && u.contains("LEVEL"));
        if (u.contains("TIER") && (u.contains("PICK") || u.contains("UPGRADE"))) return new Event(Kind.TIER, num(LEVEL_NUM, u), -1, -1, plain);
        if (leveled) return new Event(Kind.LEVEL, num(LEVEL_NUM, u), -1, -1, plain);
        if (TRIPLE.matcher(u).find()) return new Event(Kind.TRIPLE, 3, -1, -1, plain);
        if (DOUBLE.matcher(u).find()) return new Event(Kind.DOUBLE, 2, -1, -1, plain);
        if (u.contains("TREASURE")) return new Event(Kind.TREASURE, -1, -1, -1, plain);
        if (!u.contains("POUCH")) {
            m = GEM_N.matcher(u);
            if (m.find()) return new Event(Kind.GEM, parse(m.group(1)), -1, -1, plain);
            if (GEM.matcher(u).find()) return new Event(Kind.GEM, -1, -1, -1, plain);
        }
        if (RESPAWN.matcher(u).find()) return new Event(Kind.RESPAWN, -1, -1, -1, plain);
        if (u.contains("DURABILITY")) return new Event(DURA_LOW.matcher(u).find() ? Kind.DURABILITY_LOW : Kind.DURABILITY, -1, -1, -1, plain);
        m = EXP.matcher(u);
        if (m.find()) {
            int cur = m.group(2) == null ? -1 : parse(m.group(2));
            int max = m.group(3) == null ? -1 : parse(m.group(3));
            return new Event(Kind.EXP, parse(m.group(1)), cur, max, plain);
        }
        if (FAIL.matcher(u).find() && MINING_WORD.matcher(u).find() || u.contains("EXTRACT") && FAIL.matcher(u).find())
            return new Event(Kind.FAIL, -1, -1, -1, plain);
        if (MINING_WORD.matcher(u).find() && plain.length() <= 48) return new Event(Kind.OTHER, -1, -1, -1, plain);
        return null;
    }

    private static int num(Pattern p, String u) {
        Matcher m = p.matcher(u);
        return m.find() ? parse(m.group(1)) : -1;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ spawning

    private static void spawn(Event e, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long anchor;
        Vec3 at;
        if (digPos != null && now - digAt < 8000) {
            anchor = digPos.asLong();
            at = anchorPoint(digPos, digFace);
        } else if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            anchor = bh.getBlockPos().asLong();
            at = anchorPoint(bh.getBlockPos(), bh.getDirection());
        } else {
            anchor = FREE_ANCHOR;
            at = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(2.5)).add(0, -0.3, 0);
        }

        // every line gets its own card (no merging)
        cards.add(new Card(e, anchor, at, now));
        int n = 0;
        for (int i = cards.size() - 1; i >= 0; i--) {
            if (cards.get(i).anchor == anchor && ++n > MAX_PER_STACK) cards.remove(i);
        }
        if (cards.size() > 40) cards.remove(0);
        sound(e.kind);
    }

    /** Just outside the face you hit, a little above its centre. */
    private static Vec3 anchorPoint(BlockPos pos, Direction face) {
        Vec3 c = Vec3.atCenterOf(pos);
        Vec3 out = face.getAxis() == Direction.Axis.Y ? Vec3.ZERO : new Vec3(face.getStepX(), 0, face.getStepZ()).scale(0.62);
        double up = face == Direction.DOWN ? -0.75 : face == Direction.UP ? 0.7 : 0.35;
        return c.add(out).add(0, up, 0);
    }

    private static void sound(Kind k) {
        if (!DrConfig.data.oreSounds) return;
        Minecraft mc = Minecraft.getInstance();
        switch (k) {
            case TRIPLE -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.9f));
            case TREASURE -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.15f, 1.0f));
            case LEVEL, TIER -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.3f, 0.45f));
            default -> {}
        }
    }

    private static long lifeMs(Card c) {
        long base = Math.round(DrConfig.data.oreDuration * 1000);
        return c.kind.rare ? base + 1000 : c.kind == Kind.FAIL ? Math.max(600, base * 2 / 3) : base;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            cards.clear();
            queue.clear();
            digPos = null;
            return;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Queued> it = queue.iterator(); it.hasNext(); ) {
            Queued q = it.next();
            if (now >= q.at) {
                it.remove();
                spawn(q.event, now);
            }
        }
        cards.removeIf(c -> now - c.born > lifeMs(c));
    }

    // ------------------------------------------------------------------ drawing

    private static final int BODY_TOP = 0xE61A1F27, BODY_BOT = 0xE610131A;

    public static void render(Camera camera) {
        if (!enabled() || cards.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Vec3 cam = camera.getPosition();
        long now = System.currentTimeMillis();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();

        // group by ore: newest card sits on the ore, older ones get pushed up
        Map<Long, List<Card>> stacks = new LinkedHashMap<>();
        for (Card c : cards) stacks.computeIfAbsent(c.anchor, k -> new ArrayList<>()).add(c);

        for (List<Card> stack : stacks.values()) {
            float target = 0;
            for (int i = stack.size() - 1; i >= 0; i--) {
                Card c = stack.get(i);
                float dt = c.lastFrame == 0 ? 1f : Math.min(1f, (now - c.lastFrame) / 1000f * 14f);
                c.lastFrame = now;
                c.stackY = c.stackY < 0 ? target : c.stackY + (target - c.stackY) * dt;
                float frac = c.barFrac();
                if (frac >= 0) c.barShown = c.barShown < 0 ? Math.max(0f, frac - 0.08f) : c.barShown + (frac - c.barShown) * Math.min(1f, dt * 0.5f);
                target += height(c) + 3;
                drawCard(c, font, camera, cam, pose, buffers, now);
            }
        }
        buffers.endBatch();
    }

    private static float height(Card c) {
        return 15 + (sub(c) != null ? 7 : 0) + (c.barFrac() >= 0 ? 1 : 0);
    }

    private static String title(Card c) {
        return switch (c.kind) {
            case EXP -> "+" + NUM.format(c.amount) + " XP";
            case DOUBLE -> "DOUBLE ORE";
            case TRIPLE -> "TRIPLE ORE";
            case GEM -> c.amount > 0 ? "+" + NUM.format(c.amount) + (c.amount == 1 ? " GEM" : " GEMS") : "GEM FIND";
            case TREASURE -> "TREASURE FIND";
            case RESPAWN -> "ORE RESPAWN";
            case DURABILITY -> "DURABILITY SAVED";
            case DURABILITY_LOW -> "LOW DURABILITY";
            case LEVEL -> c.amount > 0 ? "PICKAXE LV " + c.amount : "LEVEL UP";
            case TIER -> "PICKAXE TIER UP";
            case FAIL -> "MISSED";
            case OTHER -> shorten(c.text, 34);
        };
    }

    /** Small grey second line. */
    private static String sub(Card c) {
        if (c.kind == Kind.EXP && c.max > 0) {
            int pct = Math.round(100f * c.cur / c.max);
            return NUM.format(c.cur) + " / " + NUM.format(c.max) + "  ·  " + Math.max(0, Math.min(100, pct)) + "%";
        }
        if (c.kind == Kind.DURABILITY_LOW || c.kind == Kind.TIER) return shorten(c.text, 40);
        return null;
    }

    private static String shorten(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private static void drawCard(Card c, Font font, Camera camera, Vec3 cam, PoseStack pose, MultiBufferSource buffers, long now) {
        long age = now - c.born;
        long life = lifeMs(c);
        long left = life - age;
        boolean rare = c.kind.rare;

        // pop in (back-out overshoot), small bump when merged, shrink + fade out at the end
        float inT = Math.min(1f, age / (rare ? 320f : 230f));
        float pop = backOut(inT, rare ? 2.6f : 1.9f);
        float start = rare ? 0.35f : 0.55f;
        float scale = start + (1f - start) * pop;
        float outT = left < 380 ? 1f - Math.max(0, left) / 380f : 0f;
        scale *= 1f - 0.12f * outT;
        float alpha = Math.min(1f, age / 90f) * (1f - outT);
        if (alpha <= 0.02f) return;
        if (rare) scale *= 1.15f;

        float rise = (float) (Math.min(1.0, age / (double) life) * 0.18); // a gentle drift up over its life
        Vec3 p = c.pos.add(0, rise, 0);
        double dist = Math.sqrt(p.distanceToSqr(cam));
        float k = 0.0185f * (float) DrConfig.data.oreSize * scale * (float) Math.max(1.0, Math.min(2.6, dist / 5.0));

        String icon = c.kind.icon;
        FormattedCharSequence iconSeq = Component.literal(icon).getVisualOrderText();
        FormattedCharSequence titleSeq = Component.literal(title(c)).withStyle(ChatFormatting.BOLD).getVisualOrderText();
        String s = sub(c);
        FormattedCharSequence subSeq = s == null ? null : Component.literal(s).getVisualOrderText();
        float subScale = 0.7f;

        int iconW = font.width(iconSeq);
        int titleW = font.width(titleSeq);
        float rowW = iconW + 4 + titleW;
        float subW = subSeq == null ? 0 : font.width(subSeq) * subScale;
        float inner = Math.max(rowW, subW);
        float w = inner + 6 + 6; // 2px accent + padding
        float h = height(c);
        float x0 = -w / 2f, x1 = w / 2f;
        float slide = (1f - easeOut(inT)) * 7f;
        float y1 = -c.stackY + slide, y0 = y1 - h;

        pose.pushPose();
        pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
        pose.mulPose(camera.rotation());
        pose.scale(k, -k, k);
        Matrix4f m = pose.last().pose();
        int bright = LightTexture.FULL_BRIGHT;
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());

        int accent = c.kind.color;
        int edge = MobHp.mix(0xFF000000 | accent, 0x000000, 0.55f) & 0xFFFFFF;
        // Translucent quads get depth-sorted, so the card is built from pieces that never overlap.
        float barH = c.barFrac() >= 0 ? 2 : 1;
        quad(vc, m, x0, y0 + 1, x0 + 2, y1 - 1, argb(alpha, accent), bright);                 // accent strip
        quad(vc, m, x0 + 2, y0, x1 - 1, y0 + 1, argb(alpha * 0.9f, MobHp.mix(0xFF000000 | accent, 0xFFFFFF, 0.1f) & 0xFFFFFF), bright); // top rim
        quad(vc, m, x1 - 1, y0 + 1, x1, y1 - barH, argb(alpha * 0.85f, edge), bright);           // right rim
        float mid = y0 + 1 + (y1 - barH - y0 - 1) * 0.5f;
        quad(vc, m, x0 + 2, y0 + 1, x1 - 1, mid, scaleAlpha(BODY_TOP, alpha), bright);           // body, light half
        quad(vc, m, x0 + 2, mid, x1 - 1, y1 - barH, scaleAlpha(BODY_BOT, alpha), bright);        // body, dark half
        if (c.barFrac() >= 0) { // XP progress along the bottom edge
            float fx = x0 + 2 + (x1 - x0 - 2) * Math.max(0f, Math.min(1f, c.barShown));
            if (fx > x0 + 2) quad(vc, m, x0 + 2, y1 - barH, fx, y1, argb(alpha, accent), bright);
            if (fx < x1) quad(vc, m, fx, y1 - barH, x1, y1, argb(alpha * 0.85f, 0x2A303A), bright);
        } else {
            quad(vc, m, x0 + 2, y1 - 1, x1 - 1, y1, argb(alpha * 0.85f, edge), bright);         // bottom rim
        }
        // glow ring when it appears (bigger and longer for rare finds)
        float flash = 1f - Math.min(1f, age / (rare ? 650f : 300f));
        if (flash > 0.01f) {
            int g = argb(alpha * flash * 0.9f, accent);
            float r = rare ? 2 : 1;
            quad(vc, m, x0, y0 - r, x1, y0, g, bright);
            quad(vc, m, x0, y1, x1, y1 + r, g, bright);
            quad(vc, m, x0 - r, y0, x0, y1, g, bright);
            quad(vc, m, x1, y0, x1 + r, y1, g, bright);
        }

        // text
        float tx = x0 + 6;
        float ty = y0 + 4;
        int ta = Math.max(8, Math.round(alpha * 255)) << 24;
        text(font, iconSeq, tx, ty, ta | accent, buffers, m);
        text(font, titleSeq, tx + iconW + 4, ty, ta | (c.kind == Kind.FAIL ? 0xC4C8CF : MobHp.mix(0xFF000000 | accent, 0xFFFFFF, 0.55f) & 0xFFFFFF), buffers, m);
        if (subSeq != null) {
            pose.pushPose();
            pose.translate(tx, ty + 10, 0);
            pose.scale(subScale, subScale, 1f);
            text(font, subSeq, 0, 0, ta | 0x9AA3AF, buffers, pose.last().pose());
            pose.popPose();
        }
        pose.popPose();
    }

    private static void text(Font font, FormattedCharSequence seq, float x, float y, int color, MultiBufferSource buffers, Matrix4f m) {
        font.drawInBatch(seq, x, y, color, true, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb, int light) {
        if (x1 - x0 < 0.01f || y1 - y0 < 0.01f) return;
        vc.addVertex(m, x0, y1, 0f).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y1, 0f).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y0, 0f).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y0, 0f).setColor(argb).setLight(light);
    }

    private static int argb(float a, int rgb) {
        return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | (rgb & 0xFFFFFF);
    }

    private static int scaleAlpha(int argb, float a) {
        return (Math.round((argb >>> 24) * a) << 24) | (argb & 0xFFFFFF);
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float backOut(float t, float s) {
        float u = t - 1f;
        return 1f + (s + 1f) * u * u * u + s * u * u;
    }

    // ------------------------------------------------------------------ test / preview

    /** Settings screen "Test": a short mining sequence on the block you're looking at. */
    public static void test() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) onDig(bh.getBlockPos(), bh.getDirection());
        else onDig(BlockPos.containing(mc.player.getEyePosition().add(mc.player.getLookAngle().scale(3))), mc.player.getDirection().getOpposite());
        long now = System.currentTimeMillis();
        String[] lines = {"+48 EXP [1205/9000 EXP]", "DOUBLE ORE DROP (2x)", "FOUND 3 GEM(S)", "+52 EXP [1257/9000 EXP]",
            "TRIPLE ORE DROP (3x)", "Ore respawned!"};
        for (int i = 0; i < lines.length; i++) {
            Event e = classify(lines[i]);
            if (e != null) queue.add(new Queued(e, now + i * 260L));
        }
        digAt = now + lines.length * 260L; // keep the anchor alive through the sequence
    }

    static int count() {
        return cards.size();
    }

    static int countKind(Kind k) {
        int n = 0;
        for (Card c : cards) if (c.kind == k) n++;
        return n;
    }
}
