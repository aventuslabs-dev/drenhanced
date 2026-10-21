package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Settings for the floating combat text. */
public final class FloatingTextScreen extends Screen {
    private static final int W = 150, H = 20, ROW = 24;
    private final Screen parent;

    public FloatingTextScreen(Screen parent) {
        super(Component.literal("Floating damage"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DrConfig.Data c = DrConfig.data;
        int left = width / 2 - W - 4, right = width / 2 + 4;
        int y = 30;
        toggle(left, y, "Floating damage", c.fctEnabled, v -> c.fctEnabled = v, "WoW-style numbers that pop off mobs when you hit them.");
        toggle(right, y, "Hide DR's numbers", c.fctHideDr, v -> c.fctHideDr = v, "Hide DR's own \"-1074 ❤\" holograms (ours replace them).");
        y += ROW;
        toggle(left, y, "Crits", c.fctBigHits, v -> c.fctBigHits = v, "DR critical strikes show larger in gold with \"!\" (from DR's * CRIT * message).");
        toggle(right, y, "Heals", c.fctHeals, v -> c.fctHeals = v, "Green +numbers.");
        y += ROW;
        toggle(left, y, "Damage taken", c.fctIncoming, v -> c.fctIncoming = v, "Red -numbers above you.");
        toggle(right, y, "Through walls", c.fctThroughWalls, v -> c.fctThroughWalls = v, "Keep numbers visible behind blocks.");
        y += ROW;
        toggle(left, y, "Proc labels", c.fctProcs, v -> c.fctProcs = v, "Small label over the number: Shattered, Cleaved, Executed, Poisoned, Thorns, Reflect.");
        toggle(right, y, "Other players", c.fctOthers, v -> c.fctOthers = v, "Also show other players' hits (small and grey). Off = only your own damage.");
        y += ROW;
        slider(left, y, c.fctSize, 0.3, 4.0, 0.05, v -> "Size: " + Math.round(v * 100) + "%", v -> c.fctSize = v);
        slider(right, y, c.fctDuration, 0.4, 5.0, 0.1, v -> String.format(Locale.ROOT, "Duration: %.1fs", v), v -> c.fctDuration = v);
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Test"), b -> FloatingText.test())
            .tooltip(Tooltip.create(Component.literal("Spawn sample numbers in front of you.")))
            .bounds(left, y, W, H).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(right, y, W, H).build());
    }

    private void toggle(int x, int y, String label, boolean initial, Consumer<Boolean> setter, String tip) {
        addRenderableWidget(CycleButton.onOffBuilder(initial)
            .withTooltip(v -> Tooltip.create(Component.literal(tip)))
            .create(x, y, W, H, Component.literal(label), (btn, v) -> setter.accept(v)));
    }

    private void slider(int x, int y, double value, double min, double max, double step,
                        DoubleFunction<String> label, DoubleConsumer setter) {
        addRenderableWidget(new Slider(x, y, value, min, max, step, label, setter));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
        else g.fill(0, 0, width, height, 0x50000000); // keep the world visible for the Test button
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
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
