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
    private static final int W = 120, H = 20, ROW = 22;
    private final Screen parent;

    public BarsScreen(Screen parent) {
        super(Component.literal("Energy / mana / HP bars"));
        this.parent = parent;
    }

    private static int tab; // 0 energy, 1 mana, 2 hp
    private static final String[] NAMES = {"Energy", "Mana", "HP"};
    private boolean dragging;

    private DrConfig.Bar bar() {
        DrConfig.Data c = DrConfig.data;
        return tab == 0 ? c.energyBar : tab == 1 ? c.manaBar : c.healthBar;
    }

    private int panelRight() {
        return 6 + W;
    }

    @Override
    protected void init() {
        DrConfig.Data c = DrConfig.data;
        int x = 6, y = 26;
        int tw = (W - 4) / 3;
        for (int i = 0; i < 3; i++) {
            final int t = i;
            Button b = Button.builder(Component.literal((i == tab ? "\u00a7e" : "") + NAMES[i]), btn -> {
                tab = t;
                rebuildWidgets();
            }).bounds(x + i * (tw + 2), y, tw, H).build();
            addRenderableWidget(b);
        }
        y += ROW + 2;
        DrConfig.Bar b = bar();
        addRenderableWidget(CycleButton.onOffBuilder(b.enabled)
            .create(x, y, W, H, Component.literal("Show"), (btn, v) -> b.enabled = v));
        y += ROW;
        slider(x, y, b.scale, 0.25, 4.0, 0.05, v -> "Size: " + Math.round(v * 100) + "%", v -> b.scale = v);
        y += ROW;
        slider(x, y, b.x, -50, 50, 0.5, v -> "Sideways: " + signed(v), v -> b.x = v);
        y += ROW;
        slider(x, y, b.y, -50, 50, 0.5, v -> "Up/down: " + signed(v), v -> b.y = v);
        y += ROW;
        slider(x, y, b.opacity, 0.05, 1.0, 0.05, v -> "Opacity: " + Math.round(v * 100) + "%", v -> b.opacity = v);
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Reset " + NAMES[tab]), btn -> {
            if (tab == 0) c.energyBar = new DrConfig.Bar();
            else if (tab == 1) c.manaBar = new DrConfig.Bar();
            else c.healthBar = new DrConfig.Bar();
            rebuildWidgets();
        }).bounds(x, y, W, H).build());
        if (tab == 1) {
            y += ROW;
            addRenderableWidget(CycleButton.onOffBuilder(c.midHealthBar)
                .create(x, y, W, H, Component.literal("Green HP bar above"), (btn, v) -> c.midHealthBar = v));
            y += ROW;
            addRenderableWidget(CycleButton.onOffBuilder(c.barText)
                .create(x, y, W, H, Component.literal("HP / mana numbers"), (btn, v) -> c.barText = v));
        }
        y += ROW + 4;
        addRenderableWidget(CycleButton.onOffBuilder(c.customBars)
            .create(x, y, W, H, Component.literal("Custom bars"), (btn, v) -> c.customBars = v));
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose()).bounds(x, y, W, H).build());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && mx > panelRight() + 4) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            DrConfig.Bar b = bar();
            b.x = Mth.clamp(b.x + dx / width * 100.0, -50, 50);
            b.y = Mth.clamp(b.y - dy / height * 100.0, -50, 50);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            rebuildWidgets(); // refresh slider positions
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx <= panelRight() || sy == 0) return super.mouseScrolled(mx, my, sx, sy);
        DrConfig.Bar b = bar();
        b.scale = Mth.clamp(Math.round((b.scale + (sy > 0 ? 0.05 : -0.05)) * 100) / 100.0, 0.25, 4.0);
        rebuildWidgets();
        return true;
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
        // in game: no dimming, so the bars behind are easy to line up; just a backdrop for the panel
        g.fill(2, 2, panelRight() + 4, height - 2, 0x90000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(font, "§6Bars", 8, 12, 0xFFFFFFFF, true);
        g.drawCenteredString(font, "§7Drag anywhere to move §f" + NAMES[tab] + "§7 · scroll to resize",
            (panelRight() + width) / 2, height - 34, 0xFFFFFFFF);
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
