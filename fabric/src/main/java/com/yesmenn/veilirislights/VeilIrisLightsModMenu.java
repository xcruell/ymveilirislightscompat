package com.yesmenn.veilirislights;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.yesmenn.veilirislights.client.screen.ConfigScreenBridge;

public final class VeilIrisLightsModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreenBridge::create;
    }
}
