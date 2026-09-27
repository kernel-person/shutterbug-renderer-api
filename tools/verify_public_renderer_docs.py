#!/usr/bin/env python3
"""Compile every canonical public documentation fixture against the public API JAR."""
import argparse
import ctypes
import errno
import hashlib
from html import unescape
from html.parser import HTMLParser
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile

PAGES = ("index.md", "operator-guide.md", "compatibility.md", "troubleshooting.md", "upgrading.md",
         "release-notes.md", "setup.md", "lifecycle.md", "capture.md", "manual-scenes.md", "assets.md",
         "jobs.md", "profiles.md", "attachments.md", "conversions.md", "failures.md", "threading.md",
         "api-reference.md", "sample-walkthrough.md")
FENCE = re.compile(r'```java\n--8<-- "fixtures/([A-Za-z][A-Za-z0-9]*\.java)"\n```')
BUILD_FENCE = re.compile(r'```(?:xml|groovy)\n--8<-- "(consumer/(?:maven/pom\.xml|gradle/build\.gradle))"\n```')
FIXTURES = ("ConsumerExample.java", "LifecycleExample.java", "CaptureExample.java", "ManualSceneExample.java",
            "AssetExample.java", "ResultInspectionExample.java", "ConversionExample.java", "FailureExample.java",
            "RecoveryExample.java", "OptionalProviderEntrypoint.java", "OptionalRendererRuntime.java")
CONSUMER_FILES = ("consumer/maven/pom.xml", "consumer/gradle/build.gradle")
# The exporter pins this script separately. The manifest excludes this script
# so the independently reviewed digest has no self-referential hash cycle.
PUBLICATION_MANIFEST_SHA256 = "8d736707b6250cc72c44f24fd9a62ccedf3ea8cacb5f69c0238c96d0b17ccd54"
CONTENTS = frozenset({"README.md", "mkdocs.yml", "requirements-docs.txt", "documentation-contract.json",
                      *("docs/" + page for page in PAGES),
                      *("docs/fixtures/" + fixture for fixture in FIXTURES),
                      *("docs/" + path for path in CONSUMER_FILES)})
JAVADOC_SITE_BASE = "https://kernel-person.github.io/shutterbug-renderer-api/"


class DocumentationHTML(HTMLParser):
    """No raw HTML tokens are allowed in documentation Markdown source."""

    def _reject(self, *args):
        raise ValueError("raw HTML is forbidden in documentation; use Markdown and canonical Java fixture includes")

    handle_starttag = _reject
    handle_startendtag = _reject
    handle_endtag = _reject
    handle_comment = _reject
    handle_decl = _reject
    unknown_decl = _reject
    handle_pi = _reject

    def handle_data(self, data):
        # The tokenizer can emit unfinished markup or decoded entities as
        # text. Reject residual opening delimiters instead of passing them on.
        if "<" in data:
            self._reject()


class RenderedCodeHTML(HTMLParser):
    """Collect every preformatted block emitted inside MkDocs page content."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.article_depth = 0
        self.pre_depth = 0
        self.current = []
        self.blocks = []

    def handle_starttag(self, tag, attrs):
        if tag == "article":
            self.article_depth += 1
        elif tag == "pre" and self.article_depth:
            if self.pre_depth:
                raise ValueError("rendered code inventory contains nested preformatted content")
            self.pre_depth = 1
            self.current = []

    def handle_endtag(self, tag):
        if tag == "pre" and self.pre_depth:
            self.blocks.append("".join(self.current))
            self.pre_depth = 0
            self.current = []
        elif tag == "article" and self.article_depth:
            self.article_depth -= 1

    def handle_data(self, data):
        if self.pre_depth:
            self.current.append(data)


def expected_rendered_code(fixtures, consumers):
    """Return the complete, page-owned rendered code inventory."""
    return {
        "index.html": (),
        "operator-guide/index.html": (),
        "compatibility/index.html": (),
        "troubleshooting/index.html": (),
        "upgrading/index.html": (),
        "release-notes/index.html": (),
        "setup/index.html": (consumers["consumer/maven/pom.xml"],
                             consumers["consumer/gradle/build.gradle"],
                             fixtures["ConsumerExample.java"]),
        "lifecycle/index.html": tuple(fixtures[name] for name in (
            "LifecycleExample.java", "OptionalProviderEntrypoint.java", "OptionalRendererRuntime.java",
        )),
        "capture/index.html": (fixtures["CaptureExample.java"],),
        "manual-scenes/index.html": (fixtures["ManualSceneExample.java"],),
        "assets/index.html": (fixtures["AssetExample.java"],),
        "jobs/index.html": (),
        "profiles/index.html": (),
        "attachments/index.html": (fixtures["ResultInspectionExample.java"],),
        "conversions/index.html": (fixtures["ConversionExample.java"],),
        "failures/index.html": (fixtures["FailureExample.java"], fixtures["RecoveryExample.java"]),
        "threading/index.html": (),
        "api-reference/index.html": (),
        "sample-walkthrough/index.html": (),
    }


def verify_rendered_code_inventory(site, fixtures, consumers):
    """Reject any rendered code not represented by a compiled, page-owned fixture."""
    expected = expected_rendered_code(fixtures, consumers)
    for relative, wanted in expected.items():
        path = Path(site) / relative
        try:
            source = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as error:
            raise ValueError("rendered code inventory is incomplete") from error
        parser = RenderedCodeHTML()
        parser.feed(source)
        parser.close()
        if parser.pre_depth or tuple(parser.blocks) != wanted:
            raise ValueError("rendered code inventory differs from compiled fixtures")


def javadoc_links(reference):
    """Return the deployable site paths from the canonical public type reference."""
    links = tuple(
        target.removeprefix(JAVADOC_SITE_BASE)
        for target in re.findall(r"\[[^]]+\]\(([^)#]+)(?:#[^)]+)?\)", reference)
        if target.startswith(JAVADOC_SITE_BASE + "apidocs/"))
    if not links or len(links) != len(set(links)):
        raise ValueError("public Javadoc link inventory is missing or duplicated")
    return links


def _absolute_path(path):
    """Return a lexical absolute path without following any filesystem entry."""
    return Path(os.path.abspath(os.fspath(path)))


def _entry_identity(entry):
    return entry.st_dev, entry.st_ino, stat.S_IFMT(entry.st_mode)


def _same_entry(left, right):
    return _entry_identity(left) == _entry_identity(right)


def _open_absolute_directory(path, label):
    """Open every directory component with O_NOFOLLOW and return the leaf descriptor."""
    path = _absolute_path(path)
    descriptor = None
    try:
        descriptor = open_directory(path.anchor)
        for component in path.parts[1:]:
            child = open_directory(component, dir_fd=descriptor)
            os.close(descriptor)
            descriptor = child
        return descriptor
    except OSError as error:
        if descriptor is not None:
            os.close(descriptor)
        raise ValueError(f"{label} has a missing or unsafe path component") from error


def _open_regular_at(directory, name, before, label):
    flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK
    try:
        descriptor = os.open(name, flags, dir_fd=directory)
    except OSError as error:
        raise ValueError(f"{label} is unsafe") from error
    opened = os.fstat(descriptor)
    if not stat.S_ISREG(opened.st_mode) or not _same_entry(before, opened):
        os.close(descriptor)
        raise ValueError(f"{label} is unsafe")
    return descriptor, opened


def _walk_regular_tree(directory, label, visit_file=None, destination=None):
    with os.scandir(directory) as entries:
        for entry in entries:
            before = entry.stat(follow_symlinks=False)
            if stat.S_ISDIR(before.st_mode):
                try:
                    child = open_directory(entry.name, dir_fd=directory)
                except OSError as error:
                    raise ValueError(f"{label} is unsafe") from error
                try:
                    if not _same_entry(before, os.fstat(child)):
                        raise ValueError(f"{label} is unsafe")
                    child_destination = None
                    if destination is not None:
                        os.mkdir(entry.name, 0o755, dir_fd=destination)
                        child_destination = open_directory(entry.name, dir_fd=destination)
                    try:
                        _walk_regular_tree(
                            child, label, visit_file=visit_file,
                            destination=child_destination)
                    finally:
                        if child_destination is not None:
                            os.close(child_destination)
                finally:
                    os.close(child)
            elif stat.S_ISREG(before.st_mode):
                descriptor, opened = _open_regular_at(
                    directory, entry.name, before, label)
                try:
                    if visit_file is not None:
                        visit_file(
                            directory, entry.name, descriptor, opened,
                            destination, label)
                    after = os.fstat(descriptor)
                    named = os.stat(entry.name, dir_fd=directory, follow_symlinks=False)
                    if not _same_entry(opened, after) or not _same_entry(after, named) \
                            or (opened.st_size, opened.st_mtime_ns, opened.st_ctime_ns) \
                            != (after.st_size, after.st_mtime_ns, after.st_ctime_ns):
                        raise ValueError(f"{label} changed while it was being read")
                finally:
                    os.close(descriptor)
            else:
                raise ValueError(f"{label} is unsafe")


def _verified_regular_tree(root, label):
    root = _absolute_path(root)
    descriptor = _open_absolute_directory(root, label)
    try:
        _walk_regular_tree(descriptor, label)
    finally:
        os.close(descriptor)
    return root


def _copy_open_regular_file(source_directory, name, source, opened,
                            destination_directory, label):
    del source_directory
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW
    try:
        destination = os.open(
            name, flags, 0o644, dir_fd=destination_directory)
    except OSError as error:
        raise ValueError("Javadoc site destination is unsafe") from error
    try:
        if not stat.S_ISREG(os.fstat(destination).st_mode):
            raise ValueError("Javadoc site destination is unsafe")
        while chunk := os.read(source, 1024 * 1024):
            remaining = memoryview(chunk)
            while remaining:
                written = os.write(destination, remaining)
                remaining = remaining[written:]
        os.fsync(destination)
    finally:
        os.close(destination)


def _trees_overlap(left, right):
    left = _absolute_path(left)
    right = _absolute_path(right)
    common = Path(os.path.commonpath((left, right)))
    if common == left or common == right:
        return True
    left_chain, left_exists = _directory_identity_chain(
        left, "documentation overlap input", allow_missing=True)
    right_chain, right_exists = _directory_identity_chain(
        right, "documentation overlap input", allow_missing=True)
    left_leaf = left_chain[-1] if left_exists else None
    right_leaf = right_chain[-1] if right_exists else None
    return (left_leaf is not None and left_leaf in right_chain) \
        or (right_leaf is not None and right_leaf in left_chain)


def _directory_identity_chain(path, label, *, allow_missing=False):
    """Return no-follow identities for each existing component of a directory path."""
    path = _absolute_path(path)
    descriptor = None
    identities = []
    exists = True
    try:
        descriptor = open_directory(path.anchor)
        identities.append(_entry_identity(os.fstat(descriptor)))
        for component in path.parts[1:]:
            try:
                child = open_directory(component, dir_fd=descriptor)
            except FileNotFoundError:
                if not allow_missing:
                    raise
                exists = False
                break
            os.close(descriptor)
            descriptor = child
            identities.append(_entry_identity(os.fstat(descriptor)))
    except OSError as error:
        raise ValueError(f"{label} has a missing or unsafe path component") from error
    finally:
        if descriptor is not None:
            os.close(descriptor)
    return tuple(identities), exists


def assemble_generated_javadocs(site, generated, links):
    """Copy generated Javadocs into the deployable MkDocs tree and verify every link."""
    site = _verified_regular_tree(site, "rendered documentation site")
    generated = _verified_regular_tree(generated, "generated Javadocs")
    if _trees_overlap(site, generated) or _trees_overlap(site / "apidocs", generated):
        raise ValueError("generated Javadocs overlap the documentation site destination")
    destination = site / "apidocs"
    if os.path.lexists(destination):
        before = destination.lstat()
        if stat.S_ISLNK(before.st_mode) or not stat.S_ISDIR(before.st_mode):
            raise ValueError("Javadoc site destination is unsafe")
        shutil.rmtree(destination)
    site_descriptor = _open_absolute_directory(site, "rendered documentation site")
    generated_descriptor = _open_absolute_directory(generated, "generated Javadocs")
    try:
        os.mkdir("apidocs", 0o755, dir_fd=site_descriptor)
        destination_descriptor = open_directory("apidocs", dir_fd=site_descriptor)
        try:
            _walk_regular_tree(
                generated_descriptor, "generated Javadocs",
                visit_file=_copy_open_regular_file,
                destination=destination_descriptor)
        finally:
            os.close(destination_descriptor)
    finally:
        os.close(generated_descriptor)
        os.close(site_descriptor)
    for link in links:
        relative = PurePosixPath(link)
        if relative.is_absolute() or ".." in relative.parts or not relative.parts \
                or relative.parts[0] != "apidocs" \
                or not (site.joinpath(*relative.parts)).is_file():
            raise ValueError(f"Javadoc link target is missing: {link}")


def _site_state(parent, name):
    try:
        entry = os.stat(name, dir_fd=parent, follow_symlinks=False)
    except FileNotFoundError:
        return None
    if stat.S_ISLNK(entry.st_mode) or not stat.S_ISDIR(entry.st_mode):
        raise ValueError("rendered documentation site is unsafe")
    return _entry_identity(entry)


def _approve_site_destination(site):
    site = _absolute_path(site)
    if not site.name:
        raise ValueError("rendered documentation site is unsafe")
    parent = _open_absolute_directory(
        site.parent, "rendered documentation site parent")
    approved_site_descriptor = None
    try:
        approved_site = _site_state(parent, site.name)
        if approved_site is not None:
            approved_site_descriptor = open_directory(site.name, dir_fd=parent)
            if _entry_identity(os.fstat(approved_site_descriptor)) != approved_site:
                raise ValueError("rendered documentation site changed during approval")
        return (site, parent, _entry_identity(os.fstat(parent)), approved_site,
                approved_site_descriptor)
    except BaseException:
        if approved_site_descriptor is not None:
            os.close(approved_site_descriptor)
        os.close(parent)
        raise


def _new_private_directory(directory, prefix):
    for _ in range(32):
        name = f".{prefix}-{secrets.token_hex(16)}"
        try:
            os.mkdir(name, 0o700, dir_fd=directory)
        except FileExistsError:
            continue
        descriptor = None
        try:
            created = os.stat(name, dir_fd=directory, follow_symlinks=False)
            descriptor = open_directory(name, dir_fd=directory)
            identity = _entry_identity(os.fstat(descriptor))
            if not stat.S_ISDIR(created.st_mode) or identity != _entry_identity(created):
                raise ValueError("private documentation staging directory changed")
            return name, descriptor, identity
        except BaseException:
            if descriptor is not None:
                os.close(descriptor)
            raise
    raise ValueError("private documentation staging directory could not be created")


def _rename_noreplace(source, destination, *, src_dir_fd, dst_dir_fd):
    """Atomically move one descriptor-relative entry without replacing a destination."""
    source_bytes = os.fsencode(source)
    destination_bytes = os.fsencode(destination)
    if not source_bytes or not destination_bytes \
            or any(token in source_bytes or token in destination_bytes
                   for token in (b"/", b"\\", b"\0")) \
            or source_bytes in {b".", b".."} \
            or destination_bytes in {b".", b".."}:
        raise ValueError("documentation move entry name is unsafe")

    library = ctypes.CDLL(None, use_errno=True)
    if sys.platform == "darwin":
        operation = getattr(library, "renameatx_np", None)
        flag = 0x00000004  # RENAME_EXCL
    elif sys.platform.startswith("linux"):
        operation = getattr(library, "renameat2", None)
        flag = 0x00000001  # RENAME_NOREPLACE
    elif os.name == "nt" and os.rename in os.supports_dir_fd:
        # Windows rename is no-clobber. Descriptor-relative support is required;
        # otherwise this safety boundary fails closed below.
        os.rename(
            source, destination,
            src_dir_fd=src_dir_fd, dst_dir_fd=dst_dir_fd)
        return
    else:
        operation = None
        flag = 0
    if operation is None:
        raise ValueError("atomic no-clobber documentation move is unavailable")

    operation.argtypes = (
        ctypes.c_int, ctypes.c_char_p, ctypes.c_int, ctypes.c_char_p,
        ctypes.c_uint,
    )
    operation.restype = ctypes.c_int
    ctypes.set_errno(0)
    if operation(
            src_dir_fd, source_bytes, dst_dir_fd, destination_bytes, flag) == 0:
        return
    failure = ctypes.get_errno()
    if failure in {errno.EEXIST, errno.ENOTEMPTY}:
        raise FileExistsError(
            failure, os.strerror(failure), destination)
    raise OSError(failure, os.strerror(failure), source)


def _remove_tree_at(directory, name, expected=None):
    """Quarantine and remove one approved tree without deleting a swapped name."""
    try:
        before = os.stat(name, dir_fd=directory, follow_symlinks=False)
    except FileNotFoundError:
        return
    if expected is None:
        raise ValueError("documentation staging cleanup requires an approved identity")
    if _entry_identity(before) != expected or not stat.S_ISDIR(before.st_mode):
        raise ValueError("documentation staging cleanup target changed")

    approved = None
    quarantine = None
    quarantine_descriptor = None
    try:
        try:
            approved = open_directory(name, dir_fd=directory)
        except OSError as error:
            raise ValueError("documentation staging cleanup is unsafe") from error
        if _entry_identity(os.fstat(approved)) != expected:
            raise ValueError("documentation staging cleanup target changed")

        quarantine, quarantine_descriptor, _ = _new_private_directory(
            directory, f"{name}.renderer-docs-cleanup")
        _rename_noreplace(
            name, "target",
            src_dir_fd=directory, dst_dir_fd=quarantine_descriptor)
        moved = os.stat(
            "target", dir_fd=quarantine_descriptor, follow_symlinks=False)
        quarantined_identity = _entry_identity(moved)
        approved_changed = _entry_identity(os.fstat(approved)) != expected
        if quarantined_identity != expected or approved_changed:
            # The public name is attacker-visible. Never restore through it after a
            # mismatch: even an absent-name check would leave an overwrite race.
            raise ValueError(
                "documentation staging cleanup target changed; moved entry "
                f"is preserved in {quarantine}/target")

        # Preserve the complete validated tree under the random quarantine.
        # Portable filesystems provide neither compare-and-unlink nor
        # compare-and-rmdir, so even recursively emptying this entry would
        # reintroduce the same last-path-mutation race at a child name.
    finally:
        if approved is not None:
            os.close(approved)
        if quarantine_descriptor is not None:
            os.close(quarantine_descriptor)


def _copy_site_to_approved_parent(staged, approval):
    site, parent, _, _, _ = approval
    sibling, destination, sibling_identity = _new_private_directory(
        parent, f"{site.name}.renderer-docs-stage")
    source = None
    try:
        source = _open_absolute_directory(staged, "assembled documentation site")
        _walk_regular_tree(
            source, "assembled documentation site",
            visit_file=_copy_open_regular_file, destination=destination)
        os.fchmod(destination, 0o755)
    except BaseException:
        if source is not None:
            os.close(source)
        if destination is not None:
            os.close(destination)
        _remove_tree_at(parent, sibling, sibling_identity)
        raise
    os.close(source)
    return sibling, destination, sibling_identity


def _revalidate_site_destination(approval):
    site, parent, approved_parent, approved_site, approved_site_descriptor = approval
    current_parent = _open_absolute_directory(
        site.parent, "rendered documentation site parent")
    try:
        approved_leaf_changed = approved_site is not None and (
            approved_site_descriptor is None
            or _entry_identity(os.fstat(approved_site_descriptor)) != approved_site)
        if _entry_identity(os.fstat(current_parent)) != approved_parent \
                or _site_state(current_parent, site.name) != approved_site \
                or _site_state(parent, site.name) != approved_site \
                or approved_leaf_changed:
            raise ValueError("rendered documentation site changed before replacement")
    finally:
        os.close(current_parent)


def _replace_approved_site(sibling, approval):
    site, parent, _, approved_site, approved_site_descriptor = approval
    sibling_name, sibling_descriptor, sibling_identity = sibling
    _revalidate_site_destination(approval)
    backup = None
    backup_descriptor = None
    moved_previous = False

    def restore_previous_site():
        nonlocal moved_previous
        if not moved_previous:
            return True
        try:
            if _site_state(backup_descriptor, "site") != approved_site \
                    or _entry_identity(os.fstat(approved_site_descriptor)) != approved_site:
                return False
            _rename_noreplace(
                "site", site.name,
                src_dir_fd=backup_descriptor, dst_dir_fd=parent)
            if _site_state(parent, site.name) != approved_site:
                return False
        except BaseException:
            return False
        moved_previous = False
        return True

    try:
        if approved_site is not None:
            backup, backup_descriptor, _ = _new_private_directory(
                parent, f"{site.name}.renderer-docs-previous")
            _rename_noreplace(
                site.name, "site",
                src_dir_fd=parent, dst_dir_fd=backup_descriptor)
            moved_identity = _site_state(backup_descriptor, "site")
            if moved_identity != approved_site \
                    or _entry_identity(os.fstat(approved_site_descriptor)) != approved_site:
                raise ValueError(
                    "rendered documentation site changed during replacement; "
                    f"moved entry is preserved in {site.parent / backup / 'site'}")
            moved_previous = True
        try:
            if _entry_identity(os.fstat(sibling_descriptor)) != sibling_identity \
                    or _site_state(parent, sibling_name) != sibling_identity:
                raise ValueError("documentation staging directory changed before installation")
            _rename_noreplace(
                sibling_name, site.name,
                src_dir_fd=parent, dst_dir_fd=parent)
            if _entry_identity(os.fstat(sibling_descriptor)) != sibling_identity \
                    or _site_state(parent, site.name) != sibling_identity:
                raise ValueError("documentation site changed during installation")
        except BaseException as failure:
            if moved_previous and not restore_previous_site():
                raise ValueError(
                    "documentation site replacement failed; previous site "
                    f"is preserved in {site.parent / backup}") from failure
            raise
        if moved_previous:
            _remove_tree_at(backup_descriptor, "site", approved_site)
            moved_previous = False
    finally:
        if backup_descriptor is not None:
            if moved_previous:
                restore_previous_site()
            os.close(backup_descriptor)
        # Retain the random backup as a recovery quarantine. Removing it by public
        # pathname after closing its descriptor would reintroduce a last-rmdir race.
    return False


def build_documentation_site(site, generated, links, fixtures, consumers):
    """Render, assemble, verify, and replace one approved documentation site."""
    approval = _approve_site_destination(site)
    site = approval[0]
    sibling = None
    try:
        generated = _verified_regular_tree(generated, "generated Javadocs")
        if _trees_overlap(site, generated) or _trees_overlap(site / "apidocs", generated):
            raise ValueError("generated Javadocs overlap the documentation site destination")
        with tempfile.TemporaryDirectory(prefix="renderer-docs-site-") as temporary:
            staging_root = Path(temporary).resolve()
            staged = staging_root / "site"
            run = subprocess.run(
                ["mkdocs", "build", "--strict", "--site-dir", str(staged)],
                capture_output=True, text=True,
            )
            if run.returncode:
                raise ValueError("strict MkDocs rendering failed")
            _verified_regular_tree(staged, "rendered documentation site")
            verify_rendered_code_inventory(staged, fixtures, consumers)
            assemble_generated_javadocs(staged, generated, links)
            _verified_regular_tree(staged, "assembled documentation site")
            sibling = _copy_site_to_approved_parent(staged, approval)
        sibling_remains = _replace_approved_site(sibling, approval)
        sibling_name, sibling_descriptor, sibling_identity = sibling
        os.close(sibling_descriptor)
        sibling = None
        if sibling_remains:
            _remove_tree_at(approval[1], sibling_name, sibling_identity)
    finally:
        if sibling is not None:
            try:
                _remove_tree_at(approval[1], sibling[0], sibling[2])
            finally:
                os.close(sibling[1])
        if approval[4] is not None:
            os.close(approval[4])
        os.close(approval[1])


def java_fixtures(text):
    """Own the one Java-display grammar used by both publication layouts."""
    canonical = FENCE.findall(text)
    remaining = BUILD_FENCE.sub("", FENCE.sub("", text))
    if "--8<--" in remaining or re.search(
        r"(?im)^(?:`{3,}|~{3,})(?:\s*java\b|[^\n]*\{[^}\n]*\.java\b[^}\n]*\})", remaining,
    ):
        raise ValueError("noncanonical Java documentation display")
    html = DocumentationHTML(convert_charrefs=True)
    html_source = unescape(remaining)
    html.feed(html_source)
    html.close()
    # Some Python HTMLParser versions silently discard an unfinished tag.
    if "<" in html_source:
        html._reject()
    return canonical

def publication_path(name):
    if name in {"README.md", "mkdocs.yml", "requirements-docs.txt", "documentation-contract.json"}: return Path(name)
    if name.startswith("docs/"): return Path(name)
    raise ValueError("publication manifest has an unsupported source path")

def open_directory(path, *, dir_fd=None):
    return os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=dir_fd)


def read_file(name, directory):
    descriptor = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
    try:
        if not stat.S_ISREG(os.fstat(descriptor).st_mode):
            raise ValueError("non-regular documentation input")
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            return stream.read()
    finally:
        os.close(descriptor)


def regular(path, parser):
    directory = None
    try:
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("unbound publication path")
        directory = open_directory(".")
        for component in path.parts[:-1]:
            child = open_directory(component, dir_fd=directory)
            os.close(directory)
            directory = child
        return read_file(path.name, directory)
    except (OSError, ValueError):
        parser.error("publication manifest content is missing or unsafe")
    finally:
        if directory is not None:
            os.close(directory)

def docs_inventory(docs, parser):
    files = {}

    def walk(directory, prefix):
        with os.scandir(directory) as entries:
            for entry in entries:
                name = prefix / entry.name
                mode = entry.stat(follow_symlinks=False).st_mode
                if stat.S_ISDIR(mode):
                    child = open_directory(entry.name, dir_fd=directory)
                    try:
                        walk(child, name)
                    finally:
                        os.close(child)
                elif stat.S_ISREG(mode):
                    files[name.as_posix()] = read_file(entry.name, directory)
                else:
                    raise ValueError("non-regular documentation input")

    try:
        root = open_directory(docs)
        try:
            walk(root, Path())
        finally:
            os.close(root)
    except (OSError, ValueError):
        parser.error("public documentation inventory is unsafe")
    expected={*PAGES,*("fixtures/" + fixture for fixture in FIXTURES),*CONSUMER_FILES}
    if set(files) != expected: parser.error("public documentation inventory is invalid")
    return files
def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument("--api-jar",type=Path,required=True); p.add_argument("--bukkit-jar",type=Path,required=True); p.add_argument("--docs",type=Path,default=Path("docs")); p.add_argument("--javadocs",type=Path,default=Path("target/apidocs")); p.add_argument("--site-dir",type=Path,default=Path("target/site")); a=p.parse_args()
    if a.docs.absolute() != Path("docs").absolute(): p.error("docs must be the publication-manifest-bound docs root")
    raw=regular(Path("publication-manifest.json"),p)
    if hashlib.sha256(raw).hexdigest() != PUBLICATION_MANIFEST_SHA256: p.error("publication manifest differs from reviewed authority")
    def unique(pairs):
        if len({key for key, _ in pairs}) != len(pairs): raise ValueError("duplicate key")
        return dict(pairs)
    try: manifest=json.loads(raw.decode("utf-8"),object_pairs_hook=unique)
    except (UnicodeDecodeError,ValueError): p.error("publication manifest is malformed")
    contents=manifest.get("contents") if isinstance(manifest,dict) and set(manifest)=={"format","contents"} and manifest.get("format")==1 else None
    if not isinstance(contents,dict) or set(contents)!=CONTENTS: p.error("publication manifest inventory is invalid")
    docs = docs_inventory(a.docs,p)
    for name,digest in contents.items():
        try: path=publication_path(name)
        except ValueError as error: p.error(str(error))
        value = docs[name.removeprefix("docs/")] if name.startswith("docs/") else regular(path,p)
        if not isinstance(digest,str) or hashlib.sha256(value).hexdigest()!=digest: p.error("publication manifest content hash mismatch")
    names=[]
    for page in PAGES:
        try:
            names.extend(java_fixtures(docs[page].decode("utf-8")))
        except (UnicodeDecodeError, ValueError) as error:
            p.error(str(error))
    if not names or len(names)!=len(set(names)): p.error("fixture inventory is missing or duplicated")
    if set(names) != set(FIXTURES): p.error("fixture include inventory is invalid")
    if not a.api_jar.is_file() or not a.bukkit_jar.is_file(): p.error("API or Bukkit input is missing")
    with tempfile.TemporaryDirectory() as out:
        # Compile the exact verified snapshot, not paths reopened after hashing.
        files = [Path(out) / name for name in names]
        for name, path in zip(names, files):
            path.write_bytes(docs["fixtures/" + name])
        run=subprocess.run(["javac","--release","21","-cp",f"{a.api_jar}:{a.bukkit_jar}","-d",out,*map(str,files)],capture_output=True,text=True)
    if run.returncode: p.error("fixture compilation failed")
    try:
        build_documentation_site(
            a.site_dir, a.javadocs,
            javadoc_links(docs["api-reference.md"].decode("utf-8")),
            {name: docs["fixtures/" + name].decode("utf-8") for name in FIXTURES},
            {name: docs[name].decode("utf-8") for name in CONSUMER_FILES},
        )
    except (OSError, UnicodeDecodeError, ValueError) as error:
        p.error(str(error))
    print(f"Verified {len(names)} public Java documentation fixtures.")
if __name__ == "__main__": main()
