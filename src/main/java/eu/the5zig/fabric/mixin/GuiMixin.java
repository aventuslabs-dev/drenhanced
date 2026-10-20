package eu.the5zig.fabric.mixin;

import eu.the5zig.fabric.Config;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The 5zig potions module replaces vanilla's top-right effect icons (optional, on by default). */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void the5zig$renderEffects(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        Config c = Config.get();
        if (c.hideVanillaEffects && c.potionsModuleActive()) ci.cancel();
    }
}
