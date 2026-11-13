package com.yesmenn.vilc.client;

import com.yesmenn.vilc.YMVilc;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class YMVilcClient {

    private YMVilcClient() {
    }

    public static void initialize() {
        YMVilcClientCommon.initialize();
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(YMVilcClientCommon.OPEN_CONFIG);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        YMVilcClientCommon.onTick();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (YMVilc.isShaderPackInUse()
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        YMVilcClientCommon.renderVanillaIfNeeded(
                event.getModelViewMatrix(),
                event.getProjectionMatrix(),
                event.getCamera());
    }

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        YMVilcClientCommon.appendDebugText(event.getRight());
    }
}
