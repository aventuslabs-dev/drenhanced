package eu.the5zig.fabric.gui;

import eu.the5zig.fabric.Config;
import eu.the5zig.fabric.hud.ItemType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Pick which items a module shows, their order, style and prefix. */
public final class ModuleItemsScreen extends Screen {
    private static final int ROW = 22;

    private final Screen parent;
    private final Config.Module module;
    private int scroll;
    private int rowsTotal;

    public ModuleItemsScreen(Screen parent, Config.Module module) {
        super(Component.literal("Module items"));
        this.parent = parent;
        this.module = module;
    }

    private static Component lit(String s) {
        return Component.literal(s);
    }

    private static String pretty(String opt) {
        return opt == null ? "" : opt.charAt(0) + opt.substring(1).toLowerCase().replace('_', ' ');
    }

    private int listTop() {
        return 58;
    }

    private int visibleRows() {
        return Math.max(1, (height - listTop() - 32) / ROW);
    }

    @Override
    protected void init() {
        int cx = width / 2;
        EditBox name = new EditBox(font, cx - 154, 24, 150, 20, lit("Name"));
        name.setMaxLength(48);
        name.setValue(module.name == null ? "" : module.name);
        name.setResponder(s -> {
            module.name = s;
            Config.save();
        });
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(lit("Label: " + (module.showLabel ? "§aON" : "§cOFF")), b -> {
            module.showLabel = !module.showLabel;
            Config.save();
            rebuildWidgets();
        }).bounds(cx + 4, 24, 72, 20).build());
        addRenderableWidget(Button.builder(lit(pretty(module.location.name())), b -> {
            module.location = module.location.next();
            Config.save();
            rebuildWidgets();
        }).bounds(cx + 80, 24, 74, 20).build());

        // Included items first (in display order), then everything else.
        List<ItemType> order = new ArrayList<>();
        for (Config.Item i : module.items) {
            ItemType t = ItemType.byName(i.type);
            if (t != null && !order.contains(t)) order.add(t);
        }
        for (ItemType t : ItemType.values()) if (!order.contains(t)) order.add(t);
        rowsTotal = order.size();
        scroll = Math.max(0, Math.min(scroll, rowsTotal - visibleRows()));

        int y = listTop();
        for (int r = scroll; r < order.size() && r < scroll + visibleRows(); r++) {
            ItemType t = order.get(r);
            Config.Item item = module.find(t);
            boolean on = item != null;
            addRenderableWidget(Button.builder(lit((on ? "§a✔ " : "§7✘ ") + t.label), b -> {
                if (module.find(t) != null) module.items.remove(module.find(t));
                else module.items.add(new Config.Item(t));
                Config.save();
                rebuildWidgets();
            }).bounds(cx - 154, y, 130, 20).build());
            if (on) {
                if (t.hasOptions()) {
                    addRenderableWidget(Button.builder(lit(pretty(t.option(item.option))), b -> {
                        item.option = t.nextOption(item.option);
                        Config.save();
                        b.setMessage(lit(pretty(item.option)));
                    }).bounds(cx - 20, y, 96, 20).build());
                }
                if (t.kind == ItemType.Kind.TEXT) {
                    addRenderableWidget(Button.builder(lit(item.showPrefix ? "Prefix" : "§8Prefix"), b -> {
                        item.showPrefix = !item.showPrefix;
                        Config.save();
                        rebuildWidgets();
                    }).bounds(cx + 80, y, 42, 20).build());
                }
                addRenderableWidget(Button.builder(lit("▲"), b -> move(item, -1)).bounds(cx + 126, y, 14, 20).build());
                addRenderableWidget(Button.builder(lit("▼"), b -> move(item, 1)).bounds(cx + 140, y, 14, 20).build());
            }
            y += ROW;
        }

        int by = height - 26;
        addRenderableWidget(Button.builder(lit("§cDelete module"), b -> {
            Config.get().modules.remove(module);
            Config.save();
            onClose();
        }).bounds(cx - 154, by, 150, 20).build());
        addRenderableWidget(Button.builder(lit("Done"), b -> onClose()).bounds(cx + 4, by, 150, 20).build());
    }

    private void move(Config.Item item, int dir) {
        int i = module.items.indexOf(item);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= module.items.size()) return;
        module.items.set(i, module.items.get(j));
        module.items.set(j, item);
        Config.save();
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int next = Math.max(0, Math.min(rowsTotal - visibleRows(), scroll - (int) Math.signum(sy) * 2));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, "Module: " + module.id + "   §7(scroll for more)", width / 2, 10, 0xFFFFAA00);
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.setScreen(parent);
    }
}
