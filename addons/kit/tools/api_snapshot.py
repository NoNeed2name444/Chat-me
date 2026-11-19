"""Reads the API's TypeScript declarations and prints the machine snapshot.

    python3 -I api_snapshot.py sorta-addon-v1.d.ts            print the snapshot
    python3 -I api_snapshot.py sorta-addon-v1.d.ts --check v1.json

The declarations use a small part of TypeScript: namespaces of functions and
constants, interfaces, type aliases and classes, with string and number
literals, unions, arrays, generics and function types. Doc comments may
carry @version (the file's), @permission (a call's), and @max and
@maxLength (a field's limits). The snapshot lists every call with its
parameters and result, every value and every type, and is what api_diff.py
compares. Standard library only.
"""
import argparse
import json
import re
import sys
from pathlib import Path

TOKEN = re.compile(r"""
    (?P<space>\s+)
  | (?P<doc>/\*\*.*?\*/)
  | (?P<comment>/\*.*?\*/|//[^\n]*)
  | (?P<string>"[^"\\\n]*")
  | (?P<number>\d+)
  | (?P<name>[A-Za-z_$][A-Za-z0-9_$]*)
  | (?P<punct>=>|\.\.\.|\[\]|[{}()<>\[\]:;,|?=.])
""", re.S | re.X)
PRIMITIVES = {"string", "number", "boolean", "null", "void", "undefined", "unknown"}


class Error(Exception):
    pass


def tokens(text: str) -> list[tuple[str, str]]:
    out, at = [], 0
    while at < len(text):
        match = TOKEN.match(text, at)
        if not match:
            line = text.count("\n", 0, at) + 1
            raise Error(f"line {line}: can't read {text[at:at + 20]!r}")
        kind = match.lastgroup
        if kind not in ("space", "comment"):
            out.append((kind, match.group()))
        at = match.end()
    out.append(("end", ""))
    return out


def tags(doc: str) -> dict[str, str]:
    return {m.group(1): m.group(2).strip() for m in re.finditer(r"@(\w+)[ \t]+([^\n*@]*)", doc)}


class Parser:
    def __init__(self, text: str):
        self.items, self.at, self.doc = tokens(text), 0, ""

    def peek(self, ahead: int = 0) -> str:
        return self.items[self.at + ahead][1]

    def kind(self, ahead: int = 0) -> str:
        return self.items[self.at + ahead][0]

    def take(self, want: str | None = None) -> str:
        kind, value = self.items[self.at]
        if kind == "doc":
            self.doc = value
            self.at += 1
            return self.take(want)
        if want is not None and value != want:
            raise Error(f"expected {want!r}, found {value!r}")
        self.at += 1
        return value

    def skip_docs(self) -> str:
        doc = ""
        while self.kind() == "doc":
            doc = self.peek()
            self.at += 1
        return doc

    def name(self) -> str:
        if self.kind() != "name":
            raise Error(f"expected a name, found {self.peek()!r}")
        return self.take()

    # types

    def type(self):
        if self.peek() == "|":
            self.take()
        members = [self.postfix()]
        while self.peek() == "|":
            self.take()
            members.append(self.postfix())
        return members[0] if len(members) == 1 else ("union", members)

    def postfix(self):
        node = self.primary()
        while self.peek() == "[]":
            self.take()
            node = ("array", node)
        return node

    def primary(self):
        kind, value = self.items[self.at]
        if kind in ("string", "number"):
            self.take()
            return ("lit", value)
        if value == "(":
            if self.peek(1) == "..." or (self.peek(1) == ")" and self.peek(2) == "=>") or \
                    (self.kind(1) == "name" and self.peek(2) in (":", "?")):
                self.take("(")
                params = self.params()
                self.take("=>")
                return ("fn", params, self.type())
            self.take("(")
            node = self.type()
            self.take(")")
            return node
        name = self.name()
        if self.peek() == "<":
            self.take()
            args = [self.type()]
            while self.peek() == ",":
                self.take()
                args.append(self.type())
            self.take(">")
            return ("generic", name, args)
        return ("name", name)

    def params(self) -> list:
        out = []
        while self.peek() != ")":
            rest = self.peek() == "..."
            if rest:
                self.take()
            name = self.name()
            optional = self.peek() == "?"
            if optional:
                self.take()
            self.take(":")
            out.append((name, self.type(), optional, rest))
            if self.peek() == ",":
                self.take()
        self.take(")")
        return out

    def fields(self) -> dict:
        self.take("{")
        out = {}
        while True:
            doc = self.skip_docs()
            if self.peek() == "}":
                break
            if self.peek() == "readonly":
                self.take()
            name = self.name()
            optional = self.peek() == "?"
            if optional:
                self.take()
            self.take(":")
            field = {"type": format_type(self.type())}
            if optional:
                field["optional"] = True
            for tag in ("max", "maxLength"):
                if tag in tags(doc):
                    field[tag] = int(tags(doc)[tag])
            if name in out:
                raise Error(f"field {name} twice")
            out[name] = field
            self.take(";")
        self.take("}")
        return out


def snapshot(text: str) -> dict:
    parser = Parser(text)
    first = ""
    if parser.kind() == "doc":
        first = parser.peek()
        parser.at += 1
    version = tags(first).get("version", "")
    if not re.fullmatch(r"\d+\.\d+", version):
        raise Error("the file's first doc comment needs @version, like @version 1.0")
    major, minor = (int(n) for n in version.split("."))
    out = {"api": "sorta-addon", "version": {"major": major, "minor": minor},
           "calls": {}, "values": {}, "types": {}}

    def namespace(prefix: str) -> None:
        parser.take("{")
        while True:
            doc = parser.skip_docs()
            word = parser.take()
            if word == "}":
                return
            name = parser.name()
            path = f"{prefix}.{name}"
            if word == "namespace":
                namespace(path)
            elif word == "const":
                parser.take(":")
                out["values"][path] = format_type(parser.type())
                parser.take(";")
            elif word == "function":
                parser.take("(")
                params = parser.params()
                parser.take(":")
                call = {"params": [param_json(p) for p in params], "returns": format_type(parser.type())}
                if "permission" in tags(doc):
                    call["permission"] = sorted(tags(doc)["permission"].split())
                add(out["calls"], path, call)
                parser.take(";")
            else:
                raise Error(f"{path}: expected namespace, const or function, found {word!r}")

    while True:
        parser.skip_docs()
        if parser.kind() == "end":
            break
        word = parser.take()
        if word == "declare":
            word = parser.take()
        if word == "namespace":
            name = parser.name()
            namespace(name)
        elif word == "interface":
            name = parser.name()
            add(out["types"], name, {"fields": parser.fields()})
        elif word == "class":
            name = parser.name()
            body = {}
            if parser.peek() == "extends":
                parser.take()
                body["extends"] = parser.name()
            body["fields"] = parser.fields()
            add(out["types"], name, body)
        elif word == "type":
            name = parser.name()
            parser.take("=")
            add(out["types"], name, {"alias": format_type(parser.type())})
            parser.take(";")
        else:
            raise Error(f"expected declare, interface or type, found {word!r}")
    return out


def add(table: dict, key: str, value) -> None:
    if key in table:
        raise Error(f"{key} is declared twice")
    table[key] = value


def param_json(param) -> dict:
    name, node, optional, rest = param
    out = {"name": name, "type": format_type(node)}
    if optional:
        out["optional"] = True
    if rest:
        out["rest"] = True
    return out


def parse_type(text: str):
    parser = Parser(text)
    node = parser.type()
    if parser.kind() != "end":
        raise Error(f"{text!r}: more after the type")
    return node


def format_type(node, inner: bool = False) -> str:
    """The canonical text of a type: union members sorted, one space around |."""
    kind = node[0]
    if kind == "lit":
        return node[1]
    if kind == "name":
        return node[1]
    if kind == "generic":
        return f"{node[1]}<{', '.join(format_type(a) for a in node[2])}>"
    if kind == "array":
        element = format_type(node[1], inner=True)
        return f"{element}[]"
    if kind == "union":
        text = " | ".join(sorted({format_type(m, inner=True) for m in node[1]}))
        return f"({text})" if inner else text
    if kind == "fn":
        params = ", ".join(("..." if r else "") + n + ("?" if o else "") + ": " + format_type(t)
                           for n, t, o, r in node[1])
        text = f"({params}) => {format_type(node[2])}"
        return f"({text})" if inner else text
    raise Error(f"unknown type node {kind}")


def dump(data: dict) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Print the API snapshot of a .d.ts file.")
    parser.add_argument("declarations", type=Path)
    parser.add_argument("--check", type=Path, help="fail unless this snapshot file matches")
    args = parser.parse_args()
    made = dump(snapshot(args.declarations.read_text(encoding="utf-8")))
    if args.check:
        if args.check.read_text(encoding="utf-8") != made:
            print(f"{args.check} doesn't match {args.declarations}. Run api_snapshot.py again and commit both.")
            return 1
        print(f"{args.check} matches {args.declarations}")
        return 0
    sys.stdout.write(made)
    return 0


if __name__ == "__main__":
    sys.exit(main())
