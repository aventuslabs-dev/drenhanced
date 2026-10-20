package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Sliders for both chat boxes, with a live outline of where each one sits. */
public final class ChatLayoutScreen extends Screen {
    private static final int W = 150;
    private static final int H = 20;
    private static final int ROW = 22;

    private final Screen parent;

    public ChatLayoutScreen(Screen parent) {
        super(Component.literal("Chat layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - W - 4;
        int right = width / 2 + 4;
        column(left, DrConfig.data.leftChat);
        column(right, DrConfig.data.rightChat);

        int by = height - 26;
        int bw = (2 * W + 8 - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Reset both"), b -> {
            DrConfig.data.leftChat = new DrConfig.ChatBox();
            DrConfig.data.rightChat = new DrConfig.ChatBox();
            SplitChat.relayout();
            rebuildWidgets();
        }).bounds(left, by, bw, H).build());
        addRenderableWidget(net.minecraft.client.gui.components.CycleButton.onOffBuilder(DrConfig.data.collapseRepeats)
            .withTooltip(v -> net.minecraft.client.gui.components.Tooltip.create(Component.literal("Fold repeated system messages into \"message (x3)\".")))
            .create(left + bw + 4, by, bw, H, Component.literal("Fold repeats"), (btn, v) -> DrConfig.data.collapseRepeats = v));
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + 2 * (bw + 4), by, bw, H).build());
    }

    private void column(int x, DrConfig.ChatBox b) {
        int y = 30;
        add(x, y, b.width, 0, 600, 10, v -> v == 0 ? "Width: Auto" : "Width: " + (int) v + "px", v -> {
            b.width = (int) v;
            SplitChat.relayout();
        });
        y += ROW;
        add(x, y, b.heightFocused, 20, 400, 5, v -> "Height (chat open): " + (int) v + "px", v -> b.heightFocused = (int) v);
        y += ROW;
        add(x, y, b.heightUnfocused, 20, 400, 5, v -> "Height (closed): " + (int) v + "px", v -> b.heightUnfocused = (int) v);
        y += ROW;
        add(x, y, b.scale, 0.25, 2.0, 0.05, v -> "Text size: " + Math.round(v * 100) + "%", v -> {
            b.scale = v;
            SplitChat.relayout();
        });
        y += ROW;
        add(x, y, b.textOpacity, 0.1, 1.0, 0.05, v -> "Text opacity: " + Math.round(v * 100) + "%", v -> b.textOpacity = v);
        y += ROW;
        add(x, y, b.background, 0.0, 1.0, 0.05, v -> "Background: " + Math.round(v * 100) + "%", v -> b.background = v);
        y += ROW;
        add(x, y, b.x, 0, 400, 1, v -> "From edge: " + (int) v + "px", v -> b.x = (int) v);
        y += ROW;
        add(x, y, b.y, 0, 300, 1, v -> "Move up: " + (int) v + "px", v -> b.y = (int) v);
    }

    private void add(int x, int y, double value, double min, double max, double step,
                     DoubleFunction<String> label, DoubleConsumer setter) {
        addRenderableWidget(new Slider(x, y, value, min, max, step, label, setter));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
        else g.fill(0, 0, width, height, 0x40000000); // keep the chats visible behind the sliders
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        g.drawCenteredString(font, "§aLeft: player chat", width / 2 - W / 2 - 4, 20, 0xFFFFFFFF);
        g.drawCenteredString(font, "§bRight: system chat", width / 2 + W / 2 + 4, 20, 0xFFFFFFFF);
        if (DrConfig.data.splitChat) {
            outline(g, SplitChat.leftX(), DrConfig.data.leftChat, minecraft.gui.getChat().getWidth(), 0xFF55FF55, "Left chat");
            outline(g, SplitChat.offsetX(width), DrConfig.data.rightChat, SplitChat.right().getWidth(), 0xFF55FFFF, "Right chat");
        } else {
            g.drawCenteredString(font, "§cSplit chat is off — turn it on in the DR settings", width / 2, height - 40, 0xFFFFFFFF);
        }
    }

    /** Approximate on-screen box: open height as an outline, closed height shaded. */
    private void outline(GuiGraphics g, int x, DrConfig.ChatBox b, int w, int color, String name) {
        int bottom = height - 40 - b.y + 2;
        int wPx = w + Mth.ceil(8 * b.scale);
        int open = (int) Math.round(b.heightFocused * b.scale);
        int closed = (int) Math.round(b.heightUnfocused * b.scale);
        g.fill(x, bottom - closed, x + wPx, bottom, (color & 0x00FFFFFF) | 0x30000000);
        int top = bottom - open;
        g.fill(x, top, x + wPx, top + 1, color);
        g.fill(x, bottom - 1, x + wPx, bottom, color);
        g.fill(x, top, x + 1, bottom, color);
        g.fill(x + wPx - 1, top, x + wPx, bottom, color);
        g.drawString(font, name, x + 3, top + 3, color, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        DrConfig.data.leftChat.clamp();
        DrConfig.data.rightChat.clamp();
        DrConfig.save();
        SplitChat.relayout();
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
            double v = min + Mth.clamp(this.value, 0.0, 1.0) * (max - min);
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
