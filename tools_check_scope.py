"""
Findet Variablen, die benutzt, aber im Gültigkeitsbereich nie angelegt werden.

Bewusst OHNE Java-Parser: javalang scheitert an modernem Java (etwa
"instanceof Foo f") und überspringt solche Dateien stillschweigend -- also
ausgerechnet die, in denen der Fehler zweimal auftrat. Stattdessen werden die
Methodenkörper über Klammernzählung abgegrenzt und der Text darin untersucht.

Gesucht wird der Fall, der zweimal den Build gekostet hat: ein Codeblock, der
per Suchen-und-Ersetzen in der falschen Methode landet und dort eine Variable
benutzt, die es nur in der anderen gibt.
"""
import re, glob, sys

SIG = re.compile(
    r'^[ \t]{1,8}(?:@\w+\s+)*(?:public|private|protected)?\s*(?:static\s+)?'
    r'(?:final\s+)?[\w.<>,\[\]?\s]+?\s+(\w+)\s*\([^;]*?\)\s*(?:throws [\w., ]+)?\{',
    re.M)

def method_bodies(src):
    """(Name, Körper, Parametertext) je Methode, über Klammernzählung."""
    out = []
    KEYWORDS = {'if','for','while','switch','catch','try','synchronized',
                'do','else','return','new','case','default'}
    for m in SIG.finditer(src):
        name = m.group(1)
        # if/for/while sehen wie Methodensignaturen aus, sind aber keine.
        if name in KEYWORDS:
            continue
        i = src.index('{', m.start())
        params = src[m.start():i]
        depth, j = 0, i
        while j < len(src):
            if src[j] == '{': depth += 1
            elif src[j] == '}':
                depth -= 1
                if depth == 0: break
            j += 1
        out.append((name, src[i:j], params))
    return out

def declared_in(body, params_src):
    names = set()
    # Parameter
    names |= set(re.findall(r'[\w.<>\[\]]+\s+(\w+)\s*(?:,|\))', params_src))
    # Lokale Deklarationen: Typ name = ...  /  var name = ...
    names |= set(re.findall(r'(?:^|[;{)\s])(?:final\s+)?(?:var|[\w.]+(?:<[^;=]*?>)?(?:\[\])?)\s+(\w+)\s*[=;]', body))
    # for-each und for(...)
    names |= set(re.findall(r'for\s*\(\s*(?:final\s+)?[^:;()]+?(\w+)\s*[:=]', body))
    # catch
    names |= set(re.findall(r'catch\s*\([^)]*?(\w+)\s*\)', body))
    # instanceof-Muster
    names |= set(re.findall(r'instanceof\s+[\w.<>]+\s+(\w+)', body))
    # try-with-resources
    names |= set(re.findall(r'try\s*\(\s*[\w.<>]+\s+(\w+)\s*=', body))
    # Lambda-Parameter
    names |= set(re.findall(r'(\w+)\s*->', body))
    names |= set(x for pair in re.findall(r'\((\w+)\s*,\s*(\w+)\)\s*->', body) for x in pair)
    return names

PROJECT_FIELDS = set()
for _p in glob.glob('src/**/*.java', recursive=True):
    PROJECT_FIELDS |= set(re.findall(
        r'^\s{1,8}(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?[\w.<>,\[\]?&\s]+?\s(\w+)\s*[=;]',
        open(_p).read(), re.M))

problems = []
for path in sorted(glob.glob('src/**/*.java', recursive=True)):
    src = open(path).read()
    # Felder der Klasse (auch geerbte tolerieren wir projektweit)
    fields = set(re.findall(r'^\s{1,8}(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?[\w.<>,\[\]?&\s]+?\s(\w+)\s*[=;]', src, re.M))
        # Geerbte Felder stehen in einer anderen Datei. Projektweit sammeln:
        # das kann den gesuchten Fehler nicht verdecken, denn dabei geht es
        # immer um LOKALE Variablen, nie um Felder.
    fields |= PROJECT_FIELDS
    for name, body, params in method_bodies(src):
        ok = fields | declared_in(body, params)
        # Benutzungen: variable.methode(
        for use in re.finditer(r'(?<![\w.])([a-z]\w*)\.\w+\s*\(', body):
            v = use.group(1)
            if v in ok: continue
            if v in ('this','super','java','javax','net','com','org','it'): continue
            problems.append((path.split('/')[-1], name, v))

seen=set()
for fn, meth, v in problems:
    k=(fn,meth,v)
    if k in seen: continue
    seen.add(k)
    print(f"  {fn}::{meth}() benutzt '{v}', das dort nicht angelegt wird")
print(f"  {'✓ keine Treffer' if not seen else str(len(seen)) + ' Treffer'}")
sys.exit(1 if seen else 0)
