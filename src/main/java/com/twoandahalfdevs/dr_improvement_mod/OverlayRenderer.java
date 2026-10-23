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
        shader.safeGetUniform("healthPercent").set(maxHealth > 0f ? player.getHealth() / maxHealth : 0f);
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
