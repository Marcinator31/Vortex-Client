# Vortex Client 2.28.5 — Fabric 26.2 Right-Shift Fix

Dieser Patch behebt eine nicht reagierende **rechte Umschalttaste** in Vortex Client für Minecraft 26.2.

## Behoben

Die ClickGUI war als Fabric-KeyMapping registriert, erhielt unter Minecraft 26.2 jedoch nicht in jeder Eingabereihenfolge ein Ereignis in der KeyMapping-Queue. Der Client prüft die rechte Umschalttaste deshalb zusätzlich einmal pro Client-Tick direkt mit Flankenerkennung. Das verhindert wiederholtes Öffnen bei gehaltenem Schlüssel und öffnet die ClickGUI zuverlässig beim Drücken.

Ein erfolgreicher Tastendruck schreibt zusätzlich die Zeile `ClickGUI opened via Right Shift.` in `latest.log`.

## Installation

Entferne die ältere Datei `vortexclient-fabric-26.2-2.28.4+26.2.jar` aus dem `mods`-Ordner und ersetze sie durch diese JAR. Es darf nur eine Vortex-26.2-JAR im Ordner liegen.

Nach dem Start einer Welt oder eines Servers öffnet **rechte Umschalttaste** die Vortex ClickGUI. Die Tastenzuordnung lässt sich außerdem unter **Optionen → Steuerung → Vortex Client** sehen und bei Bedarf ändern.
