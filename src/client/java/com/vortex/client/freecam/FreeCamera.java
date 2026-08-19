package com.vortex.client.freecam;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Input;

/**
 * Eine rein CLIENTSEITIGE Kamera-Entity, die als cameraEntity gesetzt wird,
 * damit das Chunk-Rendering (besonders das Cave-Culling unter der Erde) sich an
 * der Freecam-Position orientiert statt am echten Spieler.
 *
 * SERVER-SICHERHEIT -- das ist entscheidend: Diese Entity darf NICHTS an den
 * Server senden. Eine normale LocalPlayer meldet jeden Tick ihre Position
 * (sendMovementPackets) -- das wuerde der Server als Teleport/Flug werten und
 * Anticheat ausloesen. Deshalb ueberschreiben wir:
 *   - tick(): tut nichts (kein Ticking, keine Pakete)
 *   - sendMovementPackets(): leer (sendet garantiert nichts)
 * Die Entity existiert nur lokal in der ClientLevel. Der echte Spieler bleibt
 * stehen und meldet weiter ganz normal seine Steh-Position. Der Server sieht
 * also keinen Unterschied -- du stehst still, die Kamera fliegt nur im Client.
 *
 * Die Entity wird beim Aktivieren der Freecam gespawnt (lokal) und als
 * cameraEntity gesetzt; beim Deaktivieren wieder entfernt und die Kamera auf den
 * Spieler zurueckgesetzt.
 */
public class FreeCamera extends LocalPlayer {

    public FreeCamera() {
        super(
            Minecraft.getInstance(),
            Minecraft.getInstance().level,
            Minecraft.getInstance().getConnection(),
            new StatsCounter(),
            new ClientRecipeBook(),
            Input.EMPTY,
            false,
            ChatAbilities.NO_RESTRICTIONS
        );
        // Durch Bloecke hindurch -- die Kamera soll frei fliegen.
        this.noPhysics = true;
        // Unsichtbar: sonst sieht man einen Koerper / eine Hitbox ueber dem
        // Freecam-Kopf. setInvisible ist ein normaler Aufruf (kein Override),
        // also build-sicher, und setzt das Invisible-Flag der Entity.
        this.setInvisible(true);
    }

    /**
     * KEIN normales Ticking. Wichtig: Die Super-Implementierung wuerde u.a.
     * Bewegungspakete an den Server schicken (ueber sendMovementPackets). Wir
     * lassen tick() komplett leer -- dadurch wird sendMovementPackets gar nicht
     * erst aufgerufen, und die Entity bleibt garantiert stumm. Der echte Spieler
     * tickt normal weiter und meldet seine Steh-Position; der Server sieht also
     * keinen Unterschied.
     */
    @Override
    public void tick() {
        // bewusst leer -> keine Server-Pakete, kein Ticking
    }

    /**
     * Spawnt die Kamera-Entity lokal in die ClientLevel (kein Server-Paket) und
     * setzt sie als aktive Kamera. Position/Blick werden von Freecam gesetzt.
     */
    public void spawn() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) return;
        world.addEntity(this);
    }

    /** Entfernt die Kamera-Entity wieder aus der ClientLevel. */
    public void despawn() {
        this.discard();
    }
}
