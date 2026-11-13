package com.yesmenn.vilc;

import com.mojang.logging.LogUtils;
import net.irisshaders.iris.api.v0.IrisApi;
import org.slf4j.Logger;

public final class YMVilc {
    public static final String MODID = "ymvilc";
    public static final Logger LOGGER = LogUtils.getLogger();

    private YMVilc() {}

    /** Public API (ymCL calls this reflectively): true while an Iris shaderpack renders the level. */
    public static boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }
}
