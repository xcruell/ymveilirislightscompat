package com.yesmenn.veilirislights;

import com.yesmenn.veilirislights.client.VeilIrisLightsClientCommon;
import com.yesmenn.veilirislights.platform.FabricPlatform;
import com.yesmenn.veilirislights.platform.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class VeilIrisLightsFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Platform.init(new FabricPlatform());
        VeilIrisLightsClientCommon.initialize();
        KeyBindingHelper.registerKeyBinding(VeilIrisLightsClientCommon.OPEN_CONFIG);
        ClientTickEvents.END_CLIENT_TICK.register(client -> VeilIrisLightsClientCommon.onTick());
        WorldRenderEvents.LAST.register(this::onRenderLast);
    }

    private void onRenderLast(WorldRenderContext context) {
        if (VeilIrisLights.isShaderPackInUse()) {
            return;
        }
        VeilIrisLightsClientCommon.renderVanillaIfNeeded(
                context.matrixStack().last().pose(),
                context.projectionMatrix(),
                context.camera());
    }
}
