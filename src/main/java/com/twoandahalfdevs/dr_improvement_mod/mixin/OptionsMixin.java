package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.FullBright;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Save the player's real gamma, not the full-bright override. */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void drim$saveStart(CallbackInfo ci) {
        FullBright.saving = true;
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void drim$saveEnd(CallbackInfo ci) {
        FullBright.saving = false;
    }
}
