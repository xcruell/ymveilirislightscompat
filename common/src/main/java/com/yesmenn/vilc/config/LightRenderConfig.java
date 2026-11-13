package com.yesmenn.vilc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yesmenn.vilc.YMVilc;
import com.yesmenn.vilc.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LightRenderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final double LEGACY_DEFAULT_EXPOSURE = 0.24;
    private static final double LEGACY_DEFAULT_COLOR_STRENGTH = 2.45;
    private static final double LEGACY_DEFAULT_NEUTRAL_LIFT = 0.28;
    public static final double DEFAULT_EXPOSURE = 0.48;
    public static final double DEFAULT_COLOR_STRENGTH = 3.2;
    public static final double DEFAULT_COLOR_SATURATION = 1.15;
    public static final double DEFAULT_NEUTRAL_LIFT = 0.42;
    public static final double DEFAULT_LUMINANCE_BOOST_LIMIT = 1.6;
    public static final double DEFAULT_ADDITIVE_LIGHT = 0.8;
    public static final double MAX_EXPOSURE = 2.0;
    public static final double MAX_COLOR_STRENGTH = 12.0;
    public static final double MAX_LUMINANCE_BOOST_LIMIT = 1.6;
    public static final int MAX_POINT_LIGHTS = 96;
    public static final int MAX_AREA_LIGHTS = 96;
    public static final int MIN_VOLUMETRIC_QUALITY = 1;
    public static final int MAX_VOLUMETRIC_QUALITY = 3;
    public static final int DEFAULT_VOLUMETRIC_QUALITY = 2;

    private static LightRenderConfig instance = defaults();

    public RenderQuality quality = RenderQuality.HIGH;
    public int pointLightLimit = RenderQuality.HIGH.pointLightLimit();
    public int areaLightLimit = RenderQuality.HIGH.areaLightLimit();
    public boolean voxelShadows = RenderQuality.HIGH.voxelShadows();
    public double exposure = DEFAULT_EXPOSURE;
    public double colorStrength = DEFAULT_COLOR_STRENGTH;
    public double colorSaturation = DEFAULT_COLOR_SATURATION;
    public double neutralLift = DEFAULT_NEUTRAL_LIFT;
    public double luminanceBoostLimit = DEFAULT_LUMINANCE_BOOST_LIMIT;
    /** Share of light that is added (estimated albedo) instead of multiplying the finished image: 0 = old look. */
    public double additiveLight = DEFAULT_ADDITIVE_LIGHT;
    /** Light beams (in-scattering) on or off. */
    public boolean volumetricBeams = true;
    /** Light beam quality: 1 = low, 2 = medium, 3 = high. */
    public int volumetricQuality = DEFAULT_VOLUMETRIC_QUALITY;

    private LightRenderConfig() {
    }

    public static LightRenderConfig get() {
        return instance;
    }

    public static void load() {
        Path path = path();
        if (!Files.isRegularFile(path)) {
            instance = defaults();
            save(path);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            LightRenderConfig loaded = GSON.fromJson(reader, LightRenderConfig.class);
            instance = loaded != null ? loaded : defaults();
            instance.migrateLegacyDefaults();
            instance.clamp();
        } catch (Exception e) {
            YMVilc.LOGGER.warn("Failed to load {}, using defaults", path, e);
            instance = defaults();
        }
    }

    public static void save() {
        save(path());
    }

    /** {@code config/ymvilc-client.json}; the file of VILC up to 1.6.x (veil_iris_lights.json) is moved there once. */
    private static Path path() {
        Path path = Platform.getConfigDir().resolve("ymvilc-client.json");
        Path legacy = Platform.getConfigDir().resolve("veil_iris_lights.json");
        if (!Files.exists(path) && Files.isRegularFile(legacy)) {
            try {
                Files.move(legacy, path);
            } catch (IOException e) {
                YMVilc.LOGGER.warn("Could not move old config {} to {}", legacy, path, e);
                return legacy;
            }
        }
        return path;
    }

    private static void save(Path path) {
        instance.clamp();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            YMVilc.LOGGER.error("Failed to save {}", path, e);
        }
    }

    public void reset() {
        this.exposure = DEFAULT_EXPOSURE;
        this.colorStrength = DEFAULT_COLOR_STRENGTH;
        this.colorSaturation = DEFAULT_COLOR_SATURATION;
        this.neutralLift = DEFAULT_NEUTRAL_LIFT;
        this.luminanceBoostLimit = DEFAULT_LUMINANCE_BOOST_LIMIT;
        this.additiveLight = DEFAULT_ADDITIVE_LIGHT;
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
        if (Double.isNaN(this.additiveLight)) {
            this.additiveLight = DEFAULT_ADDITIVE_LIGHT;
        }
        this.additiveLight = clamp(this.additiveLight, 0.0, 1.0);
        this.luminanceBoostLimit = clamp(
                this.luminanceBoostLimit,
                1.0,
                MAX_LUMINANCE_BOOST_LIMIT);
        // Quality 0 used to mean "off" before the separate light beam toggle existed.
        if (this.volumetricQuality <= 0) {
            this.volumetricBeams = false;
            this.volumetricQuality = DEFAULT_VOLUMETRIC_QUALITY;
        }
        this.volumetricQuality = clamp(this.volumetricQuality, MIN_VOLUMETRIC_QUALITY, MAX_VOLUMETRIC_QUALITY);
    }

    /** Raymarch steps per pixel for light beams; 0 disables them. */
    public int volumetricSteps() {
        if (!this.volumetricBeams) {
            return 0;
        }
        return switch (this.volumetricQuality) {
            case 1 -> 8;
            case 2 -> 16;
            case 3 -> 32;
            default -> 0;
        };
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
        this.voxelShadows = preset.voxelShadows();
    }

    public void markCustom() {
        this.quality = RenderQuality.CUSTOM;
    }

    private static LightRenderConfig defaults() {
        return new LightRenderConfig();
    }

    public enum RenderQuality {
        PERFORMANCE("option.ymvilc.quality.performance", 24, 8, false),
        BALANCED("option.ymvilc.quality.balanced", 48, 16, true),
        HIGH("option.ymvilc.quality.high", 96, 32, true),
        CUSTOM("option.ymvilc.quality.custom", 0, 0, false);

        private final String translationKey;
        private final int pointLightLimit;
        private final int areaLightLimit;
        private final boolean voxelShadows;

        RenderQuality(String translationKey, int pointLightLimit, int areaLightLimit,
                      boolean voxelShadows) {
            this.translationKey = translationKey;
            this.pointLightLimit = pointLightLimit;
            this.areaLightLimit = areaLightLimit;
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
