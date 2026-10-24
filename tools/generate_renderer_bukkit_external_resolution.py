#!/usr/bin/env python3
"""Extract the two reviewed Bukkit hierarchy declarations, without shipping Bukkit classes."""

import argparse
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import zipfile

from generate_renderer_java21_external_resolution import _type_document
from renderer_api_classfile import parse_class_file


PINNED_BUKKIT_ARTIFACT = {
    "groupId": "org.spigotmc",
    "artifactId": "spigot-api",
    "version": "1.21.10-R0.1-SNAPSHOT",
    "resolvedVersion": "1.21.10-R0.1-20251209.161938-19",
    "sha256": "973502a0127dbf74d3d37609f959b76435a700ff6de2126cc37aa11ce583793c",
}
PINNED_BUKKIT_TYPES = ("org/bukkit/event/Listener", "org/bukkit/map/MapRenderer")
PINNED_BUKKIT_DECLARATIONS_SHA256 = (
    "2f3a7c5e91e57389c853dd5012c7f9f776ffb85c1c3b58f72f12f5a707e3569f"
)


def declarations_digest(types: list[dict[str, object]]) -> str:
    return sha256(json.dumps(types, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def authority_document(jar: Path) -> dict[str, object]:
    payload = jar.read_bytes()
    if sha256(payload).hexdigest() != PINNED_BUKKIT_ARTIFACT["sha256"]:
        raise ValueError("Bukkit artifact SHA-256 differs from the reviewed pin")
    types = []
    with zipfile.ZipFile(BytesIO(payload)) as artifact:
        for name in PINNED_BUKKIT_TYPES:
            entry = name + ".class"
            if artifact.namelist().count(entry) != 1:
                raise ValueError(f"Bukkit artifact must contain exactly one {entry}")
            source = artifact.read(entry)
            if source[:8] != bytes.fromhex("cafebabe0000003d"):
                raise ValueError(f"Bukkit source class must use exact version 61.0: {name}")
            # The pinned dependency is Java 17. Only its in-memory header is normalized so the
            # existing Java 21 declaration parser can read its structure. No artifact bytes are
            # rewritten, and the candidate JAR parser still rejects anything except Java 21.
            parsed = parse_class_file(source[:6] + bytes.fromhex("0041") + source[8:])
            if parsed.name != name:
                raise ValueError(f"Bukkit class owner differs from its artifact entry: {name}")
            types.append(_type_document(parsed))
    if declarations_digest(types) != PINNED_BUKKIT_DECLARATIONS_SHA256:
        raise ValueError("Bukkit declarations differ from the reviewed pin")
    return {
        "formatVersion": 1,
        "artifact": dict(PINNED_BUKKIT_ARTIFACT),
        "sourceClassFileVersion": "61.0",
        "types": types,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(authority_document(args.jar), indent=2, sort_keys=True) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
