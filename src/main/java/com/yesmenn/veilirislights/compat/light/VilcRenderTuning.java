package com.yesmenn.veilirislights.compat.light;

import com.yesmenn.veilirislights.config.LightRenderConfig;

record VilcRenderTuning(
        float exposure,
        float colorStrength,
        float colorSaturation,
        float neutralLift,
        float luminanceBoostLimit) {

    static VilcRenderTuning from(LightRenderConfig config) {
        return new VilcRenderTuning(
                (float) config.exposure,
                (float) config.colorStrength,
                (float) config.colorSaturation,
                (float) config.neutralLift,
                (float) config.luminanceBoostLimit);
    }
}
