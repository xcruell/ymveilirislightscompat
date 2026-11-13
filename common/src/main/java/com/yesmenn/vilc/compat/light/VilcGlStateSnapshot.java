package com.yesmenn.vilc.compat.light;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR_CLEAR_VALUE;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glClearColor;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glGetFloatv;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_BLEND_EQUATION_ALPHA;
import static org.lwjgl.opengl.GL20C.GL_BLEND_EQUATION_RGB;
import static org.lwjgl.opengl.GL20C.glBlendEquationSeparate;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL30C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL30C.GL_TEXTURE_BINDING_3D;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glScissor;
import static org.lwjgl.opengl.GL30C.glViewport;

final class VilcGlStateSnapshot {
    private static final int SAVED_TEXTURE_UNITS = 64;

    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int program;
    private final int vertexArray;
    private final int activeTexture;
    private final boolean blend;
    private final boolean depthTest;
    private final boolean cull;
    private final boolean scissor;
    private final int blendEquationRgb;
    private final int blendEquationAlpha;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final int[] texture2d = new int[SAVED_TEXTURE_UNITS];
    private final int[] texture3d = new int[SAVED_TEXTURE_UNITS];
    private final float[] clearColor = new float[4];

    private VilcGlStateSnapshot() {
        this.drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        this.readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        this.program = glGetInteger(GL_CURRENT_PROGRAM);
        this.vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        this.activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        this.blend = glGetBoolean(GL_BLEND);
        this.depthTest = glGetBoolean(GL_DEPTH_TEST);
        this.cull = glGetBoolean(GL_CULL_FACE);
        this.scissor = glGetBoolean(GL_SCISSOR_TEST);
        this.blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        this.blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        this.blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        this.blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        this.blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        this.blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        glGetIntegerv(GL_VIEWPORT, this.viewport);
        glGetIntegerv(GL_SCISSOR_BOX, this.scissorBox);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, this.clearColor);

        int textureUnits = textureUnitCount();
        for (int unit = 0; unit < textureUnits; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            this.texture2d[unit] = glGetInteger(GL_TEXTURE_BINDING_2D);
            this.texture3d[unit] = glGetInteger(GL_TEXTURE_BINDING_3D);
        }
        glActiveTexture(this.activeTexture);
    }

    static VilcGlStateSnapshot capture() {
        return new VilcGlStateSnapshot();
    }

    int activeTexture() {
        return this.activeTexture;
    }

    int drawFramebuffer() {
        return this.drawFramebuffer;
    }

    void restore() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer);
        glUseProgram(this.program);
        glBindVertexArray(this.vertexArray);
        setEnabled(GL_BLEND, this.blend);
        setEnabled(GL_DEPTH_TEST, this.depthTest);
        setEnabled(GL_CULL_FACE, this.cull);
        setEnabled(GL_SCISSOR_TEST, this.scissor);
        glBlendEquationSeparate(this.blendEquationRgb, this.blendEquationAlpha);
        glBlendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
        glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
        glScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
        glClearColor(this.clearColor[0], this.clearColor[1], this.clearColor[2], this.clearColor[3]);

        int textureUnits = textureUnitCount();
        for (int unit = 0; unit < textureUnits; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, this.texture2d[unit]);
            glBindTexture(GL_TEXTURE_3D, this.texture3d[unit]);
        }
        glActiveTexture(this.activeTexture);
    }

    static void clearTextureUnits() {
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        int textureUnits = textureUnitCount();
        for (int unit = 0; unit < textureUnits; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, 0);
            glBindTexture(GL_TEXTURE_3D, 0);
        }
        glActiveTexture(activeTexture);
    }

    static void unbindTexture2DFromAllUnits(int texture, int activeTexture) {
        if (texture == 0) {
            return;
        }
        int textureUnits = textureUnitCount();
        for (int unit = 0; unit < textureUnits; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            if (glGetInteger(GL_TEXTURE_BINDING_2D) == texture) {
                glBindTexture(GL_TEXTURE_2D, 0);
            }
        }
        glActiveTexture(activeTexture);
    }

    private static int textureUnitCount() {
        return Math.min(SAVED_TEXTURE_UNITS, Math.max(1, glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)));
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }
}
