package com.yesmenn.veilirislights.compat.light;

import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.SpotLightData;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional per-light beam shaping that Veil itself does not model. Public API: L?YM! calls these methods reflectively.
 * Area lights get edge softness and focus; spot lights take their edge softness from Veil's own angle and get focus
 * and an emitter (lens) radius, optionally stretched into a long capsule, here. An area light with an angle of 2 pi
 * (360 degrees) shines evenly in every direction from its rectangle, e.g. as a line light.
 * <p>
 * Any light can additionally carry {@link Extras}: whether it skips the first-person hand (camera-mounted lights such
 * as flashlights) and a chromatic fringe at the beam edge (cone lights only).
 */
public final class AreaLightShapeRegistry {
    private static final Map<LightData, Shape> SHAPES = new WeakHashMap<>();
    private static final Map<LightData, Extras> EXTRAS = new WeakHashMap<>();

    private AreaLightShapeRegistry() {
    }

    public static synchronized void set(AreaLightData light, float softness, float focus) {
        SHAPES.put(light, new Shape(clampSoftness(softness), clampFocus(focus), 0.0F, 0.0F));
    }

    public static synchronized void set(SpotLightData light, float focus, float lensRadius) {
        set(light, focus, lensRadius, 0.0F);
    }

    /**
     * Spot light with a long lens: a capsule, the disk of {@code lensRadius} swept {@code lensHalfLength} blocks to
     * both sides along the light's local x axis (e.g. a fluorescent tube). Surfaces and light beams are lit from the
     * whole length.
     */
    public static synchronized void set(SpotLightData light, float focus, float lensRadius, float lensHalfLength) {
        SHAPES.put(light, new Shape(0.0F, clampFocus(focus), clamp(lensRadius, 0.0F, 2.0F),
                clamp(lensHalfLength, 0.0F, 8.0F)));
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

    /**
     * @param skipFirstPerson the light never lights the first-person hand or held item
     * @param chromaticFringe 0-1, splits the cone edge into colored fringes like a simple lens
     */
    public static synchronized void setExtras(LightData light, boolean skipFirstPerson, float chromaticFringe) {
        EXTRAS.put(light, new Extras(skipFirstPerson, clamp(chromaticFringe, 0.0F, 1.0F)));
    }

    public static synchronized void clearExtras(LightData light) {
        EXTRAS.remove(light);
    }

    public static synchronized Extras getExtras(LightData light) {
        return EXTRAS.get(light);
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
     * @param lensHalfLength half length of a long (capsule) lens along local x (spot lights only, 0 = round lens)
     */
    public record Shape(float softness, float focus, float lensRadius, float lensHalfLength) {
    }

    public record Extras(boolean skipFirstPerson, float chromaticFringe) {
    }
}
