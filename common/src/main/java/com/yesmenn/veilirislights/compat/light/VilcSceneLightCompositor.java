package com.yesmenn.veilirislights.compat.light;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.DDALightData;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import net.minecraft.client.Minecraft;
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
    private static int lightTextureWidth;
    private static int lightTextureHeight;
    private static int attachedColorTexture;
    private static boolean glErrorLogged;
    private static int dummyBlockGridTexture;
    private static VilcLightUniforms uniforms;
    private static int compositeLightTextureUniform;
    private static int compositeSceneTextureUniform;
    private static int compositeUseSceneTextureUniform;
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

        renderToTarget(
                pointHandles,
                areaHandles,
                colorTexture,
                renderTargets.getDepthTexture(),
                renderTargets.getCurrentWidth(),
                renderTargets.getCurrentHeight(),
                1.0F,
                1.0F,
                inverseViewProjection,
                cameraPosition,
                SHADERPACK_LIGHT_GAIN,
                true,
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
                target.viewWidth,
                target.viewHeight,
                target.width > 0 ? (float) target.viewWidth / (float) target.width : 1.0F,
                target.height > 0 ? (float) target.viewHeight / (float) target.height : 1.0F,
                inverseViewProjection,
                cameraPosition,
                1.0F,
                false,
                "vanilla");
    }

    private static void renderToTarget(
            Collection<? extends LightRenderHandle<PointLightData>> pointHandles,
            Collection<? extends LightRenderHandle<AreaLightData>> areaHandles,
            int colorTexture,
            int depthTexture,
            int width,
            int height,
            float depthUvScaleX,
            float depthUvScaleY,
            Matrix4f inverseViewProjection,
            Vector3d cameraPosition,
            float lightGain,
            boolean firstPersonAdditiveEnabled,
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
        boolean hasOccludedLights = config.voxelShadows
                && hasOccludedLights(pointHandles, areaHandles, spotHandles);
        lastOcclusionEnabled = hasOccludedLights;
        if (hasOccludedLights) {
            VoxelShadowGrid.setup();
        }
        boolean hasUsableVoxelGrid = hasOccludedLights && VoxelShadowGridState.isReady();

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

        if (!copyCurrentColorTarget(glState, renderWidth, renderHeight)) {
            glState.restore();
            return;
        }

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, lightTexture, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
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
        Vector3f gridOrigin = new Vector3f(VoxelShadowGrid.getUniformGridPos());
        glUniform3f(uniforms.gridOrigin(),
                gridOrigin.x, gridOrigin.y, gridOrigin.z);
        glUniform1i(uniforms.hasBlockGrid(), hasUsableVoxelGrid ? 1 : 0);
        glUniform1i(uniforms.detailedNormals(), config.detailedNormals ? 1 : 0);

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
        glUniform1i(uniforms.firstPersonAdditiveEnabled(), firstPersonAdditiveEnabled ? 1 : 0);

        int uploadedPointCount = VilcLightBatch.uploadPointLights(
                pointHandles,
                uniforms,
                tuning.exposure(),
                config.pointLightLimit,
                lightGain);
        int uploadedAreaCount = VilcLightBatch.uploadAreaLights(
                areaHandles,
                spotHandles,
                uniforms,
                tuning.exposure(),
                config.areaLightLimit,
                lightGain);
        logPassState(
                targetName,
                uploadedPointCount,
                uploadedAreaCount,
                renderWidth,
                renderHeight,
                colorTexture,
                depthTexture);

        glBindVertexArray(vertexArray);
        glUniform1i(uniforms.firstPersonPass(), 0);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        logGlError("light draw " + targetName);

        int compositeTexture = lightTexture;
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(compositeTexture, glState.activeTexture());
        VilcGlStateSnapshot.unbindTexture2DFromAllUnits(sceneCopyTexture, glState.activeTexture());

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
        if (dummyBlockGridTexture != 0) {
            glDeleteTextures(dummyBlockGridTexture);
            dummyBlockGridTexture = 0;
        }
        lightTextureWidth = 0;
        lightTextureHeight = 0;
        attachedColorTexture = 0;
        glErrorLogged = false;
        uniforms = null;
        compositeLightTextureUniform = -1;
        compositeSceneTextureUniform = -1;
        compositeUseSceneTextureUniform = -1;
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
    }

    private static int linkProgram(String name, String fragmentSource) {
        int vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
        int linkedProgram = glCreateProgram();
        glAttachShader(linkedProgram, vertexShader);
        glAttachShader(linkedProgram, fragmentShader);
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

    private static boolean ensureLightTextures(int width, int height) {
        if (lightTexture == 0) {
            lightTexture = glGenTextures();
        }
        if (sceneCopyTexture == 0) {
            sceneCopyTexture = glGenTextures();
        }
        int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        boolean resize = lightTextureWidth != width || lightTextureHeight != height;
        setupLightTexture(lightTexture, width, height, resize);
        setupLightTexture(sceneCopyTexture, width, height, resize);
        lightTextureWidth = width;
        lightTextureHeight = height;
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(oldActiveTexture);
        return lightTexture != 0 && sceneCopyTexture != 0;
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

    private static boolean hasOccludedLights(
            Collection<? extends LightRenderHandle<PointLightData>> pointHandles,
            Collection<? extends LightRenderHandle<AreaLightData>> areaHandles,
            Collection<? extends LightRenderHandle<SpotLightData>> spotHandles) {
        return anyOccluded(pointHandles) || anyOccluded(areaHandles) || anyOccluded(spotHandles);
    }

    private static boolean anyOccluded(Collection<? extends LightRenderHandle<? extends LightData>> handles) {
        for (LightRenderHandle<? extends LightData> handle : handles) {
            LightData light = handle.isValid() ? handle.getLightData() : null;
            if (light instanceof DDALightData occludable && occludable.isOcclusionEnabled()) {
                return true;
            }
        }
        return false;
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
            uniform bool uUseSceneTexture;

            in vec2 vTexCoord;
            out vec4 fragColor;

            void main() {
                vec2 texCoord = clamp(vTexCoord, vec2(0.0), vec2(1.0));
                vec4 light = texture(uLightTexture, texCoord);
                if (uUseSceneTexture) {
                    vec4 scene = texture(uSceneTexture, texCoord);
                    fragColor = vec4(scene.rgb * (vec3(1.0) + light.rgb), scene.a);
                } else {
                    fragColor = light;
                }
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150

            const int MAX_POINT_LIGHTS = 96;
            const int MAX_AREA_LIGHTS = 96;

            uniform sampler2D uDepth;
            uniform sampler3D uBlockGrid;
            uniform mat4 uInverseViewProjection;
            uniform vec3 uCameraPosition;
            uniform vec2 uInverseViewSize;
            uniform vec2 uInverseDepthSize;
            uniform vec2 uDepthUvScale;
            uniform vec3 uGridOrigin;
            uniform bool uHasBlockGrid;
            uniform bool uDetailedNormals;
            uniform float uColorStrength;
            uniform float uColorSaturation;
            uniform float uNeutralLift;
            uniform float uLuminanceBoostLimit;
            uniform bool uFirstPersonAdditiveEnabled;
            uniform bool uFirstPersonPass;

            uniform int uPointCount;
            uniform vec4 uPointPositionRadius[MAX_POINT_LIGHTS];
            uniform vec4 uPointColor[MAX_POINT_LIGHTS];

            uniform int uAreaCount;
            uniform vec4 uAreaPositionDistance[MAX_AREA_LIGHTS];
            uniform vec4 uAreaDirectionAngle[MAX_AREA_LIGHTS];
            uniform vec4 uAreaRightSize[MAX_AREA_LIGHTS];
            uniform vec4 uAreaUpSize[MAX_AREA_LIGHTS];
            uniform vec4 uAreaColor[MAX_AREA_LIGHTS];
            uniform vec4 uAreaShape[MAX_AREA_LIGHTS];

            in vec2 vTexCoord;
            out vec4 fragColor;

            float sampleDepth(vec2 texCoord) {
                return texture(uDepth, clamp(texCoord, vec2(0.0), vec2(1.0)) * uDepthUvScale).r;
            }

            vec3 reconstructWorldPosition(vec2 texCoord, float depth) {
                vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                vec4 world = uInverseViewProjection * clip;
                return world.xyz / world.w + uCameraPosition;
            }

            vec3 surfaceNormal(vec3 worldPosition, vec2 texCoord, vec2 depthTexCoord) {
                // Screen-space derivatives must run in uniform control flow; evaluating them only for pixels
                // beyond the detailed-normal range left a ring of broken normals at exactly that distance.
                vec3 dx = dFdx(worldPosition);
                vec3 dy = dFdy(worldPosition);
                float cameraDistance = distance(worldPosition, uCameraPosition);
                if (uDetailedNormals && cameraDistance < 48.0) {
                    vec2 horizontalOffset = vec2(uInverseViewSize.x, 0.0);
                    vec2 verticalOffset = vec2(0.0, uInverseViewSize.y);
                    vec2 horizontalDepthOffset = vec2(uInverseDepthSize.x, 0.0);
                    vec2 verticalDepthOffset = vec2(0.0, uInverseDepthSize.y);
                    vec3 left = reconstructWorldPosition(
                        texCoord - horizontalOffset,
                        sampleDepth(depthTexCoord - horizontalDepthOffset)) - worldPosition;
                    vec3 right = reconstructWorldPosition(
                        texCoord + horizontalOffset,
                        sampleDepth(depthTexCoord + horizontalDepthOffset)) - worldPosition;
                    vec3 down = reconstructWorldPosition(
                        texCoord - verticalOffset,
                        sampleDepth(depthTexCoord - verticalDepthOffset)) - worldPosition;
                    vec3 up = reconstructWorldPosition(
                        texCoord + verticalOffset,
                        sampleDepth(depthTexCoord + verticalDepthOffset)) - worldPosition;
                    dx = dot(left, left) < dot(right, right) ? -left : right;
                    dy = dot(down, down) < dot(up, up) ? -down : up;
                }

                vec3 normal = normalize(cross(dx, dy));
                if (length(dx) > 4.0 || length(dy) > 4.0) {
                    normal = normalize(uCameraPosition - worldPosition);
                }
                if (dot(normal, uCameraPosition - worldPosition) < 0.0) {
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

            void main() {
                vec2 screenTexCoord = clamp(vTexCoord, vec2(0.0), vec2(1.0));
                vec2 depthTexCoord = screenTexCoord;
                float depth = sampleDepth(depthTexCoord);
                if (depth >= 0.999999) {
                    discard;
                }

                vec3 worldPosition = reconstructWorldPosition(screenTexCoord, depth);
                bool firstPersonScreenRegion = screenTexCoord.y < 0.38
                    && (screenTexCoord.x < 0.32 || screenTexCoord.x > 0.55);
                bool firstPersonFragment = firstPersonScreenRegion
                    && distance(worldPosition, uCameraPosition) < 0.42;
                if (uFirstPersonAdditiveEnabled
                        && ((uFirstPersonPass && !firstPersonFragment)
                        || (!uFirstPersonPass && firstPersonFragment))) {
                    discard;
                }
                vec3 normal = surfaceNormal(worldPosition, screenTexCoord, depthTexCoord);
                vec3 lightSum = vec3(0.0);

                for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
                    if (i >= uPointCount) {
                        break;
                    }
                    vec3 delta = uPointPositionRadius[i].xyz - worldPosition;
                    float radius = uPointPositionRadius[i].w;
                    float distanceToLight = length(delta);
                    if (distanceToLight <= 0.0001) {
                        continue;
                    }
                    float falloff = smoothstep(1.0, 0.0, distanceToLight / radius);
                    falloff *= falloff;
                    if (falloff <= 0.0) {
                        continue;
                    }
                    vec3 directionToLight = delta / distanceToLight;
                    float surface = surfaceResponse(normal, directionToLight);
                    if (surface <= 0.0) {
                        continue;
                    }
                    float visibility = shadowVisibility(
                        worldPosition,
                        normal,
                        uPointPositionRadius[i].xyz,
                        uPointColor[i].a > 0.5
                    );
                    lightSum += normalizeLightColor(uPointColor[i].rgb)
                        * falloff * surface * visibility;
                }

                for (int i = 0; i < MAX_AREA_LIGHTS; i++) {
                    if (i >= uAreaCount) {
                        break;
                    }
                    vec3 areaCenter = uAreaPositionDistance[i].xyz;
                    vec3 right = normalize(uAreaRightSize[i].xyz);
                    vec3 up = normalize(uAreaUpSize[i].xyz);
                    vec3 relativePosition = worldPosition - areaCenter;
                    vec3 lightPosition = areaCenter
                        + right * clamp(
                            dot(relativePosition, right),
                            -uAreaRightSize[i].w,
                            uAreaRightSize[i].w)
                        + up * clamp(
                            dot(relativePosition, up),
                            -uAreaUpSize[i].w,
                            uAreaUpSize[i].w);
                    vec3 fromLight = worldPosition - lightPosition;
                    float distanceToLight = length(fromLight);
                    float distanceLimit = uAreaPositionDistance[i].w;
                    if (distanceToLight <= 0.0001) {
                        continue;
                    }
                    vec3 direction = normalize(fromLight);
                    float cone = dot(direction, normalize(uAreaDirectionAngle[i].xyz));
                    float edge = uAreaDirectionAngle[i].w;
                    float edgeSoftness = max(uAreaShape[i].x, 0.0001);
                    float focus = max(uAreaShape[i].y, 0.05);
                    float coneFalloff = edge <= -0.999
                        ? 1.0
                        : smoothstep(edge, min(edge + edgeSoftness, 1.0), cone);
                    coneFalloff = pow(coneFalloff, focus);
                    float distanceFalloff = smoothstep(1.0, 0.0, distanceToLight / distanceLimit);
                    if (coneFalloff <= 0.0 || distanceFalloff <= 0.0) {
                        continue;
                    }
                    vec3 directionToLight = -direction;
                    float surface = surfaceResponse(normal, directionToLight);
                    if (surface <= 0.0) {
                        continue;
                    }
                    float visibility = shadowVisibility(
                        worldPosition,
                        normal,
                        lightPosition,
                        uAreaColor[i].a > 0.5
                    );
                    lightSum += normalizeLightColor(uAreaColor[i].rgb)
                        * coneFalloff * distanceFalloff * distanceFalloff
                        * surface * visibility;
                }

                float energy = length(lightSum) * 0.57735026919;
                float intensity = 1.0 - exp(-energy);
                vec3 compressedLight = energy > 0.0001
                    ? lightSum * (intensity / energy)
                    : vec3(0.0);
                vec3 rawColoredLift = compressedLight * uColorStrength;
                float rawLuminance = dot(rawColoredLift, vec3(0.2126, 0.7152, 0.0722));
                vec3 coloredLift = rawColoredLift / (1.0 + rawLuminance * 0.5);
                coloredLift = applySaturation(coloredLift, uColorSaturation);
                vec3 neutralLift = vec3(1.0 - exp(-intensity * uNeutralLift));
                fragColor = vec4(coloredLift + neutralLift, 0.0);
            }
            """;
}
