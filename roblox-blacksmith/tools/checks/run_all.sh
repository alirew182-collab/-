#!/usr/bin/env bash
# Статические проверки кода игры без Roblox Studio.
# Запуск из папки roblox-blacksmith:  bash tools/checks/run_all.sh
set -e
cd "$(dirname "$0")"
SRC=../../src
DEFS=globalTypes.d.luau
if [ ! -f "$DEFS" ]; then
  echo "Скачиваю определения Roblox API (luau-lsp)..."
  curl -sSL -o "$DEFS" https://raw.githubusercontent.com/JohnnyMorganz/luau-lsp/main/scripts/globalTypes.d.luau
fi
echo "== Парность блоков";            python3 blocks.py "$SRC" | tail -1
echo "== Порядок объявлений";         python3 order.py "$SRC" | tail -1
echo "== Ключи локализации и Remote"; python3 refs.py "$SRC" | grep -v '^unused'
echo "== Циклические require";        python3 cycles.py "$SRC"
echo "== Конфиги и события";          python3 config_check.py "$SRC"
echo "== Roblox API";                 python3 api_check.py "$DEFS" "$SRC"
