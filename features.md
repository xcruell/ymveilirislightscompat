# Veil Iris Lights Compat (VILC) – Features

Overview of all current features: what they do, how they are used, and how they work internally, with the
version each one arrived in. Kept up to date with every new or changed feature (see `change.log` for the full history).
Code paths are relative to `src/main/java/com/yesmenn/veilirislights/`.

---

## Dependencies

| Mod | Type | Version |
|---|---|---|
| Minecraft | required | 1.21.1 |
| NeoForge | required | 21.1.228 or newer |
| Veil | required | 4.5.2 or newer (since 1.5.1, before 4.1.4) |
| Iris | required | 1.8.14-beta.1 or newer |
| Sodium | required | 0.8.12-beta.1 or newer (built against 0.8.13) |
| BetterF3 | optional | debug info in BetterF3 layouts (since 1.3.0) |
| Lights? YesMenn! | optional | combined config screen, flashlight / tube / fluorescent lamp shaping |

Client-side only. Dedicated servers do not need VILC.

---

## Light pass

*Since 1.1.0 (shaderpack pass), 1.4.0 (fallback pass) · code: `compat/light/VilcSceneLightCompositor`, `mixin/iris/MixinIrisFinalCompositeBridge`, `compat/veil/mixin/MixinVeilNativeLightPass`*

- **Shaderpack pass:** after Iris has finished the level image, VILC reads Veil's light data and draws all visible
  lights in one full-screen pass. Since 1.5.1 it composites from a copy of the scene instead of GL blending into the
  8-bit target, so saturated colors are no longer capped.
- **Fallback pass** (Iris installed, shaderpacks off, since 1.4.0, restored in 1.4.6): the same lights drawn on the
  vanilla main target.
- **Veil's own light pass is skipped** while Iris is installed (since 1.5.1): Veil disables its albedo buffer under
  Iris, which caused a full-screen magenta checkerboard and a magenta hand.
- Framebuffers are recreated after window resize / fullscreen changes (since 1.4.4).
- Removed in 1.5.1: the old Veil shader-program translation into Iris G-buffer programs (1.0.0) and the bundled
  glsl-transformer library. VILC only renders lights now.

## Supported light types

*Code: `compat/light/VilcLightBatch`, `compat/light/VilcLightUniforms`*

| Veil type | Since | Notes |
|---|---|---|
| `PointLightData` | 1.1.0 | point-light budget |
| `AreaLightData` | 1.1.0 | rectangle emitter since 1.3.2, area-light budget |
| `SpotLightData` | 1.6.0 | shares the area-light budget, round lens |
| `DirectionalLightData` | – | not rendered |

**Area light angles:**
- Cone angles below 180 degrees: cone with per-light edge softness and focus (1.4.2).
- 180 degrees: fades out towards the back like Veil (smoothstep over the angle to the normal, since 1.6.1); two
  lights back to back give an even line light.
- 360 degrees (2 pi): no cone, shines evenly all around the rectangle (since 1.6.1), so a line light needs only one light.

## Surface lighting

*Since 1.1.0 · code: shader in `compat/light/VilcSceneLightCompositor`*

- World positions reconstructed from the depth buffer, surface normals from screen-space neighbours; angle-aware
  diffuse response without a hard Lambert cutoff.
- Area lights light from the nearest point of their rectangle (1.3.2), spot lights from their round lens (1.6.0)
  or capsule lens (1.6.1).
- Normals: precise neighbour sampling within 64 blocks (the "Detailed normals" option was removed in 1.6.0);
  depth edges use the closer neighbour, which fixes dark outlines around foliage and flowers (1.6.0).
- Color handling: perceptual RGB luminance normalization, saturation control, texture-preserving tone mapping.
  Since 1.6.0 each light is compressed on its own before accumulation, so overlapping colors mix properly
  (red + blue = magenta).
- First-person hand: since 1.6.0 detected pixel-exactly via Iris' pre-hand depth buffer; the hand is lit except by
  lights with `skipFirstPerson`.

## Light beams (in-scattering)

*Since 1.6.0 · code: `compat/light/VilcSceneLightCompositor`, `config/LightRenderConfig`*

- Veil's in-scattering strength of point, area and spot lights is rendered as visible light in the air
  (dithered raymarch, only where the view ray crosses a light's range).
- Separate half-resolution beam pass, bilinear upsampling with a slight 3x3 blur in the composite.
- Rays start at the real (view-bobbed) eye position, so beams do not sway while walking.
- Spot lenses shrink to their center point for beams (avoids "snow" with flashlights); capsule lenses to their
  center line, area-light rectangles stay whole (since 1.6.1, before: center point).
- Voxel shadows inside beams on "High" beam quality only.
- Options: "Light beams" on/off, "Light beam quality" Low / Medium / High = 8 / 16 / 32 steps (default on, Medium).
  No cost when no light has in-scattering.

## Voxel shadows

*Since 1.1.0 · code: `compat/veil/mixin/MixinVoxelShadowGrid`, `compat/veil/VoxelShadowGridState`*

- Uses Veil's camera-centered 64 x 64 x 64 block grid; DDA ray per light and pixel.
- Leaves cast partial shadows (light transmission).
- Since 1.6.0 the option is a master switch: every Veil light casts shadows, regardless of its own occlusion flag.
- Shadow rays are skipped for invisible contributions below ~0.2 % (1.6.0).
- Light blocks can opt out of self-occlusion via the tag `#veil_iris_lights:non_occluding` (1.2.0; ships optional
  entries for the L?YM! light blocks, `yesmenn:` and `lightsyesmenn:` IDs) or `api/VeilIrisLightOcclusion.registerNonOccluding(...)`.
- 1.6.0: the GL pixel-unpack state is reset around Veil's grid setup (fixes a driver crash with shaderpacks).

## Configuration

*Since 1.1.0 · code: `config/LightRenderConfig`, `client/screen/LightRenderConfigScreen`, file `config/veil_iris_lights.json`*

Opened from the mod list or with `O` (default key).

| Preset (1.2.0) | Point lights | Area lights | Voxel shadows |
|---|---:|---:|---|
| Performance | 24 | 8 | off |
| Balanced | 48 | 16 | on |
| High (default) | 96 | 32 | on |

- Custom state when settings differ from a preset (1.3.0). Budgets: 1–96 point and 1–96 area lights.
- Appearance (live): exposure (up to 2.00), color strength (up to 12.00), color saturation, neutral brightness,
  color luminance limit. Reset only restores appearance values.
- Light beams on/off and beam quality (1.6.0).

## Debug info

*Since 1.1.0 (F3), 1.3.0 (BetterF3) · code: `mixin/betterf3/MixinBetterF3DebugRenderer`*

F3 / BetterF3 show: whether the VILC pass is active, visible point/area lights vs. budgets, quality preset, voxel
occlusion state, appearance values.

## Addon API

*Code: `compat/light/AreaLightShapeRegistry`, `client/screen/ConfigScreenBridge`*

| Call | Since | Effect |
|---|---|---|
| `AreaLightShapeRegistry.set(AreaLightData, softness, focus)` | 1.4.2 | per-light cone edge softness and focus |
| `AreaLightShapeRegistry.set(SpotLightData, focus, lensRadius)` | 1.6.0 | beam focus and round lens radius; soft edge from Veil's spot angle |
| `AreaLightShapeRegistry.set(SpotLightData, focus, lensRadius, lensHalfLength)` | 1.6.1 | capsule lens along the light's local x axis (e.g. fluorescent tubes) |
| `AreaLightShapeRegistry.setExtras(LightData, skipFirstPerson, chromaticFringe)` | 1.6.0 | skip lighting the first-person hand (flashlights), chromatic fringe at the cone edge |
| `ConfigScreenBridge.registerCombinedScreen(...)` | 1.4.3, public in 1.5.1 | mod list entry and config key open the addon's combined screen |
| `VeilIrisLightOcclusion.registerNonOccluding(id)` | 1.2.0 | block does not occlude its own light |

Standard Veil lights need no dependency on VILC; lights without registered shaping use default behavior.
