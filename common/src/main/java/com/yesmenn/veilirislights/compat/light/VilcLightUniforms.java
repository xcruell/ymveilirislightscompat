package com.yesmenn.veilirislights.compat.light;

import static org.lwjgl.opengl.GL20C.glGetUniformLocation;

record VilcLightUniforms(
        int depth,
        int blockGrid,
        int inverseViewProjection,
        int cameraPosition,
        int inverseViewSize,
        int inverseDepthSize,
        int gridOrigin,
        int hasBlockGrid,
        int detailedNormals,
        int colorStrength,
        int colorSaturation,
        int neutralLift,
        int luminanceBoostLimit,
        int firstPersonAdditiveEnabled,
        int firstPersonPass,
        int depthUvScale,
        int pointCount,
        int pointPositionRadius,
        int pointColor,
        int areaCount,
        int areaPositionDistance,
        int areaDirectionAngle,
        int areaRightSize,
        int areaUpSize,
        int areaColor,
        int areaShape) {

    static VilcLightUniforms fromProgram(int program) {
        return new VilcLightUniforms(
                glGetUniformLocation(program, "uDepth"),
                glGetUniformLocation(program, "uBlockGrid"),
                glGetUniformLocation(program, "uInverseViewProjection"),
                glGetUniformLocation(program, "uCameraPosition"),
                glGetUniformLocation(program, "uInverseViewSize"),
                glGetUniformLocation(program, "uInverseDepthSize"),
                glGetUniformLocation(program, "uGridOrigin"),
                glGetUniformLocation(program, "uHasBlockGrid"),
                glGetUniformLocation(program, "uDetailedNormals"),
                glGetUniformLocation(program, "uColorStrength"),
                glGetUniformLocation(program, "uColorSaturation"),
                glGetUniformLocation(program, "uNeutralLift"),
                glGetUniformLocation(program, "uLuminanceBoostLimit"),
                glGetUniformLocation(program, "uFirstPersonAdditiveEnabled"),
                glGetUniformLocation(program, "uFirstPersonPass"),
                glGetUniformLocation(program, "uDepthUvScale"),
                glGetUniformLocation(program, "uPointCount"),
                glGetUniformLocation(program, "uPointPositionRadius"),
                glGetUniformLocation(program, "uPointColor"),
                glGetUniformLocation(program, "uAreaCount"),
                glGetUniformLocation(program, "uAreaPositionDistance"),
                glGetUniformLocation(program, "uAreaDirectionAngle"),
                glGetUniformLocation(program, "uAreaRightSize"),
                glGetUniformLocation(program, "uAreaUpSize"),
                glGetUniformLocation(program, "uAreaColor"),
                glGetUniformLocation(program, "uAreaShape"));
    }
}
