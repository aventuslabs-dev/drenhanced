package com.twoandahalfdevs.dr_improvement_mod;

import com.twoandahalfdevs.dr_improvement_mod.mixin.GuiAccessor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fabric API side of the mod (everything that doesn't need a mixin). */
public final class ClientEvents {
    private ClientEvents() {}

    private static final Pattern ORIGIN = Pattern.compile("(.*) \\((.*)/.*\\)");

    private static int lastSelectedSlot = -1;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ClientEvents::onClientTick);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addOrigin(stack, lines));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastSelectedSlot = -1;
            DrState.reset();
        });
        // /drconfig opens the settings screen (handy without ModMenu).
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("drconfig").executes(ctx -> {
                Minecraft mc = Minecraft.getInstance();
                // Queue it so the chat screen closing doesn't immediately replace it.
                mc.tell(() -> mc.setScreen(new DrConfigScreen(null)));
                return 1;
            })));
    }

    private static void onClientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            lastSelectedSlot = -1;
            return;
        }
        HealthSync.tick(mc);
        hotbarAbility(mc, mc.player);
    }

    /**
     * Selecting an ability head (scroll or number key) uses it immediately and snaps back to the
     * previous slot. useItem() syncs the head slot to the server first; the next gameMode tick
     * syncs the previous slot back.
     */
    private static void hotbarAbility(Minecraft mc, LocalPlayer player) {
        int selected = player.getInventory().selected;
        int previous = lastSelectedSlot;
        lastSelectedSlot = selected;
        if (previous < 0 || previous == selected || !DrConfig.hotbarAbilities() || mc.screen != null || mc.gameMode == null) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(selected);
        if (stack.is(Items.PLAYER_HEAD)) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            player.getInventory().selected = previous;
            lastSelectedSlot = previous;
        }
    }

    /** Called from GuiMixin before the vanilla HUD layers. */
    public static void renderHudBelow() {
        if (DrConfig.customBars()) {
            OverlayRenderer.draw();
        }
    }

    /** Called from GuiMixin after the vanilla HUD layers. */
    public static void renderHudAbove(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        GuiAccessor gui = (GuiAccessor) mc.gui;
        Component msg = gui.drim$getOverlayMessage();
        DrState.onActionBar(msg == null ? null : msg.getString(), gui.drim$getOverlayTime());

        if (DrConfig.usefulInfo()) {
            HudRenderer.draw(g);
        }
    }

    /** Shows where an item came from (DR stores "Type (Player/...)" in its custom data). */
    private static void addOrigin(ItemStack stack, List<Component> lines) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        if (!tag.contains("origin")) return;

        Matcher m = ORIGIN.matcher(tag.getString("origin"));
        if (m.find()) {
            lines.add(Component.empty());
            lines.add(Component.literal("Origin: " + m.group(1) + " - ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(m.group(2)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        }
    }
}
