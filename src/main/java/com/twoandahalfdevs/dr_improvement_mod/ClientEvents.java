package com.twoandahalfdevs.dr_improvement_mod;

import com.twoandahalfdevs.dr_improvement_mod.mixin.GuiAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Forge-event side of the mod (everything that doesn't need a mixin). */
public final class ClientEvents {
    private ClientEvents() {}

    private static final Pattern ORIGIN = Pattern.compile("(.*) \\((.*)/.*\\)");
    private static final int BOSS_BAR_EXTRA_SPACING = 14;

    private static int lastSelectedSlot = -1;

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onRenderGuiPre);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onRenderGuiPost);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onBossBar);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onTooltip);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onCrit);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onLogout);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
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

    private static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (DrConfig.customBars()) {
            OverlayRenderer.draw();
        }
    }

    private static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GuiAccessor gui = (GuiAccessor) mc.gui;
        Component msg = gui.drim$getOverlayMessage();
        DrState.onActionBar(msg == null ? null : msg.getString(), gui.drim$getOverlayTime());

        if (DrConfig.usefulInfo()) {
            HudRenderer.draw(event.getGuiGraphics());
        }
    }

    /** The "<Class> Lv X - HP a/b" boss bar: record the class, hide the bar but keep its text. */
    private static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!DrConfig.customBars()) return;

        Component name = event.getBossEvent().getName();
        String plain = name.getString();
        if (plain.contains("Lv ") && plain.contains(" - HP ")) {
            int dash = plain.indexOf('-');
            if (dash > 0) {
                DrState.setPlayerClass(plain.substring(0, dash).trim());
            }
            event.setCanceled(true);
            GuiGraphics g = event.getGuiGraphics();
            Font font = Minecraft.getInstance().font;
            int x = g.guiWidth() / 2 - font.width(name) / 2;
            g.drawString(font, name, x, event.getY() - 9, 0xFFFFFF);
        }
        // Push every bar down to make room for the shader HP bar.
        event.setIncrement(event.getIncrement() + BOSS_BAR_EXTRA_SPACING);
    }

    /** Shows where an item came from (DR stores "Type (Player/...)" in its custom data). */
    private static void onTooltip(ItemTooltipEvent event) {
        CustomData data = event.getItemStack().get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        if (!tag.contains("origin")) return;

        Matcher m = ORIGIN.matcher(tag.getString("origin"));
        if (m.find()) {
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.literal("Origin: " + m.group(1) + " - ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(m.group(2)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        }
    }

    /** No client-side crit sound/particles (the server decides crits). Never touches singleplayer's server side. */
    private static void onCrit(CriticalHitEvent event) {
        if (DrConfig.legacyCombat() && event.getEntity().level().isClientSide()) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        lastSelectedSlot = -1;
        DrState.reset();
    }
}
