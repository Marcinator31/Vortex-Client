package com.vortex.client.module;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Basis fuer JEDES Feature im Client.
 *
 * Das ist der Trick, mit dem das ganze "Lunar-Menue" funktioniert:
 * Jedes Feature ist ein Module mit
 *   - Name + Kategorie (fuer die Sortierung im Menue)
 *   - einem enabled-Zustand (der An/Aus-Schalter)
 *   - einer Liste von Settings (die anpassbaren Optionen)
 *
 * Das GUI muss KEIN einziges Feature einzeln kennen. Es laeuft nur
 * ueber die Modul-Liste und die Settings jedes Moduls. Neues Feature
 * = neue Module-Unterklasse registrieren, GUI bleibt unangetastet.
 */
public abstract class Module {

    /**
     * Order here is the order in the menu.
     *
     * CHEATS is deliberately its own group rather than a label inside PVP:
     * these modules act for you instead of showing you something, and that is
     * a difference worth seeing at a glance before switching one on.
     */
    public enum Category {
        HUD, PVP, CHEATS, PERFORMANCE, MISC
    }

    private final String name;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();

    private final BooleanSetting enabled = new BooleanSetting("Enabled", false);

    /**
     * Key that switches this module on and off.
     *
     * Every module has one, unbound by default. Binding a key to something you
     * flip often -- crystal macro, freecam, a specific ESP -- saves opening the
     * menu mid-fight, which is exactly when you do not have time for it.
     */
    private final com.vortex.client.core.setting.KeySetting toggleKey =
            new com.vortex.client.core.setting.KeySetting(
                    "Toggle Key", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);

    protected Module(String name, Category category) {
        this.name = name;
        this.category = category;
        // enabled is a setting itself, so it shows up in the menu automatically.
        this.settings.add(enabled);
        this.settings.add(toggleKey);
        enabled.rememberDefault();
        toggleKey.rememberDefault();
    }

    /** The key that toggles this module (unbound by default). */
    public com.vortex.client.core.setting.KeySetting getToggleKey() {
        return toggleKey;
    }

    /**
     * Setzt das Modul standardmaessig auf "an" -- ohne onEnable() auszuloesen
     * (das waere beim Konstruktor noch zu frueh). Im Konstruktor der
     * Unterklasse aufrufen, wenn das Modul von Anfang an aktiv sein soll.
     */
    protected void enabledByDefault() {
        this.enabled.set(true);
        this.enabled.rememberDefault();
    }

    /** Settings in der Unterklasse hinzufuegen. */
    protected void addSetting(Setting setting) {
        // Ausgangswert festhalten, damit ein frisches Preset zurueck kann.
        setting.rememberDefault();
        this.settings.add(setting);
    }

    public String getName() { return name; }
    public Category getCategory() { return category; }
    public List<Setting> getSettings() { return settings; }

    public boolean isEnabled() { return enabled.get(); }

    /**
     * Liefert das interne "Aktiviert"-Setting. Das GUI nutzt das, um zu
     * erkennen, ob eine angeklickte Setting-Zeile der Haupt-An/Aus-Schalter
     * ist -- dann muss setEnabled() laufen (loest onEnable/onDisable aus),
     * nicht nur der rohe Wert.
     */
    public BooleanSetting getEnabledSetting() { return enabled; }

    public void setEnabled(boolean value) {
        boolean was = enabled.get();
        enabled.set(value);
        if (value && !was) onEnable();
        if (!value && was) onDisable();
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    /**
     * Wendet den aktuellen enabled-Zustand aktiv an, indem onEnable() ODER
     * onDisable() aufgerufen wird -- OHNE den Wert zu aendern.
     *
     * Wird beim Client-Start einmal aufgerufen (nach dem Laden der Config),
     * damit Module, die ihre Wirkung ueber onEnable()/onDisable() entfalten
     * (z.B. AppleSkin/ShieldStatus per Reflection), ihren Effekt passend zum
     * geladenen/Default-Zustand setzen.
     *
     * Wichtig: BEIDE Richtungen werden angewendet. Ein Reflection-Modul, das
     * gespeichert "aus" ist, dessen externe Mod aber per Default "an" waere,
     * muss beim Start onDisable() ausfuehren -- sonst bliebe die Mod sichtbar,
     * obwohl das Modul aus ist. Module mit teurem onDisable (z.B. Resource-
     * Pack-Reload) pruefen selbst, ob wirklich etwas zu tun ist.
     */
    public void syncState() {
        if (enabled.get()) {
            onEnable();
        } else {
            onDisable();
        }
    }

    // Hooks fuer Unterklassen -- optional zu ueberschreiben.
    protected void onEnable() {}
    protected void onDisable() {}
}
