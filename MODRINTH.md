# Veil Iris Lights Compat

**Client-side Veil light rendering for Iris shaderpacks.**

> **Minecraft 1.21.1** | **NeoForge 21.1.228+** | **Client-side only** | **Current version: 1.6.1**

Veil Iris Lights Compat, shortened to **VILC**, keeps dynamic Veil point, spot, and area lights visible while Iris is installed. With a shaderpack enabled, VILC draws Veil's light data in its own light pass after Iris has finished the level image. With shaderpacks disabled, it renders the same lights in a fallback pass.

VILC does not translate or run other Veil shader effects inside Iris.

VILC does not add blocks, items, mobs, or gameplay content. It is a rendering compatibility mod for other mods that already use Veil, including **Lights? YesMenn!**, **Nox Lights**, and other standard Veil light sources.

## What It Does

- Renders standard Veil point lights with Iris shaderpacks
- Renders standard Veil spot lights with Iris shaderpacks
- Renders standard Veil rectangular area lights with Iris shaderpacks
- Supports cone-shaped area lights, 180 degree area lights with a soft back falloff like Veil, and 360 degree line lights that shine evenly all around
- Renders Veil's in-scattering as visible light beams in the air, with optional voxel shadows
- Supports moving and rotating lights when the source mod updates their transform
- Supports multiple differently colored lights in the same scene
- Keeps saturated colors strong: a pure red light stays red instead of washing out
- Reconstructs world positions and surface normals from depth data
- Adds diffuse, angle-aware light response on lit surfaces
- Supports configurable voxel occlusion and block shadows
- Preserves visible texture detail under bright colored lighting
- Live appearance controls for light tuning
- Fallback pass when Iris is installed but shaderpacks are disabled
- Recovers light-pass framebuffers after window resize and fullscreen changes

## Why VILC Is Needed

Veil turns off the buffers its own light renderer depends on as soon as Iris is installed, even with shaderpacks disabled. Without VILC, Veil lights either disappear under a shaderpack or show up as a magenta checkerboard pattern on screen and on the player's hand. VILC skips Veil's broken light pass in that situation and renders the lights itself.

## Dynamic Light Support

VILC works with Veil's standard light data:

- `PointLightData`
- `SpotLightData`
- `AreaLightData`

Veil's `DirectionalLightData` is not rendered by VILC yet.

For compatible mods, spot and area lights can also expose extra beam shaping:

- Beam angle
- Edge softness
- Focus
- Lens or emitter size
- Long capsule lenses, for example fluorescent tubes that light from their whole length
- Direction and rotation

These controls are useful for stage lights, searchlights, decorative RGB spotlights, live show setups, technical builds, and other scenes where a light should behave like a directed beam rather than a glowing point.

## Configuration

Open the configuration from the mod list or with `O` by default.

If **Lights? YesMenn!** is installed as well, both entry points open a combined settings screen with VILC's rendering options and the lamp color settings of Lights? YesMenn!.

Configuration is stored in:

```text
config/veil_iris_lights.json
```

## Quality Presets

| Preset | Point Lights | Area Lights | Voxel Shadows |
|---|---:|---:|---|
| **Performance** | 24 | 8 | Disabled |
| **Balanced** | 48 | 16 | Enabled |
| **High** | 96 | 32 | Enabled |

Every major performance setting can also be tuned manually:

- Point-light budget from 1 to 96
- Area-light budget from 1 to 96
- Voxel shadow toggle (applies to every Veil light)
- Light beams on or off
- Light beam quality: Low, Medium or High (8, 16 or 32 steps; shadows inside beams on High only)

Appearance controls update live:

- Exposure
- Color strength
- Color saturation
- Neutral brightness
- Color luminance limit

The Reset button restores the appearance controls without wiping custom light budgets and quality options.

## Debugging

VILC adds render information to F3 and BetterF3:

- Whether the VILC light pass is active
- Visible point and area lights compared to the configured light budgets
- Current quality preset
- Voxel occlusion state
- Current appearance values

This helps to check whether the VILC pass is running, whether a scene has reached its light budget, or whether voxel shadows are currently active.

## Requirements

| Dependency | Requirement |
|---|---|
| **Minecraft** | 1.21.1 |
| **NeoForge** | 21.1.228 or newer |
| **Iris** | 1.8.14 beta 1 or newer |
| **Veil** | 4.5.2 or newer |
| **Sodium** | 0.8.12 beta 1 or newer |

Install VILC only on clients. Dedicated servers do not need it.

## Mod Developer Support

Mods using Veil's standard `PointLightData`, `SpotLightData` and `AreaLightData` are picked up automatically. A direct dependency on VILC is not required for standard lights.

Mods can optionally use VILC's light shape support (`AreaLightShapeRegistry`): area lights get per-light edge softness and focus, spot lights get focus, a lens radius and optionally a lens half length that stretches the lens into a capsule (their soft edge comes from Veil's own spot angle). `AreaLightShapeRegistry.setExtras(...)` lets a light skip the first-person hand and held item (for example flashlights) and adds a chromatic fringe at the cone edge. Area lights with an angle of 360 degrees shine evenly all around their rectangle. Lights without extra shaping render with the default behavior.

Blocks that contain their own light source can opt out of self-occlusion with the tag:

```text
#veil_iris_lights:non_occluding
```

Client mods may also register non-occluding blocks directly:

```java
VeilIrisLightOcclusion.registerNonOccluding(
    ResourceLocation.fromNamespaceAndPath("example", "light_block")
);
```

Addons that show VILC's options in their own settings screen can register it with `ConfigScreenBridge.registerCombinedScreen(...)`. VILC's mod list entry and config key then open that combined screen.

Directional lights and custom Veil light renderer types outside the standard point, spot, and area APIs may need separate integration.

## Compatibility Notes

- Shaderpacks differ heavily, so unusual pipelines may still need specific compatibility work
- VILC is client-side and does not change server behavior
- Voxel shadows use a camera-centered 64 x 64 x 64 block grid
- Complex block models are approximated for occlusion
- Leaves use partial voxel transmission rather than full texture-alpha sampling
- Occlusion outside the active voxel grid cannot be perfectly reconstructed
- Light budgets limit how many visible Veil lights are processed per frame; spot lights share the area-light budget
- With a shaderpack, the first-person hand and held items are lit by VILC unless a light opts out (for example flashlights)
- While Iris is installed, VILC replaces Veil's own light pass in both the shaderpack and the fallback pass
- Visual beam meshes from other mods are still controlled by those mods

## Works Well With

- **Lights? YesMenn!** for configurable RGB blocks, panels, tubes, real Veil spotlights, invisible lights, and light tooling
- **Nox Lights** and other mods using standard Veil point or area lights
- **BetterF3** for extended debug layouts

## What's New in 1.6.1

- Line lights: area lights with 360 degrees shine evenly all around, 180 degree area lights fade out towards their back like in Veil
- Light beams of area lights now come from the whole light surface instead of its center point
- Long capsule lenses for spot lights, for example fluorescent tubes in Lights? YesMenn!

## What's New in 1.6.0

- Veil spot lights are now rendered with Iris, with and without a shaderpack
- Visible light beams (Veil in-scattering) with on/off toggle and quality setting
- Overlapping colored lights mix properly (red + blue = magenta)
- The voxel shadow option now works for all Veil lights
- Round spot light lenses, optional focus, lens size and chromatic fringe for compatible mods
- Flashlights from compatible mods no longer light the first-person hand
- Fixed dark outlines around foliage and flowers, and a crash when enabling shadows with a shaderpack active
- Better performance with many lights and wide light beams

## What's New in 1.5.1

- Updated to Veil 4.5.2
- Fixed the full-screen magenta checkerboard and magenta first-person hand with Iris installed
- Much stronger saturated light colors with shaderpacks
- Fixed a thin dark ring that could appear around the player
- Combined settings screen together with Lights? YesMenn!
- Shaderpack detection uses the official Iris API
- Smaller download: removed an unused bundled library

## License

VILC is released under the **YesMenn Custom License 1.0**:

- Free to use for personal, educational, and non-commercial purposes
- Free modpacks may include official releases, with credit to xcruell and YesMenn and a link to this page
- No paywalled, subscription, or paid modpacks
- No reuploads, mirrors, or modified builds without permission
- Commercial use only with prior written permission, so please ask first

Other mods may depend on VILC and build compatibility addons. The full license text is included in the mod jar and the source repository.

---

Made by **xcruell** and **YesMenn**
