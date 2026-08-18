package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

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
 * Es werden nur Optionen angefasst, die sich typsicher ueber OptionInstance
 * setzen lassen (Integer/Boolean/Double, seit 2.28.0 auch CloudStatus als
 * Enum -- gegen die echten 1.21.11-Mappings verifiziert). Den GraphicsMode
 * (Fast/Fancy) fassen wir weiterhin bewusst NICHT an, da dessen Handling in
 * 1.21.11 umgebaut wurde.
 */
public class PotatoModeModule extends Module {

    public final ModeSetting strength =
        new ModeSetting("Strength", 0, "Ausgewogen", "Aggressiv");
    public final NumberSetting renderDistance =
        new NumberSetting("Render Distance", 6, 2, 32, 1);

    /**
     * Mention Sodium and Lithium once if they are missing.
     *
     * They do far more for the frame rate than anything this module can, and
     * a player who does not know that is leaving most of the gain on the table.
     * Said once per game start, never again.
     */
    public final com.vortex.client.core.setting.BooleanSetting suggestMods =
        new com.vortex.client.core.setting.BooleanSetting("Suggest Sodium and Lithium", true);

    // Gespeicherte Originalwerte (als Object, weil OptionInstance generisch ist).
    private boolean saved = false;
    private Object oViewDistance, oMaxFps, oMipmap, oEntityShadows,
                   oBobView, oAo, oEntityDist,
                   // Added 2.28.0: biome blend (chunk-build CPU), clouds and
                   // simulation distance. All three getters verified against
                   // the real Yarn 1.21.11+build.4 mappings (raw fetch from
                   // FabricMC/yarn, branch 1.21.11):
                   //   method_41805 getBiomeBlendRadius
                   //   method_42510 getSimulationDistance
                   //   method_42528 getCloudRenderMode
                   // CloudStatus's enum constants (OFF/FAST/FANCY) are NOT
                   // in the mapping file across all versions -- Mojang does not
                   // obfuscate enum constant names, so they compile as-is.
                   oBiomeBlend, oSimDistance, oClouds;

    public PotatoModeModule() {
        super("Potato Mode", Category.PERFORMANCE);
        addSetting(strength);
        addSetting(renderDistance);
        addSetting(suggestMods);
    }

    @Override
    protected void onEnable() {
        Options o = options();
        if (o == null) return;

        // Aktuelle Werte einmalig sichern (nur wenn noch nicht gesichert).
        if (!saved) {
            oViewDistance  = get(o.renderDistance());
            oMaxFps        = get(o.framerateLimit());
            oMipmap        = get(o.mipmapLevels());
            oEntityShadows = get(o.entityShadows());
            oBobView       = get(o.bobView());
            oAo            = get(o.ambientOcclusion());
            oEntityDist    = get(o.entityDistanceScaling());
            oBiomeBlend    = get(o.biomeBlendRadius());
            oSimDistance   = get(o.simulationDistance());
            oClouds        = get(o.cloudStatus());
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
        Options o = options();
        if (o == null) return;

        if (saved) {
            set(o.renderDistance(), oViewDistance);
            set(o.framerateLimit(), oMaxFps);
            set(o.mipmapLevels(), oMipmap);
            set(o.entityShadows(), oEntityShadows);
            set(o.bobView(), oBobView);
            set(o.ambientOcclusion(), oAo);
            set(o.entityDistanceScaling(), oEntityDist);
            // Round-trips as Object: the enum read in get() goes back through
            // set() unchanged, so no enum constant needs naming here.
            set(o.biomeBlendRadius(), oBiomeBlend);
            set(o.simulationDistance(), oSimDistance);
            set(o.cloudStatus(), oClouds);
            saved = false;
            // Welt neu laden, damit die wiederhergestellte Render-Distanz wirkt.
            reloadWorld();
        }
    }

    /** Wendet die Potato-Werte je nach Staerke an. */
    private void applyPotato(Options o) {
        boolean aggressive = strength.is("Aggressiv");

        // Render-Distanz: vom Setting (Integer).
        set(o.renderDistance(), renderDistance.getInt());

        // FPS-Limit hochsetzen, damit nichts kuenstlich bremst.
        // 260 entspricht in Vanilla "Unbegrenzt".
        set(o.framerateLimit(), aggressive ? 260 : 120);

        // Mipmaps aus (0) im aggressiven Modus, sonst niedrig (1).
        set(o.mipmapLevels(), aggressive ? 0 : 1);

        // Schatten, View-Bobbing immer aus -- kosten Leistung, kein PvP-Nutzen.
        set(o.entityShadows(), false);
        set(o.bobView(), false);

        // Smooth Lighting (AO): im aggressiven Modus aus, sonst an lassen.
        set(o.ambientOcclusion(), !aggressive);

        // Entity-Distanz-Skalierung: weniger = Entities werden frueher
        // ausgeblendet (Double). Aggressiv 0.5, ausgewogen 0.75.
        set(o.entityDistanceScaling(), aggressive ? 0.5 : 0.75);

        // Biome blend (Integer). One of the more underrated CPU costs: every
        // chunk rebuild samples neighbouring biomes for colour blending, and
        // the cost grows with the radius. 0 = no blending at all.
        set(o.biomeBlendRadius(), aggressive ? 0 : 1);

        // Clouds (enum). OFF removes the cloud layer entirely; FAST keeps
        // flat clouds. Cheap win, zero PvP value lost.
        set(o.cloudStatus(), aggressive ? CloudStatus.OFF : CloudStatus.FAST);

        // Simulation distance (Integer). Only has an effect in singleplayer,
        // where the integrated server ticks the world -- on a multiplayer
        // server the server decides. Setting it in MP is harmless (ignored).
        // Vanilla minimum is 5.
        set(o.simulationDistance(), aggressive ? 5 : 8);

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
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.level == null) return;
            var acc = (com.vortex.client.mixin.client.MinecraftClientAccessor) client;
            var wr = acc.pvpclient$getWorldRenderer();
            if (wr != null) {
                wr.resetLevelRenderData();
            }
        } catch (Throwable ignored) {
            // Kein reload moeglich -> Aenderung wirkt verzoegert, kein Crash.
        }
    }

    private static Options options() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : client.options;
    }

    /** Liest den aktuellen Wert einer Option (kann null sein). */
    private static Object get(OptionInstance<?> opt) {
        try {
            return opt.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Setzt einen Wert auf eine Option. Der unchecked-Cast ist noetig, weil
     * OptionInstance generisch ist; wir uebergeben aber immer den passenden Typ
     * (Integer/Boolean/Double) bzw. den vorher ausgelesenen Originalwert.
     */
    @SuppressWarnings("unchecked")
    private static void set(OptionInstance<?> opt, Object value) {
        if (value == null) return;
        try {
            ((OptionInstance<Object>) opt).set(value);
        } catch (Throwable ignored) {
            // Falscher Typ o.ae. -> Option ueberspringen, nie crashen.
        }
    }
}
