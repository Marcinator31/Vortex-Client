# Vortex Client – LiteLoader 1.12.2

Dieser Branch enthält den **Loader- und Metadatenadapter** für LiteLoader auf Minecraft **1.12.2**. LiteLoader ist kein Fabric-Derivat: Mods verwenden `litemod.json` und implementieren `com.mumfrey.liteloader.LiteMod` statt Fabric-Entrypoints und Fabric-Mixins.

## Fertiggestellt

| Bereich | Status | Umsetzung |
|---|---:|---|
| LiteLoader-Lebenszyklus | Fertig | `VortexLiteMod` implementiert `LiteMod` |
| Java-Sprachziel | Fertig | Nur Java-8-Sprachelemente |
| Modmetadaten | Fertig | `src/main/resources/litemod.json` für Minecraft 1.12.2 |
| Referenzquellen | Fertig | Moderne Fabric-1.21.11-Quelle unter `modern-fabric-1.21.11-reference/` |

## Buildstatus

Für LiteLoader 1.12.2 ist auf dem offiziellen Entwicklungsweg eine historische SDK-/Workspace-Installation erforderlich. Anders als bei Legacy Fabric, Babric, Rift und Ornithe steht für diesen Zielzweig in der vorliegenden Toolchain keine reproduzierbar erreichbare, aktuelle Gradle-Vorlage mit dem benötigten LiteLoader-API-Artefakt bereit. Deshalb wurde für diesen Branch **kein fälschlich erfolgreicher Build behauptet**.

Der nächste technische Schritt ist die Einbindung einer konkreten LiteLoader-1.12.2-SDK-Distribution in den Branch. Danach können der Einstiegspunkt und jede portierte Funktion gegen die echten 1.12.2-Klassen kompiliert werden.

## Noch ausstehende Funktionsmigration

Die moderne Fabric-1.21.11-Quelle verwendet 44 Fabric-Imports, 36 Mixins und zahlreiche APIs, die es in LiteLoader nicht in derselben Form gibt. Neben dem ClickGUI, der Konfiguration und dem HUD müssen insbesondere Event-, Eingabe-, Render-, Netzwerk- und Mixin-Hooks individuell auf die LiteLoader/LaunchWrapper-Architektur übertragen werden.

> Dieser Branch ist eine **korrekte LiteLoader-Portierungsbasis**, aber ausdrücklich kein vollständiger Funktionsport und keine gebaute JAR.

## Technische Eckdaten

| Eigenschaft | Wert |
|---|---|
| Minecraft | `1.12.2` |
| Loader | LiteLoader |
| Metadaten | `litemod.json` |
| Einstiegspunkt | `com.mumfrey.liteloader.LiteMod` |
| Sprachziel | Java 8 |
