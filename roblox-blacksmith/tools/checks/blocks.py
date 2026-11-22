import re, sys, pathlib
root = pathlib.Path(sys.argv[1]) if __name__ == "__main__" else None
def strip(src):
    out=[];i=0;n=len(src)
    while i<n:
        if src.startswith('--[[',i) or src.startswith('--[=[',i):
            close = ']]' if src.startswith('--[[',i) else ']=]'
            j=src.find(close,i); seg=src[i:j+len(close)]; out.append('\n'*seg.count('\n')); i=j+len(close); continue
        if src.startswith('--',i):
            j=src.find('\n',i); i = n if j<0 else j; continue
        c=src[i]
        if c in '"\'':
            j=i+1
            while src[j]!=c:
                if src[j]=='\\': j+=1
                j+=1
            out.append('""'); i=j+1; continue
        if c=='`':
            j=i+1; depth=0
            while True:
                if src[j]=='\\': j+=2; continue
                if src[j]=='{': depth+=1
                elif src[j]=='}': depth-=1
                elif src[j]=='`' and depth==0: break
                j+=1
            out.append('""'); i=j+1; continue
        if src.startswith('[[',i):
            j=src.find(']]',i); out.append('""'); i=j+2; continue
        out.append(c); i+=1
    return ''.join(out)
bad=0
if __name__ != "__main__": root = pathlib.Path("/nonexistent")
for f in sorted(root.rglob('*.luau')):
    s=strip(f.read_text(encoding='utf-8'))
    depth=0; problems=[]
    for ln,line in enumerate(s.split('\n'),1):
        toks=re.findall(r'[A-Za-z_][A-Za-z0-9_]*',line)
        first=line.strip().split(' ')[0] if line.strip() else ''
        for k,t in enumerate(toks):
            if t=='function' or t=='do' or t=='repeat': depth+=1
            elif t=='if' and k==0 and first.startswith('if'): depth+=1
            elif t=='end' or t=='until': depth-=1
            if depth<0: problems.append(f'line {ln}: depth<0'); depth=0
    if depth!=0: problems.append(f'unbalanced depth {depth}')
    # parens/braces balance
    for a,b in ['()','{}','[]']:
        if s.count(a)!=s.count(b): problems.append(f'{a}{b} mismatch {s.count(a)} vs {s.count(b)}')
    if problems:
        bad+=1; print(f, problems[:5])
print('files with problems:', bad)
