package com.yesmenn.vilc;

import com.yesmenn.vilc.client.YMVilcClientCommon;
import com.yesmenn.vilc.platform.FabricPlatform;
import com.yesmenn.vilc.platform.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class YMVilcFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Platform.init(new FabricPlatform());
        YMVilcClientCommon.initialize();
        KeyBindingHelper.registerKeyBinding(YMVilcClientCommon.OPEN_CONFIG);
        ClientTickEvents.END_CLIENT_TICK.register(client -> YMVilcClientCommon.onTick());
        WorldRenderEvents.LAST.register(this::onRenderLast);
    }

    private void onRenderLast(WorldRenderContext context) {
        if (YMVilc.isShaderPackInUse()) {
            return;
        }
        // positionMatrix() is the view matrix (camera rotation), the same as NeoForge's getModelViewMatrix().
        // matrixStack() does not carry the camera rotation in 1.21.1; using it placed the lights as if the
        // camera never turned.
        YMVilcClientCommon.renderVanillaIfNeeded(
                context.positionMatrix(),
                context.projectionMatrix(),
                context.camera());
    }
}
