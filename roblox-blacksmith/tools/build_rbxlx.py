#!/usr/bin/env python3
"""
Сборка файла плейса .rbxlx БЕЗ Rojo (для быстрой проверки в Studio).
Раскладывает src/ по тем же правилам, что default.project.json:
  src/shared  -> ReplicatedStorage.Shared
  src/server  -> ServerScriptService.Server
  src/client  -> StarterPlayer.StarterPlayerScripts.Client

Правила как у Rojo:
  папка с init.luau        -> ModuleScript
  папка с init.server.luau -> Script
  папка с init.client.luau -> LocalScript
  папка без init           -> Folder
  X.luau / X.server.luau / X.client.luau -> ModuleScript / Script / LocalScript

Запуск:  python3 tools/build_rbxlx.py  (из папки roblox-blacksmith)
Результат: build/SystemBlacksmith.rbxlx
"""

import itertools
import pathlib
import sys
from xml.sax.saxutils import escape

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src"
OUT = ROOT / "build" / "SystemBlacksmith.rbxlx"

_ref = itertools.count(1)


def ref() -> str:
    return f"RBX{next(_ref):08X}"


def cdata(text: str) -> str:
    return "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>"


def item(cls: str, name: str, children: list[str], source: str | None = None, extra: str = "") -> str:
    props = f'<string name="Name">{escape(name)}</string>'
    if source is not None:
        props += f'<ProtectedString name="Source">{cdata(source)}</ProtectedString>'
    props += extra
    return f'<Item class="{cls}" referent="{ref()}"><Properties>{props}</Properties>{"".join(children)}</Item>'


SCRIPT_SUFFIXES = [(".server.luau", "Script"), (".client.luau", "LocalScript"), (".luau", "ModuleScript")]


def classify(file: pathlib.Path) -> tuple[str, str] | None:
    for suffix, cls in SCRIPT_SUFFIXES:
        if file.name.endswith(suffix):
            return file.name[: -len(suffix)], cls
    return None


def build_dir(path: pathlib.Path, name: str) -> str:
    init_cls, init_source = "Folder", None
    for suffix, cls in SCRIPT_SUFFIXES:
        init = path / ("init" + suffix)
        if init.exists():
            init_cls, init_source = cls, init.read_text(encoding="utf-8")
            break

    children = []
    for child in sorted(path.iterdir()):
        if child.is_dir():
            children.append(build_dir(child, child.name))
        elif child.is_file():
            info = classify(child)
            if info is None or info[0] == "init":
                continue
            child_name, cls = info
            children.append(item(cls, child_name, [], child.read_text(encoding="utf-8")))
    return item(init_cls, name, children, init_source)


def main() -> None:
    shared = build_dir(SRC / "shared", "Shared")
    server = build_dir(SRC / "server", "Server")
    client = build_dir(SRC / "client", "Client")

    services = [
        item("Workspace", "Workspace", [], extra='<bool name="StreamingEnabled">true</bool>'),
        item("ReplicatedStorage", "ReplicatedStorage", [shared]),
        item("ServerScriptService", "ServerScriptService", [server]),
        item("StarterPlayer", "StarterPlayer", [item("StarterPlayerScripts", "StarterPlayerScripts", [client])]),
    ]
    xml = '<roblox version="4">' + "".join(services) + "</roblox>"
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(xml, encoding="utf-8")
    print(f"OK: {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    sys.exit(main())
