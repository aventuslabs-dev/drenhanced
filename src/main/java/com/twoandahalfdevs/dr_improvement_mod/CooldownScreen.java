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
import java.util.function.IntConsumer;

/** Settings for the cooldown bar, with a live demo drawn at its real position. */
public final class CooldownScreen extends Screen {
    private static final int W = 150, H = 20, ROW = 24;
    private final Screen parent;

    public CooldownScreen(Screen parent) {
        super(Component.literal("Cooldown bar"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DrConfig.Data c = DrConfig.data;
        int left = width / 2 - W - 4, right = width / 2 + 4;
        int y = 30;
        toggle(left, y, "Cooldown bar", c.cooldownBar, v -> c.cooldownBar = v, "Ultimate, potions, combat timer and item cooldowns as WoW-style icons.");
        toggle(right, y, "Labels", c.cdShowLabels, v -> c.cdShowLabels = v, "Small name under each icon.");
        y += ROW;
        toggle(left, y, "Combat / bonus timers", c.cdShowCombat, v -> c.cdShowCombat = v, "Show the combat timer (and Rogue bonus window) while in combat.");
        toggle(right, y, "Hotbar item cooldowns", c.cdShowItems, v -> c.cdShowItems = v, "Any hotbar item the server puts on cooldown.");
        y += ROW;
        addRenderableWidget(new IntSlider(left, y, "Icon size", c.cdIconSize, 12, 48, v -> c.cdIconSize = v));
        addRenderableWidget(new IntSlider(right, y, "Sideways", c.cdX, -300, 300, v -> c.cdX = v));
        y += ROW;
        addRenderableWidget(new IntSlider(left, y, "Height from bottom", c.cdY, 0, 300, v -> c.cdY = v));
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(right, y, W, H).build());
    }

    private void toggle(int x, int y, String label, boolean initial, Consumer<Boolean> setter, String tip) {
        addRenderableWidget(CycleButton.onOffBuilder(initial)
            .withTooltip(v -> Tooltip.create(Component.literal(tip)))
            .create(x, y, W, H, Component.literal(label), (btn, v) -> setter.accept(v)));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
        else g.fill(0, 0, width, height, 0x40000000); // keep the HUD visible while positioning
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        if (DrConfig.data.cooldownBar) CooldownBar.drawRow(g, font, CooldownBar.demo(), width, height);
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
