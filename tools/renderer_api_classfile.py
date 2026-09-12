"""Strict Java 21 class-file parsing shared by renderer API verifiers."""

from dataclasses import dataclass
from pathlib import Path, PurePosixPath
import zipfile


ACC_PUBLIC = 0x0001
ACC_PRIVATE = 0x0002
ACC_PROTECTED = 0x0004
ACC_STATIC = 0x0008
ACC_FINAL = 0x0010
ACC_SYNCHRONIZED = 0x0020
ACC_SUPER = 0x0020
ACC_BRIDGE = 0x0040
ACC_VOLATILE = 0x0040
ACC_VARARGS = 0x0080
ACC_TRANSIENT = 0x0080
ACC_NATIVE = 0x0100
ACC_INTERFACE = 0x0200
ACC_ABSTRACT = 0x0400
ACC_SYNTHETIC = 0x1000
ACC_ANNOTATION = 0x2000
ACC_ENUM = 0x4000
ACC_MODULE = 0x8000

JAVA_21_MAJOR = 65
JAVA_21_MINOR = 0


class ClassFileError(ValueError):
    """Raised when an input cannot be normalized without ambiguity."""


@dataclass(frozen=True)
class InnerClassMembership:
    outer: str | None
    inner_name: str | None
    access: int


@dataclass(frozen=True)
class ParsedConstantValue:
    kind: str
    value: int | str


@dataclass(frozen=True)
class ParsedMember:
    name: str
    descriptor: str
    visibility: str
    static: bool
    final: bool
    abstract: bool
    synthetic: bool
    bridge: bool
    constant_value: ParsedConstantValue | None


@dataclass(frozen=True)
class ParsedClass:
    name: str
    class_access: int
    visibility: str
    kind: str
    abstract: bool
    final: bool
    superclass: str | None
    interfaces: tuple[str, ...]
    fields: tuple[ParsedMember, ...]
    methods: tuple[ParsedMember, ...]
    self_inner: InnerClassMembership | None
    nest_host: str | None
    nest_members: tuple[str, ...]
    sealed: bool

    def public_or_protected(self) -> bool:
        return self.visibility in {"public", "protected"}


class _Cursor:
    def __init__(self, payload: bytes):
        self.payload = payload
        self.offset = 0

    def take(self, length: int) -> bytes:
        if length < 0 or self.offset + length > len(self.payload):
            raise ClassFileError("truncated class structure")
        value = self.payload[self.offset:self.offset + length]
        self.offset += length
        return value

    def u1(self) -> int:
        return self.take(1)[0]

    def u2(self) -> int:
        return int.from_bytes(self.take(2), "big")

    def u4(self) -> int:
        return int.from_bytes(self.take(4), "big")

    def require_end(self, structure: str) -> None:
        if self.offset != len(self.payload):
            raise ClassFileError(f"malformed {structure}")


class _ConstantPool:
    def __init__(self, cursor: _Cursor):
        count = cursor.u2()
        if count < 2:
            raise ClassFileError("invalid constant pool count")
        self.entries: list[tuple[int, object] | None] = [None] * count
        index = 1
        while index < count:
            tag = cursor.u1()
            if tag == 1:
                self.entries[index] = (tag, cursor.take(cursor.u2()))
            elif tag in {3, 4}:
                self.entries[index] = (tag, cursor.take(4))
            elif tag in {5, 6}:
                self.entries[index] = (tag, cursor.take(8))
                index += 1
                if index >= count:
                    raise ClassFileError("wide constant exceeds constant pool")
            elif tag in {7, 8, 16, 19, 20}:
                self.entries[index] = (tag, cursor.u2())
            elif tag in {9, 10, 11, 12, 17, 18}:
                self.entries[index] = (tag, (cursor.u2(), cursor.u2()))
            elif tag == 15:
                self.entries[index] = (tag, (cursor.u1(), cursor.u2()))
            else:
                raise ClassFileError(f"unknown constant pool tag {tag}")
            index += 1
        self._validate_references()

    def entry(self, index: int, expected: int | tuple[int, ...] | None = None) -> tuple[int, object]:
        if index <= 0 or index >= len(self.entries) or self.entries[index] is None:
            raise ClassFileError("invalid constant pool reference")
        value = self.entries[index]
        if value is None:
            raise ClassFileError("invalid constant pool reference")
        if expected is not None:
            expected_tags = (expected,) if isinstance(expected, int) else expected
            if value[0] not in expected_tags:
                raise ClassFileError("constant pool reference has the wrong type")
        return value

    def utf8(self, index: int, purpose: str) -> str:
        raw = self.entry(index, 1)[1]
        if not isinstance(raw, bytes):
            raise ClassFileError("constant pool UTF-8 payload is malformed")
        try:
            return raw.decode("ascii")
        except UnicodeDecodeError as failure:
            raise ClassFileError(f"{purpose} is not ASCII") from failure

    def modified_utf8(self, index: int, purpose: str) -> str:
        raw = self.entry(index, 1)[1]
        if not isinstance(raw, bytes):
            raise ClassFileError("constant pool UTF-8 payload is malformed")
        try:
            return _decode_modified_utf8(raw)
        except ClassFileError as failure:
            raise ClassFileError(f"{purpose} is malformed modified UTF-8: {failure}") from failure

    def class_name(self, index: int) -> str:
        name_index = self.entry(index, 7)[1]
        if not isinstance(name_index, int):
            raise ClassFileError("constant pool class payload is malformed")
        name = self.utf8(name_index, "structural class name")
        _validate_internal_name(name)
        return name

    def constant_value(self, index: int, descriptor: str) -> ParsedConstantValue:
        integral_kinds = {
            "B": "byte",
            "C": "char",
            "I": "int",
            "S": "short",
            "Z": "boolean",
        }
        if descriptor in integral_kinds:
            raw = self.entry(index, 3)[1]
            if not isinstance(raw, bytes):
                raise ClassFileError("integer ConstantValue payload is malformed")
            return ParsedConstantValue(
                integral_kinds[descriptor], int.from_bytes(raw, "big", signed=True)
            )
        if descriptor == "J":
            raw = self.entry(index, 5)[1]
            if not isinstance(raw, bytes):
                raise ClassFileError("long ConstantValue payload is malformed")
            return ParsedConstantValue("long", int.from_bytes(raw, "big", signed=True))
        if descriptor == "F":
            raw = self.entry(index, 4)[1]
            if not isinstance(raw, bytes):
                raise ClassFileError("float ConstantValue payload is malformed")
            return ParsedConstantValue("float-bits", raw.hex())
        if descriptor == "D":
            raw = self.entry(index, 6)[1]
            if not isinstance(raw, bytes):
                raise ClassFileError("double ConstantValue payload is malformed")
            return ParsedConstantValue("double-bits", raw.hex())
        if descriptor == "Ljava/lang/String;":
            utf8_index = self.entry(index, 8)[1]
            if not isinstance(utf8_index, int):
                raise ClassFileError("string ConstantValue payload is malformed")
            return ParsedConstantValue(
                "string", self.modified_utf8(utf8_index, "string ConstantValue")
            )
        raise ClassFileError(
            f"ConstantValue is not supported for field descriptor {descriptor!r}"
        )

    def _validate_references(self) -> None:
        for entry in self.entries[1:]:
            if entry is None:
                continue
            tag, value = entry
            if tag == 7:
                self.utf8(_integer(value), "structural class name")
            elif tag == 8:
                self.entry(_integer(value), 1)
            elif tag in {9, 10, 11}:
                owner, name_type = _pair(value)
                self.entry(owner, 7)
                self.entry(name_type, 12)
            elif tag == 12:
                name, descriptor = _pair(value)
                self.utf8(name, "member name")
                self.utf8(descriptor, "member descriptor")
            elif tag == 15:
                kind, reference = _pair(value)
                if kind < 1 or kind > 9:
                    raise ClassFileError("invalid method-handle reference kind")
                expected = (9,) if kind <= 4 else (10, 11)
                self.entry(reference, expected)
            elif tag == 16:
                _validate_method_descriptor(self.utf8(_integer(value), "method type descriptor"))
            elif tag in {17, 18}:
                _, name_type = _pair(value)
                self.entry(name_type, 12)
            elif tag in {19, 20}:
                self.entry(_integer(value), 1)


def _integer(value: object) -> int:
    if not isinstance(value, int):
        raise ClassFileError("malformed constant pool payload")
    return value


def _decode_modified_utf8(raw: bytes) -> str:
    units: list[int] = []
    offset = 0
    while offset < len(raw):
        first = raw[offset]
        if 0x01 <= first <= 0x7f:
            units.append(first)
            offset += 1
            continue
        if 0xc0 <= first <= 0xdf:
            if offset + 1 >= len(raw) or raw[offset + 1] & 0xc0 != 0x80:
                raise ClassFileError("invalid two-byte sequence")
            value = ((first & 0x1f) << 6) | (raw[offset + 1] & 0x3f)
            if value < 0x80 and value != 0:
                raise ClassFileError("overlong two-byte sequence")
            units.append(value)
            offset += 2
            continue
        if 0xe0 <= first <= 0xef:
            if offset + 2 >= len(raw) \
                    or raw[offset + 1] & 0xc0 != 0x80 \
                    or raw[offset + 2] & 0xc0 != 0x80:
                raise ClassFileError("invalid three-byte sequence")
            value = ((first & 0x0f) << 12) \
                | ((raw[offset + 1] & 0x3f) << 6) \
                | (raw[offset + 2] & 0x3f)
            if value < 0x800:
                raise ClassFileError("overlong three-byte sequence")
            units.append(value)
            offset += 3
            continue
        raise ClassFileError(f"invalid leading byte 0x{first:02x}")
    characters: list[str] = []
    index = 0
    while index < len(units):
        unit = units[index]
        if 0xd800 <= unit <= 0xdbff and index + 1 < len(units) \
                and 0xdc00 <= units[index + 1] <= 0xdfff:
            scalar = 0x10000 + ((unit - 0xd800) << 10) \
                + (units[index + 1] - 0xdc00)
            characters.append(chr(scalar))
            index += 2
            continue
        characters.append(chr(unit))
        index += 1
    return "".join(characters)


def _pair(value: object) -> tuple[int, int]:
    if not isinstance(value, tuple) or len(value) != 2 \
            or not isinstance(value[0], int) or not isinstance(value[1], int):
        raise ClassFileError("malformed constant pool pair")
    return value


def _validate_internal_name(name: str) -> None:
    if not name or name.startswith("/") or name.endswith("/") or "//" in name \
            or any(character in name for character in ".;["):
        raise ClassFileError(f"invalid internal class name {name!r}")


def _visibility(access: int) -> str:
    selected = [
        name for flag, name in (
            (ACC_PUBLIC, "public"),
            (ACC_PROTECTED, "protected"),
            (ACC_PRIVATE, "private"),
        ) if access & flag
    ]
    if len(selected) > 1:
        raise ClassFileError("conflicting visibility flags")
    return selected[0] if selected else "package"


def _validate_member_name(name: str, method: bool) -> None:
    if not name or any(character in name for character in ".;[/"):
        raise ClassFileError(f"invalid {'method' if method else 'field'} name {name!r}")
    if "<" in name or ">" in name:
        if not method or name not in {"<init>", "<clinit>"}:
            raise ClassFileError(f"invalid method name {name!r}")


def _parse_field_type(descriptor: str, offset: int, allow_void: bool = False) -> int:
    if offset >= len(descriptor):
        raise ClassFileError("truncated descriptor")
    value = descriptor[offset]
    if value in "BCDFIJSZ" or (allow_void and value == "V"):
        return offset + 1
    if value == "L":
        end = descriptor.find(";", offset + 1)
        if end < 0:
            raise ClassFileError("unterminated object descriptor")
        _validate_internal_name(descriptor[offset + 1:end])
        return end + 1
    if value == "[":
        dimensions = 0
        while offset < len(descriptor) and descriptor[offset] == "[":
            dimensions += 1
            offset += 1
        if dimensions > 255:
            raise ClassFileError("array descriptor exceeds 255 dimensions")
        return _parse_field_type(descriptor, offset, False)
    raise ClassFileError(f"invalid descriptor type {value!r}")


def _validate_field_descriptor(descriptor: str) -> None:
    try:
        end = _parse_field_type(descriptor, 0)
    except ClassFileError as failure:
        raise ClassFileError(f"invalid field descriptor {descriptor!r}: {failure}") from failure
    if end != len(descriptor):
        raise ClassFileError(f"invalid field descriptor {descriptor!r}")


def _validate_method_descriptor(
    descriptor: str,
    *,
    is_static: bool | None = None,
) -> int:
    try:
        if not descriptor.startswith("("):
            raise ClassFileError("missing parameter list")
        offset = 1
        parameter_slots = 0 if is_static is not False else 1
        while offset < len(descriptor) and descriptor[offset] != ")":
            parameter_slots += 2 if descriptor[offset] in {"J", "D"} else 1
            offset = _parse_field_type(descriptor, offset)
        if offset >= len(descriptor) or descriptor[offset] != ")":
            raise ClassFileError("unterminated parameter list")
        if is_static is not None and parameter_slots > 255:
            raise ClassFileError(
                "method descriptor exceeds 255 parameter slots"
            )
        end = _parse_field_type(descriptor, offset + 1, True)
        if end != len(descriptor):
            raise ClassFileError("trailing descriptor data")
        return parameter_slots
    except ClassFileError as failure:
        raise ClassFileError(f"invalid method descriptor {descriptor!r}: {failure}") from failure


def validate_internal_name(name: str) -> None:
    _validate_internal_name(name)


def validate_field_descriptor(descriptor: str) -> None:
    _validate_field_descriptor(descriptor)


def validate_method_descriptor(
    descriptor: str,
    *,
    is_static: bool | None = None,
) -> int:
    return _validate_method_descriptor(descriptor, is_static=is_static)


def _skip_attributes(cursor: _Cursor, pool: _ConstantPool, count: int) -> None:
    for _ in range(count):
        pool.utf8(cursor.u2(), "attribute name")
        cursor.take(cursor.u4())


def _parse_code_attribute(
    body: _Cursor,
    pool: _ConstantPool,
    required_argument_slots: int,
    method_display: str,
) -> None:
    body.u2()  # max_stack
    max_locals = body.u2()
    if max_locals < required_argument_slots:
        raise ClassFileError(
            f"Code max_locals is smaller than the {required_argument_slots} "
            f"argument slots required by {method_display}"
        )
    code_length = body.u4()
    if not 1 <= code_length <= 65535:
        raise ClassFileError("Code attribute has invalid code_length")
    body.take(code_length)
    for _ in range(body.u2()):
        start_pc = body.u2()
        end_pc = body.u2()
        handler_pc = body.u2()
        catch_type = body.u2()
        if not (start_pc < end_pc <= code_length) or handler_pc >= code_length:
            raise ClassFileError("Code attribute has invalid exception-table bounds")
        if catch_type:
            pool.class_name(catch_type)
    _skip_attributes(body, pool, body.u2())
    body.require_end("Code attribute")


def _validate_class_flags(access: int) -> None:
    allowed = ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_INTERFACE | ACC_ABSTRACT \
        | ACC_SYNTHETIC | ACC_ANNOTATION | ACC_ENUM | ACC_MODULE
    unknown = access & ~allowed
    if unknown:
        raise ClassFileError(f"unsupported class flags 0x{unknown:04x}")
    if access & ACC_INTERFACE:
        if access & ACC_FINAL:
            raise ClassFileError("interface must not be final")
        prohibited = ACC_SUPER | ACC_ENUM | ACC_MODULE
        if access & prohibited:
            raise ClassFileError("interface has incompatible class flags")
        if not access & ACC_ABSTRACT:
            raise ClassFileError("interface is not abstract")
    elif access & ACC_ANNOTATION:
        raise ClassFileError("annotation is not an interface")
    if access & ACC_FINAL and access & ACC_ABSTRACT:
        raise ClassFileError("class must not be both final and abstract")


def _validate_member_flags(
    access: int,
    name: str,
    descriptor: str,
    *,
    method: bool,
    owner_interface: bool,
) -> None:
    visibility_flags = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED
    if method:
        allowed = visibility_flags | ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED \
            | ACC_BRIDGE | ACC_VARARGS | ACC_NATIVE | ACC_ABSTRACT | ACC_SYNTHETIC
    else:
        allowed = visibility_flags | ACC_STATIC | ACC_FINAL | ACC_VOLATILE \
            | ACC_TRANSIENT | ACC_SYNTHETIC | ACC_ENUM
    unknown = access & ~allowed
    if unknown:
        raise ClassFileError(
            f"unsupported {'method' if method else 'field'} flags 0x{unknown:04x} "
            f"on {name}{descriptor}"
        )
    if method and name == "<clinit>":
        if descriptor != "()V" or not access & ACC_STATIC:
            raise ClassFileError("class initializer must be static with descriptor ()V")
        # JVMS 4.6: for class-file version 51 and later every other assigned
        # method flag on <clinit> is ignored.
        return

    _visibility(access)

    if not method:
        if owner_interface:
            required = ACC_PUBLIC | ACC_STATIC | ACC_FINAL
            if access & required != required \
                    or access & ~(required | ACC_SYNTHETIC):
                raise ClassFileError(
                    f"interface field {name}:{descriptor} must be public static final"
                )
        elif access & ACC_FINAL and access & ACC_VOLATILE:
            raise ClassFileError(
                f"field {name}:{descriptor} cannot be both final and volatile"
            )
        return

    if name == "<init>":
        if owner_interface:
            raise ClassFileError("interface must not declare a constructor")
        if not descriptor.endswith(")V"):
            raise ClassFileError("constructor descriptor must return void")
        prohibited = ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED | ACC_BRIDGE \
            | ACC_NATIVE | ACC_ABSTRACT
        if access & prohibited:
            raise ClassFileError(f"constructor has invalid flags 0x{access:04x}")
        return
    if access & ACC_ABSTRACT:
        prohibited = ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED | ACC_NATIVE
        if access & prohibited:
            raise ClassFileError(
                f"abstract method {name}{descriptor} has incompatible flags"
            )
    if owner_interface:
        visibility = access & visibility_flags
        if visibility not in {ACC_PUBLIC, ACC_PRIVATE}:
            raise ClassFileError(
                f"interface method {name}{descriptor} must be public or private"
            )
        if access & (ACC_FINAL | ACC_SYNCHRONIZED | ACC_NATIVE):
            raise ClassFileError(
                f"interface method {name}{descriptor} has incompatible flags"
            )


def _parse_members(
    cursor: _Cursor,
    pool: _ConstantPool,
    method: bool,
    owner_interface: bool,
) -> tuple[ParsedMember, ...]:
    values: list[ParsedMember] = []
    seen: set[tuple[str, str]] = set()
    for _ in range(cursor.u2()):
        access = cursor.u2()
        name = pool.utf8(cursor.u2(), "member name")
        descriptor = pool.utf8(cursor.u2(), "member descriptor")
        _validate_member_name(name, method)
        required_argument_slots = 0
        if method:
            required_argument_slots = _validate_method_descriptor(
                descriptor,
                is_static=bool(access & ACC_STATIC),
            )
        else:
            _validate_field_descriptor(descriptor)
        _validate_member_flags(
            access,
            name,
            descriptor,
            method=method,
            owner_interface=owner_interface,
        )
        key = (name, descriptor)
        if key in seen:
            raise ClassFileError(f"duplicate {'method' if method else 'field'} {name}{descriptor}")
        seen.add(key)
        constant_value = None
        constant_value_seen = False
        code_count = 0
        for _ in range(cursor.u2()):
            attribute_name = pool.utf8(cursor.u2(), "attribute name")
            body = _Cursor(cursor.take(cursor.u4()))
            if attribute_name == "ConstantValue":
                if method:
                    raise ClassFileError("ConstantValue attribute is invalid on a method")
                if constant_value_seen:
                    raise ClassFileError(f"duplicate ConstantValue on field {name}:{descriptor}")
                constant_value_seen = True
                constant_value = pool.constant_value(body.u2(), descriptor)
                body.require_end("ConstantValue attribute")
            elif attribute_name == "Code":
                if not method:
                    raise ClassFileError(f"Code attribute is invalid on field {name}:{descriptor}")
                code_count += 1
                _parse_code_attribute(
                    body,
                    pool,
                    required_argument_slots,
                    f"{name}{descriptor}",
                )
        if method:
            if name == "<clinit>":
                if code_count != 1:
                    raise ClassFileError(
                        "class initializer must declare exactly one Code attribute"
                    )
            elif access & (ACC_ABSTRACT | ACC_NATIVE):
                if code_count:
                    raise ClassFileError(
                        f"abstract or native method {name}{descriptor} "
                        "must not declare a Code attribute"
                    )
            elif code_count != 1:
                raise ClassFileError(
                    f"method {name}{descriptor} must declare exactly one Code attribute"
                )
        if method and name == "<clinit>":
            continue
        values.append(ParsedMember(
            name=name,
            descriptor=descriptor,
            visibility=_visibility(access),
            static=bool(access & ACC_STATIC),
            final=bool(access & ACC_FINAL),
            abstract=bool(access & ACC_ABSTRACT),
            synthetic=bool(access & ACC_SYNTHETIC),
            bridge=bool(access & ACC_BRIDGE),
            constant_value=constant_value,
        ))
    return tuple(sorted(values, key=lambda value: (value.name, value.descriptor)))


@dataclass(frozen=True)
class _ClassAttributes:
    self_inner: InnerClassMembership | None
    nest_host: str | None
    nest_members: tuple[str, ...]
    record: bool
    sealed: bool


def _parse_class_attributes(
    cursor: _Cursor,
    pool: _ConstantPool,
    this_class: int,
    internal_name: str,
) -> _ClassAttributes:
    self_inner = None
    nest_host = None
    nest_members: tuple[str, ...] = ()
    record = False
    sealed = False
    unique: set[str] = set()
    singleton_attributes = {
        "BootstrapMethods",
        "Deprecated",
        "EnclosingMethod",
        "InnerClasses",
        "Module",
        "ModuleMainClass",
        "ModulePackages",
        "NestHost",
        "NestMembers",
        "PermittedSubclasses",
        "Record",
        "RuntimeInvisibleAnnotations",
        "RuntimeInvisibleTypeAnnotations",
        "RuntimeVisibleAnnotations",
        "RuntimeVisibleTypeAnnotations",
        "Signature",
        "SourceDebugExtension",
        "SourceFile",
        "Synthetic",
    }
    for _ in range(cursor.u2()):
        name = pool.utf8(cursor.u2(), "attribute name")
        body = _Cursor(cursor.take(cursor.u4()))
        if name in singleton_attributes:
            if name in unique:
                raise ClassFileError(f"duplicate {name} attribute")
            unique.add(name)
            if name in {"NestHost", "NestMembers"} \
                    and {"NestHost", "NestMembers"}.issubset(unique):
                raise ClassFileError(
                    f"class {internal_name} declares both NestHost and NestMembers"
                )
        if name == "InnerClasses":
            for _ in range(body.u2()):
                inner_index = body.u2()
                outer_index = body.u2()
                inner_name_index = body.u2()
                access = body.u2()
                inner_class = pool.class_name(inner_index) if inner_index else None
                outer_class = pool.class_name(outer_index) if outer_index else None
                member_name = pool.utf8(inner_name_index, "inner class name") \
                    if inner_name_index else None
                if inner_index == this_class:
                    if self_inner is not None:
                        raise ClassFileError("duplicate self InnerClasses entry")
                    if inner_class != internal_name:
                        raise ClassFileError("self InnerClasses entry has the wrong owner")
                    self_inner = InnerClassMembership(outer_class, member_name, access)
            body.require_end("InnerClasses attribute")
        elif name == "NestHost":
            nest_host = pool.class_name(body.u2())
            body.require_end("NestHost attribute")
        elif name == "NestMembers":
            nest_members = tuple(
                pool.class_name(body.u2()) for _ in range(body.u2())
            )
            body.require_end("NestMembers attribute")
        elif name == "Record":
            record = True
            for _ in range(body.u2()):
                component_name = pool.utf8(body.u2(), "record component name")
                _validate_member_name(component_name, False)
                _validate_field_descriptor(pool.utf8(body.u2(), "record component descriptor"))
                _skip_attributes(body, pool, body.u2())
            body.require_end("Record attribute")
        elif name == "PermittedSubclasses":
            sealed = True
            for _ in range(body.u2()):
                pool.class_name(body.u2())
            body.require_end("PermittedSubclasses attribute")
        elif name == "Module":
            raise ClassFileError("module class files are unsupported")
    return _ClassAttributes(self_inner, nest_host, nest_members, record, sealed)


def parse_class_file(payload: bytes) -> ParsedClass:
    """Parse one complete Java 21 class file into binary compatibility data."""
    cursor = _Cursor(payload)
    if cursor.take(4) != b"\xca\xfe\xba\xbe":
        raise ClassFileError("invalid class magic")
    minor = cursor.u2()
    major = cursor.u2()
    if (major, minor) != (JAVA_21_MAJOR, JAVA_21_MINOR):
        raise ClassFileError(f"unsupported class version {major}.{minor}")
    pool = _ConstantPool(cursor)
    class_access = cursor.u2()
    _validate_class_flags(class_access)
    if class_access & ACC_MODULE:
        raise ClassFileError("module class files are unsupported")
    this_class = cursor.u2()
    internal_name = pool.class_name(this_class)
    super_index = cursor.u2()
    superclass = pool.class_name(super_index) if super_index else None
    interfaces = tuple(pool.class_name(cursor.u2()) for _ in range(cursor.u2()))
    if len(interfaces) != len(set(interfaces)):
        raise ClassFileError("duplicate direct interface")
    owner_interface = bool(class_access & ACC_INTERFACE)
    fields = _parse_members(cursor, pool, False, owner_interface)
    methods = _parse_members(cursor, pool, True, owner_interface)
    attributes = _parse_class_attributes(cursor, pool, this_class, internal_name)
    cursor.require_end("class file")

    if attributes.record:
        kind = "record"
    elif class_access & ACC_ENUM:
        kind = "enum"
    elif class_access & ACC_INTERFACE:
        kind = "interface"
    else:
        kind = "class"
    if kind == "record" and superclass != "java/lang/Record":
        raise ClassFileError("record has the wrong superclass")
    if kind == "interface" and not class_access & ACC_ABSTRACT:
        raise ClassFileError("interface is not abstract")
    if class_access & ACC_ANNOTATION and kind != "interface":
        raise ClassFileError("annotation is not an interface")

    visibility_access = attributes.self_inner.access if attributes.self_inner else class_access
    return ParsedClass(
        name=internal_name,
        class_access=class_access,
        visibility=_visibility(visibility_access),
        kind=kind,
        abstract=bool(class_access & ACC_ABSTRACT),
        final=bool(class_access & ACC_FINAL),
        superclass=superclass,
        interfaces=interfaces,
        fields=fields,
        methods=methods,
        self_inner=attributes.self_inner,
        nest_host=attributes.nest_host,
        nest_members=attributes.nest_members,
        sealed=attributes.sealed,
    )


def _package_name(internal_name: str) -> str:
    return internal_name.rpartition("/")[0]


def validate_local_nests(classes: dict[str, ParsedClass]) -> None:
    """Require every local NestHost/NestMembers relationship to be canonical."""
    listed_by: dict[str, list[str]] = {}
    for name in sorted(classes):
        value = classes[name]
        if value.nest_host is not None and value.nest_members:
            raise ClassFileError(
                f"class {name} declares both NestHost and NestMembers"
            )
        if value.nest_host == name:
            raise ClassFileError(f"class {name} declares itself as NestHost")
        seen_members: set[str] = set()
        for member in value.nest_members:
            if member in seen_members:
                raise ClassFileError(
                    f"duplicate NestMembers entry on {name}: {member}"
                )
            seen_members.add(member)
            if member == name:
                raise ClassFileError(
                    f"nest host {name} lists itself as a member"
                )
            if member not in classes:
                raise ClassFileError(
                    f"nest host {name} references missing member {member}"
                )
            listed_by.setdefault(member, []).append(name)

    for member, hosts in sorted(listed_by.items()):
        if len(hosts) > 1:
            raise ClassFileError(
                f"nest member {member} is listed by multiple hosts: "
                f"{', '.join(sorted(hosts))}"
            )

    for name in sorted(classes):
        value = classes[name]
        if value.nest_host is not None:
            host = classes.get(value.nest_host)
            if host is None:
                raise ClassFileError(
                    f"nest member {name} references missing host {value.nest_host}"
                )
            if _package_name(name) != _package_name(value.nest_host):
                raise ClassFileError(
                    f"nest host {value.nest_host} and member {name} "
                    "are in different packages"
                )
            if name not in host.nest_members:
                raise ClassFileError(
                    f"nest member {name} names host {value.nest_host} "
                    "but the host does not list it"
                )
        for member_name in value.nest_members:
            member = classes[member_name]
            if _package_name(name) != _package_name(member_name):
                raise ClassFileError(
                    f"nest host {name} and member {member_name} "
                    "are in different packages"
                )
            if member.nest_host != name:
                if member.nest_host is None:
                    detail = "does not name it"
                else:
                    detail = f"names {member.nest_host}"
                raise ClassFileError(
                    f"nest host {name} lists {member_name} but the member {detail}"
                )


def unsafe_zip_entry(name: str) -> bool:
    path = PurePosixPath(name)
    parts = name[:-1].split("/") if name.endswith("/") else name.split("/")
    return not name or path.is_absolute() or "\\" in name or "\x00" in name \
        or any(part in {"", ".", ".."} for part in parts) \
        or (parts and parts[0].endswith(":"))


def read_class_jar(path: Path) -> dict[str, ParsedClass]:
    """Read every root class entry, rejecting ambiguous or unsupported archives."""
    if not path.is_file():
        raise ClassFileError(f"JAR does not exist: {path}")
    parsed: dict[str, ParsedClass] = {}
    try:
        with zipfile.ZipFile(path) as archive:
            entries = archive.infolist()
            names = [entry.filename for entry in entries]
            duplicates = sorted({name for name in names if names.count(name) > 1})
            if duplicates:
                raise ClassFileError(f"duplicate JAR entry: {duplicates[0]}")
            for entry in entries:
                name = entry.filename
                if unsafe_zip_entry(name):
                    raise ClassFileError(f"unsafe JAR entry: {name}")
                if entry.flag_bits & 1 or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                    raise ClassFileError(f"unsafe JAR entry: {name}")
                if entry.is_dir() or not name.endswith(".class"):
                    continue
                if name.startswith("META-INF/versions/"):
                    raise ClassFileError(
                        f"multi-release class entries are unsupported: {name}"
                    )
                try:
                    value = parse_class_file(archive.read(entry))
                except ClassFileError as failure:
                    raise ClassFileError(f"malformed class {name}: {failure}") from failure
                expected = name[:-len(".class")]
                if value.name != expected:
                    raise ClassFileError(
                        f"class owner differs from JAR entry: {name} declares {value.name}"
                    )
                if value.name in parsed:
                    raise ClassFileError(f"duplicate class owner: {value.name}")
                if value.sealed and value.public_or_protected():
                    raise ClassFileError(
                        f"unsupported sealed public/protected type: {value.name}"
                    )
                parsed[value.name] = value
            validate_local_nests(parsed)
    except (OSError, RuntimeError, zipfile.BadZipFile) as failure:
        raise ClassFileError(f"JAR cannot be inspected: {path}: {failure}") from failure
    return dict(sorted(parsed.items()))
