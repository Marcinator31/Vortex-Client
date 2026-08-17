# Vortex Client – Babric Beta 1.7.3

Dieser Branch enthält eine **build-verifizierte Portierungsbasis** für Babric auf Minecraft **Beta 1.7.3**. Babric verwendet zwar Fabric-artige Modmetadaten und `ModInitializer`, die Minecraft-Beta-Codebasis unterscheidet sich jedoch grundlegend von der modernen Fabric-1.21.11-Quelle.

## Fertiggestellt

| Bereich | Status | Umsetzung |
|---|---:|---|
| Loader-Einstiegspunkt | Fertig | `VortexBabricClient` implementiert `ModInitializer` |
| Clientseitige Modmetadaten | Fertig | `fabric.mod.json` für Babric Beta 1.7.3 |
| Build-Konfiguration | Fertig | Babric Loom, Barn-Mappings und Babric Loader aus der offiziellen Vorlage |
| Java-Sprachziel | Fertig | Java 8-Bytecode |
| Lokaler Build | Erfolgreich | `./gradlew clean build --no-daemon` mit Java 21 |

## Noch ausstehende Funktionsmigration

Der aktive Branch enthält absichtlich noch keinen als vollständig ausgegebenen Client. Das moderne Vortex-System unter `modern-fabric-1.21.11-reference/` setzt unter anderem auf moderne HUD-, Render-, Entity-, Netzwerk- und Mixin-Schnittstellen. Diese APIs existieren in Beta 1.7.3 nicht in vergleichbarer Form. Jede Funktion benötigt daher eine separate Anpassung an die Beta-Renderer- und Eingabearchitektur sowie einen eigenen Laufzeittest.

> Dieser Branch liefert eine **kompilierbare Babric-Basis** und bewahrt die moderne Quelle als Referenz. Er ist kein vollständiger Feature-Port.

## Build

Die aktuelle Babric-Loom-Version benötigt Java 17 oder neuer, erzeugt jedoch Java-8-kompatiblen Bytecode:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build --no-daemon
```

Das Client-Artefakt liegt danach unter:

```text
build/libs/vortexclient-babric-beta-1.7.3-2.28.2-babric.1.jar
```

## Technische Eckdaten

| Eigenschaft | Wert |
|---|---|
| Minecraft | `b1.7.3` |
| Loader | Babric Loader `0.15.6-babric.2` |
| Mappings | Barn `b1.7.3+build.8` |
| Build-Plugin | Babric Loom `1.1-SNAPSHOT` |
| Build-JDK | Java 21 |
| Bytecode-Ziel | Java 8 |
