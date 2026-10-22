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
import com.twoandahalfdevs.dr_improvement_mod.DualWield;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;

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

    /**
     * Dual wield (visual): your swings alternate right / left on your screen. The server still gets the normal
     * main-hand swing packet, so nothing about the hit changes and other players see your usual swing.
     */
    @Inject(method = "swing", at = @At("HEAD"), cancellable = true)
    private void drim$dualSwing(InteractionHand hand, CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (hand != InteractionHand.MAIN_HAND || !DualWield.active(self)) return;
        InteractionHand arm = DualWield.nextHand();
        self.swing(arm, false); // the animation only (LivingEntity), no packet
        // only flip arms when a new swing actually started (not mid-swing, e.g. every tick while mining)
        if (self.swinging && self.swingTime == -1 && self.swingingArm == arm) DualWield.swung();
        self.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        ci.cancel();
    }
}
