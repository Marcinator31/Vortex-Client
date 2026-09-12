"""
Sucht oeffentliche statische Schaltmethoden, die niemand aufruft.

Hintergrund: Beim Umzug der Module ins Addon ist der Block verschwunden, der
Freecam.toggle() aufrief. Die Methode blieb stehen, niemand rief sie mehr --
die Freecam liess sich nicht mehr oeffnen. Kein Fehler, keine Meldung.

Geprueft wird KLASSENBEZOGEN (Freecam.toggle), nicht nur ueber den
Methodennamen: 'toggle' kommt im Projekt auch anderswo vor, und eine Zaehlung
allein ueber den Namen haette den Fehler nicht gefunden.
"""
import re, glob, os, sys

INTERESSANT = re.compile(r'^(toggle|enable|disable|activate|deactivate|register|apply|reset)')

alle = ''
methoden = []          # (Klasse, Methode)
for f in glob.glob('src/**/*.java', recursive=True):
    s = open(f, encoding='utf-8').read()
    alle += '\n' + s
    klasse = os.path.basename(f)[:-5]
    for m in re.finditer(r'public\s+static\s+[\w<>\[\],.\s]+?\s+(\w+)\s*\(', s):
        if INTERESSANT.match(m.group(1)):
            methoden.append((klasse, m.group(1)))

# Bekannte, gepruefte Ausnahmen -- mit Begruendung, damit niemand sie blind
# erweitert:
AUSNAHMEN = {
    # Wird innerhalb derselben Klasse aufgerufen (ohne Klassennamen davor).
    'ConfigManager.resetAll',
    # Oeffentliche Schnittstelle fuer Addons: das Vortex-Plus-Addon meldet
    # damit seine Modulbeschreibungen an.
    'ModuleInfo.register',
    # Wird vom Skin-Bildschirm ueber eine Instanzreferenz benutzt.
    'SkinUploader.reset',
}

fehler = []
for klasse, name in sorted(set(methoden)):
    # Klassenbezogener Aufruf: Freecam.toggle(  oder  x.Freecam.toggle(
    if not re.search(r'\b' + klasse + r'\.' + name + r'\s*\(', alle):
        if f'{klasse}.{name}' in AUSNAHMEN: continue
        fehler.append(f'{klasse}.{name}() wird nirgends aufgerufen')

print(f'  {len(set(methoden))} oeffentliche Schaltmethoden geprueft')
for x in fehler: print('  UNBENUTZT:', x)
sys.exit(1 if fehler else 0)
