#!/usr/bin/env python3
"""Generate the locked Java 21 hierarchy used by the renderer API verifier."""

import argparse
import json
from pathlib import Path
import zipfile

from renderer_api_classfile import ParsedClass, ParsedMember, parse_class_file
from renderer_api_java21_provenance import (
    PINNED_JAVA_BASE_JMOD_SHA256,
    ToolchainProvenanceError,
    verify_pinned_java_home,
)


JAVA21_TYPES = (
    "java/io/Serializable",
    "java/lang/AutoCloseable",
    "java/lang/Comparable",
    "java/lang/Enum",
    "java/lang/Exception",
    "java/lang/Object",
    "java/lang/Record",
    "java/lang/Runnable",
    "java/lang/RuntimeException",
    "java/lang/Throwable",
    "java/lang/constant/Constable",
)


def _field_document(member: ParsedMember) -> dict[str, object]:
    constant = None
    if member.static and member.final and member.constant_value is not None:
        constant = {
            "kind": member.constant_value.kind,
            "value": member.constant_value.value,
        }
    return {
        "constantValue": constant,
        "descriptor": member.descriptor,
        "final": member.final,
        "name": member.name,
        "static": member.static,
        "visibility": member.visibility,
    }


def _method_document(member: ParsedMember) -> dict[str, object]:
    return {
        "abstract": member.abstract,
        "descriptor": member.descriptor,
        "final": member.final,
        "name": member.name,
        "static": member.static,
        "visibility": member.visibility,
    }


def _type_document(value: ParsedClass) -> dict[str, object]:
    return {
        "abstract": value.abstract,
        "fields": [_field_document(member) for member in value.fields],
        "final": value.final,
        "interfaces": list(value.interfaces),
        "kind": value.kind,
        "methods": [_method_document(member) for member in value.methods],
        "name": value.name,
        "superclass": value.superclass,
        "visibility": value.visibility,
    }


def generate(java_home: Path, output: Path) -> None:
    try:
        provenance, jmod = verify_pinned_java_home(java_home)
    except ToolchainProvenanceError as failure:
        raise SystemExit(str(failure)) from failure
    types: list[dict[str, object]] = []
    try:
        with zipfile.ZipFile(jmod) as archive:
            for name in JAVA21_TYPES:
                entry = f"classes/{name}.class"
                parsed = parse_class_file(archive.read(entry))
                if parsed.name != name:
                    raise SystemExit(
                        f"JDK class owner differs from module entry: {entry}"
                    )
                types.append(_type_document(parsed))
    except (OSError, KeyError, zipfile.BadZipFile) as failure:
        raise SystemExit(f"cannot inspect JDK 21 java.base module: {failure}") from failure

    document = {
        "classFileVersion": "65.0",
        "formatVersion": 2,
        "javaRelease": 21,
        "jdkProvenance": provenance,
        "sourceModule": "java.base",
        "sourceModuleSha256": PINNED_JAVA_BASE_JMOD_SHA256,
        "types": types,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(document, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    arguments = parser.parse_args()
    generate(arguments.java_home, arguments.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
