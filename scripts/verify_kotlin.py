#!/usr/bin/env python3
"""
Static verification for Daybook's Kotlin sources.

There is no Kotlin compiler and no Android SDK in this environment, so this
script stands in for `./gradlew build`. It reads every .kt file and every
resource file using nothing but the standard library, and reports the classes of
mistake a compiler would reject: resource ids that do not exist, format
arguments that do not match, imports that resolve to nothing, references to
helpers that were never written, duplicate declarations, and files that do not
brace-balance.

Checks
  1 resource     R.string / R.drawable / R.mipmap / R.color names exist
  2 format       stringResource/getString supply the arguments the string needs
  3 import       every com.sr2ma.daybook.* import resolves to a declaration
  4 symbol       every capitalised reference resolves (the most valuable check)
  5 unused       imported name never used again in the file        (WARN only)
  6 duplicate    the same top-level name declared twice in a package
  7 compose      no stringResource/painterResource in a non-composable scope
  8 balance      braces, parens and brackets pair up in every file

Usage
  python3 scripts/verify_kotlin.py [--json] [--errors-only] [--root DIR]

Exit code is 1 when any ERROR is reported, 0 otherwise. WARN never fails the
run: warnings are advisory and deliberately chatty.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

APP_PACKAGE = "com.sr2ma.daybook"

# ===========================================================================
# ALLOWLIST
# ---------------------------------------------------------------------------
# Names that are legal *without* an import, i.e. Kotlin/JVM's default imports
# (kotlin.*, kotlin.annotation.*, kotlin.collections.*, kotlin.comparisons.*,
# kotlin.io.*, kotlin.ranges.*, kotlin.sequences.*, kotlin.text.*, kotlin.jvm.*)
# plus java.lang.*.
#
# Everything else -- AndroidX, Compose, java.time, org.json, org.junit -- has to
# be imported in Kotlin, and check 3 proves each of those imports exists, so
# keeping this list *small* is exactly what makes check 4 worth running. Add a
# name here only when it genuinely needs no import.
# ===========================================================================
ALLOWED_UNQUALIFIED: set[str] = {
    # kotlin: core types
    "Any", "Nothing", "Unit", "Enum", "Annotation", "Boolean", "Byte", "Char",
    "CharSequence", "Comparable", "Double", "Float", "Function", "Int", "Long",
    "Number", "Short", "String", "Throwable", "Cloneable",
    # kotlin: arrays and unsigned
    "Array", "BooleanArray", "ByteArray", "CharArray", "DoubleArray",
    "FloatArray", "IntArray", "LongArray", "ShortArray", "UByte", "UInt",
    "ULong", "UShort", "UByteArray", "UIntArray", "ULongArray", "UShortArray",
    # kotlin: collections
    "ArrayDeque", "ArrayList", "Collection", "HashMap", "HashSet", "Iterable",
    "Iterator", "LinkedHashMap", "LinkedHashSet", "List", "ListIterator", "Map",
    "MutableCollection", "MutableIterable", "MutableIterator", "MutableList",
    "MutableListIterator", "MutableMap", "MutableSet", "Set", "Grouping",
    "IndexedValue",
    # kotlin: comparisons, ranges, sequences, text, io
    "Comparator", "CharRange", "ClosedRange", "IntRange", "LongRange",
    "OpenEndRange", "Sequence", "Appendable", "Charsets", "MatchResult",
    "Regex", "RegexOption", "StringBuilder", "Typography",
    # kotlin: misc stdlib
    "Lazy", "LazyThreadSafetyMode", "Pair", "Triple", "Result", "Exception",
    "Error", "RuntimeException", "DeprecationLevel", "ReplaceWith",
    "KotlinVersion", "Metadata",
    # kotlin: annotations that need no import
    "Deprecated", "JvmField", "JvmInline", "JvmName", "JvmOverloads",
    "JvmStatic", "JvmSuppressWildcards", "JvmWildcard", "MustBeDocumented",
    "OptIn", "PublishedApi", "Repeatable", "RequiresOptIn", "Retention",
    "SinceKotlin", "Strictfp", "Suppress", "Synchronized", "Target", "Throws",
    "Transient", "Volatile", "ExperimentalStdlibApi",
    "ExperimentalUnsignedTypes", "DslMarker",
    # java.lang
    "AssertionError", "ArithmeticException", "ArrayIndexOutOfBoundsException",
    "AutoCloseable", "Character", "Class", "ClassCastException",
    "ClassNotFoundException", "IllegalAccessException",
    "IllegalArgumentException", "IllegalStateException",
    "IndexOutOfBoundsException", "InterruptedException", "Integer", "Math",
    "NoSuchElementException", "NullPointerException", "NumberFormatException",
    "Object", "OutOfMemoryError", "Process", "Runnable", "Runtime",
    "SecurityException", "StackOverflowError", "StackTraceElement",
    "StringBuffer", "System", "Thread", "ThreadLocal",
    "UnsupportedOperationException", "Void",
}

# Imported simple names that legitimately never appear again in the file: the
# compiler resolves operator and delegate conventions implicitly. `by remember`
# needs getValue/setValue imported without ever naming them, for example.
IMPLICIT_USE_IMPORTS: set[str] = {
    "getValue", "setValue", "provideDelegate", "invoke", "iterator", "hasNext",
    "next", "compareTo", "contains", "rangeTo", "rangeUntil", "plus", "minus",
    "times", "div", "rem", "unaryMinus", "unaryPlus", "inc", "dec", "not",
    "plusAssign", "minusAssign", "timesAssign", "divAssign", "remAssign",
    "component1", "component2", "component3", "component4", "component5",
    "equals", "hashCode", "toString", "get", "set",
}

# Resource types this script knows how to verify. Anything else (R.id, R.dimen,
# ...) is left alone rather than guessed at.
CHECKED_RES_TYPES = ("string", "drawable", "mipmap", "color")

CHAR_LIT = re.compile(r"'(?:\\u[0-9a-fA-F]{4}|\\.|[^'\\\n])'")
IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+(?:\.\*)?)\s*(?:as\s+(\w+))?\s*$")
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)")
R_REF = re.compile(r"\bR\.(\w+)\.(\w+)")
FORMAT_SPEC = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])")
STRING_TAG = re.compile(
    r"<(string|plurals|string-array)\b[^>]*?\bname\s*=\s*\"([^\"]+)\"", re.DOTALL
)
STRING_VALUE = re.compile(
    r"<string\b[^>]*?\bname\s*=\s*\"([^\"]+)\"[^>]*>(.*?)</string>", re.DOTALL
)
COLOR_TAG = re.compile(r"<color\b[^>]*?\bname\s*=\s*\"([^\"]+)\"")
RES_REF_XML = re.compile(r"@(android:)?(string|plurals|drawable|mipmap|color)/(\w+)")
TYPE_PARAM = re.compile(r"^[A-Z]\d?$")

@dataclass
class Finding:
    path: str
    line: int
    level: str  # ERROR | WARN
    check: str
    message: str


class Report:
    def __init__(self) -> None:
        self.findings: list[Finding] = []

    def add(self, path: str, line: int, level: str, check: str, message: str) -> None:
        self.findings.append(Finding(path, max(1, int(line)), level, check, message))

    def error(self, path: str, line: int, check: str, message: str) -> None:
        self.add(path, line, "ERROR", check, message)

    def warn(self, path: str, line: int, check: str, message: str) -> None:
        self.add(path, line, "WARN", check, message)

    @property
    def errors(self) -> list[Finding]:
        return [f for f in self.findings if f.level == "ERROR"]

    @property
    def warnings(self) -> list[Finding]:
        return [f for f in self.findings if f.level == "WARN"]


def blank(span: str) -> str:
    """Same-length whitespace, keeping newlines so line numbers survive."""
    return "".join("\n" if ch == "\n" else " " for ch in span)


def scan(text: str) -> tuple[str, str, list[tuple[int, str]]]:
    """Blank out comments, string and char literals, and backtick identifiers.

    Returns (code, code_with_strings, problems). Both returned strings have
    exactly the same length as `text`, so every offset and line number computed
    on them is valid for the original file.

      code               comment, string, char and backtick bodies blanked;
                         this is what every structural check runs on
      code_with_strings  comments blanked, literals intact; used for "is this
                         name mentioned anywhere", including string templates
    """
    n = len(text)
    code: list[str] = []
    keep: list[str] = []
    problems: list[tuple[int, str]] = []
    i = 0
    while i < n:
        two = text[i:i + 2]
        if two == "//":
            j = text.find("\n", i)
            j = n if j == -1 else j
            code.append(blank(text[i:j]))
            keep.append(blank(text[i:j]))
            i = j
        elif two == "/*":
            depth, j = 0, i
            while j < n:
                if text[j:j + 2] == "/*":
                    depth += 1
                    j += 2
                elif text[j:j + 2] == "*/":
                    depth -= 1
                    j += 2
                    if depth == 0:
                        break
                else:
                    j += 1
            if depth != 0:
                problems.append((i, "unterminated block comment"))
                j = n
            code.append(blank(text[i:j]))
            keep.append(blank(text[i:j]))
            i = j
        elif text[i:i + 3] == '"""':
            j = text.find('"""', i + 3)
            if j == -1:
                problems.append((i, "unterminated raw string"))
                seg = text[i:n]
                code.append('"""' + blank(seg[3:]))
                keep.append(seg)
                i = n
            else:
                end = j + 3
                while end < n and text[end] == '"':
                    end += 1
                seg = text[i:end]
                code.append('"""' + blank(seg[3:-3]) + '"""')
                keep.append(seg)
                i = end
        elif text[i] == '"':
            j, closed = i + 1, False
            while j < n:
                ch = text[j]
                if ch == "\\":
                    j += 2
                    continue
                if ch == '"':
                    closed = True
                    break
                if ch == "\n":
                    break
                j += 1
            if closed:
                code.append('"' + blank(text[i + 1:j]) + '"')
                keep.append(text[i:j + 1])
                i = j + 1
            else:
                problems.append((i, "unterminated string literal"))
                code.append('"' + blank(text[i + 1:j]))
                keep.append(text[i:j])
                i = j
        elif text[i] == "`":
            # A backtick-quoted identifier: `a file that is not a backup`. The
            # words inside are not code, so they must not look like references.
            j = text.find("`", i + 1)
            if j == -1:
                problems.append((i, "unterminated backtick identifier"))
                j = n - 1
            code.append("`" + blank(text[i + 1:j]) + "`")
            keep.append(text[i:j + 1])
            i = j + 1
        elif text[i] == "'":
            m = CHAR_LIT.match(text, i)
            if m:
                code.append("'" + blank(m.group(0)[1:-1]) + "'")
                keep.append(m.group(0))
                i = m.end()
            else:
                # A lone apostrophe in code. Pass it through rather than
                # swallowing the rest of the file.
                code.append("'")
                keep.append("'")
                i += 1
        else:
            code.append(text[i])
            keep.append(text[i])
            i += 1
    return "".join(code), "".join(keep), problems


def line_starts_of(text: str) -> list[int]:
    starts = [0]
    for idx, ch in enumerate(text):
        if ch == "\n":
            starts.append(idx + 1)
    return starts


def line_of(starts: list[int], offset: int) -> int:
    low, high = 0, len(starts) - 1
    while low < high:
        mid = (low + high + 1) // 2
        if starts[mid] <= offset:
            low = mid
        else:
            high = mid - 1
    return low + 1


def match_bracket(code: str, start: int) -> int | None:
    """Index just past the bracket that closes the one at `start`."""
    pairs = {"(": ")", "[": "]", "{": "}"}
    if code[start] not in pairs:
        return None
    stack = [code[start]]
    i = start + 1
    while i < len(code) and stack:
        ch = code[i]
        if ch in pairs:
            stack.append(ch)
        elif ch in ")]}":
            if pairs[stack[-1]] != ch:
                return None
            stack.pop()
        i += 1
    return i if not stack else None


def split_top_level(text: str, separator: str = ",") -> list[str]:
    """Split on `separator` at bracket depth 0, ignoring nested calls.

    `<` and `>` only count as brackets when they look like a generic argument
    list (`Map<String, Int>`) rather than a lambda arrow (`{ it -> ... }`).
    """
    parts, cur, depth, angle = [], [], 0, 0
    previous = ""
    for ch in text:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "<" and (previous.isalnum() or previous in "_?>"):
            angle += 1
        elif ch == ">" and previous != "-" and angle > 0:
            angle -= 1
        if ch == separator and depth == 0 and angle == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
        previous = ch
    parts.append("".join(cur))
    return parts


def split_qualified(head: str) -> list[str]:
    """Split `Cursor.mapRows` or `Map<String, Int>.foo` on its top-level dots."""
    parts, cur, angle = [], [], 0
    i = 0
    while i < len(head):
        ch = head[i]
        if ch == "`":
            j = head.find("`", i + 1)
            j = len(head) - 1 if j == -1 else j
            cur.append(head[i:j + 1])
            i = j + 1
            continue
        if ch == "<":
            angle += 1
        elif ch == ">":
            angle = max(0, angle - 1)
        if ch == "." and angle == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
        i += 1
    parts.append("".join(cur))
    return parts


def depth_arrays(code: str) -> tuple[list[int], list[int]]:
    """Per-offset brace depth and paren/bracket depth (length len(code) + 1)."""
    brace = [0] * (len(code) + 1)
    paren = [0] * (len(code) + 1)
    b = p = 0
    for i, ch in enumerate(code):
        brace[i] = b
        paren[i] = p
        if ch == "{":
            b += 1
        elif ch == "}":
            b -= 1
        elif ch in "([":
            p += 1
        elif ch in ")]":
            p -= 1
    brace[len(code)] = b
    paren[len(code)] = p
    return brace, paren


@dataclass
class Import:
    line: int
    fqname: str
    alias: str | None = None

    @property
    def star(self) -> bool:
        return self.fqname.endswith(".*")

    @property
    def path_part(self) -> str:
        return self.fqname[:-2] if self.star else self.fqname

    @property
    def simple(self) -> str:
        if self.alias:
            return self.alias
        return "" if self.star else self.fqname.rsplit(".", 1)[-1]

    @property
    def project(self) -> bool:
        return self.fqname.startswith(APP_PACKAGE + ".")


@dataclass
class Decl:
    name: str
    kind: str          # class | interface | object | fun | val | var | typealias
    receiver: str      # "" for a plain declaration
    params: str        # raw parameter list for a fun, else ""
    line: int
    package: str = ""
    rel: str = ""

    @property
    def space(self) -> str:
        """Which namespace the name occupies: classifier, function or property."""
        if self.kind in ("class", "interface", "object", "typealias"):
            return "classifier"
        if self.kind == "fun":
            return "function"
        return "property"


def skip_generics(code: str, i: int) -> int | None:
    """Index just past the `>` matching the `<` at `i`, or None if malformed.

    `match_bracket` deliberately knows nothing about angle brackets, because in
    Kotlin `<` is usually a comparison. Here the caller has already established
    that a type-parameter list starts at `i`.
    """
    if i >= len(code) or code[i] != "<":
        return None
    depth = 0
    previous = ""
    while i < len(code):
        ch = code[i]
        if ch == "<":
            depth += 1
        elif ch == ">" and previous != "-":
            depth -= 1
            if depth == 0:
                return i + 1
        elif ch in "{;":
            return None
        previous = ch
        i += 1
    return None


def skip_ws(code: str, i: int) -> int:
    while i < len(code) and code[i] in " \t\r\n":
        i += 1
    return i


def read_head(code: str, i: int, stop_at_paren: bool) -> tuple[str, int]:
    """Read a declaration's (possibly receiver-qualified) name.

    Stops at `(` for a fun, and at `:` `=` `{` `by` or end of line for a
    property. Angle brackets are tracked so `Map<K, V>.foo` survives.
    """
    out: list[str] = []
    angle = 0
    while i < len(code):
        ch = code[i]
        if ch == "`":
            j = code.find("`", i + 1)
            j = len(code) - 1 if j == -1 else j
            out.append(code[i:j + 1])
            i = j + 1
            continue
        if ch == "<":
            angle += 1
        elif ch == ">":
            angle = max(0, angle - 1)
        if angle == 0:
            if ch == "(" and stop_at_paren:
                break
            if ch in "{=\n;":
                break
            if ch == ":":
                break
            if ch == " " and out and "".join(out).strip():
                # `val Foo.bar by lazy` / `class Foo : Bar` -- the name ended.
                rest = code[i:i + 5]
                if not rest.startswith(" <") and not rest.lstrip().startswith("."):
                    break
        out.append(ch)
        i += 1
    return "".join(out).strip(), i


def parse_decl(code: str, kw_start: int, kw: str, line: int) -> Decl | None:
    i = skip_ws(code, kw_start + len(kw))
    if kw in ("class", "interface", "object", "typealias"):
        m = re.compile(r"[A-Za-z_]\w*|`[^`]+`").match(code, i)
        if not m:
            return None
        return Decl(m.group(0).strip("`"), kw, "", "", line)
    if i < len(code) and code[i] == "<":
        j = skip_generics(code, i)
        if j is None:
            return None
        i = skip_ws(code, j)
    head, i = read_head(code, i, stop_at_paren=(kw == "fun"))
    if not head:
        return None
    parts = [p.strip() for p in split_qualified(head)]
    name = parts[-1].strip("`")
    receiver = ".".join(parts[:-1])
    if not re.fullmatch(r"[A-Za-z_]\w*", name):
        return None
    params = ""
    if kw == "fun":
        i = skip_ws(code, i)
        if i < len(code) and code[i] == "(":
            j = match_bracket(code, i)
            if j is not None:
                params = code[i + 1:j - 1]
    return Decl(name, kw, receiver, params, line)


DECL_RE = re.compile(r"(?<![\w.])(class|interface|object|fun|val|var|typealias)\s")


class KtFile:
    """One Kotlin source file, parsed just enough for the checks below."""

    def __init__(self, path: Path, root: Path) -> None:
        self.path = path
        self.rel = path.relative_to(root).as_posix()
        self.text = path.read_text(encoding="utf-8")
        self.code, self.keep, self.problems = scan(self.text)
        self.starts = line_starts_of(self.text)
        self.brace, self.paren = depth_arrays(self.code)
        self.package = ""
        self.imports: list[Import] = []
        self.decls: list[Decl] = []
        self.local_names: set[str] = set()
        self.import_lines: set[int] = set()
        self._parse()

    def line(self, offset: int) -> int:
        return line_of(self.starts, offset)

    def _parse(self) -> None:
        for idx, raw in enumerate(self.code.split("\n"), start=1):
            m = PACKAGE_RE.match(raw)
            if m and not self.package:
                self.package = m.group(1)
                continue
            m = IMPORT_RE.match(raw)
            if m:
                self.imports.append(Import(idx, m.group(1), m.group(2)))
                self.import_lines.add(idx)

        for m in DECL_RE.finditer(self.code):
            kw, at = m.group(1), m.start(1)
            decl = parse_decl(self.code, at, kw, self.line(at))
            if decl is None:
                continue
            self.local_names.add(decl.name)
            if self.brace[at] == 0 and self.paren[at] == 0:
                decl.package = self.package
                decl.rel = self.rel
                self.decls.append(decl)

        # Every other name bound anywhere in the file: enum entries, parameters,
        # locals, nested classes, destructured names, lambda parameters. Used
        # only to *suppress* unresolved-symbol reports, so over-collecting here
        # is the safe direction.
        for m in re.finditer(r"(?<![\w.])(?:val|var|fun|class|object|interface)\s+(\w+)", self.code):
            self.local_names.add(m.group(1))
        # `name:` binds a parameter, a property or a named component. Excluding
        # `::` keeps `Foo::class` out of the set.
        for m in re.finditer(r"(?<![\w.:])(\w+)\s*:(?!:)", self.code):
            self.local_names.add(m.group(1))
        # Enum entries, one per line.
        for m in re.finditer(r"^\s*([A-Z][A-Z0-9_]*)\s*(?:\(|,|;|$)", self.code, re.M):
            self.local_names.add(m.group(1))


def read_text_safe(path: Path, root: Path, report: Report) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except Exception as exc:
        report.error(
            path.relative_to(root).as_posix(), 1, "internal",
            f"[internal] could not read: {exc!r}",
        )
        return ""


def strip_xml_comments(text: str) -> str:
    """Blank `<!-- ... -->` while keeping every offset and line number."""
    out: list[str] = []
    i = 0
    while i < len(text):
        if text.startswith("<!--", i):
            j = text.find("-->", i)
            j = len(text) if j == -1 else j + 3
            out.append(blank(text[i:j]))
            i = j
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


@dataclass
class ResRef:
    rel: str
    line: int
    kind: str
    name: str


@dataclass
class Resources:
    names: dict[str, dict[str, int]] = field(default_factory=dict)  # kind -> name -> line
    values: dict[str, str] = field(default_factory=dict)            # string name -> value
    files: dict[str, str] = field(default_factory=dict)             # kind/name -> rel path
    refs: list[ResRef] = field(default_factory=list)

    def has(self, kind: str, name: str) -> bool:
        return name in self.names.get(kind, {})


def load_resources(root: Path, report: Report) -> Resources:
    res = Resources()
    for kind in ("string", "plurals", "color", "drawable", "mipmap", "style", "xml"):
        res.names[kind] = {}

    res_dir = root / "app" / "src" / "main" / "res"

    # Declared by a <resources> file.
    for xml in sorted((res_dir / "values").glob("*.xml")):
        text = strip_xml_comments(read_text_safe(xml, root, report))
        starts = line_starts_of(text)
        for m in STRING_TAG.finditer(text):
            kind = "plurals" if m.group(1) == "plurals" else "string"
            res.names[kind][m.group(2)] = line_of(starts, m.start())
        for m in COLOR_TAG.finditer(text):
            res.names["color"][m.group(1)] = line_of(starts, m.start())
        for m in re.finditer(r"<style\b[^>]*?\bname\s*=\s*\"([^\"]+)\"", text):
            res.names["style"][m.group(1)] = line_of(starts, m.start())
        for m in STRING_VALUE.finditer(text):
            res.values[m.group(1)] = m.group(2)

    # Declared by existing as a file: res/drawable*/x.xml -> R.drawable.x
    for child in sorted(res_dir.glob("*")):
        if not child.is_dir():
            continue
        kind = child.name.split("-", 1)[0]
        if kind not in ("drawable", "mipmap", "xml", "layout", "anim", "font", "raw"):
            continue
        res.names.setdefault(kind, {})
        for f in sorted(child.iterdir()):
            if f.is_file():
                res.names[kind][f.stem] = 1
                res.files[f"{kind}/{f.stem}"] = f.relative_to(root).as_posix()
    return res


def collect_refs(root: Path, files: list[KtFile], res: Resources, report: Report) -> None:
    """Every R.kind.name in Kotlin and every @kind/name in XML."""
    for kf in files:
        for m in R_REF.finditer(kf.code):
            res.refs.append(ResRef(kf.rel, kf.line(m.start()), m.group(1), m.group(2)))

    xml_paths = sorted((root / "app" / "src" / "main").rglob("*.xml"))
    for xml in xml_paths:
        rel = xml.relative_to(root).as_posix()
        text = strip_xml_comments(read_text_safe(xml, root, report))
        starts = line_starts_of(text)
        for m in RES_REF_XML.finditer(text):
            if m.group(1):        # @android:string/ok is a framework resource
                continue
            res.refs.append(ResRef(rel, line_of(starts, m.start()), m.group(2), m.group(3)))


def check_resources(res: Resources, report: Report) -> None:
    for ref in res.refs:
        if ref.kind not in CHECKED_RES_TYPES:
            continue
        if res.has(ref.kind, ref.name):
            continue
        if ref.kind == "string" and res.has("plurals", ref.name):
            continue
        report.error(
            ref.rel, ref.line, "resource",
            f"[resource] R.{ref.kind}.{ref.name} does not exist "
            f"(no @{ref.kind}/{ref.name} is declared)",
        )

    used: dict[str, set[str]] = {}
    for ref in res.refs:
        used.setdefault(ref.kind, set()).add(ref.name)

    strings_rel = "app/src/main/res/values/strings.xml"
    for name, line in sorted(res.names["string"].items(), key=lambda kv: kv[1]):
        if name in used.get("string", set()):
            continue
        report.warn(
            strings_rel, line, "resource",
            f"[resource] string {name} is never referenced from Kotlin or XML",
        )
    for kind in ("drawable", "mipmap", "color"):
        for name in sorted(res.names.get(kind, {})):
            if name in used.get(kind, set()):
                continue
            rel = res.files.get(f"{kind}/{name}", f"app/src/main/res/values/{kind}s.xml")
            line = res.names[kind][name]
            report.warn(
                rel, line, "resource",
                f"[resource] {kind} {name} is never referenced from Kotlin or XML",
            )


FIRST_ARG_STRING = re.compile(r"^(?:id\s*=\s*)?R\.string\.(\w+)$")
STRING_RES_FUNNEL = re.compile(
    r"^\s*@(?:\w+:)?StringRes\s+\w+\s*:\s*Int\s*,\s*vararg\s+\w+\s*:\s*Any\s*$"
)


def format_arity(value: str) -> int:
    """How many arguments this <string> body consumes."""
    explicit, plain = 0, 0
    for m in FORMAT_SPEC.finditer(value):
        if m.group(2) == "%":
            continue
        if m.group(1):
            explicit = max(explicit, int(m.group(1)))
        else:
            plain += 1
    return explicit if explicit else plain


def check_formats(files: list[KtFile], res: Resources, report: Report) -> None:
    funnels = {"stringResource", "getString", "getQuantityString"}
    for kf in files:
        for decl in kf.decls:
            if decl.kind == "fun" and STRING_RES_FUNNEL.match(decl.params):
                funnels.add(decl.name)

    call = re.compile(r"\b(" + "|".join(sorted(funnels)) + r")\s*\(")
    for kf in files:
        for m in call.finditer(kf.code):
            open_paren = m.end() - 1
            close = match_bracket(kf.code, open_paren)
            if close is None:
                continue
            inner = kf.code[open_paren + 1:close - 1]
            args = [a.strip() for a in split_top_level(inner)]
            if not args:
                continue
            head = FIRST_ARG_STRING.match(args[0])
            if not head:
                continue
            name = head.group(1)
            rest = [a for a in args[1:] if a]
            if any(a.startswith("*") or a.startswith("formatArgs") for a in rest):
                continue
            line = kf.line(m.start())
            if m.group(1) == "getQuantityString":
                rest = rest[1:]     # the quantity itself is not a format arg
            if name not in res.values:
                continue            # missing string: check 1 already owns that
            want = format_arity(res.values[name])
            if want != len(rest):
                report.error(
                    kf.rel, line, "format",
                    f"[format] R.string.{name} takes {want} format argument(s) "
                    f"but {m.group(1)}() is given {len(rest)}",
                )


class Project:
    """Every declaration in the project, indexed by package."""

    def __init__(self, files: list[KtFile]) -> None:
        self.files = files
        self.packages: set[str] = {kf.package for kf in files if kf.package}
        self.symbols: dict[str, dict[str, list[Decl]]] = {}
        self.by_package: dict[str, list[KtFile]] = {}
        for kf in files:
            self.by_package.setdefault(kf.package, []).append(kf)
            table = self.symbols.setdefault(kf.package, {})
            for decl in kf.decls:
                table.setdefault(decl.name, []).append(decl)
        # R.java and BuildConfig.java are generated at build time into the app
        # package (BuildConfig because `buildFeatures { buildConfig = true }` is
        # set), so neither exists as a source file for us to find.
        generated = self.symbols.setdefault(APP_PACKAGE, {})
        for name in ("R", "BuildConfig"):
            generated.setdefault(
                name, [Decl(name, "class", "", "", 0, APP_PACKAGE, "<generated>")]
            )
        self.packages.add(APP_PACKAGE)

    def names_in(self, package: str) -> set[str]:
        return set(self.symbols.get(package, {}))

    def longest_package(self, fqname: str) -> str | None:
        best = None
        for pkg in self.packages:
            if fqname.startswith(pkg + ".") and (best is None or len(pkg) > len(best)):
                best = pkg
        return best

    def local_names_of(self, package: str, name: str) -> set[str]:
        out: set[str] = set()
        for kf in self.by_package.get(package, []):
            if any(d.name == name for d in kf.decls):
                out |= kf.local_names
        return out


def check_imports(project: Project, report: Report) -> None:
    for kf in project.files:
        seen: dict[str, int] = {}
        for imp in kf.imports:
            if imp.fqname in seen:
                report.warn(
                    kf.rel, imp.line, "import",
                    f"[import] duplicate import of {imp.fqname} "
                    f"(already on line {seen[imp.fqname]})",
                )
            seen[imp.fqname] = imp.line
            if not imp.project:
                continue
            target = imp.path_part
            if imp.star:
                if target in project.packages:
                    continue
                parent, _, last = target.rpartition(".")
                if parent in project.packages and last in project.names_in(parent):
                    continue
                report.error(
                    kf.rel, imp.line, "import",
                    f"[import] no package or type {target} in the project",
                )
                continue
            pkg = project.longest_package(target)
            if pkg is None:
                report.error(
                    kf.rel, imp.line, "import",
                    f"[import] {target} is not in any package this project declares",
                )
                continue
            rest = target[len(pkg) + 1:].split(".")
            if rest[0] not in project.names_in(pkg):
                report.error(
                    kf.rel, imp.line, "import",
                    f"[import] nothing named {rest[0]} is declared in package {pkg}",
                )
                continue
            if len(rest) > 1:
                known = project.local_names_of(pkg, rest[0])
                missing = [r for r in rest[1:] if r not in known]
                if missing:
                    report.warn(
                        kf.rel, imp.line, "import",
                        f"[import] cannot confirm member {'.'.join(missing)} "
                        f"of {pkg}.{rest[0]}",
                    )


CAPITALISED = re.compile(r"(?<![\w.@$`])([A-Z]\w*)")
IS_AS_BEFORE = re.compile(r"\b(?:is|as\??)\s+$")


def prev_nonspace(code: str, i: int) -> tuple[str, int]:
    j = i - 1
    while j >= 0 and code[j] in " \t\r\n":
        j -= 1
    return (code[j], j) if j >= 0 else ("", -1)


def classify_use(code: str, start: int, end: int) -> str | None:
    """'type', 'call', 'member', 'generic' for a confident use; None otherwise."""
    after = code[end:end + 2]
    if after[:1] == "(":
        return "call"
    if after[:2] == "::":
        return "member"
    if after[:1] == "." and not after.startswith(".."):
        return "member"
    prev, at = prev_nonspace(code, start)
    if prev == ":" and (at == 0 or code[at - 1] != ":"):
        return "type"
    if prev in "<," and after[:1] in ">,":
        return "generic"
    if IS_AS_BEFORE.search(code[max(0, start - 8):start]):
        return "type"
    return None


def check_symbols(project: Project, report: Report) -> None:
    for kf in project.files:
        resolvable = set(ALLOWED_UNQUALIFIED) | kf.local_names
        resolvable |= project.names_in(kf.package)
        lenient = False
        for imp in kf.imports:
            if imp.star:
                if imp.project and imp.path_part in project.packages:
                    resolvable |= project.names_in(imp.path_part)
                else:
                    lenient = True
            else:
                resolvable.add(imp.simple)
        skip_lines = set(kf.import_lines)
        for m in CAPITALISED.finditer(kf.code):
            name = m.group(1)
            if name in resolvable or TYPE_PARAM.fullmatch(name):
                continue
            if name.upper() == name:          # SCREAMING_CASE: enum entry / const
                continue
            line = kf.line(m.start())
            if line in skip_lines:
                continue
            how = classify_use(kf.code, m.start(), m.end())
            detail = {
                "call": "called as a function or constructor",
                "type": "used as a type",
                "member": "used as a qualifier",
                "generic": "used as a type argument",
            }.get(how or "", "")
            if how and not lenient:
                report.error(
                    kf.rel, line, "symbol",
                    f"[symbol] {name} is {detail} but is not declared, "
                    f"imported, or in package {kf.package}",
                )
            else:
                report.warn(
                    kf.rel, line, "symbol",
                    f"[symbol] cannot resolve {name}",
                )


def check_unused_imports(project: Project, report: Report) -> None:
    for kf in project.files:
        body_lines = kf.keep.split("\n")
        for idx in kf.import_lines:
            if 1 <= idx <= len(body_lines):
                body_lines[idx - 1] = ""
        body = "\n".join(body_lines)
        for imp in kf.imports:
            name = imp.simple
            if not name or name in IMPLICIT_USE_IMPORTS:
                continue
            # Deliberately *not* excluding a leading dot: an imported extension
            # is always called on a receiver (`16.dp`, `cursor.reqString(...)`),
            # so requiring an unqualified use would flag every one of them.
            if re.search(r"\b" + re.escape(name) + r"\b", body):
                continue
            report.warn(
                kf.rel, imp.line, "unused",
                f"[unused] import {imp.fqname} is never used",
            )


def param_types(params: str) -> tuple[tuple[str, ...], bool]:
    """Loose parameter type list, plus whether it could be read confidently."""
    if not params.strip():
        return (), True
    types: list[str] = []
    for raw in split_top_level(params):
        part = raw.strip()
        if not part:
            continue
        part = re.sub(r"@\w+(?::\w+)?(\([^)]*\))?\s*", "", part)
        part = re.sub(r"\b(?:vararg|noinline|crossinline)\s+", "", part)
        pieces = split_top_level(part, ":")
        if len(pieces) < 2:
            return tuple(types), False
        rhs = ":".join(pieces[1:])
        rhs = split_top_level(rhs, "=")[0]
        types.append(re.sub(r"\s+", "", rhs))
    return tuple(types), True


def check_duplicates(project: Project, report: Report) -> None:
    groups: dict[tuple[str, str, str, str], list[Decl]] = {}
    for kf in project.files:
        for decl in kf.decls:
            key = (decl.package, decl.space, re.sub(r"\s+", "", decl.receiver), decl.name)
            groups.setdefault(key, []).append(decl)

    for (package, space, receiver, name), decls in sorted(groups.items()):
        if len(decls) < 2:
            continue
        shown = f"{receiver}.{name}" if receiver else name
        if space == "function":
            seen: dict[tuple[str, ...], Decl] = {}
            for decl in sorted(decls, key=lambda d: (d.rel, d.line)):
                types, sure = param_types(decl.params)
                if not sure:
                    report.warn(
                        decl.rel, decl.line, "duplicate",
                        f"[duplicate] could not read the parameter list of "
                        f"{shown}(), so an overload clash cannot be ruled out",
                    )
                    continue
                if types in seen:
                    first = seen[types]
                    report.error(
                        decl.rel, decl.line, "duplicate",
                        f"[duplicate] fun {shown}({', '.join(types)}) is already "
                        f"declared in package {package} at {first.rel}:{first.line}",
                    )
                else:
                    seen[types] = decl
            continue
        first, *rest = sorted(decls, key=lambda d: (d.rel, d.line))
        for decl in rest:
            report.error(
                decl.rel, decl.line, "duplicate",
                f"[duplicate] {decl.kind} {shown} is already declared in package "
                f"{package} at {first.rel}:{first.line}",
            )


NON_COMPOSABLE_SCOPES = ("semantics", "LaunchedEffect", "DisposableEffect", "SideEffect")
RESOURCE_CALLS = ("stringResource", "painterResource")
SCOPE_OPEN = re.compile(
    r"(?:\.|\b)(" + "|".join(NON_COMPOSABLE_SCOPES) + r")\s*(\()?"
)


def param_names(params: str) -> set[str]:
    names: set[str] = set()
    for raw in split_top_level(params):
        part = re.sub(r"@\w+(?::\w+)?(\([^)]*\))?\s*", "", raw).strip()
        part = re.sub(r"\b(?:vararg|noinline|crossinline)\s+", "", part)
        m = re.match(r"([A-Za-z_]\w*)\s*:", part)
        if m:
            names.add(m.group(1))
    return names


def function_bodies(kf: KtFile) -> list[tuple[int, int, set[str]]]:
    """(body start, body end, parameter names) for every `fun` in the file."""
    out: list[tuple[int, int, set[str]]] = []
    code = kf.code
    for m in re.finditer(r"(?<![\w.])fun\s", code):
        i = skip_ws(code, m.end())
        if i < len(code) and code[i] == "<":
            j = skip_generics(code, i)
            if j is None:
                continue
            i = skip_ws(code, j)
        _, i = read_head(code, i, stop_at_paren=True)
        i = skip_ws(code, i)
        if i >= len(code) or code[i] != "(":
            continue
        close = match_bracket(code, i)
        if close is None:
            continue
        names = param_names(code[i + 1:close - 1])
        # A body starts at the next `{` or `=`. Bounded so that a body-less
        # declaration cannot swallow the following function.
        k, limit = close, min(len(code), close + 120)
        while k < limit and code[k] not in "{=":
            k += 1
        if k >= limit:
            continue
        if code[k] == "{":
            end = match_bracket(code, k)
            if end is not None:
                out.append((k, end, names))
        elif code[k] == "=":
            end = len(code)
            for nxt in DECL_RE.finditer(code, k):
                if kf.brace[nxt.start()] <= kf.brace[m.start()]:
                    end = nxt.start()
                    break
            out.append((k, end, names))
    return out


def check_compose(project: Project, report: Report) -> None:
    for kf in project.files:
        code = kf.code
        bodies = function_bodies(kf)
        for m in SCOPE_OPEN.finditer(code):
            i = m.end()
            if m.group(2):                      # LaunchedEffect(keys...) { ... }
                close = match_bracket(code, m.end() - 1)
                if close is None:
                    continue
                i = skip_ws(code, close)
            else:
                i = skip_ws(code, i)
            if i >= len(code) or code[i] != "{":
                continue
            end = match_bracket(code, i)
            if end is None:
                continue
            for call in RESOURCE_CALLS:
                for hit in re.finditer(r"\b" + call + r"\s*\(", code[i:end]):
                    report.error(
                        kf.rel, kf.line(i + hit.start()), "compose",
                        f"[compose] {call}() is called inside {m.group(1)} "
                        f"{{...}}, which is not a @Composable scope; hoist it "
                        f"above the block",
                    )

        for m in re.finditer(r"(?<![\w.])remember\s*\{", code):
            brace = m.end() - 1
            end = match_bracket(code, brace)
            if end is None:
                continue
            enclosing: set[str] = set()
            best = -1
            for start, stop, names in bodies:
                if start <= brace < stop and start > best:
                    best, enclosing = start, names
            if not enclosing:
                continue
            body = code[brace:end]
            captured = sorted(
                n for n in enclosing
                if re.search(r"(?<![\w.])" + re.escape(n) + r"\b", body)
            )
            if captured:
                report.warn(
                    kf.rel, kf.line(m.start()), "compose",
                    f"[compose] remember {{...}} has no keys but captures "
                    f"{', '.join(captured)}; it will not recompute when they change",
                )


PAIRS = {"(": ")", "[": "]", "{": "}"}
CLOSERS = {")": "(", "]": "[", "}": "{"}


def check_balance(project: Project, report: Report) -> None:
    for kf in project.files:
        for offset, problem in kf.problems:
            report.error(kf.rel, kf.line(offset), "balance", f"[balance] {problem}")
        stack: list[tuple[str, int]] = []
        for i, ch in enumerate(kf.code):
            if ch in PAIRS:
                stack.append((ch, i))
            elif ch in CLOSERS:
                if not stack:
                    report.error(
                        kf.rel, kf.line(i), "balance",
                        f"[balance] stray '{ch}' with nothing open",
                    )
                    break
                opener, at = stack.pop()
                if PAIRS[opener] != ch:
                    report.error(
                        kf.rel, kf.line(i), "balance",
                        f"[balance] '{ch}' closes the '{opener}' opened on line "
                        f"{kf.line(at)}",
                    )
                    break
        else:
            if stack:
                opener, at = stack[0]
                report.error(
                    kf.rel, kf.line(at), "balance",
                    f"[balance] '{opener}' is never closed "
                    f"({len(stack)} unclosed bracket(s); the file may be truncated)",
                )


def load_files(root: Path, report: Report) -> list[KtFile]:
    out: list[KtFile] = []
    for base in ("app/src/main/java", "app/src/test/java", "app/src/androidTest/java"):
        directory = root / base
        if not directory.is_dir():
            continue
        for path in sorted(directory.rglob("*.kt")):
            try:
                out.append(KtFile(path, root))
            except Exception as exc:                      # never crash on a file
                rel = path.relative_to(root).as_posix()
                report.error(rel, 1, "internal", f"[internal] could not parse: {exc!r}")
    return out


CHECK_ORDER = [
    "balance", "import", "symbol", "resource", "format", "duplicate", "compose",
    "unused", "internal",
]


def print_report(report: Report, root: Path, errors_only: bool) -> None:
    findings = report.errors if errors_only else report.findings
    by_file: dict[str, list[Finding]] = {}
    for f in findings:
        by_file.setdefault(f.path, []).append(f)

    print(f"Daybook static verification -- {root}")
    label = "error" if errors_only else "finding"
    print(f"{len(findings)} {label}(s) in {len(by_file)} file(s)\n")
    for path in sorted(by_file):
        group = sorted(by_file[path], key=lambda f: (f.line, f.level, f.message))
        errs = sum(1 for f in group if f.level == "ERROR")
        warns = len(group) - errs
        print(f"{path}  ({errs} error(s), {warns} warning(s))")
        for f in group:
            print(f"  {f.path}:{f.line}: {f.level}: {f.message}")
        print()

    per_check: dict[str, tuple[int, int]] = {}
    for f in report.findings:
        e, w = per_check.get(f.check, (0, 0))
        per_check[f.check] = (e + (f.level == "ERROR"), w + (f.level == "WARN"))

    print("summary")
    for check in CHECK_ORDER:
        if check not in per_check:
            continue
        e, w = per_check[check]
        print(f"  {check:<10} {e:>3} error(s)  {w:>3} warning(s)")
    print(f"  {'TOTAL':<10} {len(report.errors):>3} error(s)  "
          f"{len(report.warnings):>3} warning(s)")
    print()
    if report.errors:
        print(f"FAIL: {len(report.errors)} error(s) must be fixed before this "
              f"project can compile.")
    else:
        print("PASS: no errors. Warnings are advisory.")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Static verification for Daybook's Kotlin sources and resources.",
    )
    parser.add_argument("--root", default=str(Path(__file__).resolve().parent.parent),
                        help="project root (the directory holding app/ and scripts/)")
    parser.add_argument("--json", action="store_true", help="machine-readable output")
    parser.add_argument("--errors-only", action="store_true",
                        help="print errors, hide warnings (exit code is unchanged)")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    if not (root / "app" / "src" / "main").is_dir():
        print(f"error: {root} does not look like the project root", file=sys.stderr)
        return 2

    report = Report()
    files = load_files(root, report)
    project = Project(files)
    res = load_resources(root, report)
    collect_refs(root, files, res, report)

    check_balance(project, report)
    check_imports(project, report)
    check_symbols(project, report)
    check_resources(res, report)
    check_formats(files, res, report)
    check_duplicates(project, report)
    check_compose(project, report)
    check_unused_imports(project, report)

    if args.json:
        findings = report.errors if args.errors_only else report.findings
        print(json.dumps({
            "root": str(root),
            "kotlinFiles": len(files),
            "errors": len(report.errors),
            "warnings": len(report.warnings),
            "findings": [
                {"path": f.path, "line": f.line, "level": f.level,
                 "check": f.check, "message": f.message}
                for f in sorted(findings, key=lambda f: (f.path, f.line, f.check))
            ],
        }, indent=2))
    else:
        print_report(report, root, args.errors_only)

    return 1 if report.errors else 0


if __name__ == "__main__":
    sys.exit(main())
