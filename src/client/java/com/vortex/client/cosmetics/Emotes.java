package com.vortex.client.cosmetics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emotes: kurze Animationen des Spielermodells (Winken, Tanzen ...).
 *
 * Die Pose wird nach Minecrafts eigener Animation gesetzt (EmoteMixin am Ende
 * von PlayerModel.setupAnim) und am Anfang/Ende weich ein- und ausgeblendet.
 * Wer losgeht, beendet sein Emote. Fuer dich selbst springt die Kamera
 * waehrenddessen in die Ansicht von vorne und danach zurueck.
 *
 * Andere Vortex-Spieler sehen es ueber den Freunde-Server (CosmeticsSync
 * schickt "emote.play", Social reicht das Ereignis "emote" hierher).
 */
public final class Emotes {
    private Emotes() {}

    @FunctionalInterface
    interface Pose { void setzen(PlayerModel m, float t, float b); }

    public record Emote(String id, String name, float dauer, Pose pose) {}

    private record Laufend(Emote emote, long seit) {}

    private static final Map<String, Emote> ALLE = new LinkedHashMap<>();
    private static final Map<UUID, Laufend> AKTIV = new ConcurrentHashMap<>();
    private static CameraType kameraVorher;

    public static Map<String, Emote> alle() { return ALLE; }

    private static void neu(String id, String name, float dauer, Pose p) { ALLE.put(id, new Emote(id, name, dauer, p)); }

    /**
     * Wert weich Richtung Ziel (b = 0..1 Einblenden). Achtung bei erhobenen Armen:
     * Minecraft dreht erst um X, dann um Z -- zeigt der Arm nach oben, kehrt sich
     * die Wirkung von zRot um (positiv = zum Kopf hin).
     */
    private static void zu(ModelPart part, float b, float x, float y, float z) {
        part.xRot += (x - part.xRot) * b;
        part.yRot += (y - part.yRot) * b;
        part.zRot += (z - part.zRot) * b;
    }

    private static float sin(float v) { return (float) Math.sin(v); }

    static {
        neu("wave", "Wave", 2.6f, (m, t, b) -> {
            zu(m.rightArm, b, -2.75f, 0f, -0.3f + sin(t * 11f) * 0.38f);
            zu(m.head, b, -0.1f, -0.15f, 0f);
        });
        neu("clap", "Clap", 3.0f, (m, t, b) -> {
            float auf = 0.18f + Math.abs(sin(t * 9f)) * 0.38f;
            zu(m.rightArm, b, -1.25f, -auf, 0f);
            zu(m.leftArm, b, -1.25f, auf, 0f);
        });
        neu("cheer", "Cheer", 2.6f, (m, t, b) -> {
            float hops = Math.abs(sin(t * 8f)) * 0.25f;
            zu(m.rightArm, b, -2.9f, 0f, -0.35f - hops);
            zu(m.leftArm, b, -2.9f, 0f, 0.35f + hops);
            zu(m.head, b, -0.3f, 0f, 0f);
        });
        neu("dance", "Dance", 8.0f, (m, t, b) -> {
            float s = sin(t * 6f);
            zu(m.rightArm, b, -1.6f + s * 0.9f, 0f, 0.4f);
            zu(m.leftArm, b, -1.6f - s * 0.9f, 0f, -0.4f);
            zu(m.rightLeg, b, s * 0.35f, 0f, 0f);
            zu(m.leftLeg, b, -s * 0.35f, 0f, 0f);
            zu(m.body, b, 0f, s * 0.25f, 0f);
            zu(m.head, b, Math.abs(s) * 0.25f, s * 0.3f, 0f);
        });
        neu("dab", "Dab", 2.2f, (m, t, b) -> {
            zu(m.head, b, 0.6f, -0.7f, 0f);
            zu(m.rightArm, b, -1.9f, -0.8f, 1.1f);
            zu(m.leftArm, b, -1.4f, 0.9f, -1.2f);
        });
        neu("salute", "Salute", 2.6f, (m, t, b) -> {
            zu(m.rightArm, b, -2.65f, -0.45f, 0.6f);
            zu(m.head, b, -0.05f, 0f, 0f);
        });
        neu("facepalm", "Facepalm", 2.6f, (m, t, b) -> {
            zu(m.rightArm, b, -2.1f, -0.3f, 0.55f);
            zu(m.head, b, 0.45f, -0.1f, 0.12f * sin(t * 4f));
        });
        neu("point", "Point", 2.2f, (m, t, b) -> {
            zu(m.rightArm, b, -1.57f, -0.1f, 0f);
            zu(m.head, b, 0f, -0.1f, 0f);
        });
        neu("shrug", "Shrug", 1.8f, (m, t, b) -> {
            zu(m.rightArm, b, -0.6f, 0.2f, 0.75f);
            zu(m.leftArm, b, -0.6f, -0.2f, -0.75f);
            zu(m.head, b, 0f, 0f, 0.18f);
        });
        neu("think", "Think", 3.0f, (m, t, b) -> {
            zu(m.rightArm, b, -2.0f, -0.5f, 0.35f);
            zu(m.leftArm, b, -0.8f, 0.7f, 0f);
            zu(m.head, b, 0.25f, 0.2f, 0f);
        });
        neu("tpose", "T-Pose", 3.0f, (m, t, b) -> {
            zu(m.rightArm, b, 0f, 0f, 1.57f);
            zu(m.leftArm, b, 0f, 0f, -1.57f);
            zu(m.head, b, 0f, 0f, 0f);
        });
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable t) { com.vortex.client.core.Errors.report("Emotes", t); }
        });
    }

    /** Eigenes Emote starten (Rad, Cosmetics-Menue) -- und an die anderen schicken. */
    public static void spielen(String id) {
        Minecraft mc = Minecraft.getInstance();
        Emote e = ALLE.get(id);
        UUID ich = mc.player != null ? mc.player.getUUID() : mc.getGameProfile().id();
        if (e == null || ich == null) return;
        AKTIV.put(ich, new Laufend(e, System.currentTimeMillis()));
        if (mc.player != null && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            kameraVorher = CameraType.FIRST_PERSON;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        CosmeticsSync.emoteGespielt(id);
    }

    /** Emote eines anderen Spielers (vom Freunde-Server). */
    public static void fremd(UUID spieler, String id) {
        Emote e = ALLE.get(id);
        if (e != null) AKTIV.put(spieler, new Laufend(e, System.currentTimeMillis()));
    }

    public static boolean laeuftBeiMir() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && AKTIV.containsKey(mc.player.getUUID());
    }

    private static void tick(Minecraft mc) {
        long jetzt = System.currentTimeMillis();
        AKTIV.entrySet().removeIf(en -> jetzt - en.getValue().seit() > en.getValue().emote().dauer() * 1000f);
        if (mc.player == null) return;
        // Losgehen beendet das eigene Emote
        if (AKTIV.containsKey(mc.player.getUUID()) && jetzt - AKTIV.get(mc.player.getUUID()).seit() > 300
                && mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.004) {
            AKTIV.remove(mc.player.getUUID());
        }
        // Kamera zurueck, sobald das eigene Emote vorbei ist
        if (kameraVorher != null && !AKTIV.containsKey(mc.player.getUUID())) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(kameraVorher);
            kameraVorher = null;
        }
    }

    /** Aus EmoteMixin: Pose fuer diesen Renderzustand setzen, falls ein Emote laeuft. */
    public static void anwenden(PlayerModel m, AvatarRenderState s) {
        UUID wer = spielerVon(s.id);
        if (wer == null) return;
        Laufend l = AKTIV.get(wer);
        if (l == null) return;
        // Andere: wer laeuft, emotet nicht (sein Emote endet bei ihm ohnehin)
        if (s.walkAnimationSpeed > 0.25f && s.id != TitelFigur.ID) return;
        float t = (System.currentTimeMillis() - l.seit()) / 1000f;
        float b = Math.max(0f, Math.min(1f, Math.min(t / 0.22f, (l.emote().dauer() - t) / 0.3f)));
        if (b <= 0f) return;
        l.emote().pose().setzen(m, t, b);
    }

    private static UUID spielerVon(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (id == TitelFigur.ID) return mc.getGameProfile().id();
        if (mc.level == null) return null;
        Entity e = mc.level.getEntity(id);
        return e instanceof Player p ? p.getUUID() : null;
    }
}
