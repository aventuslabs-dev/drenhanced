package eu.the5zig.fabric.hud;

import eu.the5zig.fabric.Config;
import eu.the5zig.fabric.gui.EditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Lays out and draws the module boxes, like the original ModuleMaster/Module renderers. */
public final class HudRenderer {
    private HudRenderer() {}

    private static final int MARGIN = 2;
    private static final int GAP = 6;
    private static final int LABEL_H = 12;
    private static final int TEXT_H = 10;
    private static final int STACK_H = 17;
    private static final int POTION_H = 12;

    /** One drawable line inside a module. */
    private interface Row {
        int width(Font f);

        int height();

        void draw(GuiGraphics g, Font f, int x, int y, int width, boolean right, boolean shadow);
    }

    private record TextRow(String text) implements Row {
        public int width(Font f) {
            return f.width(text);
        }

        public int height() {
            return TEXT_H;
        }

        public void draw(GuiGraphics g, Font f, int x, int y, int width, boolean right, boolean shadow) {
            int tx = right ? x + width - f.width(text) : x;
            g.drawString(f, text, tx, y, 0xFFFFFFFF, shadow);
        }
    }

    private record StackRow(ItemStack stack, String text) implements Row {
        public int width(Font f) {
            return text.isEmpty() ? 16 : 18 + f.width(text);
        }

        public int height() {
            return STACK_H;
        }

        public void draw(GuiGraphics g, Font f, int x, int y, int width, boolean right, boolean shadow) {
            int w = width(f);
            int ix, tx;
            if (right) {
                ix = x + width - 16;
                tx = x + width - w;
            } else {
                ix = x;
                tx = x + 18;
            }
            g.renderItem(stack, ix, y);
            g.renderItemDecorations(f, stack, ix, y);
            if (!text.isEmpty()) g.drawString(f, text, tx, y + 5, 0xFFFFFFFF, shadow);
        }
    }

    private record PotionRow(MobEffectInstance effect, String text) implements Row {
        public int width(Font f) {
            return 12 + f.width(text);
        }

        public int height() {
            return POTION_H;
        }

        public void draw(GuiGraphics g, Font f, int x, int y, int width, boolean right, boolean shadow) {
            int w = width(f);
            int ix = right ? x + width - 9 : x;
            int tx = right ? x + width - w : x + 12;
            TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effect.getEffect());
            g.blitSprite(RenderType::guiTextured, sprite, ix, y - 1, 9, 9);
            g.drawString(f, text, tx, y, 0xFFFFFFFF, shadow);
        }
    }

    /** A module after layout, in screen (GUI-scaled) pixels. Used by the editor for hit-testing. */
    public record Placed(Config.Module module, int x, int y, int w, int h, boolean right) {
        public boolean contains(double mx, double my) {
            return mx >= x - 2 && mx <= x + w + 2 && my >= y - 2 && my <= y + h + 2;
        }
    }

    private record Built(Config.Module module, List<Row> rows, String label, int innerW, int innerH) {
        int w() {
            return Math.round(innerW * module.scale);
        }

        int h() {
            return Math.round(innerH * module.scale);
        }
    }

    // ------------------------------------------------------------------

    public static void renderHud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Config cfg = Config.get();
        if (!cfg.enabled || mc.player == null || mc.options.hideGui) return;
        if (mc.screen instanceof EditorScreen) return; // the editor draws its own copy
        if (mc.getDebugOverlay().showDebugScreen() && !cfg.showWithDebugScreen) return;
        render(g, false, null);
    }

    /** Draws every enabled module and returns where each one ended up. */
    public static List<Placed> render(GuiGraphics g, boolean dummy, Config.Module highlight) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Config cfg = Config.get();
        int sw = g.guiWidth(), sh = g.guiHeight();

        List<Built> built = new ArrayList<>();
        for (Config.Module m : cfg.modules) {
            if (!m.enabled) continue;
            Built b = build(mc, font, m, dummy);
            if (b != null) built.add(b);
        }

        List<Placed> placed = new ArrayList<>();
        for (Config.Location loc : Config.Location.values()) {
            List<Built> group = new ArrayList<>();
            for (Built b : built) if (b.module.location == loc) group.add(b);
            if (group.isEmpty()) continue;

            if (loc == Config.Location.CUSTOM) {
                for (Built b : group) {
                    int x = Math.max(MARGIN, Math.min(sw - b.w() - MARGIN, Math.round(b.module.x * sw)));
                    int y = Math.max(MARGIN, Math.min(sh - b.h() - MARGIN, Math.round(b.module.y * sh)));
                    placed.add(new Placed(b.module, x, y, b.w(), b.h(), x + b.w() / 2 > sw / 2));
                }
                continue;
            }

            int total = 0;
            for (Built b : group) total += b.h() + GAP;
            total -= GAP;
            int y = switch (loc) {
                case CENTER_LEFT, CENTER_RIGHT -> (sh - total) / 2;
                case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - MARGIN - total;
                default -> MARGIN;
            };
            for (Built b : group) {
                int x = loc.right() ? sw - MARGIN - b.w() : MARGIN;
                placed.add(new Placed(b.module, x, y, b.w(), b.h(), loc.right()));
                y += b.h() + GAP;
            }
        }

        for (Placed p : placed) {
            Built b = null;
            for (Built c : built) if (c.module == p.module) b = c;
            if (b != null) draw(g, font, cfg, b, p, p.module == highlight);
        }
        return placed;
    }

    private static void draw(GuiGraphics g, Font font, Config cfg, Built b, Placed p, boolean highlight) {
        Config.Module m = b.module;
        if (m.boxOpacity > 0f) {
            int a = Math.round(Math.min(1f, m.boxOpacity) * 255f) << 24;
            g.fill(p.x - 2, p.y - 2, p.x + p.w + 2, p.y + p.h + 2, a);
        }
        if (highlight) {
            int c = 0x80FFFFFF;
            g.fill(p.x - 3, p.y - 3, p.x + p.w + 3, p.y - 2, c);
            g.fill(p.x - 3, p.y + p.h + 2, p.x + p.w + 3, p.y + p.h + 3, c);
            g.fill(p.x - 3, p.y - 2, p.x - 2, p.y + p.h + 2, c);
            g.fill(p.x + p.w + 2, p.y - 2, p.x + p.w + 3, p.y + p.h + 2, c);
        }

        g.pose().pushPose();
        g.pose().translate(p.x, p.y, 0);
        g.pose().scale(m.scale, m.scale, 1f);
        int y = 0;
        if (b.label != null) {
            int lx = p.right ? b.innerW - font.width(b.label) : 0;
            g.drawString(font, b.label, lx, y, 0xFFFFFFFF, cfg.textShadow);
            y += LABEL_H;
        }
        for (Row r : b.rows) {
            r.draw(g, font, 0, y, b.innerW, p.right, cfg.textShadow);
            y += r.height();
        }
        g.pose().popPose();
    }

    private static Built build(Minecraft mc, Font font, Config.Module m, boolean dummy) {
        List<Row> rows = new ArrayList<>();
        for (Config.Item item : m.items) {
            ItemType t = ItemType.byName(item.type);
            if (t == null) continue;
            switch (t.kind) {
                case TEXT -> addText(rows, t, item, t.value(mc, item.option, dummy));
                case STACK -> addStack(rows, t, item, t.value(mc, item.option, dummy));
                case POTIONS -> addPotions(rows, mc, t.option(item.option), dummy);
            }
        }
        if (rows.isEmpty() && !dummy) return null;

        String label = m.showLabel && m.name != null && !m.name.isEmpty() ? Fmt.prefixColor() + Fmt.S + 'l' + m.name : null;
        int w = label == null ? 0 : font.width(label);
        int h = label == null ? 0 : (rows.isEmpty() ? TEXT_H : LABEL_H);
        for (Row r : rows) {
            w = Math.max(w, r.width(font));
            h += r.height();
        }
        if (rows.isEmpty() && label == null) { // empty module in the editor: keep a grab handle
            rows.add(new TextRow(Fmt.code('8') + "(" + m.id + ")"));
            w = font.width(Fmt.code('8') + "(" + m.id + ")");
            h = TEXT_H;
        }
        return new Built(m, rows, label, w, h);
    }

    private static void addText(List<Row> rows, ItemType t, Config.Item item, Object v) {
        if (v instanceof String s) {
            rows.add(new TextRow(Fmt.line(t.label, item.showPrefix, s)));
        } else if (v instanceof String[][] lines) {
            for (String[] l : lines) rows.add(new TextRow(Fmt.line(l[0], item.showPrefix, l[1])));
        }
    }

    private static void addStack(List<Row> rows, ItemType t, Config.Item item, Object v) {
        if (!(v instanceof ItemStack stack) || stack.isEmpty()) return;
        String text = "";
        ItemType.Durability style;
        try {
            style = ItemType.Durability.valueOf(t.option(item.option));
        } catch (IllegalArgumentException e) {
            style = ItemType.Durability.RELATIVE;
        }
        if (style != ItemType.Durability.DISABLED && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            int max = stack.getMaxDamage();
            int left = max - stack.getDamageValue();
            float frac = left / (float) max;
            String c = Fmt.durabilityColor(frac);
            text = switch (style) {
                case ABSOLUTE -> c + left;
                case TOTAL -> c + left + "/" + max;
                default -> c + ItemType.one(frac * 100f) + "%";
            };
        }
        rows.add(new StackRow(stack, text));
    }

    private static void addPotions(List<Row> rows, Minecraft mc, String option, boolean dummy) {
        Collection<MobEffectInstance> effects = mc.player == null ? List.of() : mc.player.getActiveEffects();
        List<MobEffectInstance> list = new ArrayList<>(effects);
        if (list.isEmpty() && dummy) {
            list.add(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 92, 1));
            list.add(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 24, 0));
        }
        list.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
        boolean colored = "COLORED".equals(option);
        for (MobEffectInstance e : list) {
            StringBuilder sb = new StringBuilder(colored ? Fmt.potionColor(e.getDuration()) : Fmt.main());
            sb.append(e.getEffect().value().getDisplayName().getString());
            if (e.getAmplifier() > 0) {
                String key = "enchantment.level." + (e.getAmplifier() + 1);
                sb.append(' ').append(I18n.exists(key) ? I18n.get(key) : String.valueOf(e.getAmplifier() + 1));
            }
            sb.append(Fmt.code('7')).append(" - ").append(colored ? Fmt.potionColor(e.getDuration()) : Fmt.main());
            if (e.isInfiniteDuration()) {
                sb.append("∞");
            } else {
                int s = e.getDuration() / 20;
                sb.append(String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60));
            }
            rows.add(new PotionRow(e, sb.toString()));
        }
    }
}
