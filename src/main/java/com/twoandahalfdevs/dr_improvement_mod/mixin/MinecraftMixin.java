package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.AutoClicker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Energy guard: any swing (yours or the auto clicker's) can be refused when energy is too low. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void drim$energyGuard(CallbackInfoReturnable<Boolean> cir) {
        if (!AutoClicker.allowSwing((Minecraft) (Object) this)) cir.setReturnValue(false);
    }
}
