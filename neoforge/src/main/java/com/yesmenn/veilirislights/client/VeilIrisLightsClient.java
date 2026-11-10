package com.yesmenn.veilirislights.client;

import com.yesmenn.veilirislights.VeilIrisLights;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class VeilIrisLightsClient {

    private VeilIrisLightsClient() {
    }

    public static void initialize() {
        VeilIrisLightsClientCommon.initialize();
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(VeilIrisLightsClientCommon.OPEN_CONFIG);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        VeilIrisLightsClientCommon.onTick();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (VeilIrisLights.isShaderPackInUse()
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        VeilIrisLightsClientCommon.renderVanillaIfNeeded(
                event.getModelViewMatrix(),
                event.getProjectionMatrix(),
                event.getCamera());
    }

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        VeilIrisLightsClientCommon.appendDebugText(event.getRight());
    }
}
