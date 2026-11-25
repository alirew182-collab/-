#!/usr/bin/env python3
"""
Bleach Minimal Dark — проверка хэш-классов Steam.

Ручные файлы темы обращаются к элементам Steam по хэшированным классам
(вида _2-O4ZG0KrnSrzISHBKctFQ). После обновления клиента Valve иногда их
меняет. Скрипт берёт CSS текущей версии Steam и показывает, какие классы
из темы в нём больше не встречаются (файл:строка).

    python tools/check_classes.py                 # скачать текущий Steam UI с CDN Valve
    python tools/check_classes.py --steamui "C:/Program Files (x86)/Steam/steamui"

Код возврата 0 — всё на месте, 1 — есть пропавшие классы.
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_steam_palette as steam  # noqa: E402  (загрузчик CSS Steam)

THEME = steam.THEME
# Хэш Steam: 18–30 символов, есть цифра или две заглавные подряд, или дефис с ними.
HASH = re.compile(r"\.(-?[A-Za-z_][A-Za-z0-9_-]{17,29})(?![A-Za-z0-9_-])")
SKIP_DIRS = ("fallback", "tools", "store", "presets", "fonts")


def theme_files():
    for path in sorted(glob.glob(os.path.join(THEME, "**", "*.css"), recursive=True)):
        rel = os.path.relpath(path, THEME).replace("\\", "/")
        if rel.split("/")[0] in SKIP_DIRS or rel == "colors.css":
            continue
        yield rel, path


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--steamui", help="папка steamui (по умолчанию — скачать с CDN Steam)")
    args = ap.parse_args()

    css, _js, version = steam.load_from_dir(args.steamui) if args.steamui else steam.load_from_cdn()
    steam_css = "\n".join(css.values())
    known = set(re.findall(r"\.(-?[A-Za-z_][A-Za-z0-9_-]*)", steam_css))

    total, missing = 0, []
    for rel, path in theme_files():
        with open(path, encoding="utf-8") as f:
            for n, line in enumerate(f, 1):
                code = re.sub(r"/\*.*?\*/", "", line)
                for h in HASH.findall(code):
                    if not looks_hashed(h):
                        continue
                    total += 1
                    if h not in known:
                        missing.append((rel, n, h))

    print(f"Steam client {version}: проверено {total} упоминаний хэш-классов")
    if not missing:
        print("Все классы на месте.")
        return 0
    print(f"Не найдено в текущем Steam: {len(missing)}")
    for rel, n, h in missing:
        print(f"  {rel}:{n}  .{h}")
    return 1


def looks_hashed(name: str) -> bool:
    """Отличает хэш Steam от обычного читаемого класса (DialogButton и т.п.)."""
    core = name.lstrip("-_")
    if name.startswith(("btn", "home_", "store_", "game_", "search_")):
        return False  # читаемые классы магазина (store.steampowered.com)
    if len(name) < 18:
        return False
    return bool(re.search(r"\d", core) or re.search(r"[A-Z]{2}", core) or "-" in core)


if __name__ == "__main__":
    sys.exit(main())
