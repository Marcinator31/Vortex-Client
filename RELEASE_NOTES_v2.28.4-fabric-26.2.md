# Vortex Client 2.28.4 — Fabric 26.2

Dieser Patch behebt den bestätigten Startabbruch von **Vortex Client 2.28.3 für Minecraft 26.2**.

## Behoben

Der Fehler entstand durch verbliebene Yarn-Intermediary-Ziele in mehreren Mixins. Minecraft 26.2 ist nicht mehr obfuskiert und verwendet Mojang-Namen direkt. Dadurch konnte Fabric beim Laden von `EntityEspMixin` das alte Ziel `method_5851` nicht finden und brach mit einer `InvalidInjectionException` ab.

Die startkritischen Mixins wurden auf die Mojang-kompatiblen 26.2-Ziele umgestellt. Dies betrifft unter anderem Entity ESP, Freecam, Crosshair-Targeting, Hand-Rendering, Entity-Rendering, Zoom, Chat, AutoReconnect, Totem-/Statusverarbeitung und weitere Client-Hooks.

## Installation

Entferne die ältere Datei `vortexclient-fabric-26.2-2.28.3+26.2.jar` aus dem `mods`-Ordner und ersetze sie durch diese JAR. Benötigt werden Minecraft **26.2**, Fabric Loader **0.19.3** und Fabric API **0.157.0+26.2**.

Sodium und Lithium bleiben optional; ihre fehlende Installation verursacht diesen Startabbruch nicht.
