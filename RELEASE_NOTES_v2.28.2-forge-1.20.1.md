# Vortex Client 2.28.2 — Forge 1.20.1

Dies ist der vollständige **Forge-Port für Minecraft 1.20.1**.

Der Client enthält den vollständigen Vortex-Funktionsumfang des Ausgangsstands, einschließlich Community- und Preset-Funktionen, Makros, Wegpunkten, HUD-Elementen, ESP-Renderern, Aimbot, AutoTotem, AutoHit, Freecam, Zoom, Skin- und Account-Funktionen sowie der weiteren Clientmodule.

## Voraussetzungen

| Komponente | Erforderliche Version |
|---|---:|
| Minecraft | 1.20.1 |
| Mod-Loader | Forge 47.4.22 oder kompatibler Forge-47.x-Stand |
| Java | Java 17 |

## Installation

Lade die JAR-Datei dieser Veröffentlichung herunter und lege sie in den Ordner `mods` deiner Forge-1.20.1-Installation. Der Mod ist ausschließlich für den Client bestimmt.

## Technische Hinweise

Der Port verwendet Forge-Events und Forge-1.20.1-Mappings. Die bisherigen Fabric-Callback-Einstiegspunkte werden intern über Forge-kompatible Adapter bereitgestellt. Der Produktionsbuild wurde vollständig erstellt; die Mixin-Ziele wurden gegen den Forge-1.20.1-Classpath aufgelöst.
