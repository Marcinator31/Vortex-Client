package com.example.pvpclient.module;

import com.example.pvpclient.module.modules.ArmorHudModule;
import com.example.pvpclient.module.modules.CpsModule;
import com.example.pvpclient.module.modules.CoordinatesModule;
import com.example.pvpclient.module.modules.PotionEffectsModule;
import com.example.pvpclient.module.modules.TotemCountModule;
import com.example.pvpclient.module.modules.RadarModule;
import com.example.pvpclient.module.modules.SaturationModule;
import com.example.pvpclient.module.modules.FpsModule;
import com.example.pvpclient.module.modules.PingModule;
import com.example.pvpclient.module.modules.FullbrightModule;
import com.example.pvpclient.module.modules.PotatoModeModule;
import com.example.pvpclient.module.modules.NoFogModule;
import com.example.pvpclient.module.modules.ClearWaterModule;
import com.example.pvpclient.module.modules.ClearLavaModule;
import com.example.pvpclient.module.modules.HandItemScaleModule;
import com.example.pvpclient.module.modules.FreecamModule;
import com.example.pvpclient.module.modules.EspModule;
import com.example.pvpclient.module.modules.BlockEspModule;
import com.example.pvpclient.module.modules.StashFinderModule;
import com.example.pvpclient.module.modules.ContainerEspModule;
import com.example.pvpclient.module.modules.SpawnerEspModule;
import com.example.pvpclient.module.modules.ItemEspModule;
import com.example.pvpclient.module.modules.PlayerListEspModule;
import com.example.pvpclient.module.modules.SusChunksModule;
import com.example.pvpclient.module.modules.TunnelDetectorModule;
import com.example.pvpclient.module.modules.AutoTotemModule;
import com.example.pvpclient.module.modules.AimbotModule;
import com.example.pvpclient.module.modules.AutoHitModule;
import com.example.pvpclient.module.modules.AntiRenderModule;
import com.example.pvpclient.module.modules.FlyModule;
import com.example.pvpclient.module.modules.NoFallModule;
import com.example.pvpclient.module.modules.KeystrokesModule;
import com.example.pvpclient.module.modules.ChunkBordersModule;
import com.example.pvpclient.module.modules.ProjectilePathModule;
import com.example.pvpclient.module.modules.TotemPopperModule;
import com.example.pvpclient.module.modules.SessionStatsModule;
import com.example.pvpclient.module.modules.TargetInfoModule;
import com.example.pvpclient.module.modules.CrystalMacroModule;
import com.example.pvpclient.module.modules.GlobalHudColorModule;
import com.example.pvpclient.module.modules.HitboxModule;
import com.example.pvpclient.module.modules.HealthIndicatorModule;
import com.example.pvpclient.module.modules.NoParticlesModule;
import com.example.pvpclient.module.modules.SmallTotemModule;
import com.example.pvpclient.module.modules.NoPumpkinBlurModule;
import com.example.pvpclient.module.modules.LowFireModule;
import com.example.pvpclient.module.modules.LowShieldModule;
import com.example.pvpclient.module.modules.ShieldStatusModule;
import com.example.pvpclient.module.modules.ToggleSprintModule;

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
        register(new NoParticlesModule());
        register(new SmallTotemModule());
        register(new NoPumpkinBlurModule());
        register(new LowFireModule());
        register(new LowShieldModule());
        // Weitere kommen einfach hier dazu.
    }

    private void register(Module module) {
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
