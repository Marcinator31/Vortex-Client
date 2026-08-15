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
import com.vortex.client.module.modules.FreecamModule;
import com.vortex.client.module.modules.EspModule;
import com.vortex.client.module.modules.BlockEspModule;
import com.vortex.client.module.modules.StashFinderModule;
import com.vortex.client.module.modules.ContainerEspModule;
import com.vortex.client.module.modules.SpawnerEspModule;
import com.vortex.client.module.modules.ItemEspModule;
import com.vortex.client.module.modules.PlayerListEspModule;
import com.vortex.client.module.modules.SusChunksModule;
import com.vortex.client.module.modules.TunnelDetectorModule;
import com.vortex.client.module.modules.AutoTotemModule;
import com.vortex.client.module.modules.AimbotModule;
import com.vortex.client.module.modules.AutoHitModule;
import com.vortex.client.module.modules.AntiRenderModule;
import com.vortex.client.module.modules.FlyModule;
import com.vortex.client.module.modules.NoFallModule;
import com.vortex.client.module.modules.KeystrokesModule;
import com.vortex.client.module.modules.ChunkBordersModule;
import com.vortex.client.module.modules.ProjectilePathModule;
import com.vortex.client.module.modules.TotemPopperModule;
import com.vortex.client.module.modules.SessionStatsModule;
import com.vortex.client.module.modules.TargetInfoModule;
import com.vortex.client.module.modules.CrystalMacroModule;
import com.vortex.client.module.modules.NoRenderBlocksModule;
import com.vortex.client.module.modules.ZoomModule;
import com.vortex.client.module.modules.AutoReconnectModule;
import com.vortex.client.module.modules.ToggleSneakModule;
import com.vortex.client.module.modules.CrosshairModule;
import com.vortex.client.module.modules.ChatModule;
import com.vortex.client.module.modules.NametagModule;
import com.vortex.client.module.modules.ArmorWarningModule;
import com.vortex.client.module.modules.ItemCounterModule;
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
        register(new FreecamModule());
        register(new EspModule());
        register(new BlockEspModule());
        register(new StashFinderModule());
        register(new ContainerEspModule());
        register(new SpawnerEspModule());
        register(new ItemEspModule());
        register(new PlayerListEspModule());
        register(new SusChunksModule());
        register(new TunnelDetectorModule());
        register(new AutoTotemModule());
        register(new AimbotModule());
        register(new AutoHitModule());
        register(new AntiRenderModule());
        register(new FlyModule());
        register(new NoFallModule());
        register(new KeystrokesModule());
        register(new ChunkBordersModule());
        register(new ProjectilePathModule());
        register(new TotemPopperModule());
        register(new SessionStatsModule());
        register(new TargetInfoModule());
        register(new CrystalMacroModule());
        register(new NoRenderBlocksModule());
        register(new ZoomModule());
        register(new AutoReconnectModule());
        register(new ToggleSneakModule());
        register(new CrosshairModule());
        register(new ChatModule());
        register(new NametagModule());
        register(new ArmorWarningModule());
        register(new ItemCounterModule());
        register(new NoParticlesModule());
        register(new SmallTotemModule());
        register(new NoPumpkinBlurModule());
        register(new LowFireModule());
        register(new LowShieldModule());
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

    /** Alle Module einer Kategorie -- praktisch fuers GUI. */
    public List<Module> getByCategory(Module.Category category) {
        List<Module> result = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == category) result.add(m);
        }
        return result;
    }
}
