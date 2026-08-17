# Vortex Client – Legacy Fabric 1.13.2

Dieser Branch enthält eine **build-verifizierte erste Portierungsbasis** für Minecraft **1.13.2** mit Legacy Fabric. Die moderne Fabric-1.21.11-Quelle bleibt unverändert unter `modern-fabric-1.21.11-reference/` erhalten. Sie dient als nachvollziehbare Referenz, während die aktiven 1.13.2-Quellen bewusst unter `src/main/` liegen und mit Java 8 kompiliert werden.

## Bereits migrierter Funktionskern

| Bereich | Status | Umsetzung |
|---|---:|---|
| Loader und Artefakt | Fertig | Legacy Fabric, Minecraft 1.13.2, Java 8, remappte JAR |
| Client-Lebenszyklus | Fertig | Mixin an `MinecraftClient.tick()` |
| HUD | Fertig | Vortex-Titel, FPS, Spielerkoordinaten und Modusstatus |
| HUD-Umschaltung | Fertig | Vanilla-KeyBinding, Standardtaste `F6` |
| Fullbright | Fertig | 1.13.2-`GameOptions.Option.BRIGHTNESS`, Standardtaste `F7` |
| ToggleSprint | Fertig | Tick-basierte Sprintaktivierung, Standardtaste `F8` |
| Tasten-Konfiguration | Fertig | Alle drei Tasten erscheinen in der Minecraft-Steuerungskategorie `Vortex Client` |

## Bewusst noch nicht migrierte Teile

Die ursprüngliche Codebasis enthält mehr als 150 Java-Dateien, viele Renderpfade, Mixins und Minecraft-1.21.11-spezifische APIs. Diese können nicht durch ein bloßes Ändern der Build-Version auf 1.13.2 übertragen werden. Nicht enthalten sind daher derzeit insbesondere das ClickGUI/HUD-Editor-System, die Persistenz-Presets sowie die übrigen Render-, Welt-, Netzwerk- und Modulfunktionen.

> Dieser Branch ist kein als vollständig ausgegebener Funktionsport. Er ist eine **kompilierbare, ausführbare und klar abgegrenzte Portierungsbasis**. Jede weitere Funktion wird anhand der Referenzquelle gegen die 1.13.2-Mappings und die historischen Mixin-Signaturen einzeln migriert und erneut gebaut.

## Build

```bash
./gradlew clean build --no-daemon
```

Der Build wurde lokal erfolgreich durchgeführt. Das erzeugte Client-Artefakt lautet:

```text
build/libs/vortexclient-legacy-fabric-1.13.2-2.28.2-legacy.1.jar
```

## Technische Eckdaten

| Eigenschaft | Wert |
|---|---|
| Minecraft | `1.13.2` |
| Loader | Legacy Fabric Loader `0.19.3` |
| Mappings | Legacy Yarn `1.13.2+build.604` |
| Toolchain | Legacy Looming `1.15.3`, Fabric Loom `1.15.5` |
| Sprachziel | Java 8 |

