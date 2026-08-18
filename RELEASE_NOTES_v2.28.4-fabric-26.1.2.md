# Vortex Client 2.28.4 — Fabric 26.1.2 Mojang-Mixin-Fix

Dieser Patch behebt den Startabbruch von Vortex Client 2.28.3 für Minecraft 26.1.2.

## Behoben

Minecraft 26.1.2 verwendet nicht mehr die früheren Yarn-Intermediarynamen. Die alte Entity-ESP-Mixin suchte deshalb nach `method_5851` und brach beim Laden von `Entity` mit einer `InvalidInjectionException` ab.

Die startkritischen Mixins wurden auf die Mojang-kompatiblen 26.1.2-Ziele aktualisiert. Neben Entity ESP umfasst dies unter anderem Freecam, Crosshair-Targeting, Entity-Rendering, Hand-Rendering, Zoom, Chat, AutoReconnect, Nebel-Rendering und Statusverarbeitung.

## Installation

Entferne `vortexclient-fabric-26.1.2-2.28.3+26.1.2.jar` aus dem `mods`-Ordner und ersetze sie durch diese JAR. Es darf nur eine Vortex-26.1.2-JAR im Ordner liegen.

Benötigt werden Minecraft **26.1.2**, Fabric Loader **0.19.3**, Fabric API **0.155.2+26.1.2** und Java **25**.
