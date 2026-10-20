package eu.the5zig.fabric.mixin;

import eu.the5zig.fabric.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts in-game mouse clicks for the CPS item and resets the AFK timer. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void the5zig$onPress(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action != 1) return; // GLFW_PRESS
        Tracker.input();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null) Tracker.click(button);
    }
}
