package com.yesmenn.veilirislights;

import com.mojang.logging.LogUtils;
import net.irisshaders.iris.api.v0.IrisApi;
import org.slf4j.Logger;

public final class VeilIrisLights {
    public static final String MODID = "veil_iris_lights";
    public static final Logger LOGGER = LogUtils.getLogger();

    private VeilIrisLights() {}

    /** Public API (L?YM! calls this reflectively): true while an Iris shaderpack renders the level. */
    public static boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }
}
