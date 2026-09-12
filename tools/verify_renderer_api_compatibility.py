#!/usr/bin/env python3
"""Verify the normalized Java 21 binary surface of the renderer API."""

import json
import os
from pathlib import Path
import sys
import tempfile
from typing import Any

from renderer_api_classfile import (
    ClassFileError,
    InnerClassMembership,
    ParsedClass,
    ParsedConstantValue,
    ParsedMember,
    read_class_jar,
    validate_local_nests,
    validate_field_descriptor,
    validate_internal_name,
    validate_method_descriptor,
)
from renderer_api_java21_provenance import (
    PINNED_JAVA_BASE_JMOD_SHA256,
    PINNED_JDK_PROVENANCE,
)


FORMAT_VERSION = 4
CLASS_FILE_VERSION = "65.0"
VISIBILITIES = frozenset({"public", "protected", "package", "private"})
KINDS = frozenset({"class", "interface", "enum", "record"})
TYPE_KEYS = frozenset({
    "abstract", "fields", "final", "interfaces", "kind", "methods", "name",
    "superclass", "visibility",
})
RESOLUTION_TYPE_KEYS = TYPE_KEYS | frozenset({
    "nestHost", "nestMembers", "outer",
})
FIELD_KEYS = frozenset({
    "constantValue", "descriptor", "final", "name", "static", "visibility",
})
METHOD_KEYS = frozenset({
    "abstract", "descriptor", "final", "name", "static", "visibility",
})
JAVA21_AUTHORITY_PATH = Path(__file__).with_name(
    "renderer-api-java21-external-resolution.json"
)
EXTERNAL_AUTHORITY_DOCUMENTS: dict[str, dict[str, Any]] = {}
EXTERNAL_AUTHORITY_CLASSES: dict[str, ParsedClass] = {}
OBJECT_INSTANCE_METHODS: dict[tuple[str, str], ParsedMember] = {}


class BaselineError(ValueError):
    pass


def _strict_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, member in pairs:
        if key in value:
            raise BaselineError(f"duplicate JSON object key: {key}")
        value[key] = member
    return value


def _contract_member(member: ParsedMember) -> bool:
    return member.visibility in {"public", "protected"}


def _field_document(member: ParsedMember) -> dict[str, Any]:
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


def _method_document(member: ParsedMember) -> dict[str, Any]:
    return {
        "abstract": member.abstract,
        "descriptor": member.descriptor,
        "final": member.final,
        "name": member.name,
        "static": member.static,
        "visibility": member.visibility,
    }


def _type_document(
    value: ParsedClass,
    *,
    contract_only: bool,
    resolution_metadata: bool = False,
) -> dict[str, Any]:
    fields = value.fields
    methods = value.methods
    if contract_only:
        fields = tuple(member for member in fields if _contract_member(member))
        methods = tuple(member for member in methods if _contract_member(member))
    document = {
        "abstract": value.abstract,
        "fields": [_field_document(member) for member in fields],
        "final": value.final,
        # Direct-interface order is resolution-significant for fields. The
        # reviewed contract list stays normalized; resolutionTypes preserves
        # the raw class-file order used by the JVM.
        "interfaces": sorted(value.interfaces) if contract_only else list(value.interfaces),
        "kind": value.kind,
        "methods": [_method_document(member) for member in methods],
        "name": value.name,
        "superclass": value.superclass,
        "visibility": value.visibility,
    }
    if resolution_metadata:
        document["nestHost"] = value.nest_host
        document["nestMembers"] = sorted(value.nest_members)
        document["outer"] = (
            value.self_inner.outer if value.self_inner is not None else None
        )
    return document


def _externally_accessible_type(
    classes: dict[str, ParsedClass],
    value: ParsedClass,
    visiting: set[str] | None = None,
) -> bool:
    if not value.public_or_protected():
        return False
    if value.self_inner is None or value.self_inner.outer is None:
        return True
    visiting = set() if visiting is None else visiting
    if value.name in visiting:
        raise ClassFileError(f"cyclic InnerClasses ownership: {value.name}")
    outer = classes.get(value.self_inner.outer)
    if outer is None:
        raise ClassFileError(
            f"public/protected nested type lacks its outer class: {value.name}"
        )
    return _externally_accessible_type(classes, outer, visiting | {value.name})


def _parsed_resolution_references(value: ParsedClass) -> tuple[str, ...]:
    references: list[str] = []
    if value.superclass is not None:
        references.append(value.superclass)
    references.extend(value.interfaces)
    if value.self_inner is not None and value.self_inner.outer is not None:
        references.append(value.self_inner.outer)
    if value.nest_host is not None:
        references.append(value.nest_host)
    references.extend(value.nest_members)
    return tuple(dict.fromkeys(references))


def _reachable_local_classes(
    classes: dict[str, ParsedClass],
    roots: list[str],
) -> set[str]:
    reachable: set[str] = set()
    pending = list(roots)
    while pending:
        name = pending.pop(0)
        if name in reachable:
            continue
        value = classes.get(name)
        if value is None:
            if name not in EXTERNAL_AUTHORITY_CLASSES:
                raise ClassFileError(
                    f"unsupported external resolution type: {name}"
                )
            continue
        reachable.add(name)
        for reference in _parsed_resolution_references(value):
            if reference in classes:
                pending.append(reference)
            elif reference not in EXTERNAL_AUTHORITY_CLASSES:
                raise ClassFileError(
                    f"unsupported external resolution type: {reference}"
                )
    return reachable


def surface_document(classes: dict[str, ParsedClass]) -> dict[str, Any]:
    contract_values = [
        value
        for value in classes.values()
        if _externally_accessible_type(classes, value)
    ]
    reachable = _reachable_local_classes(
        classes, [value.name for value in contract_values]
    )
    types = [
        _type_document(value, contract_only=True)
        for value in contract_values
    ]
    return {
        "classFileVersion": CLASS_FILE_VERSION,
        "formatVersion": FORMAT_VERSION,
        "resolutionTypes": [
            _type_document(
                classes[name],
                contract_only=False,
                resolution_metadata=True,
            )
            for name in sorted(reachable)
        ],
        "types": types,
    }


def _expect_object(value: Any, context: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise BaselineError(f"{context} must be an object")
    return value


def _expect_exact_keys(value: dict[str, Any], keys: frozenset[str], context: str) -> None:
    actual = frozenset(value)
    if actual != keys:
        raise BaselineError(
            f"{context} keys differ: missing={sorted(keys - actual)}, "
            f"unexpected={sorted(actual - keys)}"
        )


def _expect_string(value: Any, context: str) -> str:
    if not isinstance(value, str):
        raise BaselineError(f"{context} must be a string")
    return value


def _expect_bool(value: Any, context: str) -> bool:
    if not isinstance(value, bool):
        raise BaselineError(f"{context} must be a boolean")
    return value


def _validate_visibility(value: Any, context: str) -> str:
    visibility = _expect_string(value, context)
    if visibility not in VISIBILITIES:
        raise BaselineError(f"{context} has unsupported visibility {visibility!r}")
    return visibility


def _validate_member_document(
    value: Any,
    context: str,
    *,
    method: bool,
    contract_only: bool,
) -> dict[str, Any]:
    member = _expect_object(value, context)
    _expect_exact_keys(member, METHOD_KEYS if method else FIELD_KEYS, context)
    name = _expect_string(member["name"], f"{context}.name")
    descriptor = _expect_string(member["descriptor"], f"{context}.descriptor")
    if not name:
        raise BaselineError(f"{context}.name must not be empty")
    static = _expect_bool(member["static"], f"{context}.static")
    try:
        if method:
            validate_method_descriptor(descriptor, is_static=static)
        else:
            validate_field_descriptor(descriptor)
    except ClassFileError as failure:
        raise BaselineError(f"{context}: {failure}") from failure
    visibility = _validate_visibility(member["visibility"], f"{context}.visibility")
    if contract_only and visibility not in {"public", "protected"}:
        raise BaselineError(
            f"baseline contract {'method' if method else 'field'} is not "
            f"public/protected: {name}{descriptor}"
        )
    _expect_bool(member["final"], f"{context}.final")
    if method:
        _expect_bool(member["abstract"], f"{context}.abstract")
    else:
        _validate_constant_value(
            member["constantValue"],
            descriptor,
            member["static"],
            member["final"],
            f"{context}.constantValue",
        )
    return member


def _validate_constant_value(
    value: Any,
    descriptor: str,
    static: bool,
    final: bool,
    context: str,
) -> None:
    if value is None:
        return
    constant = _expect_object(value, context)
    _expect_exact_keys(constant, frozenset({"kind", "value"}), context)
    expected_kinds = {
        "B": "byte",
        "C": "char",
        "D": "double-bits",
        "F": "float-bits",
        "I": "int",
        "J": "long",
        "S": "short",
        "Z": "boolean",
        "Ljava/lang/String;": "string",
    }
    expected = expected_kinds.get(descriptor)
    kind = _expect_string(constant["kind"], f"{context}.kind")
    if expected is None or kind != expected:
        raise BaselineError(
            f"{context}.kind {kind!r} does not match descriptor {descriptor!r}"
        )
    if not static or not final:
        raise BaselineError(f"{context} requires a static final field")
    normalized = constant["value"]
    if kind in {"byte", "char", "int", "short", "boolean"}:
        if type(normalized) is not int or not -(1 << 31) <= normalized < (1 << 31):
            raise BaselineError(f"{context}.value must be a signed 32-bit integer")
    elif kind == "long":
        if type(normalized) is not int or not -(1 << 63) <= normalized < (1 << 63):
            raise BaselineError(f"{context}.value must be a signed 64-bit integer")
    elif kind in {"float-bits", "double-bits"}:
        width = 8 if kind == "float-bits" else 16
        if not isinstance(normalized, str) or len(normalized) != width \
                or any(character not in "0123456789abcdef" for character in normalized):
            raise BaselineError(
                f"{context}.value must be {width} lowercase hexadecimal digits"
            )
    elif not isinstance(normalized, str):
        raise BaselineError(f"{context}.value must be a string")


def _validate_type_document(
    raw_type: Any,
    context: str,
    *,
    contract_only: bool,
    resolution_metadata: bool = False,
) -> dict[str, Any]:
    type_value = _expect_object(raw_type, context)
    _expect_exact_keys(
        type_value,
        RESOLUTION_TYPE_KEYS if resolution_metadata else TYPE_KEYS,
        context,
    )
    name = _expect_string(type_value["name"], f"{context}.name")
    try:
        validate_internal_name(name)
    except ClassFileError as failure:
        raise BaselineError(f"{context}: {failure}") from failure
    visibility = _validate_visibility(type_value["visibility"], f"{context}.visibility")
    if contract_only and visibility not in {"public", "protected"}:
        raise BaselineError(f"baseline contract type is not public/protected: {name}")
    kind = _expect_string(type_value["kind"], f"{context}.kind")
    if kind not in KINDS:
        raise BaselineError(f"{context}.kind is unsupported: {kind!r}")
    _expect_bool(type_value["abstract"], f"{context}.abstract")
    _expect_bool(type_value["final"], f"{context}.final")
    superclass = type_value["superclass"]
    if superclass is not None:
        superclass = _expect_string(superclass, f"{context}.superclass")
        try:
            validate_internal_name(superclass)
        except ClassFileError as failure:
            raise BaselineError(f"{context}: {failure}") from failure
    interfaces = type_value["interfaces"]
    if not isinstance(interfaces, list):
        raise BaselineError(f"{context}.interfaces must be an array")
    validated_interfaces: list[str] = []
    for interface in interfaces:
        interface = _expect_string(interface, f"{context}.interfaces entry")
        validated_interfaces.append(interface)
        try:
            validate_internal_name(interface)
        except ClassFileError as failure:
            raise BaselineError(f"{context}: {failure}") from failure
    if len(validated_interfaces) != len(set(validated_interfaces)):
        raise BaselineError(f"{context}.interfaces contains duplicates")
    if resolution_metadata:
        for key in ("nestHost", "outer"):
            reference = type_value[key]
            if reference is None:
                continue
            reference = _expect_string(reference, f"{context}.{key}")
            try:
                validate_internal_name(reference)
            except ClassFileError as failure:
                raise BaselineError(f"{context}: {failure}") from failure
        nest_members = type_value["nestMembers"]
        if not isinstance(nest_members, list):
            raise BaselineError(f"{context}.nestMembers must be an array")
        validated_nest_members: list[str] = []
        for reference in nest_members:
            reference = _expect_string(
                reference, f"{context}.nestMembers entry"
            )
            try:
                validate_internal_name(reference)
            except ClassFileError as failure:
                raise BaselineError(f"{context}: {failure}") from failure
            validated_nest_members.append(reference)
        if len(validated_nest_members) != len(set(validated_nest_members)):
            raise BaselineError(f"{context}.nestMembers contains duplicates")
        if validated_nest_members != sorted(validated_nest_members):
            raise BaselineError(f"{context}.nestMembers must be sorted")
    for member_kind, method in (("fields", False), ("methods", True)):
        members = type_value[member_kind]
        if not isinstance(members, list):
            raise BaselineError(f"{context}.{member_kind} must be an array")
        seen_members: set[tuple[str, str]] = set()
        for member_index, raw_member in enumerate(members):
            member = _validate_member_document(
                raw_member,
                f"{context}.{member_kind}[{member_index}]",
                method=method,
                contract_only=contract_only,
            )
            key = (member["name"], member["descriptor"])
            if key in seen_members:
                raise BaselineError(
                    f"duplicate baseline {member_kind[:-1]}: "
                    f"{name}#{key[0]}{key[1]}"
                )
            seen_members.add(key)
    return type_value


def _contract_projection(value: dict[str, Any]) -> dict[str, Any]:
    projected = dict(value)
    projected.pop("nestHost")
    projected.pop("nestMembers")
    projected.pop("outer")
    projected["fields"] = [
        member
        for member in value["fields"]
        if member["visibility"] in {"public", "protected"}
    ]
    projected["interfaces"] = sorted(value["interfaces"])
    projected["methods"] = [
        member
        for member in value["methods"]
        if member["visibility"] in {"public", "protected"}
    ]
    return projected


def _document_resolution_references(value: dict[str, Any]) -> tuple[str, ...]:
    references: list[str] = []
    if value["superclass"] is not None:
        references.append(value["superclass"])
    references.extend(value["interfaces"])
    for key in ("outer", "nestHost"):
        reference = value.get(key)
        if reference is not None:
            references.append(reference)
    references.extend(value.get("nestMembers", ()))
    return tuple(dict.fromkeys(references))


def _validate_baseline_resolution_graph(
    contract_types: dict[str, dict[str, Any]],
    resolution_types: dict[str, dict[str, Any]],
) -> None:
    collisions = sorted(set(resolution_types) & set(EXTERNAL_AUTHORITY_DOCUMENTS))
    if collisions:
        raise BaselineError(
            f"baseline local resolution type shadows Java 21 authority: {collisions[0]}"
        )
    reachable: set[str] = set()
    pending = list(contract_types)
    while pending:
        name = pending.pop(0)
        if name in reachable:
            continue
        value = resolution_types.get(name)
        if value is None:
            raise BaselineError(
                f"baseline resolution graph lacks local type: {name}"
            )
        reachable.add(name)
        for reference in _document_resolution_references(value):
            if reference in resolution_types:
                pending.append(reference)
            elif reference not in EXTERNAL_AUTHORITY_DOCUMENTS:
                raise BaselineError(
                    f"baseline resolution graph lacks local type: {reference}"
                )
    unreachable = sorted(set(resolution_types) - reachable)
    if unreachable:
        raise BaselineError(
            "baseline resolution type is unreachable from the contract: "
            f"{unreachable[0]}"
        )
    try:
        validate_local_nests({
            name: _parsed_class_from_document(value)
            for name, value in resolution_types.items()
        })
    except ClassFileError as failure:
        raise BaselineError(f"baseline resolution nest graph is invalid: {failure}") \
            from failure


def validate_baseline_document(value: Any) -> dict[str, Any]:
    document = _expect_object(value, "baseline")
    expected_keys = frozenset({
        "classFileVersion", "formatVersion", "resolutionTypes", "types",
    })
    _expect_exact_keys(document, expected_keys, "baseline")
    if type(document["formatVersion"]) is not int:
        raise BaselineError(
            f"baseline formatVersion must be integer {FORMAT_VERSION}"
        )
    if document["formatVersion"] != FORMAT_VERSION:
        raise BaselineError(
            f"unsupported baseline format version {document['formatVersion']!r}"
        )
    if document["classFileVersion"] != CLASS_FILE_VERSION:
        raise BaselineError(
            f"unsupported baseline class-file version {document['classFileVersion']!r}"
        )
    if not isinstance(document["types"], list):
        raise BaselineError("baseline.types must be an array")
    if not document["types"]:
        raise BaselineError("baseline must contain public/protected contract types")
    if not isinstance(document["resolutionTypes"], list):
        raise BaselineError("baseline.resolutionTypes must be an array")
    if not document["resolutionTypes"]:
        raise BaselineError("baseline must contain resolution types")

    contract_types: dict[str, dict[str, Any]] = {}
    for index, raw_type in enumerate(document["types"]):
        type_value = _validate_type_document(
            raw_type,
            f"baseline.types[{index}]",
            contract_only=True,
        )
        name = type_value["name"]
        if name in contract_types:
            raise BaselineError(f"duplicate baseline type: {name}")
        contract_types[name] = type_value

    resolution_types: dict[str, dict[str, Any]] = {}
    for index, raw_type in enumerate(document["resolutionTypes"]):
        type_value = _validate_type_document(
            raw_type,
            f"baseline.resolutionTypes[{index}]",
            contract_only=False,
            resolution_metadata=True,
        )
        name = type_value["name"]
        if name in resolution_types:
            raise BaselineError(f"duplicate baseline resolution type: {name}")
        resolution_types[name] = type_value

    for name, contract_type in contract_types.items():
        resolution_type = resolution_types.get(name)
        if resolution_type is None:
            raise BaselineError(
                f"baseline contract type lacks resolution state: {name}"
            )
        if contract_type != _contract_projection(resolution_type):
            raise BaselineError(
                f"baseline contract and resolution type differ: {name}"
            )
    _validate_baseline_resolution_graph(contract_types, resolution_types)
    return document


def _parsed_member_from_document(
    value: dict[str, Any],
    *,
    method: bool,
) -> ParsedMember:
    constant_value = None
    if not method and value["constantValue"] is not None:
        constant_value = ParsedConstantValue(
            value["constantValue"]["kind"],
            value["constantValue"]["value"],
        )
    return ParsedMember(
        name=value["name"],
        descriptor=value["descriptor"],
        visibility=value["visibility"],
        static=value["static"],
        final=value["final"],
        abstract=value["abstract"] if method else False,
        synthetic=False,
        bridge=False,
        constant_value=constant_value,
    )


def _parsed_class_from_document(value: dict[str, Any]) -> ParsedClass:
    outer = value.get("outer")
    return ParsedClass(
        name=value["name"],
        class_access=0,
        visibility=value["visibility"],
        kind=value["kind"],
        abstract=value["abstract"],
        final=value["final"],
        superclass=value["superclass"],
        interfaces=tuple(value["interfaces"]),
        fields=tuple(
            _parsed_member_from_document(member, method=False)
            for member in value["fields"]
        ),
        methods=tuple(
            _parsed_member_from_document(member, method=True)
            for member in value["methods"]
        ),
        self_inner=(
            InnerClassMembership(outer, None, 0) if outer is not None else None
        ),
        nest_host=value.get("nestHost"),
        nest_members=tuple(value.get("nestMembers", ())),
        sealed=False,
    )


def _read_java21_authority(
    path: Path,
) -> tuple[dict[str, dict[str, Any]], dict[str, ParsedClass]]:
    try:
        with path.open("r", encoding="utf-8") as source:
            document = json.load(source, object_pairs_hook=_strict_json_object)
    except (OSError, UnicodeError, json.JSONDecodeError) as failure:
        raise BaselineError(
            f"Java 21 external resolution authority cannot be read: {path}: {failure}"
        ) from failure
    document = _expect_object(document, "Java 21 authority")
    _expect_exact_keys(
        document,
        frozenset({
            "classFileVersion", "formatVersion", "javaRelease", "jdkProvenance",
            "sourceModule", "sourceModuleSha256", "types",
        }),
        "Java 21 authority",
    )
    if type(document["formatVersion"]) is not int or document["formatVersion"] != 2:
        raise BaselineError("Java 21 authority formatVersion must be integer 2")
    if type(document["javaRelease"]) is not int or document["javaRelease"] != 21:
        raise BaselineError("Java 21 authority javaRelease must be integer 21")
    if document["classFileVersion"] != CLASS_FILE_VERSION:
        raise BaselineError(
            "Java 21 authority has unsupported class-file version "
            f"{document['classFileVersion']!r}"
        )
    if document["sourceModule"] != "java.base":
        raise BaselineError("Java 21 authority sourceModule must be 'java.base'")
    provenance = _expect_object(
        document["jdkProvenance"], "Java 21 authority.jdkProvenance"
    )
    _expect_exact_keys(
        provenance,
        frozenset(PINNED_JDK_PROVENANCE),
        "Java 21 authority.jdkProvenance",
    )
    if provenance != PINNED_JDK_PROVENANCE:
        raise BaselineError(
            "Java 21 authority JDK provenance differs from the reviewed pin"
        )
    if document["sourceModuleSha256"] != PINNED_JAVA_BASE_JMOD_SHA256:
        raise BaselineError(
            "Java 21 authority java.base.jmod SHA-256 differs from the reviewed pin"
        )
    if not isinstance(document["types"], list) or not document["types"]:
        raise BaselineError("Java 21 authority types must be a non-empty array")

    documents: dict[str, dict[str, Any]] = {}
    for index, raw_type in enumerate(document["types"]):
        value = _validate_type_document(
            raw_type,
            f"Java 21 authority.types[{index}]",
            contract_only=False,
        )
        name = value["name"]
        if name in documents:
            raise BaselineError(f"duplicate Java 21 authority type: {name}")
        documents[name] = value
    if list(documents) != sorted(documents):
        raise BaselineError("Java 21 authority types must be sorted by name")
    for name, value in documents.items():
        for reference in _document_resolution_references(value):
            if reference not in documents:
                raise BaselineError(
                    f"Java 21 authority type {name} lacks hierarchy entry {reference}"
                )
    required = {
        "java/lang/AutoCloseable",
        "java/lang/Enum",
        "java/lang/Object",
        "java/lang/Record",
        "java/lang/Runnable",
        "java/lang/RuntimeException",
    }
    missing = sorted(required - set(documents))
    if missing:
        raise BaselineError(
            f"Java 21 authority lacks required type: {missing[0]}"
        )
    classes = {
        name: _parsed_class_from_document(value)
        for name, value in documents.items()
    }
    return documents, classes


EXTERNAL_AUTHORITY_DOCUMENTS, EXTERNAL_AUTHORITY_CLASSES = (
    _read_java21_authority(JAVA21_AUTHORITY_PATH)
)
_object_class = EXTERNAL_AUTHORITY_CLASSES["java/lang/Object"]
OBJECT_INSTANCE_METHODS = {
    (member.name, member.descriptor): member
    for member in _object_class.methods
    if member.visibility == "public"
    and not member.static
    and member.name not in {"<init>", "<clinit>"}
}


def read_json_baseline(path: Path) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as source:
            return validate_baseline_document(
                json.load(source, object_pairs_hook=_strict_json_object)
            )
    except (OSError, UnicodeError, json.JSONDecodeError) as failure:
        raise BaselineError(f"baseline JSON cannot be read: {path}: {failure}") from failure


def _visibility_reduced(old: str, new: str) -> bool:
    rank = {"private": 0, "package": 0, "protected": 1, "public": 2}
    return rank[new] < rank[old]


def _resolution_visibility_reduced(old: str, new: str) -> bool:
    rank = {"private": 0, "package": 1, "protected": 2, "public": 3}
    return rank[new] < rank[old]


def _candidate_resolution_state(
    classes: dict[str, ParsedClass],
    baseline_local_names: set[str],
) -> tuple[dict[str, ParsedClass], set[str]]:
    collisions = sorted(set(classes) & set(EXTERNAL_AUTHORITY_CLASSES))
    if collisions:
        raise ClassFileError(
            f"candidate local type shadows Java 21 authority: {collisions[0]}"
        )
    roots = [
        value.name
        for value in classes.values()
        if _externally_accessible_type(classes, value)
    ]
    reachable: set[str] = set()
    pending = list(roots)
    while pending:
        name = pending.pop(0)
        if name in reachable:
            continue
        value = classes.get(name)
        if value is None:
            continue
        reachable.add(name)
        for reference in _parsed_resolution_references(value):
            if reference in classes:
                pending.append(reference)
            elif reference in EXTERNAL_AUTHORITY_CLASSES:
                continue
            elif reference not in baseline_local_names:
                raise ClassFileError(
                    f"unsupported external resolution type: {reference}"
                )
    combined = dict(EXTERNAL_AUTHORITY_CLASSES)
    combined.update(classes)
    return combined, reachable


def _parsed_member_map(
    value: ParsedClass,
    member_kind: str,
) -> dict[tuple[str, str], ParsedMember]:
    members = value.fields if member_kind == "field" else value.methods
    return {(member.name, member.descriptor): member for member in members}


def _candidate_member(
    classes: dict[str, ParsedClass],
    owner: ParsedClass,
    key: tuple[str, str],
    member_kind: str,
    baseline_member: dict[str, Any],
) -> ParsedMember | None:
    declared = _parsed_member_map(owner, member_kind).get(key)
    if declared is not None:
        return declared
    if member_kind == "method":
        if key[0] == "<init>":
            return None
        if not baseline_member["static"]:
            if owner.kind == "interface" and key in OBJECT_INSTANCE_METHODS:
                return OBJECT_INSTANCE_METHODS[key]
            superclass = owner.superclass
            visited_superclasses: set[str] = set()
            while superclass is not None and superclass not in visited_superclasses:
                visited_superclasses.add(superclass)
                value = classes.get(superclass)
                if value is None:
                    if superclass == "java/lang/Object":
                        object_method = OBJECT_INSTANCE_METHODS.get(key)
                        if object_method is not None:
                            return object_method
                    break
                inherited = _parsed_member_map(value, member_kind).get(key)
                if inherited is not None:
                    # Method lookup selects a matching superclass declaration
                    # before checking whether the referring class may access it.
                    # Inaccessible declarations therefore still shadow defaults.
                    return inherited
                superclass = value.superclass
            return _candidate_effective_methods(classes, owner.name).get(key)
        if owner.kind == "interface":
            return None
        superclass = owner.superclass
        visited_superclasses: set[str] = set()
        while superclass is not None and superclass not in visited_superclasses:
            visited_superclasses.add(superclass)
            value = classes.get(superclass)
            if value is None:
                return None
            inherited = _parsed_member_map(value, member_kind).get(key)
            if inherited is not None:
                return inherited
            superclass = value.superclass
        return None
    return _candidate_resolved_field(classes, owner.name, key, set())


def _candidate_resolved_field(
    classes: dict[str, ParsedClass],
    name: str,
    key: tuple[str, str],
    visiting: set[str],
) -> ParsedMember | None:
    if name in visiting:
        return None
    value = classes.get(name)
    if value is None:
        return None
    declared = _parsed_member_map(value, "field").get(key)
    if declared is not None:
        return declared
    nested_visiting = visiting | {name}
    # JVMS 5.4.3.2 resolves direct superinterfaces recursively before the
    # superclass. This order is observable when both branches declare a field.
    for interface in value.interfaces:
        inherited = _candidate_resolved_field(
            classes, interface, key, nested_visiting
        )
        if inherited is not None:
            return inherited
    if value.superclass is not None:
        return _candidate_resolved_field(
            classes, value.superclass, key, nested_visiting
        )
    return None


def _candidate_supertypes(
    classes: dict[str, ParsedClass],
    value: ParsedClass,
) -> tuple[set[str], set[str]]:
    superclasses: set[str] = set()
    interfaces: set[str] = set()
    pending_classes = [value.superclass] if value.superclass else []
    while pending_classes:
        name = pending_classes.pop(0)
        if name in superclasses:
            continue
        superclasses.add(name)
        parent = classes.get(name)
        if parent is not None:
            interfaces.update(parent.interfaces)
            if parent.superclass:
                pending_classes.append(parent.superclass)
    pending_interfaces = list(value.interfaces) + list(interfaces)
    while pending_interfaces:
        name = pending_interfaces.pop(0)
        if name in interfaces:
            existing = classes.get(name)
            if existing is not None:
                pending_interfaces.extend(
                    interface for interface in existing.interfaces if interface not in interfaces
                )
            continue
        interfaces.add(name)
        inherited = classes.get(name)
        if inherited is not None:
            pending_interfaces.extend(inherited.interfaces)
    return superclasses, interfaces


def _member_label(owner: str, member: dict[str, Any], member_kind: str) -> str:
    if member_kind == "field":
        return f"{owner}#{member['name']}:{member['descriptor']}"
    return f"{owner}#{member['name']}{member['descriptor']}"


def _descriptor_changes(
    old_members: list[dict[str, Any]],
    candidate: ParsedClass,
    member_kind: str,
    unresolved_keys: set[tuple[str, str]],
) -> tuple[list[str], set[tuple[str, str]]]:
    candidate_members = candidate.fields if member_kind == "field" else candidate.methods
    old_by_name: dict[str, list[dict[str, Any]]] = {}
    new_by_name: dict[str, list[ParsedMember]] = {}
    for member in old_members:
        old_by_name.setdefault(member["name"], []).append(member)
    for member in candidate_members:
        if member.visibility in {"public", "protected"}:
            new_by_name.setdefault(member.name, []).append(member)
    findings: list[str] = []
    handled: set[tuple[str, str]] = set()
    for name in sorted(old_by_name):
        old_values = old_by_name[name]
        new_values = new_by_name.get(name, [])
        if len(old_values) != 1 or len(new_values) != 1:
            continue
        old = old_values[0]
        new = new_values[0]
        if (name, old["descriptor"]) not in unresolved_keys:
            continue
        if old["descriptor"] == new.descriptor:
            continue
        label = "constructor" if name == "<init>" else member_kind
        findings.append(
            f"{label} descriptor changed: {candidate.name}#{name} "
            f"{old['descriptor']} -> {new.descriptor}"
        )
        handled.add((name, old["descriptor"]))
    return findings, handled


def _compare_members(
    old_type: dict[str, Any],
    candidate: ParsedClass,
    classes: dict[str, ParsedClass],
    member_kind: str,
    *,
    detect_descriptor_changes: bool = True,
) -> list[str]:
    plural = "fields" if member_kind == "field" else "methods"
    old_members = old_type[plural]
    resolved: dict[tuple[str, str], ParsedMember | None] = {}
    unresolved: list[dict[str, Any]] = []
    for old in old_members:
        key = (old["name"], old["descriptor"])
        current = _candidate_member(
            classes, candidate, key, member_kind, old
        )
        resolved[key] = current
        if current is None:
            unresolved.append(old)
    if detect_descriptor_changes:
        findings, descriptor_changes = _descriptor_changes(
            old_members,
            candidate,
            member_kind,
            {
                (member["name"], member["descriptor"])
                for member in unresolved
            },
        )
    else:
        findings, descriptor_changes = [], set()
    for old in old_members:
        key = (old["name"], old["descriptor"])
        if key in descriptor_changes:
            continue
        current = resolved[key]
        label = "constructor" if old["name"] == "<init>" else member_kind
        display = _member_label(candidate.name, old, member_kind)
        if current is None:
            findings.append(f"{label} removed: {display}")
            continue
        if _visibility_reduced(old["visibility"], current.visibility):
            findings.append(
                f"{label} visibility reduced: {display} "
                f"{old['visibility']} -> {current.visibility}"
            )
        if old["static"] != current.static:
            findings.append(
                f"{label} static modifier changed: {display} "
                f"{'static' if old['static'] else 'instance'} -> "
                f"{'static' if current.static else 'instance'}"
            )
        if not old["final"] and current.final:
            findings.append(f"{label} became final: {display}")
        if member_kind == "field":
            old_constant = old["constantValue"]
            current_constant = _field_document(current)["constantValue"]
            if old_constant != current_constant:
                if old_constant is None:
                    change = "added"
                elif current_constant is None:
                    change = "removed"
                else:
                    change = "changed"
                findings.append(f"field constant value {change}: {display}")
        if member_kind == "method" and not old["abstract"] and current.abstract:
            findings.append(f"{label} became abstract: {display}")

    return findings


def _candidate_instance_method_map(
    value: ParsedClass,
    *,
    accessible_only: bool = True,
) -> dict[tuple[str, str], ParsedMember]:
    return {
        (member.name, member.descriptor): member
        for member in value.methods
        if (not accessible_only or member.visibility in {"public", "protected"})
        and not member.static
        and member.name not in {"<init>", "<clinit>"}
    }


def _candidate_interface_extends(
    classes: dict[str, ParsedClass],
    child: str,
    ancestor: str,
) -> bool:
    pending = [child]
    visited: set[str] = set()
    while pending:
        current = pending.pop(0)
        if current in visited:
            continue
        visited.add(current)
        value = classes.get(current)
        if value is None:
            continue
        for parent in value.interfaces:
            if parent == ancestor:
                return True
            pending.append(parent)
    return False


def _candidate_interface_methods(
    classes: dict[str, ParsedClass],
    roots: list[str],
    excluded: set[tuple[str, str]],
) -> dict[tuple[str, str], ParsedMember]:
    declarations: dict[tuple[str, str], list[tuple[str, ParsedMember]]] = {}
    pending = list(roots)
    visited: set[str] = set()
    while pending:
        name = pending.pop(0)
        if name in visited:
            continue
        visited.add(name)
        value = classes.get(name)
        if value is None:
            continue
        if value.kind != "interface":
            raise ClassFileError(f"non-interface listed as interface: {name}")
        for key, member in _candidate_instance_method_map(value).items():
            if key not in excluded:
                declarations.setdefault(key, []).append((name, member))
        pending.extend(value.interfaces)

    resolved: dict[tuple[str, str], ParsedMember] = {}
    for key, values in declarations.items():
        maximal = [
            (owner, member)
            for owner, member in values
            if not any(
                other_owner != owner
                and _candidate_interface_extends(classes, other_owner, owner)
                for other_owner, _ in values
            )
        ]
        if not maximal:
            raise ClassFileError(
                f"cyclic interface method hierarchy for {key[0]}{key[1]}"
            )
        concrete = [value for value in maximal if not value[1].abstract]
        if len(concrete) > 1:
            owners = ", ".join(sorted(owner for owner, _ in concrete))
            raise ClassFileError(
                f"ambiguous inherited interface defaults for {key[0]}{key[1]}: {owners}"
            )
        selected = concrete[0] if concrete else min(maximal, key=lambda item: item[0])
        resolved[key] = selected[1]
    return resolved


def _candidate_effective_methods(
    classes: dict[str, ParsedClass],
    name: str,
) -> dict[tuple[str, str], ParsedMember]:
    value = classes.get(name)
    if value is None:
        return {}
    if value.kind == "interface":
        declared = _candidate_instance_method_map(value)
        inherited = _candidate_interface_methods(
            classes,
            list(value.interfaces),
            set(declared) | set(OBJECT_INSTANCE_METHODS),
        )
        inherited.update(
            (key, member)
            for key, member in declared.items()
            if key not in OBJECT_INSTANCE_METHODS
        )
        return inherited

    class_methods: dict[tuple[str, str], ParsedMember] = {}
    interface_roots: list[str] = []
    current: str | None = name
    visited_classes: set[str] = set()
    while current is not None and current not in visited_classes:
        visited_classes.add(current)
        class_value = classes.get(current)
        if class_value is None:
            break
        if class_value.kind == "interface":
            raise ClassFileError(f"interface listed as superclass: {current}")
        for key, member in _candidate_instance_method_map(
            class_value, accessible_only=False
        ).items():
            class_methods.setdefault(key, member)
        interface_roots.extend(class_value.interfaces)
        current = class_value.superclass
    inherited = _candidate_interface_methods(
        classes,
        interface_roots,
        set(class_methods) | set(OBJECT_INSTANCE_METHODS),
    )
    # Any class declaration wins over an interface method, including an
    # abstract class declaration over an otherwise applicable default.
    inherited.update(class_methods)
    return {
        key: member
        for key, member in inherited.items()
        if member.visibility in {"public", "protected"}
    }


def _baseline_instance_method_map(
    value: dict[str, Any],
    *,
    accessible_only: bool = True,
) -> dict[tuple[str, str], dict[str, Any]]:
    return {
        (member["name"], member["descriptor"]): member
        for member in value["methods"]
        if (not accessible_only or member["visibility"] in {"public", "protected"})
        and not member["static"]
        and member["name"] not in {"<init>", "<clinit>"}
    }


def _baseline_interface_extends(
    types: dict[str, dict[str, Any]],
    child: str,
    ancestor: str,
) -> bool:
    pending = [child]
    visited: set[str] = set()
    while pending:
        current = pending.pop(0)
        if current in visited:
            continue
        visited.add(current)
        value = types.get(current)
        if value is None:
            continue
        for parent in value["interfaces"]:
            if parent == ancestor:
                return True
            pending.append(parent)
    return False


def _baseline_interface_methods(
    types: dict[str, dict[str, Any]],
    roots: list[str],
    excluded: set[tuple[str, str]],
) -> dict[tuple[str, str], dict[str, Any]]:
    declarations: dict[
        tuple[str, str], list[tuple[str, dict[str, Any]]]
    ] = {}
    pending = list(roots)
    visited: set[str] = set()
    while pending:
        name = pending.pop(0)
        if name in visited:
            continue
        visited.add(name)
        value = types.get(name)
        if value is None:
            continue
        if value["kind"] != "interface":
            raise BaselineError(f"non-interface listed as interface: {name}")
        for key, member in _baseline_instance_method_map(value).items():
            if key not in excluded:
                declarations.setdefault(key, []).append((name, member))
        pending.extend(value["interfaces"])

    resolved: dict[tuple[str, str], dict[str, Any]] = {}
    for key, values in declarations.items():
        maximal = [
            (owner, member)
            for owner, member in values
            if not any(
                other_owner != owner
                and _baseline_interface_extends(types, other_owner, owner)
                for other_owner, _ in values
            )
        ]
        if not maximal:
            raise BaselineError(
                f"cyclic interface method hierarchy for {key[0]}{key[1]}"
            )
        concrete = [value for value in maximal if not value[1]["abstract"]]
        if len(concrete) > 1:
            owners = ", ".join(sorted(owner for owner, _ in concrete))
            raise BaselineError(
                f"ambiguous inherited interface defaults for {key[0]}{key[1]}: {owners}"
            )
        selected = concrete[0] if concrete else min(maximal, key=lambda item: item[0])
        resolved[key] = selected[1]
    return resolved


def _baseline_effective_methods(
    types: dict[str, dict[str, Any]],
    name: str,
) -> dict[tuple[str, str], dict[str, Any]]:
    value = types.get(name)
    if value is None:
        return {}
    if value["kind"] == "interface":
        declared = _baseline_instance_method_map(value)
        inherited = _baseline_interface_methods(
            types,
            list(value["interfaces"]),
            set(declared) | set(OBJECT_INSTANCE_METHODS),
        )
        inherited.update(
            (key, member)
            for key, member in declared.items()
            if key not in OBJECT_INSTANCE_METHODS
        )
        return inherited

    class_methods: dict[tuple[str, str], dict[str, Any]] = {}
    interface_roots: list[str] = []
    current: str | None = name
    visited_classes: set[str] = set()
    while current is not None and current not in visited_classes:
        visited_classes.add(current)
        class_value = types.get(current)
        if class_value is None:
            break
        for key, member in _baseline_instance_method_map(
            class_value, accessible_only=False
        ).items():
            class_methods.setdefault(key, member)
        interface_roots.extend(class_value["interfaces"])
        current = class_value["superclass"]
    inherited = _baseline_interface_methods(
        types,
        interface_roots,
        set(class_methods) | set(OBJECT_INSTANCE_METHODS),
    )
    inherited.update(class_methods)
    return {
        key: member
        for key, member in inherited.items()
        if member["visibility"] in {"public", "protected"}
    }


def _baseline_declared_member_map(
    value: dict[str, Any],
    member_kind: str,
) -> dict[tuple[str, str], dict[str, Any]]:
    members = value["fields"] if member_kind == "field" else value["methods"]
    return {(member["name"], member["descriptor"]): member for member in members}


def _baseline_hierarchy_member_keys(
    types: dict[str, dict[str, Any]],
    name: str,
    member_kind: str,
) -> set[tuple[str, str]]:
    keys: set[tuple[str, str]] = set()
    pending = [name]
    visited: set[str] = set()
    while pending:
        current = pending.pop(0)
        if current in visited:
            continue
        visited.add(current)
        value = types.get(current)
        if value is None:
            continue
        keys.update(_baseline_declared_member_map(value, member_kind))
        pending.extend(value["interfaces"])
        if value["superclass"] is not None:
            pending.append(value["superclass"])
    return keys


def _baseline_resolved_field(
    types: dict[str, dict[str, Any]],
    name: str,
    key: tuple[str, str],
    visiting: set[str],
) -> dict[str, Any] | None:
    if name in visiting:
        return None
    value = types.get(name)
    if value is None:
        return None
    declared = _baseline_declared_member_map(value, "field").get(key)
    if declared is not None:
        return declared
    nested_visiting = visiting | {name}
    for interface in value["interfaces"]:
        inherited = _baseline_resolved_field(
            types, interface, key, nested_visiting
        )
        if inherited is not None:
            return inherited
    if value["superclass"] is not None:
        return _baseline_resolved_field(
            types, value["superclass"], key, nested_visiting
        )
    return None


def _baseline_resolved_fields(
    types: dict[str, dict[str, Any]],
    name: str,
) -> dict[tuple[str, str], dict[str, Any]]:
    resolved: dict[tuple[str, str], dict[str, Any]] = {}
    for key in _baseline_hierarchy_member_keys(types, name, "field"):
        member = _baseline_resolved_field(types, name, key, set())
        if member is not None and member["visibility"] in {"public", "protected"}:
            resolved[key] = member
    return resolved


def _baseline_resolved_static_methods(
    types: dict[str, dict[str, Any]],
    name: str,
) -> dict[tuple[str, str], dict[str, Any]]:
    value = types.get(name)
    if value is None or value["kind"] == "interface":
        return {}
    resolved: dict[tuple[str, str], dict[str, Any]] = {}
    current: str | None = name
    visited: set[str] = set()
    while current is not None and current not in visited:
        visited.add(current)
        class_value = types.get(current)
        if class_value is None:
            break
        for key, member in _baseline_declared_member_map(
            class_value, "method"
        ).items():
            if member["name"] not in {"<init>", "<clinit>"}:
                resolved.setdefault(key, member)
        current = class_value["superclass"]
    return {
        key: member
        for key, member in resolved.items()
        if member["static"]
        and member["visibility"] in {"public", "protected"}
    }


def _compare_hidden_resolution_type(
    old_type: dict[str, Any],
    candidate: ParsedClass,
    candidate_classes: dict[str, ParsedClass],
) -> list[str]:
    name = old_type["name"]
    findings: list[str] = []
    if _resolution_visibility_reduced(
        old_type["visibility"], candidate.visibility
    ):
        findings.append(
            f"resolution type visibility reduced: {name} "
            f"{old_type['visibility']} -> {candidate.visibility}"
        )
    if old_type["kind"] != candidate.kind:
        findings.append(
            f"resolution type kind changed: {name} "
            f"{old_type['kind']} -> {candidate.kind}"
        )
    if not old_type["abstract"] and candidate.abstract:
        findings.append(f"resolution type became abstract: {name}")
    if old_type["kind"] == "class" and not old_type["final"] and candidate.final:
        findings.append(f"resolution type became final: {name}")
    superclasses, interfaces = _candidate_supertypes(
        candidate_classes, candidate
    )
    old_superclass = old_type["superclass"]
    if old_superclass is not None and old_superclass not in superclasses:
        findings.append(
            f"resolution supertype removed: {name} no longer extends {old_superclass}"
        )
    for interface in old_type["interfaces"]:
        if interface not in interfaces:
            findings.append(
                f"resolution interface removed: {name} no longer implements {interface}"
            )
    return findings


def _compare_nest_contract(
    old_type: dict[str, Any],
    candidate: ParsedClass,
) -> list[str]:
    findings: list[str] = []
    old_host = old_type["nestHost"]
    if old_host is not None and candidate.nest_host != old_host:
        findings.append(
            f"nest host changed: {candidate.name} {old_host} -> "
            f"{candidate.nest_host if candidate.nest_host is not None else '<none>'}"
        )
    candidate_members = set(candidate.nest_members)
    for member in old_type["nestMembers"]:
        if member not in candidate_members:
            findings.append(
                f"nest member removed: {candidate.name} no longer lists {member}"
            )
    return findings


def compare(
    baseline: dict[str, Any],
    candidate_classes: dict[str, ParsedClass],
) -> list[str]:
    findings: list[str] = []
    baseline_local_types = {
        value["name"]: value for value in baseline["resolutionTypes"]
    }
    resolution_classes, candidate_reachable = _candidate_resolution_state(
        candidate_classes, set(baseline_local_types)
    )
    baseline_types = dict(EXTERNAL_AUTHORITY_DOCUMENTS)
    baseline_types.update(baseline_local_types)
    contract_names = {value["name"] for value in baseline["types"]}
    for name, old_type in sorted(baseline_local_types.items()):
        candidate_resolution_type = candidate_classes.get(name)
        if candidate_resolution_type is not None:
            findings.extend(
                _compare_nest_contract(old_type, candidate_resolution_type)
            )
    for name in sorted(set(baseline_local_types) - contract_names):
        candidate_resolution_type = candidate_classes.get(name)
        if candidate_resolution_type is None:
            findings.append(f"resolution type removed: {name}")
            continue
        if name not in candidate_reachable:
            findings.append(f"resolution type no longer reachable: {name}")
        findings.extend(
            _compare_hidden_resolution_type(
                baseline_local_types[name],
                candidate_resolution_type,
                resolution_classes,
            )
        )
    for old_type in baseline["types"]:
        name = old_type["name"]
        candidate = candidate_classes.get(name)
        if candidate is None:
            findings.append(f"type removed: {name}")
            continue
        if _visibility_reduced(old_type["visibility"], candidate.visibility):
            findings.append(
                f"type visibility reduced: {name} "
                f"{old_type['visibility']} -> {candidate.visibility}"
            )
        if old_type["kind"] != candidate.kind:
            findings.append(
                f"type kind changed: {name} {old_type['kind']} -> {candidate.kind}"
            )
        if not old_type["abstract"] and candidate.abstract:
            findings.append(f"type became abstract: {name}")
        if old_type["kind"] == "class" and not old_type["final"] and candidate.final:
            findings.append(f"type became final: {name}")
        superclasses, interfaces = _candidate_supertypes(
            resolution_classes, candidate
        )
        old_superclass = old_type["superclass"]
        if old_superclass is not None and old_superclass not in superclasses:
            findings.append(
                f"supertype removed: {name} no longer extends {old_superclass}"
            )
        for interface in old_type["interfaces"]:
            if interface not in interfaces:
                findings.append(
                    f"interface removed: {name} no longer implements {interface}"
                )
        findings.extend(_compare_members(old_type, candidate, resolution_classes, "field"))
        findings.extend(_compare_members(old_type, candidate, resolution_classes, "method"))
        declared_field_keys = {
            (member["name"], member["descriptor"])
            for member in old_type["fields"]
        }
        inherited_fields = [
            member
            for key, member in _baseline_resolved_fields(
                baseline_types, name
            ).items()
            if key not in declared_field_keys
        ]
        if inherited_fields:
            findings.extend(_compare_members(
                {"fields": inherited_fields},
                candidate,
                resolution_classes,
                "field",
                detect_descriptor_changes=False,
            ))
        old_effective = _baseline_effective_methods(baseline_types, name)
        old_resolved_methods = dict(old_effective)
        old_resolved_methods.update(
            _baseline_resolved_static_methods(baseline_types, name)
        )
        declared_method_keys = {
            (member["name"], member["descriptor"])
            for member in old_type["methods"]
        }
        inherited_methods = [
            member
            for key, member in old_resolved_methods.items()
            if key not in declared_method_keys
        ]
        if inherited_methods:
            findings.extend(_compare_members(
                {"methods": inherited_methods},
                candidate,
                resolution_classes,
                "method",
                detect_descriptor_changes=False,
            ))
        for key, member in _candidate_effective_methods(
            resolution_classes, name
        ).items():
            if not member.abstract:
                continue
            previous = old_effective.get(key)
            if previous is not None:
                if not previous["abstract"]:
                    findings.append(
                        f"method became abstract: {name}#{key[0]}{key[1]}"
                    )
                continue
            label = "interface" if old_type["kind"] == "interface" else "class"
            findings.append(
                f"abstract {label} method added: {name}#{key[0]}{key[1]}"
            )
    return sorted(set(findings))


def write_baseline(output: Path, candidate: Path) -> int:
    classes = read_class_jar(candidate)
    document = surface_document(classes)
    validate_baseline_document(document)
    if not document["types"]:
        raise BaselineError("candidate JAR contains no public/protected contract types")
    serialized = json.dumps(document, indent=2, sort_keys=True) + "\n"
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary_name = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            dir=output.parent,
            prefix=f".{output.name}.",
            delete=False,
        ) as temporary:
            temporary.write(serialized)
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary_name = temporary.name
        os.replace(temporary_name, output)
    finally:
        if temporary_name is not None and Path(temporary_name).exists():
            Path(temporary_name).unlink()
    print(
        f"Wrote reviewed renderer API binary baseline: {output} "
        f"({len(document['types'])} public/protected types from {candidate})"
    )
    return 0


def verify(baseline_option: str, baseline_path: Path, candidate_path: Path) -> int:
    if baseline_option == "--baseline-jar":
        baseline_classes = read_class_jar(baseline_path)
        baseline = surface_document(baseline_classes)
        if not baseline["types"]:
            raise BaselineError("baseline JAR contains no public/protected contract types")
    elif baseline_option == "--baseline-json":
        baseline = read_json_baseline(baseline_path)
    else:
        raise BaselineError(f"unsupported baseline option: {baseline_option}")
    candidate_classes = read_class_jar(candidate_path)
    findings = compare(baseline, candidate_classes)
    if findings:
        print(
            f"Renderer API binary compatibility check failed for candidate JAR "
            f"{candidate_path} against {baseline_option.removeprefix('--baseline-')} "
            f"{baseline_path}:",
            file=sys.stderr,
        )
        for finding in findings:
            print(f"- {finding}", file=sys.stderr)
        return 1
    print(
        f"Renderer API binary compatibility verified: {candidate_path} "
        f"against {baseline_path}"
    )
    return 0


def usage() -> int:
    print(
        "usage:\n"
        "  verify_renderer_api_compatibility.py --baseline-jar OLD.jar CANDIDATE.jar\n"
        "  verify_renderer_api_compatibility.py --baseline-json BASELINE.json CANDIDATE.jar\n"
        "  verify_renderer_api_compatibility.py --write-baseline-json OUTPUT.json CANDIDATE.jar",
        file=sys.stderr,
    )
    return 2


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        return usage()
    try:
        if argv[1] == "--write-baseline-json":
            return write_baseline(Path(argv[2]), Path(argv[3]))
        if argv[1] in {"--baseline-jar", "--baseline-json"}:
            return verify(argv[1], Path(argv[2]), Path(argv[3]))
        return usage()
    except (BaselineError, ClassFileError, OSError) as failure:
        print(f"renderer API compatibility input error: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
