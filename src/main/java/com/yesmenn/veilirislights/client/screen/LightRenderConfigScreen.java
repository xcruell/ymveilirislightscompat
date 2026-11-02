package com.yesmenn.veilirislights.client.screen;

import com.yesmenn.veilirislights.VeilIrisLights;
import com.yesmenn.veilirislights.config.LightRenderConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class LightRenderConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 228;
    private static final int COLUMN_WIDTH = 205;
    private static final int SLIDER_TOP_OFFSET = 32;
    private static final float FOOTER_SCALE = 0.75F;
    private static final Component FOOTER = Component.literal(
            "Made by xcruell - v" + ModList.get()
                    .getModContainerById(VeilIrisLights.MODID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown"));

    private final Screen parent;

    public LightRenderConfigScreen(Screen parent) {
        super(Component.translatable("screen.veil_iris_lights.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int y = top + SLIDER_TOP_OFFSET;
        LightRenderConfig config = LightRenderConfig.get();

        this.addRenderableWidget(Button.builder(
                qualityLabel(config),
                button -> {
                    config.applyPreset(config.quality.next());
                    this.rebuildWidgets();
                }).bounds(left, y, PANEL_WIDTH, 20).build());

        int controlsY = y + 26;
        this.addRenderableWidget(new ConfigSlider(
                left, controlsY, COLUMN_WIDTH, "option.veil_iris_lights.point_lights",
                1, LightRenderConfig.MAX_POINT_LIGHTS,
                () -> config.pointLightLimit,
                value -> {
                    config.pointLightLimit = (int) Math.round(value);
                    config.markCustom();
                },
                true));
        this.addRenderableWidget(new ConfigSlider(
                left, controlsY + 26, COLUMN_WIDTH, "option.veil_iris_lights.area_lights",
                1, LightRenderConfig.MAX_AREA_LIGHTS,
                () -> config.areaLightLimit,
                value -> {
                    config.areaLightLimit = (int) Math.round(value);
                    config.markCustom();
                },
                true));
        this.addRenderableWidget(Button.builder(
                toggleLabel("option.veil_iris_lights.detailed_normals", config.detailedNormals),
                button -> {
                    config.detailedNormals = !config.detailedNormals;
                    config.markCustom();
                    this.rebuildWidgets();
                }).bounds(left, controlsY + 52, COLUMN_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(
                toggleLabel("option.veil_iris_lights.voxel_shadows", config.voxelShadows),
                button -> {
                    config.voxelShadows = !config.voxelShadows;
                    config.markCustom();
                    this.rebuildWidgets();
                }).bounds(left, controlsY + 78, COLUMN_WIDTH, 20).build());

        int right = left + COLUMN_WIDTH + 10;
        this.addRenderableWidget(new ConfigSlider(
                right, controlsY, COLUMN_WIDTH, "option.veil_iris_lights.exposure",
                0.01, LightRenderConfig.MAX_EXPOSURE,
                () -> config.exposure, value -> config.exposure = value));
        this.addRenderableWidget(new ConfigSlider(
                right, controlsY + 26, COLUMN_WIDTH, "option.veil_iris_lights.color_strength",
                0.0, LightRenderConfig.MAX_COLOR_STRENGTH,
                () -> config.colorStrength, value -> config.colorStrength = value));
        this.addRenderableWidget(new ConfigSlider(
                right, controlsY + 52, COLUMN_WIDTH, "option.veil_iris_lights.color_saturation",
                0.5, 2.0, () -> config.colorSaturation, value -> config.colorSaturation = value));
        this.addRenderableWidget(new ConfigSlider(
                right, controlsY + 78, COLUMN_WIDTH, "option.veil_iris_lights.neutral_lift",
                0.0, 2.0, () -> config.neutralLift, value -> config.neutralLift = value));
        this.addRenderableWidget(new ConfigSlider(
                right, controlsY + 104, COLUMN_WIDTH, "option.veil_iris_lights.luminance_limit",
                1.0, LightRenderConfig.MAX_LUMINANCE_BOOST_LIMIT,
                () -> config.luminanceBoostLimit, value -> config.luminanceBoostLimit = value));

        this.addRenderableWidget(Button.builder(
                Component.translatable("controls.reset"),
                button -> {
                    config.reset();
                    this.rebuildWidgets();
                }).bounds(left, y + 166, COLUMN_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"), button -> this.onClose())
                .bounds(right, y + 166, COLUMN_WIDTH, 20).build());
    }

    private static Component qualityLabel(LightRenderConfig config) {
        return Component.translatable(
                "option.veil_iris_lights.quality",
                Component.translatable(config.quality.translationKey()));
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.translatable(
                key,
                Component.translatable(enabled ? "options.on" : "options.off"));
    }

    @Override
    public void onClose() {
        LightRenderConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = (this.height - PANEL_HEIGHT) / 2;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.veil_iris_lights.live_hint"),
                this.width / 2,
                top + 14,
                0xA0A0A0);

        graphics.pose().pushPose();
        graphics.pose().scale(FOOTER_SCALE, FOOTER_SCALE, 1.0F);
        graphics.drawString(
                this.font,
                FOOTER,
                Math.round(6 / FOOTER_SCALE),
                Math.round((this.height - 8) / FOOTER_SCALE),
                0x70FFFFFF,
                false);
        graphics.pose().popPose();
    }

    private static final class ConfigSlider extends AbstractSliderButton {

        private final String translationKey;
        private final double min;
        private final double max;
        private final DoubleConsumer setter;
        private final boolean integer;

        private ConfigSlider(int x, int y, int width, String translationKey,
                             double min, double max, DoubleSupplier getter, DoubleConsumer setter) {
            this(x, y, width, translationKey, min, max, getter, setter, false);
        }

        private ConfigSlider(int x, int y, int width, String translationKey,
                             double min, double max, DoubleSupplier getter, DoubleConsumer setter,
                             boolean integer) {
            super(x, y, width, 20, Component.empty(), normalize(getter.getAsDouble(), min, max));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.integer = integer;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            String value = this.integer
                    ? Integer.toString((int) Math.round(this.currentValue()))
                    : String.format("%.2f", this.currentValue());
            this.setMessage(Component.translatable(this.translationKey, value));
        }

        @Override
        protected void applyValue() {
            this.setter.accept(this.currentValue());
        }

        private double currentValue() {
            return this.min + this.value * (this.max - this.min);
        }

        private static double normalize(double value, double min, double max) {
            return (value - min) / (max - min);
        }
    }
}
