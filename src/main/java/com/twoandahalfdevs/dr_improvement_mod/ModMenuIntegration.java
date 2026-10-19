package com.twoandahalfdevs.dr_improvement_mod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Only loaded when ModMenu is installed (the "modmenu" entrypoint). */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DrConfigScreen::new;
    }
}
