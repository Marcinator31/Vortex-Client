# PvP Client (Fabric, Minecraft 1.21.11)

Ein PvP-fokussierter Fabric-Client als Lernprojekt. Aufgebaut wie ein
echtes Mod-Projekt, das du per GitHub Actions zu einer `.jar` baust --
genau wie dein Plugin, nur mit Fabric statt Bukkit/Spigot.

## Was schon drin ist

- **CPS-Counter** als HUD-Overlay (oben links). Das ist deine **Lern-Vorlage**.
  Sie zeigt die zwei Werkzeuge, die du fuer alles Weitere brauchst:
  - ein **Mixin** (`MouseMixin`), das Mausklicks abfaengt
  - ein **Fabric-Event** (`HudRenderer`), das aufs HUD zeichnet
- **GitHub-Actions-Build** (`.github/workflows/build.yml`) -> spuckt die
  `.jar` im Actions-Tab als Artifact aus.
- **Performance-Mods** (Sodium, Lithium) als `modRuntimeOnly` -- laufen im
  Dev-Client mit.

## Zum FPS-Boost -- wichtig und ehrlich

Du schreibst den FPS-Boost **nicht selbst**. Niemand in dieser Liga tut das,
auch Lunar und Ogulniega nicht. Deren "Boost" ist zum allergroessten Teil
**Sodium + Lithium + FerriteCore + Entity Culling** usw., zusammengepackt
unter einem eigenen Namen.

Dein Client macht es genauso, nur transparent:
- Im Dev-Client laufen Sodium/Lithium ueber `modRuntimeOnly` mit.
- Spieler legen dieselben Mods spaeter einfach in ihren `mods`-Ordner.

Eine eigene Render-Engine zu schreiben, die Sodium schlaegt, waere ein
Mehrjahres-Projekt eines bezahlten Teams. Das ist nicht der Weg.

## Bauen

### Lokal
```
./gradlew build
```
Die fertige Mod liegt dann in `build/libs/` (die Datei **ohne** `-sources`).

### Im Dev-Client testen
```
./gradlew runClient
```

### Per GitHub Actions
Einfach pushen. Im Tab **Actions** -> Workflow-Run -> unten bei **Artifacts**
liegt `pvpclient-jar`.

## Versionen (verifiziert fuer 1.21.11)

- Minecraft **1.21.11** (braucht **Java 21**)
- Fabric Loader **0.18.1**, Loom **1.14**
- **Mojang Mappings** statt Yarn -- ab 1.21.11 der empfohlene, zukunftssichere
  Weg. Yarn wird danach nicht mehr aktualisiert.

> Hinweis: Die exakten Build-Nummern von Fabric API, Sodium und Lithium fuer
> 1.21.11 aenderst du beim ersten Build ggf. in `gradle.properties` /
> `build.gradle` auf die neuesten Werte von Modrinth. Steht dort auch als
> Kommentar.

## Naechste Schritte (deine eigentlichen Ziele)

1. **ArmorHUD** -- gleiches Geruest wie der CPS-Renderer, aber statt Text
   zeichnest du `context.drawItem(...)` fuer jedes Ruestungsteil aus
   `client.player.getInventory()`. Dazu die Haltbarkeit als Balken/Zahl.
2. **Keystrokes** -- WASD/Maus-Tasten als Kaesten, die aufleuchten.
   Tastenstatus kommt aus `client.options`-Keybinds.
3. **FPS / Ping / Potion-HUD** -- weitere HudRenderer-Module.
4. **Account-Switcher** -- eigener Screen + Microsoft-OAuth (MSA-Flow).
   Das ist der komplexeste Brocken, machen wir separat und sorgfaeltig.
5. **Settings-GUI** -- Module an/aus, Positionen verschieben.

## Projektstruktur

```
src/
  main/java/...            common-Einstiegspunkt (wenig)
  main/resources/          fabric.mod.json (= dein "plugin.yml")
  client/java/...          der ganze Client-Code (HUD, Mixins)
  client/resources/        Mixin-Config
.github/workflows/build.yml  der Build, wie bei deinem Plugin
```

## Das Modul-System (Stand jetzt)

Das ist das Fundament fuer "alles customizen wie bei Lunar".

Bedienung im Spiel:
- **Rechte Umschalttaste** -> oeffnet das Mods-Menue (ClickGUI)
- **Linksklick** auf ein Modul -> an/aus
- **Rechtsklick** auf ein Modul -> Einstellungen auf-/zuklappen
- **O** -> Account-Switcher

Architektur (wichtig zu verstehen):
- `module/Module.java` -- Basis fuer JEDES Feature (Name, Kategorie,
  an/aus, Settings-Liste).
- `core/setting/` -- Setting-Typen: Boolean (Schalter), Number (Slider),
  Color (Farbwaehler-Grundlage).
- `module/ModuleManager.java` -- hier registrierst du neue Features.
- `gui/ClickGui.java` -- das Menue. Kennt KEIN einzelnes Feature, baut
  sich aus der Modul-Liste auf. Neues Modul -> erscheint automatisch.
- `gui/Theme.java` -- alle GUI-Farben zentral, anpassbar.

Ein neues Feature hinzufuegen = 1) Module-Unterklasse in
`module/modules/` schreiben, 2) in ModuleManager registrieren. Fertig --
es taucht im Menue auf, ist an/aus-schaltbar und speicherbar.

### Was NICHT eingebaut wird
Cheat-Features (Killaura, Reach-Erweiterung, Auto-Clicker, Anti-KB,
Velocity ...). Die verschaffen unfairen Vorteil gegen echte Spieler und
fliegen von jedem Server. Komfort/Anzeige (ArmorHUD, Keystrokes, FPS,
Toggle-Sprint) ist drin -- das ist das, was Lunar auch wirklich macht.

### Noch offen (Ausbau)
- Settings persistent speichern/laden (Setting hat schon serialize()).
- Echter Farbwaehler-Screen fuer ColorSettings.
- Slider-Widget statt Klick-zum-Erhoehen.
- Drag&Drop, um HUD-Elemente mit der Maus zu positionieren.
