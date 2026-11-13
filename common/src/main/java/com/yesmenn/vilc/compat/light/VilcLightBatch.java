package com.yesmenn.vilc.compat.light;

import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.Collection;

import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform4fv;

/**
 * Packs Veil lights into the light shader's uniform arrays.
 * <p>
 * The color alpha channel carries per-light data so no extra uniform arrays are needed (some drivers only expose
 * 1024 fragment uniform vectors): {@code alpha = flags + 4 * round(inscattering * 256)} with flag bit 0 = voxel
 * shadows and bit 1 = skip first-person hand. Lights with in-scattering are uploaded first, so the beam raymarch
 * only loops over the first {@code beamCount} entries.
 */
final class VilcLightBatch {
    private static final int MAX_POINT_LIGHTS = com.yesmenn.vilc.config.LightRenderConfig.MAX_POINT_LIGHTS;
    private static final int MAX_AREA_LIGHTS = com.yesmenn.vilc.config.LightRenderConfig.MAX_AREA_LIGHTS;
    private static final float MAX_INSCATTERING = 16.0F;
    private static final float[] POINT_POSITIONS = new float[MAX_POINT_LIGHTS * 4];
    private static final float[] POINT_COLORS = new float[MAX_POINT_LIGHTS * 4];
    private static final float[] AREA_POSITIONS = new float[MAX_AREA_LIGHTS * 4];
    private static final float[] AREA_DIRECTIONS = new float[MAX_AREA_LIGHTS * 4];
    private static final float[] AREA_RIGHT_SIZE = new float[MAX_AREA_LIGHTS * 4];
    private static final float[] AREA_UP_SIZE = new float[MAX_AREA_LIGHTS * 4];
    private static final float[] AREA_COLORS = new float[MAX_AREA_LIGHTS * 4];
    private static final float[] AREA_SHAPES = new float[MAX_AREA_LIGHTS * 4];

    private VilcLightBatch() {
    }

    /**
     * @param count     uploaded lights
     * @param beamCount leading lights that have in-scattering (only meaningful when beams are enabled)
     */
    record Upload(int count, int beamCount) {
    }

    static Upload uploadPointLights(
            Collection<? extends LightRenderHandle<PointLightData>> handles,
            VilcLightUniforms uniforms,
            float exposure,
            int requestedLimit,
            float lightGain,
            boolean shadows,
            boolean beams) {
        int limit = Math.min(requestedLimit, MAX_POINT_LIGHTS);
        int count = 0;
        int beamCount = 0;
        // Pass 0 uploads beam lights, pass 1 the rest; without beams everything goes in pass 1.
        for (int pass = beams ? 0 : 1; pass < 2; pass++) {
            for (LightRenderHandle<PointLightData> handle : handles) {
                if (count >= limit) {
                    break;
                }
                PointLightData light = handle.isValid() ? handle.getLightData() : null;
                if (light == null) {
                    continue;
                }
                float inscattering = beams ? inscattering(light.getInscatteringStrength()) : 0.0F;
                if ((pass == 0) != (inscattering > 0.0F)) {
                    continue;
                }
                Vector3dc position = light.getPosition();
                Colorc color = light.getColor();
                float brightness = exposure * lightGain * light.getBrightness();
                int index = count * 4;
                POINT_POSITIONS[index] = (float) position.x();
                POINT_POSITIONS[index + 1] = (float) position.y();
                POINT_POSITIONS[index + 2] = (float) position.z();
                POINT_POSITIONS[index + 3] = light.getRadius();
                POINT_COLORS[index] = color.red() * brightness;
                POINT_COLORS[index + 1] = color.green() * brightness;
                POINT_COLORS[index + 2] = color.blue() * brightness;
                POINT_COLORS[index + 3] = packFlags(light, shadows, inscattering);
                count++;
                if (pass == 0) {
                    beamCount++;
                }
            }
        }

        glUniform1i(uniforms.pointCount(), count);
        glUniform1i(uniforms.pointBeamCount(), beamCount);
        if (count > 0) {
            glUniform4fv(uniforms.pointPositionRadius(), POINT_POSITIONS);
            glUniform4fv(uniforms.pointColor(), POINT_COLORS);
        }
        return new Upload(count, beamCount);
    }

    /**
     * Uploads Veil area lights and spot lights into the shared cone-light arrays. Both use the same light-space
     * convention (beam along local +Z); a spot light is a cone with a point (or small round lens) emitter.
     */
    static Upload uploadAreaLights(
            Collection<? extends LightRenderHandle<AreaLightData>> areaHandles,
            Collection<? extends LightRenderHandle<SpotLightData>> spotHandles,
            VilcLightUniforms uniforms,
            float exposure,
            int requestedLimit,
            float lightGain,
            boolean shadows,
            boolean beams) {
        int limit = Math.min(requestedLimit, MAX_AREA_LIGHTS);
        int count = 0;
        int beamCount = 0;
        for (int pass = beams ? 0 : 1; pass < 2; pass++) {
            for (LightRenderHandle<AreaLightData> handle : areaHandles) {
                if (count >= limit) {
                    break;
                }
                AreaLightData light = handle.isValid() ? handle.getLightData() : null;
                if (light == null) {
                    continue;
                }
                float inscattering = beams ? inscattering(light.getInscatteringStrength()) : 0.0F;
                if ((pass == 0) != (inscattering > 0.0F)) {
                    continue;
                }
                AreaLightShapeRegistry.Shape shape = AreaLightShapeRegistry.get(light);
                float angle = areaAngleRadians(light.getAngle());
                writeConeLight(
                        count++,
                        light.getPosition(),
                        light.getOrientation(),
                        light.getDistance(),
                        // 360 degrees: shines all around (no cone); flagged below the valid cosine range.
                        angle >= (float) (Math.PI * 2.0) - 0.01F ? OMNI_EDGE : (float) Math.cos(angle),
                        light.getSize().x,
                        light.getSize().y,
                        light.getColor(),
                        exposure * lightGain * light.getBrightness(),
                        packFlags(light, shadows, inscattering),
                        shape != null ? shape.softness() : 0.08F,
                        shape != null ? shape.focus() : 1.0F,
                        -1.0F,
                        chromaticFringe(light));
                if (pass == 0) {
                    beamCount++;
                }
            }
            for (LightRenderHandle<SpotLightData> handle : spotHandles) {
                if (count >= limit) {
                    break;
                }
                SpotLightData light = handle.isValid() ? handle.getLightData() : null;
                if (light == null) {
                    continue;
                }
                float inscattering = beams ? inscattering(light.getInscatteringStrength()) : 0.0F;
                if ((pass == 0) != (inscattering > 0.0F)) {
                    continue;
                }
                // Veil: size = cone half-angle, angle = width of the soft edge inside the cone (both radians).
                float cone = Math.max(0.0F, Math.min((float) Math.PI, light.getSize()));
                float innerCone = Math.max(0.0F, cone - Math.max(0.0F, light.getAngle()));
                float cosCone = (float) Math.cos(cone);
                float edgeSoftness = Math.max(0.005F, (float) Math.cos(innerCone) - cosCone);
                AreaLightShapeRegistry.Shape shape = AreaLightShapeRegistry.get(light);
                float lensRadius = shape != null ? shape.lensRadius() : 0.0F;
                writeConeLight(
                        count++,
                        light.getPosition(),
                        light.getOrientation(),
                        light.getDistance(),
                        cosCone,
                        lensRadius,
                        lensRadius,
                        light.getColor(),
                        exposure * lightGain * light.getBrightness(),
                        packFlags(light, shadows, inscattering),
                        edgeSoftness,
                        shape != null ? shape.focus() : 1.0F,
                        shape != null ? shape.lensHalfLength() : 0.0F,
                        chromaticFringe(light));
                if (pass == 0) {
                    beamCount++;
                }
            }
        }

        glUniform1i(uniforms.areaCount(), count);
        glUniform1i(uniforms.areaBeamCount(), beamCount);
        if (count > 0) {
            glUniform4fv(uniforms.areaPositionDistance(), AREA_POSITIONS);
            glUniform4fv(uniforms.areaDirectionAngle(), AREA_DIRECTIONS);
            glUniform4fv(uniforms.areaRightSize(), AREA_RIGHT_SIZE);
            glUniform4fv(uniforms.areaUpSize(), AREA_UP_SIZE);
            glUniform4fv(uniforms.areaColor(), AREA_COLORS);
            glUniform4fv(uniforms.areaShape(), AREA_SHAPES);
        }
        return new Upload(count, beamCount);
    }

    private static float inscattering(float strength) {
        if (!(strength > 0.0F)) {
            return 0.0F;
        }
        // Quantized to 1/256 when packed; anything that rounds to zero is treated as no beam.
        float clamped = Math.min(strength, MAX_INSCATTERING);
        return Math.round(clamped * 256.0F) > 0 ? clamped : 0.0F;
    }

    private static float packFlags(LightData light, boolean shadows, float inscattering) {
        AreaLightShapeRegistry.Extras extras = AreaLightShapeRegistry.getExtras(light);
        int flags = (shadows ? 1 : 0) | (extras != null && extras.skipFirstPerson() ? 2 : 0);
        return flags + 4.0F * Math.round(inscattering * 256.0F);
    }

    private static float chromaticFringe(LightData light) {
        AreaLightShapeRegistry.Extras extras = AreaLightShapeRegistry.getExtras(light);
        return extras != null ? extras.chromaticFringe() : 0.0F;
    }

    private static void writeConeLight(
            int slot,
            Vector3dc position,
            Quaternionfc lightOrientation,
            float distance,
            float cosEdge,
            float halfWidth,
            float halfHeight,
            Colorc color,
            float brightness,
            float packedFlags,
            float edgeSoftness,
            float focus,
            float roundLensHalfLength,
            float chromaticFringe) {
        Quaternionf orientation = new Quaternionf(lightOrientation).conjugate();
        Vector3f forward = orientation.transform(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
        Vector3f right = orientation.transform(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
        Vector3f up = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        int index = slot * 4;
        AREA_POSITIONS[index] = (float) position.x();
        AREA_POSITIONS[index + 1] = (float) position.y();
        AREA_POSITIONS[index + 2] = (float) position.z();
        AREA_POSITIONS[index + 3] = Math.max(0.01F, distance);
        AREA_DIRECTIONS[index] = forward.x();
        AREA_DIRECTIONS[index + 1] = forward.y();
        AREA_DIRECTIONS[index + 2] = forward.z();
        AREA_DIRECTIONS[index + 3] = cosEdge;
        AREA_RIGHT_SIZE[index] = right.x();
        AREA_RIGHT_SIZE[index + 1] = right.y();
        AREA_RIGHT_SIZE[index + 2] = right.z();
        AREA_RIGHT_SIZE[index + 3] = Math.max(0.001F, halfWidth);
        AREA_UP_SIZE[index] = up.x();
        AREA_UP_SIZE[index + 1] = up.y();
        AREA_UP_SIZE[index + 2] = up.z();
        AREA_UP_SIZE[index + 3] = Math.max(0.001F, halfHeight);
        AREA_COLORS[index] = color.red() * brightness;
        AREA_COLORS[index + 1] = color.green() * brightness;
        AREA_COLORS[index + 2] = color.blue() * brightness;
        AREA_COLORS[index + 3] = packedFlags;
        AREA_SHAPES[index] = edgeSoftness;
        AREA_SHAPES[index + 1] = focus;
        // 0 = rectangle; 1 + half length = round (spot) lens, stretched into a capsule along local x.
        AREA_SHAPES[index + 2] = roundLensHalfLength >= 0.0F ? 1.0F + roundLensHalfLength : 0.0F;
        AREA_SHAPES[index + 3] = chromaticFringe;
    }

    /** Cone edge value marking an area light that shines in every direction (cosines are never below -1). */
    private static final float OMNI_EDGE = -2.0F;

    private static float areaAngleRadians(float angle) {
        float absolute = Math.abs(angle);
        if (absolute > (float) (Math.PI * 2.0 + 0.001)) {
            return (float) Math.toRadians(angle);
        }
        return angle;
    }
}
