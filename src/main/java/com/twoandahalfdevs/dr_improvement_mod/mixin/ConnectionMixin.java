package com.twoandahalfdevs.dr_improvement_mod.mixin;

import com.twoandahalfdevs.dr_improvement_mod.DrConfig;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Debounces client-settings (1/s) and tab-completion (2/s) packets during play.
 * Unlike the Fabric version, the first packet goes out immediately; only bursts are held back,
 * and the newest held packet is flushed once the interval has passed.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow
    public abstract void send(Packet<?> packet);

    @Shadow
    public abstract PacketListener getPacketListener();

    @Unique private static final long SETTINGS_INTERVAL_MS = 1000L;
    @Unique private static final long COMPLETION_INTERVAL_MS = 500L;

    @Unique private Packet<?> drim$heldSettings;
    @Unique private Packet<?> drim$heldCompletion;
    @Unique private long drim$lastSettings;
    @Unique private long drim$lastCompletion;
    @Unique private boolean drim$flushing;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
        at = @At("HEAD"), cancellable = true)
    private void drim$throttle(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush, CallbackInfo ci) {
        if (drim$flushing || !DrConfig.throttlePackets() || !(getPacketListener() instanceof ClientPacketListener)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (packet instanceof ServerboundClientInformationPacket) {
            if (now - drim$lastSettings < SETTINGS_INTERVAL_MS) {
                drim$heldSettings = packet;
                ci.cancel();
            } else {
                drim$lastSettings = now;
                drim$heldSettings = null;
            }
        } else if (packet instanceof ServerboundCommandSuggestionPacket) {
            if (now - drim$lastCompletion < COMPLETION_INTERVAL_MS) {
                drim$heldCompletion = packet;
                ci.cancel();
            } else {
                drim$lastCompletion = now;
                drim$heldCompletion = null;
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void drim$flush(CallbackInfo ci) {
        if (!(getPacketListener() instanceof ClientPacketListener)) {
            drim$heldSettings = null;
            drim$heldCompletion = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (drim$heldSettings != null && now - drim$lastSettings >= SETTINGS_INTERVAL_MS) {
            drim$lastSettings = now;
            drim$sendHeld(drim$heldSettings);
            drim$heldSettings = null;
        }
        if (drim$heldCompletion != null && now - drim$lastCompletion >= COMPLETION_INTERVAL_MS) {
            drim$lastCompletion = now;
            drim$sendHeld(drim$heldCompletion);
            drim$heldCompletion = null;
        }
    }

    @Unique
    private void drim$sendHeld(Packet<?> packet) {
        drim$flushing = true;
        try {
            send(packet);
        } finally {
            drim$flushing = false;
        }
    }
}
