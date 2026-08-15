"""
Prueft, ob die Parameter jeder Mixin-Injektion zur Zielmethode passen.

Genau dieser Fehler hat das Spiel beim Start abstuerzen lassen: Ein @Inject
mit falscher Parameterliste wird nicht etwa uebersprungen, sondern bricht die
Mixin-Umwandlung ab -- auch mit require = 0. Der Java-Parser sieht davon
nichts, die Datei ist ja syntaktisch einwandfrei.
"""
import re, os, glob, sys

M = '/home/claude/yarn1211/yarn-1.21.11/mappings'

def descriptor(mid):
    for root, _, fs in os.walk(M):
        for fn in fs:
            if not fn.endswith('.mapping'):
                continue
            for line in open(os.path.join(root, fn)):
                if f"METHOD {mid} " in line:
                    return line.strip().split()[-1]
    return None

def count_params(desc):
    inner = desc[desc.index('(') + 1:desc.index(')')]
    return len(re.findall(r'L[^;]+;|\[+[A-Z]|[BCDFIJSZ]', inner))

problems = []
for p in sorted(glob.glob('src/client/java/com/vortex/client/mixin/**/*.java', recursive=True)):
    src = open(p).read()
    name = p.split('/')[-1]

    # Nur @Inject -- ModifyVariable und ModifyConstant folgen anderen Regeln.
    for m in re.finditer(r'@Inject\([^)]*method\s*=\s*"(method_\d+)"[^)]*\)\s*'
                         r'private[\w<>\s$]*\(([\s\S]*?)\)\s*\{', src):
        mid, args = m.group(1), m.group(2)
        d = descriptor(mid)
        if d is None:
            problems.append((name, mid, "in den Mappings nicht gefunden"))
            continue
        expected = count_params(d)
        params = [x for x in args.split(',') if x.strip()]
        actual = len(params) - 1          # ohne CallbackInfo
        if actual != expected:
            problems.append((name, mid, f"{actual} Parameter im Code, {expected} erwartet"))

for name, mid, txt in problems:
    print(f"  {name}: {mid} -> {txt}")
print(f"  {'✓ alle Injektionen passen' if not problems else str(len(problems)) + ' Abweichungen'}")
sys.exit(1 if problems else 0)
