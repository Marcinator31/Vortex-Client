package com.vortex.client.module;

import com.vortex.client.module.modules.ArmorHudModule;
import com.vortex.client.module.modules.CpsModule;
import com.vortex.client.module.modules.CoordinatesModule;
import com.vortex.client.module.modules.PotionEffectsModule;
import com.vortex.client.module.modules.TotemCountModule;
import com.vortex.client.module.modules.RadarModule;
import com.vortex.client.module.modules.SaturationModule;
import com.vortex.client.module.modules.FpsModule;
import com.vortex.client.module.modules.PingModule;
import com.vortex.client.module.modules.FullbrightModule;
import com.vortex.client.module.modules.PotatoModeModule;
import com.vortex.client.module.modules.NoFogModule;
import com.vortex.client.module.modules.ClearWaterModule;
import com.vortex.client.module.modules.ClearLavaModule;
import com.vortex.client.module.modules.HandItemScaleModule;
import com.vortex.client.module.modules.PlayerListEspModule;
import com.vortex.client.module.modules.AntiRenderModule;
import com.vortex.client.module.modules.KeystrokesModule;
import com.vortex.client.module.modules.ChunkBordersModule;
import com.vortex.client.module.modules.ProjectilePathModule;
import com.vortex.client.module.modules.TotemPopperModule;
import com.vortex.client.module.modules.SessionStatsModule;
import com.vortex.client.module.modules.TargetInfoModule;
import com.vortex.client.module.modules.NoRenderBlocksModule;
import com.vortex.client.module.modules.ZoomModule;
import com.vortex.client.module.modules.AutoReconnectModule;
import com.vortex.client.module.modules.ToggleSneakModule;
import com.vortex.client.module.modules.CrosshairModule;
import com.vortex.client.module.modules.ChatModule;
import com.vortex.client.module.modules.NametagModule;
import com.vortex.client.module.modules.ArmorWarningModule;
import com.vortex.client.module.modules.ItemCounterModule;
import com.vortex.client.module.modules.DebugOverlayModule;
import com.vortex.client.module.modules.GlobalHudColorModule;
import com.vortex.client.module.modules.HitboxModule;
import com.vortex.client.module.modules.HealthIndicatorModule;
import com.vortex.client.module.modules.NoParticlesModule;
import com.vortex.client.module.modules.SmallTotemModule;
import com.vortex.client.module.modules.NoPumpkinBlurModule;
import com.vortex.client.module.modules.LowFireModule;
import com.vortex.client.module.modules.LowShieldModule;
import com.vortex.client.module.modules.ShieldStatusModule;
import com.vortex.client.module.modules.ToggleSprintModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Liste aller Module. Hier wird jedes Feature EINMAL
 * registriert -- danach taucht es ueberall automatisch auf
 * (im GUI, beim Speichern, beim Rendern).
 */
public final class ModuleManager {

    public static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new ArrayList<>();

    /**
     * Nachschlagetabelle Klasse -> Modul.
     *
     * Grund: Viele Stellen (Mixins!) brauchen "das Modul vom Typ X". Frueher
     * wurde dafuer jedes Mal die ganze Modul-Liste durchlaufen. In Render-Pfaden
     * passiert das pro Entity pro Frame -- bei vielen Entities sind das Millionen
     * Vergleiche pro Sekunde und entsprechend Rechenzeit. Hier wird einmal beim
     * Start eingetragen und danach in konstanter Zeit nachgeschlagen.
     */
    private final java.util.Map<Class<?>, Module> byType = new java.util.HashMap<>();

    private ModuleManager() {
        // --- Hier registrierst du neue Features ---
        register(new CpsModule());
        register(new ArmorHudModule());
        register(new FpsModule());
        register(new PingModule());
        register(new CoordinatesModule());
        register(new PotionEffectsModule());
        register(new TotemCountModule());
        register(new RadarModule());
        register(new com.vortex.client.module.modules.SpotifyModule());
        register(new com.vortex.client.module.modules.NowPlayingModule());
        register(new GlobalHudColorModule());
        register(new SaturationModule());
        register(new ToggleSprintModule());
        register(new HitboxModule());
        register(new HealthIndicatorModule());
        register(new ShieldStatusModule());
        register(new FullbrightModule());
        register(new PotatoModeModule());
        register(new NoFogModule());
        register(new ClearWaterModule());
        register(new ClearLavaModule());
        register(new HandItemScaleModule());
        register(new PlayerListEspModule());
        register(new AntiRenderModule());
        register(new KeystrokesModule());
        register(new ChunkBordersModule());
        register(new ProjectilePathModule());
        register(new TotemPopperModule());
        register(new SessionStatsModule());
        register(new TargetInfoModule());
        register(new NoRenderBlocksModule());
        register(new ZoomModule());
        register(new AutoReconnectModule());
        register(new ToggleSneakModule());
        register(new CrosshairModule());
        register(new ChatModule());
        register(new NametagModule());
        register(new ArmorWarningModule());
        register(new ItemCounterModule());
        register(new DebugOverlayModule());
        register(new NoParticlesModule());
        register(new SmallTotemModule());
        register(new NoPumpkinBlurModule());
        register(new LowFireModule());
        register(new LowShieldModule());
        // --- 4.4.0 ---
        register(new com.vortex.client.module.modules.SpeedometerModule());
        register(new com.vortex.client.module.modules.MaceHudModule());   // 4.7.0
        // --- 4.11.0 ---
        register(new com.vortex.client.module.modules.LightLevelModule());
        register(new com.vortex.client.module.modules.ChatFilterModule());
        register(new com.vortex.client.module.modules.SoundControlModule());
        register(new com.vortex.client.module.modules.BetterTooltipsModule());
        register(new com.vortex.client.module.modules.ExplosionTimerModule());
        register(new com.vortex.client.module.modules.AutoGGModule());
        // --- 4.9.0 ---
        register(new com.vortex.client.module.modules.CooldownHudModule());
        register(new com.vortex.client.module.modules.InventoryHudModule());
        register(new com.vortex.client.module.modules.DamageNumbersModule());
        register(new com.vortex.client.module.modules.HitEffectsModule());
        register(new com.vortex.client.module.modules.PearlTrackerModule());
        register(new com.vortex.client.module.modules.GlintModule());
        register(new com.vortex.client.module.modules.MotionBlurModule());
        register(new com.vortex.client.module.modules.CompassModule());
        register(new com.vortex.client.module.modules.ReachModule());
        register(new com.vortex.client.module.modules.ComboModule());
        register(new com.vortex.client.module.modules.TpsModule());
        register(new com.vortex.client.module.modules.ClockModule());
        register(new com.vortex.client.module.modules.BlockOutlineModule());
        register(new com.vortex.client.module.modules.HitColorModule());
        register(new com.vortex.client.module.modules.ScoreboardModule());
        register(new com.vortex.client.module.modules.BossBarModule());
        register(new com.vortex.client.module.modules.TitlesModule());
        register(new com.vortex.client.module.modules.TimeChangerModule());
        register(new com.vortex.client.module.modules.ItemPhysicsModule());
        register(new com.vortex.client.module.modules.FreelookModule());
        register(new com.vortex.client.module.modules.StreamerModeModule());
        register(new com.vortex.client.module.modules.FriendsModule());
        register(new com.vortex.client.module.modules.SlotLockModule());
        // --- 4.5.0 ---
        register(new com.vortex.client.module.modules.ItemSizeModule());
        // Weitere kommen einfach hier dazu.
    }

    /**
     * Adds a module.
     *
     * Public so an addon can bring its own. It was private before, which left
     * addons reaching into the internal list directly -- that skips the type
     * table used for lookups, and any mistake there shows up much later as a
     * module that cannot be found.
     */
    public void register(Module module) {
        nachKategorie.clear();
        modules.add(module);
        byType.put(module.getClass(), module);
    }

    /**
     * Liefert das registrierte Modul dieses Typs (oder null). Konstante Laufzeit --
     * fuer Aufrufe in Render-/Tick-Pfaden immer diese Methode benutzen.
     */
    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        return (T) byType.get(type);
    }

    public List<Module> getModules() {
        return modules;
    }

    /** Sortierte Listen je Kategorie (Menues fragen jedes Bild). */
    private final java.util.Map<Module.Category, List<Module>> nachKategorie = new java.util.EnumMap<>(Module.Category.class);

    /** Alle Module einer Kategorie -- praktisch fuers GUI. Neue Liste (darf veraendert werden). */
    public List<Module> getByCategory(Module.Category category) {
        List<Module> fertig = nachKategorie.get(category);
        if (fertig == null) {
            fertig = new ArrayList<>();
            for (Module m : modules) {
                if (m.getCategory() == category) fertig.add(m);
            }
            // Alphabetisch (Gross/klein egal) -- sonst stehen sie in der
            // Reihenfolge der Anmeldung, und Addon-Module haengen hinten dran.
            fertig.sort(java.util.Comparator.comparing(
                    (Module m) -> m.getName().toLowerCase(java.util.Locale.ROOT)));
            nachKategorie.put(category, fertig);
        }
        return new ArrayList<>(fertig);
    }
}
