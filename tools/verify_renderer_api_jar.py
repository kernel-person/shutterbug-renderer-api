#!/usr/bin/env python3
"""Fail closed unless a packaged renderer-api JAR has the exact v1 boundary."""

from pathlib import Path
import sys
import zipfile

from renderer_api_classfile import (
    ParsedClass,
    parse_class_file,
    unsafe_zip_entry,
    validate_local_nests,
)


API_PREFIX = "ke/ric/renderer/api/"
API_TYPES = frozenset({
    "AssetHandle", "AssetPack", "Attachment", "CaptureRequest", "ChunkSectionState",
    "MapDither", "MinecraftMapConverter", "PngConverter", "RenderFailureCode",
    "RenderJob", "RenderProgress", "RenderResult", "RenderSettings", "RenderStatus",
    "RenderTask", "RenderTimings", "RendererCapabilities", "RendererClient",
    "RendererException", "RendererService", "ResolvedLabel", "ResolvedMaterial",
    "ResolvedMesh", "ResolvedRenderTarget", "ResolvedTexture", "ResolvedTransform",
    "ResolvedTriangle", "Scene", "SceneBuilder", "SceneEntity", "SceneStructure",
    "VisibilityStatistics",
})
EXPOSED_NESTED_TYPES = frozenset({
    "RenderProgress$Phase", "RenderSettings$Profile", "ResolvedMaterial$AlphaMode",
    "ResolvedTriangle$Face", "SceneEntity$Builder",
})
ALLOWED_RESOURCES = frozenset({
    "META-INF/MANIFEST.MF",
    "META-INF/maven/com.github.kernel-person/shutterbug-renderer-api/pom.xml",
    "META-INF/maven/com.github.kernel-person/shutterbug-renderer-api/pom.properties",
})
ALLOWED_DIRECTORIES = frozenset({
    "META-INF/", "META-INF/maven/", "META-INF/maven/com.github.kernel-person/",
    "META-INF/maven/com.github.kernel-person/shutterbug-renderer-api/", "ke/", "ke/ric/",
    "ke/ric/renderer/", "ke/ric/renderer/api/",
})
FORBIDDEN_LICENSING_TOKENS = (
    b"entitlement", b"license", b"licensing", b"lukittu", b"heartbeat",
)
FORBIDDEN_CLASS_REFERENCES = (
    b"ke/ric/render/", b"ke.ric.render.",
    b"ke/ric/resource/", b"ke.ric.resource.",
    b"ke/ric/renderer/provider/", b"ke.ric.renderer.provider.",
    b"ke/ric/shutterbug", b"ke.ric.shutterbug",
    b"org/bukkit/craftbukkit/", b"io/papermc/", b"net/minecraft/",
    b"nativebridge", b"native_handle", b"nativehandle", b"classicscene",
    b"encodedbuffer", b"allocatedirect", b"directbytebuffer",
    b"profileparameters", b"boundedarchive",
)


def fail(message: str) -> int:
    print(message, file=sys.stderr)
    return 1


def _parse_class(
    payload: bytes,
) -> tuple[ParsedClass, tuple[str | None, str | None, int] | None]:
    """Return parsed class data and its normalized self InnerClasses relation."""
    parsed = parse_class_file(payload)
    membership = parsed.self_inner
    self_inner = None if membership is None else (
        membership.outer, membership.inner_name, membership.access,
    )
    return parsed, self_inner


def verify(jar: Path) -> int:
    if not jar.is_file():
        return fail(f"renderer-api JAR does not exist: {jar}")
    try:
        with zipfile.ZipFile(jar) as artifact:
            entries = artifact.infolist()
            names = [entry.filename for entry in entries]
            if len(names) != len(set(names)):
                return fail("renderer-api JAR contains duplicate entries")
            top_level_types: set[str] = set()
            exposed_nested_types: set[str] = set()
            parsed_classes: dict[str, ParsedClass] = {}
            for entry in entries:
                name = entry.filename
                lowered_name = name.encode("utf-8", "replace").lower()
                if unsafe_zip_entry(name):
                    return fail(f"renderer-api JAR contains unsafe entry: {name}")
                if entry.flag_bits & 1 or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                    return fail(f"renderer-api JAR contains unsafe entry: {name}")
                if name.startswith("META-INF/services/"):
                    return fail(f"renderer-api JAR contains service provider resource: {name}")
                if any(token in lowered_name for token in FORBIDDEN_LICENSING_TOKENS):
                    return fail(f"renderer-api JAR contains forbidden licensing entry: {name}")
                if entry.is_dir():
                    if name not in ALLOWED_DIRECTORIES or entry.file_size != 0:
                        return fail(f"renderer-api JAR contains unexpected directory: {name}")
                    continue
                if not name.endswith(".class") and name not in ALLOWED_RESOURCES:
                    return fail(f"renderer-api JAR contains unexpected resource: {name}")
                contents = artifact.read(entry)
                lowered_content = contents.lower()
                if name not in ALLOWED_RESOURCES and any(token in lowered_content for token in FORBIDDEN_LICENSING_TOKENS):
                    return fail(f"renderer-api JAR contains forbidden licensing surface: {name}")
                if not name.endswith(".class"):
                    continue
                if not name.startswith(API_PREFIX):
                    return fail(f"renderer-api JAR contains application class outside API package: {name}")
                relative = name[len(API_PREFIX):-len(".class")]
                if not relative or "/" in relative:
                    return fail(f"renderer-api JAR contains class in a non-v1 API package: {name}")
                outer = relative.split("$", 1)[0]
                if outer not in API_TYPES:
                    return fail(f"renderer-api JAR contains unexpected API type: {name}")
                if any(token in lowered_name for token in FORBIDDEN_CLASS_REFERENCES):
                    return fail(f"renderer-api JAR contains forbidden implementation class: {name}")
                if any(token in lowered_content for token in FORBIDDEN_CLASS_REFERENCES):
                    return fail(f"renderer-api JAR contains forbidden implementation reference: {name}")
                try:
                    parsed, self_inner = _parse_class(contents)
                except ValueError as failure:
                    return fail(f"renderer-api JAR contains malformed class: {name}: {failure}")
                internal_name = parsed.name
                class_access = parsed.class_access
                nest_host = parsed.nest_host
                expected_name = name[:-len(".class")]
                if internal_name != expected_name:
                    return fail("renderer-api JAR class owner differs from its entry: "
                                f"{name} declares {internal_name}")
                parsed_classes[internal_name] = parsed
                if "$" not in relative:
                    if self_inner is not None or nest_host is not None:
                        return fail("renderer-api JAR top-level API type declares nested self "
                                    f"membership: {name}")
                    if not class_access & 0x0001:
                        return fail(f"renderer-api JAR top-level API type is not public: {name}")
                    top_level_types.add(outer)
                    continue
                if self_inner is None:
                    return fail("renderer-api JAR nested API type lacks self InnerClasses "
                                f"membership: {name}")
                expected_nest_host = API_PREFIX + outer
                if nest_host != expected_nest_host:
                    return fail("renderer-api JAR nested API type has the wrong NestHost: "
                                f"{name}: expected {expected_nest_host}, got {nest_host}")
                direct_outer, binary_suffix = relative.rsplit("$", 1)
                self_outer, inner_name, inner_access = self_inner
                expected_direct_outer = API_PREFIX + direct_outer
                if self_outer is None:
                    digit_count = 0
                    while digit_count < len(binary_suffix) \
                            and binary_suffix[digit_count].isdigit():
                        digit_count += 1
                    expected_local_name = binary_suffix[digit_count:] or None
                    if digit_count == 0 or inner_name != expected_local_name:
                        return fail("renderer-api JAR nested API type has invalid anonymous/local "
                                    f"self membership: {name}")
                elif self_outer != expected_direct_outer or inner_name != binary_suffix:
                    return fail("renderer-api JAR nested API type has mismatched self membership: "
                                f"{name}: outer={self_outer}, inner={inner_name}")
                if relative in EXPOSED_NESTED_TYPES:
                    if class_access & 0x0007 != 0x0001:
                        return fail("renderer-api JAR required nested API type class visibility "
                                    f"is not public: {name}")
                    if inner_access & 0x0007 != 0x0001:
                        return fail("renderer-api JAR required nested API type self InnerClasses "
                                    f"visibility is not public: {name}")
                    exposed_nested_types.add(relative)
                elif inner_access & 0x0005:
                    return fail("renderer-api JAR contains unexpected public/protected "
                                f"nested API type: {name}")
            try:
                validate_local_nests(parsed_classes)
            except ValueError as failure:
                return fail(
                    f"renderer-api JAR contains invalid local nest relationships: {failure}"
                )
            missing = sorted(API_TYPES - top_level_types)
            unexpected = sorted(top_level_types - API_TYPES)
            if missing or unexpected:
                return fail("renderer-api JAR top-level API differs: "
                            f"missing={missing}, unexpected={unexpected}")
            missing_nested = sorted(EXPOSED_NESTED_TYPES - exposed_nested_types)
            unexpected_nested = sorted(exposed_nested_types - EXPOSED_NESTED_TYPES)
            if missing_nested or unexpected_nested:
                return fail("renderer-api JAR public/protected nested API differs: "
                            f"missing={missing_nested}, unexpected={unexpected_nested}")
    except (OSError, zipfile.BadZipFile, RuntimeError) as failure:
        return fail(f"renderer-api JAR cannot be inspected: {jar}: {failure}")
    print(f"Verified exact renderer-api v1 boundary: {jar}")
    return 0


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        return fail("usage: verify_renderer_api_jar.py <renderer-api.jar>")
    return verify(Path(argv[1]))


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
