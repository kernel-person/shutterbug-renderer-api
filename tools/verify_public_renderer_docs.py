#!/usr/bin/env python3
"""Compile every canonical public documentation fixture against the public API JAR."""
import argparse
import hashlib
from html import unescape
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile

PAGES = ("index.md", "setup.md", "lifecycle.md", "capture.md", "manual-scenes.md", "jobs.md", "profiles.md", "attachments.md", "conversions.md", "failures.md", "threading.md", "troubleshooting.md", "sample-walkthrough.md")
FENCE = re.compile(r'```java\n--8<-- "fixtures/([A-Za-z][A-Za-z0-9]*\.java)"\n```')
# This exact reviewed XML example is escaped by Markdown's code fence. Do not
# exempt arbitrary fences: they could hide uncompiled Java or raw HTML.
XML_SETUP_FENCE = re.compile(r"(?m)^" + re.escape('''```xml
<repositories><repository><id>jitpack.io</id><url>https://jitpack.io</url></repository></repositories>
<dependency><groupId>com.github.kernel-person</groupId><artifactId>shutterbug-renderer-api</artifactId><version>v1.0.0</version><scope>provided</scope></dependency>
```''') + r"$")
XML_SETUP_TEXT = '''<repositories><repository><id>jitpack.io</id><url>https://jitpack.io</url></repository></repositories>
<dependency><groupId>com.github.kernel-person</groupId><artifactId>shutterbug-renderer-api</artifactId><version>v1.0.0</version><scope>provided</scope></dependency>
'''
FIXTURES = ("LifecycleExample.java", "CaptureExample.java", "ManualSceneExample.java", "ConversionExample.java", "FailureExample.java", "OptionalProviderEntrypoint.java", "OptionalRendererRuntime.java")
# The exporter pins this script separately. The manifest excludes this script
# so the independently reviewed digest has no self-referential hash cycle.
PUBLICATION_MANIFEST_SHA256 = "15077b14bbf6968dfca2dad1e1ab1667d598730316d4a51a3af7e2c729aadf88"
CONTENTS = frozenset({"README.md", "mkdocs.yml", "requirements-docs.txt", *("docs/" + page for page in PAGES), *("docs/fixtures/" + fixture for fixture in FIXTURES)})


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


def expected_rendered_code(fixtures):
    """Return the complete, page-owned rendered code inventory."""
    return {
        "index.html": (),
        "setup/index.html": (XML_SETUP_TEXT,),
        "lifecycle/index.html": tuple(fixtures[name] for name in (
            "LifecycleExample.java", "OptionalProviderEntrypoint.java", "OptionalRendererRuntime.java",
        )),
        "capture/index.html": (fixtures["CaptureExample.java"],),
        "manual-scenes/index.html": (fixtures["ManualSceneExample.java"],),
        "jobs/index.html": (),
        "profiles/index.html": (),
        "attachments/index.html": (),
        "conversions/index.html": (fixtures["ConversionExample.java"],),
        "failures/index.html": (fixtures["FailureExample.java"],),
        "threading/index.html": (),
        "troubleshooting/index.html": (),
        "sample-walkthrough/index.html": (),
    }


def verify_rendered_code_inventory(site, fixtures):
    """Reject any rendered code not represented by a compiled, page-owned fixture."""
    expected = expected_rendered_code(fixtures)
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


def java_fixtures(text):
    """Own the one Java-display grammar used by both publication layouts."""
    canonical = FENCE.findall(text)
    remaining = FENCE.sub("", text)
    if "--8<--" in remaining or re.search(
        r"(?im)^(?:`{3,}|~{3,})(?:\s*java\b|[^\n]*\{[^}\n]*\.java\b[^}\n]*\})", remaining,
    ):
        raise ValueError("noncanonical Java documentation display")
    html = DocumentationHTML(convert_charrefs=True)
    html_source = unescape(XML_SETUP_FENCE.sub("", remaining))
    html.feed(html_source)
    html.close()
    # Some Python HTMLParser versions silently discard an unfinished tag.
    if "<" in html_source:
        html._reject()
    return canonical

def publication_path(name):
    if name in {"README.md", "mkdocs.yml", "requirements-docs.txt"}: return Path(name)
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
    expected={*PAGES,*("fixtures/" + fixture for fixture in FIXTURES)}
    if set(files) != expected: parser.error("public documentation inventory is invalid")
    return files
def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument("--api-jar",type=Path,required=True); p.add_argument("--bukkit-jar",type=Path,required=True); p.add_argument("--docs",type=Path,default=Path("docs")); a=p.parse_args()
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
    with tempfile.TemporaryDirectory() as out:
        site = Path(out) / "site"
        run = subprocess.run(
            ["mkdocs", "build", "--strict", "--site-dir", str(site)],
            capture_output=True, text=True,
        )
        if run.returncode:
            p.error("strict MkDocs rendering failed")
        try:
            verify_rendered_code_inventory(
                site, {name: docs["fixtures/" + name].decode("utf-8") for name in FIXTURES},
            )
        except (UnicodeDecodeError, ValueError) as error:
            p.error(str(error))
    print(f"Verified {len(names)} public Java documentation fixtures.")
if __name__ == "__main__": main()
