package eu.the5zig.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import eu.the5zig.fabric.gui.EditorScreen;
import eu.the5zig.fabric.gui.SettingsScreen;
import eu.the5zig.fabric.hud.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class The5zigMod implements ClientModInitializer {
    public static final String MOD_ID = "the5zig";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String CATEGORY = "key.categories.the5zig";
    private static KeyMapping settingsKey, editorKey, toggleKey, timerKey, timerResetKey;
    /** Set by the /5zig command; the screen opens on the next tick, after the chat screen has closed. */
    private static volatile boolean openSettings;

    @Override
    public void onInitializeClient() {
        Config.load();

        settingsKey = key("key.the5zig.settings", GLFW.GLFW_KEY_RIGHT_SHIFT);
        editorKey = key("key.the5zig.editor", InputConstants.UNKNOWN.getValue());
        toggleKey = key("key.the5zig.toggle", GLFW.GLFW_KEY_F6);
        timerKey = key("key.the5zig.timer", InputConstants.UNKNOWN.getValue());
        timerResetKey = key("key.the5zig.timer_reset", InputConstants.UNKNOWN.getValue());

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> HudRenderer.renderHud(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(The5zigMod::onTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("5zig").executes(ctx -> {
                    openSettings = true;
                    return 1;
                })));

        if (SelfTest.enabled()) SelfTest.register();
        LOGGER.info("The 5zig Mod (Fabric) loaded");
    }

    private static KeyMapping key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, code, CATEGORY));
    }

    private static void onTick(Minecraft mc) {
        Tracker.tick(mc);
        if (openSettings) {
            openSettings = false;
            mc.setScreen(new SettingsScreen(null));
        }
        while (settingsKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new SettingsScreen(null));
        }
        while (editorKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new EditorScreen(null));
        }
        while (toggleKey.consumeClick()) {
            Config c = Config.get();
            c.enabled = !c.enabled;
            Config.save();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§6[5zig] §fHUD " + (c.enabled ? "§aon" : "§coff")), true);
            }
        }
        while (timerKey.consumeClick()) Tracker.toggleTimer();
        while (timerResetKey.consumeClick()) Tracker.resetTimer();
    }
}
