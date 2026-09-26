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
    for m in re.finditer(r'for \(int i = 0; i < w; i\+\+\) \{\s*\n\s*ctx\.fill\(x \+ i', code):
        z = code[:m.start()].count('\n') + 1
        fehler.append(f'{f.split("/")[-1]}:{z}: pixelweiser Verlauf')
print(f'  {len(glob.glob("src/**/*.java", recursive=True))} Dateien geprueft')
for x in fehler: print('  FEHLER:', x)
sys.exit(1 if fehler else 0)
