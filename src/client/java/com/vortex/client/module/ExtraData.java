package com.vortex.client.module;

/**
 * Fuer Module, die neben ihren Einstellungen noch eine eigene Liste speichern.
 *
 * WARUM ES DAS GIBT: ConfigManager kannte bisher jedes solche Modul beim
 * Namen -- EspModule, BlockEspModule, AntiRenderModule und zwei weitere,
 * jeweils mit eigenem instanceof-Zweig. Damit war der Kern des Clients an
 * konkrete Module gebunden, und ein Modul liess sich nicht mehr entfernen,
 * ohne den ConfigManager anzufassen.
 *
 * Genau das steht beim Umzug der Cheat-Module ins Addon an. Ueber diese
 * Schnittstelle fragt der ConfigManager nur noch "hast du Zusatzdaten?" --
 * und es ist ihm gleich, ob das Modul aus dem Client oder einem Addon kommt.
 */
public interface ExtraData {

    /**
     * Schluessel in der Konfigurationsdatei, z. B. "__mobs__".
     *
     * Muss zu dem passen, was frueher fest im ConfigManager stand, sonst
     * verlieren bestehende Presets ihre Listen.
     */
    String extraKey();

    /** Die Liste als Text fuer die Datei. */
    String serializeExtra();

    /** Text aus der Datei zurueck in die Liste. */
    void deserializeExtra(String value);

    /** Beim Zuruecksetzen auf Standardwerte. */
    void clearExtra();
}
