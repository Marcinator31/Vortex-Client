package com.vortex.client.gui;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Merkt sich Oberflaechen-Zustand, der ueber das Schliessen des Menues hinaus
 * erhalten bleiben soll: angepinnte Lieblingsmodule und die Fensterposition des
 * ClickGUI. Wird ueber den ConfigManager mit dem aktiven Preset gespeichert.
 *
 * Bewusst getrennt vom ClickGUI selbst, weil das Fenster bei jedem Oeffnen neu
 * erzeugt wird -- der Zustand darf davon nicht abhaengen.
 */
public final class GuiState {

    /** Namen der angepinnten Module (Reihenfolge bleibt erhalten). */
    private static final Set<String> favorites = new LinkedHashSet<>();

    /**
     * Fensterposition. Gespeichert wird der Versatz zur Bildschirmmitte, nicht
     * die absolute Position -- so bleibt das Fenster auch nach einem Wechsel der
     * Aufloesung oder Fenstergroesse an einer sinnvollen Stelle.
     */
    private static int offsetX = 0;
    private static int offsetY = 0;

    /**
     * Vom Nutzer eingestellte Fenstergroesse (0 = Standard).
     *
     * Noetig, weil bei vielen Modulen in einer Kategorie sonst Eintraege unten
     * aus dem Fenster fallen -- ohne Moeglichkeit, es groesser zu ziehen.
     */
    private static int windowW = 0;
    private static int windowH = 0;

    private GuiState() {}

    // ---- Favoriten ----

    public static boolean isFavorite(String moduleName) {
        return favorites.contains(moduleName);
    }

    public static void toggleFavorite(String moduleName) {
        if (!favorites.add(moduleName)) favorites.remove(moduleName);
    }

    public static Set<String> getFavorites() {
        return favorites;
    }

    public static boolean hasFavorites() {
        return !favorites.isEmpty();
    }

    public static String serializeFavorites() {
        return String.join(",", favorites);
    }

    public static void deserializeFavorites(String data) {
        favorites.clear();
        if (data == null || data.isEmpty()) return;
        for (String s : data.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) favorites.add(t);
        }
    }

    // ---- Fensterposition ----

    public static int getOffsetX() { return offsetX; }
    public static int getOffsetY() { return offsetY; }

    public static void setOffset(int x, int y) {
        offsetX = x;
        offsetY = y;
    }

    public static void resetOffset() {
        offsetX = 0;
        offsetY = 0;
        windowW = 0;
        windowH = 0;
    }

    public static int getWindowW() { return windowW; }
    public static int getWindowH() { return windowH; }

    public static void setWindowSize(int w, int h) {
        windowW = w;
        windowH = h;
    }

    public static String serializeWindow() {
        return offsetX + ":" + offsetY + ":" + windowW + ":" + windowH;
    }

    public static void deserializeWindow(String data) {
        if (data == null) return;
        String[] parts = data.split(":");
        if (parts.length < 2) return;
        try {
            offsetX = Integer.parseInt(parts[0].trim());
            offsetY = Integer.parseInt(parts[1].trim());
            // Groesse kam spaeter dazu -- aeltere Dateien haben sie nicht.
            if (parts.length >= 4) {
                windowW = Integer.parseInt(parts[2].trim());
                windowH = Integer.parseInt(parts[3].trim());
            }
        } catch (Throwable ignored) {
            offsetX = 0;
            offsetY = 0;
            windowW = 0;
            windowH = 0;
        }
    }
}
