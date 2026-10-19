package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    protected int sprintTriggerTime;

    /** No double-tap-W sprint. */
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void drim$noDoubleTapSprint(CallbackInfo ci) {
        if (DrConfig.legacyCombat()) {
            this.sprintTriggerTime = 0;
        }
    }

    /** Let the server do the drop; don't predict it client-side (the 1.8 server may refuse it). */
    @Redirect(method = "drop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;removeFromSelected(Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack drim$noClientDrop(Inventory inventory, boolean fullStack) {
        if (DrConfig.legacyCombat()) {
            return inventory.getSelected();
        }
        return inventory.removeFromSelected(fullStack);
    }

    /** Returning false stops the hand swing that vanilla plays after a successful drop. */
    @Inject(method = "drop", at = @At("RETURN"), cancellable = true)
    private void drim$noDropSwing(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (DrConfig.legacyCombat()) {
            cir.setReturnValue(false);
        }
    }
}
