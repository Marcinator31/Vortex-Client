"""
Findet die beiden Ursachen fuer ruckelnde Oberflaechen, die in diesem
Projekt mehrfach aufgetreten sind.

1. SPRINGENDE ANIMATION: "Math.min(1f, speed * dt)" oder "if (f > 1f) f = 1f".
   Bei niedriger Bildrate wird speed * dt groesser als 1, und die Bewegung
   springt in einem Schritt ans Ziel. Richtig: 1 - exp(-speed * dt).

2. PIXELWEISER VERLAUF: eine Schleife ueber jede Pixelspalte mit je einem
   fill-Aufruf. Im ClickGUI waren das ueber 3000 Aufrufe je Bild -- der
   Grund fuer den 5-FPS-Eindruck beim Scrollen. Richtig: Baender.
"""
import re, glob, sys
fehler = []
for f in glob.glob('src/**/*.java', recursive=True):
    s = open(f, encoding='utf-8').read()
    code = re.sub(r'//[^\n]*', '', s)
    for m in re.finditer(r'Math\.min\(1f,\s*[0-9.]+f?\s*\*\s*dt\)|if \(f > 1f\) f = 1f', code):
        z = code[:m.start()].count('\n') + 1
        fehler.append(f'{f.split("/")[-1]}:{z}: springende Animation')
    # Pixelweise Schleife -- mit beliebigen Variablennamen.
    #
    # Gemeldet wird nur eine for-Schleife in Einerschritten ueber eine
    # BREITE, in der ein fill genau einen Pixel breit ist ("+ i" bis
    # "+ i + 1"). Nicht gemeldet werden:
    #   - einzelne Trennlinien ohne Schleife
    #   - Schleifen ueber den Eckradius (r, RADIUS, kleine Zahlen) -- das
    #     sind drei Spalten fuer die Rundung, nicht ein ganzer Verlauf
    # Die erste Fassung kannte nur "x + i" und uebersah deshalb die
    # Farbleisten im Farbwaehler; die zweite war zu grob und meldete
    # Eckrundungen.
    for m in re.finditer(r'for \(int (\w+) = 0; \1 < (\w+); \1\+\+\) \{', code):
        var, grenze = m.group(1), m.group(2)
        if grenze in ('r', 'R', 'radius', 'RADIUS') or grenze.isdigit():
            continue
        rumpf = code[m.end():m.end() + 400]
        if re.search(r'(?:ctx|context)\.fill\(\s*\w+ \+ ' + var + r',\s*[^,]+,\s*\w+ \+ '
                     + var + r' \+ 1,', rumpf):
            z = code[:m.start()].count('\n') + 1
            fehler.append(f'{f.split("/")[-1]}:{z}: pixelweiser Verlauf')
print(f'  {len(glob.glob("src/**/*.java", recursive=True))} Dateien geprueft')
for x in fehler: print('  FEHLER:', x)
sys.exit(1 if fehler else 0)
