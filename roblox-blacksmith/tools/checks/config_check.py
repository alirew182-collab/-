import re, sys, pathlib
src = pathlib.Path(sys.argv[1]); C = src/'shared/Config'
read = lambda p: re.sub(r'--[^\n]*', '', (C/p).read_text(encoding='utf-8'))
def top_keys(text, start_pat):
    # ключи верхнего уровня таблицы после start_pat: "\tName = {"
    return set(re.findall(r'^\t([A-Z]\w*) = \{', text, re.M))
mobs = top_keys(read('Mobs.luau'), '')
ores = top_keys(read('Ores.luau'), '')
picks = top_keys(read('Pickaxes.luau'), '')
rar = top_keys(read('Rarities.luau'), '')
traits = top_keys(read('Traits.luau'), '')
weapons = top_keys(read('Weapons.luau'), '')
world = read('World.luau'); gates = read('Gates.luau')
zones = set(re.findall(r'^\t\t(\w+) = \{\n\t\t\tId = "\1"', world, re.M))
audio = set(re.findall(r'^\t(\w+) = "', read('Audio.luau'), re.M))
init = read('init.luau'); tags = set(re.findall(r'^\t\t\w+ = "(\w+)"', init, re.M))
errs = []
def need(kind, val, pool, where):
    if val not in pool: errs.append(f'{where}: {kind} "{val}" не найден')
for m in re.findall(r'Mob = "(\w+)"', gates): need('моб', m, mobs, 'Gates')
for m in re.findall(r'Boss = "(\w+)"', gates): need('босс', m, mobs, 'Gates')
for m in re.findall(r'OreId = "(\w+)"', gates): need('руда', m, ores, 'Gates')
for m in re.findall(r'Mob = "(\w+)"', world): need('моб', m, mobs, 'World')
for m in re.findall(r'Zone = "(\w+)"', world): need('зона', m, zones, 'World')
for m in re.findall(r'OreId = "(\w+)"', world): need('руда', m, ores, 'World')
for m in re.findall(r'Music = "(\w+)"', world): need('звук', m, audio, 'World')
for m in re.findall(r'OreId = "(\w+)"', read('Mobs.luau')): need('руда', m, ores, 'Mobs.Drops')
tut = read('Tutorial.luau')
for m in re.findall(r'(?:OreId|TargetOre) = "(\w+)"', tut): need('руда', m, ores, 'Tutorial')
for m in re.findall(r'TargetTag = "(\w+)"', tut): need('тег', m, tags, 'Tutorial')
for f in ('Daily.luau', 'Codes.luau'):
    for m in re.findall(r'Ores = \{([^}]*)\}', read(f)):
        for o in re.findall(r'(\w+) = \d', m): need('руда', o, ores, f)
forg = read('Forging.luau')
for m in re.findall(r'MinRarity = "(\w+)"', forg): need('редкость', m, rar, 'Forging')
need('кирка', re.search(r'StartingPickaxe = "(\w+)"', read('Economy.luau')).group(1), picks, 'Economy')
# события: кто отправляет и кто ждёт
code = '\n'.join(p.read_text(encoding='utf-8') for p in src.rglob('*.luau'))
emitted = set(re.findall(r'ProgressService:Record\(\w+, "(\w+)"', code))
for f in ('Tutorial.luau', 'Daily.luau'):
    for e in re.findall(r'Event = "(\w+)"', read(f)):
        if e not in emitted: errs.append(f'{f}: событие "{e}" никто не отправляет — задание не выполнить')
# звуки
for s in set(re.findall(r'Sound\.Play\("(\w+)"', code)): need('звук', s, audio, 'Sound.Play')
for s in re.findall(r'Sound = "(\w+)"', code): need('звук', s, audio, 'SystemWindow')
print('мобов', len(mobs), 'руд', len(ores), 'зон', len(zones), 'звуков', len(audio), 'событий', sorted(emitted))
print('\n'.join(errs) or 'ошибок нет')
