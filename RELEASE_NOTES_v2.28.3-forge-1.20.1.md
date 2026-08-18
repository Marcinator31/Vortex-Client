# Vortex Client 2.28.3 — Forge 1.20.1 Refmap- und Accessor-Fix

Dieser Patch behebt den Startabbruch von Vortex Client 2.28.2 für Forge 1.20.1.

## Behoben

Die Forge-Mixin-Konfiguration deklarierte die beim Build erzeugte `vortexclient.refmap.json` nicht. Deshalb konnten die mit offiziellen Mojang-Namen geschriebenen Accessor-Ziele zur Laufzeit nicht auf die 1.20.1-SRG-Felder remappt werden. Der Start brach bei `MinecraftClientAccessor` ab, weil das Laufzeitfeld für den Benutzer nicht unter dem Quellnamen `user` gefunden wurde.

Die Refmap ist nun im Release-JAR eingebettet und in der Mixin-Konfiguration deklariert. Damit werden Accountwechsel, Rendererzugriff, Freecam-Crosshair sowie AutoHit- und Makro-Invoker korrekt auf die Forge-1.20.1-Laufzeitnamen abgebildet.

## Installation

Entferne `vortexclient-2.28.2+forge-1.20.1.jar` aus dem `mods`-Ordner und ersetze sie durch diese JAR. Es darf nur eine Vortex-Forge-1.20.1-JAR im Ordner liegen.

Benötigt werden Minecraft **1.20.1**, Forge **47.x** und Java **17**.
