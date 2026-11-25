# Bleach Minimal Dark — установщик темы для Millennium (Windows).
# Запуск: двойной щелчок по install.bat (или: powershell -ExecutionPolicy Bypass -File install.ps1)
# Необязательно: -SteamPath "D:\Games\Steam"
param([string]$SteamPath)

$ErrorActionPreference = 'Stop'
$ThemeName = 'bleach-minimal-dark'
$src = $PSScriptRoot

function Fail($msg) { Write-Host ''; Write-Host "ОШИБКА: $msg" -ForegroundColor Red; exit 1 }

if (-not (Test-Path -LiteralPath (Join-Path $src 'skin.json'))) {
    Fail "рядом с install.ps1 нет skin.json. Запускайте установщик из папки темы."
}

# 1. Папка Steam: параметр → реестр → путь по умолчанию
if (-not $SteamPath) {
    try { $SteamPath = (Get-ItemProperty -Path 'HKCU:\Software\Valve\Steam' -ErrorAction Stop).SteamPath } catch { }
}
if (-not $SteamPath) { $SteamPath = 'C:\Program Files (x86)\Steam' }
$SteamPath = $SteamPath -replace '/', '\'
if (-not (Test-Path -LiteralPath (Join-Path $SteamPath 'steam.exe'))) {
    Write-Host "steam.exe не найден в: $SteamPath" -ForegroundColor Yellow
    $SteamPath = Read-Host 'Введите путь к папке Steam (где лежит steam.exe)'
    if (-not (Test-Path -LiteralPath (Join-Path $SteamPath 'steam.exe'))) { Fail "steam.exe не найден и там." }
}
Write-Host "Steam: $SteamPath"

# 2. Millennium установлен?
$millennium = Join-Path $SteamPath 'millennium'
if (-not (Test-Path -LiteralPath $millennium)) {
    Write-Host 'Папка millennium не найдена — похоже, Millennium не установлен.' -ForegroundColor Yellow
    Write-Host 'Установите его с https://steambrew.app и запустите Steam хотя бы раз, затем повторите.' -ForegroundColor Yellow
}

# 3. Куда копировать: актуальный Millennium + старый steamui\skins, если он есть
$targets = @(Join-Path $millennium 'themes')
$legacy = Join-Path $SteamPath 'steamui\skins'
if (Test-Path -LiteralPath $legacy) { $targets += $legacy }

$srcFull = (Resolve-Path -LiteralPath $src).Path.TrimEnd('\')
foreach ($root in $targets) {
    $dest = Join-Path $root $ThemeName
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    $destFull = (Resolve-Path -LiteralPath $dest).Path.TrimEnd('\')
    if ($destFull -ieq $srcFull) { Write-Host "Уже на месте: $dest"; continue }

    # robocopy (встроен в Windows) надёжно сливает папки при повторной установке
    $nested = Join-Path $dest $ThemeName
    & robocopy $srcFull $destFull /E /XD $nested /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { Fail "robocopy не смог скопировать файлы в $dest (код $LASTEXITCODE)" }
    if (-not (Test-Path -LiteralPath (Join-Path $dest 'skin.json'))) { Fail "не удалось скопировать в $dest" }

    # частая ошибка: тема распакована во вложенную папку themes\bleach-minimal-dark\bleach-minimal-dark
    if ((Test-Path -LiteralPath (Join-Path $nested 'skin.json'))) {
        if (((Resolve-Path -LiteralPath $nested).Path.TrimEnd('\')) -ieq $srcFull) {
            Write-Host "Тема скопирована на правильный уровень. Лишнюю вложенную папку можно удалить вручную: $nested" -ForegroundColor Yellow
        } else {
            Remove-Item -LiteralPath $nested -Recurse -Force
            Write-Host "Удалена лишняя вложенная папка: $nested"
        }
    }
    Write-Host "Установлено: $dest" -ForegroundColor Green
}

Write-Host ''
if (Get-Process -Name 'steam' -ErrorAction SilentlyContinue) {
    Write-Host 'Steam запущен — полностью закройте его (Steam → Выход) и откройте снова.' -ForegroundColor Yellow
}
Write-Host 'Дальше: Steam → Millennium → Темы → выберите «Bleach Minimal Dark».'
