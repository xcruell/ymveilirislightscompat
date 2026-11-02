package com.yesmenn.veilirislights.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yesmenn.veilirislights.VeilIrisLights;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LightRenderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("veil_iris_lights.json");

    private static final double LEGACY_DEFAULT_EXPOSURE = 0.24;
    private static final double LEGACY_DEFAULT_COLOR_STRENGTH = 2.45;
    private static final double LEGACY_DEFAULT_NEUTRAL_LIFT = 0.28;
    public static final double DEFAULT_EXPOSURE = 0.48;
    public static final double DEFAULT_COLOR_STRENGTH = 3.2;
    public static final double DEFAULT_COLOR_SATURATION = 1.15;
    public static final double DEFAULT_NEUTRAL_LIFT = 0.42;
    public static final double DEFAULT_LUMINANCE_BOOST_LIMIT = 1.6;
    public static final double MAX_EXPOSURE = 2.0;
    public static final double MAX_COLOR_STRENGTH = 12.0;
    public static final double MAX_LUMINANCE_BOOST_LIMIT = 1.6;
    public static final int MAX_POINT_LIGHTS = 96;
    public static final int MAX_AREA_LIGHTS = 96;

    private static LightRenderConfig instance = defaults();

    public RenderQuality quality = RenderQuality.HIGH;
    public int pointLightLimit = RenderQuality.HIGH.pointLightLimit();
    public int areaLightLimit = RenderQuality.HIGH.areaLightLimit();
    public boolean detailedNormals = RenderQuality.HIGH.detailedNormals();
    public boolean voxelShadows = RenderQuality.HIGH.voxelShadows();
    public double exposure = DEFAULT_EXPOSURE;
    public double colorStrength = DEFAULT_COLOR_STRENGTH;
    public double colorSaturation = DEFAULT_COLOR_SATURATION;
    public double neutralLift = DEFAULT_NEUTRAL_LIFT;
    public double luminanceBoostLimit = DEFAULT_LUMINANCE_BOOST_LIMIT;

    private LightRenderConfig() {
    }

    public static LightRenderConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.isRegularFile(PATH)) {
            instance = defaults();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            LightRenderConfig loaded = GSON.fromJson(reader, LightRenderConfig.class);
            instance = loaded != null ? loaded : defaults();
            instance.migrateLegacyDefaults();
            instance.clamp();
        } catch (Exception e) {
            VeilIrisLights.LOGGER.warn("Failed to load {}, using defaults", PATH, e);
            instance = defaults();
        }
    }

    public static void save() {
        instance.clamp();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            VeilIrisLights.LOGGER.error("Failed to save {}", PATH, e);
        }
    }

    public void reset() {
        this.exposure = DEFAULT_EXPOSURE;
        this.colorStrength = DEFAULT_COLOR_STRENGTH;
        this.colorSaturation = DEFAULT_COLOR_SATURATION;
        this.neutralLift = DEFAULT_NEUTRAL_LIFT;
        this.luminanceBoostLimit = DEFAULT_LUMINANCE_BOOST_LIMIT;
    }

    private void migrateLegacyDefaults() {
        if (this.exposure == LEGACY_DEFAULT_EXPOSURE
                && this.colorStrength == LEGACY_DEFAULT_COLOR_STRENGTH
                && this.neutralLift == LEGACY_DEFAULT_NEUTRAL_LIFT) {
            this.exposure = DEFAULT_EXPOSURE;
            this.colorStrength = DEFAULT_COLOR_STRENGTH;
            this.neutralLift = DEFAULT_NEUTRAL_LIFT;
        }
    }

    private void clamp() {
        if (this.quality == null) {
            this.quality = RenderQuality.HIGH;
        }
        if (this.pointLightLimit <= 0 || this.areaLightLimit <= 0) {
            RenderQuality migrationPreset = this.quality == RenderQuality.CUSTOM
                    ? RenderQuality.HIGH
                    : this.quality;
            applyPreset(migrationPreset);
        }
        this.pointLightLimit = clamp(this.pointLightLimit, 1, MAX_POINT_LIGHTS);
        this.areaLightLimit = clamp(this.areaLightLimit, 1, MAX_AREA_LIGHTS);
        this.exposure = clamp(this.exposure, 0.01, MAX_EXPOSURE);
        this.colorStrength = clamp(this.colorStrength, 0.0, MAX_COLOR_STRENGTH);
        if (this.colorSaturation <= 0.0) {
            this.colorSaturation = DEFAULT_COLOR_SATURATION;
        }
        this.colorSaturation = clamp(this.colorSaturation, 0.5, 2.0);
        this.neutralLift = clamp(this.neutralLift, 0.0, 2.0);
        this.luminanceBoostLimit = clamp(
                this.luminanceBoostLimit,
                1.0,
                MAX_LUMINANCE_BOOST_LIMIT);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public void applyPreset(RenderQuality preset) {
        if (preset == RenderQuality.CUSTOM) {
            return;
        }
        this.quality = preset;
        this.pointLightLimit = preset.pointLightLimit();
        this.areaLightLimit = preset.areaLightLimit();
        this.detailedNormals = preset.detailedNormals();
        this.voxelShadows = preset.voxelShadows();
    }

    public void markCustom() {
        this.quality = RenderQuality.CUSTOM;
    }

    private static LightRenderConfig defaults() {
        return new LightRenderConfig();
    }

    public enum RenderQuality {
        PERFORMANCE("option.veil_iris_lights.quality.performance", 24, 8, false, false),
        BALANCED("option.veil_iris_lights.quality.balanced", 48, 16, false, true),
        HIGH("option.veil_iris_lights.quality.high", 96, 32, true, true),
        CUSTOM("option.veil_iris_lights.quality.custom", 0, 0, false, false);

        private final String translationKey;
        private final int pointLightLimit;
        private final int areaLightLimit;
        private final boolean detailedNormals;
        private final boolean voxelShadows;

        RenderQuality(String translationKey, int pointLightLimit, int areaLightLimit,
                      boolean detailedNormals, boolean voxelShadows) {
            this.translationKey = translationKey;
            this.pointLightLimit = pointLightLimit;
            this.areaLightLimit = areaLightLimit;
            this.detailedNormals = detailedNormals;
            this.voxelShadows = voxelShadows;
        }

        public String translationKey() {
            return this.translationKey;
        }

        public int pointLightLimit() {
            return this.pointLightLimit;
        }

        public int areaLightLimit() {
            return this.areaLightLimit;
        }

        public boolean detailedNormals() {
            return this.detailedNormals;
        }

        public boolean voxelShadows() {
            return this.voxelShadows;
        }

        public RenderQuality next() {
            return switch (this) {
                case PERFORMANCE -> BALANCED;
                case BALANCED -> HIGH;
                case HIGH, CUSTOM -> PERFORMANCE;
            };
        }
    }
}
