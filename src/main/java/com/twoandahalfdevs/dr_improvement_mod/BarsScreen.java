package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Enable, size, move and fade the energy arcs, mana bar and HP bar. The real bars update live behind it. */
public final class BarsScreen extends Screen {
    private static final int W = 128, H = 20, ROW = 22;
    private final Screen parent;

    public BarsScreen(Screen parent) {
        super(Component.literal("Energy / mana / HP bars"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DrConfig.Data c = DrConfig.data;
        int gap = 4;
        int x0 = width / 2 - (3 * W + 2 * gap) / 2;
        column(x0, "Energy", c.energyBar);
        column(x0 + W + gap, "Mana", c.manaBar);
        column(x0 + 2 * (W + gap), "HP", c.healthBar);

        int by = 30 + 6 * ROW + 6;
        addRenderableWidget(CycleButton.onOffBuilder(c.customBars)
            .create(x0, by, W, H, Component.literal("Custom bars"), (b, v) -> c.customBars = v));
        addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> {
            c.energyBar = new DrConfig.Bar();
            c.manaBar = new DrConfig.Bar();
            c.healthBar = new DrConfig.Bar();
            rebuildWidgets();
        }).bounds(x0 + W + gap, by, W, H).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x0 + 2 * (W + gap), by, W, H).build());
    }

    private void column(int x, String name, DrConfig.Bar b) {
        int y = 30;
        addRenderableWidget(CycleButton.onOffBuilder(b.enabled)
            .create(x, y, W, H, Component.literal(name), (btn, v) -> b.enabled = v));
        y += ROW;
        slider(x, y, b.scale, 0.25, 4.0, 0.05, v -> "Size: " + Math.round(v * 100) + "%", v -> b.scale = v);
        y += ROW;
        slider(x, y, b.x, -50, 50, 0.5, v -> "Sideways: " + signed(v), v -> b.x = v);
        y += ROW;
        slider(x, y, b.y, -50, 50, 0.5, v -> "Up/down: " + signed(v), v -> b.y = v);
        y += ROW;
        slider(x, y, b.opacity, 0.05, 1.0, 0.05, v -> "Opacity: " + Math.round(v * 100) + "%", v -> b.opacity = v);
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Centre"), btn -> {
            b.x = 0;
            b.y = 0;
            rebuildWidgets();
        }).bounds(x, y, W, H).build());
    }

    private static String signed(double v) {
        return (v > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f%%", v);
    }

    private void slider(int x, int y, double value, double min, double max, double step,
                        DoubleFunction<String> label, DoubleConsumer setter) {
        addRenderableWidget(new Slider(x, y, value, min, max, step, label, setter));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
        // in game: no dimming, so the bars behind are easy to line up
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        g.drawCenteredString(font, "§7Changes show live on the real bars. Y is upwards.", width / 2, height - 14, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        DrConfig.data.energyBar.clamp();
        DrConfig.data.manaBar.clamp();
        DrConfig.data.healthBar.clamp();
        DrConfig.save();
        minecraft.setScreen(parent);
    }

    private static final class Slider extends AbstractSliderButton {
        private final double min, max, step;
        private final DoubleFunction<String> label;
        private final DoubleConsumer setter;

        Slider(int x, int y, double value, double min, double max, double step, DoubleFunction<String> label, DoubleConsumer setter) {
            super(x, y, W, H, Component.empty(), (Mth.clamp(value, min, max) - min) / (max - min));
            this.min = min;
            this.max = max;
            this.step = step;
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        private double current() {
            double v = min + Mth.clamp(value, 0.0, 1.0) * (max - min);
            return Math.round(v / step) * step;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label.apply(current())));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
