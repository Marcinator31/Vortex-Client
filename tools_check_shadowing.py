"""
Findet Variablen, die einen Paketnamen verdecken.

Hintergrund: In 3.4.0 hiess eine Variable "java". Weiter unten stand
"java.nio.file.Files" -- und der Compiler las das als Feld "nio" der
Variablen. Build-Fehler, obwohl beide Zeilen fuer sich richtig aussehen.

Geprueft wird: gibt es in einer Datei eine Variable namens java, net, com,
org oder javax, UND wird dort gleichzeitig ein vollqualifizierter Name mit
diesem Anfang benutzt?
"""
import re, glob, sys
PAKETE = ['java', 'javax', 'net', 'com', 'org']
fehler = []
for f in glob.glob('src/**/*.java', recursive=True):
    s = open(f, encoding='utf-8').read()
    code = re.sub(r'/\*[\s\S]*?\*/', '', s)
    code = re.sub(r'//[^\n]*', '', code)
    code = re.sub(r'"(?:\\.|[^"\\])*"', '""', code)
    for p in PAKETE:
        # Deklaration: Typ gefolgt vom Paketnamen als Variablenname
        if re.search(r'\b[A-Z]\w*(?:<[^>]*>)?(?:\[\])?\s+' + p + r'\s*[=;,)]', code):
            if re.search(r'\b' + p + r'\.[a-z]', code):
                fehler.append(f'{f.split("/")[-1]}: Variable "{p}" verdeckt das Paket {p}.*')
print(f'  {len(glob.glob("src/**/*.java", recursive=True))} Dateien geprueft')
for x in fehler: print('  FEHLER:', x)
sys.exit(1 if fehler else 0)
