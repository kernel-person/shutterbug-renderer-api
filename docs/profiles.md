# Profiles and capabilities

Check `RendererCapabilities.supportedProfiles()` before submission. The stable names are `CLASSIC`, `CLASSIC_HQ`, `NORMAL`, `CINEMATIC`, `ULTRA`, `ULTRA_PLUS`, and `EXTREME`. A consumer should select a supported fallback rather than assume every provider exposes every profile. `nativeAvailable()` reports current runtime availability; `packagedForProduction()` reports verified package identity, not license state or general development usability. Keep operator activation failures separate from API capability handling.
