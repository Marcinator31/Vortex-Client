# Weitere Fassungen: Minecraft 26.1.1, 26.1.2 und 1.21.11

Der 26.2-Quelltext in `src/` ist die **einzige** Quelle. Die anderen Fassungen
werden bei jedem Build daraus erzeugt (`tools/port.py <version> build-port/<version>`)
und im selben Release mit veroeffentlicht. Neue Features landen also
automatisch in allen Fassungen.

Was sich zwischen den Versionen unterscheidet, steht an drei Stellen:

1. `port/<version>/replace.tsv` -- Umbenennungen (Regex, Tab, Ersatz, optional
   Dateifilter), z. B. `GuiGraphicsExtractor` -> `GuiGraphics` (1.21.11),
   `mc.gui.screen()` -> `mc.screen` (26.1.1 und 1.21.11), Fabric-API-Namen.
2. Bedingte Bloecke direkt im Code, wenn eine Stelle anders gebaut werden muss:

       //#if 26                 <- gilt fuer 26.2 UND 26.1.1 (echter Code)
       code fuer 26.x
       //#else
       //$ code fuer 1.21.11 (auskommentiert, damit 26.2 normal baut)
       //#endif

       //#if 26.2               <- nur 26.2; 26.1.1 und 1.21.11 nehmen den else-Zweig
       ...
       //#elif 26.1             <- optional: eigener Zweig nur fuer 26.1.x
       //$ ...
       //#else
       //$ ...
       //#endif

   Der erste Zweig muss 26.2 einschliessen. Es gilt der erste passende Zweig.
3. `port/<version>/overlay/` -- ganze Dateien, die es nur dort gibt.

`port/26.1.2/basis` enthaelt "26.1.1": 26.1.2 uebernimmt alle Regeln von
26.1.1 und hat nur eigene gradle.properties. `{ziel}` in replace.tsv wird
durch die Zielversion ersetzt (z. B. fuer "minecraft" in fabric.mod.json).

Beim Aendern von Code: laeuft ein Port-Build im CI nicht durch, stehen die
Fehler als Anmerkungen mit dem Praefix `[26.1.1]`, `[26.1.2]` bzw. `[1.21.11]` am Lauf.
Die 26.2-Jar wird trotzdem veroeffentlicht.

26.1.1 und 26.1.2 sind wie 26.2 unverschleiert (gleiche Namen zur Laufzeit), Java 25.

1.21.11 ist zur Laufzeit verschleiert: Reflection ueber Mojang-Namen
(`getDeclaredField("allMessages")`) oder Klassennamen (`getSimpleName()`)
funktioniert dort NICHT -- ueber Typen suchen oder `instanceof` benutzen.
