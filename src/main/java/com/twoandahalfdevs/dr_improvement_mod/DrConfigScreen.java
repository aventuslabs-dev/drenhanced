package com.twoandahalfdevs.dr_improvement_mod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

/** Settings screen opened from Forge's Mods list ("Config" button). Changes save on Done. */
public class DrConfigScreen extends Screen {
    private static final int W = 200;
    private static final int H = 20;
    private static final int GAP = 24;

    private final Screen parent;

    public DrConfigScreen(Screen parent) {
        super(Component.literal("DR Improvement Mod"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - W - 5;
        int right = this.width / 2 + 5;
        int y = 40;

        toggle(left, y, "Custom bars", DrConfig.CUSTOM_BARS, "Shader energy / mana / HP bars.");
        toggle(right, y, "Useful info", DrConfig.USEFUL_INFO, "Combat timer, pot cooldown, ability cooldown.");
        y += GAP;
        toggle(left, y, "Guild nametags", DrConfig.GUILD_NAMETAGS, "Light green nametags for guild members.");
        toggle(right, y, "Colour-blind glow", DrConfig.COLOR_BLIND, "Alternative item rarity glow colours.");
        y += GAP;
        toggle(left, y, "Hotbar abilities", DrConfig.HOTBAR_ABILITIES, "Selecting an ability head uses it and returns to your slot.");
        toggle(right, y, "Throttle packets", DrConfig.THROTTLE_PACKETS, "Debounce settings / tab-complete packets.");
        y += GAP;
        toggle(left, y, "1.8 combat feel", DrConfig.LEGACY_COMBAT, "No double-tap sprint, miss delay, client crits or re-equip bob.");
        y += GAP;
        addRenderableWidget(new IntSlider(left, y, "Shadowmeld points", DrConfig.SHADOWMELD_POINTS, 0, 10));
        addRenderableWidget(new IntSlider(right, y, "Just My Nature points", DrConfig.JUST_MY_NATURE_POINTS, 0, 5));

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(this.width / 2 - 100, this.height - 30, 200, H).build());
    }

    private void toggle(int x, int y, String label, ForgeConfigSpec.BooleanValue value, String tip) {
        addRenderableWidget(CycleButton.onOffBuilder(DrConfig.on(value, false))
            .withTooltip(v -> Tooltip.create(Component.literal(tip)))
            .create(x, y, W, H, Component.literal(label), (btn, v) -> value.set(v)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        DrConfig.SPEC.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final String label;
        private final ForgeConfigSpec.IntValue target;
        private final int min;
        private final int max;

        IntSlider(int x, int y, String label, ForgeConfigSpec.IntValue target, int min, int max) {
            super(x, y, W, H, Component.empty(), (DrConfig.num(target, min) - min) / (double) (max - min));
            this.label = label;
            this.target = target;
            this.min = min;
            this.max = max;
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
            target.set(current());
        }
    }
}
