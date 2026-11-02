package com.yesmenn.veilirislights.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesmenn.veilirislights.client.screen.ConfigScreenBridge;
import com.yesmenn.veilirislights.compat.light.VilcSceneLightCompositor;
import com.yesmenn.veilirislights.config.LightRenderConfig;
import com.yesmenn.veilirislights.VeilIrisLights;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

public final class VeilIrisLightsClient {

    private static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.veil_iris_lights.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.veil_iris_lights");

    private static int lastTargetWidth = -1;
    private static int lastTargetHeight = -1;
    private static int lastColorTexture = -1;
    private static int lastDepthTexture = -1;

    private VeilIrisLightsClient() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    public static void onClientTick(ClientTickEvent.Post event) {

        Minecraft minecraft = Minecraft.getInstance();
        handleRenderTargetResize(minecraft);
        while (OPEN_CONFIG.consumeClick()) {
            minecraft.setScreen(ConfigScreenBridge.create(minecraft.screen));
        }
    }

    private static void handleRenderTargetResize(Minecraft minecraft) {
        if (minecraft.level == null) {
            lastTargetWidth = -1;
            lastTargetHeight = -1;
            lastColorTexture = -1;
            lastDepthTexture = -1;
            return;
        }

        com.mojang.blaze3d.pipeline.RenderTarget target = minecraft.getMainRenderTarget();
        int width = target.width;
        int height = target.height;
        int colorTexture = target.getColorTextureId();
        int depthTexture = target.getDepthTextureId();
        if (width <= 0 || height <= 0 || colorTexture == 0 || depthTexture == 0) {
            return;
        }

        if (lastTargetWidth == -1) {
            lastTargetWidth = width;
            lastTargetHeight = height;
            lastColorTexture = colorTexture;
            lastDepthTexture = depthTexture;
            return;
        }

        if (width != lastTargetWidth
                || height != lastTargetHeight
                || colorTexture != lastColorTexture
                || depthTexture != lastDepthTexture) {
            VilcSceneLightCompositor.releaseGpuResources();
            lastTargetWidth = width;
            lastTargetHeight = height;
            lastColorTexture = colorTexture;
            lastDepthTexture = depthTexture;
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (VeilIrisLights.isShaderPackInUse()
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        VilcSceneLightCompositor.renderVanillaLights(
                event.getModelViewMatrix(),
                event.getProjectionMatrix(),
                event.getCamera());
    }

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        VilcSceneLightCompositor.appendDiagnostics(event.getRight());
    }

    public static void initialize() {
        LightRenderConfig.load();
    }
}
