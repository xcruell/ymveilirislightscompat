package com.yesmenn.vilc.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesmenn.vilc.client.screen.ConfigScreenBridge;
import com.yesmenn.vilc.compat.light.VilcSceneLightCompositor;
import com.yesmenn.vilc.config.LightRenderConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class YMVilcClientCommon {

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.ymvilc.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.ymvilc");

    private static int lastTargetWidth = -1;
    private static int lastTargetHeight = -1;
    private static int lastColorTexture = -1;
    private static int lastDepthTexture = -1;

    private YMVilcClientCommon() {}

    public static void initialize() {
        LightRenderConfig.load();
        com.yesmenn.vilc.client.screen.VilcSettings.register();
    }

    public static void onTick() {
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

    public static void renderVanillaIfNeeded(Matrix4f modelView, Matrix4f projection, Camera camera) {
        VilcSceneLightCompositor.renderVanillaLights(modelView, projection, camera);
    }

    public static void appendDebugText(List<String> rightLines) {
        VilcSceneLightCompositor.appendDiagnostics(rightLines);
    }
}
