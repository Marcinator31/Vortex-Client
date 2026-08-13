package com.vortex.client.freecam;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.FreecamModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

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

    // Position des echten Spielers beim Einschalten der Freecam. Solange die
    // Freecam laeuft, wird der Spieler jeden Tick horizontal hierauf zurueck-
    // gesetzt -- egal welcher Code-Pfad ihn bewegen wollte.
    private static double lockX = 0, lockZ = 0;

    private Freecam() {}

    public static boolean isActive() {
        return active;
    }

    public static Vec3d getPos() {
        return new Vec3d(x, y, z);
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        active = !active;
        if (active) {
            Vec3d eye = mc.player.getEyePos();
            x = eye.x; y = eye.y; z = eye.z;
            velX = velY = velZ = 0;
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            lockX = mc.player.getX();
            lockZ = mc.player.getZ();
            lastFrameNano = System.nanoTime();
            // Kamera-Entity spawnen und als aktive Kamera setzen, damit das
            // Rendering (inkl. Cave-Culling) der Freecam folgt.
            spawnCameraEntity(mc);
        } else {
            removeCameraEntity(mc);
        }
    }

    /** Erstellt die stumme Kamera-Entity und macht sie zur aktiven Kamera. */
    private static void spawnCameraEntity(MinecraftClient mc) {
        // Nur wenn der Render-Anker ausdruecklich eingeschaltet ist. Sonst bleibt
        // der Spieler die Kamera -- das ist der sichere Weg (siehe FreecamModule).
        FreecamModule fm = module();
        if (fm == null || !fm.renderAnchor.get()) {
            cameraEntity = null;
            return;
        }
        try {
            if (mc.world == null || mc.getNetworkHandler() == null) return;
            cameraEntity = new FreeCamera();
            cameraEntity.refreshPositionAndAngles(x, y, z, yaw, pitch);
            cameraEntity.spawn();
            mc.setCameraEntity(cameraEntity);
        } catch (Throwable t) {
            // Falls das Spawnen fehlschlaegt, laeuft die Freecam trotzdem --
            // nur ohne den verbesserten Unter-Erde-Render.
            cameraEntity = null;
        }
    }

    /** Entfernt die Kamera-Entity und setzt die Kamera zurueck auf den Spieler. */
    private static void removeCameraEntity(MinecraftClient mc) {
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
        removeCameraEntity(MinecraftClient.getInstance());
    }

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
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
                .END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;

            if (active) {
                // Erste Sperre: der FreecamMoveMixin faengt den Bewegungsvektor
                // ab, BEVOR er angewendet wird. Zweite Sperre: die Tasten werden
                // neutralisiert. Hier kommt die dritte, als Sicherheitsnetz.

                // Waagerechten Restschwung abbauen.
                net.minecraft.util.math.Vec3d v = mc.player.getVelocity();
                if (v.x != 0.0 || v.z != 0.0) {
                    mc.player.setVelocity(0.0, v.y, 0.0);
                }

                // Ist der Spieler trotzdem abgedriftet, zurueckholen -- aber NUR
                // ab einem spuerbaren Abstand. Ein staendiges Zuruecksetzen bei
                // jedem Tick erzeugte frueher sichtbares Gleiten, weil die
                // Zwischenbilder die alte Position noch zeigten. Mit dieser
                // Schwelle passiert im Normalfall gar nichts.
                double dx = mc.player.getX() - lockX;
                double dz = mc.player.getZ() - lockZ;
                if ((dx * dx + dz * dz) > 0.02) {
                    mc.player.setPosition(lockX, mc.player.getY(), lockZ);
                }
                return;
            }

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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { active = false; return; }

        // Delta-Zeit seit dem letzten Frame (in Sekunden), begrenzt gegen Spruenge.
        long now = System.nanoTime();
        double dt = (now - lastFrameNano) / 1_000_000_000.0;
        lastFrameNano = now;
        if (dt <= 0) return;
        if (dt > 0.1) dt = 0.1; // bei Hängern nicht springen

        // Bei offenem Bildschirm nur ausgleiten, keine neuen Eingaben.
        boolean inputAllowed = (mc.currentScreen == null);

        double accel = 0;
        double fx = 0, fy = 0, fz = 0, rx = 0, rz = 0;
        boolean up = false, down = false;
        boolean anyMove = false;

        if (inputAllowed) {
            boolean fwd   = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_W);
            boolean back  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_S);
            boolean left  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_A);
            boolean right = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_D);
            up    = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
            down  = isDown(mc, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);

            // Freecam-eigene Blickrichtung nutzen (nicht die des Spielers).
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            // Vorwaerts-Vektor (inkl. Pitch fuers Hoch-/Runterfliegen beim Blicken).
            fx = -Math.sin(yawRad) * Math.cos(pitchRad);
            fy = -Math.sin(pitchRad);
            fz =  Math.cos(yawRad) * Math.cos(pitchRad);
            // Rechts-Vektor (horizontal), korrekt = (-cos(yaw), -sin(yaw)).
            // Herleitung: rechts = forward um 90 Grad gedreht in der XZ-Ebene
            // -> (-fz, fx) bei pitch 0. So zeigt D wirklich nach rechts.
            rx = -Math.cos(yawRad);
            rz = -Math.sin(yawRad);

            // Geschwindigkeit aus den Modul-Einstellungen (in der GUI regelbar).
            FreecamModule fm = module();
            double speed = (fm != null) ? fm.speed.get() : SPEED;
            double sprintFactor = (fm != null) ? fm.sprintMult.get() : SPRINT_MULT;
            if (mc.options.sprintKey.isPressed()) speed *= sprintFactor;
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
            // Direkte Zielgeschwindigkeit (snappy, gut kontrollierbar).
            velX = tvx; velY = tvy; velZ = tvz;
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
            cameraEntity.refreshPositionAndAngles(x, y, z, yaw, pitch);
            // lastRender* fuer ruckelfreies Interpolieren auf die neue Position
            // setzen (verifizierte Yarn-Feldnamen).
            cameraEntity.lastRenderX = x;
            cameraEntity.lastRenderY = y;
            cameraEntity.lastRenderZ = z;
        }
    }

    private static boolean isDown(MinecraftClient mc, int key) {
        return InputUtil.isKeyPressed(mc.getWindow(), key);
    }

    /** Liefert das Freecam-Modul (oder null). */
    public static FreecamModule module() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof FreecamModule f) return f;
        }
        return null;
    }
}
