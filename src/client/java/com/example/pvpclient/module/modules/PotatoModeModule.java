package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ModeSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

/**
 * Potato Mode -- senkt mehrere Grafik-Optionen, um die FPS deutlich zu
 * erhoehen. Praktisch fuer schwache PCs oder grosse PvP-Szenen.
 *
 * Zwei Stufen ueber den "Staerke"-Schalter:
 *   "Ausgewogen" -> spuerbar schneller, sieht noch ok aus
 *   "Aggressiv"  -> maximale FPS, sieht haesslich aus
 *
 * Die Render-Distanz ist separat einstellbar (Setting "Render-Distanz").
 *
 * WICHTIG: Beim Aktivieren werden die aktuellen Werte GESPEICHERT und beim
 * Deaktivieren wieder hergestellt -- der Modus zerstoert also nicht dauerhaft
 * deine Grafik-Einstellungen.
 *
 * Es werden nur Optionen angefasst, die sich typsicher ueber SimpleOption
 * setzen lassen (Integer/Boolean/Double). Den GraphicsMode (Fast/Fancy) fassen
 * wir bewusst NICHT an, da dessen Handling in 1.21.11 umgebaut wurde.
 */
public class PotatoModeModule extends Module {

    public final ModeSetting strength =
        new ModeSetting("Staerke", 0, "Ausgewogen", "Aggressiv");
    public final NumberSetting renderDistance =
        new NumberSetting("Render-Distanz", 6, 2, 32, 1);

    // Gespeicherte Originalwerte (als Object, weil SimpleOption generisch ist).
    private boolean saved = false;
    private Object oViewDistance, oMaxFps, oMipmap, oEntityShadows,
                   oBobView, oAo, oEntityDist;

    public PotatoModeModule() {
        super("Potato Mode", Category.PERFORMANCE);
        addSetting(strength);
        addSetting(renderDistance);
    }

    @Override
    protected void onEnable() {
        GameOptions o = options();
        if (o == null) return;

        // Aktuelle Werte einmalig sichern (nur wenn noch nicht gesichert).
        if (!saved) {
            oViewDistance  = get(o.getViewDistance());
            oMaxFps        = get(o.getMaxFps());
            oMipmap        = get(o.getMipmapLevels());
            oEntityShadows = get(o.getEntityShadows());
            oBobView       = get(o.getBobView());
            oAo            = get(o.getAo());
            oEntityDist    = get(o.getEntityDistanceScaling());
            saved = true;
        }

        applyPotato(o);
    }

    @Override
    protected void onDisable() {
        restoreOriginals();
    }

    /**
     * Stellt die gesicherten Original-Grafikwerte wieder her (ohne den
     * Modul-Zustand zu aendern). Wird von onDisable() und beim Spiel-Beenden
     * aufgerufen, damit Minecraft nicht die Potato-Werte dauerhaft speichert.
     */
    public void restoreOriginals() {
        GameOptions o = options();
        if (o == null) return;

        if (saved) {
            set(o.getViewDistance(), oViewDistance);
            set(o.getMaxFps(), oMaxFps);
            set(o.getMipmapLevels(), oMipmap);
            set(o.getEntityShadows(), oEntityShadows);
            set(o.getBobView(), oBobView);
            set(o.getAo(), oAo);
            set(o.getEntityDistanceScaling(), oEntityDist);
            saved = false;
            // Welt neu laden, damit die wiederhergestellte Render-Distanz wirkt.
            reloadWorld();
        }
    }

    /** Wendet die Potato-Werte je nach Staerke an. */
    private void applyPotato(GameOptions o) {
        boolean aggressive = strength.is("Aggressiv");

        // Render-Distanz: vom Setting (Integer).
        set(o.getViewDistance(), renderDistance.getInt());

        // FPS-Limit hochsetzen, damit nichts kuenstlich bremst.
        // 260 entspricht in Vanilla "Unbegrenzt".
        set(o.getMaxFps(), aggressive ? 260 : 120);

        // Mipmaps aus (0) im aggressiven Modus, sonst niedrig (1).
        set(o.getMipmapLevels(), aggressive ? 0 : 1);

        // Schatten, View-Bobbing immer aus -- kosten Leistung, kein PvP-Nutzen.
        set(o.getEntityShadows(), false);
        set(o.getBobView(), false);

        // Smooth Lighting (AO): im aggressiven Modus aus, sonst an lassen.
        set(o.getAo(), !aggressive);

        // Entity-Distanz-Skalierung: weniger = Entities werden frueher
        // ausgeblendet (Double). Aggressiv 0.5, ausgewogen 0.75.
        set(o.getEntityDistanceScaling(), aggressive ? 0.5 : 0.75);

        // Welt neu laden, damit Render-Distanz & Smooth-Lighting sofort wirken.
        reloadWorld();
    }

    /**
     * Stoesst ein Neuzeichnen der Welt an, damit Aenderungen an Render-Distanz
     * und Smooth Lighting sofort sichtbar werden. Greift ueber den Accessor-
     * Mixin auf das package-private worldRenderer-Feld zu. Alles in try-catch:
     * schlaegt der Zugriff fehl, wirken die Aenderungen eben leicht verzoegert,
     * aber es crasht nichts.
     */
    private static void reloadWorld() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) return;
            var acc = (com.example.pvpclient.mixin.client.MinecraftClientAccessor) client;
            var wr = acc.pvpclient$getWorldRenderer();
            if (wr != null) {
                wr.reload();
            }
        } catch (Throwable ignored) {
            // Kein reload moeglich -> Aenderung wirkt verzoegert, kein Crash.
        }
    }

    private static GameOptions options() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : client.options;
    }

    /** Liest den aktuellen Wert einer Option (kann null sein). */
    private static Object get(SimpleOption<?> opt) {
        try {
            return opt.getValue();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Setzt einen Wert auf eine Option. Der unchecked-Cast ist noetig, weil
     * SimpleOption generisch ist; wir uebergeben aber immer den passenden Typ
     * (Integer/Boolean/Double) bzw. den vorher ausgelesenen Originalwert.
     */
    @SuppressWarnings("unchecked")
    private static void set(SimpleOption<?> opt, Object value) {
        if (value == null) return;
        try {
            ((SimpleOption<Object>) opt).setValue(value);
        } catch (Throwable ignored) {
            // Falscher Typ o.ae. -> Option ueberspringen, nie crashen.
        }
    }
}
