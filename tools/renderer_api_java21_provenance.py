"""Repository-owned provenance pins for renderer API Java 21 authority."""

from hashlib import sha256
from pathlib import Path


PINNED_JDK_PROVENANCE = {
    "implementor": "Homebrew",
    "implementorVersion": "Homebrew",
    "javaRuntimeVersion": "21.0.9",
    "javaVersion": "21.0.9",
    "javaVersionDate": "2025-10-21",
    "releaseSha256": (
        "996a8728d8ad80e1ae4ec819084e2c4ac"
        "add5831088632b648a083f736a4ecf3"
    ),
}
PINNED_JAVA_BASE_JMOD_SHA256 = (
    "a7f0120eb6842f8f2b553a53a0605bc06e9e2df2fc16ee059c5b84571b80d8d7"
)


class ToolchainProvenanceError(ValueError):
    """Raised when authority regeneration does not use the reviewed JDK."""


def _digest(payload: bytes) -> str:
    return sha256(payload).hexdigest()


def _digest_path(path: Path) -> str:
    digest = sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _release_properties(payload: bytes) -> dict[str, str]:
    try:
        contents = payload.decode("utf-8")
    except UnicodeDecodeError as failure:
        raise ToolchainProvenanceError(
            "JDK release metadata is not UTF-8"
        ) from failure
    properties: dict[str, str] = {}
    for line in contents.splitlines():
        if not line or "=" not in line:
            raise ToolchainProvenanceError("JDK release metadata is malformed")
        key, raw_value = line.split("=", 1)
        if not key or key in properties or len(raw_value) < 2 \
                or not raw_value.startswith('"') or not raw_value.endswith('"'):
            raise ToolchainProvenanceError("JDK release metadata is malformed")
        properties[key] = raw_value[1:-1]
    return properties


def verify_pinned_java_home(java_home: Path) -> tuple[dict[str, str], Path]:
    """Return recorded metadata and jmod only for the reviewed exact JDK input."""
    release_path = java_home / "release"
    try:
        release_payload = release_path.read_bytes()
    except OSError as failure:
        raise ToolchainProvenanceError(
            f"cannot read JDK release metadata: {failure}"
        ) from failure
    release_digest = _digest(release_payload)
    if release_digest != PINNED_JDK_PROVENANCE["releaseSha256"]:
        raise ToolchainProvenanceError(
            "unpinned JDK release metadata: expected SHA-256 "
            f"{PINNED_JDK_PROVENANCE['releaseSha256']}, got {release_digest}; "
            "update the reviewed provenance pins before regeneration"
        )
    properties = _release_properties(release_payload)
    actual_provenance = {
        "implementor": properties.get("IMPLEMENTOR"),
        "implementorVersion": properties.get("IMPLEMENTOR_VERSION"),
        "javaRuntimeVersion": properties.get("JAVA_RUNTIME_VERSION"),
        "javaVersion": properties.get("JAVA_VERSION"),
        "javaVersionDate": properties.get("JAVA_VERSION_DATE"),
        "releaseSha256": release_digest,
    }
    if actual_provenance != PINNED_JDK_PROVENANCE:
        raise ToolchainProvenanceError(
            "unpinned JDK release provenance; update the reviewed provenance pins "
            "before regeneration"
        )

    jmod = java_home / "jmods" / "java.base.jmod"
    try:
        jmod_digest = _digest_path(jmod)
    except OSError as failure:
        raise ToolchainProvenanceError(
            f"cannot read JDK 21 java.base.jmod: {failure}"
        ) from failure
    if jmod_digest != PINNED_JAVA_BASE_JMOD_SHA256:
        raise ToolchainProvenanceError(
            "unpinned java.base.jmod SHA-256: expected "
            f"{PINNED_JAVA_BASE_JMOD_SHA256}, got {jmod_digest}; "
            "update the reviewed provenance pins before regeneration"
        )
    return dict(PINNED_JDK_PROVENANCE), jmod
