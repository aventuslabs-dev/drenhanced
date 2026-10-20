package eu.the5zig.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import eu.the5zig.fabric.gui.SettingsScreen;

/** Only loaded when ModMenu is installed (the "modmenu" entrypoint). */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
