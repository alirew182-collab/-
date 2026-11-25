# Minimal Dark · Bleach — установщик надстройки (Windows).
# Делает КОПИЮ вашего Minimal Dark в отдельную тему «Minimal Dark · Bleach»,
# добавляет слой Bleach и вкладку настроек «Bleach». Оригинал не меняется.
#
# Запуск: двойной щелчок по install.bat
# Параметры (необязательно):
#   -Source  "путь\к\Minimal-Dark-for-Steam-6.6.8.zip"  (или к распакованной папке)
#   -SteamPath "D:\Games\Steam"
param([string]$Source, [string]$SteamPath)

$ErrorActionPreference = 'Stop'
$ThemeName = 'minimal-dark-bleach'
$Addon = $PSScriptRoot
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false
$ImageExt = @('.jpg', '.jpeg', '.png', '.webp', '.gif', '.avif')

function Fail($msg) { Write-Host ''; Write-Host "ОШИБКА: $msg" -ForegroundColor Red; exit 1 }
function Info($msg) { Write-Host $msg }
function Good($msg) { Write-Host $msg -ForegroundColor Green }
function Warn($msg) { Write-Host $msg -ForegroundColor Yellow }

function Test-MinimalDark($dir) {
    $skin = Join-Path $dir 'skin.json'
    if (-not (Test-Path -LiteralPath $skin)) { return $false }
    if (-not (Test-Path -LiteralPath (Join-Path $dir 'libraryroot.custom.css'))) { return $false }
    $text = [System.IO.File]::ReadAllText($skin)
    return ($text -match '"name"\s*:\s*"Minimal Dark"')
}

function Find-ThemeDir($root) {
    if (Test-MinimalDark $root) { return $root }
    foreach ($d in Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue) {
        if (Test-MinimalDark $d.FullName) { return $d.FullName }
    }
    return $null
}

function Write-Text($path, $text) { [System.IO.File]::WriteAllText($path, $text, $Utf8NoBom) }

# ── 1. Папка Steam ───────────────────────────────────────────────────────
if (-not $SteamPath) {
    try { $SteamPath = (Get-ItemProperty -Path 'HKCU:\Software\Valve\Steam' -ErrorAction Stop).SteamPath } catch { }
}
if (-not $SteamPath) { $SteamPath = 'C:\Program Files (x86)\Steam' }
$SteamPath = $SteamPath -replace '/', '\'
if (-not (Test-Path -LiteralPath (Join-Path $SteamPath 'steam.exe'))) {
    Warn "steam.exe не найден в: $SteamPath"
    $SteamPath = Read-Host 'Введите путь к папке Steam (где лежит steam.exe)'
    if (-not (Test-Path -LiteralPath (Join-Path $SteamPath 'steam.exe'))) { Fail 'steam.exe не найден и там.' }
}
$Themes = Join-Path $SteamPath 'millennium\themes'
if (-not (Test-Path -LiteralPath $Themes)) {
    Warn 'Папка millennium\themes не найдена — установите Millennium (https://steambrew.app) и запустите Steam хотя бы раз.'
    New-Item -ItemType Directory -Force -Path $Themes | Out-Null
}
$Target = Join-Path $Themes $ThemeName
Info "Steam: $SteamPath"

# ── 2. Где взять Minimal Dark ────────────────────────────────────────────
$tempDir = $null
$srcDir = $null
if ($Source) {
    if (-not (Test-Path -LiteralPath $Source)) { Fail "не найден: $Source" }
    $candidates = @((Resolve-Path -LiteralPath $Source).Path)
} else {
    $downloads = Join-Path $env:USERPROFILE 'Downloads'
    $zips = @()
    foreach ($dir in @($Addon, (Split-Path $Addon -Parent), $downloads)) {
        if (Test-Path -LiteralPath $dir) {
            $zips += Get-ChildItem -LiteralPath $dir -Filter 'Minimal-Dark-for-Steam*.zip' -File -ErrorAction SilentlyContinue |
                     Sort-Object LastWriteTime -Descending | ForEach-Object { $_.FullName }
        }
    }
    $installed = Get-ChildItem -LiteralPath $Themes -Directory -ErrorAction SilentlyContinue |
                 Where-Object { $_.Name -ne $ThemeName -and (Test-MinimalDark $_.FullName) } |
                 ForEach-Object { $_.FullName }
    $candidates = @($zips) + @($installed)
}

foreach ($c in $candidates) {
    if ((Get-Item -LiteralPath $c).PSIsContainer) {
        $found = Find-ThemeDir $c
    } else {
        $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("md-bleach-" + [guid]::NewGuid().ToString('N'))
        Expand-Archive -LiteralPath $c -DestinationPath $tempDir -Force
        $found = Find-ThemeDir $tempDir
    }
    if ($found) { $srcDir = $found; Info "Minimal Dark: $c"; break }
}
if (-not $srcDir) {
    Fail ('Minimal Dark не найден. Положите архив Minimal-Dark-for-Steam-*.zip рядом с install.bat ' +
          '(или в «Загрузки»), либо установите Minimal Dark в Millennium, либо укажите -Source.')
}

# ── 3. Сохранить картинки, которые вы могли положить прямо в установленную тему ─
$targetAssets = Join-Path $Target 'bleach\assets'
$addonAssets = Join-Path $Addon 'bleach\assets'
if (Test-Path -LiteralPath $targetAssets) {
    & robocopy $targetAssets $addonAssets /E /XC /XN /XO /NFL /NDL /NJH /NJS /NP | Out-Null
}

# ── 4. Копия Minimal Dark + слой Bleach ──────────────────────────────────
New-Item -ItemType Directory -Force -Path $Target | Out-Null
& robocopy $srcDir $Target /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { Fail "не удалось скопировать Minimal Dark (robocopy $LASTEXITCODE)" }
& robocopy (Join-Path $Addon 'bleach') (Join-Path $Target 'bleach') /E /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { Fail "не удалось скопировать слой Bleach (robocopy $LASTEXITCODE)" }
# Иначе встроенное обновление Millennium «обновит» копию до чистого Minimal Dark
Remove-Item -LiteralPath (Join-Path $Target 'metadata.json') -Force -ErrorAction SilentlyContinue

# ── 5. CSS под ваши картинки (только то, что реально лежит в папках) ─────
$gen = Join-Path $Target 'bleach\generated'
New-Item -ItemType Directory -Force -Path $gen | Out-Null
$assets = Join-Path $Target 'bleach\assets'
function Get-Images($dir, $filter) {
    if (-not (Test-Path -LiteralPath $dir)) { return @() }
    return @(Get-ChildItem -LiteralPath $dir -File -Filter $filter -ErrorAction SilentlyContinue |
             Where-Object { $ImageExt -contains $_.Extension.ToLower() } | Sort-Object Name)
}
function Url($rel) {
    $enc = (($rel -split '/') | ForEach-Object { [uri]::EscapeDataString($_) }) -join '/'
    return 'url("../assets/' + $enc + '")'
}

$P = 'html:root body.DesktopUI'
$CARD = ':is(._3Ehhd5MxErV_bXQE4qVhzB, .ZsU8nIIpj8MJn0vzMzSp1)'   # appportrait: PortraitImage, LandscapeImage
$IMG = 'img._24_AuLm54JVe1Zc0AApCDR'                              # appportrait_Image
$LIST = ':is(._3vHkmRShhzwd67_MtEq8-n, ._3CJctA7pLVdwHzNF5JmORE)'  # сетка и карусель полок
$HEAD = "/* Сгенерировано install.ps1 по картинкам из bleach/assets. Не правьте вручную — запустите install.bat ещё раз. */`n"

$covers = Get-Images (Join-Path $assets 'covers') '*'
$css = $HEAD
if ($covers.Count -gt 0) {
    $n = $covers.Count
    $css += "$P $CARD $IMG {`n    content: $(Url ('covers/' + $covers[0].Name)) !important;`n    object-fit: cover !important;`n}`n"
    if ($n -gt 1) {
        for ($k = 1; $k -le $n; $k++) {
            $css += "$P $LIST > :nth-child(${n}n+$k) $CARD $IMG { content: $(Url ('covers/' + $covers[$k-1].Name)) !important; }`n"
        }
    }
} else { $css += "/* В bleach/assets/covers нет картинок — обложки не меняются. */`n" }
Write-Text (Join-Path $gen 'covers.css') $css
$hover = $css
if ($covers.Count -gt 0) {
    $hover += "/* При наведении — настоящая обложка игры (appportrait_Draggable:hover) */`n"
    $hover += "$P ._1pwP4eeP1zQD7PEgmsep0W:hover $CARD $IMG { content: normal !important; }`n"
}
Write-Text (Join-Path $gen 'covers-hover.css') $hover

$hero = Get-Images $assets 'hero.*' | Select-Object -First 1
$css = $HEAD
if ($hero) {
    $css += "/* appdetails_ImgSrc — широкая картинка вверху страницы игры (и её размытые копии) */`n"
    $css += "$P img.HNbe3eZf6H7dtJ042x1vM {`n    content: $(Url $hero.Name) !important;`n    object-fit: cover !important;`n}`n"
} else { $css += "/* Нет bleach/assets/hero.* — шапка страницы игры не меняется. */`n" }
Write-Text (Join-Path $gen 'hero.css') $css

$bg = Get-Images $assets 'background.*' | Select-Object -First 1
$side = Get-Images $assets 'sidebar.*' | Select-Object -First 1
$dim = 'color-mix(in srgb, var(--minimal_dark_blacker_alt) 82%, transparent)'
$css = $HEAD
if ($bg) {
    $css += "/* Главная библиотеки: арт под затемнением (libraryhome_Container; LibraryHome прозрачная) */`n"
    $css += "$P ._3xRRJfD2xy95i9NhJxLTp0 {`n    background: linear-gradient($dim, $dim), $(Url $bg.Name) center / cover no-repeat !important;`n}`n"
    $css += "$P ._3Sb2o_mQ30IDRh0C72QUUu, $P ._2gSXCB6PbOlMxslr8hm6dm { background: transparent !important; }`n"
}
if ($side) {
    $css += "/* Левая колонка: арт внизу, плавно уходит в фон (library_LeftListSizableContainer) */`n"
    $css += "$P ._9sPoVBFyE_vE87mnZJ5aB {`n    background: linear-gradient(to bottom, var(--minimal_dark_blacker_alt) 35%, $dim 100%), $(Url $side.Name) bottom center / cover no-repeat !important;`n}`n"
    $css += "$P ._3x1HklzyDs4TEjACrRO2tB, $P ._1ijTaXJJA5YWl_fW2IxcaT { background: transparent !important; }`n"
}
if (-not $bg -and -not $side) { $css += "/* Нет bleach/assets/background.* и sidebar.* — фон не меняется. */`n" }
Write-Text (Join-Path $gen 'library-bg.css') $css

# ── 6. Вкладка «Bleach» в skin.json копии ────────────────────────────────
$skinPath = Join-Path $Target 'skin.json'
$skin = [System.IO.File]::ReadAllText($skinPath)
$original = $skin
$frag = [System.IO.File]::ReadAllText((Join-Path $Addon 'bleach\conditions.json')).Trim()
$frag = $frag.Substring(1, $frag.Length - 2).Trim()   # без внешних фигурных скобок

$rx = [regex]'"Conditions"\s*:\s*\{'
if (-not $rx.IsMatch($skin)) { Fail 'в skin.json Minimal Dark нет "Conditions" — неизвестная версия.' }
$m = $rx.Match($skin)
$skin = $skin.Insert($m.Index + $m.Length, "`n" + $frag + ",`n")
$skin = ([regex]'"name"\s*:\s*"Minimal Dark"').Replace($skin, '"name": "Minimal Dark · Bleach"', 1)
# без ссылки на GitHub Minimal Dark — чтобы обновление темы не стёрло слой Bleach
$noGit = ([regex]'"github"\s*:\s*\{[^{}]*\}\s*,').Replace($skin, '', 1)
# Проверка JSON (если сам оригинал читается PowerShell — иначе проверять нечем)
$canCheck = $true
try { $null = $original | ConvertFrom-Json } catch { $canCheck = $false }
if ($canCheck) {
    try { $null = $noGit | ConvertFrom-Json; $skin = $noGit } catch { }
    try { $null = $skin | ConvertFrom-Json } catch {
        Write-Text $skinPath $original
        Fail "не удалось встроить вкладку Bleach в skin.json: $($_.Exception.Message)"
    }
}
Write-Text $skinPath $skin

# ── 7. Итог ──────────────────────────────────────────────────────────────
if ($tempDir) { Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue }
Write-Host ''
Good "Установлено: $Target"
Info ("Картинки: обложек {0}, шапка {1}, фон {2}, левая колонка {3}" -f $covers.Count,
      $(if ($hero) { $hero.Name } else { 'нет' }), $(if ($bg) { $bg.Name } else { 'нет' }), $(if ($side) { $side.Name } else { 'нет' }))
if (Get-Process -Name 'steam' -ErrorAction SilentlyContinue) {
    Warn 'Steam запущен — полностью закройте его (Steam → Выход) и откройте снова.'
}
Info 'Дальше: Millennium → Темы → «Minimal Dark · Bleach» → настройки → вкладка «Bleach».'
