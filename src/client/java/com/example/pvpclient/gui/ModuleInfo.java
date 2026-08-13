package com.example.pvpclient.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * Kurzbeschreibungen aller Module -- werden im ClickGUI als Hinweis eingeblendet,
 * wenn man mit der Maus auf einer Karte stehen bleibt.
 *
 * Bewusst zentral an einer Stelle statt in jeder der ueber 40 Modul-Klassen: so
 * bleibt der Text an einem Ort pflegbar und die Module bleiben schlank. Fehlt zu
 * einem Modul ein Eintrag, wird einfach kein Hinweis gezeigt.
 */
public final class ModuleInfo {

    private static final Map<String, String> TEXT = new HashMap<>();

    static {
        // --- HUD ---
        put("FPS", "Zeigt die aktuelle Bildrate an.");
        put("Ping", "Zeigt deine Verzoegerung zum Server in Millisekunden.");
        put("CPS", "Zaehlt deine Klicks pro Sekunde.");
        put("Coordinates", "Blendet deine Position und die Blickrichtung ein.");
        put("Potion Effects", "Listet deine aktiven Traenke mit Restdauer auf.");
        put("ArmorHUD", "Zeigt deine Ruestung und deren Haltbarkeit.");
        put("Totem Counter", "Zaehlt, wie viele Totems du noch im Inventar hast.");
        put("Radar", "Kleine Karte mit Spielern in deiner Naehe.");
        put("AppleSkin", "Zeigt Saettigung und wie viel Nahrung ein Essen bringt.");
        put("HUD-Farbe", "Setzt eine gemeinsame Farbe fuer alle HUD-Anzeigen.");
        put("Player List", "Liste der Spieler in der Naehe samt Entfernung.");
        put("Totem Popper", "Zaehlt, wie viele Totems die Spieler um dich herum verbraucht haben.");
        put("Session Stats", "Spielzeit, Tode, verbrauchte Totems und deine hoechste Klickrate.");
        put("Keystrokes", "Zeigt WASD, Leertaste und die Maustasten samt Klickrate an.");

        // --- PVP ---
        put("Hitboxes", "Zeichnet die Trefferbereiche von Wesen sichtbar ein.");
        put("Shield Status", "Zeigt an, ob dein Schild gerade blockt oder aussetzt.");
        put("Toggle Sprint", "Haelt das Sprinten dauerhaft, ohne die Taste zu halten.");
        put("Health Indicator", "Blendet die Lebenspunkte deines Ziels ein.");
        put("ESP", "Hebt ausgewaehlte Mobs durch Waende hervor.");
        put("Block-ESP", "Hebt ausgewaehlte Bloecke durch Waende hervor, z.B. Erze.");
        put("Container ESP", "Zeigt Kisten, Oefen und andere Behaelter durch Waende.");
        put("Spawner ESP", "Zeigt Monster-Spawner durch Waende.");
        put("Item ESP", "Hebt am Boden liegende Gegenstaende hervor.");
        put("Auto Totem", "Legt automatisch ein Totem in die Nebenhand. Auf Servern riskant.");
        put("Aimbot", "Zielt weich auf den naechsten Spieler. Hohes Bann-Risiko.");
        put("Auto Hit", "Schlaegt automatisch zu, sobald der Angriff voll geladen ist. Hohes Bann-Risiko.");
        put("Ziel-Info", "Zeigt ueber dem Gegner seine Ausruestung und ob er in Angriffsreichweite ist.");
        put("Projektil-Flugbahn", "Zeigt vorher, wo Enderperle, Wurftrank oder Pfeil landet.");
        put("Crystal Macro", "Setzt Enderkristalle auf Obsidian und sprengt sie sofort. Extrem hohes Bann-Risiko.");
        put("Fly", "Fliegen wie im Kreativmodus. Wird von Anticheats sofort erkannt.");

        // --- PERFORMANCE ---
        put("Potato Mode", "Senkt Grafikeinstellungen fuer mehr Bildrate.");
        put("Anti Render", "Blendet ausgewaehlte Entity-Typen komplett aus. Hilft gegen Lag durch viele Loren oder Ruestungsstaender.");

        // --- MISC ---
        put("Fullbright", "Macht alles hell, auch ohne Fackeln.");
        put("No Particles", "Schaltet stoerende Partikel ab.");
        put("Small Totem", "Verkleinert die Totem-Animation.");
        put("No Pumpkin Blur", "Entfernt die Sichtbehinderung durch einen Kuerbis.");
        put("Low Fire", "Senkt die Flammen am Bildschirmrand, damit man mehr sieht.");
        put("Low Shield", "Rueckt den Schild aus dem Blickfeld.");
        put("No Fog", "Entfernt den Nebel in der Ferne.");
        put("Klarsicht Wasser", "Klare Sicht unter Wasser.");
        put("Klarsicht Lava", "Klare Sicht in Lava.");
        put("Item-Groesse Hand", "Aendert die Groesse des Gegenstands in deiner Hand.");
        put("Freecam", "Loest die Kamera vom Koerper, um sich umzusehen. Der Koerper bleibt stehen.");
        put("Stash Finder", "Meldet Chunks mit auffaellig vielen Kisten -- Hinweis auf ein Lager.");
        put("Sus Chunks", "Faerbt Chunks nach Aktivitaet ein, um Basen aufzuspueren.");
        put("Tunnel Detector", "Findet gegrabene Tunnel unter der Erde.");
        put("No Fall", "Verhindert Fallschaden. Wird von Anticheats zuverlaessig erkannt.");
        put("Waypoints", "Eigene Marker setzen und wiederfinden, mit Saeule, Beschriftung und Randpfeil.");
        put("Chunk-Grenzen", "Zeichnet die Kanten des Chunks, in dem du stehst. Hilft beim Bauen von Farmen.");
    }

    private ModuleInfo() {}

    private static void put(String name, String text) {
        TEXT.put(name, text);
    }

    /** Beschreibung zu einem Modul, oder null wenn keine hinterlegt ist. */
    public static String get(String moduleName) {
        return TEXT.get(moduleName);
    }
}
