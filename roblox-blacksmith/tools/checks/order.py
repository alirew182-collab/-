import re, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from blocks import strip  # reuse comment/string stripper
root = pathlib.Path(sys.argv[1])
bad=0
for f in sorted(root.rglob('*.luau')):
    s = strip(f.read_text(encoding='utf-8'))
    lines = s.split('\n')
    # top-level local declarations (no indentation)
    decls = {}
    for i,l in enumerate(lines):
        m = re.match(r'^local function ([A-Za-z_]\w*)', l) or re.match(r'^local ([A-Za-z_]\w*)\s*[:=]', l)
        if m and m.group(1) not in decls: decls[m.group(1)] = i
        m2 = re.match(r'^local ([A-Za-z_]\w*(?:\s*,\s*[A-Za-z_]\w*)+)', l)
        if m2:
            for n in re.split(r'\s*,\s*', m2.group(1)):
                decls.setdefault(n, i)
    for name, line in decls.items():
        for i in range(line):
            if re.match(r'^\s*(export\s+)?type\s', lines[i]):
                continue  # объявление типа с тем же именем — не использование
            for m in re.finditer(r'(?<![\w.:])'+re.escape(name)+r'(?!\w)', lines[i]):
                # ignore table field keys like  name = ...  inside constructors, and type annotations
                after = lines[i][m.end():m.end()+3]
                before = lines[i][:m.start()]
                if re.match(r'\s*=[^=]', after) and before.strip().endswith((',','{')) : continue
                if before.rstrip().endswith(':'): continue
                print(f'{f.relative_to(root)}:{i+1}: "{name}" used before declaration (line {line+1})'); bad+=1
print('issues:', bad)
