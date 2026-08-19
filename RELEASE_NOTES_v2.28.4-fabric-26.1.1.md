# Vortex Client 2.28.4 — Fabric 26.1.1 Mojang-Mixin-Fix

Dieser Patch behebt den Startabbruch von Vortex Client 2.28.3 für Minecraft 26.1.1.

## Behoben

Die alte Entity-ESP-Mixin verwendete noch den Yarn-Intermediarynamen `method_5851`. Minecraft 26.1.1 verwendet direkte Mojang-Namen, sodass der Client beim Laden von `Entity` mit einer `InvalidInjectionException` abstürzte.

Die startkritischen Mixins wurden für die Mojang-26.1.1-API aktualisiert. Dies umfasst Entity ESP, Freecam, Crosshair-Targeting, Renderer, Zoom, Nebel, Chat, Statusverarbeitung und LowFire.

## Installation

Entferne `vortexclient-fabric-26.1.1-2.28.3+26.1.1.jar` aus dem `mods`-Ordner und ersetze sie durch diese JAR. Es darf nur eine Vortex-26.1.1-JAR im Ordner liegen.
