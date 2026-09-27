package com.vortex.client.core.setting;

/**
 * Eine Farbe (ARGB als int) -- wahlweise auch ein FARBVERLAUF.
 *
 * Format einer Farbe: 0xAARRGGBB
 *   AA = Deckkraft (alpha), RR = rot, GG = gruen, BB = blau.
 *
 * ARTEN (neu in 4.6.0)
 *   Solid     eine Farbe, wie bisher
 *   Gradient  Verlauf von Farbe 1 nach Farbe 2
 *   Wave      derselbe Verlauf, aber er laeuft ueber den Text
 *   Rainbow   alle Farbtoene, laufend (Deckkraft von Farbe 1)
 *
 * ZWEI ARTEN, EINE FARBE ABZUFRAGEN
 *   at(pos)   Farbe an einer Stelle 0..1 -- fuer Dinge, die eine Breite
 *             haben (Texte, Leisten, Rahmen). So entsteht der Verlauf.
 *   get()     EINE Farbe -- fuer alles, was nur eine Farbe kennt (Hit
 *             Color, Umrisse, ESP ...). Bei einem Verlauf blendet sie
 *             langsam zwischen beiden Farben hin und her, bei Rainbow
 *             wandert sie durch alle Farbtoene.
 *
 * Damit funktioniert der Verlauf ueberall, wo bisher get() stand, ohne dass
 * jede Stelle einzeln angepasst werden muss.
 *
 * Gespeichert wird eine einfarbige Einstellung genau wie frueher (nur die
 * Hex-Zahl) -- alte Presets bleiben gueltig, und Presets ohne Verlauf sehen
 * in aelteren Versionen gleich aus.
 */
public class ColorSetting extends Setting {

    public static final int SOLID = 0, GRADIENT = 1, WAVE = 2, RAINBOW = 3;
    public static final String[] TYPES = {"Solid", "Gradient", "Wave", "Rainbow"};

    private int argb;
    private int argb2;
    private int type = SOLID;
    private float speed = 1f;
    private boolean gradientAllowed = true;

    public ColorSetting(String name, int defaultArgb) {
        super(name);
        this.argb = defaultArgb;
        // Zweite Farbe vorbelegen: das Blau des Client-Verlaufs, aber mit der
        // Deckkraft der ersten -- sonst waere ein halbdurchsichtiger
        // Hintergrund nach dem Umschalten ploetzlich halb deckend.
        this.argb2 = (defaultArgb & 0xFF000000) | 0x3B82F6;
    }

    /** Mit Verlauf als Ausgangswert. */
    public ColorSetting(String name, int defaultArgb, int defaultArgb2, int defaultType) {
        super(name);
        this.argb = defaultArgb;
        this.argb2 = defaultArgb2;
        this.type = clampType(defaultType);
    }

    // ------------------------------------------------------------------
    // Abfragen
    // ------------------------------------------------------------------

    /**
     * EINE Farbe fuer diesen Augenblick. Einfarbig: die Farbe selbst.
     * Verlauf: blendet zwischen beiden hin und her. Rainbow: wandert.
     */
    public int get() {
        switch (effectiveType()) {
            case GRADIENT:
            case WAVE:
                return mix(argb, argb2, tri(zeit() * 0.25f * speed));
            case RAINBOW:
                return at(0f);
            default:
                return argb;
        }
    }

    /**
     * Farbe an einer Stelle 0..1 (links .. rechts). Fuer alles, was eine
     * Breite hat.
     */
    public int at(float pos) {
        switch (effectiveType()) {
            case GRADIENT:
                return mix(argb, argb2, clamp01(pos));
            case WAVE:
                // Eine volle Breite zeigt den Weg von Farbe 1 nach Farbe 2;
                // die Welle laeuft nach rechts.
                return mix(argb, argb2, tri(pos * 0.5f - zeit() * 0.35f * speed));
            case RAINBOW: {
                float h = pos * 0.35f - zeit() * 0.18f * speed;
                h -= (float) Math.floor(h);
                return (argb & 0xFF000000) | hsbToRgb(h, 0.72f, 1f);
            }
            default:
                return argb;
        }
    }

    /** Ist es mehr als eine Farbe? */
    public boolean isGradient() {
        return effectiveType() != SOLID;
    }

    /** Aendert sie sich mit der Zeit (Wave, Rainbow)? */
    public boolean isAnimated() {
        int t = effectiveType();
        return t == WAVE || t == RAINBOW;
    }

    public int color1() { return argb; }
    public int color2() { return argb2; }
    public int type() { return effectiveType(); }
    public float speed() { return speed; }

    // ------------------------------------------------------------------
    // Aendern
    // ------------------------------------------------------------------

    /** Setzt Farbe 1 (bei einfarbig: DIE Farbe). */
    public void set(int argb) {
        this.argb = argb;
    }

    public void setColor2(int argb2) {
        this.argb2 = argb2;
    }

    public void setType(int type) {
        this.type = clampType(type);
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.1f, Math.min(5f, speed));
    }

    /** Alles uebernehmen -- Farben, Art, Tempo. Fuer "HUD Color auf alle". */
    public void copyFrom(ColorSetting o) {
        if (o == null) return;
        this.argb = o.argb;
        this.argb2 = o.argb2;
        this.type = o.type;
        this.speed = o.speed;
    }

    /**
     * Fuer Stellen, die nur eine feste Farbe speichern koennen (z. B. die
     * Markierungsfarbe eines Wegpunkts): dort bietet der Farbwaehler keinen
     * Verlauf an.
     */
    public ColorSetting noGradient() {
        this.gradientAllowed = false;
        this.type = SOLID;
        return this;
    }

    public boolean gradientAllowed() {
        return gradientAllowed;
    }

    // Bequeme Einzelzugriffe fuer einen Farbwaehler (Farbe 1).
    public int alpha() { return (argb >> 24) & 0xFF; }
    public int red()   { return (argb >> 16) & 0xFF; }
    public int green() { return (argb >> 8)  & 0xFF; }
    public int blue()  { return argb & 0xFF; }

    public void setComponents(int a, int r, int g, int b) {
        this.argb = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    // ------------------------------------------------------------------
    // Speichern
    // ------------------------------------------------------------------

    @Override
    public String serialize() {
        if (effectiveType() == SOLID) return Integer.toHexString(argb);
        return Integer.toHexString(argb) + ";" + type + ";" + Integer.toHexString(argb2)
                + ";" + Math.round(speed * 100f) / 100f;
    }

    @Override
    public void deserialize(String value) {
        if (value == null) return;
        try {
            String[] t = value.trim().split(";");
            this.argb = (int) Long.parseLong(t[0], 16);
            if (t.length >= 3) {
                this.type = clampType(Integer.parseInt(t[1].trim()));
                this.argb2 = (int) Long.parseLong(t[2].trim(), 16);
                if (t.length >= 4) setSpeed(Float.parseFloat(t[3].trim()));
            } else {
                this.type = SOLID;
            }
            if (!gradientAllowed) this.type = SOLID;
        } catch (RuntimeException ignored) {
            // Kaputter Eintrag: den bisherigen Wert behalten.
        }
    }

    // ------------------------------------------------------------------
    // Rechnen
    // ------------------------------------------------------------------

    private int effectiveType() {
        return gradientAllowed ? type : SOLID;
    }

    private static int clampType(int t) {
        return (t < SOLID || t > RAINBOW) ? SOLID : t;
    }

    /** Sekunden, fuer die Animation. */
    private static float zeit() {
        return (System.currentTimeMillis() % 3_600_000L) / 1000f;
    }

    /** Dreieckswelle 0..1..0 mit Periode 1 -- weiches Hin und Her. */
    private static float tri(float x) {
        float f = x - (float) Math.floor(x);
        float t = 1f - Math.abs(2f * f - 1f);
        // Etwas weicher an den Umkehrpunkten (Smoothstep)
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static int mix(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** HSB -> RGB (ohne java.awt, das im Spiel nicht sicher vorhanden ist). */
    private static int hsbToRgb(float h, float s, float b) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int sector = (int) Math.floor(hh);
        float f = hh - sector;
        float p = b * (1f - s), q = b * (1f - s * f), u = b * (1f - s * (1f - f));
        float r, g, bl;
        switch (sector % 6) {
            case 0:  r = b; g = u; bl = p; break;
            case 1:  r = q; g = b; bl = p; break;
            case 2:  r = p; g = b; bl = u; break;
            case 3:  r = p; g = q; bl = b; break;
            case 4:  r = u; g = p; bl = b; break;
            default: r = b; g = p; bl = q; break;
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(bl * 255f);
    }
}
