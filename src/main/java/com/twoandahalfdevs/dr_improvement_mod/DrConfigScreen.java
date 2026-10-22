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

/** Settings screen (Mods menu via ModMenu, or /drconfig). Changes save on Done. */
public class DrConfigScreen extends Screen {
    private static final int W = 200;
    private static final int H = 20;
    private static final int GAP = 22;

    private final Screen parent;

    public DrConfigScreen(Screen parent) {
        super(Component.literal("DR Improvement Mod"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DrConfig.Data c = DrConfig.data;
        int left = this.width / 2 - W - 5;
        int right = this.width / 2 + 5;
        int y = 40;

        toggle(left, y, "Custom bars", c.customBars, v -> c.customBars = v, "Shader energy / mana / HP bars.");
        toggle(right, y, "Useful info", c.usefulInfo, v -> c.usefulInfo = v, "Combat timer, pot cooldown, ability cooldown.");
        y += GAP;
        toggle(left, y, "Guild nametags", c.guildNametags, v -> c.guildNametags = v, "Light green nametags for guild members.");
        toggle(right, y, "Colour-blind glow", c.colorBlindGlow, v -> c.colorBlindGlow = v, "Alternative item rarity glow colours.");
        y += GAP;
        toggle(left, y, "Hotbar abilities", c.hotbarAbilities, v -> c.hotbarAbilities = v, "Selecting an ability head uses it and returns to your slot.");
        toggle(right, y, "Throttle packets", c.throttlePackets, v -> c.throttlePackets = v, "Debounce settings / tab-complete packets.");
        y += GAP;
        toggle(left, y, "1.8 combat feel", c.legacyCombat, v -> c.legacyCombat = v, "No double-tap sprint, miss delay, client crits or re-equip bob.");
        toggle(right, y, "Split chat", c.splitChat, v -> {
            c.splitChat = v;
            if (!v) SplitChat.reset();
        }, "Player chat on the left, system/server messages (damage, loot, notices) on the right.");
        y += GAP;
        addRenderableWidget(new IntSlider(left, y, "Shadowmeld points", c.shadowmeldPoints, 0, 10, v -> c.shadowmeldPoints = v));
        addRenderableWidget(new IntSlider(right, y, "Just My Nature points", c.justMyNaturePoints, 0, 5, v -> c.justMyNaturePoints = v));

        y += GAP;
        addRenderableWidget(Button.builder(Component.literal("Chat layout..."), b -> this.minecraft.setScreen(new ChatLayoutScreen(this)))
            .bounds(left, y, W, H).build());
        addRenderableWidget(Button.builder(Component.literal("Enemy HP bar..."), b -> this.minecraft.setScreen(new EnemyHpScreen(this)))
            .bounds(right, y, W, H).build());
        y += GAP;
        addRenderableWidget(Button.builder(Component.literal("Cooldown bar..."), b -> this.minecraft.setScreen(new CooldownScreen(this)))
            .bounds(left, y, W, H).build());
        addRenderableWidget(Button.builder(Component.literal("Energy / mana / HP bars..."), b -> this.minecraft.setScreen(new BarsScreen(this)))
            .bounds(right, y, W, H).build());
        y += GAP;
        addRenderableWidget(Button.builder(Component.literal("Floating damage..."), b -> this.minecraft.setScreen(new FloatingTextScreen(this)))
            .bounds(left, y, W, H).build());
        addRenderableWidget(Button.builder(Component.literal(Capture.active() ? "\u00a7cStop message capture" : "Start message capture"), b -> {
            Component msg = Capture.toggle();
            if (this.minecraft.player != null) this.minecraft.player.displayClientMessage(msg, false);
            rebuildWidgets();
        }).tooltip(Tooltip.create(Component.literal("Logs DR's chat, action bar, titles, boss bars, sidebar and nameplates to .minecraft/dr_capture/ so the mod can be tuned to DR's exact text. Same as /drcapture.")))
            .bounds(right, y, W, H).build());
        y += GAP;
        toggle(left, y, "Full bright (G)", c.fullbright, v -> {
            c.fullbright = v;
            DrConfig.save();
        }, "Everything fully lit: caves, night, dungeons. Toggle in game with G (rebind in Controls).");
        addRenderableWidget(Button.builder(Component.literal("Mining popups..."), b -> this.minecraft.setScreen(new OrePopupScreen(this)))
            .tooltip(Tooltip.create(Component.literal("Double / triple ore, gems, XP, ore respawn and more pop up on the ore you mined.")))
            .bounds(right, y, W, H).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(this.width / 2 - 100, this.height - 30, 200, H).build());
    }

    private void toggle(int x, int y, String label, boolean initial, Consumer<Boolean> setter, String tip) {
        addRenderableWidget(CycleButton.onOffBuilder(initial)
            .withTooltip(v -> Tooltip.create(Component.literal(tip)))
            .create(x, y, W, H, Component.literal(label), (btn, v) -> setter.accept(v)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        DrConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer setter;

        IntSlider(int x, int y, String label, int initial, int min, int max, IntConsumer setter) {
            super(x, y, W, H, Component.empty(), (initial - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(Mth.clamp(this.value, 0.0, 1.0) * (max - min));
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
