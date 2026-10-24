# Profiles and capabilities

The fluent API defaults to CLASSIC at 128×128. Set `.profile(...)` and `.resolution(width, height)` independently, or supply `RenderSettings` for attachments and advanced limits. Higher quality or larger images can reduce delivered feed rate; 20 FPS remains a scheduling target.

Explicit point/spot lights and requested shadows work in every supported profile. Captured world-emitter detail still follows the existing profile. Query `renderer.client().lightingCapabilities()` for creative-light limits as well as the general capabilities below. Older providers explicitly reject unavailable controls.

Check `RendererCapabilities.supportedProfiles()` before submission. The stable names are `CLASSIC`, `CLASSIC_HQ`, `NORMAL`, `CINEMATIC`, `ULTRA`, `ULTRA_PLUS`, and `EXTREME`. A consumer should select a supported fallback rather than assume every provider exposes every profile. `nativeAvailable()` reports current runtime availability; `packagedForProduction()` reports verified package identity, not license state or general development usability. Keep operator activation failures separate from API capability handling.
