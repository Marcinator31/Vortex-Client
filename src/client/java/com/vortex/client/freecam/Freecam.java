package com.vortex.client.freecam;

import com.mojang.blaze3d.platform.InputConstants;
import com.vortex.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Verwaltet den Freecam-Zustand: ob aktiv, die freie Kamera-Position und die
 * Bewegung per WASD/Leertaste/Shift.
 *
 * WICHTIG fuer fluessige Bewegung: Die Position wird pro RENDER-FRAME aktualisiert
 * (nicht pro Tick), mit Delta-Zeit-Skalierung. So ist die Bewegung bei jeder
 * Framerate gleich schnell und ruckelt nicht. Der CameraMixin ruft updateFrame()
 * jeden Frame auf.
 *
 * Die Rotation kommt aus der normalen Spieler-Blickrichtung (die Maus dreht also
 * die Kamera), die Position ist unabhaengig -- der Spieler bleibt stehen.
 */
public final class Freecam {

    private static boolean active = false;
    private static double x, y, z;          // aktuelle Freecam-Position
    private static Object freecamWelt = null, freecamSpieler = null;
    private static double velX, velY, velZ; // Geschwindigkeit (Bloecke pro Sekunde)

    // Die clientseitige Kamera-Entity (Anker fuers Chunk-Rendering, damit auch
    // unter der Erde korrekt gerendert wird). Stumm -- sendet nichts an Server.
    private static FreeCamera cameraEntity = null;

    // Eigene Blickrichtung der Freecam (unabhaengig vom Spieler).
    private static float yaw = 0f;
    private static float pitch = 0f;

    // Geschwindigkeit in Bloecken pro Sekunde (nicht pro Tick!).
    private static final double SPEED = 10.0;
    private static final double SPRINT_MULT = 3.0;
    // Reibung pro Sekunde: wie stark die Geschwindigkeit abklingt, wenn keine
    // Taste gedrueckt ist. Hoeher = laenger gleiten. Wird mit Delta skaliert.
    private static final double DAMPING_PER_SEC = 0.0025; // (Faktor^Sekunde)

    private static long lastFrameNano = 0L;

    /** Mausrad-Faktor auf die eingestellte Geschwindigkeit (bleibt fuer die Sitzung). */
    private static double radFaktor = 1.0;
    /** War der Spieler beim Einschalten am Schleichen? Dann bleibt er es. */
    private static boolean schleichenBeimStart = false;
    /** Leben + Absorption im letzten Tick -- zum Erkennen von Schaden. */
    private static float letztesLeben = -1f;


    private Freecam() {}

    public static boolean isActive() {
        return active;
    }

    public static Vec3 getPos() {
        return new Vec3(x, y, z);
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    /**
     * Dreht die Freecam-Blickrichtung (von der Maus aufgerufen). cursorDeltaX/Y
     * kommen aus changeLookDirection schon sensitivity-skaliert; Vanilla
     * multipliziert danach intern mit 0.15. Genau diesen Faktor wenden wir hier
     * an, damit die Freecam-Empfindlichkeit der normalen Spiel-Sensitivitaet
     * entspricht.
     */
    public static void addRotation(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) (cursorDeltaX * 0.15);
        pitch += (float) (cursorDeltaY * 0.15);
        if (pitch > 90f) pitch = 90f;
        if (pitch < -90f) pitch = -90f;
        yaw %= 360f;
    }

    /** Schaltet die Freecam an/aus. Beim Anschalten startet sie an der Spielerposition. */
    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        active = !active;
        if (active) {
            Vec3 eye = mc.player.getEyePosition();
            x = eye.x; y = eye.y; z = eye.z;
            velX = velY = velZ = 0;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            lastFrameNano = System.nanoTime();
            schleichenBeimStart = mc.player.isShiftKeyDown();
            letztesLeben = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            // Kamera-Entity spawnen und als aktive Kamera setzen, damit das
            // Rendering (inkl. Cave-Culling) der Freecam folgt.
            spawnCameraEntity(mc);
        } else {
            removeCameraEntity(mc);
        }
    }

    /** Erstellt die stumme Kamera-Entity und macht sie zur aktiven Kamera. */
    private static void spawnCameraEntity(Minecraft mc) {
        // Eine eigene Kamera-Entity wird aus ZWEI Gruenden gebraucht:
        //
        //  - "Render Anchor": besseres Rendern unter der Erde
        //  - "Show Player":   Minecraft zeichnet die Kamera-Entity in der
        //                     Ich-Perspektive NIE. Bleibt der Spieler die
        //                     Kamera, sieht man sich selbst nicht -- egal was
        //                     eingestellt ist. Erst mit eigener Kamera-Entity
        //                     ist der Spieler eine gewoehnliche Entity und
        //                     wird gezeichnet.
        //
        // Genau daran scheiterte "Show Player" vorher: die Einstellung wurde
        // ausgewertet, aber der Spieler war die Kamera und damit ohnehin
        // unsichtbar.
        // Seit 4.6.1 NUR noch fuer "Render Anchor". "Show Player" braucht
        // keine eigene Kamera-Entity mehr (CameraMixin: detached) -- der
        // Spieler bleibt Kamera und schickt weiter seine Pakete.
        boolean brauchtKamera = schalter("Render Anchor", false);
        if (!brauchtKamera) {
            cameraEntity = null;
            return;
        }
        try {
            if (mc.level == null || mc.getConnection() == null) return;
            cameraEntity = new FreeCamera();
            cameraEntity.snapTo(x, y, z, yaw, pitch);
            cameraEntity.spawn();
            mc.setCameraEntity(cameraEntity);
        } catch (Throwable t) {
            // Falls das Spawnen fehlschlaegt, laeuft die Freecam trotzdem --
            // nur ohne den verbesserten Unter-Erde-Render.
            cameraEntity = null;
        }
    }

    /** Entfernt die Kamera-Entity und setzt die Kamera zurueck auf den Spieler. */
    private static void removeCameraEntity(Minecraft mc) {
        try {
            mc.setCameraEntity(mc.player);
            if (cameraEntity != null) {
                cameraEntity.despawn();
            }
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("Freecam", pvpErr);
            } finally {
            cameraEntity = null;
        }
    }

    public static void disable() {
        if (!active) return;
        active = false;
        removeCameraEntity(Minecraft.getInstance());
    }

    /**
     * Freecam beenden UND das Modul ausschalten.
     *
     * Nur disable() reichte nicht: das Modul blieb an, und der Client schaltete
     * die Kamera im naechsten Tick wieder ein (VortexClientMod gleicht beide ab).
     * Nach dem Tod lief das jeden Tick im Kreis.
     */
    public static void beenden(String grund) {
        disable();
        try {
            com.vortex.client.module.Module m = module();
            if (m != null && m.isEnabled()) m.setEnabled(false);
        } catch (Throwable ignored) { }
        Minecraft mc = Minecraft.getInstance();
        if (grund != null && mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7d[Freecam] \u00a7f" + grund));
        }
    }

    /** Freecam: Verdeckungsberechnung aus (FreecamCullMixin). */
    public static boolean ohneCulling() {
        return active && schalter("No Culling", true);
    }

    /** Freecam: kein Nebel (MixinFogRenderer). */
    public static boolean ohneNebel() {
        return active && schalter("No Fog", true);
    }

    /** Freecam: keine Bildschirm-Overlays des Spielers (Kuerbis, Feuer, Wasser, Block im Kopf ...). */
    public static boolean ohneOverlays() {
        return active && schalter("Hide Overlays", true);
    }

    /** Fuer die Eingabe-Mixins: soll der Spieler waehrend der Freecam schleichen? */
    public static boolean schleichen() {
        return active && schleichenBeimStart && schalter("Keep Sneaking", true);
    }

    /** Aufhellung in der Freecam (GammaMixin). */
    public static boolean hell() {
        return active && schalter("Fullbright", true);
    }

    /** Aktuelle Fluggeschwindigkeit in Bloecken pro Sekunde (ohne Sprint). */
    public static double tempo() {
        return zahl("Speed", SPEED) * radFaktor;
    }

    /**
     * Mausrad in der Freecam: Geschwindigkeit aendern statt Hotbar-Slot.
     * @return true, wenn das Rad verbraucht wurde
     */
    public static boolean onScroll(double amount) {
        if (!active || amount == 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || !schalter("Scroll Changes Speed", true)) return false;
        radFaktor *= Math.pow(1.25, Math.signum(amount));
        double basis = Math.max(0.1, zahl("Speed", SPEED));
        // Ergebnis zwischen 0,5 und 200 Bloecken pro Sekunde.
        radFaktor = Math.max(0.5 / basis, Math.min(200.0 / basis, radFaktor));
        infoBis = System.currentTimeMillis() + 1500;
        return true;
    }

    /** Bis wann die Info-Zeile hervorgehoben wird (nach einer Aenderung). */
    private static long infoBis = 0;

    /**
     * Sicherheitsnetz gegen "Spieler haengt fest".
     *
     * Wenn die Freecam AUS ist, muss der echte Spieler die aktive Kamera sein.
     * Bleibt aus irgendeinem Grund (Fehler beim Beenden, Weltwechsel, Tod) eine
     * andere Kamera-Entity gesetzt, gilt der Spieler intern nicht als "Kamera" --
     * daran haengt u.a. das Senden der Bewegungspakete, und man steht fest.
     * Dieser Tick-Check setzt das automatisch zurueck.
     */
    public static void registerSafety() {
        try {
            net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                    net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MISC_OVERLAYS,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("vortexclient", "freecam_info"),
                    (ctx, tick) -> info(ctx));
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("Freecam.hud", pvpErr);
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
                .END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;

            // Tod, Respawn oder Dimensionswechsel: die gesperrte Position und
            // die Kamera-Entity gehoeren zur alten Welt -- vorher wurde der
            // Spieler nach dem Respawn an die alte Stelle zurueckgezogen.
            if (active) {
                if (freecamWelt == null) { freecamWelt = mc.level; freecamSpieler = mc.player; }
                if (mc.level != freecamWelt || mc.player != freecamSpieler || mc.player.isDeadOrDying()) {
                    freecamWelt = null;
                    freecamSpieler = null;
                    beenden(mc.player.isDeadOrDying() ? "Off -- you died." : null);
                    return;
                }
                // Schaden: sofort zurueck in den eigenen Koerper. Wer in der
                // Freecam angegriffen wird, merkt es sonst erst, wenn es zu
                // spaet ist.
                float leben = mc.player.getHealth() + mc.player.getAbsorptionAmount();
                if (letztesLeben >= 0 && leben < letztesLeben - 0.01f && schalter("Disable On Damage", true)) {
                    letztesLeben = -1f;
                    beenden("Off -- you took damage.");
                    return;
                }
                letztesLeben = leben;
            } else {
                freecamWelt = null;
                freecamSpieler = null;
            }

            // KEIN Festhalten des Spielers (seit 4.15). Bis 4.14 wurde er in der
            // Freecam waagerecht eingefroren -- ein Sprint-Sprung oder Elytra-Flug
            // stoppte mitten in der Luft. Das sieht kein echter Spieler so, und
            // Anti-Cheats schlagen an. Jetzt werden nur die Tasten neutralisiert
            // (KeyboardInputMixin): Schwung, Sprung und Gleitflug laufen ganz
            // normal aus -- wie wenn man die Tasten loslaesst. Gezeichnet wird
            // der Spieler dort, wo er wirklich ist.
            if (active) return;

            // Freecam aus -> der Spieler muss wieder die aktive Kamera sein.
            if (mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
        });
    }

    /**
     * Pro RENDER-FRAME aufrufen (aus dem CameraMixin): liest Eingaben, bewegt die
     * Freecam mit Delta-Zeit. So ist die Bewegung fluessig und framerate-unabhaengig.
     */
    public static void updateFrame() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { active = false; return; }

        // Delta-Zeit seit dem letzten Frame (in Sekunden), begrenzt gegen Spruenge.
        long now = System.nanoTime();
        double dt = (now - lastFrameNano) / 1_000_000_000.0;
        lastFrameNano = now;
        if (dt <= 0) return;
        if (dt > 0.1) dt = 0.1; // bei Hängern nicht springen

        // Kamera-Entity nachziehen, wenn sich eine Einstellung waehrend der
        // Freecam aendert.
        //
        // spawnCameraEntity laeuft sonst nur beim Einschalten -- wer "Show
        // Player" mitten im Flug umlegt, saehe bis zum naechsten Ein- und
        // Ausschalten keine Wirkung.
        //
        // Nur bei ECHTER Aenderung handeln: sonst wuerde die Entity in jedem
        // Bild neu erzeugt.
        boolean brauchtJetzt = schalter("Render Anchor", false);
        if (brauchtJetzt && cameraEntity == null) {
            spawnCameraEntity(mc);
        } else if (!brauchtJetzt && cameraEntity != null) {
            removeCameraEntity(mc);
        }

        // Spieler mitdrehen, falls eingestellt.
        //
        // Standard ist AUS: der Koerper bleibt stehen, wie er stand, und nur
        // die Kamera dreht sich. Das ist der Sinn einer Freecam -- und auf
        // einem Server sieht niemand eine Drehung, die es nicht gibt.
        //
        // Nur die BLICKRICHTUNG wird uebertragen, nie die Position: bewegt
        // wird der Spieler in der Freecam grundsaetzlich nicht.
        if (schalter("Rotate Player", false) && mc.player != null) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            // yRotO/xRotO mitziehen, sonst interpoliert der Renderer zwischen
            // altem und neuem Winkel und der Kopf zappelt.
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            mc.player.setYHeadRot(yaw);
            mc.player.yHeadRotO = yaw;
        }

        // Bei offenem Bildschirm nur ausgleiten, keine neuen Eingaben.
        boolean inputAllowed = (mc.gui.screen() == null);

        double accel = 0;
        double fx = 0, fy = 0, fz = 0, rx = 0, rz = 0;
        boolean up = false, down = false;
        boolean anyMove = false;

        if (inputAllowed) {
            boolean fwd   = taste(mc, mc.options.keyUp, org.lwjgl.glfw.GLFW.GLFW_KEY_W);
            boolean back  = taste(mc, mc.options.keyDown, org.lwjgl.glfw.GLFW.GLFW_KEY_S);
            boolean left  = taste(mc, mc.options.keyLeft, org.lwjgl.glfw.GLFW.GLFW_KEY_A);
            boolean right = taste(mc, mc.options.keyRight, org.lwjgl.glfw.GLFW.GLFW_KEY_D);
            up    = taste(mc, mc.options.keyJump, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
            down  = taste(mc, mc.options.keyShift, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);

            // Freecam-eigene Blickrichtung nutzen (nicht die des Spielers).
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            // Vorwaerts-Vektor (inkl. Pitch fuers Hoch-/Runterfliegen beim Blicken).
            // "Horizontal Movement": wie Kreativ-Flug -- W bleibt auf der Hoehe,
            // hoch und runter nur mit Springen/Schleichen.
            if (schalter("Horizontal Movement", false)) {
                fx = -Math.sin(yawRad);
                fy = 0;
                fz = Math.cos(yawRad);
            } else {
                fx = -Math.sin(yawRad) * Math.cos(pitchRad);
                fy = -Math.sin(pitchRad);
                fz =  Math.cos(yawRad) * Math.cos(pitchRad);
            }
            // Rechts-Vektor (horizontal), korrekt = (-cos(yaw), -sin(yaw)).
            // Herleitung: rechts = forward um 90 Grad gedreht in der XZ-Ebene
            // -> (-fz, fx) bei pitch 0. So zeigt D wirklich nach rechts.
            rx = -Math.cos(yawRad);
            rz = -Math.sin(yawRad);

            // Geschwindigkeit aus den Modul-Einstellungen (in der GUI regelbar).
            double speed = zahl("Speed", SPEED) * radFaktor;
            double sprintFactor = zahl("Sprint Multiplier", SPRINT_MULT);
            if (mc.options.keySprint.isDown()) speed *= sprintFactor;
            accel = speed;

            // Zielgeschwindigkeit aus den gedrueckten Tasten bauen.
            double tvx = 0, tvy = 0, tvz = 0;
            if (fwd)   { tvx += fx; tvy += fy; tvz += fz; anyMove = true; }
            if (back)  { tvx -= fx; tvy -= fy; tvz -= fz; anyMove = true; }
            if (right) { tvx += rx; tvz += rz; anyMove = true; }
            if (left)  { tvx -= rx; tvz -= rz; anyMove = true; }
            if (up)    { tvy += 1; anyMove = true; }
            if (down)  { tvy -= 1; anyMove = true; }

            // Richtungsvektor normalisieren, damit Diagonale nicht schneller ist.
            double len = Math.sqrt(tvx*tvx + tvy*tvy + tvz*tvz);
            if (len > 0.0001) {
                tvx = tvx/len * speed;
                tvy = tvy/len * speed;
                tvz = tvz/len * speed;
            }
            if (schalter("Smooth Movement", false)) {
                // Weich anfahren und abbremsen (fuer Aufnahmen).
                double k = 1.0 - Math.exp(-4.0 * dt);
                velX += (tvx - velX) * k; velY += (tvy - velY) * k; velZ += (tvz - velZ) * k;
                anyMove = true;   // Ausgleiten uebernimmt hier die Glaettung
            } else {
                // Direkte Zielgeschwindigkeit (snappy, gut kontrollierbar).
                velX = tvx; velY = tvy; velZ = tvz;
            }
        }

        // Wenn keine Taste: ausgleiten ueber Daempfung (delta-skaliert).
        if (!anyMove) {
            double factor = Math.pow(DAMPING_PER_SEC, dt);
            velX *= factor; velY *= factor; velZ *= factor;
        }

        // Position bewegen: Geschwindigkeit (Bloecke/Sek) * Delta-Zeit.
        x += velX * dt;
        y += velY * dt;
        z += velZ * dt;

        // Kamera-Entity der Freecam-Position/-Blickrichtung folgen lassen, damit
        // das Chunk-Rendering (Cave-Culling) korrekt der Kamera folgt.
        if (cameraEntity != null) {
            cameraEntity.snapTo(x, y, z, yaw, pitch);
            // lastRender* fuer ruckelfreies Interpolieren auf die neue Position
            // setzen (verifizierte Yarn-Feldnamen).
            cameraEntity.xOld = x;
            cameraEntity.yOld = y;
            cameraEntity.zOld = z;
        }
    }

    /**
     * Kleine Zeile oben in der Mitte: Tempo und Abstand zum eigenen Koerper.
     * Der Abstand zaehlt: nur Chunks in Sichtweite des SPIELERS werden
     * geladen -- fliegt man zu weit, endet die Welt.
     */
    private static void info(net.minecraft.client.gui.GuiGraphicsExtractor ctx) {
        if (!active || !schalter("Show Info", true)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return;
        double dx = x - mc.player.getX(), dy = y - mc.player.getEyeY(), dz = z - mc.player.getZ();
        int abstand = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
        String t = String.format(java.util.Locale.ROOT, "Freecam  \u00a77%.1f b/s  \u00b7  %d m", tempo(), abstand);
        int grenze = mc.options.getEffectiveRenderDistance() * 16;
        if (abstand > grenze - 16) t += "  \u00a7c(edge of loaded world)";
        boolean hervor = System.currentTimeMillis() < infoBis;
        int w = mc.font.width(t);
        int sx = (ctx.guiWidth() - w) / 2;
        ctx.fill(sx - 4, 3, sx + w + 4, 15, hervor ? 0xC0301A4A : 0x80000000);
        ctx.text(mc.font, net.minecraft.network.chat.Component.literal(t), sx, 5, 0xFFE8DDFF, false);
    }

    private static boolean isDown(Minecraft mc, int key) {
        return InputConstants.isKeyDown(mc.getWindow(), key);
    }

    /**
     * Die Taste, die der Spieler in den Steuerungs-Optionen belegt hat (AZERTY,
     * ESDF ...), nicht fest W/A/S/D. Direkt am Fenster abgefragt, weil die
     * Freecam die Tastenbelegungen des Spielers absichtlich neutralisiert.
     */
    private static boolean taste(Minecraft mc, net.minecraft.client.KeyMapping km, int fallback) {
        try {
            InputConstants.Key key = InputConstants.getKey(km.saveString());
            if (key.getType() == InputConstants.Type.KEYSYM) return isDown(mc, key.getValue());
            if (key.getType() == InputConstants.Type.MOUSE) {
                return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
        } catch (Throwable ignored) { }
        return isDown(mc, fallback);
    }

    /** Liefert das Freecam-Modul (oder null). */
    /**
     * Das Freecam-Modul, falls geladen.
     *
     * Gesucht wird ueber den NAMEN, nicht die Klasse: das Modul liegt seit
     * dem Umzug im Addon. Waere der Klassenname hier fest verdrahtet, liesse
     * sich der Client ohne Addon nicht mehr uebersetzen. Ohne Addon bleibt
     * die Kamera einfach aus.
     */
    public static com.vortex.client.module.Module module() {
        try {
            for (com.vortex.client.module.Module m
                    : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                if ("Freecam".equals(m.getName())) return m;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    // --- Einstellungen ueber den Namen lesen ------------------------------
    // Die Kamera darf die Modulklasse nicht kennen. Fehlt das Addon, greift
    // jeweils der Standardwert.

    private static boolean schalter(String name, boolean standard) {
        try {
            com.vortex.client.module.Module m = module();
            if (m == null) return standard;
            for (com.vortex.client.core.setting.Setting st : m.getSettings()) {
                if (st.getName().equals(name)
                        && st instanceof com.vortex.client.core.setting.BooleanSetting b) {
                    return b.get();
                }
            }
        } catch (Throwable ignored) { }
        return standard;
    }

    private static double zahl(String name, double standard) {
        try {
            com.vortex.client.module.Module m = module();
            if (m == null) return standard;
            for (com.vortex.client.core.setting.Setting st : m.getSettings()) {
                if (st.getName().equals(name)
                        && st instanceof com.vortex.client.core.setting.NumberSetting n) {
                    return n.get();
                }
            }
        } catch (Throwable ignored) { }
        return standard;
    }

    /**
     * Soll der eigene Spieler in der Freecam gezeichnet werden?
     *
     * Wird vom EntityRenderShouldRenderMixin abgefragt. Standard AN: sonst
     * weiss man nicht, wo der eigene Koerper steht, und fliegt beim Beenden
     * ueberraschend dorthin zurueck.
     */
    public static boolean zeigeSpieler() {
        return schalter("Show Player", true);
    }

}
