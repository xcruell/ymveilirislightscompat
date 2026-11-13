# YM Veil Iris Lights Compat

Client-side NeoForge 1.21.1 mod that renders Veil point and area lights while an
Iris shaderpack is active.

Veil lights normally do not show up once a shaderpack takes over rendering. VILC
reads Veil's light data and draws it in its own full-screen light pass after
Iris has finished the level image. Without a shaderpack, VILC provides an
equivalent fallback pass on the vanilla main target.

VILC does not translate or run Veil shader programs inside Iris.

## Integration

Mods using Veil's standard `PointLightData` and `AreaLightData` are picked up
automatically. No direct dependency on this mod's Java classes is required.

Blocks that should not occlude a light placed inside themselves can be added to:

```text
#ymvilc:non_occluding
```

The tag can be supplied by a mod or datapack. Client mods can also register a
block directly when tags are unsuitable:

```java
VeilIrisLightOcclusion.registerNonOccluding(
    ResourceLocation.fromNamespaceAndPath("example", "light_block")
);
```

Custom Veil light renderer types outside the standard point and area light APIs
are not handled.

## Rendering Quality

- `Performance`: derivative normals and no voxel-shadow tracing.
- `Balanced`: derivative normals with voxel shadows.
- `High`: detailed depth-based normals with voxel shadows.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.228 or newer
- Iris 1.8.14-beta.1 or newer
- Sodium 0.8.12-beta.1 or newer
- Veil 4.5.2 or newer
