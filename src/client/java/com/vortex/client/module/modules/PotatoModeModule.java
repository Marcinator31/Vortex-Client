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
            sichereExtras(o);
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
            stelleExtrasHer();
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

        // Grafik "Schnell", undurchsichtiges Laub, keine verbesserte
        // Transparenz. Seit 1.21.11 sind das einzelne Optionen statt eines
        // Grafik-Schalters -- siehe setzeExtras().
        setzeExtras(o);

        // Welt neu laden, damit Render-Distanz & Smooth-Lighting sofort wirken.
        reloadWorld();
    }

    /**
     * Die Options-Setter veranlassen Vanilla selbst zu einem sicheren Chunk- und
     * Renderdistanz-Refresh. Ein direkter resetLevelRenderData()-Aufruf ist in
     * 26.x nicht mehr sicher: Er kann die ViewArea zwischen zwei Renderframes
     * leeren und führt dann in LevelRenderer.repositionCamera zu einem Absturz.
     */
    private static void reloadWorld() {
        // Absichtlich kein direkter Renderer-Reset.
    }

    // ------------------------------------------------------------------
    // Grafik, Laub, Transparenz (4.4.0)
    // ------------------------------------------------------------------
    //
    // WARUM PER NAME GESUCHT: Mojang hat "Grafik: Schnell/Schoen" in 1.21.11
    // in einzelne Optionen zerlegt, und die Namen sind zwischen den Fassungen
    // gewandert. Statt fest einen Namen einzubauen (Build-Fehler, falls er
    // nicht passt), wird die Option zur Laufzeit gesucht. Gibt es sie nicht,
    // wird sie einfach uebersprungen.
    //
    //   Wahrheitswert  -> auf false (kein durchsichtiges Laub, keine teure
    //                     Transparenz-Stufe)
    //   Aufzaehlung    -> auf den Wert "FAST", falls vorhanden

    private static final String[] EXTRA_OPTIONEN = {
            // KEIN "graphicsPreset": ein Preset setzt beim Anwenden auch
            // Sichtweite und Co. -- es wuerde Potatos eigene Werte ueberschreiben.
            "graphicsMode", "cutoutLeaves", "improvedTransparency"
    };
    // Reihenfolge zaehlt: ein Grafik-Preset kann beim Setzen andere Optionen
    // mitsetzen. Deshalb feste Reihenfolge (LinkedHashMap) und beim
    // Zuruecksetzen das Preset ZUERST, dann die Einzeloptionen darueber.
    private final java.util.Map<String, Object> extraAlt = new java.util.LinkedHashMap<>();

    private static OptionInstance<?> finde(Options o, String name) {
        try {
            java.lang.reflect.Method m = o.getClass().getMethod(name);
            Object r = m.invoke(o);
            return (r instanceof OptionInstance<?> oi) ? oi : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void sichereExtras(Options o) {
        extraAlt.clear();
        for (String n : EXTRA_OPTIONEN) {
            OptionInstance<?> oi = finde(o, n);
            if (oi != null) extraAlt.put(n, get(oi));
        }
    }

    private void stelleExtrasHer() {
        Options o = options();
        if (o == null) return;
        for (var e : extraAlt.entrySet()) {
            OptionInstance<?> oi = finde(o, e.getKey());
            if (oi != null) set(oi, e.getValue());
        }
        extraAlt.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setzeExtras(Options o) {
        for (String n : EXTRA_OPTIONEN) {
            OptionInstance<?> oi = finde(o, n);
            if (oi == null) continue;
            Object jetzt = get(oi);
            if (jetzt instanceof Boolean) {
                set(oi, Boolean.FALSE);
            } else if (jetzt instanceof Enum<?>) {
                Enum<?> en = (Enum<?>) jetzt;
                try {
                    set(oi, Enum.valueOf((Class) en.getDeclaringClass(), "FAST"));
                } catch (Throwable ignored) {
                    // Kein Wert "FAST" -- Option bleibt, wie sie ist.
                }
            }
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
