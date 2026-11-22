"""
Сверка кода Luau с определениями Roblox API (globalTypes.d.luau из luau-lsp).
Проверяет: Enum.X.Y, Instance.new("X"), GetService("X"),
свойства/методы у переменных с известным классом, ключи в таблицах свойств C.Make("X", {...}) и т.п.
Запуск: python3 api_check.py <defs.d.luau> <src dir>
"""
import json, re, sys, pathlib, collections

defs_path, src = sys.argv[1], pathlib.Path(sys.argv[2])
text = open(defs_path, encoding="utf-8").read()

meta = json.loads(text.split("\n", 1)[0][len("--#METADATA#"):])
creatable = set(meta.get("CREATABLE_INSTANCES", []))

classes = {}  # name -> {"parent":..., "members": set()}
enums = {}  # name -> set(items)
cur = None
for line in text.split("\n"):
    m = re.match(r"^declare extern type (\w+)(?: extends (\w+))? with(?: end)?\s*$", line)
    if m:
        name, parent = m.group(1), m.group(2)
        if line.rstrip().endswith(" end"):
            classes[name] = {"parent": parent, "members": set()}
            cur = None
        else:
            classes[name] = {"parent": parent, "members": set()}
            cur = name
        continue
    if cur:
        if line.strip() == "end":
            cur = None
            continue
        m = re.match(r"^\t+function (\w+)\(", line) or re.match(r"^\t+(\w+)\s*:", line)
        if m:
            classes[cur]["members"].add(m.group(1))
for name, c in classes.items():
    if name.startswith("Enum") and name.endswith("_INTERNAL"):
        enums[name[4:-9]] = c["members"]


def members(cls):
    out = set()
    seen = 0
    while cls and cls in classes and seen < 40:
        out |= classes[cls]["members"]
        cls = classes[cls]["parent"]
        seen += 1
    return out


def is_class(n):
    return n in classes and not n.startswith("Enum")


def strip_comments(s):
    s = re.sub(r"--\[\[.*?\]\]", lambda m: "\n" * m.group(0).count("\n"), s, flags=re.S)
    return re.sub(r"--[^\n]*", "", s)


issues = []
stats = collections.Counter()

for f in sorted(src.rglob("*.luau")):
    rel = f.relative_to(src)
    code = strip_comments(f.read_text(encoding="utf-8"))
    lines = code.split("\n")

    def report(i, msg):
        issues.append(f"{rel}:{i}: {msg}")

    # 1. Enum.X.Y
    for i, l in enumerate(lines, 1):
        for m in re.finditer(r"\bEnum\.(\w+)\.(\w+)", l):
            stats["enum"] += 1
            e, item = m.group(1), m.group(2)
            if e not in enums:
                report(i, f"нет перечисления Enum.{e}")
            elif item not in enums[e] and item not in ("GetEnumItems", "FromName", "FromValue"):
                report(i, f"нет значения Enum.{e}.{item}")
        # 2. Instance.new
        for m in re.finditer(r'Instance\.new\("(\w+)"\)', l):
            stats["new"] += 1
            if m.group(1) not in creatable:
                report(i, f'Instance.new("{m.group(1)}"): класс нельзя создать или его нет')
        # 3. GetService
        for m in re.finditer(r':GetService\("(\w+)"\)', l):
            stats["service"] += 1
            if not is_class(m.group(1)):
                report(i, f'GetService("{m.group(1)}"): нет такого сервиса')
        # Make / ensure / getOrCreate по строковому имени класса
        for m in re.finditer(r'(?:C\.Make|Make|ensure|getOrCreate\([^,]+,)\s*\(?\s*"(\w+)"', l):
            if not is_class(m.group(1)) and m.group(1)[0].isupper():
                report(i, f'класс "{m.group(1)}" не найден в API')

    # 4. типы переменных в файле
    vartype = collections.defaultdict(set)
    for i, l in enumerate(lines, 1):
        for m in re.finditer(r"\b(?:local\s+)?(\w+)\s*=\s*Instance\.new\(\"(\w+)\"\)", l):
            vartype[m.group(1)].add(m.group(2))
        for m in re.finditer(r"\blocal\s+(\w+)\s*=\s*game:GetService\(\"(\w+)\"\)", l):
            vartype[m.group(1)].add(m.group(2))
        for m in re.finditer(r"\b(\w+)\s*=\s*C\.Make\(\"(\w+)\"", l):
            vartype[m.group(1)].add(m.group(2))
        for m in re.finditer(r"\blocal\s+(\w+)\s*:\s*(\w+)\??\s*[=\n]", l + "\n"):
            if is_class(m.group(2)):
                vartype[m.group(1)].add(m.group(2))
        for m in re.finditer(r"[(,]\s*(\w+)\s*:\s*(\w+)\??\s*[,)]", l):
            if is_class(m.group(2)):
                vartype[m.group(1)].add(m.group(2))
        for m in re.finditer(r"\blocal\s+(\w+)\s*=.*::\s*(\w+)\??\s*$", l):
            if is_class(m.group(2)):
                vartype[m.group(1)].add(m.group(2))
    # типы, объявленные в самом файле (type Hint = ...), — не классы Roblox
    local_types = set(re.findall(r"\btype\s+(\w+)", code))
    known = {
        v: next(iter(t))
        for v, t in vartype.items()
        if len(t) == 1 and next(iter(t)) not in local_types and next(iter(t)) != "Instance"
    }
    # контейнеры, у которых в коде обращаются к дочерним объектам по имени (ReplicatedStorage.Shared)
    containers = {"ReplicatedStorage", "ServerStorage", "ServerScriptService", "StarterPlayer", "Workspace", "Folder"}

    for i, l in enumerate(lines, 1):
        for m in re.finditer(r"(?<![\w.:])(\w+)([.:])(\w+)", l):
            var, sep, name = m.groups()
            cls = known.get(var)
            if not cls:
                continue
            after = l[m.end():m.end() + 1]
            if sep == "." and after == "(":
                continue  # вызов через точку — это не свойство (редко)
            if cls in containers and name[:1].isupper():
                continue  # обращение к дочернему объекту
            stats["member"] += 1
            if name not in members(cls):
                report(i, f"{var}{sep}{name}: у {cls} нет такого члена")

    # 5. ключи таблиц свойств: C.Make("X", {...}), ensure("X", {...}), part(parent, {...})
    for i, l in enumerate(lines):
        m = re.search(r'(?:C\.Make|ensure)\("(\w+)",\s*\{', l)
        cls = m.group(1) if m else None
        if not cls and re.search(r"\bpart\(\w+, \{", l) and "WorldBuilder" in str(f):
            cls = "Part"
        if not cls or not is_class(cls):
            continue
        depth = 0
        start = l.index("{", m.end() - 1 if m else l.index("part("))
        body = l[start:]
        j = i
        chunk = []
        while True:
            for ch in body:
                chunk.append(ch)
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0:
                        break
            if depth == 0 or j + 1 >= len(lines):
                break
            j += 1
            body = "\n" + lines[j]
        table = "".join(chunk)
        # ключи верхнего уровня
        d = 0
        keys = []
        token = ""
        for k, ch in enumerate(table):
            if ch in "{(":
                d += 1
            elif ch in "})":
                d -= 1
            if d == 1:
                mm = re.match(r"[{,\s]\s*([A-Za-z_]\w*)\s*=[^=]", table[k:k + 60])
                if mm and (k == 0 or table[k] in "{,\n\t "):
                    keys.append(mm.group(1))
        mem = members(cls)
        for key in set(keys):
            stats["tablekey"] += 1
            if key not in mem and key != "Parent":
                issues.append(f"{rel}:{i + 1}: {cls} {{ {key} = … }}: у {cls} нет свойства {key}")

print("проверено:", dict(stats))
print("классов в API:", len(classes), "перечислений:", len(enums))
seen = set()
for x in issues:
    if x not in seen:
        seen.add(x)
        print(x)
print("замечаний:", len(seen))
