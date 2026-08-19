package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.waypoint.WaypointSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.vortex.client.waypoint.WaypointManager;

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
     * Ohne das springt der Component hart an und aus, sobald das Fadenkreuz den
     * Punkt streift -- das wirkt unruhig. Hier waechst der Wert von 0 auf 1,
     * solange man drauf zielt, und faellt danach wieder ab.
     */
    private static final java.util.Map<Object, Float> AIM_ANIM = new java.util.HashMap<>();

    /** Markers belonging to the world we are in, rebuilt a few times a second. */
    private static final java.util.List<WaypointManager.Waypoint> visibleHere =
            new java.util.ArrayList<>();

    /** When that list was last rebuilt, and for which world. */
    private static long visibleBuilt = 0L;
    private static String visibleFor = "";

    /**
     * Per-waypoint text memo.
     *
     * Every marker rebuilt its letter (a trim + substring + toUpperCase) and
     * two to four Component objects on EVERY FRAME. With fifty markers at 150 fps
     * that is tens of thousands of allocations a second for strings that
     * change when you walk a whole metre.
     *
     * Keyed on the Waypoint object, which has identity semantics (plain class,
     * no equals override). The name is snapshotted because Waypoint.name is
     * mutable -- renaming a marker must invalidate its letter and label.
     */
    private static final class Memo {
        String name;
        Component letter;
        int dist = Integer.MIN_VALUE;
        Component label;   // "name  123m"  (two spaces, as before)
        Component edge;    // "name 123m"   (one space, as before)
        int labelW;   // pixel width of label, see updateDist
        int edgeW;
    }

    private static final java.util.Map<WaypointManager.Waypoint, Memo> MEMOS =
            new java.util.HashMap<>();

    /** The pending-area hint: constant text, width measured on first use. */
    private static final String AREA_HINT_STR = "Area: pick the second corner";
    private static final Component AREA_HINT = Component.literal(AREA_HINT_STR);
    private static int areaHintW = -1;

    /** The active block-group hint, rebuilt only when the group changes. */
    private static String groupHintFor = null;
    private static Component groupHint = null;
    private static int groupHintW = 0;

    /** The nearest-marker line, rebuilt only when its text would change. */
    private static String nearestFor = null;
    private static Component nearestText = null;
    private static int nearestW = 0;

    /** The memo for a waypoint, rebuilding the name-derived parts on rename. */
    private static Memo memo(WaypointManager.Waypoint wp) {
        Memo m = MEMOS.get(wp);
        if (m == null) {
            m = new Memo();
            MEMOS.put(wp, m);
        }
        if (!java.util.Objects.equals(m.name, wp.name)) {
            m.name = wp.name;
            m.letter = Component.literal(initial(wp.name));
            m.dist = Integer.MIN_VALUE; // force the distance texts to rebuild
        }
        return m;
    }

    /**
     * Distance-dependent texts, rebuilt only when the whole metre changes.
     *
     * The widths are cached alongside: they were measured per marker per frame,
     * and they only change when the string does. Measured with the String
     * overload (getWidth(String), method_1727) rather than passing the Component --
     * getWidth(StringVisitable) exists too, but the String one is the version
     * this codebase already uses everywhere, so no inheritance assumption.
     * Scaling happens via the matrix in pushScale, so a cached pixel width
     * stays correct at any marker scale.
     */
    private static void updateDist(net.minecraft.client.gui.Font tr,
                                   Memo m, WaypointManager.Waypoint wp, int d) {
        if (m.dist == d && m.label != null) return;
        m.dist = d;
        String labelStr = wp.name + "  " + d + "m";
        String edgeStr = wp.name + " " + d + "m";
        m.label = Component.literal(labelStr);
        m.edge = Component.literal(edgeStr);
        m.labelW = tr.width(labelStr);
        m.edgeW = tr.width(edgeStr);
    }
    private static long lastNano = 0L;

    public static void draw(GuiGraphicsExtractor ctx, Minecraft client) {
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
        if (client.player == null || client.level == null) return;
        if (client.font == null) return;
        if (!mod.labels.get() && !mod.edgeArrows.get()) return;

        // Position AND direction come from the render camera.
        //
        // Using the player's own values was only correct in first person. In
        // third person the camera sits behind the player, and in the front view
        // it even looks the opposite way — markers ended up shifted, or moved
        // the wrong way entirely when the view changed.
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        net.minecraft.world.phys.Vec3 camPos = EspRender.cameraOffset(client, tickDelta);

        float yaw, pitch;
        if (com.vortex.client.freecam.Freecam.isActive()) {
            yaw = com.vortex.client.freecam.Freecam.getYaw();
            pitch = com.vortex.client.freecam.Freecam.getPitch();
        } else {
            var camera = client.gameRenderer.mainCamera();
            if (camera != null) {
                yaw = camera.yRot();
                pitch = camera.xRot();
            } else {
                yaw = client.player.getYRot();
                pitch = client.player.getXRot();
            }
        }

        double fov = 70.0;
        try {
            Object v = client.options.fov().get();
            if (v instanceof Number n) fov = n.doubleValue();
        } catch (Throwable t) {
            // Standardwert behalten.
        }
        if (fov < 30.0 || fov > 150.0) fov = 70.0;

        // The zoom narrows the angle of view, and the markers have to follow.
        //
        // Without this they keep the positions they had at the normal angle
        // while the world underneath them pulls in -- so the further from the
        // centre a marker sits, the further it drifts from the place it marks.
        double zoom = com.vortex.client.hud.Zoom.factor();
        if (zoom > 1.001) {
            fov = fov / zoom;
        }

        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        double maxDist = mod.maxDistance.get();

        String dim = WaypointRenderer.currentWorldKey(client);

        // Hinweis, solange eine Bereichsauswahl laeuft oder eine Block-Gruppe
        // aktiv ist -- sonst vergisst man, dass die erste Ecke noch offen ist.
        if (com.vortex.client.waypoint.WaypointActions.hasPendingCorner()) {
            // Constant string. Width measured on first use, not at class init:
            // there is no TextRenderer yet when the class loads.
            if (areaHintW < 0) {
                areaHintW = client.font.width(AREA_HINT_STR);
            }
            ctx.text(client.font, AREA_HINT,
                    (w - areaHintW) / 2, h - 70, 0xFFFFD070);
        } else {
            String grp = com.vortex.client.waypoint.WaypointActions.activeGroupName();
            if (grp != null) {
                // Rebuilt only when the active group changes.
                if (!grp.equals(groupHintFor)) {
                    groupHintFor = grp;
                    String hint = "Block group: " + grp;
                    groupHint = Component.literal(hint);
                    groupHintW = client.font.width(hint);
                }
                ctx.text(client.font, groupHint,
                        (w - groupHintW) / 2, h - 70, 0x90FFFFFF, false);
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
                // Rebuilt only when the text actually changes -- the string
                // itself is the cache key, so a rename or a whole metre of
                // movement invalidates it and nothing else does.
                if (!line.equals(nearestFor)) {
                    nearestFor = line;
                    nearestText = Component.literal(line);
                    nearestW = client.font.width(line);
                }
                ctx.text(client.font, nearestText,
                        (w - nearestW) / 2, h - 58, near.color | 0xFF000000);
            }
        }

        // The world check is done a few times a second, not per frame.
        //
        // matches() takes the world key apart -- splitting strings, allocating
        // arrays -- and it was doing that for every marker on every frame. With
        // fifty markers at a hundred and fifty frames a second that is seven
        // thousand string splits a second to answer a question whose answer
        // changes when you change server.
        long nowMs = System.currentTimeMillis();
        if (nowMs - visibleBuilt > 250 || !dim.equals(visibleFor)) {
            visibleBuilt = nowMs;
            visibleFor = dim;
            visibleHere.clear();
            for (WaypointManager.Waypoint wp : WaypointManager.all()) {
                if (wp.visible && WaypointManager.matches(wp, dim)) {
                    visibleHere.add(wp);
                }
            }
            // Drop memos for markers that are gone or no longer here, so the
            // map stays the size of what is actually on screen.
            MEMOS.keySet().retainAll(new java.util.HashSet<>(visibleHere));
        }

        for (WaypointManager.Waypoint wp : visibleHere) {
            Memo memo = memo(wp);
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
                    // Buchstabe traegt IMMER die Farbe des Markers -- so gehoert
                    // beides sichtbar zusammen und man erkennt den Marker auch,
                    // wenn der Ring gerade von etwas verdeckt wird.
                    int lc = fadeA(color, alpha);
                    int lx = cxI + r + 3;
                    int ly = cyI - 3;
                    pushScale(ctx, lx, ly, 0.8f);
                    // Zarter Schatten fuer Lesbarkeit auf hellem Untergrund.
                    // One Component from the memo, used for both draws -- it was
                    // built twice per marker per frame for the same string.
                    ctx.text(client.font, memo.letter,
                            lx + 1, ly + 1, fadeA(0xFF000000, alpha * 0.75f), false);
                    ctx.text(client.font, memo.letter,
                            lx, ly, lc, false);
                    popScale(ctx);
                }

                // --- Beim Anvisieren: voller Name und Entfernung darunter ---
                if (a > 0.02f && mod.labels.get()) {
                    updateDist(client.font, memo, wp, (int) dist);
                    int ty = cyI + r + 5 + Math.round((1f - a) * 3f);
                    pushScale(ctx, cxI, ty, 0.8f);
                    int tx = cxI - memo.labelW / 2;
                    ctx.text(client.font, memo.label,
                            tx + 1, ty + 1, fadeA(0xFF000000, a * 0.7f), false);
                    ctx.text(client.font, memo.label,
                            tx, ty, fadeA(0xFFFFFFFF, a), false);
                    popScale(ctx);
                }
            } else if (mod.edgeArrows.get()) {
                updateDist(client.font, memo, wp, (int) dist);
                drawEdgeArrow(ctx, client, s, w, h, color, memo.edge, memo.edgeW);
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
    private static void drawRing(GuiGraphicsExtractor ctx, int cx, int cy, int r,
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
    private static void pushScale(GuiGraphicsExtractor ctx, float ax, float ay, float scale) {
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(ax, ay);
        m.scale(scale, scale);
        m.translate(-ax, -ay);
    }

    private static void popScale(GuiGraphicsExtractor ctx) {
        ctx.pose().popMatrix();
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
    private static void drawEdgeArrow(GuiGraphicsExtractor ctx, Minecraft client,
                                      Screen s, int w, int h, int color,
                                      Component txt, int tw) {
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

        // Width comes from the memo, measured when the text last changed.
        int tx = Math.max(2, Math.min(w - tw - 2, px - tw / 2));
        int ty = Math.max(2, Math.min(h - 12, py + 8));
        ctx.text(client.font, txt, tx, ty, color);
    }
}
