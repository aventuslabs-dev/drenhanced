package eu.the5zig.fabric.gui;

import eu.the5zig.fabric.Config;
import eu.the5zig.fabric.hud.Fmt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Main settings: global formatting + the module list. */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private int scroll;
    private boolean confirmReset;

    public SettingsScreen(Screen parent) {
        super(Component.literal("The 5zig Mod"));
        this.parent = parent;
    }

    private static Component lit(String s) {
        return Component.literal(s);
    }

    private static String onOff(boolean b) {
        return b ? "§aON" : "§cOFF";
    }

    private Button toggle(int x, int y, int w, Supplier<String> text, Runnable action) {
        return Button.builder(lit(text.get()), b -> {
            action.run();
            Config.save();
            b.setMessage(lit(text.get()));
        }).bounds(x, y, w, 20).build();
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int colW = 150, gap = 4;
        int left = width / 2 - colW - gap / 2, right = width / 2 + gap / 2;
        int y = 30 - scroll;

        addRenderableWidget(toggle(left, y, colW, () -> "HUD: " + onOff(Config.get().enabled), () -> c.enabled = !c.enabled));
        addRenderableWidget(Button.builder(lit("Edit HUD layout..."), b -> minecraft.setScreen(new EditorScreen(this)))
                .bounds(right, y, colW, 20).build());
        y += 24;
        addRenderableWidget(toggle(left, y, colW, () -> "Prefix: " + Fmt.colorName(Config.get().colorPrefix),
                () -> c.colorPrefix = Fmt.nextColor(c.colorPrefix)));
        addRenderableWidget(toggle(right, y, colW, () -> "Value: " + Fmt.colorName(Config.get().colorMain),
                () -> c.colorMain = Fmt.nextColor(c.colorMain)));
        y += 24;
        addRenderableWidget(toggle(left, y, colW, () -> "Brackets: " + Fmt.colorName(Config.get().colorBrackets),
                () -> c.colorBrackets = Fmt.nextColor(c.colorBrackets)));
        addRenderableWidget(toggle(right, y, colW, () -> "Style: " + Fmt.line("FPS", true, "60"),
                () -> c.formattingBrackets = c.formattingBrackets.next()));
        y += 24;
        addRenderableWidget(toggle(left, y, colW, () -> "Text shadow: " + onOff(Config.get().textShadow), () -> c.textShadow = !c.textShadow));
        addRenderableWidget(toggle(right, y, colW, () -> "Colored durability: " + onOff(Config.get().coloredEquipmentDurability),
                () -> c.coloredEquipmentDurability = !c.coloredEquipmentDurability));
        y += 24;
        Button f3 = toggle(left, y, colW, () -> "Show with F3: " + onOff(Config.get().showWithDebugScreen),
                () -> c.showWithDebugScreen = !c.showWithDebugScreen);
        f3.setTooltip(Tooltip.create(lit("Keep the HUD visible while the F3 debug screen is open.")));
        addRenderableWidget(f3);
        Button fx = toggle(right, y, colW, () -> "Hide vanilla effects: " + onOff(Config.get().hideVanillaEffects),
                () -> c.hideVanillaEffects = !c.hideVanillaEffects);
        fx.setTooltip(Tooltip.create(lit("Hide the vanilla top-right effect icons while a 5zig module shows potions.")));
        addRenderableWidget(fx);
        y += 32;

        for (Config.Module m : c.modules) {
            addRenderableWidget(toggle(left, y, colW, () -> trim(m.name) + ": " + onOff(m.enabled), () -> m.enabled = !m.enabled));
            addRenderableWidget(Button.builder(lit("Items (" + m.items.size() + ")..."), b -> minecraft.setScreen(new ModuleItemsScreen(this, m)))
                    .bounds(right, y, colW, 20).build());
            y += 24;
        }
        y += 8;
        int bw = (colW * 2 + gap - 8) / 3;
        addRenderableWidget(Button.builder(lit(confirmReset ? "\u00a7cClick again" : "Reset all"), b -> {
            if (confirmReset) {
                Config.reset();
                confirmReset = false;
            } else {
                confirmReset = true;
            }
            rebuildWidgets();
        }).bounds(left, y, bw, 20).build());
        addRenderableWidget(Button.builder(lit("+ New module"), b -> {
            Config.Module m = new Config.Module();
            m.id = "custom" + (Config.get().modules.size() + 1);
            m.name = "Module " + (Config.get().modules.size() + 1);
            m.location = Config.Location.CUSTOM;
            m.x = 0.4f;
            m.y = 0.4f;
            Config.get().modules.add(m);
            Config.save();
            minecraft.setScreen(new ModuleItemsScreen(this, m));
        }).bounds(left + bw + 4, y, bw, 20).build());
        addRenderableWidget(Button.builder(lit("Done"), b -> onClose()).bounds(left + 2 * (bw + 4), y, bw, 20).build());
        contentBottom = y + 20 + scroll;
    }

    private int contentBottom;

    private static String trim(String s) {
        if (s == null) return "?";
        return s.length() > 14 ? s.substring(0, 13) + "…" : s;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, contentBottom - height + 10);
        int next = Math.max(0, Math.min(max, scroll - (int) (sy * 16)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 12 - scroll, 0xFFFFAA00);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }
}
