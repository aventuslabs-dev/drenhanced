package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.twoandahalfdevs.dr_improvement_mod.DualWield;

/** No held-item "re-equip" dip every time you hit something. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;

    @Inject(method = "tick", at = @At("TAIL"))
    private void drim$noReequip(CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || !DrConfig.legacyCombat()) return;
        this.mainHandItem = DualWield.mainShown(player);
        this.offHandItem = DualWield.offhandShown(player);
        this.mainHandHeight = this.oMainHandHeight = 1.0F;
        this.offHandHeight = this.oOffHandHeight = 1.0F;
    }

    /** Auto skills: the artifact is selected for one tick; keep drawing the weapon. */
    @WrapOperation(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack drim$skillMainHand(LocalPlayer player, Operation<ItemStack> original) {
        return com.twoandahalfdevs.dr_improvement_mod.AutoSkill.shownMainHand(player, original.call(player));
    }

    /** Dual wield (visual): the first-person offhand shows your main-hand weapon. */
    @WrapOperation(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack drim$dualOffhand(LocalPlayer player, Operation<ItemStack> original) {
        return DualWield.active(player) ? DualWield.mainShown(player) : original.call(player);
    }
}
