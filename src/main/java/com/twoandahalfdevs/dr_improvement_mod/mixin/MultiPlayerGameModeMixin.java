package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import com.twoandahalfdevs.dr_improvement_mod.MobHp;
import com.twoandahalfdevs.dr_improvement_mod.OreText;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    /**
     * Ability heads must never be placed as blocks. PASS (instead of the old CONSUME) skips the
     * block placement but still falls through to a plain item use, so right-clicking a block with
     * an ability head activates the ability instead of doing nothing.
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void drim$noHeadPlacement(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.getItemInHand(hand).is(Items.PLAYER_HEAD)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /** No 10-tick delay after swinging at air (1.8 behaviour). */
    @Inject(method = "hasMissTime", at = @At("HEAD"), cancellable = true)
    private void drim$noMissTime(CallbackInfoReturnable<Boolean> cir) {
        if (DrConfig.legacyCombat()) {
            cir.setReturnValue(false);
        }
    }

    /** Remember the block being mined so DR's mining lines can pop up on that ore. */
    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void drim$startDig(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (OreText.isMiningDig(pos)) OreText.onDig(pos, face);
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
    private void drim$continueDig(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (OreText.isMiningDig(pos)) OreText.onDig(pos, face);
    }

    /** Remember who we hit so the next "DMG -> Name [HP]" line can be matched to that mob. */
    @Inject(method = "attack", at = @At("HEAD"))
    private void drim$attack(Player player, Entity target, CallbackInfo ci) {
        MobHp.onAttack(target);
        com.twoandahalfdevs.dr_improvement_mod.AutoSkill.onAttack(target);
    }
}
