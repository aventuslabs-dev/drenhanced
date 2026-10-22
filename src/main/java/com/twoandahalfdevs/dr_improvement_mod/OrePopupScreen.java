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

/** Settings for the mining popups. */
public final class OrePopupScreen extends Screen {
    private static final int W = 150, H = 20, ROW = 24;
    private final Screen parent;

    public OrePopupScreen(Screen parent) {
        super(Component.literal("Mining popups"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DrConfig.Data c = DrConfig.data;
        int left = width / 2 - W - 4, right = width / 2 + 4;
        int y = 30;
        toggle(left, y, "Mining popups", c.oreEnabled, v -> c.oreEnabled = v,
            "DR's mining lines (double / triple ore, gems, XP, ore respawn, durability, level up) pop up as cards on the ore you mined.");
        toggle(right, y, "Hide from chat", c.oreHideChat, v -> c.oreHideChat = v,
            "Keep the mining lines that became popups out of chat.");
        y += ROW;
        toggle(left, y, "XP gains", c.oreShowExp, v -> c.oreShowExp = v, "+XP card with a progress bar to the next pickaxe level.");
        toggle(right, y, "Failed mines", c.oreShowFails, v -> c.oreShowFails = v, "A small grey \"Missed\" card when a mine fails.");
        y += ROW;
        toggle(left, y, "Sounds", c.oreSounds, v -> c.oreSounds = v, "A soft chime for triple ore and treasure, a fanfare for pickaxe level / tier up.");
        y += ROW;
        slider(left, y, c.oreSize, 0.4, 3.0, 0.05, v -> "Size: " + Math.round(v * 100) + "%", v -> c.oreSize = v);
        slider(right, y, c.oreDuration, 0.8, 6.0, 0.1, v -> String.format(Locale.ROOT, "Duration: %.1fs", v), v -> c.oreDuration = v);
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Test"), b -> OreText.test())
            .tooltip(Tooltip.create(Component.literal("Play a sample mining sequence on the block you're looking at.")))
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
