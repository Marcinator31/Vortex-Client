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

    // ---- Spaltenmenue (4.6.2) ----------------------------------------------
    //
    // Groesse des ganzen Menues, frei verschobene Kategorien und eigene
    // Spaltenbreiten. Gespeichert in global.txt (gehoert zum Rechner, nicht
    // zum Preset -- ein Freund mit anderem Bildschirm bekommt es nicht mit).

    /** 0 = Auto (passt sich an), sonst Prozent 50..150. */
    private static int panelScale = 0;
    /** Kategorie -> {x, y} als Anteil der Bildschirmbreite/-hoehe. */
    private static final java.util.Map<String, float[]> spaltenPos = new java.util.LinkedHashMap<>();
    /** Kategorie -> eigene Breite in Pixeln (des verkleinerten Menues). */
    private static final java.util.Map<String, Integer> spaltenBreite = new java.util.LinkedHashMap<>();

    public static int getPanelScale() { return panelScale; }

    public static void setPanelScale(int prozent) {
        panelScale = prozent <= 0 ? 0 : Math.max(50, Math.min(150, prozent));
    }

    public static float[] getSpaltePos(String kat) { return spaltenPos.get(kat); }

    public static void setSpaltePos(String kat, float x, float y) {
        spaltenPos.put(kat, new float[]{Math.max(0f, Math.min(0.98f, x)), Math.max(0f, Math.min(0.98f, y))});
    }

    public static boolean hatFreieAnordnung() { return !spaltenPos.isEmpty(); }

    public static int getSpalteBreite(String kat) {
        Integer w = spaltenBreite.get(kat);
        return w == null ? 0 : w;
    }

    public static void setSpalteBreite(String kat, int w) {
        spaltenBreite.put(kat, Math.max(70, Math.min(240, w)));
    }

    /** Alles zurueck auf die automatische Anordnung. */
    public static void resetSpalten() {
        panelScale = 0;
        spaltenPos.clear();
        spaltenBreite.clear();
    }

    /** Format: skala;KAT@x@y@breite,KAT@... (x/y "-" = automatisch). */
    public static String serializeSpalten() {
        StringBuilder sb = new StringBuilder().append(panelScale).append(';');
        java.util.Set<String> alle = new java.util.LinkedHashSet<>(spaltenPos.keySet());
        alle.addAll(spaltenBreite.keySet());
        boolean erst = true;
        for (String k : alle) {
            if (!erst) sb.append(',');
            erst = false;
            float[] p = spaltenPos.get(k);
            sb.append(k).append('@')
              .append(p == null ? "-" : String.format(java.util.Locale.ROOT, "%.4f", p[0])).append('@')
              .append(p == null ? "-" : String.format(java.util.Locale.ROOT, "%.4f", p[1])).append('@')
              .append(getSpalteBreite(k));
        }
        return sb.toString();
    }

    public static void deserializeSpalten(String data) {
        resetSpalten();
        if (data == null || data.isBlank()) return;
        try {
            String[] teile = data.split(";", 2);
            setPanelScale(Integer.parseInt(teile[0].trim()));
            if (teile.length < 2 || teile[1].isBlank()) return;
            for (String e : teile[1].split(",")) {
                String[] f = e.split("@");
                if (f.length < 4) continue;
                if (!f[1].equals("-") && !f[2].equals("-")) {
                    setSpaltePos(f[0], Float.parseFloat(f[1]), Float.parseFloat(f[2]));
                }
                int w = Integer.parseInt(f[3].trim());
                if (w > 0) setSpalteBreite(f[0], w);
            }
        } catch (RuntimeException ignored) {
            resetSpalten();
        }
    }
}
