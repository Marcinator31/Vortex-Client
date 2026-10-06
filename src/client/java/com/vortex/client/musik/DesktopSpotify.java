package com.vortex.client.musik;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

/**
 * Ohne Spotify-Anmeldung (nur Windows): Titel und Kuenstler aus dem Fenster
 * der Spotify-App lesen. Die App zeigt beim Abspielen "Kuenstler - Titel" als
 * Fenstertitel, pausiert nur "Spotify Premium"/"Spotify Free"/"Spotify".
 *
 * Kein Cover, keine Position, kein Mithoeren durch diese Quelle -- dafuer
 * braucht es die Anmeldung. Andere koennen aber trotzdem mithoeren, wenn SIE
 * angemeldet sind (ihr Client sucht den Song dann auf Spotify).
 *
 * JNA liegt Minecraft bei (oshi), auch fuer 1.21.11.
 */
final class DesktopSpotify {
    private DesktopSpotify() {}

    private static volatile boolean kaputt;

    static boolean moeglich() {
        return !kaputt && net.minecraft.util.Util.getPlatform() == net.minecraft.util.Util.OS.WINDOWS;
    }

    /** Fenstertitel der Spotify-App (oder null, wenn nicht offen). Blockiert kurz -- nicht im Render-Thread. */
    static String fensterTitel() {
        Object[] f = fenster();
        return f == null ? null : (String) f[0];
    }

    // WM_APPCOMMAND: dieselben Befehle wie die Medientasten der Tastatur -- aber
    // nur an die Spotify-App geschickt (andere Player bleiben unberuehrt).
    static final int PLAY_PAUSE = 14, NAECHSTER = 11, VORHERIGER = 12;

    /**
     * Steuert die Spotify-App direkt (Windows). Geht auch mit Spotify Free und
     * ohne Anmeldung -- die Web-API erlaubt Steuern nur mit Premium.
     * Blockiert kurz; nicht im Render-Thread. true = die App war da.
     */
    static boolean befehl(int appCommand) {
        Object[] f = fenster();
        if (f == null) return false;
        try {
            WinDef.HWND h = (WinDef.HWND) f[1];
            User32.INSTANCE.SendMessage(h, 0x0319, new WinDef.WPARAM(Pointer.nativeValue(h.getPointer())),
                    new WinDef.LPARAM((long) appCommand << 16));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {Titel, HWND} des Spotify-Hauptfensters, oder null. */
    private static Object[] fenster() {
        if (!moeglich()) return null;
        try {
            Object[] treffer = { null, null };
            User32.INSTANCE.EnumWindows((WinDef.HWND h, Pointer p) -> {
                if (!User32.INSTANCE.IsWindowVisible(h)) return true;
                char[] buf = new char[512];
                int n = User32.INSTANCE.GetWindowText(h, buf, buf.length);
                if (n <= 0) return true;
                IntByReference pid = new IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(h, pid);
                String exe = ProcessHandle.of(pid.getValue()).flatMap(ph -> ph.info().command()).orElse("");
                if (!exe.toLowerCase(java.util.Locale.ROOT).endsWith("spotify.exe")) return true;
                String t = new String(buf, 0, n);
                // Das Hauptfenster: entweder "Kuenstler - Titel" oder "Spotify..."
                if (t.contains(" - ") || t.startsWith("Spotify")) { treffer[0] = t; treffer[1] = h; return false; }
                return true;
            }, null);
            return treffer[0] == null ? null : treffer;
        } catch (Throwable t) {
            kaputt = true;          // JNA fehlt o. ae.: nicht weiter versuchen
            return null;
        }
    }

    /** Aus dem Fenstertitel einen Song machen (null = pausiert / kein Song). */
    static Song song(String titel) {
        if (titel == null || !titel.contains(" - ") || titel.startsWith("Spotify")) return null;
        int i = titel.indexOf(" - ");
        String kuenstler = titel.substring(0, i).trim();
        String name = titel.substring(i + 3).trim();
        if (name.isEmpty()) return null;
        return new Song("", name, kuenstler, "", "", 0, 0, true, System.currentTimeMillis(), "");
    }
}
