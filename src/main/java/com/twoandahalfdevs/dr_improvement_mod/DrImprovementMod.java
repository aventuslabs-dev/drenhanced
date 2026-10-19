package com.twoandahalfdevs.dr_improvement_mod;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DrImprovementMod implements ClientModInitializer {
    public static final String MOD_ID = "dr_improvement_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        DrConfig.load();
        ClientEvents.register();
        if (SelfTest.enabled()) {
            SelfTest.register();
        }
    }
}
