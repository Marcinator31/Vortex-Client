package com.vortex.client.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die Huete & Co. (seit 4.27): glatte, animierte 3D-Formen aus {@link Netz}
 * statt farbiger Kloetzchen. Jedes Teil wird je Bild neu gebaut (wenige
 * hundert Flaechen) -- so koennen sich Ringe drehen, Flammen flackern,
 * Kristalle schweben.
 *
 * Ohne Minecraft-Klassen (Vorschau im Test). t = Zeit in Ticks.
 */
public final class Kopfschmuck {
    private Kopfschmuck() {}

    public interface Bauer { void baue(Netz n, float t); }

    public record Design(String id, String name, String text, Bauer bauer) {}

    private static final Map<String, Design> ALLE = new LinkedHashMap<>();
    public static Map<String, Design> alle() { return ALLE; }
    public static Design get(String id) { return id == null ? null : ALLE.get(id); }

    private static void neu(String id, String name, String text, Bauer b) { ALLE.put(id, new Design(id, name, text, b)); }

    static float sin(double v) { return (float) Math.sin(v); }
    static float cos(double v) { return (float) Math.cos(v); }

    static int hsv(float h, float s, float v) {
        h = (h % 1 + 1) % 1 * 6;
        int i = (int) h;
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), u = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    static {
        // --- Heiligenschein: leuchtender goldener Ring, schwebt und dreht sich; feiner zweiter Ring
        neu("halo", "Halo", "Glowing ring of light", (n, t) -> {
            float puls = 0.75f + 0.25f * sin(t * 0.12f);
            n.push();
            n.verschiebe(0, 3.4f + 0.45f * sin(t * 0.09f), 0);
            n.drehX(0.12f * sin(t * 0.05f));
            n.drehZ(0.1f);
            n.drehY(t * 0.03f);
            n.leuchten(puls);
            n.torus(4.3f, 0.5f, 48, 10, 0xFFFFF4C8, 0xFFFFC23D);
            n.leuchten(0.45f * puls);
            n.drehY(-t * 0.07f);
            n.torus(5.1f, 0.13f, 48, 6, 0xFFFFF0B0, 0xFFFFD873);
            // kleine Lichtfunken auf dem Ring
            n.leuchten(1f);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + t * 0.05;
                float hoch = 0.3f * sin(t * 0.2f + i);
                n.kristall(5.1f * cos(a), hoch, 5.1f * sin(a), 0.32f, 0.5f, 0.5f, 4, 0xFFFFFFFF, 0xFFFFF6D0);
            }
            n.pop();
            n.leuchten(0);
        });

        // --- Koenigskrone: goldener Reif mit Zacken, Perlen und funkelnden Edelsteinen
        neu("crown", "Royal Crown", "Gold, pearls and glowing gems", (n, t) -> {
            int goldD = 0xFFA8740F, gold = 0xFFE7B53B, goldH = 0xFFFFE08A;
            n.metall(true);
            n.reif(4.35f, 4.85f, -0.6f, 1.7f, 32, goldD, gold);
            n.push(); n.verschiebe(0, -0.6f, 0); n.torus(4.75f, 0.32f, 40, 6, goldH, gold); n.pop();
            n.push(); n.verschiebe(0, 1.7f, 0); n.torus(4.6f, 0.26f, 40, 6, goldH, gold); n.pop();
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + Math.PI / 8;
                float r = 4.6f, hoch = i % 2 == 0 ? 4.6f : 3.6f;
                float x = r * cos(a), z = r * sin(a);
                n.kegel(new float[]{ x, 1.5f, z }, 0.85f, new float[]{ x * 1.02f, hoch, z * 1.02f }, 10, gold, goldH);
                n.metall(false);
                n.kugel(x * 1.02f, hoch + 0.35f, z * 1.02f, 0.42f, 10, 0xFFFFFFFF, 0xFFE8E2D6);
                n.metall(true);
            }
            n.metall(false);
            // Edelsteine rundum: vorne ein Rubin, seitlich Saphire, hinten Smaragde
            int[] farben = { 0xFFFF2D55, 0xFF3D7BFF, 0xFF2BD67B, 0xFF3D7BFF };
            for (int i = 0; i < 8; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 4;
                int f = farben[(i == 0) ? 0 : (i == 4 ? 2 : (i % 2 == 0 ? 1 : 3))];
                float funkel = 0.45f + 0.35f * (0.5f + 0.5f * sin(t * 0.15f + i * 1.7f));
                n.leuchten(funkel);
                n.push();
                n.verschiebe(4.95f * cos(a), 0.55f, 4.95f * sin(a));
                n.drehY((float) (-a + Math.PI / 2));
                n.drehX((float) Math.PI / 2);
                n.kristall(0, 0, 0, i == 0 ? 0.75f : 0.5f, 0.35f, 0.25f, 6, Netz.mische(f, 0xFFFFFFFF, 0.25f), f);
                n.pop();
            }
            n.leuchten(0);
        });

        // --- Daemonenhoerner: gebogen, dunkel, mit gluehenden Spitzen
        neu("horns", "Demon Horns", "Curved horns with glowing tips", (n, t) -> {
            float glut = 0.65f + 0.35f * sin(t * 0.14f);
            for (int s = -1; s <= 1; s += 2) {
                int k = 14;
                float[][] pfad = new float[k][];
                float[] rad = new float[k];
                for (int i = 0; i < k; i++) {
                    float u = i / (float) (k - 1);
                    pfad[i] = new float[]{ s * (2.1f + 3.0f * u - 0.4f * u * u), -0.6f + 5.8f * u - 1.2f * u * u, -1.6f + 0.6f * u + 3.4f * u * u };
                    rad[i] = 1.55f * (1 - u) + 0.05f;
                }
                rad[k - 1] = 0;
                n.roehre(java.util.Arrays.copyOfRange(pfad, 0, 10), java.util.Arrays.copyOfRange(rad, 0, 10), 14, 0xFF1A0C10, 0xFF5A1420);
                n.leuchten(glut);
                n.roehre(java.util.Arrays.copyOfRange(pfad, 9, k), java.util.Arrays.copyOfRange(rad, 9, k), 14, 0xFFB3261E, 0xFFFFC04D);
                n.leuchten(0);
                // Rillen am Ansatz
                for (int r = 1; r < 6; r++) {
                    float u = r / 13f;
                    n.push();
                    n.verschiebe(pfad[r][0], pfad[r][1], pfad[r][2]);
                    float[] d = Netz.norm(Netz.sub(pfad[r + 1], pfad[r - 1]));
                    n.drehZ((float) Math.atan2(-d[0], d[1]));
                    n.drehX((float) Math.asin(Math.max(-1, Math.min(1, d[2]))));
                    n.torus(rad[r] * 0.97f, 0.12f, 16, 4, 0xFF3A1218, 0xFF240A0E);
                    n.pop();
                }
            }
        });

        // --- Umlaufbahn: drei leuchtende Kugeln kreisen mit Schweif um den Kopf
        neu("orbit", "Orbit", "Three glowing orbs circling you", (n, t) -> {
            int[] farben = { 0xFF5EEAFF, 0xFFFF5EDB, 0xFFFFD45E };
            for (int i = 0; i < 3; i++) {
                n.push();
                n.verschiebe(0, -2.2f, 0);
                n.drehX(0.35f * cos(i * 2.1f));
                n.drehZ(0.35f * sin(i * 2.1f));
                for (int k = 0; k < 7; k++) {
                    double a = t * 0.11 + i * 2.094 - k * 0.13;
                    float r = k == 0 ? 1.05f : 0.7f * (1 - k / 8f);
                    n.leuchten(k == 0 ? 1f : 0.85f - k * 0.1f);
                    int f = k == 0 ? Netz.mische(farben[i], 0xFFFFFFFF, 0.35f) : farben[i];
                    n.kugel(6.4f * cos(a), 0, 6.4f * sin(a), r, k == 0 ? 14 : 8, f, farben[i]);
                }
                n.pop();
            }
            n.leuchten(0);
        });

        // --- Kristallkrone: schwebende, sich drehende Kristalle
        neu("crystals", "Crystal Crown", "Floating crystals of light", (n, t) -> {
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + t * 0.02;
                float y = 2.6f + 0.6f * sin(t * 0.08f + i * 1.3f);
                float puls = 0.5f + 0.3f * sin(t * 0.13f + i);
                n.leuchten(puls);
                n.push();
                n.verschiebe(4.6f * cos(a), y, 4.6f * sin(a));
                n.drehY(t * 0.06f + i);
                n.drehZ(0.25f * sin(t * 0.05f + i));
                int f1 = i % 2 == 0 ? 0xFF8FF7FF : 0xFFC7A6FF, f2 = i % 2 == 0 ? 0xFFE8FFFF : 0xFFF1E6FF;
                n.kristall(0, 0, 0, 0.9f, 2.0f, 1.1f, 6, f1, f2);
                n.pop();
            }
            // grosser Kristall in der Mitte oben
            n.leuchten(0.65f + 0.25f * sin(t * 0.1f));
            n.push();
            n.verschiebe(0, 4.8f + 0.5f * sin(t * 0.07f), 0);
            n.drehY(-t * 0.04f);
            n.kristall(0, 0, 0, 1.3f, 2.8f, 1.6f, 6, 0xFFB794FF, 0xFFF5EEFF);
            n.pop();
            n.leuchten(0);
        });

        // --- Neon-Kopfhoerer: passt zu Music -- die Ringe leuchten in wechselnden Farben
        neu("headphones", "Neon Headphones", "Glowing RGB headphones", (n, t) -> {
            int k = 17;
            float[][] buegel = new float[k][];
            float[] r = new float[k];
            for (int i = 0; i < k; i++) {
                double a = Math.PI * i / (k - 1);
                buegel[i] = new float[]{ -5.0f * cos(a), -3.6f + 4.9f * sin(a), 0 };
                r[i] = 0.55f;
            }
            n.roehre(buegel, r, 10, 0xFF2B2B36, 0xFF2B2B36);
            // Polster oben
            n.push(); n.verschiebe(0, 1.0f, 0); n.skaliere(1, 0.6f, 1); n.kugel(0, 0, 0, 1.1f, 12, 0xFF3A3A48, 0xFF202028); n.pop();
            for (int s = -1; s <= 1; s += 2) {
                n.push();
                n.verschiebe(s * 5.0f, -4.4f, 0);
                n.drehZ((float) (s * Math.PI / 2));
                n.zylinder(2.4f, -0.9f, 0.9f, 24, 0xFF1C1C24, 0xFF33333F);
                int farbe = hsv(t * 0.01f + (s > 0 ? 0.5f : 0), 0.85f, 1f);
                n.leuchten(0.9f);
                n.push(); n.verschiebe(0, -s * 0.95f, 0); n.torus(1.9f, 0.28f, 32, 6, farbe, farbe); n.pop();
                n.leuchten(0.5f);
                n.push(); n.verschiebe(0, -s * 0.92f, 0); n.skaliere(1, 0.12f, 1); n.kugel(0, 0, 0, 1.2f, 16, Netz.mische(farbe, 0xFFFFFFFF, 0.4f), farbe); n.pop();
                n.leuchten(0);
                n.pop();
            }
        });

        // --- Fuchsohren: weich, mit hellem Inneren -- zucken ab und zu
        neu("fox_ears", "Fox Ears", "Fluffy ears that twitch", (n, t) -> {
            for (int s = -1; s <= 1; s += 2) {
                float ph = (t + (s > 0 ? 37 : 0)) % 90;
                float zuck = ph < 6 ? 0.22f * sin(ph / 6f * Math.PI) : 0;
                n.push();
                n.verschiebe(s * 2.5f, -0.3f, 0.6f);
                n.drehZ(s * (-0.22f - zuck));
                // Ohr: flach-dreieckig, unten breit, Spitze dunkel
                n.push();
                n.skaliere(1, 1, 0.55f);
                n.roehre(new float[][]{ { 0, 0, 0 }, { 0, 1.5f, 0 }, { 0, 3.0f, 0 }, { 0, 4.3f, 0 } }, new float[]{ 2.0f, 1.45f, 0.75f, 0 }, 14,
                        0xFFE8803A, 0xFF3A2216);
                n.pop();
                // helles, flauschiges Inneres (vorne)
                n.push();
                n.verschiebe(0, 0.25f, -0.62f);
                n.skaliere(1, 1, 0.22f);
                n.roehre(new float[][]{ { 0, 0, 0 }, { 0, 1.3f, 0 }, { 0, 2.6f, 0 }, { 0, 3.5f, 0 } }, new float[]{ 1.35f, 0.95f, 0.45f, 0 }, 12,
                        0xFFFFF1E4, 0xFFFFD2BC);
                n.pop();
                n.pop();
            }
        });

        // --- Flammenkrone: flackernde Flammenzungen rund um den Kopf
        neu("flame", "Flame Crown", "A ring of living fire", (n, t) -> {
            n.leuchten(0.7f);
            n.push(); n.verschiebe(0, 0.1f, 0); n.torus(4.5f, 0.45f, 40, 8, 0xFFFF8A2B, 0xFFB3261E); n.pop();
            n.leuchten(1f);
            int z = 12;
            for (int i = 0; i < z; i++) {
                double a = i * 2 * Math.PI / z;
                float hoch = 2.4f + 1.4f * (0.5f + 0.5f * sin(t * 0.37f + i * 2.3f)) + 0.6f * sin(t * 0.9f + i);
                int k = 7;
                float[][] pfad = new float[k][];
                float[] rad = new float[k];
                for (int j = 0; j < k; j++) {
                    float u = j / (float) (k - 1);
                    float wellig = 0.45f * u * sin(t * 0.5f + i * 1.7f + u * 4f);
                    float rr = 4.5f - 0.6f * u;
                    pfad[j] = new float[]{ rr * cos(a) + wellig * -sin(a), 0.2f + hoch * u, rr * sin(a) + wellig * cos(a) };
                    rad[j] = 0.75f * (1 - u * u);
                }
                rad[k - 1] = 0;
                n.roehre(pfad, rad, 8, 0xFFFF4A12, 0xFFFFF0A0);
            }
            n.leuchten(0);
        });

        // --- Hexenhut: breite Krempe, geknickte Spitze, leuchtendes Band, Sterne
        neu("witch_hat", "Witch Hat", "Bent tip, glowing band and stars", (n, t) -> {
            n.push();
            n.verschiebe(0, -0.2f, 0);
            n.skaliere(1, 0.16f, 1);
            n.torus(5.3f, 2.4f, 40, 10, 0xFF3E1E66, 0xFF2A1248);
            n.pop();
            int k = 12;
            float[][] pfad = new float[k][];
            float[] rad = new float[k];
            float wackel = 0.2f * sin(t * 0.06f);
            for (int i = 0; i < k; i++) {
                float u = i / (float) (k - 1);
                float knick = Math.max(0, u - 0.55f) / 0.45f;
                pfad[i] = new float[]{ wackel * u * 3f, 0.2f + 8.5f * u - 2.5f * knick * knick, 0.8f * u + 4.2f * knick * knick };
                rad[i] = 4.2f * (1 - u) + 0.15f * (1 - u);
            }
            rad[k - 1] = 0;
            n.roehre(pfad, rad, 20, 0xFF4A2478, 0xFF2A1248);
            n.leuchten(0.75f);
            n.push(); n.verschiebe(0, 1.2f, 0); n.torus(3.85f, 0.42f, 40, 6, 0xFF8CFF6B, 0xFF35C24A); n.pop();
            n.leuchten(0);
            // Schnalle vorne
            n.metall(true);
            n.push(); n.verschiebe(0, 1.2f, -4.1f); n.drehX((float) Math.PI / 2); n.skaliere(1, 1, 0.8f); n.torus(0.75f, 0.2f, 16, 6, 0xFFFFE08A, 0xFFC8962B); n.pop();
            n.metall(false);
            // Sterne um die Spitze
            n.leuchten(1);
            float[] spitze = pfad[k - 1];
            for (int i = 0; i < 3; i++) {
                double a = t * 0.07 + i * 2.094;
                n.kristall(spitze[0] + 1.6f * cos(a), spitze[1] + 0.4f * sin(t * 0.1f + i), spitze[2] + 1.6f * sin(a), 0.3f, 0.45f, 0.45f, 4, 0xFFFFF3A0, 0xFFFFFFFF);
            }
            n.leuchten(0);
        });
    }
}
