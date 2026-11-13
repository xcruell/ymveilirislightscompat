package com.yesmenn.vilc.client.screen;

import com.yesmenn.ducttape.client.kit.YmContainer;
import com.yesmenn.ducttape.client.kit.YmScreen;
import com.yesmenn.ducttape.client.kit.element.YmText;
import com.yesmenn.ducttape.client.kit.settings.YmRows;
import com.yesmenn.ducttape.client.kit.settings.YmSettings;
import com.yesmenn.ducttape.client.kit.style.YmStyle;
import com.yesmenn.vilc.YMVilc;
import com.yesmenn.vilc.config.LightRenderConfig;
import com.yesmenn.vilc.config.LightRenderConfig.RenderQuality;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * VILC's settings as a section of the YM kit settings (YM Duct Tape): quality, light beams and the light pass. Shown
 * alone by VILC's config entries, or next to other mods' sections in a combined screen (e.g. YM Colored Lights).
 */
public final class VilcSettings {
    /** Section ID; other mods use it to include VILC's pages in their screen. */
    public static final String SECTION = YMVilc.MODID;

    private VilcSettings() {
    }

    public static void register() {
        YmSettings.register(new YmSettings.Section(SECTION, Component.literal("VILC"))
                .accent(YmStyle.ACCENT_LIGHTS)
                .page(tr("page.quality"), new ItemStack(Items.REDSTONE_TORCH), VilcSettings::qualityPage)
                .page(tr("page.beams"), new ItemStack(Items.END_ROD), VilcSettings::beamsPage)
                .page(tr("page.light_pass"), new ItemStack(Items.GLOWSTONE), VilcSettings::lightPassPage)
                .onSave(LightRenderConfig::save)
                .onReset(VilcSettings::resetAll));
    }

    private static void qualityPage(YmContainer page) {
        LightRenderConfig config = LightRenderConfig.get();
        page.add(new YmText(Component.translatable("ymducttape.kit.settings.live")).wrap().dim());
        YmRows.choice(page, tr("quality"), List.of(RenderQuality.values()), () -> config.quality, quality -> {
            if (quality == RenderQuality.CUSTOM) {
                config.markCustom();
            } else {
                config.applyPreset(quality);
            }
            // The preset changes the rows below.
            YmScreen screen = page.screen();
            if (screen != null) {
                screen.rebuild();
            }
        }, quality -> Component.translatable(quality.translationKey()), RenderQuality.HIGH);
        page.add(new YmText(tr("quality.help")).wrap().dim());
        YmRows.intSlider(page, tr("point_lights"), 1, LightRenderConfig.MAX_POINT_LIGHTS,
                () -> config.pointLightLimit, v -> {
                    config.pointLightLimit = v;
                    config.markCustom();
                }, RenderQuality.HIGH.pointLightLimit());
        YmRows.intSlider(page, tr("area_lights"), 1, LightRenderConfig.MAX_AREA_LIGHTS,
                () -> config.areaLightLimit, v -> {
                    config.areaLightLimit = v;
                    config.markCustom();
                }, RenderQuality.HIGH.areaLightLimit());
        YmRows.toggle(page, tr("voxel_shadows"), () -> config.voxelShadows, on -> {
            config.voxelShadows = on;
            config.markCustom();
        }, RenderQuality.HIGH.voxelShadows()).label().setTooltip(tr("voxel_shadows.tooltip"));
    }

    private static void beamsPage(YmContainer page) {
        LightRenderConfig config = LightRenderConfig.get();
        page.add(new YmText(tr("beams.help")).wrap().dim());
        YmRows.toggle(page, tr("beams"), () -> config.volumetricBeams, on -> config.volumetricBeams = on, true);
        List<Integer> qualities = List.of(1, 2, 3);
        YmRows.choice(page, tr("beam_quality"), qualities, () -> config.volumetricQuality,
                quality -> config.volumetricQuality = quality,
                quality -> Component.translatable("option.ymvilc.volumetric." + quality),
                LightRenderConfig.DEFAULT_VOLUMETRIC_QUALITY);
    }

    private static void lightPassPage(YmContainer page) {
        LightRenderConfig config = LightRenderConfig.get();
        YmRows.slider(page, tr("exposure"), 0.01, LightRenderConfig.MAX_EXPOSURE, 0.01,
                () -> config.exposure, v -> config.exposure = v, LightRenderConfig.DEFAULT_EXPOSURE, VilcSettings::two);
        YmRows.slider(page, tr("color_strength"), 0.0, LightRenderConfig.MAX_COLOR_STRENGTH, 0.05,
                () -> config.colorStrength, v -> config.colorStrength = v, LightRenderConfig.DEFAULT_COLOR_STRENGTH,
                VilcSettings::two);
        YmRows.slider(page, tr("color_saturation"), 0.5, 2.0, 0.01,
                () -> config.colorSaturation, v -> config.colorSaturation = v,
                LightRenderConfig.DEFAULT_COLOR_SATURATION, VilcSettings::two);
        YmRows.slider(page, tr("neutral_lift"), 0.0, 2.0, 0.01,
                () -> config.neutralLift, v -> config.neutralLift = v, LightRenderConfig.DEFAULT_NEUTRAL_LIFT,
                VilcSettings::two);
        YmRows.slider(page, tr("luminance_limit"), 1.0, LightRenderConfig.MAX_LUMINANCE_BOOST_LIMIT, 0.01,
                () -> config.luminanceBoostLimit, v -> config.luminanceBoostLimit = v,
                LightRenderConfig.DEFAULT_LUMINANCE_BOOST_LIMIT, VilcSettings::two);
        YmRows.slider(page, tr("additive_light"), 0.0, 1.0, 0.01,
                () -> config.additiveLight, v -> config.additiveLight = v, LightRenderConfig.DEFAULT_ADDITIVE_LIGHT,
                VilcSettings::two).label().setTooltip(tr("additive_light.tooltip"));
    }

    private static void resetAll() {
        LightRenderConfig config = LightRenderConfig.get();
        config.reset();
        config.applyPreset(RenderQuality.HIGH);
        config.volumetricBeams = true;
        config.volumetricQuality = LightRenderConfig.DEFAULT_VOLUMETRIC_QUALITY;
    }

    private static String two(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static MutableComponent tr(String key) {
        return Component.translatable("vilc.settings." + key);
    }
}
