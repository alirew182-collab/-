import re, pathlib, sys
root = pathlib.Path(sys.argv[1])
graph = {}
files = {f.stem: f for f in root.rglob('*.luau')}
for f in root.rglob('*.luau'):
    s = f.read_text(encoding='utf-8')
    deps = set()
    for m in re.finditer(r'require\(script\.Parent\.([A-Za-z]+)\)', s): deps.add(m.group(1))
    for m in re.finditer(r'require\(script\.Parent\.Parent\.(?:[A-Za-z]+)\.([A-Za-z]+)\)', s): deps.add(m.group(1))
    for m in re.finditer(r'require\(ReplicatedStorage\.Shared\.([A-Za-z]+)\)', s): deps.add(m.group(1))
    graph[f.stem] = deps
seen, stack, cycles = set(), [], []
def dfs(n):
    if n in stack:
        cycles.append(stack[stack.index(n):]+[n]); return
    if n in seen: return
    seen.add(n); stack.append(n)
    for d in graph.get(n, ()): dfs(d)
    stack.pop()
for n in graph: dfs(n)
print('cycles:', cycles or 'none')
