package com.vortex.client.module;

/**
 * Fuer Module, die neben ihren Einstellungen eine eigene Liste speichern.
 *
 * Vorher kannte der ConfigManager jedes solche Modul beim Namen -- damit war
 * der Kern an konkrete Module gebunden und ein Modul liess sich nicht
 * entfernen, ohne den ConfigManager anzufassen. Genau das steht beim Umzug
 * der Cheats ins Addon an.
 */
public interface ExtraData {
    /** Schluessel in der Datei, z. B. "__mobs__". Unveraendert lassen. */
    String extraKey();
    String serializeExtra();
    void deserializeExtra(String value);
    void clearExtra();
}
