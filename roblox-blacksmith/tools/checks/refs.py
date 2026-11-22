import re, pathlib, sys
root = pathlib.Path(sys.argv[1])
src = {f: f.read_text(encoding='utf-8') for f in root.rglob('*.luau')}
strings = (root/'shared/Localization/Strings.luau').read_text(encoding='utf-8')
en_part, ru_part = strings.split('local ru:')
en = set(re.findall(r'^\t([A-Z][A-Z0-9_]+) = ', en_part, re.M))
ru = set(re.findall(r'^\t([A-Z][A-Z0-9_]+) = ', ru_part, re.M))
print('en keys', len(en), 'ru keys', len(ru))
print('missing in ru:', sorted(en-ru)); print('extra in ru:', sorted(ru-en))
# keys used
used=set()
for f,s in src.items():
    if 'Strings.luau' in str(f): continue
    for m in re.finditer(r'"(@?)((?:UI|ERR|SYS|TOAST|FORGE|Q|QUEST|PROMPT|FX|ORE|MAT|WPN|PICK|RARITY|MOB|GATE|WEAPON)_[A-Z0-9_]+)"', s):
        used.add(m.group(2))
# config NameKey / TitleKey
cfg=set(re.findall(r'(?:NameKey|TitleKey) = "([A-Z0-9_]+)"', ''.join(src.values())))
used|=cfg
# dynamic SYS_KIND_ from kinds
kinds=set(re.findall(r'^\t([A-Za-z]+) = \{ Color =', src[root/'client/Controllers/SystemWindowController.luau'], re.M))
used|={'SYS_KIND_'+k.upper() for k in kinds}
used|={'WPN_'+w.upper() for w in ['Sword','Axe','Spear']}|{'MAT_'+o.upper() for o in ['Copper','Iron','Moonsilver']}
print('used but missing in en:', sorted(k for k in used-en if not k.endswith('_')))
print('unused en keys:', sorted(en-used))
# Net names
net = src[root/'shared/Net.luau']
names=set(re.findall(r'^\t([A-Za-z]+) = true', net, re.M))
usedNet=set()
for f,s in src.items():
    for m in re.finditer(r'(?:Net\.(?:Event|Function|UnreliableEvent)|OnEvent|OnInvoke|:Invoke)\("([A-Za-z]+)"', s): usedNet.add(m.group(1))
    for m in re.finditer(r':(?:Fire|FireAll|FireList)\([^"\n]*"([A-Za-z]+)"', s): usedNet.add(m.group(1))
print('net used but undefined:', sorted(usedNet-names)); print('net defined unused:', sorted(names-usedNet))
# require paths script.Parent...X
for f,s in src.items():
    for m in re.finditer(r'require\((script(?:\.Parent)*(?:\.[A-Za-z]+)*)', s):
        path=m.group(1).split('.')
        # resolve: init.* files: script refers to folder
        cur = f.parent if f.stem in ('init','init.server','init.client') else f
        node = cur
        for p in path[1:]:
            if p=='Parent':
                node = node.parent
                continue
            cands=[node/(p+'.luau'), node/p]
            hit=[c for c in cands if c.exists()]
            if not hit: print('BAD require', f.relative_to(root), m.group(1)); break
            node = hit[0] if hit[0].is_dir() else hit[0]
            if node.suffix=='.luau': node=node  # module file; further children not allowed
# ReplicatedStorage.Shared.X
for f,s in src.items():
    for m in re.finditer(r'ReplicatedStorage\.Shared\.([A-Za-z]+)', s):
        p=root/'shared'/m.group(1)
        if not (p.with_suffix('.luau').exists() or p.is_dir()): print('BAD shared', f.relative_to(root), m.group(1))
