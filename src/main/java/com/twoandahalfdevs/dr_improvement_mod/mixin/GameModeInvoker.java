package com.twoandahalfdevs.dr_improvement_mod.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Send the selected hotbar slot to the server right now. */
@Mixin(MultiPlayerGameMode.class)
public interface GameModeInvoker {
    @Invoker("ensureHasSentCarriedItem")
    void drim$sendCarried();
}
