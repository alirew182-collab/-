#!/usr/bin/env python3
"""
Bleach Minimal Dark — генератор перекраски палитры Steam.

Что делает:
  1. Берёт стили клиента Steam (sp.css + library.css) — из локальной папки
     steamui или скачивает официальный пакет steamui_websrc_all с CDN Valve.
  2. Находит все правила, где Steam задаёт цвета своей палитры (тёмно-синие
     фоны, серый текст, синий акцент), и пишет те же селекторы с цветами
     через переменные темы (--bleach-*). Зелёный/жёлтый/красный (статусы,
     ошибки) не трогаются; исключение — кнопка «Играть».
  3. Результат: generated/steam-palette.css. Только var(--bleach-*), ни одного
     цвета значением.

Классы Steam хэшированы и иногда меняются с обновлениями клиента. Если после
обновления Steam тема «слетела» — просто запустите скрипт ещё раз:

    python tools/build_steam_palette.py              # скачать текущий Steam UI
    python tools/build_steam_palette.py --steamui "C:/Program Files (x86)/Steam/steamui"

Нужен только Python 3.8+, без сторонних библиотек.
"""
from __future__ import annotations

import argparse
import colorsys
import io
import os
import re
import sys
import urllib.request
import zipfile

CDN = "https://client-update.steamstatic.com"
MANIFEST = "steam_client_win64"
CSS_FILES = ("sp.css", "library.css")

HERE = os.path.dirname(os.path.abspath(__file__))
THEME = os.path.dirname(HERE)
OUT = os.path.join(THEME, "generated", "steam-palette.css")
OUT_VARS = os.path.join(THEME, "generated", "steam-colors.css")

# Статусные цвета Steam (зелёный «в игре», жёлтый, красный…), которые тема не
# перекрашивает, выносятся в переменные --steam-RRGGBB (generated/steam-colors.css),
# чтобы в правилах не было ни одного цвета значением.
STEAM_VARS: dict[str, str] = {}

# Селекторы Big Picture / Steam Deck — тема их не трогает.
SKIP_SELECTOR = re.compile(r"\.(GamepadMode|BasicUI|gamepadui|SteamOS)\b")

BOOST = r":not(#\#)"  # +1 к специфичности (ID), совпадения не меняет

NAMED = {
    "white": "#ffffff", "black": "#000000", "gray": "#808080", "grey": "#808080",
    "dimgray": "#696969", "dimgrey": "#696969", "darkgray": "#a9a9a9", "darkgrey": "#a9a9a9",
    "lightgray": "#d3d3d3", "lightgrey": "#d3d3d3", "silver": "#c0c0c0", "gainsboro": "#dcdcdc",
    "whitesmoke": "#f5f5f5", "red": "#ff0000", "green": "#008000", "lime": "#00ff00",
    "blue": "#0000ff", "yellow": "#ffff00", "orange": "#ffa500", "gold": "#ffd700",
    "hotpink": "#ff69b4", "purple": "#800080", "cyan": "#00ffff", "magenta": "#ff00ff",
    "lightblue": "#add8e6", "skyblue": "#87ceeb", "dodgerblue": "#1e90ff", "steelblue": "#4682b4",
}
COLOR_RE = re.compile(r"#[0-9a-fA-F]{3,8}\b|\b(?:rgba?|hsla?)\([^()]*\)|(?<![\w-])(?:" + "|".join(NAMED) + r")(?![\w-])", re.I)
URL_RE = re.compile(r"url\([^)]*\)", re.I)

BG_PROPS = {"background", "background-color", "background-image"}
TEXT_PROPS = {"color", "fill", "stroke", "caret-color", "-webkit-text-fill-color", "text-decoration-color"}
LINE_PROPS = {
    "border", "border-top", "border-right", "border-bottom", "border-left",
    "border-color", "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
    "outline", "outline-color", "column-rule", "column-rule-color",
}
SHADOW_PROPS = {"box-shadow", "text-shadow", "filter"}
COLOR_PROPS = BG_PROPS | TEXT_PROPS | LINE_PROPS | SHADOW_PROPS


# ── загрузка CSS Steam ──────────────────────────────────────────────────
def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "bleach-theme-builder"})
    with urllib.request.urlopen(req, timeout=300) as r:
        return r.read()


def load_from_cdn() -> tuple[dict[str, str], str, str]:
    manifest = fetch(f"{CDN}/{MANIFEST}").decode("utf-8", "replace")
    version = re.search(r'"version"\s*"(\d+)"', manifest).group(1)
    block = manifest.split('"steamui_websrc_all"', 1)[1]
    name = re.search(r'"file"\s*"([^"]+)"', block).group(1)
    print(f"Steam client {version}: скачиваю {name} …", file=sys.stderr)
    data = fetch(f"{CDN}/{name}")
    z = zipfile.ZipFile(io.BytesIO(data))
    css, js = {}, []
    for entry in z.namelist():
        norm = entry.replace("\\", "/")
        base = norm.rsplit("/", 1)[-1]
        if norm.startswith("steamui/css/") and base in CSS_FILES:
            css[base] = z.read(entry).decode("utf-8", "replace")
        elif norm.startswith("steamui/") and norm.count("/") == 1 and base.endswith(".js"):
            js.append(z.read(entry).decode("utf-8", "replace"))
    return css, "\n".join(js), version


def load_from_dir(path: str) -> tuple[dict[str, str], str, str]:
    css, js = {}, []
    for name in CSS_FILES:
        with open(os.path.join(path, "css", name), encoding="utf-8", errors="replace") as f:
            css[name] = f.read()
    for fn in os.listdir(path):
        if fn.endswith(".js"):
            with open(os.path.join(path, fn), encoding="utf-8", errors="replace") as f:
                js.append(f.read())
    return css, "\n".join(js), "local"


# ── разбор CSS (минимальный, без зависимостей) ──────────────────────────
def strip_comments(s: str) -> str:
    return re.sub(r"/\*[\s\S]*?\*/", "", s)


def find_block_end(s: str, i: int) -> int:
    """i указывает на '{'; вернуть индекс парной '}'."""
    depth, q = 0, None
    while i < len(s):
        c = s[i]
        if q:
            if c == "\\":
                i += 1
            elif c == q:
                q = None
        elif c in "\"'":
            q = c
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(s) - 1


def parse(s: str):
    """Список узлов: ('rule', selector, body) | ('at', prelude, [узлы])."""
    nodes, i, n = [], 0, len(s)
    while i < n:
        j = i
        q = None
        while j < n:
            c = s[j]
            if q:
                if c == "\\":
                    j += 1
                elif c == q:
                    q = None
            elif c in "\"'":
                q = c
            elif c in "{;":
                break
            j += 1
        if j >= n:
            break
        prelude = s[i:j].strip()
        if s[j] == ";":  # @import/@charset
            i = j + 1
            continue
        end = find_block_end(s, j)
        body = s[j + 1:end]
        if prelude.startswith("@"):
            kw = prelude.split(None, 1)[0].lower()
            if kw in ("@media", "@supports", "@container", "@layer", "@document"):
                nodes.append(("at", prelude, parse(body)))
            # @keyframes, @font-face, @property и т.п. пропускаем
        else:
            nodes.append(("rule", prelude, body))
        i = end + 1
    return nodes


def split_top(s: str, sep: str) -> list[str]:
    out, depth, q, cur = [], 0, None, []
    for c in s:
        if q:
            cur.append(c)
            if c == q:
                q = None
            continue
        if c in "\"'":
            q = c
        elif c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
        elif c == sep and depth == 0:
            out.append("".join(cur))
            cur = []
            continue
        cur.append(c)
    out.append("".join(cur))
    return out


# ── цвета ───────────────────────────────────────────────────────────────
def parse_color(tok: str):
    raw = tok.lower().strip()
    raw = NAMED.get(raw, raw)
    t = raw.replace(" ", "")
    if t.startswith("#"):
        h = t[1:]
        if len(h) in (3, 4):
            h = "".join(ch * 2 for ch in h)
        if len(h) not in (6, 8):
            return None
        r, g, b = (int(h[k:k + 2], 16) for k in (0, 2, 4))
        a = int(h[6:8], 16) / 255 if len(h) == 8 else 1.0
        return r, g, b, a
    m = re.match(r"rgba?\(([^)]*)\)", raw)
    if m:
        parts = [p for p in re.split(r"[\s,/]+", m.group(1).strip()) if p]
        if len(parts) < 3 or any(not re.fullmatch(r"-?[\d.]+%?", p) for p in parts):
            return None  # var(), none, calc() и т.п. — не трогаем
        try:
            vals = [float(p.rstrip("%")) * (2.55 if p.endswith("%") else 1) for p in parts[:3]]
            a = float(parts[3].rstrip("%")) / (100 if parts[3].endswith("%") else 1) if len(parts) > 3 else 1.0
        except (ValueError, IndexError):
            return None
        return vals[0], vals[1], vals[2], a
    m = re.match(r"hsla?\(([^)]*)\)", raw)
    if m:
        parts = [p for p in re.split(r"[\s,/]+", m.group(1).strip()) if p]
        try:
            hh = float(parts[0].replace("deg", "")) / 360
            ss = float(parts[1].rstrip("%")) / 100
            ll = float(parts[2].rstrip("%")) / 100
            a = float(parts[3].rstrip("%")) / (100 if parts[3].endswith("%") else 1) if len(parts) > 3 else 1.0
        except (ValueError, IndexError):
            return None
        rr, gg, bb = colorsys.hls_to_rgb(hh % 1, ll, ss)
        return rr * 255, gg * 255, bb * 255, a
    return None


def with_alpha(var: str, a: float) -> str:
    if a >= 0.995:
        return f"var({var})"
    return f"color-mix(in srgb, var({var}) {round(a * 100, 1):g}%, transparent)"


def role(kind: str, rgba, play_button: bool) -> str | None:
    """Вернуть переменную темы для цвета или None (не трогать)."""
    r, g, b, a = rgba
    if a <= 0.004:
        return None
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    hue = h * 360
    blueish = 185 <= hue <= 245
    greenish = 75 <= hue <= 160

    # Кнопка «Играть»: зелёная → акцентная заливка
    if play_button and greenish and s > 0.3:
        return {"bg": "--bleach-accent-fill", "text": "--bleach-accent-text",
                "line": "--bleach-accent", "shadow": "--bleach-glow-soft"}[kind]

    # Синий акцент Steam
    if blueish and s >= 0.55 and 0.22 <= l <= 0.9:
        if kind == "bg":
            return "--bleach-accent-wash" if l < 0.36 else "--bleach-accent-fill"
        if kind == "text":
            return "--bleach-accent-text"
        if kind == "line":
            return "--bleach-accent"
        return "--bleach-glow-soft"

    # Приглушённый стальной синий (выделение, ссылки) — тоже акцент, но мягче
    if blueish and 0.3 <= s < 0.55 and 0.42 <= l <= 0.8:
        return {"bg": "--bleach-accent-wash", "text": "--bleach-accent-text",
                "line": "--bleach-accent", "shadow": "--bleach-glow-faint"}[kind]

    neutral = s < 0.3 or (blueish and l < 0.42 and (s < 0.6 or l < 0.25))
    if not neutral:
        return None  # статусные цвета (зелёный/жёлтый/красный/фиолетовый)
    if (r, g, b) in ((255, 255, 255), (0, 0, 0)):
        return None  # чистые белый и чёрный — уже в стиле темы

    if kind == "bg":
        if l < 0.055: return "--bleach-bg-deep"
        if l < 0.10: return "--bleach-bg"
        if l < 0.16: return "--bleach-surface"
        if l < 0.23: return "--bleach-surface-raised"
        if l < 0.32: return "--bleach-surface-hover"
        if l < 0.55: return "--bleach-surface-strong"
        if l < 0.80: return "--bleach-text-muted"
        return "--bleach-text"
    if kind == "text":
        if l < 0.30: return "--bleach-bg"
        if l < 0.45: return "--bleach-text-faint"
        if l < 0.66: return "--bleach-text-muted"
        return "--bleach-text"  # в т.ч. body{color:#b8bcbf} — основной текст Steam
    if kind == "line":
        if l < 0.30: return "--bleach-line"
        if l < 0.60: return "--bleach-line-strong"
        return "--bleach-text-muted"
    return None  # нейтральные тени оставляем


def passthrough(rgba) -> str:
    """Цвет, который тема не меняет, — тоже через переменную."""
    r, g, b, a = (round(rgba[0]), round(rgba[1]), round(rgba[2]), rgba[3])
    if a <= 0.004:
        return "transparent"
    if (r, g, b) == (255, 255, 255):
        return with_alpha("--bleach-white", a)
    if (r, g, b) == (0, 0, 0):
        return with_alpha("--bleach-shadow", a)
    hx = f"{r:02x}{g:02x}{b:02x}"
    STEAM_VARS[f"--steam-{hx}"] = "#" + hx
    return with_alpha(f"--steam-{hx}", a)


def recolor_value(prop: str, value: str, play_button: bool) -> tuple[str, bool]:
    kind = ("bg" if prop in BG_PROPS else "text" if prop in TEXT_PROPS
            else "line" if prop in LINE_PROPS else "shadow")
    # не трогаем цвета внутри url(...)
    hidden = {}

    def hide(m):
        key = f"\0{len(hidden)}\0"
        hidden[key] = m.group(0)
        return key

    v = URL_RE.sub(hide, value)
    changed = False

    def sub(m):
        nonlocal changed
        rgba = parse_color(m.group(0))
        if rgba is None:
            return m.group(0)
        var = role(kind, rgba, play_button)
        if var is not None:
            changed = True
            return with_alpha(var, rgba[3])
        return passthrough(rgba)

    v = COLOR_RE.sub(sub, v)
    for k, orig in hidden.items():
        v = v.replace(k, orig)
    return v, changed


# ── селекторы ───────────────────────────────────────────────────────────
PSEUDO_EL = re.compile(
    r"::|:(?:before|after|first-line|first-letter|-webkit-scrollbar[\w-]*|-webkit-[\w-]*placeholder"
    r"|-webkit-slider[\w-]*|-webkit-inner-spin-button|-webkit-outer-spin-button|-webkit-search[\w-]*"
    r"|-webkit-progress[\w-]*|-webkit-meter[\w-]*|-webkit-resizer|-webkit-file-upload-button)\b"
)


def boost_one(sel: str) -> str:
    sel = sel.strip()
    # граница последнего составного селектора (после комбинатора вне скобок)
    depth, last = 0, 0
    for k, c in enumerate(sel):
        if c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
        elif depth == 0 and c in " >+~":
            last = k + 1
    head, tail = sel[:last], sel[last:]
    depth = 0
    for k in range(len(tail)):
        c = tail[k]
        if c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
        elif depth == 0 and c == ":" and PSEUDO_EL.match(tail, k):
            return head + tail[:k] + BOOST + tail[k:]
    return head + tail + BOOST


def boost(selector: str) -> str | None:
    parts = [p for p in split_top(selector, ",") if p.strip() and not SKIP_SELECTOR.search(p)]
    if not parts:
        return None
    return ",".join(boost_one(p) for p in parts)


# ── генерация ───────────────────────────────────────────────────────────
def emit(nodes, play_hashes: set[str], stats: dict, indent: str = "") -> list[str]:
    out = []
    for node in nodes:
        if node[0] == "at":
            inner = emit(node[2], play_hashes, stats, indent + "  ")
            if inner:
                out.append(f"{indent}{node[1]}{{")
                out.extend(inner)
                out.append(f"{indent}}}")
            continue
        _, selector, body = node
        decls = []
        any_changed = False
        is_play = any(h in selector for h in play_hashes)
        for d in split_top(body, ";"):
            if ":" not in d:
                continue
            prop, val = d.split(":", 1)
            prop = prop.strip().lower()
            if prop not in COLOR_PROPS:
                continue
            val = val.strip()
            new, changed = recolor_value(prop, val, is_play)
            any_changed |= changed
            # переносим ВСЕ цветовые объявления правила (с усилением), чтобы
            # сохранить порядок «база → состояние» внутри самого Steam
            decls.append(f"{prop}:{new}")
        if not any_changed:
            continue
        sel = boost(selector)
        if not sel:
            continue
        stats["rules"] += 1
        out.append(f"{indent}{sel}{{{';'.join(decls)}}}")
    return out


def play_button_hashes(js_text: str, css_text: str) -> set[str]:
    """Все классы модулей кнопки «Играть» (в модуле есть ключ PlayButton:"…"):
    сама кнопка, её зелёная заливка (Green), дочерние элементы."""
    hashes = set()
    for obj in re.findall(r'\{[^{}]*PlayButton:"[^{}]*\}', js_text):
        for val in re.findall(r':"([A-Za-z0-9_-]{18,30})"', obj):
            hashes.add(val)
    return {h for h in hashes if "." + h in css_text}


# ── шаблоны акцентов: {{Модуль.Класс}} → .хэш ───────────────────────────
TEMPLATES = (("steam-accents.css", "steam-accents.css"), ("steam-glow.css", "steam-glow.css"))


def class_modules(js_text: str) -> list[dict[str, str]]:
    mods = []
    for obj in re.findall(r"\{[^{}]*\}", js_text):
        pairs = re.findall(r'"?([\w$-]+)"?:"([A-Za-z0-9_ -]+)"', obj)
        if len(pairs) >= 2 and any(re.fullmatch(r"[A-Za-z0-9_-]{18,30}", v.split(" ")[0]) for _, v in pairs):
            mods.append({k: v.split(" ")[0] for k, v in pairs})
    return mods


def render_template(text: str, mods, css_text: str) -> tuple[str, list[str]]:
    missing = []

    def resolve(m):
        anchor, key = m.group(1), m.group(2)
        cands = [d[key] for d in mods if anchor in d and key in d]
        used = [h for h in cands if "." + h in css_text]
        if used or cands:
            return "." + (used or cands)[0]
        missing.append(f"{anchor}.{key}")
        return "\0MISSING\0"

    out = re.sub(r"\{\{([\w$-]+)\.([\w$-]+)\}\}", resolve, text)
    # выбрасываем правила, где не нашёлся хоть один класс
    kept = []
    for chunk in re.split(r"\n\s*\n", out):  # блоки в шаблонах разделены пустой строкой
        if "\0MISSING\0" in chunk:
            continue
        kept.append(chunk)
    return "\n\n".join(kept).rstrip() + "\n", sorted(set(missing))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--steamui", help="папка steamui (по умолчанию — скачать с CDN Steam)")
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()

    if args.steamui:
        css, js_text, version = load_from_dir(args.steamui)
    else:
        css, js_text, version = load_from_cdn()

    all_css = "\n".join(strip_comments(css[n]) for n in CSS_FILES if n in css)
    play = play_button_hashes(js_text, all_css)
    print(f"классы кнопки «Играть»: {len(play)}", file=sys.stderr)

    stats = {"rules": 0}
    body = emit(parse(all_css), play, stats)
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write("/*\n  СГЕНЕРИРОВАНО tools/build_steam_palette.py — не правьте вручную.\n"
                f"  Источник: Steam client {version} ({', '.join(CSS_FILES)}).\n"
                f"  Правил: {stats['rules']}. Цвета только через var(--bleach-*).\n*/\n")
        f.write("\n".join(body) + "\n")
    with open(OUT_VARS if args.out == OUT else args.out.replace(".css", "-colors.css"), "w", encoding="utf-8", newline="\n") as f:
        f.write("/*\n  СГЕНЕРИРОВАНО tools/build_steam_palette.py — не правьте вручную.\n"
                "  Статусные цвета Steam, которые тема сохраняет как есть (зелёный «в игре»,\n"
                "  жёлтый, красный и т.п.). Вынесены в переменные, чтобы правила\n"
                "  steam-palette.css не содержали цветов значением.\n*/\n:root {\n")
        for k in sorted(STEAM_VARS):
            f.write(f"    {k}: {STEAM_VARS[k]};\n")
        f.write("}\n")
    print(f"готово: {args.out} — {stats['rules']} правил, {len(STEAM_VARS)} статусных цветов", file=sys.stderr)

    if args.out == OUT:
        mods = class_modules(js_text)
        for src, dst in TEMPLATES:
            with open(os.path.join(HERE, "templates", src), encoding="utf-8") as f:
                text, missing = render_template(f.read(), mods, all_css)
            text = text.replace("/*", f"/*\n  СГЕНЕРИРОВАНО из tools/templates/{src} для Steam client {version}.\n", 1)
            with open(os.path.join(THEME, "generated", dst), "w", encoding="utf-8", newline="\n") as f:
                f.write(text)
            note = f" (не найдены: {', '.join(missing)})" if missing else ""
            print(f"готово: generated/{dst}{note}", file=sys.stderr)


if __name__ == "__main__":
    main()
