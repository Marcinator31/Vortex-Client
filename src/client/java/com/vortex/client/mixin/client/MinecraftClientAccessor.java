package com.vortex.client.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Ein "Accessor-Mixin" -- ein zweiter Mixin-Typ, den du kennen solltest.
 *
 * Problem: MinecraftClient.session ist privat und final. Der Account-
 * Switcher muss es aber neu setzen koennen.
 *
 * Loesung: @Accessor erzeugt automatisch einen Setter dafuer. Statt
 * haesslicher Reflection castest du den Client einfach auf dieses
 * Interface und rufst pvpclient$setSession() auf:
 *
 *   ((MinecraftClientAccessor) MinecraftClient.getInstance())
 *       .pvpclient$setSession(neueSession);
 *
 * Hinweis: Der exakte Feldname ("session") kann je nach Mappings
 * leicht abweichen. Falls der Build meckert, in der MinecraftClient-
 * Klasse nach dem Session-Feld suchen und den Namen anpassen.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    /**
     * Setzt die Sitzung (den angemeldeten Account).
     *
     * WICHTIG -- der Name ist bewusst eindeutig gewaehlt:
     * Andere Mods legen ebenfalls Zugriffe auf MinecraftClient an. Der Essential-
     * Mod bringt eine Methode namens "setSession" mit; heisst unsere genauso,
     * verwirft Mixin unsere stillschweigend ("Method overwrite conflict ...
     * Skipping method") und der Account-Wechsel bleibt wirkungslos, ohne dass
     * eine Fehlermeldung erscheint.
     *
     * Auf welches Feld zugegriffen wird, steht ohnehin in der Annotation -- der
     * Methodenname ist frei waehlbar. Mit dem Praefix kann er mit keiner anderen
     * Mod kollidieren.
     */
    @Accessor("field_1726")
    void pvpclient$setSession(Session session);

    /**
     * Getter fuer das package-private worldRenderer-Feld. Wird vom Potato
     * Mode genutzt, um nach einer Render-Distanz-Aenderung reload() aufzurufen,
     * damit die Aenderung sofort sichtbar wird.
     */
    @Accessor("worldRenderer")
    WorldRenderer pvpclient$getWorldRenderer();

    /**
     * Setter fuer das crosshairTarget-Feld. Wird genutzt, damit in der Freecam
     * der Abbau/Angriff vom echten Spieler ausgeht statt von der Kamera: wir
     * berechnen das Ziel selbst vom Spieler und setzen es hier.
     */
    @Accessor("field_1765")
    void pvpclient$setCrosshairTarget(net.minecraft.util.hit.HitResult target);

    /**
     * Invoker fuer die private Methode doAttack() (method_1536). @Invoker ist das
     * Methoden-Gegenstueck zu @Accessor: es macht eine private/geschuetzte
     * Methode von aussen aufrufbar. Auto Hit nutzt das, um einen vollstaendigen
     * Vanilla-Angriff auszuloesen (Reichweite, Schaden, Swing, Pakete), ohne die
     * Logik nachbauen zu muessen.
     */
    @org.spongepowered.asm.mixin.gen.Invoker("method_1536")
    boolean pvpclient$invokeDoAttack();
}
