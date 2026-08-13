package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.waypoint.WaypointSettings;
import com.vortex.client.waypoint.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Beschriftung der Waypoints am Bildschirm plus Pfeile am Rand fuer Marker, die
 * gerade nicht im Blickfeld liegen.
 *
 * Kern ist die Umrechnung eines Weltpunktes auf Bildschirmkoordinaten:
 *   1) Punkt relativ zur Kamera nehmen
 *   2) um Gier und Neigung zurueckdrehen, sodass die Blickrichtung auf die
 *      Z-Achse faellt
 *   3) perspektivisch durch die Tiefe teilen und auf die Fenstergroesse skalieren
 *
 * Liegt der Punkt hinter der Kamera oder ausserhalb des Bildes, wird stattdessen
 * ein Pfeil am Rand gezeichnet, der in seine Richtung zeigt -- so findet man auch
 * Marker wieder, die man gerade nicht sieht.
 */
public final class WaypointHud {

    private WaypointHud() {}

    /** Ergebnis der Umrechnung. */
    private static final class Screen {
        final double x, y, depth;
        final boolean visible;
        Screen(double x, double y, double depth, boolean visible) {
            this.x = x; this.y = y; this.depth = depth; this.visible = visible;
        }
    }

    /** Rechnet einen Weltpunkt auf Bildschirmkoordinaten um. */
    private static Screen project(double px, double py, double pz,
                                  double camX, double camY, double camZ,
                                  float yawDeg, float pitchDeg,
                                  double fovDeg, int width, int height) {
        double dx = px - camX, dy = py - camY, dz = pz - camZ;

        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        // Drehung um die Hochachse (Gier), sodass die Blickrichtung auf die
        // Z-Achse faellt.
        //
        // ACHTUNG, hier steckte der Fehler: Die Vorzeichen waren vertauscht.
        // Das faellt nur auf, wenn man geradeaus schaut (Gier 0) -- dort ist
        // der Sinus null und beide Varianten liefern dasselbe. Bei jeder
        // anderen Blickrichtung landete ein Punkt, der direkt vor einem lag,
        // rechnerisch seitlich -- deshalb rutschten Marker in die Bildecke.
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double rx = dx * cy + dz * sy;      // Anteil nach rechts
        double rz = -dx * sy + dz * cy;     // Anteil nach vorn

        // Drehung um die Querachse (Neigung).
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double ry = dy * cp + rz * sp;      // Anteil nach oben
        double rz2 = rz * cp - dy * sp;

        if (rz2 <= 0.05) {
            // Hinter der Kamera -- Bildschirmposition waere sinnlos.
            return new Screen(0, 0, rz2, false);
        }
        double f = (height / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);
        double sx = width / 2.0 - rx * f / rz2;
        double syy = height / 2.0 - ry * f / rz2;
        boolean onScreen = sx >= 0 && sx <= width && syy >= 0 && syy <= height;
        return new Screen(sx, syy, rz2, onScreen);
    }

    /** Wird aus dem HudRenderer aufgerufen. */
    /**
     * Weiche Ein- und Ausblendung der Beschriftung je Marker.
     *
     * Ohne das springt der Text hart an und aus, sobald das Fadenkreuz den
     * Punkt streift -- das wirkt unruhig. Hier waechst der Wert von 0 auf 1,
     * solange man drauf zielt, und faellt danach wieder ab.
     */
    private static final java.util.Map<Object, Float> AIM_ANIM = new java.util.HashMap<>();
    private static long lastNano = 0L;

    public static void draw(DrawContext ctx, MinecraftClient client) {
        // Zeitschritt fuer die Animationen (bildratenunabhaengig).
        long now = System.nanoTime();
        float dt = (lastNano == 0L) ? 0.016f : (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;
        if (dt > 0.1f) dt = 0.1f;

        // Aufraeumen: die Tabelle merkt sich einen Wert je Marker. Ohne dieses
        // Entfernen bleiben geloeschte Marker fuer immer darin stehen -- ein
        // langsam wachsendes Speicherleck ueber die gesamte Spielzeit.
        if (AIM_ANIM.size() > WaypointManager.all().size()) {
            AIM_ANIM.keySet().retainAll(WaypointManager.all());
        }
        WaypointSettings mod = WaypointSettings.INSTANCE;
        if (!mod.isEnabled()) return;
        if (client.player == null || client.world == null) return;
        if (client.textRenderer == null) return;
        if (!mod.labels.get() && !mod.edgeArrows.get()) return;

        // Kameraposition ueber den bereits erprobten Helfer holen -- der
        // beruecksichtigt auch die Freecam. Camera.getPos() gibt es in 1.21.11
        // nicht, deshalb bewusst dieser Weg.
        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        net.minecraft.util.math.Vec3d camPos = EspRender.cameraOffset(client, tickDelta);

        float yaw, pitch;
        if (com.vortex.client.freecam.Freecam.isActive()) {
            yaw = com.vortex.client.freecam.Freecam.getYaw();
            pitch = com.vortex.client.freecam.Freecam.getPitch();
        } else {
            yaw = client.player.getYaw();
            pitch = client.player.getPitch();
        }

        double fov = 70.0;
        try {
            Object v = client.options.getFov().getValue();
            if (v instanceof Number n) fov = n.doubleValue();
        } catch (Throwable t) {
            // Standardwert behalten.
        }
        if (fov < 30.0 || fov > 150.0) fov = 70.0;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        double maxDist = mod.maxDistance.get();

        String dim = WaypointRenderer.currentWorldKey(client);

        // Hinweis, solange eine Bereichsauswahl laeuft oder eine Block-Gruppe
        // aktiv ist -- sonst vergisst man, dass die erste Ecke noch offen ist.
        if (com.vortex.client.waypoint.WaypointActions.hasPendingCorner()) {
            String hint = "Area: pick the second corner";
            int hw = client.textRenderer.getWidth(hint);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(hint),
                    (w - hw) / 2, h - 70, 0xFFFFD070);
        } else {
            String grp = com.vortex.client.waypoint.WaypointActions.activeGroupName();
            if (grp != null) {
                String hint = "Block group: " + grp;
                int hw = client.textRenderer.getWidth(hint);
                ctx.drawText(client.textRenderer, Text.literal(hint),
                        (w - hw) / 2, h - 70, 0x90FFFFFF, false);
            }
        }

        // Zeile mit dem naechstgelegenen Marker -- praktisch beim Zurueckfinden.
        if (mod.showNearest.get()) {
            var near = WaypointManager.nearest(dim, client.player.getX(),
                    client.player.getY(), client.player.getZ());
            if (near != null) {
                double dx = near.x - client.player.getX();
                double dy = near.y - client.player.getY();
                double dz = near.z - client.player.getZ();
                int d = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
                String line = near.name + "  " + d + "m";
                int lw = client.textRenderer.getWidth(line);
                ctx.drawTextWithShadow(client.textRenderer, Text.literal(line),
                        (w - lw) / 2, h - 58, near.color | 0xFF000000);
            }
        }

        for (WaypointManager.Waypoint wp : WaypointManager.all()) {
            if (!wp.visible || !WaypointManager.matches(wp, dim)) continue;
            double wx = wp.x + 0.5, wy = wp.y + 1.0, wz = wp.z + 0.5;

            double ddx = wx - client.player.getX();
            double ddy = wy - client.player.getY();
            double ddz = wz - client.player.getZ();
            double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
            if (maxDist > 0 && dist > maxDist) continue;
            // Ganz nahe Marker ausblenden -- sonst steht man im Ziel und die
            // Beschriftung deckt das halbe Bild ab.
            double hideNear = mod.hideNear.get();
            if (hideNear > 0 && dist < hideNear) continue;

            Screen s = project(wx, wy, wz, camPos.x, camPos.y, camPos.z,
                    yaw, pitch, fov, w, h);

            int color = wp.color | 0xFF000000;

            if (s.visible) {
                // Wie stark wird dieser Marker gerade anvisiert? (0..1)
                double dxc = s.x - w / 2.0;
                double dyc = s.y - h / 2.0;
                int baseSize = mod.dotSize.getInt();
                double aimRadius = baseSize * 2.4;
                boolean aimed = (dxc * dxc + dyc * dyc) < (aimRadius * aimRadius);

                float a = AIM_ANIM.getOrDefault(wp, 0f);
                a = a + ((aimed ? 1f : 0f) - a) * Math.min(1f, 10f * dt);
                AIM_ANIM.put(wp, a);

                // Position EINMAL auf ganze Pixel runden -- sonst zittert alles,
                // weil sie sich bei jeder Bewegung um Bruchteile aendert.
                int cxI = Math.round((float) s.x);
                int cyI = Math.round((float) s.y);

                // Ring: klein im Ruhezustand, waechst beim Anvisieren.
                int rBase = Math.max(2, mod.dotSize.getInt() / 2);
                int r = rBase + Math.round(a * 4f);

                // Deckkraft: im Ruhezustand die eingestellte, beim Anvisieren
                // zur vollen hin aufblenden.
                float base = (float) mod.markerOpacity.get();
                float alpha = base + (1f - base) * a;

                drawRing(ctx, cxI, cyI, r, fadeA(color, alpha), mod, a);

                // Buchstabe immer sichtbar -- neben dem Ring, damit er auch bei
                // sehr kleinen Ringen lesbar bleibt und nichts verdeckt.
                if (mod.showLetter.get()) {
                    String letter = initial(wp.name);
                    // Buchstabe traegt IMMER die Farbe des Markers -- so gehoert
                    // beides sichtbar zusammen und man erkennt den Marker auch,
                    // wenn der Ring gerade von etwas verdeckt wird.
                    int lc = fadeA(color, alpha);
                    int lx = cxI + r + 3;
                    int ly = cyI - 3;
                    pushScale(ctx, lx, ly, 0.8f);
                    // Zarter Schatten fuer Lesbarkeit auf hellem Untergrund.
                    ctx.drawText(client.textRenderer, Text.literal(letter),
                            lx + 1, ly + 1, fadeA(0xFF000000, alpha * 0.75f), false);
                    ctx.drawText(client.textRenderer, Text.literal(letter),
                            lx, ly, lc, false);
                    popScale(ctx);
                }

                // --- Beim Anvisieren: voller Name und Entfernung darunter ---
                if (a > 0.02f && mod.labels.get()) {
                    String label = wp.name + "  " + (int) dist + "m";
                    int ty = cyI + r + 5 + Math.round((1f - a) * 3f);
                    pushScale(ctx, cxI, ty, 0.8f);
                    int tw = client.textRenderer.getWidth(label);
                    int tx = cxI - tw / 2;
                    ctx.drawText(client.textRenderer, Text.literal(label),
                            tx + 1, ty + 1, fadeA(0xFF000000, a * 0.7f), false);
                    ctx.drawText(client.textRenderer, Text.literal(label),
                            tx, ty, fadeA(0xFFFFFFFF, a), false);
                    popScale(ctx);
                }
            } else if (mod.edgeArrows.get()) {
                drawEdgeArrow(ctx, client, s, w, h, color, wp.name, (int) dist);
            }
        }

    }

    /**
     * Zeichnet den Punkt als weiche Raute statt als hartes Viereck.
     *
     * Die Form entsteht aus waagerechten Streifen, deren Breite zu den Raendern
     * hin abnimmt -- das wirkt gerundet, ohne dass dafuer eine Textur noetig
     * waere. Aussen liegt ein zarter Rand, damit der Punkt auf jedem Untergrund
     * ablesbar bleibt.
     */
    /**
     * Zeichnet einen hohlen Ring.
     *
     * Nur der Rand ist gefaerbt, die Mitte bleibt frei -- dadurch verdeckt der
     * Marker nichts und wirkt deutlich leichter als ein gefuellter Punkt.
     *
     * Der Ring entsteht aus waagerechten Streifen: je Zeile werden nur die
     * beiden Randstuecke gesetzt, deren Breite sich aus dem Kreisradius ergibt.
     * Innen wird nichts gezeichnet.
     */
    private static void drawRing(DrawContext ctx, int cx, int cy, int r,
                                 int color, com.vortex.client.waypoint.WaypointSettings mod,
                                 float aim) {
        int thickness = Math.max(1, mod.borderWidth.getInt());
        int inner = Math.max(1, r - thickness);

        for (int dy = -r; dy <= r; dy++) {
            int outerHalf = rowHalf(dy, r);
            if (outerHalf <= 0) continue;
            int innerHalf = rowHalf(dy, inner);

            int y = cy + dy;
            if (innerHalf <= 0) {
                // Oben und unten: durchgehender Streifen (Ringschluss).
                ctx.fill(cx - outerHalf, y, cx + outerHalf, y + 1, color);
            } else {
                // Dazwischen: nur die beiden Seitenstuecke.
                ctx.fill(cx - outerHalf, y, cx - innerHalf, y + 1, color);
                ctx.fill(cx + innerHalf, y, cx + outerHalf, y + 1, color);
            }
        }

        // Beim Anvisieren ein zweiter, zarter Ring aussen herum.
        if (aim > 0.02f) {
            int rr = r + 3;
            int glow = fadeA(color, aim * 0.45f);
            for (int dy = -rr; dy <= rr; dy++) {
                int half = rowHalf(dy, rr);
                if (half <= 0) continue;
                int y = cy + dy;
                ctx.fill(cx - half, y, cx - half + 1, y + 1, glow);
                ctx.fill(cx + half - 1, y, cx + half, y + 1, glow);
            }
        }
    }

    /** Halbe Breite einer Kreiszeile im Abstand dy vom Mittelpunkt. */
    private static int rowHalf(int dy, int radius) {
        double t = (double) dy / radius;
        double v = 1.0 - t * t;
        if (v <= 0) return 0;
        return (int) Math.round(Math.sqrt(v) * radius);
    }

    /** Zeichnen um einen Punkt herum verkleinern. */
    private static void pushScale(DrawContext ctx, float ax, float ay, float scale) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ax, ay);
        m.scale(scale, scale);
        m.translate(-ax, -ay);
    }

    private static void popScale(DrawContext ctx) {
        ctx.getMatrices().popMatrix();
    }

    /** Farbe mit Deckkraft skalieren (fuer weiches Ein- und Ausblenden). */
    private static int fadeA(int argb, float f) {
        if (f >= 1f) return argb;
        if (f <= 0f) return argb & 0x00FFFFFF;
        // Vorhandene Deckkraft wird MULTIPLIZIERT, nicht ersetzt -- sonst wuerde
        // ein bereits halbdurchsichtiger Farbwert wieder voll deckend werden.
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Anfangsbuchstabe fuer den Punkt (Ziffern und Zeichen gehen auch). */
    private static String initial(String name) {
        if (name == null || name.isBlank()) return "?";
        return name.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    /** Ist die Farbe hell? Dann dunkle Schrift darauf, sonst helle. */
    private static boolean isBright(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        // Wahrgenommene Helligkeit -- Gruen wiegt am schwersten.
        return (r * 299 + g * 587 + b * 114) / 1000 > 140;
    }

    /**
     * Pfeil am Bildschirmrand fuer einen Marker ausserhalb des Blickfelds.
     *
     * Die Richtung ergibt sich aus der Bildschirmposition relativ zur Mitte.
     * Liegt der Punkt hinter der Kamera, wird die Richtung gespiegelt -- sonst
     * zeigte der Pfeil genau falsch herum.
     */
    private static void drawEdgeArrow(DrawContext ctx, MinecraftClient client,
                                      Screen s, int w, int h, int color,
                                      String name, int dist) {
        double cx = w / 2.0, cy = h / 2.0;
        double dx = s.x - cx;
        double dy = s.y - cy;
        if (s.depth <= 0.05) {
            // Hinter der Kamera: Richtung umkehren und nach unten schieben.
            dx = -dx;
            dy = Math.abs(dy) + 1;
        }
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-3) return;
        dx /= len;
        dy /= len;

        // Auf einen Rahmen mit etwas Abstand zum Rand legen.
        double margin = 26;
        double maxX = cx - margin, maxY = cy - margin;
        double scale = Math.min(maxX / Math.abs(dx == 0 ? 1e-6 : dx),
                                maxY / Math.abs(dy == 0 ? 1e-6 : dy));
        int px = (int) (cx + dx * scale);
        int py = (int) (cy + dy * scale);

        // Kleines Dreieck aus waagerechten Streifen, in Richtung gedreht.
        for (int i = 0; i < 6; i++) {
            int half = 6 - i;
            int ox = (int) (dx * i * 1.6);
            int oy = (int) (dy * i * 1.6);
            ctx.fill(px + ox - half, py + oy - 1, px + ox + half, py + oy + 1, color);
        }

        String txt = name + " " + dist + "m";
        int tw = client.textRenderer.getWidth(txt);
        int tx = Math.max(2, Math.min(w - tw - 2, px - tw / 2));
        int ty = Math.max(2, Math.min(h - 12, py + 8));
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(txt), tx, ty, color);
    }
}
