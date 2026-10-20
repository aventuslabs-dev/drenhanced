package eu.the5zig.fabric.gui;

import eu.the5zig.fabric.Config;
import eu.the5zig.fabric.hud.HudRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Drag-and-drop HUD layout editor (the original "Display Modules" edit view). */
public final class EditorScreen extends Screen {
    private static final float[] BOX = {0f, 0.25f, 0.5f, 0.75f};

    private final Screen parent;
    private List<HudRenderer.Placed> placed = List.of();
    private Config.Module selected;
    private Config.Module dragging;
    private double offX, offY;

    public EditorScreen(Screen parent) {
        super(Component.literal("5zig HUD layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height - 46;
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> minecraft.setScreen(new SettingsScreen(this)))
                .bounds(width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset layout"), b -> {
            Config.reset();
            selected = null;
        }).bounds(width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 + 54, y, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) super.renderBackground(g, mouseX, mouseY, delta);
        else g.fill(0, 0, width, height, 0x30000000); // keep the world visible while positioning
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        placed = HudRenderer.render(g, true, selected);
        String help = "Drag: move · Scroll: resize · Right-click: next position · L: label · B: background";
        g.drawCenteredString(font, help, width / 2, height - 20, 0xFFAAAAAA);
        if (selected != null) {
            String info = "§6" + selected.name + "§7 — " + selected.location.name().toLowerCase().replace('_', ' ')
                    + ", " + Math.round(selected.scale * 100) + "%";
            g.drawCenteredString(font, info, width / 2, height - 58, 0xFFFFFFFF);
        }
    }

    private Config.Module at(double mx, double my) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            if (placed.get(i).contains(mx, my)) return placed.get(i).module();
        }
        return null;
    }

    private HudRenderer.Placed placedOf(Config.Module m) {
        for (HudRenderer.Placed p : placed) if (p.module() == m) return p;
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        Config.Module m = at(mx, my);
        if (m == null) {
            selected = null;
            return false;
        }
        selected = m;
        if (button == 0) {
            HudRenderer.Placed p = placedOf(m);
            dragging = m;
            offX = mx - p.x();
            offY = my - p.y();
        } else if (button == 1) {
            m.location = m.location.next();
            if (m.location == Config.Location.CUSTOM) {
                HudRenderer.Placed p = placedOf(m);
                m.x = p.x() / (float) width;
                m.y = p.y() / (float) height;
            }
            Config.save();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null && button == 0) {
            dragging.location = Config.Location.CUSTOM;
            dragging.x = (float) Math.max(0, Math.min(1, (mx - offX) / width));
            dragging.y = (float) Math.max(0, Math.min(1, (my - offY) / height));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            Config.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        Config.Module m = at(mx, my);
        if (m == null) m = selected;
        if (m == null || sy == 0) return false;
        m.scale = Math.round(Math.max(0.5f, Math.min(3f, m.scale + (sy > 0 ? 0.1f : -0.1f))) * 10f) / 10f;
        selected = m;
        Config.save();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (selected != null) {
            if (key == GLFW.GLFW_KEY_L) {
                selected.showLabel = !selected.showLabel;
                Config.save();
                return true;
            }
            if (key == GLFW.GLFW_KEY_B) {
                int i = 0;
                for (int k = 0; k < BOX.length; k++) if (Math.abs(BOX[k] - selected.boxOpacity) < 0.01f) i = k;
                selected.boxOpacity = BOX[(i + 1) % BOX.length];
                Config.save();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }
}
