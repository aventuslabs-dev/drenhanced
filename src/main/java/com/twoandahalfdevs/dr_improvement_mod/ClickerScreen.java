package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Auto clicker and energy guard settings. */
public final class ClickerScreen extends Screen {
    private static final int W = 150, H = 20, ROW = 24;
    private final Screen parent;

    public ClickerScreen(Screen parent) {
        super(Component.literal("Combat: clicker / skills / energy / lag"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DrConfig.Data c = DrConfig.data;
        int left = width / 2 - W - 4, right = width / 2 + 4;
        int y = 34;
        toggle(left, y, "Auto clicker", c.autoClicker, v -> c.autoClicker = v,
            "Hold attack to keep swinging at the CPS on the right. Off while mining with a pickaxe. Toggle key: Controls > DR Improvement Mod.");
        slider(right, y, c.autoCps, 1, 20, 1, v -> "Clicks per second: " + Math.round(v), v -> c.autoCps = (int) Math.round(v));
        y += ROW;
        toggle(left, y, "Energy guard", c.energyGuard, v -> c.energyGuard = v,
            "Refuses a swing (yours or the clicker's) that would drop your energy under the floor, so you never run dry. Learns what a swing costs as you fight.");
        slider(right, y, c.energyFloor, 0, 50, 1, v -> "Keep energy above: " + Math.round(v) + "%", v -> c.energyFloor = (int) Math.round(v));
        y += ROW;
        toggle(left, y, "Auto skills", c.autoSkills, v -> c.autoSkills = v,
            "When you hit a mob and a damage artifact on your hotbar is ready (cooldown, mana, range), it's cast for you. Your weapon stays in hand. /drskill to see or skip artifacts.");
        slider(right, y, c.autoSkillMinMana, 0, 100, 1, v -> "Auto cast at: " + Math.round(v) + "+ mana", v -> c.autoSkillMinMana = (int) Math.round(v));
        y += ROW;
        toggle(left, y, "Less mob smoothing", c.snappyMobs, v -> c.snappyMobs = v,
            "Mobs catch up to their server position by the time DR's next update for them is due, instead of always easing over 3 ticks. Up to ~100 ms less lag on what you see while staying smooth.");
        toggle(right, y, "Instant hit feedback", c.hitPrediction, v -> c.hitPrediction = v,
            "Low-ping feel: the mob flashes red, sparks fly and its HP bar drops the moment you swing, instead of a ping later. DR's real numbers correct it when they arrive.");
        y += ROW + 6;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 - 100, y, 200, H).build());
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
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
