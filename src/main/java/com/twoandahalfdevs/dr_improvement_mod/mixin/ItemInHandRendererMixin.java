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
        this.mainHandItem = player.getMainHandItem();
        this.offHandItem = player.getOffhandItem();
        this.mainHandHeight = this.oMainHandHeight = 1.0F;
        this.offHandHeight = this.oOffHandHeight = 1.0F;
    }
}
