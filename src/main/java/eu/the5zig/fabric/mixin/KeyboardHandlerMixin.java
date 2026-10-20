package eu.the5zig.fabric.mixin;

import eu.the5zig.fabric.Tracker;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Any key press counts as activity for the AFK timer. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void the5zig$keyPress(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (action != 0) Tracker.input();
    }
}
