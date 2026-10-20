package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.IntConsumer;

/** Settings for DR mob health bars, with a live preview of the WoW-style plate. */
public final class EnemyHpScreen extends Screen {
    private static final int W = 150, H = 20, ROW = 24;
    private static final List<String> COLORS = List.of("REACTION", "HEALTH", "GOLD");
    private static final List<String> TEXTS = List.of("CURRENT_MAX", "CURRENT", "CURRENT_PERCENT", "PERCENT", "NONE");

    private final Screen parent;

    public EnemyHpScreen(Screen parent) {
        super(Component.literal("Enemy HP bar"));
        this.parent = parent;
    }

    private static String colorLabel(String c) {
        return switch (c) {
            case "HEALTH" -> "By health";
            case "GOLD" -> "Gold";
            default -> "Hostile red";
        };
    }

    private static String textLabel(String t) {
        return switch (t) {
            case "CURRENT" -> "1,840";
            case "CURRENT_PERCENT" -> "1,840 (77%)";
            case "PERCENT" -> "77%";
            case "NONE" -> "None";
            default -> "1,840 / 2,400";
        };
    }

    @Override
    protected void init() {
        DrConfig.Data c = DrConfig.data;
        int left = width / 2 - W - 4, right = width / 2 + 4;
        int y = 88;
        addRenderableWidget(CycleButton.<EnemyHp.Style>builder(st -> Component.literal(st.label))
            .withValues(EnemyHp.Style.values()).withInitialValue(DrConfig.enemyHpStyle())
            .withTooltip(st -> Tooltip.create(Component.literal(st == EnemyHp.Style.MODERN
                ? "Name + level until hit, then a health bar with exact HP under it."
                : "Rewrites DR's ||||| bar as text.")))
            .create(left, y, W, H, Component.literal("Style"), (b, st) -> { c.enemyHpStyle = st.name(); rebuildWidgets(); }));
        addRenderableWidget(CycleButton.<String>builder(t -> Component.literal(textLabel(t)))
            .withValues(TEXTS).withInitialValue(TEXTS.contains(c.enemyHpText) ? c.enemyHpText : "CURRENT_MAX")
            .create(right, y, W, H, Component.literal("Numbers"), (b, t) -> c.enemyHpText = t));
        y += ROW;
        addRenderableWidget(new IntSlider(left, y, "Bar width", c.enemyHpBarWidth, 30, 200, v -> c.enemyHpBarWidth = v));
        addRenderableWidget(new IntSlider(right, y, "Bar height", c.enemyHpBarHeight, 4, 24, v -> c.enemyHpBarHeight = v));
        y += ROW;
        addRenderableWidget(CycleButton.<String>builder(t -> Component.literal(colorLabel(t)))
            .withValues(COLORS).withInitialValue(COLORS.contains(c.enemyHpColor) ? c.enemyHpColor : "REACTION")
            .create(left, y, W, H, Component.literal("Colour"), (b, t) -> c.enemyHpColor = t));
        addRenderableWidget(CycleButton.onOffBuilder(c.enemyHpShowLevel)
            .withTooltip(v -> Tooltip.create(Component.literal("Text styles: keep [Lv 90] after the bar.")))
            .create(right, y, W, H, Component.literal("Level (text styles)"), (b, v) -> c.enemyHpShowLevel = v));
        y += ROW;
        addRenderableWidget(new IntSlider(left, y, "Text bar length", c.enemyHpBarLength, 4, 40, v -> c.enemyHpBarLength = v));
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(right, y, W, H).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        preview(g, width / 2, 46);
        g.drawCenteredString(font, "§7Exact HP needs DR's damage messages (debug) on; otherwise it's estimated (~).", width / 2, height - 14, 0xFFFFFFFF);
    }

    /** 2D preview using the same layout as the in-world plate (1 nametag pixel = 1 GUI pixel). */
    private void preview(GuiGraphics g, int cx, int top) {
        float frac = 0.77f;
        long t = System.currentTimeMillis() % 3000;
        float trail = t < 1200 ? 0.77f + 0.13f * (1f - t / 1200f) : 0.77f;
        EnemyHp.Style st = DrConfig.enemyHpStyle();
        if (st != EnemyHp.Style.MODERN) {
            var p = new EnemyHp.Parsed(15, 20, "", "[Lv 90]");
            Component c = st == EnemyHp.Style.ORIGINAL
                ? Component.literal("§a|||||||||||||||§7||||| §f[Lv 90]")
                : EnemyHp.render(p, st, DrConfig.data.enemyHpBarLength, DrConfig.data.enemyHpShowLevel);
            g.drawCenteredString(font, c, cx, top + 6, 0xFFFFFFFF);
            return;
        }
        int w = DrConfig.data.enemyHpBarWidth, h = DrConfig.data.enemyHpBarHeight;
        int x0 = cx - w / 2, x1 = x0 + w, y0 = top + 12, y1 = y0 + h;
        g.drawCenteredString(font, "§cSpooky Skeleton §f[Lv 90]", cx, top, 0xFFFFFFFF);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, MobHp.BORDER);
        g.fill(x0, y0, x1, y1, MobHp.BACK);
        int fe = x0 + Math.round(w * frac), te = x0 + Math.round(w * trail);
        if (te > fe) g.fill(fe, y0, te, y1, MobHp.TRAIL);
        g.fill(x0, y0, fe, y1, MobHp.fillColor(frac));
        g.fill(x0, y0, fe, y0 + Math.max(1, Math.round(h * 0.45f)), MobHp.SHINE);
        g.fill(x0, y1 - Math.max(1, Math.round(h * 0.3f)), fe, y1, MobHp.SHADE);
        String txt = switch (DrConfig.data.enemyHpText) {
            case "CURRENT" -> "1,840";
            case "CURRENT_PERCENT" -> "1,840 (77%)";
            case "PERCENT" -> "77%";
            case "NONE" -> "";
            default -> "1,840 / 2,400";
        };
        if (!txt.isEmpty()) {
            float s = Math.max(0.5f, Math.min(1.2f, (h - 1) / 8f));
            g.pose().pushPose();
            g.pose().translate(cx, y0 + h / 2f, 0);
            g.pose().scale(s, s, 1f);
            int tx = -font.width(txt) / 2;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    if (dx != 0 || dy != 0) g.drawString(font, txt, tx + dx, -4 + dy, 0xFF000000, false);
            g.drawString(font, txt, tx, -4, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }

    @Override
    public void onClose() {
        DrConfig.save();
        minecraft.setScreen(parent);
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final String label;
        private final int min, max;
        private final IntConsumer setter;

        IntSlider(int x, int y, String label, int initial, int min, int max, IntConsumer setter) {
            super(x, y, W, H, Component.empty(), (Mth.clamp(initial, min, max) - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(Mth.clamp(value, 0.0, 1.0) * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
