# Vortex Client – Rift / RiftLoader 1.13.2

Dieser Branch enthält eine **build-verifizierte Adapterbasis** für den von dir bereitgestellten RiftLoader-Quellstand auf Minecraft **1.13.2**. Anders als Fabric startet Rift Mods über `riftmod.json` und Listener-Klassen. Der Vortex-Adapter implementiert deshalb `MinecraftStartListener` und wird ausschließlich auf der Client-Seite geladen.

## Fertiggestellt

| Bereich | Status | Umsetzung |
|---|---:|---|
| Rift/RiftLoader-Startpunkt | Fertig | `VortexRiftClient` implementiert `MinecraftStartListener` |
| Clientseitige Metadaten | Fertig | `riftmod.json` mit Listener `side: CLIENT` |
| Artefakt- und Paketkennung | Fertig | `com.vortex`, `vortexclient-rift-1.13.2` |
| Java-Sprachziel | Fertig | Java 8 |
| Historische Toolchain | Fertig | Rift ForgeGradle 2.4 mit `RiftLoaderClientTweaker` |
| Lokaler Build | Erfolgreich | `./gradlew clean build --no-daemon` mit Java 8 |

## Wichtiger Hinweis zur Rift-API

Das historische Maven-Repository für das originale Rift-Entwicklungsartefakt ist nicht mehr per DNS erreichbar. Damit der Branch weiterhin reproduzierbar kompiliert, liegt unter `libs/rift-api-1.13.2-compileonly.jar` eine **kleine Compile-only-Schnittstelle** für `MinecraftStartListener`. Sie wurde aus dem von dir bereitgestellten RiftLoader-1.13.2-Quellarchiv abgeleitet und wird nicht in die Vortex-Mod-JAR eingebettet. Beim Start stellt RiftLoader die echte Schnittstelle bereit.

## Noch ausstehende Funktionsmigration

Die Fabric-1.21.11-Quelle ist als unveränderte Referenz unter `modern-fabric-1.21.11-reference/` abgelegt. Rift bietet in der bereitgestellten API keinen allgemeinen clientseitigen Tick- oder HUD-Listener. Für jede weitere Vortex-Funktion müssen daher passende Minecraft-1.13.2-Mixin-Hooks in Verbindung mit dem Rift-Startprofil implementiert und gegen die historischen MCP-Mappings getestet werden. Der Branch behauptet ausdrücklich **keinen vollständigen Funktionsport**.

## Build

Die historische Buildkette benötigt Java 8:

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build --no-daemon
```

Die Artefakte werden in `build/libs/` erzeugt.

## Technische Eckdaten

| Eigenschaft | Wert |
|---|---|
| Minecraft | `1.13.2` |
| Loader | Rift / RiftLoader (TweakClass `RiftLoaderClientTweaker`) |
| Mappings | MCP Snapshot `20181130` |
| Toolchain | Rift ForgeGradle 2.4 Snapshot |
| Sprachziel | Java 8 |
