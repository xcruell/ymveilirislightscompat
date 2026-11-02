package com.yesmenn.veilirislights.compat.light;

import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.SpotLightData;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional per-light beam shaping that Veil itself does not model. Public API: L?YM! calls these methods reflectively.
 * Area lights get edge softness and focus; spot lights take their edge softness from Veil's own angle and get focus
 * and an emitter (lens) radius here.
 */
public final class AreaLightShapeRegistry {
    private static final Map<LightData, Shape> SHAPES = new WeakHashMap<>();

    private AreaLightShapeRegistry() {
    }

    public static synchronized void set(AreaLightData light, float softness, float focus) {
        SHAPES.put(light, new Shape(clampSoftness(softness), clampFocus(focus), 0.0F));
    }

    public static synchronized void set(SpotLightData light, float focus, float lensRadius) {
        SHAPES.put(light, new Shape(0.0F, clampFocus(focus), clamp(lensRadius, 0.0F, 2.0F)));
    }

    public static synchronized void clear(AreaLightData light) {
        SHAPES.remove(light);
    }

    public static synchronized void clear(SpotLightData light) {
        SHAPES.remove(light);
    }

    public static synchronized Shape get(LightData light) {
        return SHAPES.get(light);
    }

    private static float clampSoftness(float softness) {
        return clamp(softness, 0.005F, 0.5F);
    }

    private static float clampFocus(float focus) {
        return clamp(focus, 0.1F, 5.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * @param softness   cone edge width in cosine space (area lights only)
     * @param focus      exponent applied to the cone falloff
     * @param lensRadius emitter radius in blocks (spot lights only, 0 = point source)
     */
    public record Shape(float softness, float focus, float lensRadius) {
    }
}
