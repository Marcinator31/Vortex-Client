# Vortex Client 2.28.5 — Fabric 26.1.2 LowFire Startfix

Dieser Patch behebt den Startabbruch von Vortex Client 2.28.4 für Minecraft 26.1.2.

## Behoben

Die LowFire-Mixin verwendete den erst in 26.2 vorhandenen Hook `submitFire`. In Minecraft 26.1.2 heißt der tatsächliche Feuer-Renderpfad `renderFire` und verwendet eine `MultiBufferSource`. Die fehlende Zielmethode führte beim Aufbau des GameRenderer zu einer `InvalidInjectionException`.

LowFire wurde auf den korrekten 26.1.2-Renderpfad portiert. Das Modul verschiebt das Feuer-Overlay weiterhin nach unten, ohne den Clientstart zu blockieren.

## Installation

Entferne `vortexclient-fabric-26.1.2-2.28.4+26.1.2.jar` aus dem `mods`-Ordner und ersetze sie durch diese JAR. Es darf nur eine Vortex-26.1.2-JAR im Ordner liegen.
