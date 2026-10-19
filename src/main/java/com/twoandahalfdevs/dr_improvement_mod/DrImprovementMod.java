package com.twoandahalfdevs.dr_improvement_mod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(DrImprovementMod.MOD_ID)
public final class DrImprovementMod {
    public static final String MOD_ID = "dr_improvement_mod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DrImprovementMod(FMLJavaModLoadingContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            LOGGER.warn("DR Improvement Mod is client-only; doing nothing on a dedicated server.");
            return;
        }
        context.registerConfig(ModConfig.Type.CLIENT, DrConfig.SPEC);
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new DrConfigScreen(parent)));
        ClientEvents.register();
    }
}
