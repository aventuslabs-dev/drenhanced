package com.twoandahalfdevs.dr_improvement_mod;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the energy arcs, mana bar and HP bar as one fullscreen quad through a core shader
 * (assets/dr_improvement_mod/shaders/core/dr_overlay.*). Going through Minecraft's shader manager
 * instead of raw GL means it survives F3+T / resource-pack reloads and keeps GL state tracking in sync.
 */
public final class OverlayRenderer {
    private OverlayRenderer() {}

    public static final ShaderProgram PROGRAM = new ShaderProgram(
        ResourceLocation.fromNamespaceAndPath(DrImprovementMod.MOD_ID, "core/dr_overlay"),
        DefaultVertexFormat.POSITION,
        ShaderDefines.EMPTY);

    private static boolean failed;

    // must match dr_overlay.fsh
    private static final double MANA_BAR_Y = 0.42, MANA_BAR_START = 0.5 - 0.073 / 2.0, MIDHP_GAP = 0.017, BAR_H = 0.012;

    /** HP and mana numbers to the left of the centre bars (drawn with the HUD text, after the shader bars). */
    public static void drawText(net.minecraft.client.gui.GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        DrConfig.Data c = DrConfig.data;
        DrConfig.Bar mb = c.manaBar;
        if (mc.player == null || mc.options.hideGui || failed || !c.customBars || !mb.enabled || !c.barText) return;
        int gw = g.guiWidth(), gh = g.guiHeight();
        double sc = mb.scale;
        // the bars' left end and centre lines, in screen fractions (uv: y up), then GUI pixels
        double left = 0.5 + (MANA_BAR_START - 0.5) * sc + mb.x / 100.0;
        double manaY = MANA_BAR_Y + mb.y / 100.0;
        double hpY = MANA_BAR_Y + MIDHP_GAP * sc + mb.y / 100.0;
        float ts = (float) Math.max(0.5, Math.min(1.0, BAR_H * 1.3 * sc * gh / 8.0)); // text about as tall as a bar row
        int alpha = (int) Math.round(255 * mb.opacity) << 24;
        int xRight = (int) Math.round(left * gw) - 3;
        var font = mc.font;
        java.text.NumberFormat nf = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US);
        if (c.midHealthBar) {
            int hp = DrState.hp(), max = DrState.hpMax();
            String t;
            if (hp >= 0 && max > 0) t = nf.format(hp) + " / " + nf.format(max);
            else t = Math.round(mc.player.getHealth() / Math.max(1f, mc.player.getMaxHealth()) * 100) + "%";
            label(g, font, t, xRight, (1 - hpY) * gh, ts, alpha | 0x5BE36E);
        }
        label(g, font, DrState.mana() + " / " + DrState.manaMax(), xRight, (1 - manaY) * gh, ts, alpha | 0x6FC0FF);
    }

    private static void label(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, String t, int xRight, double cy, float s, int color) {
        g.pose().pushPose();
        g.pose().translate(xRight - font.width(t) * s, cy - 4 * s, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, t, 0, 0, color, true);
        g.pose().popPose();
    }

    public static void draw() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || failed) return;
        DrConfig.Data c = DrConfig.data;
        if (!c.energyBar.enabled && !c.manaBar.enabled && !c.healthBar.enabled) return;

        CompiledShaderProgram shader;
        try {
            shader = RenderSystem.setShader(PROGRAM);
        } catch (RuntimeException e) {
            // Don't spam a broken shader every frame; log once and fall back to vanilla HUD.
            failed = true;
            DrImprovementMod.LOGGER.error("DR overlay shader failed to load; custom bars disabled", e);
            return;
        }
        if (shader == null) return;

        Window window = mc.getWindow();
        float maxHealth = player.getMaxHealth();
        shader.safeGetUniform("energyPercent").set(DrState.interpolatedEnergy());
        // DR's real HP from its boss bar when known (same numbers as the text), else the synced entity health
        float hpFrac = DrState.hp() >= 0 && DrState.hpMax() > 0 ? DrState.hp() / (float) DrState.hpMax()
            : maxHealth > 0f ? player.getHealth() / maxHealth : 0f;
        shader.safeGetUniform("healthPercent").set(hpFrac);
        shader.safeGetUniform("manaPercent").set(DrState.mana() / (float) DrState.manaMax());
        shader.safeGetUniform("aspectRatio").set((float) window.getWidth() / Math.max(1, window.getHeight()));
        place(shader, "energy", DrConfig.data.energyBar);
        place(shader, "mana", DrConfig.data.manaBar);
        place(shader, "health", DrConfig.data.healthBar);
        DrConfig.Bar mb = DrConfig.data.manaBar;
        shader.safeGetUniform("midHealthAlpha").set(mb.enabled && DrConfig.data.midHealthBar ? (float) mb.opacity : 0f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        // Counter-clockwise so back-face culling keeps it.
        buf.addVertex(-1f, -1f, 0f);
        buf.addVertex(1f, -1f, 0f);
        buf.addVertex(1f, 1f, 0f);
        buf.addVertex(-1f, 1f, 0f);
        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void place(CompiledShaderProgram shader, String name, DrConfig.Bar b) {
        shader.safeGetUniform(name + "X").set((float) (b.x / 100.0));
        shader.safeGetUniform(name + "Y").set((float) (b.y / 100.0));
        shader.safeGetUniform(name + "Scale").set((float) b.scale);
        shader.safeGetUniform(name + "Alpha").set(b.enabled ? (float) b.opacity : 0f);
    }
}
