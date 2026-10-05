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
        if (!moeglich()) return null;
        try {
            String[] treffer = { null };
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
                if (t.contains(" - ") || t.startsWith("Spotify")) { treffer[0] = t; return false; }
                return true;
            }, null);
            return treffer[0];
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
