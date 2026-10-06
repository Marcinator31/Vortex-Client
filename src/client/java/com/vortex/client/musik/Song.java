package com.vortex.client.musik;

/**
 * Ein Song, wie er gerade laeuft -- eigener (Spotify/Desktop-App) oder der
 * eines anderen Spielers (vom Freunde-Server).
 *
 * @param id        Spotify-Track-ID (22 Zeichen) oder "" (nur Titel bekannt, z. B. Desktop-App)
 * @param cover     Cover-Adresse (i.scdn.co) oder ""
 * @param position  Position in ms zum Zeitpunkt {@code stand}
 * @param stand     System.currentTimeMillis(), als {@code position} galt
 * @param kontext   spotify:playlist:... / spotify:album:... (nur eigener Song, fuers Fortsetzen)
 */
public record Song(String id, String titel, String kuenstler, String album, String cover,
                   long dauer, long position, boolean spielt, long stand, String kontext) {

    /** Position jetzt (laeuft zwischen den Abfragen weiter). */
    public long jetzt() {
        if (!spielt) return position;
        long p = position + (System.currentTimeMillis() - stand);
        return dauer > 0 ? Math.min(dauer, p) : p;
    }

    /** 0..1 */
    public float anteil() {
        return dauer > 0 ? Math.max(0f, Math.min(1f, jetzt() / (float) dauer)) : 0f;
    }

    public boolean mitId() { return id != null && id.length() == 22; }

    /** Gleicher Song (unabhaengig von Position/Pause)? */
    public boolean gleich(Song o) {
        if (o == null) return false;
        if (mitId() && o.mitId()) return id.equals(o.id);
        return titel.equalsIgnoreCase(o.titel) && kuenstler.equalsIgnoreCase(o.kuenstler);
    }

    /** "Titel · Kuenstler" */
    public String zeile() {
        return kuenstler == null || kuenstler.isBlank() ? titel : titel + " · " + kuenstler;
    }

    public static String zeit(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
