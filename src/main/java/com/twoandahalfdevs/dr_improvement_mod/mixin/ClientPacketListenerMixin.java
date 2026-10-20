package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrState;
import com.twoandahalfdevs.dr_improvement_mod.HealthSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet taps. Each handler is invoked first on the network thread (where vanilla reschedules it)
 * and again on the main thread; we only act on the main-thread call.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    private static boolean drim$offThread() {
        return !Minecraft.getInstance().isSameThread();
    }

    /** DR: bar progress = energy, total XP = mana (0-100). Show mana on the vanilla bar, energy on ours. */
    @Inject(method = "handleSetExperience", at = @At("HEAD"), cancellable = true)
    private void drim$experience(ClientboundSetExperiencePacket packet, CallbackInfo ci) {
        if (drim$offThread()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        DrState.onEnergy(packet.getExperienceProgress());
        mc.player.setExperienceValues(packet.getTotalExperience() / 100f, packet.getExperienceLevel(), packet.getTotalExperience());
        ci.cancel();
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void drim$systemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (drim$offThread()) return;
        com.twoandahalfdevs.dr_improvement_mod.Capture.chat(packet.overlay() ? "ACTIONBAR_PKT" : "CHAT", packet.content());
        if (packet.overlay()) return;
        DrState.onChat(packet.content().getString());
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void drim$playerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (drim$offThread()) return;
        com.twoandahalfdevs.dr_improvement_mod.Capture.chat("PLAYERCHAT", net.minecraft.network.chat.Component.literal(packet.body().content()));
        DrState.onChat(packet.body().content());
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void drim$entityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (drim$offThread()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(packet.id());
        if (!(entity instanceof Player player)) return;

        int healthId = LivingEntityAccessor.drim$healthId().id();
        for (SynchedEntityData.DataValue<?> value : packet.packedItems()) {
            if (value.id() == healthId && value.value() instanceof Float health) {
                HealthSync.onEntityHealth(player.getScoreboardName(), health);
            }
        }
    }

    /** Not cancelled any more: if we don't know a player's max HP yet, the server's value still shows. */
    @Inject(method = "handleSetScore", at = @At("HEAD"))
    private void drim$score(ClientboundSetScorePacket packet, CallbackInfo ci) {
        if (drim$offThread()) return;
        HealthSync.onScore(packet.objectiveName(), packet.owner(), packet.score());
    }
}
