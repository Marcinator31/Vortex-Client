# Vortex Client – Ornithe 1.14.4

Dieser Branch enthält eine **build-verifizierte Portierungsbasis** für Ornithe auf Minecraft **1.14.4**. Der Zielzweig verwendet Ornithe Gen2, Feather-Mappings und die OSL-Entrypoint-API. Die moderne Fabric-1.21.11-Quelle bleibt getrennt unter `modern-fabric-1.21.11-reference/` erhalten.

## Fertiggestellt

| Bereich | Status | Umsetzung |
|---|---:|---|
| OSL-Lebenszyklus | Fertig | `VortexOrnitheClient` implementiert `net.ornithemc.osl.entrypoints.api.ModInitializer` |
| Clientseitige Metadaten | Fertig | `fabric.mod.json` mit OSL-Entrypoint `init` |
| Build-Konfiguration | Fertig | Ornithe Gen2, Feather und OSL für Minecraft 1.14.4 |
| Java-Sprachziel | Fertig | Java 8-Bytecode |
| Lokaler Build | Erfolgreich | `./gradlew clean build --no-daemon` mit Java 21 |

## Verifizierte Abhängigkeiten

| Eigenschaft | Wert |
|---|---|
| Minecraft | `1.14.4` |
| Loader | Fabric Loader `0.19.3` in Ornithe |
| Mappings | Feather Gen2 `1.14.4+build.1` |
| OSL | Gen2 `0.21.0-alpha.34` |
| Plugins | Fabric Loom `1.17.19`, Ploceus `1.17.7` |

Für 1.14.4 sind in den Ornithe-Metadaten keine Raven-, Sparrow- oder Nests-Releases verzeichnet. Der Branch verwendet deshalb nur die tatsächlich verfügbaren Feather- und OSL-Komponenten.

## Noch ausstehende Funktionsmigration

Die aktive Quelle ist keine vollständige Umschreibung des 1.21.11-Clients. ClickGUI, Persistenz-Presets, HUD-Editor und die restlichen Modul-, Render-, Netzwerk- und Mixin-Funktionen müssen einzeln auf die 1.14.4-API und die Ornithe-Mappings übertragen werden. Der Branch ist als **ausführbare, kompilierte Ausgangsbasis** konzipiert und behauptet keinen vollständigen Feature-Port.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build --no-daemon
```

Das Client-Artefakt lautet:

```text
build/libs/vortexclient-ornithe-1.14.4-2.28.2-ornithe.1+mc1.14.4.jar
```
