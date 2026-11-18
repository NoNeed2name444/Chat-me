"""Compares two API snapshots and fails on any change that could break an add-on.

    python3 -I api_diff.py <old.json> <new.json>

It fails, exit 1, on:
- removal: a call, value, type, field or parameter that old has and new
  doesn't;
- rename: a removal that comes with an addition of the same shape, like a
  call renamed with the same parameters and result;
- narrowed: a type at the same place that takes fewer values than before
  (a union member gone, a literal where a whole type was, a call's result
  no longer a Promise), a parameter or field whose optional mark changed in
  a way callers notice, a new required parameter, or a @max or @maxLength
  that went down.
It passes, exit 0, on additions: new calls, values, types, optional
parameters at the end, optional fields, union members and higher limits.
Parameter names may change, since calls pass them by position. Standard
library only.
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from api_snapshot import format_type, parse_type  # noqa: E402

FAILS = ("removal", "rename", "narrowed")


def members(node) -> list:
    return node[1] if node[0] == "union" else [node]


def covers(new, old) -> bool:
    """Whether every value the old type takes, the new type takes too."""
    return all(any(covers_one(n, o) for n in members(new)) for o in members(old))


def covers_one(new, old) -> bool:
    if new == old:
        return True
    if old[0] == "lit" and new[0] == "name":
        return new[1] == ("string" if old[1].startswith('"') else "number")
    if new[0] != old[0]:
        return False
    if new[0] == "array":
        return covers(new[1], old[1])
    if new[0] == "generic":
        return new[1] == old[1] and len(new[2]) == len(old[2]) and all(map(covers, new[2], old[2]))
    if new[0] == "fn":
        return (len(new[1]) >= len(old[1]) and all(n[2] for n in new[1][len(old[1]):])
                and all(covers(n[1], o[1]) for n, o in zip(new[1], old[1])) and covers(new[2], old[2]))
    return False


def shape(call: dict) -> str:
    return json.dumps([[p["type"], bool(p.get("optional")), bool(p.get("rest"))] for p in call["params"]]
                      + [call["returns"]])


def diff(old: dict, new: dict) -> list[tuple[str, str]]:
    found = []

    def note(kind: str, text: str) -> None:
        found.append((kind, text))

    def type_change(where: str, old_type: str, new_type: str) -> None:
        if not covers(parse_type(new_type), parse_type(old_type)):
            note("narrowed", f"{where}: {old_type} became {new_type}")
        elif old_type != new_type:
            note("addition", f"{where}: {old_type} became {new_type}")

    def limits(where: str, old_field: dict, new_field: dict) -> None:
        for tag in ("max", "maxLength"):
            if tag in old_field and (tag not in new_field or new_field[tag] < old_field[tag]):
                note("narrowed", f"{where}: @{tag} {old_field[tag]} became {new_field.get(tag, 'unset')}")
            elif tag in new_field and tag in old_field and new_field[tag] > old_field[tag]:
                note("addition", f"{where}: @{tag} {old_field[tag]} became {new_field[tag]}")
            elif tag in new_field and tag not in old_field:
                note("narrowed", f"{where}: a new @{tag} {new_field[tag]}")

    def table(kind: str, old_items: dict, new_items: dict, same, compare) -> None:
        gone = [k for k in old_items if k not in new_items]
        added = [k for k in new_items if k not in old_items]
        for key in gone:
            twin = next((a for a in added if same(old_items[key]) == same(new_items[a])), None)
            if twin:
                added.remove(twin)
                note("rename", f"{kind} {key} became {twin}")
            else:
                note("removal", f"{kind} {key}")
        for key in added:
            note("addition", f"{kind} {key}")
        for key in old_items:
            if key in new_items:
                compare(key, old_items[key], new_items[key])
        return added

    def compare_call(name: str, before: dict, after: dict) -> None:
        old_params, new_params = before["params"], after["params"]
        for at, param in enumerate(old_params):
            where = f"{name} parameter {at + 1} ({param['name']})"
            if at >= len(new_params):
                note("removal", where)
                continue
            other = new_params[at]
            type_change(where, param["type"], other["type"])
            if param.get("optional") and not other.get("optional"):
                note("narrowed", f"{where}: optional became required")
            if bool(param.get("rest")) != bool(other.get("rest")):
                note("narrowed", f"{where}: ...rest changed")
        for at, param in enumerate(new_params[len(old_params):], len(old_params)):
            where = f"{name} parameter {at + 1} ({param['name']})"
            note("addition" if param.get("optional") or param.get("rest") else "narrowed",
                 f"{where}: new, {'optional' if param.get('optional') else 'required'}")
        type_change(f"{name} result", before["returns"], after["returns"])
        # any one of a call's permissions lets it run, so a longer list, or none at all, lets more add-ons call it
        was, now = set(before.get("permission", [])), set(after.get("permission", []))
        if was != now:
            wider = not now or (was and now >= was)
            note("addition" if wider else "narrowed",
                 f"{name}: permission {sorted(was) or 'none'} became {sorted(now) or 'none'}")

    def compare_value(name: str, before: str, after: str) -> None:
        type_change(name, before, after)

    def compare_type(name: str, before: dict, after: dict) -> None:
        if ("alias" in before) != ("alias" in after):
            note("narrowed", f"type {name}: changed between an alias and an interface")
            return
        if "alias" in before:
            type_change(f"type {name}", before["alias"], after["alias"])
            return
        if before.get("extends") != after.get("extends"):
            note("narrowed", f"type {name}: extends {before.get('extends')} became {after.get('extends')}")

        def field_shape(field: dict) -> str:
            return json.dumps(field, sort_keys=True)

        def compare_field(field: str, old_field: dict, new_field: dict) -> None:
            where = f"{name}.{field}"
            type_change(where, old_field["type"], new_field["type"])
            if bool(old_field.get("optional")) != bool(new_field.get("optional")):
                note("narrowed", f"{where}: {'optional' if old_field.get('optional') else 'required'} became "
                                 f"{'optional' if new_field.get('optional') else 'required'}")
            limits(where, old_field, new_field)

        for field in table(f"field of {name}:", before["fields"], after["fields"], field_shape, compare_field):
            if not after["fields"][field].get("optional"):
                note("narrowed", f"{name}.{field}: a new required field")

    if new["version"]["major"] != old["version"]["major"]:
        note("narrowed", f"major version {old['version']['major']} became {new['version']['major']}")
    table("call", old["calls"], new["calls"], shape, compare_call)
    table("value", old["values"], new["values"], lambda v: v, compare_value)
    table("type", old["types"], new["types"], lambda t: json.dumps(t, sort_keys=True), compare_type)
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare two API snapshots.")
    parser.add_argument("old", type=Path)
    parser.add_argument("new", type=Path)
    args = parser.parse_args()
    found = diff(json.loads(args.old.read_text(encoding="utf-8")), json.loads(args.new.read_text(encoding="utf-8")))
    for kind, text in found:
        print(f"{kind}: {text}")
    bad = [kind for kind, _ in found if kind in FAILS]
    print(f"{len(bad)} breaking change(s)" if bad else "no breaking change")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
