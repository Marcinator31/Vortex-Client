# Vortex Client 2.28.6 — Fabric 26.2 FogRenderer Startfix

Dieser Patch behebt den Startabbruch von Vortex Client 2.28.5 für Minecraft 26.2.

## Behoben

Minecraft 26.2 gibt aus `FogRenderer.setupFog` ein `FogData`-Objekt zurück. Die bisherige Nebel-Mixin verwendete noch die alte Rückgabe `Vector4f` und konnte daher kein passendes Ziel finden. Fabric brach beim Initialisieren des `GameRenderer` mit einer `InvalidInjectionException` ab.

Die Mixin verarbeitet nun das von 26.2 zurückgegebene `FogData` direkt nach der Nebelberechnung. No Fog, Clear Water und Clear Lava bleiben dadurch erhalten, ohne den Startpfad zu blockieren.

## Installation

Entferne `vortexclient-fabric-26.2-2.28.5+26.2.jar` aus dem `mods`-Ordner und ersetze sie durch diese JAR. Es darf nur eine Vortex-26.2-JAR im Ordner liegen.
