package com.yesmenn.veilirislights;

import com.yesmenn.veilirislights.client.VeilIrisLightsClient;
import com.yesmenn.veilirislights.client.screen.ConfigScreenBridge;
import com.yesmenn.veilirislights.platform.NeoForgePlatform;
import com.yesmenn.veilirislights.platform.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(VeilIrisLights.MODID)
public final class VeilIrisLightsNeoForge {

    public VeilIrisLightsNeoForge(IEventBus modBus, ModContainer container) {
        Platform.init(new NeoForgePlatform());
        VeilIrisLightsClient.initialize();
        modBus.addListener(VeilIrisLightsClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(VeilIrisLightsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(VeilIrisLightsClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(VeilIrisLightsClient::onDebugText);
        IConfigScreenFactory configScreenFactory =
                (ignored, parent) -> ConfigScreenBridge.create(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);
    }
}
