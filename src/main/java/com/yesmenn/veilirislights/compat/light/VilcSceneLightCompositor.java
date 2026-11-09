package com.yesmenn.veilirislights.compat.light;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import net.minecraft.client.Minecraft;
import net.irisshaders.iris.targets.DepthTexture;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import com.yesmenn.veilirislights.VeilIrisLights;
import com.yesmenn.veilirislights.compat.veil.VoxelShadowGridState;
import com.yesmenn.veilirislights.config.LightRenderConfig;

import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.List;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;

public final class VilcSceneLightCompositor {
    private static final float SHADERPACK_LIGHT_GAIN = 1.5F;

    private static int program;
    private static int compositeProgram;
    private static int framebuffer;
    private static int compositeFramebuffer;
    private static int vertexArray;
    private static int lightTexture;
    private static int sceneCopyTexture;
    private static int beamTexture;
    private static int lightTextureWidth;
    private static int lightTextureHeight;
    /** Light beams are raymarched at half resolution and upsampled bilinearly in the composite. */
    private static int beamTextureWidth;
    private static int beamTextureHeight;
    private static int attachedColorTexture;
    private static boolean glErrorLogged;
    private static int dummyBlockGridTexture;
    private static VilcLightUniforms uniforms;
    private static int compositeLightTextureUniform;
    private static int compositeSceneTextureUniform;
    private static int compositeUseSceneTextureUniform;
    private static int compositeBeamTextureUniform;
    private static boolean initializationFailed;
    private static boolean hookLogged;
    private static boolean targetsLogged;
    private static int lastPointCount = -1;
    private static int lastAreaCount = -1;
    private static int lastUploadedPointCount = -1;
    private static int lastUploadedAreaCount = -1;
    private static String lastRenderTargetName = "";
    private static boolean lastOcclusionEnabled;

    private VilcSceneLightCompositor() {
    }

    public static void renderShaderpackLights(RenderTargets renderTargets, ImmutableSet<Integer> flippedAfterTranslucent) {
        RenderSystem.assertOnRenderThread();

        Collection<? extends LightRenderHandle<PointLightData>> pointHandles =
                VeilRenderSystem.renderer().getLightRenderer().getLights(LightTypeRegistry.POINT.get());
        Collection<? extends LightRenderHandle<AreaLightData>> areaHandles =
                VeilRenderSystem.renderer().getLightRenderer().getLights(LightTypeRegistry.AREA.get());
        RenderTarget colorTarget = renderTargets.getOrCreate(0);
        int colorTexture = flippedAfterTranslucent.contains(0)
                ? colorTarget.getAltTexture()
                : colorTarget.getMainTexture();
        Matrix4f inverseViewProjection = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection())
                .mul(CapturedRenderingState.INSTANCE.getGbufferModelView())
                .invert();
        Vector3d cameraPosition = CameraUniforms.getUnshiftedCameraPosition();
        // Iris copies the depth buffer right before the hand renders; comparing both depths masks hand and held item.
        DepthTexture handFreeDepth = renderTargets.getDepthTextureNoHand();

        renderToTarget(
                pointHandles,
                areaHandles,
                colorTexture,
                renderTargets.getDepthTexture(),
                handFreeDepth != null ? handFreeDepth.getTextureId() : 0,
                renderTargets.getCurrentWidth(),
                renderTargets.getCurrentHeight(),
                1.0F,
                1.0F,
                inverseViewProjection,
                cameraPosition,
                SHADERPACK_LIGHT_GAIN,
                flippedAfterTranslucent.contains(0) ? "shaderpack-flipped" : "shaderpack");
    }

    public static void renderVanillaLights(
            Matrix4f modelView,
            Matrix4f projection,
            net.minecraft.client.Camera camera) {
        RenderSystem.assertOnRenderThread();

        Minecraft minecraft = Minecraft.getInstance();
        com.mojang.blaze3d.pipeline.RenderTarget target = minecraft.getMainRenderTarget();
        int colorTexture = target.getColorTextureId();
        int depthTexture = target.getDepthTextureId();
        if (target.width <= 0 || target.height <= 0 || colorTexture == 0 || depthTexture == 0) {
            return;
        }

        Matrix4f inverseViewProjection = new Matrix4f(projection)
                .mul(modelView)
                .invert();
        Vector3d cameraPosition = new Vector3d(
                camera.getPosition().x,
                camera.getPosition().y,
                camera.getPosition().z);

        target.bindWrite(true);
        renderToTarget(
                VeilRenderSystem.renderer().getLightRenderer().getLights(LightTypeRegistry.POINT.get()),
                VeilRenderSystem.renderer().getLightRenderer().getLights(LightTypeRegistry.AREA.get()),
                colorTexture,
                depthTexture,
                0,
                target.viewWidth,
                target.viewHeight,
                target.width > 0 ? (float) target.viewWidth / (float) target.width : 1.0F,
                target.height > 0 ? (float) target.viewHeight / (float) target.height : 1.0F,
                inverseViewProjection,
                cameraPosition,
                1.0F,
                "vanilla");
    }

    private static void renderToTarget(
            Collection<? extends LightRenderHandle<PointLightData>> pointHandles,
            Collection<? extends LightRenderHandle<AreaLightData>> areaHandles,
            int colorTexture,
            int depthTexture,
            int handFreeDepthTexture,
            int width,
            int height,
            float depthUvScaleX,
            float depthUvScaleY,
            Matrix4f inverseViewProjection,
            Vector3d cameraPosition,
            float lightGain,
            String targetName) {
        if (!hookLogged) {
            hookLogged = true;
            VeilIrisLights.LOGGER.debug("Veil Iris light pass is active");
        }
        Collection<? extends LightRenderHandle<SpotLightData>> spotHandles =
                VeilRenderSystem.renderer().getLightRenderer().getLights(LightTypeRegistry.SPOT.get());
        int coneLightCount = areaHandles.size() + spotHandles.size();
        if (pointHandles.size() != lastPointCount || coneLightCount != lastAreaCount) {
            lastPointCount = pointHandles.size();
            lastAreaCount = coneLightCount;
            VeilIrisLights.LOGGER.debug(
                    "Veil Iris light pass sees {} point light(s) and {} area/spot light(s)",
                    lastPointCount,
                    lastAreaCount);
        }
        if (pointHandles.isEmpty() && coneLightCount == 0) {
            return;
        }

        LightRenderConfig config = LightRenderConfig.get();
        VilcRenderTuning tuning = VilcRenderTuning.from(config);
        // Both passes composite from a scene copy in the shader. Blending into the RGBA8 main target clamps the
        // light term to [0, 1], which capped colored light at 2x the scene color and made saturated lights look weak.
        boolean vanillaTuning = "vanilla".equals(targetName);
        // The VILC option is the master switch: when enabled, every Veil light casts voxel shadows, regardless of
        // the per-light occlusion flag (light mods often leave it off, which made the option appear to do nothing).
        boolean shadowsEnabled = config.voxelShadows;
        lastOcclusionEnabled = shadowsEnabled;
        if (shadowsEnabled) {
            setupVoxelGridWithCleanUnpackState();
        }
        boolean hasUsableVoxelGrid = shadowsEnabled && VoxelShadowGridState.isReady();

        ensureInitialized();
        if (initializationFailed || program == 0) {
            return;
        }
        if (!targetsLogged) {
            targetsLogged = true;
            VeilIrisLights.LOGGER.debug(
                    "Veil Iris target {}: color={}, depth={}, size={}x{}",
                    targetName,
                    colorTexture,
                    depthTexture,
                    width,
                    height);
        }

        VilcGlStateSnapshot glState = VilcGlStateSnapshot.capture();

        int renderX = 0;
        int renderY = 0;
        int renderWidth = width;
        int renderHeight = height;

        if (!ensureLightTextures(renderWidth, renderHeight)) {
            glState.restore();
            return;
        }

        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(lightTexture, glState.activeTexture());
        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(sceneCopyTexture, glState.activeTexture());
        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(beamTexture, glState.activeTexture());

        if (!copyCurrentColorTarget(glState, renderWidth, renderHeight)) {
            glState.restore();
            return;
        }

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, lightTexture, 0);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, 0, 0);
        glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_NONE});
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
            glState.restore();
            return;
        }
        glViewport(0, 0, renderWidth, renderHeight);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(program);
        clearGlError("before light draw");
        VilcGlStateSnapshot.clearTextureUnits();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_3D, 0);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        int depthTextureWidth = Math.max(width, renderWidth);
        int depthTextureHeight = Math.max(height, renderHeight);
        glUniform1i(uniforms.depth(), 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindTexture(GL_TEXTURE_3D, hasUsableVoxelGrid ? VoxelShadowGrid.getTextureId() : dummyBlockGridTexture);
        glUniform1i(uniforms.blockGrid(), 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_3D, 0);
        glBindTexture(GL_TEXTURE_2D, handFreeDepthTexture != 0 ? handFreeDepthTexture : depthTexture);
        glUniform1i(uniforms.handFreeDepth(), 2);
        glUniform1i(uniforms.hasHandFreeDepth(), handFreeDepthTexture != 0 ? 1 : 0);
        Vector3f gridOrigin = new Vector3f(VoxelShadowGrid.getUniformGridPos());
        glUniform3f(uniforms.gridOrigin(),
                gridOrigin.x, gridOrigin.y, gridOrigin.z);
        glUniform1i(uniforms.hasBlockGrid(), hasUsableVoxelGrid ? 1 : 0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            inverseViewProjection.get(matrix);
            glUniformMatrix4fv(uniforms.inverseViewProjection(), false, matrix);
        }
        glUniform3f(uniforms.cameraPosition(),
                (float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z);
        glUniform2f(uniforms.inverseViewSize(),
                1.0F / renderWidth,
                1.0F / renderHeight);
        glUniform2f(uniforms.inverseDepthSize(),
                1.0F / renderWidth,
                1.0F / renderHeight);
        glUniform2f(uniforms.depthUvScale(),
                depthUvScaleX,
                depthUvScaleY);
        float colorStrength = vanillaTuning ? tuning.colorStrength() : tuning.colorStrength() * 1.35F;
        float colorSaturation = vanillaTuning ? tuning.colorSaturation() : Math.max(tuning.colorSaturation(), 1.35F);
        float neutralLift = vanillaTuning ? tuning.neutralLift() : 0.0F;
        glUniform1f(uniforms.colorStrength(), colorStrength);
        glUniform1f(uniforms.colorSaturation(), colorSaturation);
        glUniform1f(uniforms.neutralLift(), neutralLift);
        glUniform1f(uniforms.luminanceBoostLimit(), tuning.luminanceBoostLimit());
        int beamSteps = config.volumetricSteps();
        boolean beams = beamSteps > 0;

        VilcLightBatch.Upload pointUpload = VilcLightBatch.uploadPointLights(
                pointHandles,
                uniforms,
                tuning.exposure(),
                config.pointLightLimit,
                lightGain,
                hasUsableVoxelGrid,
                beams);
        VilcLightBatch.Upload areaUpload = VilcLightBatch.uploadAreaLights(
                areaHandles,
                spotHandles,
                uniforms,
                tuning.exposure(),
                config.areaLightLimit,
                lightGain,
                hasUsableVoxelGrid,
                beams);
        // Skip the raymarch entirely when no uploaded light has in-scattering.
        glUniform1i(uniforms.beamSteps(),
                pointUpload.beamCount() + areaUpload.beamCount() > 0 ? beamSteps : 0);
        boolean beamPassActive = pointUpload.beamCount() + areaUpload.beamCount() > 0;
        glUniform1i(uniforms.beamShadows(),
                hasUsableVoxelGrid && config.volumetricQuality >= LightRenderConfig.MAX_VOLUMETRIC_QUALITY ? 1 : 0);
        glUniform1i(uniforms.beamPass(), 0);
        logPassState(
                targetName,
                pointUpload.count(),
                areaUpload.count(),
                renderWidth,
                renderHeight,
                colorTexture,
                depthTexture);

        glBindVertexArray(vertexArray);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        logGlError("light draw " + targetName);

        // Beam pass: same program and uniforms, half resolution, only the beam output (location 1) is written.
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, beamTexture, 0);
        glDrawBuffers(new int[]{GL_NONE, GL_COLOR_ATTACHMENT1});
        glViewport(0, 0, beamTextureWidth, beamTextureHeight);
        glClear(GL_COLOR_BUFFER_BIT);
        if (beamPassActive && glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            glUniform1i(uniforms.beamPass(), 1);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            logGlError("beam draw " + targetName);
        }
        glViewport(0, 0, renderWidth, renderHeight);

        int compositeTexture = lightTexture;
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, 0, 0);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, 0);
        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(compositeTexture, glState.activeTexture());
        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(sceneCopyTexture, glState.activeTexture());
        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(beamTexture, glState.activeTexture());

        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, glState.drawFramebuffer());
        glDrawBuffer(glState.drawFramebuffer() == 0 ? GL_BACK : GL_COLOR_ATTACHMENT0);
        glViewport(renderX, renderY, renderWidth, renderHeight);
        glDisable(GL_BLEND);
        glUseProgram(compositeProgram);
        clearGlError("before composite draw");
        VilcGlStateSnapshot.clearTextureUnits();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_3D, 0);
        glBindTexture(GL_TEXTURE_2D, compositeTexture);
        glUniform1i(compositeLightTextureUniform, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_3D, 0);
        glBindTexture(GL_TEXTURE_2D, sceneCopyTexture);
        glUniform1i(compositeSceneTextureUniform, 1);
        glUniform1i(compositeUseSceneTextureUniform, 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_3D, 0);
        glBindTexture(GL_TEXTURE_2D, beamTexture);
        glUniform1i(compositeBeamTextureUniform, 2);
        glBindVertexArray(vertexArray);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        logGlError("composite draw " + targetName);
        glUseProgram(0);
        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindTexture(GL_TEXTURE_3D, 0);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, 0);

        glState.restore();
    }
    public static void releaseGpuResources() {
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
        if (compositeProgram != 0) {
            glDeleteProgram(compositeProgram);
            compositeProgram = 0;
        }
        if (framebuffer != 0) {
            glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (compositeFramebuffer != 0) {
            glDeleteFramebuffers(compositeFramebuffer);
            compositeFramebuffer = 0;
        }
        if (vertexArray != 0) {
            glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (lightTexture != 0) {
            glDeleteTextures(lightTexture);
            lightTexture = 0;
        }
        if (sceneCopyTexture != 0) {
            glDeleteTextures(sceneCopyTexture);
            sceneCopyTexture = 0;
        }
        if (beamTexture != 0) {
            glDeleteTextures(beamTexture);
            beamTexture = 0;
        }
        if (dummyBlockGridTexture != 0) {
            glDeleteTextures(dummyBlockGridTexture);
            dummyBlockGridTexture = 0;
        }
        lightTextureWidth = 0;
        lightTextureHeight = 0;
        beamTextureWidth = 0;
        beamTextureHeight = 0;
        attachedColorTexture = 0;
        glErrorLogged = false;
        uniforms = null;
        compositeLightTextureUniform = -1;
        compositeSceneTextureUniform = -1;
        compositeUseSceneTextureUniform = -1;
        compositeBeamTextureUniform = -1;
        initializationFailed = false;
        hookLogged = false;
        targetsLogged = false;
        lastPointCount = -1;
        lastAreaCount = -1;
        lastUploadedPointCount = -1;
        lastUploadedAreaCount = -1;
        lastRenderTargetName = "";
        lastOcclusionEnabled = false;
    }

    public static void appendDiagnostics(List<String> lines) {
        LightRenderConfig config = LightRenderConfig.get();
        if (lines.stream().anyMatch(line -> line.startsWith("[Veil Iris Lights]"))) {
            return;
        }
        lines.add("");
        lines.add("[Veil Iris Lights] " + (hookLogged ? "active" : "waiting"));
        lines.add("Point lights: " + Math.max(lastPointCount, 0) + "/" + config.pointLightLimit
                + ", area lights: " + Math.max(lastAreaCount, 0) + "/" + config.areaLightLimit);
        lines.add("Quality: " + config.quality.name().toLowerCase()
                + ", voxel occlusion: " + (lastOcclusionEnabled ? "active" : "inactive"));
        lines.add(String.format(
                "Exposure: %.2f, color: %.2f, saturation: %.2f",
                config.exposure,
                config.colorStrength,
                config.colorSaturation));
        lines.add(String.format(
                "Neutral: %.2f, luminance: %.2f",
                config.neutralLift,
                config.luminanceBoostLimit));
    }

    private static void ensureInitialized() {
        if (program != 0 || initializationFailed) {
            return;
        }

        program = linkProgram("light", FRAGMENT_SHADER);
        compositeProgram = linkProgram("composite", COMPOSITE_FRAGMENT_SHADER);
        if (program == 0 || compositeProgram == 0) {
            initializationFailed = true;
            releaseGpuResources();
            return;
        }

        framebuffer = glGenFramebuffers();
        compositeFramebuffer = glGenFramebuffers();
        vertexArray = glGenVertexArrays();
        ensureDummyBlockGridTexture();
        uniforms = VilcLightUniforms.fromProgram(program);
        compositeLightTextureUniform = glGetUniformLocation(compositeProgram, "uLightTexture");
        compositeSceneTextureUniform = glGetUniformLocation(compositeProgram, "uSceneTexture");
        compositeUseSceneTextureUniform = glGetUniformLocation(compositeProgram, "uUseSceneTexture");
        compositeBeamTextureUniform = glGetUniformLocation(compositeProgram, "uBeamTexture");
    }

    private static int linkProgram(String name, String fragmentSource) {
        int vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
        int linkedProgram = glCreateProgram();
        glAttachShader(linkedProgram, vertexShader);
        glAttachShader(linkedProgram, fragmentShader);
        // The light pass writes lighting and light beams into two attachments; unused names are ignored.
        glBindFragDataLocation(linkedProgram, 0, "fragColor");
        glBindFragDataLocation(linkedProgram, 1, "beamColor");
        glLinkProgram(linkedProgram);
        if (glGetProgrami(linkedProgram, GL_LINK_STATUS) == GL_FALSE) {
            VeilIrisLights.LOGGER.error("Failed to link Veil Iris {} shader: {}", name, glGetProgramInfoLog(linkedProgram));
            glDeleteProgram(linkedProgram);
            linkedProgram = 0;
        }
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return linkedProgram;
    }

    /**
     * Veil creates the voxel grid texture on first use with a client-memory glTexImage3D and does not reset the pixel
     * unpack state. Inside Iris' pipeline a pixel unpack buffer or row/image skip values can still be set, so the
     * driver read far outside the 64^3 byte upload and crashed (access violation in the driver when voxel shadows were
     * switched on while a shaderpack was active). The unpack state is reset around the call and restored afterwards.
     */
    private static void setupVoxelGridWithCleanUnpackState() {
        int unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        int rowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        int skipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
        int skipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        int imageHeight = glGetInteger(GL_UNPACK_IMAGE_HEIGHT);
        int skipImages = glGetInteger(GL_UNPACK_SKIP_IMAGES);
        int texture3d = glGetInteger(GL_TEXTURE_BINDING_3D);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);
        try {
            VoxelShadowGrid.setup();
        } finally {
            glBindTexture(GL_TEXTURE_3D, texture3d);
            glPixelStorei(GL_UNPACK_SKIP_IMAGES, skipImages);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, imageHeight);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
    }

    private static boolean ensureLightTextures(int width, int height) {
        if (lightTexture == 0) {
            lightTexture = glGenTextures();
        }
        if (sceneCopyTexture == 0) {
            sceneCopyTexture = glGenTextures();
        }
        if (beamTexture == 0) {
            beamTexture = glGenTextures();
        }
        int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        boolean resize = lightTextureWidth != width || lightTextureHeight != height;
        setupLightTexture(lightTexture, width, height, resize);
        setupLightTexture(sceneCopyTexture, width, height, resize);
        int beamWidth = Math.max(1, (width + 1) / 2);
        int beamHeight = Math.max(1, (height + 1) / 2);
        boolean beamResize = beamTextureWidth != beamWidth || beamTextureHeight != beamHeight;
        setupLightTexture(beamTexture, beamWidth, beamHeight, beamResize);
        // Beams are smooth fog: bilinear upsampling hides the half resolution.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        lightTextureWidth = width;
        lightTextureHeight = height;
        beamTextureWidth = beamWidth;
        beamTextureHeight = beamHeight;
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(oldActiveTexture);
        return lightTexture != 0 && sceneCopyTexture != 0 && beamTexture != 0;
    }

    private static void setupLightTexture(int texture, int width, int height, boolean resize) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        if (resize) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, 0L);
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private static boolean copyCurrentColorTarget(VilcGlStateSnapshot glState, int width, int height) {
        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(sceneCopyTexture, glState.activeTexture());
        glBindFramebuffer(GL_READ_FRAMEBUFFER, glState.drawFramebuffer());
        glReadBuffer(glState.drawFramebuffer() == 0 ? GL_BACK : GL_COLOR_ATTACHMENT0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, compositeFramebuffer);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneCopyTexture, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
            logGlError("scene copy framebuffer");
            return false;
        }
        glDisable(GL_SCISSOR_TEST);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
        logGlError("scene copy");
        return true;
    }

    private static void clearGlError(String marker) {
        while (glGetError() != GL_NO_ERROR) {
        }
    }

    private static void logGlError(String marker) {
        int error = glGetError();
        if (error != GL_NO_ERROR && !glErrorLogged) {
            glErrorLogged = true;
            VeilIrisLights.LOGGER.error("Veil Iris light pass GL error after {}: 0x{}", marker, Integer.toHexString(error));
            while (glGetError() != GL_NO_ERROR) {
            }
        }
    }

    private static void ensureDummyBlockGridTexture() {
        if (dummyBlockGridTexture != 0) {
            return;
        }
        int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE1);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_3D);
        dummyBlockGridTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, dummyBlockGridTexture);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, 1, 1, 1, 0, GL_RED, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glBindTexture(GL_TEXTURE_3D, oldTexture);
        glActiveTexture(oldActiveTexture);
    }
    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            VeilIrisLights.LOGGER.error("Failed to compile Veil Iris light shader: {}", glGetShaderInfoLog(shader));
            glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static void logPassState(
            String targetName,
            int uploadedPointCount,
            int uploadedAreaCount,
            int width,
            int height,
            int colorTexture,
            int depthTexture) {
        if (uploadedPointCount == lastUploadedPointCount
                && uploadedAreaCount == lastUploadedAreaCount
                && targetName.equals(lastRenderTargetName)) {
            return;
        }
        lastUploadedPointCount = uploadedPointCount;
        lastUploadedAreaCount = uploadedAreaCount;
        lastRenderTargetName = targetName;
        VeilIrisLights.LOGGER.info(
                "VILC {} pass uploaded {} point light(s), {} area light(s), target={}x{}, colorTex={}, depthTex={}",
                targetName,
                uploadedPointCount,
                uploadedAreaCount,
                width,
                height,
                colorTexture,
                depthTexture);
    }

    private static final String VERTEX_SHADER = """
            #version 150
            out vec2 vTexCoord;

            void main() {
                vec2 position = vec2(
                    (gl_VertexID == 1) ? 3.0 : -1.0,
                    (gl_VertexID == 2) ? 3.0 : -1.0
                );
                vTexCoord = position * 0.5 + 0.5;
                gl_Position = vec4(position, 0.0, 1.0);
            }
            """;

    private static final String COMPOSITE_FRAGMENT_SHADER = """
            #version 150

            uniform sampler2D uSceneTexture;
            uniform sampler2D uLightTexture;
            uniform sampler2D uBeamTexture;
            uniform bool uUseSceneTexture;

            in vec2 vTexCoord;
            out vec4 fragColor;

            // The beams are raymarched at half resolution with a fixed dither per pixel; upscaled, the dither showed
            // as a fine grid / snow pattern. Beams are soft fog, so a small blur (3x3 bilinear taps, about 6x6 beam
            // texels) removes the pattern without visible loss of detail.
            vec3 blurredBeam(vec2 texCoord) {
                vec2 texel = 1.0 / vec2(textureSize(uBeamTexture, 0));
                vec3 sum = vec3(0.0);
                float weightSum = 0.0;
                for (int y = -1; y <= 1; y++) {
                    for (int x = -1; x <= 1; x++) {
                        float weight = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0);
                        sum += texture(uBeamTexture, texCoord + vec2(x, y) * texel * 1.5).rgb * weight;
                        weightSum += weight;
                    }
                }
                return sum / weightSum;
            }

            void main() {
                vec2 texCoord = clamp(vTexCoord, vec2(0.0), vec2(1.0));
                vec4 light = texture(uLightTexture, texCoord);
                vec3 beam = blurredBeam(texCoord);
                if (uUseSceneTexture) {
                    vec4 scene = texture(uSceneTexture, texCoord);
                    // Surface light multiplies the scene; light beams are scattered light in the air and add on top.
                    fragColor = vec4(scene.rgb * (vec3(1.0) + light.rgb) + beam, scene.a);
                } else {
                    fragColor = vec4(light.rgb + beam, light.a);
                }
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 150

            const int MAX_POINT_LIGHTS = 96;
            const int MAX_AREA_LIGHTS = 96;
            const int MAX_BEAM_STEPS = 32;
            const float SKY_DEPTH = 0.999999;
            const float BEAM_MAX_DISTANCE = 48.0;
            const float BEAM_DENSITY = 0.02;
            const float MIN_VISIBLE_LIGHT = 0.002;
            const float DETAILED_NORMAL_DISTANCE = 64.0;
            const int FLAG_OCCLUDED = 1;
            const int FLAG_SKIP_FIRST_PERSON = 2;

            uniform sampler2D uDepth;
            uniform sampler2D uHandFreeDepth;
            uniform bool uHasHandFreeDepth;
            uniform sampler3D uBlockGrid;
            uniform mat4 uInverseViewProjection;
            uniform vec3 uCameraPosition;
            uniform vec2 uInverseViewSize;
            uniform vec2 uInverseDepthSize;
            uniform vec2 uDepthUvScale;
            uniform vec3 uGridOrigin;
            uniform bool uHasBlockGrid;
            uniform float uColorStrength;
            uniform float uColorSaturation;
            uniform float uNeutralLift;
            uniform float uLuminanceBoostLimit;
            uniform int uBeamSteps;
            uniform bool uBeamShadows;
            // false: full-resolution lighting pass; true: half-resolution light beam pass.
            uniform bool uBeamPass;

            uniform int uPointCount;
            uniform int uPointBeamCount;
            uniform vec4 uPointPositionRadius[MAX_POINT_LIGHTS];
            uniform vec4 uPointColor[MAX_POINT_LIGHTS];

            uniform int uAreaCount;
            uniform int uAreaBeamCount;
            uniform vec4 uAreaPositionDistance[MAX_AREA_LIGHTS];
            uniform vec4 uAreaDirectionAngle[MAX_AREA_LIGHTS];
            uniform vec4 uAreaRightSize[MAX_AREA_LIGHTS];
            uniform vec4 uAreaUpSize[MAX_AREA_LIGHTS];
            uniform vec4 uAreaColor[MAX_AREA_LIGHTS];
            uniform vec4 uAreaShape[MAX_AREA_LIGHTS];

            in vec2 vTexCoord;
            out vec4 fragColor;
            out vec4 beamColor;

            // Color alpha = flags + 4 * round(inscattering * 256), see VilcLightBatch.
            int lightFlags(float packedData) {
                return int(packedData + 0.5) & 3;
            }

            float lightInscattering(float packedData) {
                return float(int(packedData + 0.5) >> 2) / 256.0;
            }

            bool isFiniteVector(vec3 value) {
                return !any(isnan(value)) && !any(isinf(value));
            }

            float sampleDepth(vec2 texCoord) {
                return texture(uDepth, clamp(texCoord, vec2(0.0), vec2(1.0)) * uDepthUvScale).r;
            }

            vec3 reconstructWorldPosition(vec2 texCoord, float depth) {
                vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                vec4 world = uInverseViewProjection * clip;
                return world.xyz / world.w + uCameraPosition;
            }

            // Where the view rays actually start. View bobbing (and hurt shake) live in the projection matrix and
            // move the eye away from the entity camera position while walking; using uCameraPosition as the ray
            // origin made light beams swim around with every step. The eye is the projection centre, i.e. the
            // point that maps to clip (0, 0, z, 0).
            vec3 gEyePosition;

            vec3 projectionEye() {
                vec4 eye = uInverseViewProjection * vec4(0.0, 0.0, 1.0, 0.0);
                if (abs(eye.w) < 1.0e-8) {
                    return uCameraPosition;
                }
                vec3 position = eye.xyz / eye.w + uCameraPosition;
                return isFiniteVector(position) ? position : uCameraPosition;
            }

            // Offset to a neighbouring surface pixel. Sky neighbours (holes in leaves, grass silhouettes) reconstruct
            // to infinity or NaN, which used to turn the normal into NaN and the pixel black; they now count as
            // "far away", so the opposite neighbour or the camera-facing fallback is used instead.
            vec3 neighborOffset(vec2 texCoord, vec2 depthTexCoord, vec3 worldPosition) {
                float depth = sampleDepth(depthTexCoord);
                if (depth >= SKY_DEPTH) {
                    return vec3(1.0e4);
                }
                vec3 offset = reconstructWorldPosition(texCoord, depth) - worldPosition;
                return isFiniteVector(offset) ? offset : vec3(1.0e4);
            }

            // A neighbour on the same surface is roughly one pixel footprint away; a longer step is a depth edge
            // (grass blade in front of the ground) and must not feed the normal.
            bool isDepthEdge(vec3 offset, float cameraDistance) {
                float limit = 0.05 + cameraDistance * 0.02;
                return dot(offset, offset) > limit * limit;
            }

            vec3 surfaceNormal(vec3 worldPosition, vec2 texCoord, vec2 depthTexCoord) {
                // Screen-space derivatives must run in uniform control flow; evaluating them only for pixels
                // beyond the detailed-normal range left a ring of broken normals at exactly that distance.
                vec3 dx = dFdx(worldPosition);
                vec3 dy = dFdy(worldPosition);
                float cameraDistance = distance(worldPosition, gEyePosition);
                // Quad derivatives pair each pixel with one fixed neighbour. At depth edges that neighbour lies on
                // another surface, which tilted the normal away from the light and left dashed dark outlines around
                // foliage. Small steps (flower petals lying on grass) are too short for any distance threshold, so
                // nearby pixels always take the closer of both neighbours; far away the threshold is good enough.
                bool depthEdge = !isFiniteVector(dx) || !isFiniteVector(dy)
                    || isDepthEdge(dx, cameraDistance) || isDepthEdge(dy, cameraDistance);
                if (cameraDistance < DETAILED_NORMAL_DISTANCE || depthEdge) {
                    vec2 horizontalOffset = vec2(uInverseViewSize.x, 0.0);
                    vec2 verticalOffset = vec2(0.0, uInverseViewSize.y);
                    vec2 horizontalDepthOffset = vec2(uInverseDepthSize.x, 0.0);
                    vec2 verticalDepthOffset = vec2(0.0, uInverseDepthSize.y);
                    vec3 left = neighborOffset(
                        texCoord - horizontalOffset, depthTexCoord - horizontalDepthOffset, worldPosition);
                    vec3 right = neighborOffset(
                        texCoord + horizontalOffset, depthTexCoord + horizontalDepthOffset, worldPosition);
                    vec3 down = neighborOffset(
                        texCoord - verticalOffset, depthTexCoord - verticalDepthOffset, worldPosition);
                    vec3 up = neighborOffset(
                        texCoord + verticalOffset, depthTexCoord + verticalDepthOffset, worldPosition);
                    dx = dot(left, left) < dot(right, right) ? -left : right;
                    dy = dot(down, down) < dot(up, up) ? -down : up;
                }

                vec3 towardCamera = normalize(gEyePosition - worldPosition);
                // Both neighbours across an edge (one-pixel-wide blades): no usable surface, face the camera.
                if (!isFiniteVector(dx) || !isFiniteVector(dy)
                        || isDepthEdge(dx, cameraDistance) || isDepthEdge(dy, cameraDistance)) {
                    return towardCamera;
                }
                vec3 crossed = cross(dx, dy);
                if (dot(crossed, crossed) < 1.0e-12) {
                    return towardCamera;
                }
                vec3 normal = normalize(crossed);
                if (dot(normal, gEyePosition - worldPosition) < 0.0) {
                    normal = -normal;
                }
                vec3 absoluteNormal = abs(normal);
                float dominantAxis = max(absoluteNormal.x, max(absoluteNormal.y, absoluteNormal.z));
                if (dominantAxis > 0.98) {
                    normal = sign(normal) * step(vec3(dominantAxis - 0.0001), absoluteNormal);
                }
                return normal;
            }

            float surfaceResponse(vec3 normal, vec3 directionToLight) {
                float lambert = max(dot(normal, directionToLight), 0.0);
                return 0.15 * smoothstep(0.0, 0.08, lambert)
                    + 0.85 * sqrt(lambert);
            }

            vec3 normalizeLightColor(vec3 color) {
                float peak = max(color.r, max(color.g, color.b));
                if (peak <= 0.0001) {
                    return vec3(0.0);
                }
                vec3 hue = color / peak;
                float luminance = dot(hue, vec3(0.2126, 0.7152, 0.0722));
                float requestedBoost = luminance > 0.0001
                    ? max(0.7152 / luminance, 1.0)
                    : 1.0;
                float boostRange = max(uLuminanceBoostLimit - 1.0, 0.0);
                float boostDelta = requestedBoost - 1.0;
                float boost = boostRange > 0.0001
                    ? 1.0 + boostRange * boostDelta / (boostRange + boostDelta)
                    : 1.0;
                return hue * boost * peak;
            }

            // Compresses each light on its own before accumulation. Compressing only the summed vector kept the
            // linear hue ratio of the sum, so in overlaps the closer light dominated completely while both lights
            // already looked saturated on their own: red and blue split along a hard edge instead of mixing to
            // magenta. For a single light this is identical to the previous summed compression.
            vec3 compressedContribution(vec3 color, float amount) {
                float strength = length(color) * 0.57735026919;
                if (strength <= 0.0001 || amount <= 0.0) {
                    return vec3(0.0);
                }
                return color * ((1.0 - exp(-strength * amount)) / strength);
            }

            vec3 applySaturation(vec3 color, float saturation) {
                float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
                vec3 chroma = color - vec3(luminance);
                if (saturation <= 1.0) {
                    return vec3(luminance) + chroma * saturation;
                }

                float minimumChroma = min(chroma.r, min(chroma.g, chroma.b));
                float maximumScale = minimumChroma < -0.000001
                    ? luminance / -minimumChroma
                    : saturation;
                float availableExtra = max(maximumScale - 1.0, 0.0);
                float requestedExtra = saturation - 1.0;
                float appliedExtra = availableExtra > 0.000001
                    ? requestedExtra / (1.0 + requestedExtra / availableExtra)
                    : 0.0;
                return vec3(luminance) + chroma * (1.0 + appliedExtra);
            }

            bool insideGrid(ivec3 cell) {
                ivec3 local = cell - ivec3(uGridOrigin);
                return all(greaterThanEqual(local, ivec3(0)))
                    && all(lessThan(local, ivec3(64)));
            }

            bool clipRayToGrid(inout vec3 start, vec3 direction, float rayLength) {
                vec3 gridMinimum = uGridOrigin;
                vec3 gridMaximum = uGridOrigin + vec3(64.0);
                vec3 safeDirection = vec3(
                    abs(direction.x) > 0.00001 ? direction.x : 0.00001,
                    abs(direction.y) > 0.00001 ? direction.y : 0.00001,
                    abs(direction.z) > 0.00001 ? direction.z : 0.00001
                );
                vec3 first = (gridMinimum - start) / safeDirection;
                vec3 second = (gridMaximum - start) / safeDirection;
                vec3 nearAxis = min(first, second);
                vec3 farAxis = max(first, second);
                float nearDistance = max(max(nearAxis.x, nearAxis.y), nearAxis.z);
                float farDistance = min(min(farAxis.x, farAxis.y), farAxis.z);
                if (farDistance < max(nearDistance, 0.0) || nearDistance >= rayLength) {
                    return false;
                }
                if (nearDistance > 0.0) {
                    start += direction * (nearDistance + 0.001);
                }
                return true;
            }

            float shadowVisibility(vec3 worldPosition, vec3 normal, vec3 lightPosition, bool occluded) {
                if (!occluded || !uHasBlockGrid) {
                    return 1.0;
                }

                vec3 start = worldPosition + normal * 0.08;
                vec3 ray = lightPosition - start;
                float rayLength = length(ray);
                if (rayLength <= 0.25) {
                    return 1.0;
                }

                vec3 direction = ray / rayLength;
                if (!insideGrid(ivec3(floor(start)))
                        && !clipRayToGrid(start, direction, rayLength)) {
                    return 1.0;
                }
                ray = lightPosition - start;
                rayLength = length(ray);
                if (rayLength <= 0.0001) {
                    return 1.0;
                }
                direction = ray / rayLength;
                ivec3 cell = ivec3(floor(start));
                ivec3 lightCell = ivec3(floor(lightPosition));
                ivec3 stepDirection = ivec3(
                    direction.x >= 0.0 ? 1 : -1,
                    direction.y >= 0.0 ? 1 : -1,
                    direction.z >= 0.0 ? 1 : -1
                );
                vec3 inverseDirection = 1.0 / max(abs(direction), vec3(0.00001));
                vec3 nextBoundary = vec3(cell) + vec3(
                    stepDirection.x > 0 ? 1.0 : 0.0,
                    stepDirection.y > 0 ? 1.0 : 0.0,
                    stepDirection.z > 0 ? 1.0 : 0.0
                );
                vec3 sideDistance = abs((nextBoundary - start) * inverseDirection);
                vec3 deltaDistance = inverseDirection;
                float visibility = 1.0;

                for (int step = 0; step < 64; step++) {
                    float traveled;
                    if (sideDistance.x <= sideDistance.y && sideDistance.x <= sideDistance.z) {
                        traveled = sideDistance.x;
                        sideDistance.x += deltaDistance.x;
                        cell.x += stepDirection.x;
                    } else if (sideDistance.y <= sideDistance.z) {
                        traveled = sideDistance.y;
                        sideDistance.y += deltaDistance.y;
                        cell.y += stepDirection.y;
                    } else {
                        traveled = sideDistance.z;
                        sideDistance.z += deltaDistance.z;
                        cell.z += stepDirection.z;
                    }

                    if (traveled >= rayLength - 0.2 || all(equal(cell, lightCell))) {
                        return visibility;
                    }
                    if (!insideGrid(cell)) {
                        return visibility;
                    }

                    ivec3 local = cell - ivec3(uGridOrigin);
                    float occupancy = texelFetch(uBlockGrid, local, 0).r;
                    if (occupancy >= 0.99) {
                        return 0.0;
                    }
                    visibility *= 1.0 - occupancy * 0.75;
                    if (visibility <= 0.04) {
                        return 0.0;
                    }
                }
                return visibility;
            }

            float pointFalloff(int i, vec3 position) {
                float distanceToLight = length(uPointPositionRadius[i].xyz - position);
                float falloff = 1.0 - smoothstep(0.0, 1.0, distanceToLight / uPointPositionRadius[i].w);
                return falloff * falloff;
            }

            // Per-channel weight (cone x distance falloff) of cone light i at a position. The channels only differ
            // with a chromatic fringe: red reaches slightly further out than green, blue stops slightly earlier,
            // like the edge of a simple lens. lightPosition receives the closest point on the emitter.
            // pointEmitter: light beams shrink a round (spot) lens to its center point, a capsule lens to its center
            // line; rectangles stay whole. With a round lens as an area, a beam
            // sample just in front of it counted as fully inside the cone whatever the ray direction; for a light
            // at the eye (flashlight) that single sample covered a whole step and left a dithered pattern of bright
            // dots ("snow") all over the screen.
            vec3 coneLightWeights(int i, vec3 position, bool pointEmitter, out vec3 lightPosition) {
                vec3 areaCenter = uAreaPositionDistance[i].xyz;
                vec3 right = normalize(uAreaRightSize[i].xyz);
                vec3 up = normalize(uAreaUpSize[i].xyz);
                vec3 relativePosition = position - areaCenter;
                vec2 lensOffset = vec2(dot(relativePosition, right), dot(relativePosition, up));
                if (uAreaShape[i].z > 0.5) {
                    // Spot light lens: a disk, or a capsule (the disk swept along local x, e.g. a fluorescent
                    // tube). Clamping per axis made a square emitter whose corners followed the light's roll, so
                    // the beam looked square and rotated with the view direction. Beams (pointEmitter) shrink the
                    // disk to its center line.
                    float halfLength = uAreaShape[i].z - 1.0;
                    vec2 onAxis = vec2(clamp(lensOffset.x, -halfLength, halfLength), 0.0);
                    vec2 fromAxis = lensOffset - onAxis;
                    float lensLength = length(fromAxis);
                    float lensRadius = pointEmitter ? 0.0 : uAreaRightSize[i].w;
                    if (lensLength > lensRadius) {
                        fromAxis *= lensRadius / max(lensLength, 0.000001);
                    }
                    lensOffset = onAxis + fromAxis;
                } else {
                    lensOffset = clamp(
                        lensOffset,
                        -vec2(uAreaRightSize[i].w, uAreaUpSize[i].w),
                        vec2(uAreaRightSize[i].w, uAreaUpSize[i].w));
                }
                lightPosition = areaCenter + right * lensOffset.x + up * lensOffset.y;

                vec3 fromLight = position - lightPosition;
                float distanceToLight = length(fromLight);
                if (distanceToLight <= 0.0001) {
                    return vec3(0.0);
                }
                float cone = dot(fromLight / distanceToLight, normalize(uAreaDirectionAngle[i].xyz));
                float edge = uAreaDirectionAngle[i].w;
                float edgeSoftness = max(uAreaShape[i].x, 0.0001);
                float focus = max(uAreaShape[i].y, 0.05);
                vec3 coneFalloff = vec3(1.0);
                if (edge < -1.5) {
                    // 360 degree area light: no cone, shines evenly all around its rectangle (line lights).
                } else if (edge <= -0.999 && uAreaShape[i].z < 0.5) {
                    // 180 degree area light: fade out towards the back like Veil does (smoothstep over the angle to
                    // the normal). Two such lights back to back then add up to an even line light (L?YM! tube rows).
                    coneFalloff = vec3(1.0 - smoothstep(0.0, 1.0, acos(clamp(cone, -1.0, 1.0)) / 3.14159265));
                } else if (edge > -0.999) {
                    float spread = uAreaShape[i].w * edgeSoftness * 1.5;
                    vec3 edges = vec3(edge - spread, edge, edge + spread);
                    vec3 tops = max(min(edges + edgeSoftness, vec3(1.0)), edges + 0.00001);
                    coneFalloff = smoothstep(edges, tops, vec3(cone));
                }
                coneFalloff = pow(coneFalloff, vec3(focus));
                float distanceFalloff = 1.0 - smoothstep(0.0, 1.0, distanceToLight / uAreaPositionDistance[i].w);
                return coneFalloff * (distanceFalloff * distanceFalloff);
            }

            float interleavedGradientNoise(vec2 pixel) {
                return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
            }

            // Overlap of the view ray with a light's range sphere, clipped to [0, rayLength].
            bool beamInterval(vec3 rayDirection, float rayLength, vec3 center, float radius,
                              out float rayStart, out float rayEnd) {
                vec3 toCenter = center - gEyePosition;
                float along = dot(toCenter, rayDirection);
                float closestSquared = dot(toCenter, toCenter) - along * along;
                float radiusSquared = radius * radius;
                rayStart = 0.0;
                rayEnd = 0.0;
                if (closestSquared >= radiusSquared) {
                    return false;
                }
                float halfChord = sqrt(radiusSquared - closestSquared);
                rayStart = max(along - halfChord, 0.0);
                rayEnd = min(along + halfChord, rayLength);
                return rayEnd > rayStart;
            }

            // Light scattered towards the camera by the air along the view ray, up to the surface (or a fixed
            // distance for the sky). Each light is only sampled where the ray passes through its range, so the
            // steps are spent where the beam actually is; dithered offsets hide the banding of the few steps.
            // Voxel shadows inside beams are expensive (steps x lights x DDA) and only run on High quality.
            vec3 lightBeams(vec2 texCoord, float surfaceDistance) {
                vec3 rayDirection = normalize(reconstructWorldPosition(texCoord, 0.5) - gEyePosition);
                float rayLength = surfaceDistance < 0.0
                    ? BEAM_MAX_DISTANCE
                    : min(surfaceDistance, BEAM_MAX_DISTANCE);
                if (!(rayLength > 0.01) || !isFiniteVector(rayDirection)) {
                    return vec3(0.0);
                }
                float jitter = interleavedGradientNoise(gl_FragCoord.xy);
                float steps = float(uBeamSteps);
                vec3 scattered = vec3(0.0);

                for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
                    if (i >= uPointBeamCount) {
                        break;
                    }
                    vec3 lightPosition = uPointPositionRadius[i].xyz;
                    float rayStart;
                    float rayEnd;
                    if (!beamInterval(rayDirection, rayLength, lightPosition, uPointPositionRadius[i].w,
                            rayStart, rayEnd)) {
                        continue;
                    }
                    float stepLength = (rayEnd - rayStart) / steps;
                    bool occluded = uBeamShadows && (lightFlags(uPointColor[i].a) & FLAG_OCCLUDED) != 0;
                    float amount = 0.0;
                    for (int beamStep = 0; beamStep < MAX_BEAM_STEPS; beamStep++) {
                        if (beamStep >= uBeamSteps) {
                            break;
                        }
                        vec3 samplePosition = gEyePosition
                            + rayDirection * (rayStart + stepLength * (float(beamStep) + jitter));
                        float falloff = pointFalloff(i, samplePosition);
                        if (falloff <= 0.0) {
                            continue;
                        }
                        amount += falloff * shadowVisibility(samplePosition, vec3(0.0), lightPosition, occluded);
                    }
                    scattered += normalizeLightColor(uPointColor[i].rgb)
                        * (lightInscattering(uPointColor[i].a) * amount * stepLength);
                }

                for (int i = 0; i < MAX_AREA_LIGHTS; i++) {
                    if (i >= uAreaBeamCount) {
                        break;
                    }
                    float lensReach = max(uAreaRightSize[i].w, uAreaUpSize[i].w)
                        + (uAreaShape[i].z > 0.5 ? uAreaShape[i].z - 1.0 : 0.0);
                    float rayStart;
                    float rayEnd;
                    if (!beamInterval(rayDirection, rayLength, uAreaPositionDistance[i].xyz,
                            uAreaPositionDistance[i].w + lensReach, rayStart, rayEnd)) {
                        continue;
                    }
                    float stepLength = (rayEnd - rayStart) / steps;
                    bool occluded = uBeamShadows && (lightFlags(uAreaColor[i].a) & FLAG_OCCLUDED) != 0;
                    vec3 amount = vec3(0.0);
                    for (int beamStep = 0; beamStep < MAX_BEAM_STEPS; beamStep++) {
                        if (beamStep >= uBeamSteps) {
                            break;
                        }
                        vec3 samplePosition = gEyePosition
                            + rayDirection * (rayStart + stepLength * (float(beamStep) + jitter));
                        vec3 lightPosition;
                        vec3 weights = coneLightWeights(i, samplePosition, true, lightPosition);
                        if (max(weights.r, max(weights.g, weights.b)) <= 0.0) {
                            continue;
                        }
                        amount += weights * shadowVisibility(samplePosition, vec3(0.0), lightPosition, occluded);
                    }
                    scattered += normalizeLightColor(uAreaColor[i].rgb) * amount
                        * (lightInscattering(uAreaColor[i].a) * stepLength);
                }

                scattered *= BEAM_DENSITY;
                vec3 beam = vec3(1.0) - exp(-scattered);
                return isFiniteVector(beam) ? beam : vec3(0.0);
            }

            void main() {
                gEyePosition = projectionEye();
                vec2 screenTexCoord = clamp(vTexCoord, vec2(0.0), vec2(1.0));
                float depth = sampleDepth(screenTexCoord);
                bool sky = depth >= SKY_DEPTH;
                if (uBeamPass) {
                    // Half-resolution beam pass: only the air between the eye and the surface matters here.
                    fragColor = vec4(0.0);
                    vec3 beam = vec3(0.0);
                    if (uBeamSteps > 0) {
                        vec3 surfacePosition = reconstructWorldPosition(screenTexCoord, min(depth, SKY_DEPTH));
                        beam = lightBeams(screenTexCoord, sky ? -1.0 : distance(surfacePosition, gEyePosition));
                    }
                    beamColor = vec4(beam, 0.0);
                    return;
                }
                // Computed for every pixel (sky included) so the derivatives in surfaceNormal stay in uniform flow.
                vec3 worldPosition = reconstructWorldPosition(screenTexCoord, min(depth, SKY_DEPTH));
                vec3 normal = surfaceNormal(worldPosition, screenTexCoord, screenTexCoord);
                // Hand and held item: closer than the depth Iris captured right before drawing the hand.
                bool firstPerson = uHasHandFreeDepth && !sky
                    && depth < texture(uHandFreeDepth, screenTexCoord * uDepthUvScale).r - 0.000001;
                vec3 lightSum = vec3(0.0);

                if (!sky) {
                    for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
                        if (i >= uPointCount) {
                            break;
                        }
                        int flags = lightFlags(uPointColor[i].a);
                        if (firstPerson && (flags & FLAG_SKIP_FIRST_PERSON) != 0) {
                            continue;
                        }
                        vec3 delta = uPointPositionRadius[i].xyz - worldPosition;
                        float distanceToLight = length(delta);
                        if (distanceToLight <= 0.0001) {
                            continue;
                        }
                        float falloff = pointFalloff(i, worldPosition);
                        if (falloff <= 0.0) {
                            continue;
                        }
                        float surface = surfaceResponse(normal, delta / distanceToLight);
                        vec3 lightColor = normalizeLightColor(uPointColor[i].rgb);
                        // Below ~0.2 % the light is invisible; skip it before paying for the shadow ray.
                        if (length(lightColor) * 0.57735026919 * falloff * surface < MIN_VISIBLE_LIGHT) {
                            continue;
                        }
                        float visibility = shadowVisibility(
                            worldPosition,
                            normal,
                            uPointPositionRadius[i].xyz,
                            (flags & FLAG_OCCLUDED) != 0);
                        lightSum += compressedContribution(lightColor, falloff * surface * visibility);
                    }

                    for (int i = 0; i < MAX_AREA_LIGHTS; i++) {
                        if (i >= uAreaCount) {
                            break;
                        }
                        int flags = lightFlags(uAreaColor[i].a);
                        if (firstPerson && (flags & FLAG_SKIP_FIRST_PERSON) != 0) {
                            continue;
                        }
                        vec3 lightPosition;
                        vec3 weights = coneLightWeights(i, worldPosition, false, lightPosition);
                        float peakWeight = max(weights.r, max(weights.g, weights.b));
                        if (peakWeight <= 0.0) {
                            continue;
                        }
                        vec3 toLight = lightPosition - worldPosition;
                        float distanceToLight = length(toLight);
                        if (distanceToLight <= 0.0001) {
                            continue;
                        }
                        float surface = surfaceResponse(normal, toLight / distanceToLight);
                        vec3 lightColor = normalizeLightColor(uAreaColor[i].rgb);
                        if (length(lightColor) * 0.57735026919 * peakWeight * surface < MIN_VISIBLE_LIGHT) {
                            continue;
                        }
                        float visibility = shadowVisibility(
                            worldPosition,
                            normal,
                            lightPosition,
                            (flags & FLAG_OCCLUDED) != 0);
                        lightSum += compressedContribution(lightColor, peakWeight * surface * visibility)
                            * (weights / peakWeight);
                    }
                }
                if (!isFiniteVector(lightSum)) {
                    lightSum = vec3(0.0);
                }

                // Lights are already compressed individually, so a single light stays below 1. Only overlapping
                // lights get a soft shoulder above 1 (C1-continuous at the knee) to keep stacked lights bounded.
                float energy = length(lightSum) * 0.57735026919;
                float intensity = energy <= 1.0
                    ? energy
                    : 2.0 - exp(1.0 - energy);
                vec3 compressedLight = energy > 0.0001
                    ? lightSum * (intensity / energy)
                    : vec3(0.0);
                vec3 rawColoredLift = compressedLight * uColorStrength;
                float rawLuminance = dot(rawColoredLift, vec3(0.2126, 0.7152, 0.0722));
                vec3 coloredLift = rawColoredLift / (1.0 + rawLuminance * 0.5);
                coloredLift = applySaturation(coloredLift, uColorSaturation);
                vec3 neutralLift = vec3(1.0 - exp(-intensity * uNeutralLift));
                fragColor = vec4(coloredLift + neutralLift, 0.0);
                beamColor = vec4(0.0);
            }
            """;
}
